(ns ^{:pattern-expansion-context :emf}
  ttc15-java-refactoring-funnyqt.refactor
  (:require [clojure.string   :as    str]
            [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall? the]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.pmatch   :refer :all]
            [funnyqt.in-place :refer :all]
            [funnyqt.utils    :refer [errorf]]
            ttc15-java-refactoring-funnyqt.jamopp
            [ttc15-java-refactoring-funnyqt.mm.java :as j]
            [ttc15-java-refactoring-funnyqt.mm.pg   :as pg])
  (:import (ttc.testdsl.tTCTest Java_Class Java_Method)
           (org.eclipse.emf.ecore.resource Resource ResourceSet)))

;;* Utils

(defn find-tclass [pg qn]
  (first (filter #(= qn (pg/tName %))
                 (pg/all-TClasses pg))))

(defn find-tmethodsig [pg method-name param-qns]
  (let [pclasses (mapv (partial find-tclass pg) param-qns)]
    (first (filter #(and (-> % pg/->method pg/tName (= method-name))
                         (= pclasses (pg/->paramList %)))
                   (pg/all-TMethodSignatures pg)))))

(defn superclass? [super sub]
  (loop [sub-super (pg/->parentClass sub)]
    (when sub-super
      (or (= sub-super super)
          (recur (pg/->parentClass sub-super))))))

(defn accessible-from? [cls m-or-f]
  (let [defining-cls (econtainer m-or-f)]
    (or (= defining-cls cls)                    ;; own members
        (superclass? defining-cls cls)          ;; inherited members
        (not (superclass? cls defining-cls))    ;; unrelated classes
        (and (pg/isa-TMethodDefinition? m-or-f) ;; subclass-method but with override
             (member? (pg/->signature m-or-f)
                      (pg/->signature cls))
             (superclass? cls defining-cls)))))

;;* Task 2: Refactoring on the Program Graph

(defpattern pull-up-member-pattern
  ([pg]
   [super<TClass> -<:childClasses>-> sub -<:signature>-> sig
    :extends [(pull-up-member-pattern 1)]])
  ([pg super sig]
   [super<TClass> -<:childClasses>-> sub -<:signature>-> sig
    sub -<:defines>-> member<TMember> -<:signature>-> sig
    :nested [others [super -<:childClasses>-> osub
                     :when (not= sub osub)
                     osub -<:signature>-> sig
                     osub -<:defines>-> omember<TMember> -<:signature>-> sig]]
    ;; There are actually other subclasses with this sig
    :when (seq others)
    ;; super doesn't have a member of this signature already
    super -!<:signature>-> sig
    ;; Really all subclasses define a member with that sig
    :when (= (count (pg/->childClasses super))
             (inc (count others)))
    :let [all-members (conj (map :omember others) member)]
    ;; All accesses from all members must be accessible already from the
    ;; superclass.
    :when (forall? (partial accessible-from? super)
                   (mapcat #(eget % :access) all-members))]))

(defn do-pull-up-member! [pg2jamopp-map-atom super member sig others]
  ;; Delete the other member defs
  (doseq [o others]
    (edelete! (:omember o)))
  ;; Add the member to the super class
  (pg/->add-defines! super member)
  (pg/->add-signature! super sig)
  ;; Synchronize action on the JaMoPP model
  (fn [_]
    (doseq [o others]
      (edelete! (@pg2jamopp-map-atom (:omember o)))
      (swap! pg2jamopp-map-atom dissoc (:omember o)))
    (j/->add-members! (@pg2jamopp-map-atom super) (@pg2jamopp-map-atom member))))

(defrule pull-up-method
  ([pg pg2jamopp-map-atom] [:extends [(pull-up-member-pattern 0)]
                            member<TMethodDefinition>]
   (println "Pulling up" (-> sig pg/->method pg/tName) "to" (pg/tName super))
   (do-pull-up-member! pg2jamopp-map-atom super member sig others))
  ([pg pg2jamopp-map-atom super sig] [:extends [(pull-up-member-pattern 1)]
                                      member<TMethodDefinition>]
   (println "Pulling up" (-> sig pg/->method pg/tName) "to" (pg/tName super))
   (do-pull-up-member! pg2jamopp-map-atom super member sig others)))

(defrule pull-up-field
  ([pg pg2jamopp-map-atom] [:extends [(pull-up-member-pattern 0)]
                            member<TFieldDefinition>]
   (println "Pulling up" (-> sig pg/->field pg/tName) "to" (pg/tName super))
   (do-pull-up-member! pg2jamopp-map-atom super member sig others))
  ([pg pg2jamopp-map-atom super sig] [:extends [(pull-up-member-pattern 1)]
                                      member<TFieldDefinition>]
   (println "Pulling up" (-> sig pg/->field pg/tName) "to" (pg/tName super))
   (do-pull-up-member! pg2jamopp-map-atom super member sig others)))

(defn make-type-reference [target-class]
  (j/create-NamespaceClassifierReference!
   nil {:namespaces (j/namespaces (econtainer target-class))
        :classifierReferences [(j/create-ClassifierReference!
                                nil {:target target-class})]}))

(defn create-superclass [pg pg2jamopp-map-atom classes new-superclass-qn]
  (let [scs (into #{} (map pg/->parentClass) classes)]
    (when (and (= 1 (count scs))
               (not (find-tclass pg new-superclass-qn)))
      (println "Creating new superclass" new-superclass-qn
               (if (first scs)
                 (str "with parent" (pg/tName (first scs)))
                 "with no parent"))
      (let [new-tclass (pg/create-TClass! pg {:tName new-superclass-qn
                                              :childClasses classes
                                              :parentClass (first scs)})]
        (fn [^ResourceSet rs]
          (let [[pkgs class-name] (let [parts (str/split new-superclass-qn #"\.")]
                                    [(butlast parts) (last parts)])
                r (new-resource rs (str (->> (.toFileString
                                              (.getURI ^Resource (.get (.getResources rs) 0)))
                                             (re-matches #"(.*/src/).*")
                                             second)
                                        (str/join "/" pkgs) "/" class-name ".java"))
                nc (j/create-Class! nil {:name class-name
                                         :annotationsAndModifiers [(j/create-Public! nil)]})
                cu (j/create-CompilationUnit! r {:name (str new-superclass-qn ".java")
                                                 :namespaces  pkgs
                                                 :classifiers [nc]})]
            (doseq [c classes]
              (j/->set-extends! (@pg2jamopp-map-atom c) (make-type-reference nc)))
            (when-let [parent (first scs)]
              (j/->set-extends! nc (make-type-reference (@pg2jamopp-map-atom parent))))
            (swap! pg2jamopp-map-atom assoc new-tclass nc)))))))

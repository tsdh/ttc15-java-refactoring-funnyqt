(ns ^{:pattern-expansion-context :emf}
  ttc15-java-refactoring-funnyqt.refactor
  (:require [clojure.string   :as    str]
            [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall? the]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.in-place :refer :all]
            [funnyqt.utils    :refer [errorf]]
            [ttc15-java-refactoring-funnyqt.mm.java :as j]
            [ttc15-java-refactoring-funnyqt.mm.pg   :as pg])
  (:import (ttc.testdsl.tTCTest Java_Class Java_Method)
           (org.eclipse.emf.ecore.resource Resource ResourceSet)))

;;* Utils

(defn find-tclass [pg qn]
  (first (filter #(= qn (pg/tName %))
                 (pg/eall-TClasses pg))))

(defn find-tmethodsig [pg method-name param-qns]
  (let [pclasses (mapv (partial find-tclass pg) param-qns)]
    (first (filter #(and (-> % pg/->method pg/tName (= method-name))
                         (= pclasses (pg/->paramList %)))
                   (pg/eall-TMethodSignatures pg)))))

;;* Task 2: Refactoring on the Program Graph

(defn ^:private superclass? [super sub]
  (loop [sub-super (pg/->parentClass sub)]
    (when sub-super
      (or (= sub-super super)
          (recur (pg/->parentClass sub-super))))))

(defn ^:private accessible-from? [cls m-or-f]
  (let [defining-cls (econtainer m-or-f)]
    (or (= defining-cls cls)                    ;; own members
        (superclass? defining-cls cls)          ;; inherited members
        (not (superclass? cls defining-cls))    ;; unrelated classes
        (and (pg/isa-TMethodDefinition? m-or-f) ;; subclass-method but with override
             (member? (pg/->signature m-or-f)
                      (pg/->signature cls))
             (superclass? cls defining-cls)))))

(defrule pull-up-method [pg pg2jamopp-map-atom super sig]
  [super<TClass> -<:childClasses>-> sub -<:signature>-> sig
   sub -<:defines>-> md<TMethodDefinition> -<:signature>-> sig
   :nested [others [super -<:childClasses>-> osub
                    :when (not= sub osub)
                    osub -<:signature>-> sig
                    osub -<:defines>-> omd<TMethodDefinition> -<:signature>-> sig]]
   ;; super doesn't have such a method of this signature already
   super -!<:signature>-> sig
   ;; Really all subclasses define a method with sig
   :when (= (count (pg/->childClasses super))
            (inc (count others)))
   :let [all-mds (conj (map :omd others) md)]
   ;; All accesses from all method-defs must be accessible already from the
   ;; superclass.
   :when (forall? (partial accessible-from? super)
                  (mapcat #(eget % :access) all-mds))]
  (println "Pulling up" (-> sig pg/->method pg/tName) "to" (pg/tName super))
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o)))
  ;; Add it to the super class
  (pg/->add-defines! super md)
  (pg/->add-signature! super sig)
  (fn [_]
    (doseq [o others]
      (edelete! (@pg2jamopp-map-atom (:omd o)))
      (swap! pg2jamopp-map-atom dissoc (:omd o)))
    (j/->add-members! (@pg2jamopp-map-atom super) (@pg2jamopp-map-atom md))))

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

(ns ^{:pattern-expansion-context :emf}
  ttc15-java-refactoring-funnyqt.refactor
  (:require [clojure.string   :as    str]
            [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall? exists? the]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.pmatch   :refer :all]
            [funnyqt.in-place :refer :all]
            [funnyqt.utils    :refer [errorf]]
            ttc15-java-refactoring-funnyqt.jamopp
            [ttc15-java-refactoring-funnyqt.mm.java :as j]
            [ttc15-java-refactoring-funnyqt.mm.pg   :as pg])
  (:import (org.eclipse.emf.ecore.resource Resource ResourceSet)))

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
    :when (seq others) ;; There are actually other subclasses with this sig
    super -!<:signature>-> sig ;; super doesn't have a member of this sig
    :when (= (count (pg/->childClasses super)) ;; all subs define  that sig
             (inc (count others)))
    :let [all-members (conj (map :omember others) member)]
    :when (forall? (partial accessible-from? super)
                   (mapcat #(eget % :access) all-members))]))

(defn find-accessors [pg tmember]
  (filter #(member? tmember (pg/->access %))
          (pg/all-TMembers pg)))

(defn do-pull-up-member! [pg pg2jamopp-map-atom super sub member sig others]
  (doseq [o others]
    (doseq [acc (find-accessors pg (:omember o))]
      (pg/->remove-access! acc (:omember o))
      (pg/->add-access! acc member))
    (edelete! (:omember o))
    (pg/->remove-signature! (:osub o) sig))
  (pg/->remove-signature! sub sig)
  (pg/->add-defines! super member)
  (pg/->add-signature! super sig)
  (fn [_]
    (doseq [o others]
      (edelete! (@pg2jamopp-map-atom (:omember o)))
      (swap! pg2jamopp-map-atom dissoc (:omember o)))
    (j/->add-members! (@pg2jamopp-map-atom super) (@pg2jamopp-map-atom member))))

(defrule pull-up-method
  ([pg pg2jamopp-map-atom jamopp] [:extends [(pull-up-member-pattern 0)]
                                   member<TMethodDefinition>]
   ((do-pull-up-member! pg pg2jamopp-map-atom super sub member sig others)
    jamopp))
  ([pg pg2jamopp-map-atom super sig] [:extends [(pull-up-member-pattern 1)]
                                      member<TMethodDefinition>]
   (do-pull-up-member! pg pg2jamopp-map-atom super sub member sig others)))

(defrule pull-up-field
  ([pg pg2jamopp-map-atom jamopp] [:extends [(pull-up-member-pattern 0)]
                                   member<TFieldDefinition>]
   ((do-pull-up-member! pg pg2jamopp-map-atom super sub member sig others)
    jamopp))
  ([pg pg2jamopp-map-atom super sig] [:extends [(pull-up-member-pattern 1)]
                                      member<TFieldDefinition>]
   (do-pull-up-member! pg pg2jamopp-map-atom super sub member sig others)))

(defn make-type-reference [target-class]
  (j/create-NamespaceClassifierReference!
   nil {:namespaces (j/namespaces (econtainer target-class))
        :classifierReferences [(j/create-ClassifierReference!
                                nil {:target target-class})]}))

(defrule create-superclass
  ([pg pg2jamopp-map-atom jamopp]
   [sig<TSignature>
    :let [classes (filter #(member? sig (pg/->signature %))
                          (remove pg/->parentClass (pg/all-TClasses pg)))
          new-superclass-qn (str (gensym "ext.NewParent"))]
    :when (> (count classes) 1)
    :extends [(create-superclass 1)]]
   ((create-superclass pg pg2jamopp-map-atom classes new-superclass-qn)
    jamopp))
  ([pg pg2jamopp-map-atom classes new-superclass-qn]
   [:let [scs (into #{} (map pg/->parentClass) classes)]
    :when (and (= 1 (count scs)) (not (find-tclass pg new-superclass-qn)))]
   (let [new-tclass (pg/create-TClass! pg {:tName new-superclass-qn
                                           :childClasses classes
                                           :parentClass (first scs)})]
     (fn [^ResourceSet rs]
       (let [[pkgs class-name] (let [parts (str/split new-superclass-qn #"\.")]
                                 [(butlast parts) (last parts)])
             ^Resource other-r (.get (.getResources rs) 0)
             r (new-resource rs (str (->> other-r .getURI .toFileString
                                          (re-matches #"(.*[/-]src/).*")
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
         (swap! pg2jamopp-map-atom assoc new-tclass nc))))))

(defrule extract-superclass [pg pg2jamopp-map-atom jamopp]
  [:extends [(create-superclass 0)]]
  ((create-superclass pg pg2jamopp-map-atom classes new-superclass-qn) jamopp)
  (let [super (find-tclass pg new-superclass-qn)]
    (doseq [sig (filter (fn [sig]
                          (forall? #(member? sig (pg/->signature %)) classes))
                        (pg/all-TSignatures pg))]
      (if (pg/isa-TFieldSignature? sig)
        ((pull-up-field pg pg2jamopp-map-atom super sig) jamopp)
        ((pull-up-method pg pg2jamopp-map-atom super sig) jamopp)))))

(defn refactor-interactively [pg pg2jamopp-map-atom jamopp]
  ((interactive-rule create-superclass pull-up-method pull-up-field extract-superclass)
   pg pg2jamopp-map-atom jamopp))

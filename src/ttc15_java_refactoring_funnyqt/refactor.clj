(ns ttc15-java-refactoring-funnyqt.refactor
  (:require [clojure.string   :as    str]
            [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall? the]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.in-place :refer :all]
            [funnyqt.utils    :refer [errorf]])
  (:import (ttc.testdsl.tTCTest Java_Class Java_Method)
           (org.eclipse.emf.ecore.resource Resource ResourceSet)))

;;* Utils

(defn find-tclass [pg qn]
  (first (filter #(= qn (eget-raw % :tName))
                 (eallcontents pg 'TClass))))

(defn find-tmethodsig [pg method-name param-qns]
  (let [pclasses (mapv (partial find-tclass pg) param-qns)]
    (first (filter (fn [tms]
                     (and (-> tms
                              (eget-raw :method)
                              (eget-raw :tName)
                              (= method-name))
                          (= pclasses (eget-raw tms :paramList))))
                   (eallcontents pg 'TMethodSignature)))))

;;* Task 2: Refactoring on the Program Graph

(defn ^:private superclass? [super sub]
  (loop [sub-super (eget-raw sub :parentClass)]
    (when sub-super
      (or (= sub-super super)
          (recur (eget-raw sub-super :parentClass))))))

(defn ^:private accessible-from? [cls m-or-f]
  (let [defining-cls (econtainer m-or-f)]
    (or (= defining-cls cls)                 ;; own members
        (superclass? defining-cls cls)       ;; inherited members
        (not (superclass? cls defining-cls)) ;; unrelated classes
        (and (has-type? m-or-f 'TMethodDefinition) ;; subclass-method but with override
             (member? (eget-raw m-or-f :signature)
                      (eget-raw cls :signature))
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
   :when (= (count (eget super :childClasses))
            (inc (count others)))
   :let [all-mds (conj (map :omd others) md)]
   ;; All accesses from all method-defs must be accessible already from the
   ;; superclass.
   :when (forall? (partial accessible-from? super)
                  (mapcat #(eget % :access) all-mds))]
  (println "Pulling up" (-> sig (eget :method) (eget :tName)) "to" (eget super :tName))
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o)))
  ;; Add it to the super class
  (eadd! super :defines md)
  (eadd! super :signature sig)
  (fn [_]
    (doseq [o others]
      (edelete! (@pg2jamopp-map-atom (:omd o)))
      (swap! pg2jamopp-map-atom dissoc (:omd o)))
    (eadd! (@pg2jamopp-map-atom super) :members (@pg2jamopp-map-atom md))))

(defn make-type-reference [target-class]
  (ecreate! nil 'ClassifierReference {:target target-class}))

(defn create-superclass [pg pg2jamopp-map-atom classes new-superclass-qn]
  (let [scs (into #{} (map #(eget-raw % :parentClass)) classes)]
    (when (and (= 1 (count scs))
               (not (find-tclass pg new-superclass-qn)))
      (println "Creating new superclass" new-superclass-qn
               (if (first scs)
                 (str "with parent" (eget (first scs) :tName))
                 "with no parent"))
      (let [new-tclass (ecreate! pg 'TClass {:tName new-superclass-qn
                                             :childClasses classes
                                             :parentClass (first scs)})]
        (fn [^ResourceSet rs]
          (let [[pkgs class-name] (let [parts (str/split new-superclass-qn #"\.")]
                                    [(butlast parts) (last parts)])
                r (new-resource rs (str/replace (.toFileString (.getURI ^Resource (.get (.getResources rs) 0)))
                                                #"[A-Za-z0-9]+\.java$" (str class-name ".java")))
                nc (ecreate! nil 'Class {:name class-name})
                cu (ecreate! r 'CompilationUnit {:name (str new-superclass-qn ".java")
                                                 :namespaces  pkgs
                                                 :classifiers [nc]})]
            (doseq [c classes]
              (eset! (@pg2jamopp-map-atom c) :extends (make-type-reference nc)))
            (when-let [parent (first scs)]
              (eset! nc :extends (make-type-reference (@pg2jamopp-map-atom parent))))
            (swap! pg2jamopp-map-atom assoc new-tclass nc)))))))

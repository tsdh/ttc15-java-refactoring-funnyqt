(ns ttc15-java-refactoring-funnyqt.refactor
  (:require [clojure.string   :refer [join]]
            [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall? the]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.in-place :refer :all]
            [funnyqt.utils    :refer [errorf]])
  (:import (ttc.testdsl.tTCTest Java_Class Java_Method)))

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
    (or (= defining-cls cls)
        (superclass? defining-cls cls)
        (and (has-type? m-or-f 'TMethodDefinition)
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
   :let [all-mds (map :omd others)]
   ;; All accesses from all method-defs must be accessible already from the
   ;; superclass.
   :when (forall? (partial accessible-from? super)
                  (mapcat #(eget % :access) all-mds))]
  (println "pulling up" (-> sig (eget :method) (eget :tName))
           "from" (eget sub :tName) "to" (eget super :tName))
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o))
    (edelete! (@pg2jamopp-map-atom (:omd o))))
  ;; Add it to the super class
  (eadd! super :defines md)
  (eadd! super :signature sig)
  (fn []
    (doseq [o others]
      (edelete! (@pg2jamopp-map-atom (:omd o)))
      (swap! pg2jamopp-map-atom dissoc (:omd o)))
    (eadd! (@pg2jamopp-map-atom super) :members (@pg2jamopp-map-atom md))))

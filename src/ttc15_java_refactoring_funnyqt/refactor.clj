(ns ttc15-java-refactoring-funnyqt.refactor
  (:require [funnyqt.emf      :refer :all]
            [funnyqt.query    :refer [member? forall?]]
            [funnyqt.generic  :refer [has-type?]]
            [funnyqt.in-place :refer :all]))

;;* Task 2: Refactoring on the Program Graph

(defn superclass? [super sub]
  (loop [sub-super (eget-raw sub :parentClass)]
    (when sub-super
      (or (= sub-super super)
          (recur (eget-raw sub-super :parentClass))))))

(defn accessible-from? [cls m-or-f]
  (let [defining-cls (econtainer m-or-f)]
    (or (= defining-cls cls)
        (superclass? defining-cls cls)
        (and (has-type? m-or-f 'TMethodDefinition)
             (member? (eget-raw m-or-f :signature)
                      (eget-raw cls :signature))
             (superclass? cls defining-cls)))))

(defrule pull-up-method [g pg2jamopp-map-atom]
  [super<TClass> -<:childClasses>-> sub
   -<:defines>-> md<TMethodDefinition>
   -<:signature>-> sig
   :nested [others [super -<:childClasses>-> osub
                    :when (not= sub osub)
                    osub -<:defines>-> omd<TMethodDefinition>
                    -<:signature>-> sig]]
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
  (println @pg2jamopp-map-atom)
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o))
    #_(edelete! (@pg2jamopp-map-atom (:omd o))))
  ;; Add it to the super class
  (eadd! super :defines md)
  (eadd! super :signature sig))

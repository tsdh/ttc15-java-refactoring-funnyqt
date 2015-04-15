(ns ttc15-java-refactoring-funnyqt.refactor
  (:require [funnyqt.emf      :refer :all]
            [funnyqt.in-place :refer :all]))

;;* Task 2: Refactoring on the Program Graph

;; TODO: Check accesses!
(defrule pull-up-method [g jamopp2pg]
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
            (inc (count others)))]
  (println "pulling up" (-> sig (eget :method) (eget :tName))
           "from" (eget sub :tName) "to" (eget super :tName))
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o)))
  ;; Add it to the super class
  (eadd! super :defines md)
  (eadd! super :signature sig))

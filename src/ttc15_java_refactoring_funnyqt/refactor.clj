(ns ttc15-java-refactoring-funnyqt.refactor
  (:require [funnyqt.emf      :refer :all]
            [funnyqt.in-place :refer :all]))

;;* Task 2: Refactoring on the Program Graph

;; TODO: Check accesses!
(defrule pull-up-method [g]
  [super<TClass> -<:subclasses>-> sub
   -<:defines>-> md<TMethodDefinition>
   -<:signature>-> sig
   :nested [others [super -<:subclasses>-> osub
                    :when (not= sub osub)
                    osub -<:defines>-> omd<TMethodDefinition>
                    -<:signature>-> sig]]
   ;; super doesn't have such a method already
   super -!<:signature>-> sig
   ;; Really all subclasses define a method with sig
   :when (= (count (eget super :subclasses))
            (inc (count others)))]
  ;; Delete the other method defs
  (doseq [o others]
    (edelete! (:omd o)))
  ;; Add it to the super class
  (eadd! super :defines md)
  (eadd! super :signature sig))

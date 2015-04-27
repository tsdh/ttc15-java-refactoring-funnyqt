(ns ttc15-java-refactoring-funnyqt.core-test
  (:require [clojure.test :refer :all]
            [funnyqt.visualization :as viz]
            [funnyqt.emf :as emf]
            [funnyqt.utils :as u]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp    :as jamopp]
            [ttc15-java-refactoring-funnyqt.refactor  :refer :all])
  (:import
   (org.eclipse.emf.ecore.resource Resource ResourceSet)
   (org.eclipse.emf.common.util URI)))

(defn print-jamopp-resource [^ResourceSet rs res-name]
  (if-let [r (.getResource rs (URI/createFileURI res-name) true)]
    (viz/print-model r :gtk)
    (println "No such resource" res-name)))

(deftest test-pum-paper-example01
  (println "Test PUM: paper-example01")
  (let [jamopp (jamopp/parse-directory "test-src/paper-example01/src")
        pg (emf/new-resource)
        mappings-atom (prepare-pg2jamopp-map (jamopp2pg jamopp pg "example01"))
        tclass (or (find-tclass pg "example01.ParentClass")
                   (u/error "TClass not found"))
        tmethodsig (or (find-tmethodsig pg "method" ["java.lang.String" "int"])
                       (u/error "TMethodSignature not found"))
        thunk (pull-up-method pg mappings-atom tclass tmethodsig)]
    #_(viz/print-model pg :gtk)
    ;; The ruse must have been applicable
    (is thunk)
    ;; Executing the thunk must work
    (thunk)
    ;; We don't save the resource set in order not to change test-src/
    ))

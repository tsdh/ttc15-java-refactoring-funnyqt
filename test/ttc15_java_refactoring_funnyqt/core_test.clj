(ns ttc15-java-refactoring-funnyqt.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [funnyqt.visualization :as viz]
            [funnyqt.emf :as emf]
            [funnyqt.utils :as u]
            [ttc15-java-refactoring-funnyqt.jamopp    :as jamopp]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.refactor  :refer :all])
  (:import
   (org.eclipse.emf.ecore.resource Resource ResourceSet)
   (org.eclipse.emf.common.util URI)))

(defn print-jamopp-resource [^ResourceSet rs res-name]
  (if-let [r (.getResource rs (URI/createFileURI res-name) true)]
    (viz/print-model r :gtk)
    (println "No such resource" res-name)))

(deftest test-refactor-interactively
  (println "Testing interactive refactoring")
  (let [jamopp (jamopp/parse-directory "test-src")
        pg (emf/new-resource)
        mappings-atom (prepare-pg2jamopp-map (jamopp2pg jamopp pg))]
    (refactor-interactively pg mappings-atom jamopp)
    (viz/print-model pg :gtk)
    (jamopp/save-java-rs jamopp)))

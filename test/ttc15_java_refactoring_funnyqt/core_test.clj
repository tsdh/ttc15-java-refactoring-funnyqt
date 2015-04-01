(ns ttc15-java-refactoring-funnyqt.core-test
  (:require [clojure.test :refer :all]
            [funnyqt.visualization :as viz]
            [funnyqt.emf :as emf]
            [ttc15-java-refactoring-funnyqt.core :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp :as jamopp])
  (:import
   (org.eclipse.emf.ecore.resource Resource ResourceSet)
   (org.eclipse.emf.common.util URI)))

(def test1-jamopp (jamopp/parse-directory "test-src/test1/"))
(def test2-jamopp (jamopp/parse-directory "test-src/test2/"))

(defn print-jamopp-resource [^ResourceSet rs res-name]
  (if-let [r (.getResource rs (URI/createFileURI res-name) true)]
    (viz/print-model r :gtk)))

(defn print-result-pg [rs base-pkg]
  (let [result (emf/new-resource)]
    (jamopp2pg rs result base-pkg)
    (println "The result has" (count (emf/eallcontents result)) "elements.")
    (viz/print-model result :gtk)))

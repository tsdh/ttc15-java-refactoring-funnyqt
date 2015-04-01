(ns ttc15-java-refactoring-funnyqt.core-test
  (:require [clojure.test :refer :all]
            [funnyqt.visualization :as viz]
            [funnyqt.emf :as emf]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp    :as jamopp]
            [ttc15-java-refactoring-funnyqt.refactor  :refer :all])
  (:import
   (org.eclipse.emf.ecore.resource Resource ResourceSet)
   (org.eclipse.emf.common.util URI)))

(def test1-jamopp (jamopp/parse-directory "test-src/test1/"))
(def test2-jamopp (jamopp/parse-directory "test-src/test2/"))
(def test3-jamopp (jamopp/parse-directory "test-src/test3/"))
(def test4-jamopp (jamopp/parse-directory "test-src/test4/"))

(defn print-jamopp-resource [^ResourceSet rs res-name]
  (if-let [r (.getResource rs (URI/createFileURI res-name) true)]
    (viz/print-model r :gtk)))

(defn print-result-pg [rs base-pkg]
  (let [result (emf/new-resource)]
    (jamopp2pg rs result base-pkg)
    (println "The result has" (count (emf/eallcontents result)) "elements and"
             (count (emf/epairs result)) "references.")
    (viz/print-model result :gtk)))

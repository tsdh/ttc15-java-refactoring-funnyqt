(ns ttc15-java-refactoring-funnyqt.main
  (:require [clojure.string :as str]
            [funnyqt.emf :as emf]
            [funnyqt.in-place :as ip]
            [ttc15-java-refactoring-funnyqt.jamopp    :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.refactor  :refer :all])
  (:import (org.eclipse.emf.ecore.resource Resource ResourceSet)))

(defn perform-refactorings [src-dir base-pkg]
  (let [src-dir (if (.endsWith ^String src-dir "/")
                  src-dir
                  (str src-dir "/"))
        ^ResourceSet jamopp (parse-directory src-dir)
        pg (emf/new-resource)
        jamopp2pg-map-atom (atom (jamopp2pg jamopp pg base-pkg))]
    ((ip/iterated-rule pull-up-method) pg jamopp2pg-map-atom)
    (doseq [^Resource r (.getResources jamopp)
            :let [uri (.getURI r)]
            :when (and (.isFile uri)
                       (re-matches (re-pattern (str "^" src-dir
                                                    (str/replace base-pkg \. \/)
                                                    "/.*\\.java$"))
                                   (.path uri)))]
      (emf/save-resource r))))

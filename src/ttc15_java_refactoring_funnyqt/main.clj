(ns ttc15-java-refactoring-funnyqt.main
  (:require [clojure.string   :as str]
            [clojure.set      :as set]
            [funnyqt.emf      :as emf]
            [funnyqt.in-place :as ip]
            [ttc15-java-refactoring-funnyqt.jamopp    :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.refactor  :refer :all]))

(defn prepare-pg2jamopp-map [trace]
  (println (:method2tmethoddef trace))
  (into {} (comp (map #(% trace))
                 (map set/map-invert))
        [:class2tclass :field2tfielddef :method2tmethoddef]))

(defn perform-refactorings [src-dir base-pkg]
  (let [src-dir (if (.endsWith ^String src-dir "/")
                  src-dir
                  (str src-dir "/"))
        jamopp (parse-directory src-dir)
        pg (emf/new-resource)
        trace (jamopp2pg jamopp pg base-pkg)
        pg2jamopp-map-atom (atom (prepare-pg2jamopp-map trace))]
    ((ip/iterated-rule pull-up-method) pg pg2jamopp-map-atom)
    (save-java-rs jamopp src-dir nil #_base-pkg)))

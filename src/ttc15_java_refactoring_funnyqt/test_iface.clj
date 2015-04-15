(ns ttc15-java-refactoring-funnyqt.test-iface
  (:require [funnyqt.emf :as emf]
            [ttc15-java-refactoring-funnyqt.jamopp :refer :all]
            [ttc15-java-refactoring-funnyqt.jamopp2pg :refer :all]
            [ttc15-java-refactoring-funnyqt.refactor :refer :all])
  (:import (java.io File)
           (ttc.testdsl.tTCTest Pull_Up_Refactoring Create_Superclass_Refactoring))
  (:gen-class
   :name ttc15_java_refactoring_funnyqt.TestInterfaceImpl
   :implements [ttc.testsuite.interfaces.TestInterface]
   :constructors {[] []}
   :init init
   :state state))

(defn -init []
  [[] {:permanent-storage-path (atom nil)
       :tmp-path               (atom nil)
       :program-location       (atom nil)
       :jamopp-model           (atom nil)
       :pg-model               (atom nil)
       :synchronizers          (atom [])}])

(defn -getPluginName [this]
  "FunnyQT")

(defn -setPermanentStoragePath [this f]
  (reset! (:permanent-storage-path (.state this)) f))

(defn -setTmpPath [this f]
  (reset! (:tmp-path (.state this)) f))

(defn -setLogPath [this f])

(defn -usesProgramGraph [this]
  true)

(defn -setProgramLocation [this path]
  (reset! (:program-location (.state this)) path)
  (reset! (:jamopp-model (.state this)) (parse-directory path)))

(defn -createProgramGraph [this path]
  (reset! (:pg-model (.state this))
          (jamopp2pg @(:jamopp-model (.state this))
                     (emf/new-resource)
                     path))
  true)

(defn -applyPullUpMethod [this pur]
  true)

(defn -applyCreateSuperclass [this csr]
  true)

(defn -synchronizeChanges [this]
  (doseq [f @(:synchronizers (.state this))]
    (f))
  true)

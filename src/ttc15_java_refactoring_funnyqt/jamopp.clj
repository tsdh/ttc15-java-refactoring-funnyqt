(ns ttc15-java-refactoring-funnyqt.jamopp
  (:require [clojure.java.io :as io]
            [clojure.string  :as str]
            [funnyqt.emf     :as emf])
  (:import
   ;; EMF
   (org.eclipse.emf.ecore EPackage EPackage$Registry EObject InternalEObject)
   (org.eclipse.emf.ecore.util EcoreUtil)
   (org.eclipse.emf.ecore.resource
    Resource ResourceSet Resource$Factory$Registry)
   (org.eclipse.emf.ecore.resource.impl ResourceSetImpl)
   (org.eclipse.emf.common.util URI)
   (org.eclipse.emf.ecore.xmi.impl XMIResourceFactoryImpl)
   ;; Jamopp
   (org.emftext.language.java JavaPackage)
   (org.emftext.language.java.resource JavaSourceOrClassFileResourceFactoryImpl)
   (org.emftext.language.java.resource.java IJavaOptions)))

(defn ^:private set-up []
  (.put (.getExtensionToFactoryMap Resource$Factory$Registry/INSTANCE)
        "java" (JavaSourceOrClassFileResourceFactoryImpl.))
  (.put (.getExtensionToFactoryMap Resource$Factory$Registry/INSTANCE)
        Resource$Factory$Registry/DEFAULT_EXTENSION
        (XMIResourceFactoryImpl.)))

JavaPackage/eINSTANCE
(set-up)

(defn ^:private parse-file [^ResourceSet rs ^java.io.File f]
  (print (format "Parsing file %s... " f))
  (.getResource rs (URI/createFileURI (.getPath f)) true)
  (println "Done"))

(defn parse-directory [dir]
  (let [rs (ResourceSetImpl.)]
    ;; rs.getLoadOptions().put(IJavaOptions.DISABLE_LOCATION_MAP, Boolean.TRUE)
    (.put (.getLoadOptions rs)
          IJavaOptions/DISABLE_LOCATION_MAP
          Boolean/TRUE)
    (.put (.getLoadOptions rs)
          IJavaOptions/DISABLE_LAYOUT_INFORMATION_RECORDING
          Boolean/TRUE)
    (doseq [^java.io.File f (file-seq (io/file dir))
            :when (and (.isFile f)
                       (re-matches #".*\.java$" (.getName f)))]
      (parse-file rs f))
    rs))

(defn save-java-rs [^ResourceSet rs]
  (doseq [^Resource r (.getResources rs)
          :let [uri (.getURI r)]
          :when (and (.isFile uri)
                     (= "java" (.fileExtension uri)))]
    (emf/save-resource r)))

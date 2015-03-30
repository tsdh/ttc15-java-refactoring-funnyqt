(ns ttc15-java-refactoring-funnyqt.jamopp
  (:require [clojure.java.io :as io]
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
  (.put (EPackage$Registry/INSTANCE)
        "http://www.emftext.org/java" JavaPackage/eINSTANCE)
  (.put (.getExtensionToFactoryMap Resource$Factory$Registry/INSTANCE)
        "java" (JavaSourceOrClassFileResourceFactoryImpl.))
  (.put (.getExtensionToFactoryMap Resource$Factory$Registry/INSTANCE)
        Resource$Factory$Registry/DEFAULT_EXTENSION
        (XMIResourceFactoryImpl.)))

(set-up)

(defn ^:private parse-file [^ResourceSet rs ^java.io.File f]
  (print (format "Parsing file %s... " f))
  (.getResource rs (URI/createFileURI (.getPath f)) true)
  (println "Done"))

(defn ^:private resolve-all [^ResourceSet rs]
  (print "Resolving... ")
  (let [proxy-count (volatile! 0), unresolved (volatile! 0)]
    (loop [objs (emf/eallcontents rs)]
      (when (seq objs)
        (let [^InternalEObject eo (first objs)]
          (doseq [^EObject cr (.eCrossReferences eo)]
            (when (.eIsProxy cr)
              (vswap! proxy-count inc)
              (let [^EObject cr (EcoreUtil/resolve cr rs)]
                (when (.eIsProxy cr)
                  (vswap! unresolved inc)))))
          (recur (rest objs)))))
    (println "Done")
    (println (format "Out of %s proxies, %s couldn't be resolved."
                     @proxy-count @unresolved))))

(defn parse-directory [dir]
  (let [rs (ResourceSetImpl.)]
    ;; rs.getLoadOptions().put(IJavaOptions.DISABLE_LOCATION_MAP, Boolean.TRUE)
    (.put (.getLoadOptions rs)
          IJavaOptions/DISABLE_LOCATION_MAP
          Boolean/TRUE)
    (.put (.getLoadOptions rs)
          IJavaOptions/DISABLE_LAYOUT_INFORMATION_RECORDING
          Boolean/TRUE)
    (loop [fs (file-seq (io/file dir))]
      (when (seq fs)
        (let [^java.io.File f (first fs)]
          (when (and (.isFile f)
                     (re-matches #".*\.java$" (.getName f)))
            (parse-file rs f))
          (recur (rest fs)))))
    ;; Seems like all proxies in the user classes are resolved automatically,
    ;; so no need to try it again.
    #_(resolve-all rs)
    rs))

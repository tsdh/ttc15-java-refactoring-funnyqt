(ns ttc15-java-refactoring-funnyqt.core
  (:require [clojure.string      :as str]
            [funnyqt.emf         :as emf]
            [funnyqt.generic     :as g]
            [funnyqt.model2model :as m2m]
            [ttc15-java-refactoring-funnyqt.jamopp :as jamopp])
  (:import (org.emftext.language.java.classifiers
            ConcreteClassifier)
           (org.emftext.language.java.types
            TypeReference PrimitiveType)))

(emf/load-ecore-resource "models/TypeGraphBasic.ecore")

(def ^:dynamic *tg* nil)

(defn get-ref-target [^TypeReference ref]
  (.getTarget ref))

(defn get-type [typed-element]
  (get-ref-target (emf/eget typed-element :typeReference)))

(m2m/deftransformation jamopp2pg [[jamopp] [pg] base-pkg]
  (user-defined?
   [^ConcreteClassifier cc]
   (.startsWith (.getQualifiedName cc) base-pkg))
  (main
   []
   (binding [*tg* (emf/ecreate! pg 'TypeGraph)]
     (doseq [c (emf/eallcontents jamopp 'Class)
             :when (user-defined? c)]
       (class2tclass c))))
  (type-name
   [t]
   (g/type-case t
     ConcreteClassifier (.getQualifiedName ^ConcreteClassifier t)
     NamedElement       (emf/eget t :name)
     PrimitiveType      (str/lower-case (str/replace (.getSimpleName (class t))
                                                     "Impl" ""))))
  (type2tclass
   :from [t 'Type]
   :disjuncts [class2tclass primitive2tclass])
  (class2tclass
   :from [c 'Class]
   :to [tc 'TClass]
   (emf/eadd! *tg* :classes tc)
   (emf/eset! tc :tName (type-name c))
   (when (user-defined? c)
     (when-let [super-ref (emf/eget c :extends)]
       (emf/eset! tc :inheritance (class2tclass (get-ref-target super-ref))))
     (let [tfields (mapv #(field2tfielddef %)
                         (filter (g/type-matcher jamopp 'Field)
                                 (emf/eget c :members)))
           tmethods (mapv #(method2tmethoddef %)
                          (filter (g/type-matcher jamopp 'Method)
                                  (emf/eget c :members)))]
       (println "tfields =" tfields)
       (println "tmethods =" tmethods)
       (emf/eaddall! tc :defines (concat tfields tmethods))
       (emf/eaddall! tc :signature (concat (mapv #(emf/eget % :signature)
                                                 tfields)
                                           (mapv #(emf/eget % :signature)
                                                 tmethods))))))
  (primitive2tclass
   :from [pt 'PrimitiveType]
   :to [tc 'TClass {:tName (type-name pt)}])
  (get-tfield
   :from [f 'Field]
   :id   [name (emf/eget f :name)]
   :to   [tf 'TField {:tName name}]
   (emf/eadd! *tg* :fields tf))
  (get-tfieldsig
   :from [f 'Field]
   :id   [sig (str (type-name (get-type f))
                   (emf/eget f :name))]
   :to   [tfs 'TFieldSignature {:field (get-tfield f)
                                :type  (type2tclass (get-type f))}])
  (field2tfielddef
   :from [f 'Field]
   :to   [tfd 'TFieldDefinition]
   (emf/eset! tfd :signature (get-tfieldsig f)))
  (get-tmethod
   :from [m 'Method]
   :id   [name (emf/eget m :name)]
   :to   [tm 'TMethod {:tName name}]
   (emf/eadd! *tg* :methods tm))
  (get-tmethodsig
   :from [m 'Method]
   :id   [sig (str (type-name (get-type m))
                   (emf/eget m :name)
                   "(" (str/join ", " (map #(type-name (get-type %))
                                           (emf/eget m :parameters))) ")")]
   :to   [tms 'TMethodSignature {:method (get-tmethod m)
                                 :returnType (type2tclass (get-type m))
                                 :paramList (map #(type2tclass (get-type %))
                                                 (emf/eget m :parameters))}])
  (method2tmethoddef
   :from [m 'Method]
   :to   [tmd 'TMethodDefinition]
   (emf/eset! tmd :signature (get-tmethodsig m))))


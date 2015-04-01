(ns ttc15-java-refactoring-funnyqt.jamopp2pg
  (:require [clojure.string      :as str]
            [funnyqt.emf         :refer :all]
            [funnyqt.query       :refer [p-seq p-* p-alt p-restr p-apply]]
            [funnyqt.query.emf   :refer [<>--]]
            [funnyqt.generic     :refer [type-case type-matcher]]
            [funnyqt.model2model :refer [deftransformation]])
  (:import (org.emftext.language.java.classifiers
            ConcreteClassifier)
           (org.emftext.language.java.types
            TypeReference PrimitiveType)))

(load-ecore-resource "models/TypeGraphBasic.ecore")

;;* Task 1: JaMoPP to Program Graph

(def ^:dynamic *tg* nil)

(defn get-ref-target [^TypeReference ref]
  (.getTarget ref))

(defn get-type [typed-element]
  (get-ref-target (eget typed-element :typeReference)))

(defn overridden-or-hidden-def [def sig]
  (let [tc (econtainer def)]
    (loop [super (eget tc :inheritance)]
      (when super
        (or (first (filter #(= sig (eget % :signature))
                           (eget super :defines)))
            (recur (eget super :inheritance)))))))

(deftransformation jamopp2pg [[jamopp] [pg] base-pkg]
  (user-defined?
   [^ConcreteClassifier cc]
   (.startsWith (.getQualifiedName cc) base-pkg))
  (main
   []
   (binding [*tg* (ecreate! pg 'TypeGraph)]
     (doseq [c (filter user-defined?
                       (eallcontents jamopp 'Class))]
       (class2tclass c))
     ;; overloaded methods/hidden fields
     (doseq [tmsig (eallcontents pg 'TSignature)
             tmdef (eget tmsig :definitions)]
       (when-let [stmdef (overridden-or-hidden-def tmdef tmsig)]
         ;; FIXME: The :overridden/:hidden refs shouldn't be multi-valued.
         (eadd! tmdef (type-case tmdef
                        TMethodDefinition :overridden
                        TFieldDefinition  :hidden)
                stmdef)))))
  (type-name
   [t]
   (type-case t
     ConcreteClassifier (.getQualifiedName ^ConcreteClassifier t)
     NamedElement       (eget t :name)
     PrimitiveType      (str/lower-case (str/replace (.getSimpleName (class t))
                                                     "Impl" ""))))
  (type2tclass
   :from [t 'Type]
   :disjuncts [class2tclass primitive2tclass])
  (member2tmemberdef
   :from [m 'Member]
   :disjuncts [field2tfielddef method2tmethoddef])
  (member-accesses
   [m]
   (map member2tmemberdef
        (p-apply m [p-seq
                    [p-alt :statements :initialValue]
                    [p-* <>--]
                    :target])))
  (class2tclass
   :from [c 'Class]
   :to [tc 'TClass]
   (eadd! *tg* :classes tc)
   (eset! tc :tName (type-name c))
   (when (user-defined? c)
     (when-let [super-ref (eget c :extends)]
       (eset! tc :inheritance (class2tclass (get-ref-target super-ref))))
     (let [fields (filter (type-matcher jamopp 'Field)
                          (eget c :members))
           tfields (map #(field2tfielddef %) fields)
           methods (filter (type-matcher jamopp 'Method)
                           (eget c :members))
           tmethods (map #(method2tmethoddef %) methods)]
       (eaddall! tc :defines (concat tfields tmethods))
       (eaddall! tc :signature (concat (map #(eget % :signature)
                                            tfields)
                                       (map #(eget % :signature)
                                            tmethods)))
       (doseq [m (concat fields methods)]
         (eset! (member2tmemberdef m) :access (member-accesses m))))))
  (primitive2tclass
   :from [pt 'PrimitiveType]
   :to [tc 'TClass {:tName (type-name pt)}])
  (get-tfield
   :from [f 'Field]
   :id   [name (eget f :name)]
   :to   [tf 'TField {:tName name}]
   (eadd! *tg* :fields tf))
  (get-tfieldsig
   :from [f 'Field]
   :id   [sig (str (type-name (get-type f))
                   (eget f :name))]
   :to   [tfs 'TFieldSignature {:field (get-tfield f)
                                :type  (type2tclass (get-type f))}])
  (field2tfielddef
   :from [f 'Field]
   :to   [tfd 'TFieldDefinition]
   (eset! tfd :signature (get-tfieldsig f)))
  (get-tmethod
   :from [m 'Method]
   :id   [name (eget m :name)]
   :to   [tm 'TMethod {:tName name}]
   (eadd! *tg* :methods tm))
  (get-tmethodsig
   :from [m 'Method]
   :id   [sig (str (type-name (get-type m))
                   (eget m :name)
                   "(" (str/join ", " (map #(type-name (get-type %))
                                           (eget m :parameters))) ")")]
   :to   [tms 'TMethodSignature {:method (get-tmethod m)
                                 :returnType (type2tclass (get-type m))
                                 :paramList (map #(type2tclass (get-type %))
                                                 (eget m :parameters))}])
  (method2tmethoddef
   :from [m 'Method]
   :to   [tmd 'TMethodDefinition]
   (eset! tmd :signature (get-tmethodsig m))))

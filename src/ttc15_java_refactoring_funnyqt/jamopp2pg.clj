(ns ttc15-java-refactoring-funnyqt.jamopp2pg
  (:require [clojure.string      :as str]
            [clojure.set         :as set]
            [funnyqt.emf         :refer :all]
            [funnyqt.query       :refer [p-seq p-* p-alt p-restr p-apply member?]]
            [funnyqt.query.emf   :refer [<>--]]
            [funnyqt.generic     :refer [type-case type-matcher]]
            [funnyqt.model2model :refer [deftransformation]]
            [funnyqt.utils       :refer [mapc]]
            [ttc15-java-refactoring-funnyqt.mm.java :as j]
            [ttc15-java-refactoring-funnyqt.mm.pg   :as pg])
  (:import (org.emftext.language.java.classifiers
            ConcreteClassifier)
           (org.emftext.language.java.types
            TypeReference PrimitiveType)))

(load-ecore-resource "TypeGraphBasic.ecore")

;;* Task 1: JaMoPP to Program Graph

(def ^:dynamic *tg* nil)

(defn static? [x]
  (seq (filter j/isa-Static? (j/->annotationsAndModifiers x))))

(defn ref-target [^TypeReference ref]
  (.getTarget ref))

(defn get-type [typed-element]
  (if-let [tr (j/->typeReference typed-element)]
    (ref-target tr)
    (funnyqt.utils/errorf "%s has no typeReference!" typed-element)))

(defn type-name [t]
  (type-case t
    ConcreteClassifier (.getQualifiedName ^ConcreteClassifier t)
    NamedElement       (j/name t)
    PrimitiveType      (str/lower-case (str/replace (.getSimpleName (class t)) "Impl" ""))))

(defn user-defined? [cc]
  (-> cc eresource .getURI .isFile))

(deftransformation jamopp2pg [[jamopp] [pg]]
  (main
   []
   (binding [*tg* (pg/create-TypeGraph! pg)]
     (mapc class2tclass (filter user-defined? (j/all-Classes jamopp)))))
  (type2tclass
   :from [t 'Type]
   :disjuncts [class2tclass primitive2tclass])
  (member2tmemberdef
   :from [m 'Member]
   :disjuncts [field2tfielddef method2tmethoddef])
  (class2tclass
   :from [c 'Class]
   :to [tc 'TClass {:tName (type-name c)}]
   (pg/->add-classes! *tg* tc)
   (when (user-defined? c)
     (when-let [super-ref (j/->extends c)]
       (pg/->set-parentClass! tc (class2tclass (ref-target super-ref))))
     (let [fields (remove static? (filter j/isa-Field? (j/->members c)))
           tfields (map #(field2tfielddef %) fields)
           methods (remove static? (filter j/isa-ClassMethod? (j/->members c)))
           tmethods (map #(method2tmethoddef %) methods)]
       (pg/->addall-defines! tc (concat tfields tmethods))
       (pg/->addall-signature! tc (concat (map pg/->signature tfields)
                                          (map pg/->signature tmethods)))
       (doseq [m (concat fields methods)]
         (pg/->set-access! (member2tmemberdef m)
                           (map member2tmemberdef
                                (p-apply m [p-seq
                                            [p-alt :statements :initialValue]
                                            [p-* <>--] :target
                                            [p-restr [:or 'Field 'ClassMethod]
                                             (complement static?)]])))))))
  (primitive2tclass
   :from [pt 'PrimitiveType]
   :id   [name (type-name pt)]
   :to   [tc 'TClass {:tName name}])
  (get-tfield
   :from [f 'Field]
   :id   [name (j/name f)]
   :to   [tf 'TField {:tName name}]
   (pg/->add-fields! *tg* tf))
  (get-tfieldsig
   :from [f 'Field]
   :id   [sig (str (type-name (get-type f)) (j/name f))]
   :to   [tfs 'TFieldSignature {:field (get-tfield f)
                                :type  (type2tclass (get-type f))}])
  (field2tfielddef
   :from [f 'Field]
   :when (not (static? f))
   :to   [tfd 'TFieldDefinition {:signature (get-tfieldsig f)}])
  (get-tmethod
   :from [m 'ClassMethod]
   :id   [name (j/name m)]
   :to   [tm 'TMethod {:tName name}]
   (pg/->add-methods! *tg* tm))
  (get-tmethodsig
   :from [m 'ClassMethod]
   :id   [sig (str (type-name (get-type m))
                   (j/name m)
                   "(" (str/join ", " (map #(type-name (get-type %))
                                           (j/->parameters m))) ")")]
   :to   [tms 'TMethodSignature {:method (get-tmethod m)
                                 :paramList (map #(type2tclass (get-type %))
                                                 (j/->parameters m))}])
  (method2tmethoddef
   :from [m 'ClassMethod]
   :when (not (static? m))
   :to   [tmd 'TMethodDefinition {:returnType (type2tclass (get-type m))
                                  :signature (get-tmethodsig m)}]
   (pg/->add-defines! (class2tclass (econtainer m)) tmd)))

(defn prepare-pg2jamopp-map [trace]
  (atom (into {} (comp (map #(% trace))
                       (map set/map-invert))
              [:class2tclass :field2tfielddef :method2tmethoddef])))

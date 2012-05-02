(ns appengine-magic.services.datastore
  (:use appengine-magic.utils)
  (:import [com.google.appengine.api.datastore DatastoreService DatastoreServiceFactory
            DatastoreServiceConfig DatastoreServiceConfig$Builder
            EntityNotFoundException
            ReadPolicy ReadPolicy$Consistency ImplicitTransactionManagementPolicy
            Key KeyFactory
            Entity
            FetchOptions$Builder
            Query Query$FilterOperator Query$SortDirection]
           ;; types
           [com.google.appengine.api.datastore Blob ShortBlob Text Link]
           com.google.appengine.api.blobstore.BlobKey))



;;; ----------------------------------------------------------------------------
;;; forward declarations
;;; ----------------------------------------------------------------------------

(declare run-after-load)



;;; ----------------------------------------------------------------------------
;;; helper variables and constants
;;; ----------------------------------------------------------------------------

(defonce ^{:dynamic true} *datastore-service* (atom nil))


(defonce ^{:dynamic true} *current-transaction* nil)


(defonce ^{:dynamic true} *datastore-read-policy-map*
  {:eventual ReadPolicy$Consistency/EVENTUAL
   :strong ReadPolicy$Consistency/STRONG})


(defonce ^{:dynamic true} *datastore-implicit-transaction-policy-map*
  {:auto ImplicitTransactionManagementPolicy/AUTO
   :none ImplicitTransactionManagementPolicy/NONE})



;;; ----------------------------------------------------------------------------
;;; datastore type conversion functions
;;; ----------------------------------------------------------------------------

(let [byte-array-class (class (byte-array 0))]

  (defn as-blob [data]
    (cond (instance? Blob data) data
          (instance? byte-array-class data) (Blob. data)
          :else (Blob. (.getBytes data))))

  (defn as-short-blob [data]
    (cond (instance? ShortBlob data) data
          (instance? byte-array-class data) (ShortBlob. data)
          :else (ShortBlob. (.getBytes data)))))


(defn as-blob-key [x]
  (if (instance? BlobKey x)
      x
      (BlobKey. x)))


(defn as-text [x]
  (if (instance? Text x)
      x
      (Text. x)))


(defn as-link [x]
  (if (instance? Link x)
      x
      (Link. x)))



;;; ----------------------------------------------------------------------------
;;; datastore service management functions; use directly if necessary
;;; ----------------------------------------------------------------------------

(defn get-datastore-service []
  (when (nil? @*datastore-service*)
    (reset! *datastore-service* (DatastoreServiceFactory/getDatastoreService)))
  @*datastore-service*)


(defn init-datastore-service [& {:keys [deadline read-policy implicit-transaction-policy]}]
  (let [datastore-config-object (DatastoreServiceConfig$Builder/withDefaults)]
    (when deadline
      (.deadline datastore-config-object deadline))
    (when read-policy
      (.readPolicy
       datastore-config-object
       (ReadPolicy. (get *datastore-read-policy-map* read-policy))))
    (when implicit-transaction-policy
      (.implicitTransactionManagementPolicy
       datastore-config-object
       (get *datastore-implicit-transaction-policy-map* implicit-transaction-policy)))
    (reset! *datastore-service*
            (DatastoreServiceFactory/getDatastoreService datastore-config-object))
    @*datastore-service*))



;;; ----------------------------------------------------------------------------
;;; protocol for dealing with Clojure entity records
;;; ----------------------------------------------------------------------------

(defprotocol EntityProtocol
  "Entities are Clojure records which conform to the EntityProtocol. Each Entity
   must have a key. If an entity record field has a :key metadata tag, then that
   field becomes the key. If a record has no :key metadata tags, then a key is
   automatically generated for it. In either case, the key becomes part of the
   entity's metadata. Entity retrieval operations must set the :key metadata on
   returned entity records.

   In addition, any field may be marked with a ^:clj metadata tag. This tag
   means that the field's value goes through prn-str on its way out and through
   read-string on its way in. It allows automatic serialization for some types
   not directly supported by the datastore."
  (get-clj-properties [this]
    "Returns a set of all properties which pass through the Clojure reader on
     their way in and out of the datastore.")
  (get-key-object [this] [this parent]
    "Returns nil if no tag is specified in the record definition, and no :key
     metadata exists. Otherwise returns a Key object. Specify optional entity
     group parent.")
  (get-entity-object [this]
    "Returns a datastore Entity object instance for the record.")
  (run-after-load [this]
    "Invokes the after-load callback.")
  (save! [this]
    "Writes the given entity to the data store."))



;;; ----------------------------------------------------------------------------
;;; helper functions; do not use these directly
;;; ----------------------------------------------------------------------------

(defn- unqualified-name [sym]
  (let [s (str sym)
        last-slash (.lastIndexOf s "/")]
    (.substring (str s) (inc (if (neg? last-slash)
                                 (.lastIndexOf s ".")
                                 last-slash)))))


(defn- coerce-key-value-type [key-value]
  (if (integer? key-value) (long key-value) key-value))


(defn- coerce-java-type [v]
  (cond (instance? java.util.ArrayList v) (into [] v)
        (instance? java.util.Map v) (into {} v)
        (instance? java.util.Set v) (into #{} v)
        :else v))


(defn- coerce-clojure-type [v]
  (let [to-java-hashmap (fn [m]
                          (let [jhm (java.util.HashMap.)]
                            (doseq [[k v] m] (.put jhm k v))
                            jhm))
        to-java-hashset (fn [s]
                          (let [jhs (java.util.HashSet.)]
                            (doseq [v s] (.add jhs v))
                            jhs))]
   (cond (instance? clojure.lang.APersistentMap v) (to-java-hashmap v) ; broken in GAE 1.3.7
         (instance? clojure.lang.APersistentSet v) (to-java-hashset v) ; broken in GAE 1.3.7
         (extends? EntityProtocol (class v)) (get-key-object v)
	 (or (instance? clojure.lang.PersistentList v)
	     (instance? clojure.lang.PersistentVector v))
	 (map #(if (extends? EntityProtocol (class %)) (get-key-object %) %) v) ; ReferenceList support
         :else v)))


(defn- coerce-to-key-seq [any-seq]
  (map #(if (instance? Key %) % (get-key-object %)) any-seq))


(defn get-key-object-helper [entity-record key-property kind parent]
  (let [entity-record-metadata (meta entity-record)
        metadata-key-value (when entity-record-metadata (:key entity-record-metadata))
        key-property-value (coerce-key-value-type
                            (when key-property (key-property entity-record)))]
    (cond
     ;; neither exists: autogenerate
     (and (nil? key-property-value) (nil? metadata-key-value))
     nil
     ;; metadata key exists
     (and (not (nil? metadata-key-value)) (instance? Key metadata-key-value))
     metadata-key-value
     ;; key property exists
     (not (nil? key-property-value))
     (if parent
         (if (instance? Key parent)
             (KeyFactory/createKey parent kind key-property-value)
             (KeyFactory/createKey (get-key-object parent) kind key-property-value))
         (KeyFactory/createKey kind key-property-value))
     ;; something's wrong
     :else (throw (RuntimeException.
                   "entity has no valid :key metadata, and has no fields marked :key")))))


(defn get-entity-object-helper [entity-record kind before-save]
  (let [entity-record (before-save entity-record)
        key-object (get-key-object entity-record)
        clj-properties (get-clj-properties entity-record)
        entity-meta (meta entity-record)
        entity (cond key-object (Entity. key-object)
                     (contains? entity-meta :parent) (Entity. kind (:parent entity-meta))
                     :else (Entity. kind))]
    (doseq [[property-kw value] entity-record]
      (.setProperty entity (name property-kw) (if (contains? clj-properties property-kw)
                                                  (Text. (prn-str value))
                                                  (coerce-clojure-type value))))
    entity))


(defn save!-helper [entity-record]
  (let [new-key (.put (get-datastore-service) (get-entity-object entity-record))]
    (with-meta entity-record (merge (meta entity-record) {:key new-key}))))


(defn- save-many-helper! [entity-record-seq]
  (let [entities (map get-entity-object entity-record-seq)
        new-keys (.put (get-datastore-service) entities)]
    (map (fn [e k]
           (with-meta e (merge (meta e) {:key k})))
         entity-record-seq new-keys)))



;;; ----------------------------------------------------------------------------
;;; query helper objects and functions; do not use these directly
;;; ----------------------------------------------------------------------------

(defrecord QueryFilter [operator property value])


(defrecord QuerySort [property direction])


(defn- make-query-object [kind ancestor filters sorts keys-only?]
  (let [kind (cond (nil? kind) kind
                   (string? kind) kind
                   (extends? EntityProtocol kind) (unqualified-name kind)
                   :else (throw (RuntimeException. "invalid kind specified in query")))
        ancestor-key-object (cond (instance? Key ancestor) ancestor
                                  (extends? EntityProtocol
                                            (class ancestor)) (get-key-object ancestor)
                                  :else nil)
        query-object (cond (and (nil? kind) (nil? ancestor-key-object)) (Query.)
                           (nil? kind) (Query. ancestor-key-object)
                           (nil? ancestor-key-object) (Query. kind)
                           :else (Query. kind ancestor-key-object))]
    (when keys-only?
      (.setKeysOnly query-object))
    ;; prepare filters
    (doseq [current-filter filters]
      (let [filter-operator (:operator current-filter)
            filter-property-kw (:property current-filter)
            filter-value (:value current-filter)]
        (cond
         ;; valid filter provided
         (and (not (nil? filter-operator))
              (not (nil? filter-property-kw))
              (not (nil? filter-value))
              (keyword? filter-property-kw))
         (let [filter-property (name filter-property-kw)
               filter-value (if (extends? EntityProtocol (class filter-value))
                                (get-key-object filter-value)
                                filter-value)]
           (.addFilter query-object filter-property filter-operator filter-value))
         ;; no filter definition
         (and (nil? filter-operator) (nil? filter-property-kw) (nil? filter-value))
         nil
         ;; invalid filter
         :else (throw (RuntimeException. "invalid filter specified in query")))))
    ;; prepare sorts
    (doseq [current-sort sorts]
      (let [sort-property-kw (:property current-sort)
            sort-direction (:direction current-sort)]
        (cond
         ;; valid sort provided
         (and (not (nil? sort-property-kw))
              (not (nil? sort-direction))
              (keyword? sort-property-kw))
         (.addSort query-object (name sort-property-kw) sort-direction)
         ;; no sort definition
         (and (nil? sort-property-kw) (nil? sort-direction))
         nil
         ;; invalid sort
         :else (throw (RuntimeException. "invalid sort specified in query")))))
    query-object))


(defn- make-fetch-options-object [limit offset prefetch-size chunk-size]
  (let [fetch-options-object (FetchOptions$Builder/withDefaults)]
    (when limit (.limit fetch-options-object limit))
    (when offset (.offset fetch-options-object offset))
    (when prefetch-size (.prefetchSize fetch-options-object prefetch-size))
    (when chunk-size (.chunkSize fetch-options-object chunk-size))
    fetch-options-object))


(defn- entity->properties [raw-properties clj-properties]
  (reduce (fn [m [k v]]
            (let [k (keyword k)]
             (assoc m k (if (contains? clj-properties k)
                            (binding [*read-eval* false]
                              (read-string (.getValue v)))
                            (coerce-java-type v)))))
          {}
          raw-properties))



;;; ----------------------------------------------------------------------------
;;; user functions and macros
;;; ----------------------------------------------------------------------------

(defn- retrieve-helper [entity-record-type key-value-or-values &
                        {:keys [parent kind]
                         :or {kind (unqualified-name (.getName entity-record-type))}}]
  (let [make-key-from-value (fn [key-value real-parent]
                              (cond
                               ;; already a Key object
                               (instance? Key key-value) key-value
                               ;; parent provided
                               real-parent
                               (KeyFactory/createKey (get-key-object real-parent)
                                                     kind
                                                     (coerce-key-value-type key-value))
                               ;; no parent provided
                               :else
                               (KeyFactory/createKey kind
                                                     (coerce-key-value-type key-value))))]
    (if (sequential? key-value-or-values)
        ;; handles sequences of values
        (let [key-objects (map (fn [kv] (if (sequential? kv)
                                            (make-key-from-value (first kv) (second kv))
                                            (make-key-from-value kv nil)))
                               key-value-or-values)
              entities (.get (get-datastore-service) key-objects)
              model-record (record entity-record-type)]
          (map #(let [v (.getValue %)]
                  (with-meta
                    (run-after-load
                     (merge model-record
                            (entity->properties (.getProperties v)
                                                (get-clj-properties model-record))))
                    {:key (.getKey v)}))
               entities))
        ;; handles singleton values
        (let [key-object (make-key-from-value key-value-or-values parent)
              entity (.get (get-datastore-service) key-object)
              raw-properties (into {} (.getProperties entity))
              entity-record (record entity-record-type)]
          (with-meta
            (run-after-load
              (merge entity-record (entity->properties raw-properties
                                                      (get-clj-properties entity-record))))
            {:key (.getKey entity)})))))


(defn retrieve [entity-record-type key-value-or-values &
                {:keys [parent kind]
                 :or {kind (unqualified-name (.getName entity-record-type))}}]
  (try
    (retrieve-helper entity-record-type key-value-or-values :parent parent :kind kind)
    ;; XXX: Clojure 1.2.x only:
    (catch EntityNotFoundException _ nil)
    ;; XXX: Clojure 1.3.x:
    (catch Exception ex (if (isa? (class (.getCause ex)) EntityNotFoundException)
                            nil
                            (throw ex)))))


(defn exists? [entity-record-type key-value-or-values &
               {:keys [parent kind]
                :or {kind (unqualified-name (.getName entity-record-type))}}]
  (not (nil? (retrieve entity-record-type key-value-or-values :parent parent :kind kind))))


(defn delete! [target]
  (let [target (if (sequential? target) target [target])
        key (coerce-to-key-seq target)]
    (.delete (get-datastore-service) key)))


(defmacro defentity [name properties &
                     {:keys [kind before-save after-load]
                      :or {kind (unqualified-name name)
                           before-save identity
                           after-load identity}}]
  ;; TODO: Clojure 1.3: Remove the ugly Clojure version check.
  (let [clj13? (fn [] (and (= 1 (:major *clojure-version*))
                           (= 3 (:minor *clojure-version*))))
        key-property-name (if (clj13?)
                              (first (filter #(contains? (meta %) :key) properties))
                              (first (filter #(= (:tag (meta %)) :key) properties)))
        ;; TODO: Clojure 1.3: Remove unnecessary call to str.
        key-property (if key-property-name (keyword (str key-property-name)) nil)
        ;; XXX: The keyword and str composition below works
        ;; around a weird Clojure bug (see
        ;; http://groups.google.com/group/clojure-dev/browse_thread/thread/655f6e7d1b312f17).
        ;; TODO: This bug is fixed in Clojure 1.3 after alpha4 came out (see
        ;; http://dev.clojure.org/jira/browse/CLJ-693).
        clj-properties (if (clj13?)
                           (set (map (comp keyword str)
                                     (filter #(contains? (meta %) :clj) properties)))
                           (set (map (comp keyword str)
                                     (filter #(= (:tag (meta %)) :clj) properties))))]
    `(defrecord ~name ~properties
       EntityProtocol
       (get-clj-properties [this#]
         ~clj-properties)
       (get-key-object [this#]
         (get-key-object-helper this# ~key-property ~kind nil))
       (get-key-object [this# parent#]
         (get-key-object-helper this# ~key-property ~kind parent#))
       (get-entity-object [this#]
         (get-entity-object-helper this# ~kind ~before-save))
       (run-after-load [this#]
         (~after-load this#))
       (save! [this#]
         (save!-helper this#)))))


(defentity EntityBase [])


(extend-type Iterable
  EntityProtocol
  (save! [this] (save-many-helper! this)))


;;; TODO: new* is a mistake. Its current implementation prevents property-values
;;; from being evaluated and used if the argument is a variable, or the result
;;; of a function call. Unfortunately, undoing this is not easy. The following
;;; implementation, though it superficially works, is both dangerous (because it
;;; uses eval), and does not pass tests.
;;
;; (defmacro new* [entity-record-type property-values & {:keys [parent]}]
;;   `(let [property-values# ~property-values
;;          entity# (cond (map? property-values#) (record ~entity-record-type property-values#)
;;                   (vector? property-values#) (eval `(new ~~entity-record-type ~@property-values#)))
;;          parent# ~parent]
;;      (if (nil? parent#)
;;          entity#
;;          (with-meta entity# {:key (get-key-object entity# parent#)
;;                              :parent (get-key-object parent#)}))))
;;
;;; The key difficulty is in vectors stored in variables which contain the
;;; record's slot values in order. Dealing with the vector value would require
;;; the equivalent of (apply new ~entity-record-type ~property-values), but
;;; apply cannot be used on macros or special forms.
;;;
;;; The correct solution to this problem is to deprecate new* and encourage
;;; anyone who needs entity group creation to use an alternative function which
;;; populates the :parent metadata correctly.
(defmacro new* [entity-record-type property-values & {:keys [parent]}]
  (let [props-expr (cond (vector? property-values) `(new ~entity-record-type ~@property-values)
                         (map? property-values) `(record ~entity-record-type ~property-values)
                         :else (throw (IllegalArgumentException. "bad argument to new*")))]
    `(let [entity# ~props-expr
           parent# ~parent]
       (if (nil? parent#)
           entity#
           (with-meta entity# {:key (get-key-object entity# parent#)
                               :parent (get-key-object parent#)})))))


;;; Note that the code relies on the API's implicit transaction tracking
;;; wherever possible, but the *current-transaction* value is still used for
;;; query construction.
(defmacro with-transaction [& body]
  `(binding [*current-transaction* (.beginTransaction (get-datastore-service))]
     (try
       (let [body-result# (do ~@body)]
         (.commit *current-transaction*)
         body-result#)
       (catch Throwable err#
         (do (.rollback *current-transaction*)
             (throw err#))))))


(defn query-helper [kind ancestor filters sorts keys-only?
                    count-only? in-transaction?
                    limit offset
                    start-cursor end-cursor
                    prefetch-size chunk-size
                    entity-record-type]
  (let [query-object (make-query-object kind ancestor filters sorts keys-only?)
        fetch-options-object (make-fetch-options-object limit offset prefetch-size chunk-size)
        prepared-query (if (and in-transaction? *current-transaction*)
                           (.prepare (get-datastore-service) *current-transaction* query-object)
                           (.prepare (get-datastore-service) query-object))
        result-type (if (and (instance? Class kind) (extends? EntityProtocol kind))
                        kind
                        entity-record-type)]
    (if count-only?
      (.countEntities prepared-query fetch-options-object)
      (let [results (seq (.asIterable prepared-query fetch-options-object))
            model-record (if result-type
                             ;; we know this type; good
                             (record result-type)
                             ;; unknown type; just use a basic EntityProtocol
                             (EntityBase.))]
        (map #(with-meta
                (run-after-load
                 (merge model-record
                        (entity->properties (.getProperties %)
                                            (get-clj-properties model-record))))
                {:key (.getKey %)})
             results)))))


(defmacro query
  "TODO: Document this better.
   :kind - Either a Clojure entity record type, or a string naming a datastore
     entity kind. If this is a string, :entity-record-type must be given, must
     be an entity record type, and will contain the results of the query.
   :entity-record-type - Unless :kind is given and is an entity record type,
     will contain the results of the query. Otherwise, the type of :kind is
     used."
  [& {:keys [kind ancestor filter sort keys-only?
             count-only? in-transaction?
             limit offset
             start-cursor end-cursor ; TODO: Implement this.
             prefetch-size chunk-size
             entity-record-type]
      :or {keys-only? false, filter [], sort [],
           count-only? false, in-transaction? false}}]
  ;; Normalize :filter and :sort keywords (into lists, even if only one is given),
  ;; then turn them into QueryFilter and QuerySort objects.
  (let [filter (if (every? sequential? filter) filter (vector filter))
        filter `(list ~@(map (fn [[op k v]] `(list (keyword '~op) ~k ~v)) filter))
        sort (if (sequential? sort) sort (vector sort))]
    `(let [filter# (map (fn [[sym# prop-kw# prop-val#]]
                          (QueryFilter. (condp = sym#
                                            := Query$FilterOperator/EQUAL
                                            :> Query$FilterOperator/GREATER_THAN
                                            :>= Query$FilterOperator/GREATER_THAN_OR_EQUAL
                                            :in Query$FilterOperator/IN
                                            :< Query$FilterOperator/LESS_THAN
                                            :<= Query$FilterOperator/LESS_THAN_OR_EQUAL
                                            :! Query$FilterOperator/NOT_EQUAL
                                            :!= Query$FilterOperator/NOT_EQUAL
                                            :<> Query$FilterOperator/NOT_EQUAL)
                                        prop-kw# prop-val#))
                        ~filter)
           sort# (map (fn [sort-spec#]
                        (if (sequential? sort-spec#)
                            (let [[sort-property# sort-dir-spec#] sort-spec#
                                  sort-dir# (condp = sort-dir-spec#
                                                :asc Query$SortDirection/ASCENDING
                                                :ascending Query$SortDirection/ASCENDING
                                                :dsc Query$SortDirection/DESCENDING
                                                :desc Query$SortDirection/DESCENDING
                                                :descending Query$SortDirection/DESCENDING)]
                              (QuerySort. sort-property# sort-dir#))
                            (QuerySort. sort-spec# Query$SortDirection/ASCENDING)))
                      ~sort)]
       (query-helper ~kind ~ancestor filter# sort# ~keys-only?
                     ~count-only? ~in-transaction?
                     ~limit ~offset
                     ~start-cursor ~end-cursor
                     ~prefetch-size ~chunk-size
                     ~entity-record-type))))


(defn- get-key-str-helper [key]
  (let [str-key (str key)]
    (if (empty? str-key)
        (throw (IllegalArgumentException.
                (str "get-key-str must be called on an object with a datastore key, "
                     "i.e., an object already persisted with save!")))
        str-key)))


(defn key-str
  "Given an object, or a kind and an object, returns a string representation of
   the object's key, e.g., \"User(10)\". It must be called after an object
   already has acquired a key. The kind may be a string representing a datastore
   kind, or an entity record defined with defentity. This function provides a
   useful general-purpose mechanism for determining a unique identifier for a
   datastore entity. Note that the resulting key string cannot, by itself, be
   used for datastore queries. It is likely to be more helpful for saving
   entities in, e.g., memcache.

   The object argument can be the result of a KeyFactory/keyToString call, an
   existing Key, or an existing entity record instance.

   > (key-str \"ahNhcHBlbmdpbmUtbWFnaWMtYXBwcgoLEgRVc2VyGAgM\")
   \"User(8)\"
   > (key-str User 8)
   \"User(8)\"
   > (key-str Person \"alice@example.com\")
   \"Person(\"alice@example.com\")\"
   > (key-str user-object)
   \"User(8)\""
  ([obj]
     (let [key (cond
                ;; an entity; use its existing key
                (extends? EntityProtocol (class obj))
                (get-key-object obj)
                ;; already a Key; use it
                (instance? Key obj)
                obj
                ;; a string; make a Key
                (string? obj)
                (KeyFactory/stringToKey obj))]
       (get-key-str-helper key)))
  ([kind obj]
     (let [kind (cond
                 ;; already a string
                 (string? kind)
                 kind
                 ;; probably an entity class
                 (class? kind)
                 (unqualified-name kind)
                 ;; no clue
                 :else (throw (IllegalArgumentException. "unsupported kind argument type")))
           key (cond
                ;; an entity; use its existing key
                (extends? EntityProtocol (class obj))
                (get-key-object obj)
                ;; already a Key; use it
                (instance? Key obj)
                obj
                ;; something else
                :else
                (KeyFactory/createKey kind (coerce-key-value-type obj)))]
       (get-key-str-helper key))))


(defn key-id [entity]
  (when entity
    (.getId (get-key-object entity))))


(defn key-name [entity]
  (when entity
    (.getName (get-key-object entity))))


(defn key-kind [entity]
  (when entity
    (.getKind (get-key-object entity))))

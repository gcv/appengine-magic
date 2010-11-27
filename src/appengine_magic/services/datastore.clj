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
;;; helper variables and constants
;;; ----------------------------------------------------------------------------

(defonce *datastore-service* (atom nil))


(defonce *current-transaction* nil)


(defonce *datastore-read-policy-map*
  {:eventual ReadPolicy$Consistency/EVENTUAL
   :strong ReadPolicy$Consistency/STRONG})


(defonce *datastore-implicit-transaction-policy-map*
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
   returned entity records."
  (get-key-object [this] [this parent]
    "Returns nil if no tag is specified in the record definition, and no :key
     metadata exists. Otherwise returns a Key object. Specify optional entity
     group parent.")
  (get-entity-object [this]
    "Returns a datastore Entity object instance for the record.")
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


(defn get-entity-object-helper [entity-record kind]
  (let [key-object (get-key-object entity-record)
        entity (if key-object
                   (Entity. key-object)
                   (Entity. kind))]
    (doseq [[property-kw value] entity-record]
      (let [property-name (.substring (str property-kw) 1)]
        (.setProperty entity property-name (coerce-clojure-type value))))
    entity))


(defn save!-helper [entity-record]
  (.put (get-datastore-service) (get-entity-object entity-record)))


(defn- save-many-helper! [entity-record-seq]
  (let [entities (map get-entity-object entity-record-seq)]
    (.put (get-datastore-service) entities)))



;;; ----------------------------------------------------------------------------
;;; query helper objects and functions
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
         (let [filter-property (.substring (str filter-property-kw) 1)
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
         (let [sort-property (.substring (str sort-property-kw) 1)]
           (.addSort query-object sort-property sort-direction))
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
    (when prefetch-size (.prefetchSize prefetch-size))
    (when chunk-size (.chunkSize chunk-size))
    fetch-options-object))


(defn- entity->properties [raw-properties]
  (reduce (fn [m [k v]]
            (assoc m
              (keyword k)
              (coerce-java-type v)))
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
                    (merge model-record (entity->properties (.getProperties v)))
                    {:key (.getKey v)}))
               entities))
        ;; handles singleton values
        (let [key-object (make-key-from-value key-value-or-values parent)
              entity (.get (get-datastore-service) key-object)
              raw-properties (into {} (.getProperties entity))
              entity-record (record entity-record-type)]
          (with-meta
            (merge entity-record (entity->properties raw-properties))
            {:key (.getKey entity)})))))


(defn retrieve [entity-record-type key-value-or-values &
                {:keys [parent kind]
                 :or {kind (unqualified-name (.getName entity-record-type))}}]
  (try
    (retrieve-helper entity-record-type key-value-or-values :parent parent :kind kind)
    (catch EntityNotFoundException _ nil)))


(defn exists? [entity-record-type key-value-or-values &
               {:keys [parent kind]
                :or {kind (unqualified-name (.getName entity-record-type))}}]
  (not (nil? (retrieve entity-record-type key-value-or-values :parent parent :kind kind))))


(defn delete! [target]
  (let [target (if (sequential? target) target [target])
        key (coerce-to-key-seq target)]
    (.delete (get-datastore-service) key)))


(defmacro defentity [name properties &
                     {:keys [kind]
                      :or {kind (unqualified-name name)}}]
  (let [key-property-name (first (filter #(= (:tag (meta %)) :key) properties))
        key-property (if key-property-name (keyword (str key-property-name)) nil)]
    `(defrecord ~name ~properties
       EntityProtocol
       (get-key-object [this#]
         (get-key-object-helper this# ~key-property ~kind nil))
       (get-key-object [this# parent#]
         (get-key-object-helper this# ~key-property ~kind parent#))
       (get-entity-object [this#]
         (get-entity-object-helper this# ~kind))
       (save! [this#]
         (save!-helper this#)))))


(defentity EntityBase [])


(extend-type Iterable
  EntityProtocol
  (save! [this] (save-many-helper! this)))


(defmacro new* [entity-record-type property-values & {:keys [parent]}]
  `(let [parent# ~parent
         entity# (new ~entity-record-type ~@property-values)]
     (if (nil? parent#)
         entity#
         (with-meta entity# {:key (get-key-object entity# parent#)}))))


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


(defn query* [kind ancestor filters sorts keys-only?
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
                        entity-record-type)
        result-count (.countEntities prepared-query)]
    (cond count-only? result-count
          (zero? result-count) (list)
          :else (let [results (seq (.asIterable prepared-query fetch-options-object))
                      model-record (if result-type
                                       ;; we know this type; good
                                       (record result-type)
                                       ;; unknown type; just use a basic EntityProtocol
                                       (EntityBase.))]
                  (map #(with-meta
                          (merge model-record (entity->properties (.getProperties %)))
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
  ;; Normalize :filter and :sort keywords (into lists, even if only one is gen),
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
       (query* ~kind ~ancestor filter# sort# ~keys-only?
               ~count-only? ~in-transaction?
               ~limit ~offset
               ~start-cursor ~end-cursor
               ~prefetch-size ~chunk-size
               ~entity-record-type))))

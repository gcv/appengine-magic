(ns appengine-magic.services.datastore
  (:import [com.google.appengine.api.datastore DatastoreService DatastoreServiceFactory
            DatastoreServiceConfig DatastoreServiceConfig$Builder
            ReadPolicy ReadPolicy$Consistency ImplicitTransactionManagementPolicy
            Key KeyFactory
            Entity
            FetchOptions$Builder
            Query Query$FilterOperator Query$SortDirection]))


(defonce *datastore-service* (atom nil))
(defonce *current-transaction* nil)


(defonce *datastore-read-policy-map*
  {:eventual ReadPolicy$Consistency/EVENTUAL
   :strong ReadPolicy$Consistency/STRONG})


(defonce *datastore-implicit-transaction-policy-map*
  {:auto ImplicitTransactionManagementPolicy/AUTO
   :none ImplicitTransactionManagementPolicy/NONE})


(defonce *filter-operator-map*
  {:eq Query$FilterOperator/EQUAL
   :gt Query$FilterOperator/GREATER_THAN
   :ge Query$FilterOperator/GREATER_THAN_OR_EQUAL
   :in Query$FilterOperator/IN
   :lt Query$FilterOperator/LESS_THAN
   :le Query$FilterOperator/LESS_THAN_OR_EQUAL
   :ne Query$FilterOperator/NOT_EQUAL})


(defonce *sort-direction-map*
  {:ascending Query$SortDirection/ASCENDING
   :asc Query$SortDirection/ASCENDING
   :descending Query$SortDirection/DESCENDING
   :dsc Query$SortDirection/DESCENDING
   :desc Query$SortDirection/DESCENDING})


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
  (save! [this]
    "Writes the given entity to the data store."))


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


(defn get-key-object-helper [entity-record key-property kind &
                             {:keys [parent]}]
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


(defn save!-helper [entity-record kind]
  (let [key-object (get-key-object entity-record)
        entity (if key-object
                   (Entity. key-object)
                   (Entity. kind))]
    (doseq [[property-kw value] entity-record]
      (let [property-name (.substring (str property-kw) 1)]
        (.setProperty entity property-name (coerce-clojure-type value))))
    (.put (get-datastore-service) entity)))


(defn retrieve [entity-record-type key-value &
                {:keys [parent kind]
                 :or {kind (unqualified-name (.getName entity-record-type))}}]
  (let [key-object (if parent
                       (KeyFactory/createKey (get-key-object parent)
                                             kind
                                             (coerce-key-value-type key-value))
                       (KeyFactory/createKey kind
                                             (coerce-key-value-type key-value)))
        entity (.get (get-datastore-service) key-object)
        raw-properties (into {} (.getProperties entity))
        properties (reduce (fn [m [k v]]
                             (assoc m
                               (keyword k)
                               (coerce-java-type v)))
                           {}
                           raw-properties)
        ;; XXX: No good choice but to use eval here. No way to know the number
        ;; of arguments to the record constructor at compile-time, and no clean
        ;; way to access any custom constructor defined by defentity, since that
        ;; constructor would be in a different namespace.
        entity-record (eval `(new ~entity-record-type ~@(repeat (count raw-properties) nil)))]
    (with-meta (merge entity-record properties) {:key (.getKey entity)})))


(defn delete! [target]
  (let [target (if (sequential? target) target [target])
        key (if (every? #(instance? Key %) target)
                target
                (map get-key-object target))]
    (.delete (get-datastore-service) key)))


(defmacro defentity [name properties &
                     {:keys [kind]
                      :or {kind (unqualified-name name)}}]
  (let [key-property-name (first (filter #(= (:tag (meta %)) :key) properties))
        key-property (if key-property-name (keyword (str key-property-name)) nil)]
    `(defrecord ~name ~properties
       EntityProtocol
       (get-key-object [this#]
         (get-key-object-helper this# ~key-property ~kind))
       (get-key-object [this# parent#]
         (get-key-object-helper this# ~key-property ~kind :parent parent#))
       (save! [this#]
         (save!-helper this# ~kind)))))


(defmacro new* [entity-record-type property-values & {:keys [parent]}]
  (if parent
      `(let [parent# ~parent
             entity# (new ~entity-record-type ~@property-values)]
         (if (nil? parent#)
             entity#
             (with-meta entity# {:key (get-key-object entity# parent#)})))))


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


(defn- make-query-object [kind ancestor filter sort keys-only?]
  (let [kind (cond (nil? kind) kind
                   (string? kind) kind
                   (extends? EntityProtocol kind) (unqualified-name kind)
                   :else (throw (RuntimeException. "invalid kind specified in query")))
        ancestor-key-object (cond (instance? Key ancestor) ancestor
                                  (extends? EntityProtocol ancestor) (get-key-object ancestor)
                                  :else nil)
        query-object (cond (and (nil? kind) (nil? ancestor-key-object)) (Query.)
                           (nil? kind) (Query. ancestor-key-object)
                           (nil? ancestor-key-object) (Query. kind)
                           :else (Query. kind ancestor-key-object))
        ;; normalize filter criteria into a vector (even if there's just one)
        filter (if (every? sequential? filter) filter (vector filter))
        ;; normalize sort criteria into a vector (even if there's just one)]
        sort (if (every? sequential? sort) sort (vector sort))]
    (when keys-only?
      (.setKeysOnly query-object))
    (doseq [[filter-operator filter-property-kw filter-value] filter]
      (cond
       ;; valid filter provided
       (and (not (nil? filter-operator))
            (not (nil? filter-property-kw))
            (not (nil? filter-value))
            (keyword? filter-property-kw))
       (let [op-object (get *filter-operator-map* filter-operator)
             filter-property (.substring (str filter-property-kw) 1)]
         (.addFilter query-object filter-property op-object filter-value))
       ;; no filter definition
       (and (nil? filter-operator) (nil? filter-property-kw) (nil? filter-value))
       nil
       ;; invalid filter
       :else (throw (RuntimeException. "invalid filter specified in query"))))
    (doseq [[sort-property-kw sort-direction] sort]
      (cond
       ;; valid sort provided
       (and (not (nil? sort-property-kw))
            (not (nil? sort-direction))
            (keyword? sort-property-kw))
       (let [sort-property (.substring (str sort-property-kw) 1)
             sort-direction-object (get *sort-direction-map* sort-direction)]
         (.addSort query-object sort-property sort-direction-object))
       ;; no sort definition
       (and (nil? sort-property-kw) (nil? sort-direction))
       nil
       ;; invalid sort
       :else (throw (RuntimeException. "invalid sort specified in query"))))
    query-object))


(defn query [& {:keys [kind ancestor filter sort keys-only?
                       count-only? in-transaction?
                       limit offset
                       start-cursor end-cursor ; TODO: Implement this.
                       prefetch-size chunk-size]
                :or {keys-only? false, filter [[]], sort [[]],
                     count-only? false, in-transaction? false}}]
  (let [query-object (make-query-object kind ancestor filter sort keys-only?)
        fetch-options-object (FetchOptions$Builder/withDefaults)]
    (when limit
      (.limit fetch-options-object limit))
    (when offset
      (.offset fetch-options-object offset))
    (when prefetch-size
      (.prefetchSize prefetch-size))
    (when chunk-size
      (.chunkSize chunk-size))
    (let [prepared-query (if (and in-transaction? *current-transaction*)
                             (.prepare (get-datastore-service) *current-transaction*
                                       query-object)
                             (.prepare (get-datastore-service) query-object))]
      (if count-only?
          (.countEntities prepared-query)
          (seq (.asIterable prepared-query fetch-options-object))))))

(ns appengine-magic.services.datastore
  (:import [com.google.appengine.api.datastore DatastoreService DatastoreServiceFactory
            Key KeyFactory KeyRange
            Entity]))


(defonce *default-datastore-service* (atom nil))


(defn- get-datastore-service [& {:keys [service]}]
  (if (nil? service)
      (do (when (nil? @*default-datastore-service*)
            (reset! *default-datastore-service* (DatastoreServiceFactory/getDatastoreService)))
          @*default-datastore-service*)
      service))


(defn- unqualified-name [sym]
  (let [s (str sym)
        last-slash (.lastIndexOf s "/")]
    (.substring (str s) (inc (if (neg? last-slash)
                                 (.lastIndexOf s ".")
                                 last-slash)))))


(defprotocol EntityProtocol
  "Entities are Clojure records which conform to the EntityProtocol. Each Entity
   must have a key. If an entity record field has a :key metadata tag, then that
   field becomes the key. If a record has no :key metadata tags, then a key is
   automatically generated for it. In either case, the key becomes part of the
   entity's metadata. Entity retrieval operations must set the :key metadata on
   returned entity records."
  (get-key-object [this] "Returns nil if no tag is specified in the record
                          definition, and no :key metadata exists. Otherwise
                          returns a Key object.")
  (get-key-value [this]  "Returns the Clojure value of the entity's Key object.")
  (save! [this]          "Saves the entity to the datastore."))


(defn entity-get-key-object-helper [entity-record parent key-property datastore-entity-name]
  ;; TODO: Handle parent entities.
  (let [entity-record-metadata (meta entity-record)
        metadata-key-value (when entity-record-metadata (:key entity-record-metadata))
        key-property-value (when key-property (key-property entity-record))]
    (cond
     ;; neither exists: autogenerate
     (and (nil? key-property-value) (nil? metadata-key-value))
     nil
     ;; key property exists
     (not (nil? key-property-value))
     (KeyFactory/createKey datastore-entity-name key-property-value)
     ;; metadata key exists
     (and (not (nil? metadata-key-value)) (instance? Key metadata-key-value))
     metadata-key-value
     ;; something's wrong
     :else (throw (RuntimeException. "entity has no valid :key metadata, and has no fields marked :key")))))


(defn entity-get-key-value-helper [entity-record]
  (let [key-object (get-key-object entity-record)]
    (when key-object
      (if-let [name (.getName key-object)]
        name
        (.getId key-object)))))


(defn entity-save-helper [entity-record datastore-entity-name]
  (let [key-object (get-key-object entity-record)
        entity (if key-object
                   (Entity. key-object)
                   (Entity. datastore-entity-name))]
    (doseq [[property-kw value] entity-record]
      (let [property-name (.substring (str property-kw) 1)]
        (.setProperty entity property-name value)))
    ;; TODO: Allow optional :service argument!
    (.put (get-datastore-service) entity)))


(defn retrieve [entity-record-type key-value &
                {:keys [parent datastore-entity-name]
                 :or {datastore-entity-name (unqualified-name (.getName entity-record-type))}}]
  ;; TODO: Allow optional :service argument!
  ;; TODO: Deal with parent entities.
  (let [key-object (KeyFactory/createKey datastore-entity-name (if (integer? key-value)
                                                                   (long key-value)
                                                                   key-value))
        entity (.get (get-datastore-service) key-object)
        properties (into {} (.getProperties entity))
        kw-keys-properties (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} properties)
        ;; XXX: No good choice but to use eval here. No way to know the number
        ;; of arguments to the record constructor at compile-time, and no clean
        ;; way to access any custom constructor defined by defentity, since that
        ;; constructor would be in a different namespace.
        entity-record (eval `(new ~entity-record-type ~@(repeat (count properties) nil)))]
    (with-meta (merge entity-record kw-keys-properties) {:key (.getKey entity)})))


(defmacro defentity [name properties &
                     {:keys [parent datastore-entity-name]
                      :or {datastore-entity-name (unqualified-name name)}}]
  (let [key-property-name (first (filter #(= (:tag (meta %)) :key) properties))
        key-property (if key-property-name (keyword (str key-property-name)) nil)]
    `(defrecord ~name ~properties
       EntityProtocol
       (get-key-object [this#]
         (entity-get-key-object-helper this# ~parent ~key-property ~datastore-entity-name))
       (get-key-value [this#]
         (entity-get-key-value-helper this#))
       (save! [this#]
         (entity-save-helper this# ~datastore-entity-name)))))


;; - Is type metadata necessary, or can it be determined at runtime?
;;   It helps with optional checking, improves error messages.
;;   Verify type behavior in general, e.g., dates.
;; - Implement transactions.


;; (query
;;  :filter (filter-operator filter-property filter-value) ; can be a vector of filters
;;  :sort (property direction)                             ; can specify multiples
;;  :keys-only true                                        ; default is false
;;  :kind "Person")

(ns appengine-magic.services.datastore
  (:import [com.google.appengine.api.datastore DatastoreService DatastoreServiceFactory
            Key KeyFactory KeyRange
            Entity]))


(defonce *datastore-service* (atom nil))

(defonce *current-transaction* nil)


(defn get-datastore-service []
  (when (nil? @*datastore-service*)
    (reset! *datastore-service* (DatastoreServiceFactory/getDatastoreService)))
  @*datastore-service*)


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
  (save! [this] [this parent]
    "Writes the given entity to the data store. Specify optional entity group
    parent."))


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
         (KeyFactory/createKey (get-key-object parent) kind key-property-value)
         (KeyFactory/createKey kind key-property-value))
     ;; something's wrong
     :else (throw (RuntimeException.
                   "entity has no valid :key metadata, and has no fields marked :key")))))


(defn save!-helper [entity-record kind &
                    {:keys [parent]}]
  (let [key-object (if parent
                       (get-key-object entity-record parent)
                       (get-key-object entity-record))
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
         (save!-helper this# ~kind))
       (save! [this# parent#]
         (save!-helper this# ~kind :parent parent#)))))


;;; Note that the code relies on the API's implicit transaction tracking, and
;;; the *current-transaction* value is not used. Making it available just in
;;; case.
(defmacro with-transaction [& body]
  `(binding [*current-transaction* (.beginTransaction (get-datastore-service))]
     (try
       (let [body-result# (do ~@body)]
         (.commit *current-transaction*)
         body-result#)
       (catch Throwable err#
         (do (.rollback *current-transaction*)
             (throw err#))))))

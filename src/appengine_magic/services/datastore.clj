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
  (create-key [this])
  (save! [this]))


(defn entity-key-helper [entity-record parent key-info datastore-entity-name]
  ;; TODO: Deal with parent entities.
  (let [key-info (key-info entity-record)
        key-object (if key-info
                       (KeyFactory/createKey datastore-entity-name key-info)
x                       ;; TODO: This looks really broken. Always 1?
                       (.getStart (KeyRange. parent datastore-entity-name 1 1)))]
    (clojure.pprint/pprint key-object)
    key-object))


(defn entity-save-helper [entity-record]
  (let [key-object (create-key entity-record)
        entity (Entity. key-object)]
    (doseq [[property-kw value] entity-record]
      (let [property-name (.substring (str property-kw) 1)]
        (.setProperty entity property-name value)))
    ;; TODO: Allow optional :service argument!
    (.put (get-datastore-service) entity)))


(defn retrieve [entity-record-type key-info &
                {:keys [parent datastore-entity-name]
                 :or {datastore-entity-name (unqualified-name (.getName entity-record-type))}}]
  ;; TODO: Allow optional :service argument!
  (let [key-object (KeyFactory/createKey datastore-entity-name key-info)
        entity (.get (get-datastore-service) key-object)
        properties (into {} (.getProperties entity))
        kw-keys-properties (reduce (fn [m [k v]] (assoc m (keyword k) v)) {} properties)
        ;; XXX: No good choice but to use eval here. No way to know the number
        ;; of arguments to the record constructor at compile-time, and no clean
        ;; way to access any custom constructor defined by defentity, since that
        ;; constructor would be in a different namespace.
        entity-record (eval `(new ~entity-record-type ~@(repeat (count properties) nil)))]
    (merge entity-record kw-keys-properties)))


(defmacro defentity [name properties &
                     {:keys [parent key-fn datastore-entity-name]
                      :or {datastore-entity-name (unqualified-name name)}}]
  ;; All entities need keys. Verify that exactly one of the properties has a
  ;; :key metadata tag, or a :key-fn optional parameter is given. :key metadata
  ;; takes precedence over :key-fn.
  ;; TODO: This ain't checking for :tag :key!
  (let [key-info (or (keyword (str (first (filter #(meta %) properties))))
                     key-fn)]
    (when-not key-info
      (throw (RuntimeException. "either provide ^:key on one property, or a :key-fn")))
    `(defrecord ~name ~properties
       EntityProtocol
       (create-key [this#] (entity-key-helper this# ~parent ~key-info ~datastore-entity-name))
       (save! [this#] (entity-save-helper this#)))))


;; - Is type metadata necessary, or can it be determined at runtime?
;;   It helps with optional checking, improves error messages.
;; - Either give a ^:key metadata to a property, or provide a key-fn.
;; - Implement transactions.


;; (query
;;  :filter (filter-operator filter-property filter-value) ; can be a vector of filters
;;  :sort (property direction)                             ; can specify multiples
;;  :keys-only true                                        ; default is false
;;  :kind "Person")

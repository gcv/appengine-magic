(ns appengine-magic.services.memcache
  (:refer-clojure :exclude (contains? get))
  (:use [appengine-magic.utils :only [record]])
  (:require [appengine-magic.core :as core]
            [appengine-magic.services.datastore :as ds])
  (:import [com.google.appengine.api.memcache MemcacheService MemcacheServiceFactory
            MemcacheService$SetPolicy]
           appengine_magic.services.datastore.EntityProtocol))


(defonce *memcache-service* (atom nil))
(defonce *namespaced-memcache-services* (atom {}))


(defonce *policy-type-map*
  {:always MemcacheService$SetPolicy/SET_ALWAYS
   :add-if-not-present MemcacheService$SetPolicy/ADD_ONLY_IF_NOT_PRESENT
   :replace-only MemcacheService$SetPolicy/REPLACE_ONLY_IF_PRESENT})


(defn get-memcache-service [& {:keys [namespace]}]
  (if (nil? namespace)
      (do (when (nil? @*memcache-service*)
            (reset! *memcache-service* (MemcacheServiceFactory/getMemcacheService)))
          @*memcache-service*)
      (let [s (@*namespaced-memcache-services* namespace)]
        (if-not (nil? s)
            s
            ((swap! *namespaced-memcache-services* assoc
                    namespace (MemcacheServiceFactory/getMemcacheService namespace))
             namespace)))))


(defrecord Statistics [bytes-returned-for-hits
                       hit-count
                       item-count
                       max-time-without-access
                       miss-count
                       total-item-bytes])


(defn statistics [& {:keys [namespace]}]
  (let [stats (.getStatistics (get-memcache-service :namespace namespace))]
    (Statistics. (.getBytesReturnedForHits stats)
                 (.getHitCount stats)
                 (.getItemCount stats)
                 (.getMaxTimeWithoutAccess stats)
                 (.getMissCount stats)
                 (.getTotalItemBytes stats))))


(defn clear-all!
  "Clears the entire cache. Does not respect namespaces!"
  []
  (.clearAll (get-memcache-service)))


(defn contains? [key & {:keys [namespace]}]
  (.contains (get-memcache-service :namespace namespace) key))


(defn delete!
  "If (sequential? key-or-keys), deletes in batch."
  [key-or-keys & {:keys [namespace millis-no-readd]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if millis-no-readd
        (if (sequential? key-or-keys)
            (.deleteAll service key-or-keys millis-no-readd)
            (.delete service key-or-keys millis-no-readd))
        (if (sequential? key-or-keys)
            (.deleteAll service key-or-keys)
            (.delete service key-or-keys)))))


(defn- to-entity-cast [value]
  (if (and (= :interactive (core/appengine-environment-type))
           (instance? EntityProtocol value))
      (let [obj-meta (merge (meta value) {:type (.getName (class value))})
            obj-map (into {} value)]
        (with-meta obj-map obj-meta))
      value))


(defn- to-entity-cast-many [value-map]
  (if (= :interactive (core/appengine-environment-type))
      (into {} (map (fn [[k v]] [k (to-entity-cast v)]) value-map))
      value-map))


(defn- from-entity-cast [value]
  (if (and (= :interactive (core/appengine-environment-type))
           (not (nil? (meta value)))
           (clojure.core/contains? (meta value) :type))
      (let [claimed-class (Class/forName (:type (meta value)))]
        (with-meta (record claimed-class value) (dissoc (meta value) :type)))
      value))


(defn- from-entity-cast-many [value-map]
  (if (= :interactive (core/appengine-environment-type))
      (into {} (map (fn [[k v]] [k (from-entity-cast v)]) value-map))
      (into {} value-map)))


(defn get
  "If (sequential? key-or-keys), returns values as a map."
  [key-or-keys & {:keys [namespace]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if (sequential? key-or-keys)
        (from-entity-cast-many (.getAll service key-or-keys))
        (from-entity-cast (.get service key-or-keys)))))


(defn put! [key value & {:keys [namespace expiration policy]
                         :or {policy :always}}]
  (let [service (get-memcache-service :namespace namespace)
        policy (*policy-type-map* policy)]
    (.put service key (to-entity-cast value) expiration policy)))


(defn put-map! [values & {:keys [namespace expiration policy]
                          :or {policy :always}}]
  (let [service (get-memcache-service :namespace namespace)
        policy (*policy-type-map* policy)]
    (.putAll service (to-entity-cast-many values) expiration policy)))


(defn increment!
  "If (sequential? key-or-keys), increment each key by the delta."
  [key-or-keys delta & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (if (sequential? key-or-keys)
            (.incrementAll service key-or-keys delta (long initial))
            (.increment service key-or-keys delta (long initial)))
        (if (sequential? key-or-keys)
            (.incrementAll service key-or-keys delta)
            (.increment service key-or-keys delta)))))


(defn increment-map! [values & {:keys [namespace initial]}]
  (let [service (get-memcache-service :namespace namespace)]
    (if initial
        (.incrementAll service values (long initial))
        (.incrementAll service values))))

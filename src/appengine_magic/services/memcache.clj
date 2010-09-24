(ns appengine-magic.services.memcache
  (:refer-clojure :exclude (contains? get))
  (:import [com.google.appengine.api.memcache MemcacheService MemcacheServiceFactory]))


(defonce *default-memcache-service* (atom nil))
(defonce *namespaced-memcache-services* (atom {}))


(defn- get-memcache-service [& {:keys [service namespace]}]
  (cond (and (nil? service) (nil? namespace))
          (do (when (nil? @*default-memcache-service*)
                (reset! *default-memcache-service* (MemcacheServiceFactory/getMemcacheService)))
              @*default-memcache-service*)
        (not (nil? service))
          service
        (not (nil? namespace))
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


(defn statistics [& {:keys [service namespace]}]
  (let [service (get-memcache-service :service service :namespace namespace)
        stats (.getStatistics service)]
    (Statistics. (.getBytesReturnedForHits stats)
                 (.getHitCount stats)
                 (.getItemCount stats)
                 (.getMaxTimeWithoutAccess stats)
                 (.getMissCount stats)
                 (.getTotalItemBytes stats))))


(defn clear-all
  "Clears the entire cache. Does not respect namespaces!"
  [& {:keys [service namespace]}]
  (let [service (get-memcache-service :service service :namespace namespace)]
    (.clearAll service)))


(defn contains? [key & {:keys [service namespace]}]
  (let [service (get-memcache-service :service service :namespace namespace)]
    (.contains service key)))


(defn delete
  "If (sequential? key), deletes in batch."
  [key & {:keys [service namespace millis-no-readd]}]
  (let [service (get-memcache-service :service service :namespace namespace)]
    (if millis-no-readd
        (if (sequential? key)
            (.deletAll service key millis-no-readd)
            (.delete service key millis-no-readd))
        (if (sequential? key)
            (.deleteAll service key)
            (.delete service key)))))


(defn get
  "If (sequential? key), returns values as a map."
  [key & {:keys [service namespace]}]
  (let [service (get-memcache-service :service service :namespace namespace)]
    (if (sequential? key)
        (into {} (.getAll service key))
        (.get service key))))


(defn put [key value & {:keys [service namespace]}]
  (let [service (get-memcache-service :service service :namespace namespace)]
    (.put service key value)))

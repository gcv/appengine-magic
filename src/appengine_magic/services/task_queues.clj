(ns appengine-magic.services.task-queues
  (:require [appengine-magic.services.datastore :as ds])
  (:import java.util.Date
           [com.google.appengine.api.taskqueue Queue QueueFactory
            TaskOptions$Builder TaskOptions$Method]))


(defonce ^{:dynamic true} *default-queue* (atom nil))
(defonce ^{:dynamic true} *named-queues* (atom {}))


(defonce ^{:dynamic true} *task-http-methods*
  {:post TaskOptions$Method/POST
   :delete TaskOptions$Method/DELETE
   :get TaskOptions$Method/GET
   :head TaskOptions$Method/HEAD
   :put TaskOptions$Method/PUT})


(defn get-task-queue [& {:keys [queue]}]
  (if (nil? queue)
      (do (when (nil? @*default-queue*)
            (reset! *default-queue* (QueueFactory/getDefaultQueue)))
          @*default-queue*)
      (let [q (@*named-queues* queue)]
        (if-not (nil? q)
            q
            ((swap! *named-queues* assoc
                    queue (QueueFactory/getQueue queue))
             queue)))))


;; TODO: Support adding multiple tasks at once.
(defn add! [& {:keys [queue url task-name
                      join-current-transaction? params headers payload method
                      countdown-ms eta-ms eta]
               :or {join-current-transaction? false
                    params {}
                    headers {}
                    method :post}}]
  (when (or (nil? url) (not (string? url)) (= "" (.trim url)))
    (throw (IllegalArgumentException. "add! requires a :url argument")))
  (when-not (map? params)
    (throw (IllegalArgumentException. "add! :params must be a map")))
  (let [queue-obj (get-task-queue :queue queue)
        opts (TaskOptions$Builder/withUrl url)]
    ;; headers
    (doseq [[header-name header-value] headers]
      (.header opts header-name header-value))
    ;; task naming
    (when task-name
      (.taskName opts task-name))
    ;; params
    (doseq [[param-name param-value] params]
      (.param opts
              (name param-name)
              (cond
               (string? param-value) param-value
               (instance? (class (byte-array 0)) param-value) param-value
               :else (str param-value))))
    ;; HTTP method for hitting task
    (.method opts (*task-http-methods* method))
    ;; payload
    (cond
     ;; nothing, no problem
     (nil? payload) nil
     ;; just a string
     (string? payload)
     (.payload opts payload)
     ;; string with a charset, or a byte array with a Content-Type
     (and (sequential? payload) (= 2 (count payload)))
     (.payload opts (first payload) (second payload))
     ;; something's wrong
     :else (throw (IllegalArgumentException. "add! :payload invalid")))
    ;; scheduling
    (cond
     ;; more than one given, blow up
     (> (count (remove nil? [countdown-ms eta-ms eta])) 1)
     (throw (IllegalArgumentException. "add! only supports one scheduling parameter at a time"))
     ;; direct countdown
     (not (nil? countdown-ms))
     (.countdownMillis opts (long countdown-ms))
     ;; direct time given in milliseconds
     (not (nil? eta-ms))
     (.etaMillis opts (long eta-ms))
     ;; direct time given, incorrectly
     (and (not (nil? eta)) (not (instance? Date eta)))
     (throw (IllegalArgumentException. "add! :eta requires a java.util.Date argument"))
     ;; direct time given, correctly, as a Date object
     (not (nil? eta))
     (.etaMillis opts (long (.getTime eta)))
     ;; nothing given, no problem
     :else nil)
    ;; transactions and done
    (if join-current-transaction?
        (.add queue-obj ds/*current-transaction* opts)
        (.add queue-obj opts))))


(defn purge! [& {:keys [queue]}]
  (let [queue-obj (get-task-queue :queue queue)]
    (.purge queue-obj)))


;; TODO: Support deleting multiple tasks at once.
(defn delete! [task & {:keys [queue]}]
  (let [queue-obj (get-task-queue :queue queue)]
    (.deleteTask queue-obj task)))

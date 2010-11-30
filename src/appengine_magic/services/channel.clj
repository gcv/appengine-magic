(ns appengine-magic.services.channel
  (:refer-clojure :exclude [send])
  (:import [com.google.appengine.api.channel ChannelServiceFactory ChannelMessage]))


(defonce *channel-service* (atom nil))


(defn get-channel-service []
  (when (nil? @*channel-service*)
    (reset! *channel-service* (ChannelServiceFactory/getChannelService)))
  @*channel-service*)


(defn create-channel
  "Returns a channel ID."
  [^String channel-group-key]
  (.createChannel (get-channel-service) channel-group-key))


(defn make-message [^String channel-group-key, ^String message]
  (ChannelMessage. channel-group-key message))


(defn send
  ([^ChannelMessage message]
     (.sendMessage (get-channel-service) message))
  ([^String channel-group-key, ^String message]
     (send (make-message channel-group-key message))))

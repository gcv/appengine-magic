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
  [channel-group-key]
  (.createChannel (get-channel-service) channel-group-key))


(defn make-message [channel-group-key message]
  (ChannelMessage. channel-group-key message))


(defn send [message]
  (.sendMessage (get-channel-service) message))

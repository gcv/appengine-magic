(ns appengine-magic.services.images
  (:require [appengine-magic.services.datastore :as ds])
  (:import [com.google.appengine.api.images ImagesService ImagesServiceFactory Image
            OutputSettings]
           com.google.appengine.api.blobstore.BlobKey))


(defonce *images-service* (atom nil))


(defonce *output-formats*
  {:jpg ImagesService$OutputEncoding/JPEG
   :jpeg ImagesService$OutputEncoding/JPEG
   :png ImagesService$OutputEncoding/PNG})


(defn get-images-service []
  (when (nil? @*images-service*)
    (reset! *images-service* (ImagesServiceFactory/getImagesService)))
  @*images-service*)


(defn transform [image-arg transform &
                 {:keys [async? format quality height width]
                  :or {async false
                       format :jpeg}}]
  (let [^Image image (cond
                      ;; a byte array
                      (instance? (class (byte-array 0)) image-arg)
                      (ImagesServiceFactory/makeImage image-arg)
                      ;; a blob
                      (or (string? image-arg) (instance? BlobKey image-arg))
                      (ImagesServiceFactory/makeImageFromBlob (ds/as-blob-key image-arg))
                      ;; blow up
                      :else (throw (IllegalArgumentException.
                                    "a source image must be a blob key or a byte array")))
        starting-height (.getHeight image)
        starting-width (.getWidth image)
        ^OutputSettings settings (OutputSettings. (*output-formats* format))]
    (when-not (nil? quality)
      (.setQuality settings quality))
    ;; TODO: basic transforms
    ;; TODO: async
    ;; TODO: composites (use height and width keywords, default to starting values if omitted)
    ;; TODO: composite canvas colors
    ))


(defn histogram [^Image image]
  ;; TODO: Implement this.
  )


(defn serving-url [blob-key & {:keys [size crop?] :or {crop? false}}]
  (let [blob-key (ds/as-blob-key blob-key)]
    (if-not (nil? size)
        (.getServingUrl blob-key size crop?)
        (.getServingUrl blob-key))))

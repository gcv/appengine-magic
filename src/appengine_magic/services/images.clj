(ns appengine-magic.services.images
  (:use [appengine-magic.utils :only [derefify-future]])
  (:require [appengine-magic.services.datastore :as ds])
  (:import [com.google.appengine.api.images ImagesService ImagesServiceFactory Image
            OutputSettings ImagesService$OutputEncoding Composite$Anchor]
           com.google.appengine.api.blobstore.BlobKey))



;;; ----------------------------------------------------------------------------
;;; helpers
;;; ----------------------------------------------------------------------------

(defonce ^{:dynamic true} *images-service* (atom nil))


(defonce ^{:dynamic true} *output-formats*
  {:jpg ImagesService$OutputEncoding/JPEG
   :jpeg ImagesService$OutputEncoding/JPEG
   :png ImagesService$OutputEncoding/PNG})


;; (defonce ^{:dynamic true} *composite-anchor*
;;   {:bottom Composite$Anchor/BOTTOM_CENTER
;;    :bottom-left Composite$Anchor/BOTTOM_LEFT
;;    :bottom-right Composite$Anchor/BOTTOM_RIGHT
;;    :center Composite$Anchor/CENTER_CENTER
;;    :center-left Composite$Anchor/CENTER_LEFT
;;    :center-right Composite$Anchor/CENTER_RIGHT
;;    :top Composite$Anchor/TOP_CENTER
;;    :top-left Composite$Anchor/TOP_LEFT
;;    :top-right Composite$Anchor/TOP_RIGHT})


(defrecord ImageHistogram [red green blue])


(defn get-images-service []
  (when (nil? @*images-service*)
    (reset! *images-service* (ImagesServiceFactory/getImagesService)))
  @*images-service*)


(defn get-image [image-arg]
  (cond
   ;; already an image
   (instance? Image image-arg)
   image-arg
   ;; a byte array
   (instance? (class (byte-array 0)) image-arg)
   (ImagesServiceFactory/makeImage image-arg)
   ;; a blob reference
   (or (string? image-arg) (instance? BlobKey image-arg))
   (ImagesServiceFactory/makeImageFromBlob (ds/as-blob-key image-arg))
   ;; blow up
   :else (throw (IllegalArgumentException.
                 "a source image must be a blob key or a byte array"))))



;;; ----------------------------------------------------------------------------
;;; transformations
;;; ----------------------------------------------------------------------------

(defn crop* [left-x top-y right-x bottom-y]
  (ImagesServiceFactory/makeCrop (double left-x) (double top-y)
                                 (double right-x) (double bottom-y)))


(defn im-feeling-lucky* []
  (ImagesServiceFactory/makeImFeelingLucky))


(defn resize* [width height]
  (ImagesServiceFactory/makeResize (int width) (int height)))


(defn rotate* [degrees-clockwise]
  (ImagesServiceFactory/makeRotate (int degrees-clockwise)))


(defn horizontal-flip* []
  (ImagesServiceFactory/makeHorizontalFlip))


(defn vertical-flip* []
  (ImagesServiceFactory/makeVerticalFlip))



;;; ----------------------------------------------------------------------------
;;; main interface
;;; ----------------------------------------------------------------------------

(defn transform [image-arg transforms &
                 {:keys [async? format quality]
                  :or {async? false
                       format :jpeg}}]
  (let [^Image image (get-image image-arg)
        ^OutputSettings settings (OutputSettings. (*output-formats* format))
        transforms (if (nil? transforms) [] transforms)
        n-transforms (count transforms)]
    (when-not (nil? quality)
      (.setQuality settings quality))
    ;; run it
    (cond
     ;; just one, use it directly
     (= 1 (count transforms))
     (if async?
         (derefify-future (.applyTransformAsync (get-images-service)
                                                (first transforms) image settings))
         (.applyTransform (get-images-service) (first transforms) image settings))
     ;; multiples
     :else
     (if async?
         (derefify-future (.applyTransformAsync
                           (get-images-service)
                           (ImagesServiceFactory/makeCompositeTransform transforms)
                           image
                           settings))
         (.applyTransform (get-images-service)
                          (ImagesServiceFactory/makeCompositeTransform transforms)
                          image
                          settings)))))


(defn histogram [image-arg]
  (let [^Image image (get-image image-arg)
        raw (.histogram (get-images-service) image)
        [red green blue] raw]
    (ImageHistogram. (vec red) (vec green) (vec blue))))


(defn serving-url [blob-key & {:keys [size crop?] :or {crop? false}}]
  (let [blob-key (ds/as-blob-key blob-key)]
    (if-not (nil? size)
        (.getServingUrl (get-images-service) blob-key size crop?)
        (.getServingUrl (get-images-service) blob-key))))

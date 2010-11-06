(ns appengine-magic.services.blobstore
  (:require [appengine-magic.core :as core])
  (:import [com.google.appengine.api.blobstore ByteRange BlobKey
            BlobstoreService BlobstoreServiceFactory]
           [javax.servlet.http HttpServletRequest HttpServletResponse]))


(defonce *blobstore-service* (atom nil))


(defn get-blobstore-service []
  (when (nil? @*blobstore-service*)
    (reset! *blobstore-service* (BlobstoreServiceFactory/getBlobstoreService)))
  @*blobstore-service*)


(defn upload-url [success-path]
  (.createUploadUrl (get-blobstore-service) success-path))


(defn delete! [& args]
  ;; TODO: Implement this.
  )


(defn fetch-data [^:BlobKey blob-key, start-index, end-index]
  (.fetchData (get-blobstore-service) blob-key start-index end-index))


(defn byte-range [^:HttpServletRequest request]
  (.getByteRange (get-blobstore-service) request))


(defn- make-blob-key [x]
  (if (instance? BlobKey x)
      x
      (BlobKey. x)))


(defn- serve-helper
  ([blob-key, ^:HttpServletResponse response]
     (.serve (get-blobstore-service) (make-blob-key blob-key) response))
  ([blob-key, start, end, ^:HttpServletResponse response]
     (.serve (get-blobstore-service) (make-blob-key blob-key) (ByteRange. start end) response)))


(defn serve [request blob-key]
  (serve-helper blob-key (:response request))
  ;; This returns a special Ring response map. The serve-helper primes the HTTP
  ;; response object, but this response must not be committed by the running servlet.
  {:commit? false})


(defn callback-complete [request destination]
  (.sendRedirect (:response request) destination)
  {:commit? false})


(if (core/in-appengine-interactive-mode?)
    (load "blobstore_local")
    (load "blobstore_google"))

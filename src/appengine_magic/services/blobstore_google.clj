(in-ns 'appengine-magic.services.blobstore)


(defn uploaded-blobs [^:HttpServletRequest request]
  (into {} (.getUploadedBlobs (get-blobstore-service) request)))

(in-ns 'appengine-magic.services.blobstore)


(defn uploaded-blobs [ring-request-map]
  (let [^:HttpServletRequest request (:request ring-request-map)
        raw-uploaded-blobs (slurp (.getInputStream request))
        uploaded-blobs (read-string raw-uploaded-blobs)
        processed-blobs (reduce (fn [acc [upload-name blob-key-str]]
                                  (assoc acc upload-name (BlobKey. blob-key-str)))
                                {}
                                uploaded-blobs)]
    processed-blobs))

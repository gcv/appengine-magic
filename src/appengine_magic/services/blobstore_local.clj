(in-ns 'appengine-magic.services.blobstore)


(defn uploaded-blobs [^:HttpServletRequest request]
  {(.getHeader request "X-AppEngine-BlobUpload-Name")
   (BlobKey. (.getHeader request "X-AppEngine-BlobUpload-BlobKey"))})

;;; This code is adapted from Ring (http://github.com/mmcgrana/ring) and patches
;;; by Adam Blinkinsop
;;; (https://github.com/coonsta/compojure/commit/dd36e217de2ea968eca1953a0b9d5a81b54d0d9c).
;;;
;;; Required change from Ring: use of the streaming multipart API instead of the
;;; temporary file API from Apache Commons FileUpload.


(ns appengine-magic.multipart-params
  (:import [org.apache.commons.fileupload.servlet ServletFileUpload]
           [org.apache.commons.fileupload.util Streams]
           org.apache.commons.io.IOUtils))


(defn- multipart-form?
  "Does a request have a multipart form?"
  [request]
  (ServletFileUpload/isMultipartContent (:request request)))


(defn- itemiterator-to-seq
  "Converts an ItemIterator into a sequence."
  [it]
  (lazy-seq (when (.hasNext it)
              (cons (.next it) (itemiterator-to-seq it)))))


(defn- field-seq
  "Map field names to values, which will either be a simple string or map.
   Multipart values will be maps with content-type, name (original filename),
   and stream (an open input stream object)."
  [request encoding]
  (into {}
        (map (fn [i]
               [(.getFieldName i)
                (if (.isFormField i)
                    (Streams/asString (.openStream i) encoding)
                    (let [upload-bytes (IOUtils/toByteArray (.openStream i))]
                      {:content-type (.getContentType i)
                       :filename (.getName i)
                       :size (alength upload-bytes)
                       :bytes upload-bytes}))])
             (itemiterator-to-seq (.getItemIterator (ServletFileUpload.)
                                                    (:request request))))))


(defn wrap-multipart-params
  "Works just like ring.middleware.multipart-params/wrap-multipart-params:
   adds :multipart-params and :params to the request map (the latter requires
   ring.middleware.params/wrap-params). Takes a map with an optional :encoding
   map."
  [handler & [opts]]
  (fn [request]
    (let [encoding (or (:encoding opts)
                       (:character-encoding request)
                       "UTF-8")
          params (if (multipart-form? request)
                     (field-seq request encoding)
                     {})
          request (merge-with merge request
                              {:multipart-params params}
                              {:params params})]
      (handler request))))

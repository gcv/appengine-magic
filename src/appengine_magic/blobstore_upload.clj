(ns appengine-magic.blobstore-upload
  (:require [appengine-magic.services.datastore :as ae-ds]
            [clojure.java.io :as io])
  (:import java.io.File
           [java.net URL HttpURLConnection]
           com.google.appengine.api.datastore.KeyFactory
           com.google.appengine.api.blobstore.BlobKey))


(ae-ds/defentity BlobInfo [^:key blob-key, content_type, creation, filename, size]
  :kind "__BlobInfo__")

(ae-ds/defentity BlobUploadSession [success_path] ; XXX: underscore (_), not hyphen (-)
  :kind "__BlobUploadSession__")


(defn- make-clean-uuid []
  (.replaceAll (str (java.util.UUID/randomUUID)) "-" ""))


;; {symphony #<BlobKey <BlobKey: -2IhXr2qCjiwVvE069nsvQ>>}

(defn- hit-callback [req upload-name blob-info success-path]
  (let [url (URL. "http" (:server-name req) (:server-port req) success-path)
        cxn (cast HttpURLConnection (.openConnection url))]
    (doto cxn
      (.setDoOutput true)
      (.setRequestMethod "POST")
      (.setUseCaches false)
      (.setInstanceFollowRedirects false)
      (.setRequestProperty "Content-Type" "text/plain")
      (.setRequestProperty "X-AppEngine-BlobUpload" "true")
      (.setRequestProperty "X-AppEngine-BlobUpload-Name" upload-name)
      (.setRequestProperty "X-AppEngine-BlobUpload-BlobKey" (:blob-key blob-info)))
    (doseq [header ["User-Agent" "Cookie" "Origin" "Referer"]]
      (let [lc-header (.toLowerCase header)]
        (.setRequestProperty cxn header (get (:headers req) lc-header))))
    (.connect cxn)
    (let [resp-code (.getResponseCode cxn)
          headers (reduce (fn [acc [header-key header-value]]
                            (let [hv (into [] header-value)
                                  hv (if (= 1 (count hv)) (first hv) hv)]
                              (when-not (nil? header-key)
                                (assoc acc header-key hv))))
                          {}
                          (.getHeaderFields cxn))]
      (when-not (= 302 resp-code)
        (throw (RuntimeException. "An upload callback must return a redirect.")))
      (.sendRedirect (:response req) (headers "Location"))
      {:commit? false})))


(defn make-blob-upload-handler [war-root]
  (let [web-inf-dir (File. war-root "WEB-INF")
        appengine-generated-dir (File. web-inf-dir "appengine-generated")]
    (fn [req]
      (let [uri (:uri req)
            key-string (.substring uri (inc (.lastIndexOf uri "/")))
            key-object (KeyFactory/stringToKey key-string)
            upload-session (ae-ds/retrieve BlobUploadSession key-object
                                           :kind "__BlobUploadSession__")
            [upload-name upload-info] (first (:multipart-params req))
            {:keys [filename size content-type tempfile]} upload-info
            blob-key (make-clean-uuid)
            blob-info (BlobInfo. blob-key content-type (java.util.Date.) filename size)
            blob-file (File. appengine-generated-dir blob-key)]
        (io/copy tempfile blob-file)
        (ae-ds/delete! upload-session)
        (ae-ds/save! blob-info)
        (let [resp (hit-callback req upload-name blob-info (:success_path upload-session))]
          ;; just return it to the user's browser
          resp)))))

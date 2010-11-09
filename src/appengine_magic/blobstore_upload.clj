(ns appengine-magic.blobstore-upload
  (:require [appengine-magic.services.datastore :as ae-ds]
            [clojure.java.io :as io])
  (:import [java.io File OutputStreamWriter]
           [java.net URL HttpURLConnection]
           com.google.appengine.api.datastore.KeyFactory
           com.google.appengine.api.blobstore.BlobKey))


(ae-ds/defentity BlobInfo [^:key blob-key, content_type, creation, filename, size]
  :kind "__BlobInfo__")

(ae-ds/defentity BlobUploadSession [success_path] ; XXX: underscore (_), not hyphen (-)
  :kind "__BlobUploadSession__")


(defn- make-clean-uuid []
  (.replaceAll (str (java.util.UUID/randomUUID)) "-" ""))


(defn- hit-callback [req uploads success-path]
  (let [url (URL. "http" (:server-name req) (:server-port req) success-path)
        cxn (cast HttpURLConnection (.openConnection url))]
    (doto cxn
      (.setDoOutput true)
      (.setRequestMethod "POST")
      (.setUseCaches false)
      (.setInstanceFollowRedirects false)
      (.setRequestProperty "Content-Type" "text/plain")
      (.setRequestProperty "X-AppEngine-BlobUpload" "true"))
    (doseq [header ["User-Agent" "Cookie" "Origin" "Referer"]]
      (let [lc-header (.toLowerCase header)]
        (.setRequestProperty cxn header (get (:headers req) lc-header))))
    (with-open [cxn-writer (-> cxn .getOutputStream OutputStreamWriter.)]
      (.write cxn-writer (prn-str uploads)))
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


(defn- save-upload! [upload-name upload-info target-dir]
  (let [{:keys [filename size content-type tempfile]} upload-info
        blob-key (make-clean-uuid)
        blob-info (BlobInfo. blob-key content-type (java.util.Date.) filename size)
        blob-file (File. target-dir blob-key)]
    (io/copy tempfile blob-file)
    (ae-ds/save! blob-info)
    ;; Return the blob-key for later use.
    blob-key))


(defn make-blob-upload-handler [war-root]
  (let [web-inf-dir (File. war-root "WEB-INF")
        appengine-generated-dir (File. web-inf-dir "appengine-generated")]
    (fn [req]
      (let [uri (:uri req)
            key-string (.substring uri (inc (.lastIndexOf uri "/")))
            key-object (KeyFactory/stringToKey key-string)
            upload-session (ae-ds/retrieve BlobUploadSession key-object
                                           :kind "__BlobUploadSession__")
            raw-uploads (:multipart-params req)
            uploads (reduce (fn [acc [upload-name upload-info]]
                              (assoc acc upload-name
                                     (save-upload! upload-name
                                                   upload-info appengine-generated-dir)))
                            {}
                            raw-uploads)]
        (ae-ds/delete! upload-session)
        (let [resp (hit-callback req uploads (:success_path upload-session))]
          ;; just return it to the user's browser
          resp)))))

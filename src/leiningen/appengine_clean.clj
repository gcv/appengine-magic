(ns leiningen.appengine-clean
  "Cleans out appengine library area."
  (:require lancet)
  (:import java.io.File))


(defn appengine-clean [project]
  (let [prj-application (or (:appengine-application project) (:name project))
        resources-dir (File. (:resources-path project))
        WEB-INF-dir (File. resources-dir "WEB-INF")
        target-lib-dir (File. WEB-INF-dir "lib")]
    (println "cleaning out App Engine application" prj-application)
    (lancet/delete {:dir (.getPath target-lib-dir)})))

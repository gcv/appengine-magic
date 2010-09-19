(ns leiningen.appengine-clean
  "Cleans out appengine compiled class and library areas."
  (:require lancet)
  (:import java.io.File))


(defn appengine-clean [project]
  (println "cleaning out App Engine application" (:appengine-display-name project))
  (let [resources-dir (File. (:resources-path project))
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")
        target-classes-dir (File. WEB-INF-dir "classes")
        target-lib-dir (File. WEB-INF-dir "lib")]
    ;; delete existing content of target classes/ and lib/
    (lancet/delete {:dir (.getPath target-classes-dir)})
    (lancet/delete {:dir (.getPath target-lib-dir)})))

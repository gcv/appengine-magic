(ns leiningen.appengine-update
  "Deploys the application to the production Google App Engine."
  (:use appengine-magic.utils)
  (:require [leiningen.core :as lein])
  (:import java.io.File
           com.google.appengine.tools.admin.AppCfg))


(defn appengine-update [project app-name]
  (let [appengine-sdk (:appengine-sdk project)
        appengine-sdk (cond
                       ;; not given
                       (nil? appengine-sdk)
                       (lein/abort "appengine-update requires :appengine-sdk in project.clj")
                       ;; a string
                       (string? appengine-sdk)
                       appengine-sdk
                       ;; a map: {username location}
                       (map? appengine-sdk)
                       (let [username (System/getProperty "user.name")
                             raw (get appengine-sdk username)]
                         (when (nil? raw)
                           (lein/abort
                            (format "no valid App Engine SDK directory defined for user %s"
                                    username)))
                         raw))
        appengine-sdk (let [appengine-sdk (File. appengine-sdk)]
                        (when-not (.isDirectory appengine-sdk)
                          (lein/abort (format "%s is not a valid App Engine SDK directory"
                                              appengine-sdk)))
                        appengine-sdk)
        versions (if (contains? project :appengine-app-versions)
                     (:appengine-app-versions project)
                     (lein/abort (str "appengine-update requires :appengine-app-versions"
                                      " in project.clj")))
        version (cond
                 ;; not a map
                 (not (map? versions))
                 (lein/abort "bad format for :appengine-app-versions")
                 ;; check the given app-name
                 (not (contains? versions app-name))
                 (lein/abort (format ":appengine-app-versions does not contain %s" app-name))
                 ;; looks fine now
                 :else (versions app-name))
        resources-dir (File. (:resources-path project))
        web-inf-dir (File. resources-dir "WEB-INF")
        in-appengine-web-xml-tmpl (File. web-inf-dir "appengine-web.xml.tmpl")
        out-appengine-web-xml (File. web-inf-dir "appengine-web.xml")]
    (when (not (.exists in-appengine-web-xml-tmpl))
      (lein/abort "appengine-update requires WEB-INF/appengine-web.xml.tmpl template file"))
    (when (.exists out-appengine-web-xml)
      (lein/abort "appengine-update cannot run when a WEB-INF/appengine-web.xml file exists"))
    (try
      (xpath-replace-all in-appengine-web-xml-tmpl out-appengine-web-xml
                         {"//application" app-name
                          "//version" version})
      (System/setProperty "appengine.sdk.root" (.getCanonicalPath appengine-sdk))
      (AppCfg/main (into-array ["update" (.getCanonicalPath resources-dir)]))
      (finally (.delete out-appengine-web-xml)))))

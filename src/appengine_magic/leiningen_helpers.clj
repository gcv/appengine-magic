(ns appengine-magic.leiningen-helpers
  (:use appengine-magic.utils)
  (:require [leiningen.core.main :as lein])
  (:import java.io.File
           com.google.appengine.tools.admin.AppCfg
           [org.apache.commons.exec CommandLine DefaultExecutor]))


(defn run-with-appengine-app-versions [task-name project app-name version]
  (let [appengine-sdk (:appengine-sdk project)
        appengine-sdk (cond
                       ;; not given
                       (nil? appengine-sdk)
                       (if-let [from-env (System/getenv "APPENGINE_HOME")]
                           from-env
                           (lein/abort (str task-name "no App Engine SDK specified: set :appengine-sdk in project.clj, or APPENGINE_HOME in the environment")))
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
        version (if (not (nil? version))
                    version ; just use the given version
                    (let [versions (if (contains? project :appengine-app-versions)
                                       (:appengine-app-versions project)
                                       (lein/abort (str task-name
                                                        " requires :appengine-app-versions"
                                                        " in project.clj")))]
                      (cond
                       ;; not a map
                       (not (map? versions))
                       (lein/abort "bad format for :appengine-app-versions")
                       ;; check the given app-name
                       (not (contains? versions app-name))
                       (lein/abort (format ":appengine-app-versions does not contain %s"
                                           app-name))
                       ;; looks fine now
                       :else (versions app-name))))
        war-dir (File. (or (:appengine-app-war-root project) "war"))
        web-inf-dir (File. war-dir "WEB-INF")
        in-appengine-web-xml-tmpl (File. web-inf-dir "appengine-web.xml.tmpl")
        out-appengine-web-xml (File. web-inf-dir "appengine-web.xml")]
    (when (not (.exists in-appengine-web-xml-tmpl))
      (lein/abort (str task-name "requires WEB-INF/appengine-web.xml.tmpl template file")))
    (when (.exists out-appengine-web-xml)
      (lein/abort (str task-name "cannot run when a WEB-INF/appengine-web.xml file exists")))
    (try
      (xpath-replace-all in-appengine-web-xml-tmpl out-appengine-web-xml
                         {"//application" app-name
                          "//version" version})
      (System/setProperty "appengine.sdk.root" (.getCanonicalPath appengine-sdk))
      (.addShutdownHook (Runtime/getRuntime) (proxy [Thread] []
                                               (run [] (when (.exists out-appengine-web-xml)
                                                         (.delete out-appengine-web-xml)))))
      ;; XXX: This is ugly, but there is no particularly clean solution. AppCfg
      ;; pretty much only works when invoked directly (has something to do with
      ;; the way it reads the user password). The dev_appserver class needs a
      ;; bizarre KickStart invocation class, and it has been difficult to invoke
      ;; it in such a way that it knows the location of the App Engine SDK. So
      ;; it runs from a shell script.
      (cond
       ;; update task
       (= "appengine-update" task-name)
       (AppCfg/main (into-array ["update" (.getCanonicalPath war-dir)]))
       ;; dev_appserver
       (= "appengine-dev-appserver" task-name)
       (let [cmd (format "%s/bin/dev_appserver.sh %s"
                         (.getCanonicalPath appengine-sdk)
                         (.getCanonicalPath war-dir))
             cmd-line (CommandLine/parse cmd)
             executor (DefaultExecutor.)]
         (.execute executor cmd-line))
       ;; blow up
       :else (lein/abort "unknown task"))
      (finally (.delete out-appengine-web-xml)))))

(ns appengine-magic.local-env-helpers
  (:use appengine-magic.utils)
  (:import java.io.File
           [com.google.apphosting.api ApiProxy ApiProxy$Environment]
           [com.google.appengine.tools.development ApiProxyLocalFactory ApiProxyLocalImpl
            LocalServerEnvironment]
           com.google.appengine.api.taskqueue.dev.LocalTaskQueue))


(defonce ^{:dynamic true} *current-app-id* (atom nil))
(defonce ^{:dynamic true} *current-app-version* (atom nil))

(defonce ^{:dynamic true} *current-server-port* (atom nil))


(defn make-thread-environment-proxy [& {:keys [user-email user-admin?]}]
  (proxy [ApiProxy$Environment] []
    (isLoggedIn [] (or (boolean user-email) false))
    (getAuthDomain [] "")
    (getRequestNamespace [] "")
    (getDefaultNamespace [] "")
    (getAttributes [] (java.util.HashMap.))
    (getEmail [] (or user-email ""))
    (isAdmin [] (or (Boolean/parseBoolean user-admin?) false))
    (getAppId [] @*current-app-id*)
    (getVersionId [] @*current-app-version*)))


(defn appengine-init [#^File dir, port high-replication in-memory]
  (let [appengine-web-file (File. dir "WEB-INF/appengine-web.xml")
        application-id (if (.exists appengine-web-file)
                           (first (xpath-value appengine-web-file "//application"))
                           "appengine-magic-app")
        application-version (if (.exists appengine-web-file)
                                (first (xpath-value appengine-web-file "//version"))
                                "")
        proxy-factory (ApiProxyLocalFactory.)
        environment (proxy [LocalServerEnvironment] []
                      (enforceApiDeadlines [] true)
                      (simulateProductionLatencies [] true)
                      (getAppDir [] dir)
                      (getHostName [] "localhost")
                      (getAddress [] "localhost")
                      (getPort [] port)
                      (waitForServerToStart [] nil))
        api-proxy (.create proxy-factory environment)]
    (reset! *current-app-id* application-id)
    (reset! *current-app-version* application-version)
    (reset! *current-server-port* port)

    ;; Set datastore properties for optional features
    (.setProperty api-proxy "datastore.no_storage" (str in-memory))
    (if high-replication
      (.setProperty api-proxy "datastore.default_high_rep_job_policy_unapplied_job_pct" "20"))

    (ApiProxy/setDelegate api-proxy)
    ;; This installs a thread environment onto the REPL thread and allows App
    ;; Engine API calls to work in the REPL.
    (ApiProxy/setEnvironmentForCurrentThread (make-thread-environment-proxy))))


(defn appengine-clear []
  (reset! *current-app-id* nil)
  (ApiProxy/clearEnvironmentForCurrentThread)
  (.stop (.getService (ApiProxy/getDelegate) LocalTaskQueue/PACKAGE))
  (.stop (ApiProxy/getDelegate)))

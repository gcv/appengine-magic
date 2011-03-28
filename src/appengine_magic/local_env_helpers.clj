(ns appengine-magic.local-env-helpers
  (:use appengine-magic.utils)
  (:import java.io.File
           [com.google.apphosting.api ApiProxy ApiProxy$Environment]
           [com.google.appengine.tools.development ApiProxyLocalFactory ApiProxyLocalImpl
            LocalServerEnvironment]
           com.google.appengine.api.taskqueue.dev.LocalTaskQueue))


(defonce *current-app-id* (atom nil))
(defonce *current-app-version* (atom nil))

(defonce *current-server-port* (atom nil))


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


(defn appengine-init [#^File dir, port]
  (let [appengine-web-file (File. dir "WEB-INF/appengine-web.xml")
        application-id (if (.exists appengine-web-file)
                           (first (xpath-value appengine-web-file "//application"))
                           "appengine-magic-app")
        application-version (if (.exists appengine-web-file)
                                (first (xpath-value appengine-web-file "//version"))
                                "")
        proxy-factory (ApiProxyLocalFactory.)
        environment (proxy [LocalServerEnvironment] []
                      (getAppDir [] dir)
                      (getAddress [] "localhost")
                      (getPort [] port)
                      (waitForServerToStart [] nil))
        api-proxy (.create proxy-factory environment)]
    (reset! *current-app-id* application-id)
    (reset! *current-app-version* application-version)
    (reset! *current-server-port* port)
    (ApiProxy/setDelegate api-proxy)
    ;; This installs a thread environment onto the REPL thread and allows App
    ;; Engine API calls to work in the REPL.
    (ApiProxy/setEnvironmentForCurrentThread (make-thread-environment-proxy))))


(defn appengine-clear []
  (reset! *current-app-id* nil)
  (ApiProxy/clearEnvironmentForCurrentThread)
  (.stop (.getService (ApiProxy/getDelegate) LocalTaskQueue/PACKAGE))
  (.stop (ApiProxy/getDelegate)))

(ns appengine-magic.local-env-helpers
  (:use appengine-magic.utils)
  (:require [clojure.string :as str])
  (:import java.io.File
           [com.google.apphosting.api ApiProxy ApiProxy$Environment]
           [com.google.appengine.tools.development ApiProxyLocalFactory ApiProxyLocalImpl
            LocalServerEnvironment]))


(defonce *current-app-id* (atom nil))


(defn appengine-init [#^File dir]
  (let [appengine-web-file (File. dir "WEB-INF/appengine-web.xml")
        application-id (first (xpath-value appengine-web-file "//application"))
        proxy-factory (ApiProxyLocalFactory.)
        environment (proxy [LocalServerEnvironment] []
                      (getAppDir [] dir))
        api-proxy (.create proxy-factory environment)]
    (reset! *current-app-id* application-id)
    (ApiProxy/setDelegate api-proxy)))


(defn appengine-clear []
  (reset! *current-app-id* nil)
  (ApiProxy/clearEnvironmentForCurrentThread)
  (.stop (ApiProxy/getDelegate)))


(defn local-environment-proxy [req]
  (let [servlet-cookies (:servlet-cookies req)
        login-cookie (:value (get servlet-cookies "dev_appserver_login"))
        [user-email user-admin _] (when login-cookie (str/split login-cookie #":"))]
    (proxy [ApiProxy$Environment] []
      (isLoggedIn [] (boolean user-email))
      (getAuthDomain [] "")
      (getRequestNamespace [] "")
      (getDefaultNamespace [] "")
      (getAttributes [] (java.util.HashMap.))
      (getEmail [] (or user-email ""))
      (isAdmin [] user-admin)
      (getAppId [] @*current-app-id*))))


(defmacro with-appengine [proxy & body]
  `(last (doall [(ApiProxy/setEnvironmentForCurrentThread ~proxy) ~@body])))


(defn environment-decorator [application]
  (fn [req]
    (with-appengine (local-environment-proxy req)
      (application req))))

(ns appengine-magic.local-env-helpers
  (:require [clojure.string :as str])
  (:import [com.google.apphosting.api ApiProxy ApiProxy$Environment]
           [com.google.appengine.tools.development ApiProxyLocalFactory ApiProxyLocalImpl
            LocalServerEnvironment]))


(defn appengine-init [#^File dir]
  (let [proxy-factory (ApiProxyLocalFactory.)
        environment (proxy [LocalServerEnvironment] []
                      (getAppDir [] dir))
        api-proxy (.create proxy-factory environment)]
    (ApiProxy/setDelegate api-proxy)))


(defn appengine-clear []
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
      (getAppId [] "local"))))


(defmacro with-appengine [proxy & body]
  `(last (doall [(ApiProxy/setEnvironmentForCurrentThread ~proxy) ~@body])))


(defn environment-decorator [application]
  (fn [req]
    (with-appengine (local-environment-proxy req)
      (application req))))

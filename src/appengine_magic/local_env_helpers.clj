(ns appengine-magic.local-env-helpers
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


(defmacro with-appengine
  ([body] `(with-appengine env-proxy ~body))
  ([proxy body]
     `(last (doall [(ApiProxy/setEnvironmentForCurrentThread ~proxy) ~body]))))


(defn login-aware-proxy [req]
  (let [email (:email (:session req))]
    (proxy [ApiProxy$Environment] []
      (isLoggedIn [] (boolean email))
      (getAuthDomain [] "")
      (getRequestNamespace [] "")
      (getDefaultNamespace [] "")
      (getAttributes [] (java.util.HashMap.))
      (getEmail [] (or email ""))
      (isAdmin [] true)
      (getAppId [] "local"))))


(defn environment-decorator [application]
  (fn [req]
    (with-appengine (login-aware-proxy req)
      (application req))))


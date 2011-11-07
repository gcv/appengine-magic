(ns appengine-magic.core
  (:import com.google.apphosting.api.ApiProxy))


(declare appengine-environment-type)


(defn open-resource-stream [resource-name]
  (-> (clojure.lang.RT/baseLoader) (.getResourceAsStream resource-name)))


(defn resource-url [resource-name]
  (-> (clojure.lang.RT/baseLoader) (.getResource resource-name)))


(defn appengine-environment-type []
  (let [env-property (System/getProperty "com.google.appengine.runtime.environment")]
    (cond
     (= env-property "Development") :dev-appserver
     (= env-property "Production") :production
     (nil? env-property) (try
                           (let [stack-trace (.getStackTrace (Thread/currentThread))]
                             (if (some #(.contains (.toString %) "clojure.lang.Compiler.compile")
                                       stack-trace)
                                 :compiling
                                 :interactive))
                           (catch java.security.AccessControlException ace
                             :production)))))


(defn appengine-app-id []
  (try
    (-> (ApiProxy/getCurrentEnvironment) .getAppId)
    (catch NullPointerException npe
      (throw (RuntimeException. "the server must be running" npe)))))


(defn appengine-app-version []
  (try
    (-> (ApiProxy/getCurrentEnvironment) .getVersionId)
    (catch NullPointerException npe
      (throw (RuntimeException. "the server must be running" npe)))))


(if (= :interactive (appengine-environment-type))
    (load "core_local")
    (load "core_google"))

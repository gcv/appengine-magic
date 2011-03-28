(ns appengine-magic.testing
  (:use [appengine-magic.utils :only [os-type]])
  (:import [com.google.appengine.tools.development.testing LocalServiceTestHelper
            LocalServiceTestConfig
            LocalMemcacheServiceTestConfig LocalMemcacheServiceTestConfig$SizeUnit
            LocalMailServiceTestConfig
            LocalDatastoreServiceTestConfig
            LocalUserServiceTestConfig]
           [com.google.apphosting.api ApiProxy]))


(def *memcache-size-units*
     {:bytes LocalMemcacheServiceTestConfig$SizeUnit/BYTES
      :kb LocalMemcacheServiceTestConfig$SizeUnit/KB
      :mb LocalMemcacheServiceTestConfig$SizeUnit/MB})


(def *logging-levels*
     {:all java.util.logging.Level/ALL
      :severe java.util.logging.Level/SEVERE
      :warning java.util.logging.Level/WARNING
      :info java.util.logging.Level/INFO
      :config java.util.logging.Level/CONFIG
      :fine java.util.logging.Level/FINE
      :finer java.util.logging.Level/FINER
      :finest java.util.logging.Level/FINEST
      :off java.util.logging.Level/OFF})


(defn memcache [& {:keys [max-size size-units]}]
  (let [lmstc (LocalMemcacheServiceTestConfig.)]
    (cond
     ;; this means adjust the cache size
     (and max-size size-units)
     (.setMaxSize lmstc (long max-size) (*memcache-size-units* size-units))
     ;; nothing provided; do nothing
     (and (nil? max-size) (nil? size-units))
     true
     ;; one or the other provided: too error-prone, disallow
     :else
     (throw (RuntimeException. "provide both :max-size and :size-units")))
    lmstc))


(defn datastore [& {:keys [storage? store-delay-ms
                           max-txn-lifetime-ms max-query-lifetime-ms
                           backing-store-location]
                    :or {storage? false}}]
  (let [ldstc (LocalDatastoreServiceTestConfig.)]
    (.setNoStorage ldstc (not storage?))
    (when-not (nil? store-delay-ms)
      (.setStoreDelayMs ldstc store-delay-ms))
    (when-not (nil? max-txn-lifetime-ms)
      (.setMaxTxnLifetimeMs ldstc max-txn-lifetime-ms))
    (when-not (nil? max-query-lifetime-ms)
      (.setMaxQueryLifetimeMs ldstc max-query-lifetime-ms))
    (if-not (nil? backing-store-location)
        (.setBackingStoreLocation ldstc backing-store-location)
        (.setBackingStoreLocation ldstc (if (= :windows (os-type))
                                            "NUL"
                                            "/dev/null")))
    ldstc))


(defn mail [& {:keys [log-mail-body? log-mail-level]
               :or {log-mail-body? false
                    log-mail-level :info}}]
  (let [lmstc (LocalMailServiceTestConfig.)]
    (.setLogMailBody lmstc log-mail-body?)
    (.setLogMailLevel lmstc (*logging-levels* log-mail-level))
    lmstc))


(defn user []
  (LocalUserServiceTestConfig.))


(defn- make-local-services-fixture-fn [services hook-helper]
  (fn [test-fn]
    (let [environment (ApiProxy/getCurrentEnvironment)
          delegate (ApiProxy/getDelegate)
          helper (hook-helper (LocalServiceTestHelper. (into-array LocalServiceTestConfig services)))]
      (.setUp helper)
      (test-fn)
      (.tearDown helper)
      (ApiProxy/setEnvironmentForCurrentThread environment)
      (ApiProxy/setDelegate delegate))))


(defn- local-services-helper
  ([]
     [(memcache) (datastore) (mail) (user)])
  ([services override]
     (let [services (if (= :all services) (local-services-helper) services)]
       (if (nil? override)
           services
           (let [given-services (zipmap (map class services) services)
                 override-services (zipmap (map class override) override)]
             (merge given-services override-services))))))


(defn local-services
  ([]
     "Uses all services with their default settings."
     (make-local-services-fixture-fn (local-services-helper) identity))
  ([services & {:keys [override hook-helper] :or {hook-helper identity}}]
     "- If services is :all, uses all services with their default settings.
      - If services is a vector of services, uses those as given.
      - To use all defaults, but override some specific services, use :all
        and an :override vector."
     (make-local-services-fixture-fn (local-services-helper services override) hook-helper)))


(defn login
  "Hook helper to be a logged-in user."
  [email]
  (let [domain (-> (re-seq #"@(.+)$" email) first second)]
    #(.. % (setEnvIsLoggedIn true) (setEnvAuthDomain domain) (setEnvEmail email))))


(defn admin
  "Hook helper to be an admin."
  []
  #(.setEnvIsAdmin % true))


(defn admin-login
  "Hook helper to logged in as an admin."
  [email]
  (comp (admin) (login email)))

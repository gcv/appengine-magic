(ns appengine-magic.core)


(defn- in-appengine-interactive-mode? []
  (try
    (let [stack-trace (.getStackTrace (Thread/currentThread))]
      (some #(.contains (.toString %) "swank.core") stack-trace))
    (catch java.security.AccessControlException ace
      false)))


(if (in-appengine-interactive-mode?)
    (load "core_local")
    (load "core_google"))

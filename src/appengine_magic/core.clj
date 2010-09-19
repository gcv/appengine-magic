(ns appengine-magic.core)


(defn- in-appengine-interactive-mode? []
  (try
    (let [stack-trace (.getStackTrace (Thread/currentThread))]
      (some #(or (.contains (.toString %) "swank.core")
                 (.contains (.toString %) "clojure.main$repl"))
            stack-trace))
    (catch java.security.AccessControlException ace
      false)))


(if (in-appengine-interactive-mode?)
    (load "core_local")
    (load "core_google"))

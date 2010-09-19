(ns appengine-magic.core)


(defn- in-appengine-dev-mode? []
  false)


(if (in-appengine-dev-mode?)
    (load "core_local")
    (load "core_google"))

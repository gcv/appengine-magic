(ns leiningen.appengine-dev-appserver
  "Starts a dev_appserver instance."
  (:use appengine-magic.leiningen-helpers))


(defn appengine-dev-appserver [project app-name & [app-version]]
  (run-with-appengine-app-versions "appengine-dev-appserver" project app-name app-version))

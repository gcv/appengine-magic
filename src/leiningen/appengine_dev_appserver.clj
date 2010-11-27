(ns leiningen.appengine-dev-appserver
  "Deploys the application to the production Google App Engine."
  (:use appengine-magic.leiningen-helpers))


(defn appengine-dev-appserver [project app-name]
  (run-with-appengine-app-versions "appengine-dev-appserver" project app-name))

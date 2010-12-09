(ns leiningen.appengine-update
  "Deploys the application to the production Google App Engine."
  (:use appengine-magic.leiningen-helpers))


(defn appengine-update [project app-name]
  (run-with-appengine-app-versions "appengine-update" project app-name))

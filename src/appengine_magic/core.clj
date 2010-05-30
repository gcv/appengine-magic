(ns appengine-magic.core
  (:use appengine-magic.local-env-helpers
        [ring.middleware.file :only [wrap-file]]))


;;; This attempts to abstract away the nuts and bolts of bootstrapping an App
;;; Engine application. The application should just depend on appengine-magic,
;;; and it only needs to put resources into war/WEB-INF/... directories. It also
;;; needs to provide the .xml descriptor files for the application. Beyond that,
;;; however, it can use any framework to make a Ring-compatible handler, and use
;;; appengine-magic to manage a local development server, compile a deployable
;;; servlet, or deploy to App Engine.



(defmacro def-appengine-app [app-var-name ring-handler war-root]
  (let [real-handler (wrap-file (environment-decorator ring-handler) (str war-root))]))


(defn start [app & options])


(defn stop [app])


(defn compile-app-to-servlet [app servlet-class-name])


(defn deploy [app])


(defn rollback [app])

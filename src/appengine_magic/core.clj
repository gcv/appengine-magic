(ns appengine-magic.core
  (:use appengine-magic.local-env-helpers
        [ring.middleware.file :only [wrap-file]]
        [ring.util.servlet :only [servlet]])
  (:require [appengine-magic.jetty :as jetty]))


;;; This attempts to abstract away the nuts and bolts of bootstrapping an App
;;; Engine application. The application should just depend on appengine-magic,
;;; and it only needs to put resources into war/WEB-INF/... directories. It also
;;; needs to provide the .xml descriptor files for the application. Beyond that,
;;; however, it can use any framework to make a Ring-compatible handler, and use
;;; appengine-magic to manage a local development server, compile a deployable
;;; servlet, or deploy to App Engine.


(defmacro def-appengine-app [app-var-name handler war-root]
  `(def ~app-var-name
        (let [handler# ~handler
              war-root# ~war-root]
          {:handler (wrap-file (environment-decorator handler#) (str war-root#))
           :war-root war-root#})))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments.
(defn start [appengine-app {:keys [port join?] :or {port 8080 join? false}}]
  (let [handler-servlet (servlet (:handler appengine-app))]
    (app-engine-init (:war-root appengine-app))
    ;; TODO: Also needs a static fallback into /war, excluding /war/WEB-INF.
    (jetty/start {"/" handler-servlet
                  "/_ah/login" (com.google.appengine.api.users.dev.LocalLoginServlet.)}
                 {:port port :join? join?})))


(defn stop [server]
  (jetty/stop server))


;;; TODO: Implement this.
(defn compile-to-servlet [appengine-app servlet-class-name])


;;; TODO: Implement this.
(defn deploy [])


;;; TODO: Implement this.
(defn rollback [])

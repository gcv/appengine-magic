(in-ns 'appengine-magic.core)

(use 'appengine-magic.local-env-helpers
     '[ring.middleware.file :only [wrap-file]]
     '[ring.util.servlet :only [servlet]])

(require '[appengine-magic.jetty :as jetty])


(defn wrap-war-static [app #^String war-root]
  (fn [req] (let [#^String uri (:uri req)]
              (if (.startsWith uri "/WEB-INF")
                  (app req)
                  ((wrap-file app war-root) req)))))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments, if it works with macros.
(defmacro def-appengine-app [app-var-name handler & [args]]
  (let [{:keys [war-root] :or {war-root "war"}} args]
    `(def ~app-var-name
         (let [handler# ~handler
               war-root# (-> (clojure.lang.RT/baseLoader) (.getResource ~war-root) .getFile)]
           {:handler (wrap-war-static
                      (environment-decorator handler#)
                      war-root#)
            :war-root war-root#}))))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments.
(defn start* [appengine-app {:keys [port join?] :or {port 8080 join? false}}]
  (let [handler-servlet (servlet (:handler appengine-app))]
    (appengine-init (:war-root appengine-app))
    (jetty/start {"/" handler-servlet
                  "/_ah/login" (com.google.appengine.api.users.dev.LocalLoginServlet.)}
                 {:port port :join? join?})))


(defn stop* [server]
  (jetty/stop server))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments.
(defmacro start [appengine-app & [args]]
  (let [{:keys [server] :or {server '*server*}} args
        args (dissoc args :server)]
    `(do (defonce ~server (atom nil))
         (reset! ~server (start* ~appengine-app ~args)))))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments.
(defmacro stop [{:keys [server] :or {server '*server*}}]
  `(stop* @~server))

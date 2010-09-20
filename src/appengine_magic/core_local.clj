(in-ns 'appengine-magic.core)

(use 'appengine-magic.local-env-helpers
     '[appengine-magic.servlet :only [servlet]]
     '[ring.middleware.file :only [wrap-file]])

(require '[appengine-magic.jetty :as jetty])


(defn wrap-war-static [app #^String war-root]
  (fn [req] (let [#^String uri (:uri req)]
              (if (.startsWith uri "/WEB-INF")
                  (app req)
                  ((wrap-file app war-root) req)))))


(defmacro def-appengine-app [app-var-name handler &
                             {:keys [war-root] :or {war-root "war"}}]
  `(def ~app-var-name
        (let [handler# ~handler
              war-root# (-> (clojure.lang.RT/baseLoader) (.getResource ~war-root) .getFile)]
          {:handler (wrap-war-static
                     (environment-decorator handler#)
                     war-root#)
           :war-root war-root#})))


(defn start* [appengine-app & {:keys [port join?]}]
  (let [handler-servlet (servlet (:handler appengine-app))]
    (appengine-init (:war-root appengine-app))
    (jetty/start {"/" handler-servlet
                  "/_ah/login" (com.google.appengine.api.users.dev.LocalLoginServlet.)}
                 :port port
                 :join? join?)))


(defn stop* [server]
  (jetty/stop server))


(defmacro start [appengine-app &
                 {:keys [server port join?]
                  :or {server '*server* port 8080 join? false}}]
  `(do (defonce ~server (atom nil))
       (reset! ~server (start* ~appengine-app :port ~port :join? ~join?))))


(defmacro stop [& {:keys [server] :or {server '*server*}}]
  `(stop* @~server))

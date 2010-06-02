(ns appengine-magic.jetty
  (:use [ring.util.servlet :only [servlet]])
  (:import org.mortbay.jetty.handler.ContextHandlerCollection
           org.mortbay.jetty.Server
           javax.servlet.http.HttpServlet
           [org.mortbay.jetty.servlet Context ServletHolder]))


(defn- proxy-multihandler
  "Returns a Jetty Handler implementation for the given map of relative URLs to
   handlers. Each handler may be a Ring handler or an HttpServlet instance."
  [handlers]
  (let [all-contexts (ContextHandlerCollection.)]
    (doseq [[relative-url handler] handlers]
      (let [context (Context. all-contexts relative-url)
            handler-servlet (if (instance? HttpServlet handler)
                                handler
                                (servlet handler))
            servlet-holder (ServletHolder. handler-servlet)]
        (.addServlet context servlet-holder "/*")))
    all-contexts))


;;; TODO: When Clojure 1.2 comes out, change this to use the destructuring
;;; syntax for keyword arguments.
(defn #^Server start [handlers {:keys [port join?] :or {port 8080 join? false}}]
  (let [server (Server. port)]
    (doto server
      (.setHandler (if (map? handlers)
                       (proxy-multihandler handlers)
                       (proxy-multihandler {"/" handlers})))
      (.start))
    (when join? (.join server))
    server))


(defn stop [#^Server server]
  (.stop server))

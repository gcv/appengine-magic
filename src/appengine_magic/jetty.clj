(ns appengine-magic.jetty
  (:use [appengine-magic.servlet :only [servlet]])
  (:import org.mortbay.jetty.handler.ContextHandlerCollection
           [org.mortbay.jetty Server Handler]
           javax.servlet.http.HttpServlet
           javax.servlet.Filter
           [org.mortbay.jetty.servlet Context ServletHolder FilterHolder]))


(defn- proxy-multihandler
  "Returns a Jetty Handler implementation for the given map of relative URLs to
   handlers. Each handler may be a Ring handler or an HttpServlet instance."
  [all-handlers]
  (let [all-contexts (ContextHandlerCollection.)]
    (doseq [[relative-url url-handlers] all-handlers]
      (let [url-handlers (if (sequential? url-handlers) url-handlers [url-handlers])
            context (Context. all-contexts relative-url Context/SESSIONS)]
        (doseq [handler url-handlers]
          (cond
           ;; plain servlets
           (instance? HttpServlet handler)
           (.addServlet context (ServletHolder. handler) "/*")
           ;; plain filters
           (instance? Filter handler)
           (.addFilter context (FilterHolder. handler) "/*" Handler/ALL)
           ;; a Ring handler
           :else
           (.addServlet context (ServletHolder. (servlet handler)) "/*")))))
    all-contexts))


(defn #^Server start [handlers &
                      {:keys [port join?] :or {port 8080 join? false}}]
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

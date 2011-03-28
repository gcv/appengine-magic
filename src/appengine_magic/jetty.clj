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
  [filters all-handlers]
  (let [all-contexts (ContextHandlerCollection.)
        context (Context. all-contexts "/" Context/SESSIONS)]
    (doseq [[url filter-objs] filters]
      (let [filter-objs (if (sequential? filter-objs) filter-objs [filter-objs])]
        (doseq [filter-obj filter-objs]
          (.addFilter context (FilterHolder. filter-obj) url Handler/ALL))))
    (doseq [[relative-url url-handler] all-handlers]
      (.addServlet context (ServletHolder. url-handler) relative-url))
    all-contexts))


(defn #^Server start [filter-map servlet-map &
                      {:keys [port join?] :or {port 8080 join? false}}]
  (let [server (Server. port)]
    (doto server
      (.setHandler (proxy-multihandler filter-map servlet-map))
      (.start))
    (when join? (.join server))
    server))


(defn stop [#^Server server]
  (.stop server))

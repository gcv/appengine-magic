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
    (appengine-init (java.io.File. (:war-root appengine-app)))
    (jetty/start
     {"/" handler-servlet
      ;; App Engine services
      "/_ah/login" (com.google.appengine.api.users.dev.LocalLoginServlet.)
      "/_ah/logout" (com.google.appengine.api.users.dev.LocalLogoutServlet.)
      "/_ah/upload/*" (com.google.appengine.api.blobstore.dev.UploadBlobServlet.)
      "/_ah/img/*" (com.google.appengine.api.images.dev.LocalBlobImageServlet.)
      "/_ah/channel/jsapi" (com.google.appengine.api.channel.dev.ServeScriptServlet.)
      "/_ah/channel/dev" (com.google.appengine.api.channel.dev.LocalChannelServlet.)
      ;; These other mappings are in webdefault.xml in in
      ;; appengine-local-runtime-*.jar, but they required jars are not packaged
      ;; in a Maven-friendly manner. They are only available in the SDK's own
      ;; messy library structure.
      ;; "/_ah/sessioncleanup" (com.google.apphosting.utils.servlet.SessionCleanupServlet.)
      ;; "/_ah/admin" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
      ;; "/_ah/admin/" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
      ;; "/_ah/admin/datastore" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
      ;; "/_ah/admin/taskqueue" (com.google.apphosting.utils.servlet.TaskQueueViewerServlet.)
      ;; "/_ah/admin/xmpp" (com.google.apphosting.utils.servlet.XmppServlet.)
      ;; "/_ah/admin/inboundmail" (com.google.apphosting.utils.servlet.InboundMailServlet.)
      ;; "/_ah/resources" (com.google.apphosting.utils.servlet.AdminConsoleResourceServlet.)
      ;; "/_ah/adminConsole" (org.apache.jsp.ah.adminConsole_jsp.)
      ;; "/_ah/datastoreViewerHead" (org.apache.jsp.ah.datastoreViewerHead_jsp.)
      ;; "/_ah/datastoreViewerBody" (org.apache.jsp.ah.datastoreViewerBody_jsp.)
      ;; "/_ah/datastoreViewerFinal" (org.apache.jsp.ah.datastoreViewerFinal_jsp.)
      ;; "/_ah/entityDetailsHead" (org.apache.jsp.ah.entityDetailsHead_jsp.)
      ;; "/_ah/entityDetailsBody" (org.apache.jsp.ah.entityDetailsBody_jsp.)
      ;; "/_ah/entityDetailsFinal" (org.apache.jsp.ah.entityDetailsFinal_jsp.)
      ;; "/_ah/taskqueueViewerHead" (org.apache.jsp.ah.taskqueueViewerHead_jsp.)
      ;; "/_ah/taskqueueViewerBody" (org.apache.jsp.ah.taskqueueViewerBody_jsp.)
      ;; "/_ah/taskqueueViewerFinal" (org.apache.jsp.ah.taskqueueViewerFinal_jsp.)
      ;; "/_ah/xmppHead" (org.apache.jsp.ah.xmppHead_jsp.)
      ;; "/_ah/xmppBody" (org.apache.jsp.ah.xmppBody_jsp.)
      ;; "/_ah/xmppFinal" (org.apache.jsp.ah.xmppFinal_jsp.)
      ;; "/_ah/inboundmailHead" (org.apache.jsp.ah.inboundMailHead_jsp.)
      ;; "/_ah/inboundmailBody" (org.apache.jsp.ah.inboundMailBody_jsp.)
      ;; "/_ah/inboundmailFinal" (org.apache.jsp.ah.inboundMailFinal_jsp.)
      }
     :port port
     :join? join?)))


(defn stop* [server]
  (appengine-clear)
  (jetty/stop server))


(defmacro start [appengine-app &
                 {:keys [server port join?]
                  :or {server '*server* port 8080 join? false}}]
  `(do (defonce ~server (atom nil))
       (reset! ~server (start* ~appengine-app :port ~port :join? ~join?))))


(defmacro stop [& {:keys [server] :or {server '*server*}}]
  `(stop* @~server))

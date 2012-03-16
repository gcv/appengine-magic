(in-ns 'appengine-magic.core)

(use 'appengine-magic.local-env-helpers
     '[appengine-magic.servlet :only [servlet]]
     '[appengine-magic.swank :only [wrap-swank]]
     '[ring.middleware.file :only [wrap-file]]
     '[ring.middleware.file-info :only [wrap-file-info]])

(require '[clojure.string :as str]
         '[appengine-magic.jetty :as jetty]
         '[appengine-magic.blobstore-upload :as blobstore-upload])

(import java.io.File
        com.google.apphosting.api.ApiProxy)



;;; ----------------------------------------------------------------------------
;;; appengine-magic core API functions
;;; ----------------------------------------------------------------------------

(defn default-war-root []
  (-> (clojure.lang.RT/baseLoader)
      (.getResource ".")
      .getFile
      java.net.URLDecoder/decode
      (File. "../war")
      .getAbsolutePath))


(defn appengine-base-url [& {:keys [https?] :or {https? false}}]
  ;; NB: The https? argument is intentionally ignored. HTTPS is not supported
  ;; for local environments.
  (str "http://localhost:"
       (str @appengine-magic.local-env-helpers/*current-server-port*)))


(defn wrap-war-static [app, #^String war-root]
  (fn [req]
    (let [#^String uri (:uri req)]
      (if (.startsWith uri "/WEB-INF")
          (app req)
          ((wrap-file-info (wrap-file app war-root)) req)))))


(defmacro def-appengine-app [app-var-name handler & {:keys [war-root]}]
  `(def ~app-var-name
        (let [handler# ~handler
              war-root-arg# ~war-root
              war-root# (if (nil? war-root-arg#)
                            (default-war-root)
                            war-root-arg#)]
          {:handler (-> handler#
                        wrap-swank
                        (wrap-war-static war-root#))
           :war-root war-root#})))


(defn make-appengine-request-environment-filter []
  (reify javax.servlet.Filter
    (init [_ filter-config]
      (.setAttribute (.getServletContext filter-config)
                     "com.google.appengine.devappserver.ApiProxyLocal"
                     (ApiProxy/getDelegate)))
    (destroy [_])
    (doFilter [_ req resp chain]
      (let [all-cookies (.getCookies req)
            login-cookie (when all-cookies
                           (let [raw (first (filter #(= "dev_appserver_login" (.getName %))
                                                    (.getCookies req)))]
                             (when raw (.getValue raw))))
            [user-email user-admin? _] (when login-cookie
                                         (str/split login-cookie #":"))
            thread-environment-proxy (make-thread-environment-proxy :user-email user-email
                                                                    :user-admin? user-admin?)]
        (ApiProxy/setEnvironmentForCurrentThread thread-environment-proxy))
      (.doFilter chain req resp))))



;;; ----------------------------------------------------------------------------
;;; development server controls
;;; ----------------------------------------------------------------------------

(defonce ^{:dynamic true} *server* (atom nil))


(defn start [appengine-app & {:keys [port join? high-replication in-memory]
                              :or {port 8080, join? false, high-replication false, in-memory false}}]
  (let [war-root (java.io.File. (:war-root appengine-app))
        handler-servlet (servlet (:handler appengine-app))]
    (appengine-init war-root port high-replication in-memory)
    (reset!
     *server*
     (jetty/start
      {"/*" [(make-appengine-request-environment-filter)
             (com.google.apphosting.utils.servlet.TransactionCleanupFilter.)
             (com.google.appengine.api.blobstore.dev.ServeBlobFilter.)]}
      {"/" handler-servlet
       ;; These mappings are from webdefault.xml in appengine-local-runtime-*.jar.
       "/_ah/login" (com.google.appengine.api.users.dev.LocalLoginServlet.)
       "/_ah/logout" (com.google.appengine.api.users.dev.LocalLogoutServlet.)
       "/_ah/upload/*" (servlet (blobstore-upload/make-blob-upload-handler war-root))
       "/_ah/img/*" (com.google.appengine.api.images.dev.LocalBlobImageServlet.)
       "/_ah/channel/jsapi" (com.google.appengine.api.channel.dev.ServeScriptServlet.)
       "/_ah/channel/dev" (com.google.appengine.api.channel.dev.LocalChannelServlet.)
       "/_ah/sessioncleanup" (com.google.apphosting.utils.servlet.SessionCleanupServlet.)
       "/_ah/admin" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
       "/_ah/admin/" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
       "/_ah/admin/datastore" (com.google.apphosting.utils.servlet.DatastoreViewerServlet.)
       "/_ah/admin/taskqueue" (com.google.apphosting.utils.servlet.TaskQueueViewerServlet.)
       "/_ah/admin/xmpp" (com.google.apphosting.utils.servlet.XmppServlet.)
       "/_ah/admin/inboundmail" (com.google.apphosting.utils.servlet.InboundMailServlet.)
       "/_ah/resources" (com.google.apphosting.utils.servlet.AdminConsoleResourceServlet.)
       "/_ah/adminConsole" (org.apache.jsp.ah.adminConsole_jsp.)
       "/_ah/datastoreViewerHead" (org.apache.jsp.ah.datastoreViewerHead_jsp.)
       "/_ah/datastoreViewerBody" (org.apache.jsp.ah.datastoreViewerBody_jsp.)
       "/_ah/datastoreViewerFinal" (org.apache.jsp.ah.datastoreViewerFinal_jsp.)
       "/_ah/entityDetailsHead" (org.apache.jsp.ah.entityDetailsHead_jsp.)
       "/_ah/entityDetailsBody" (org.apache.jsp.ah.entityDetailsBody_jsp.)
       "/_ah/entityDetailsFinal" (org.apache.jsp.ah.entityDetailsFinal_jsp.)
       "/_ah/taskqueueViewerHead" (org.apache.jsp.ah.taskqueueViewerHead_jsp.)
       "/_ah/taskqueueViewerBody" (org.apache.jsp.ah.taskqueueViewerBody_jsp.)
       "/_ah/taskqueueViewerFinal" (org.apache.jsp.ah.taskqueueViewerFinal_jsp.)
       "/_ah/xmppHead" (org.apache.jsp.ah.xmppHead_jsp.)
       "/_ah/xmppBody" (org.apache.jsp.ah.xmppBody_jsp.)
       "/_ah/xmppFinal" (org.apache.jsp.ah.xmppFinal_jsp.)
       "/_ah/inboundmailHead" (org.apache.jsp.ah.inboundMailHead_jsp.)
       "/_ah/inboundmailBody" (org.apache.jsp.ah.inboundMailBody_jsp.)
       "/_ah/inboundmailFinal" (org.apache.jsp.ah.inboundMailFinal_jsp.)}
      :port port
      :join? join?))))


(defn stop []
  (when-not (nil? @*server*)
    (appengine-clear)
    (jetty/stop @*server*)
    (reset! *server* nil)))


(defn serve [appengine-app & {:keys [port high-replication in-memory]
                              :or {port 8080, high-replication false, in-memory false}}]
  (stop)
  (start appengine-app :port port :high-replication high-replication :in-memory in-memory))

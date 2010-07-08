(ns leiningen.appengine-new
  "Create the skeleton of a Google App Engine application."
  (:use appengine-magic.utils)
  (:import [java.io File FileWriter]))


(def app-servlet-src
     (str "(ns %s.%s\n"
          "  (:gen-class :extends javax.servlet.http.HttpServlet)\n"
          "  ;; Replace the ... below with the namespace which defines your appengine-app.\n"
          "  (:use %s....)\n"
          "  (:use [ring.util.servlet :only [defservice]]))\n"
          "\n"
          ";; Replace the ... below with the var containing your appengine-app.\n"
          "(defservice ...)\n"))


(defn appengine-new [project]
  (let [resources-dir (File. (:resources-path project))
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")
        prj-application (:appengine-application project)
        prj-display-name (:appengine-display-name project)
        prj-servlet (:appengine-servlet project "app_servlet")]
    (println "making a skeleton for a Google App Engine application")
    ;; verify required entries
    (when-not prj-application
      (println ":appengine-application required in project.clj")
      (System/exit 1))
    (when-not prj-display-name
      (println ":appengine-display-name required in project.clj")
      (System/exit 1))
    ;; set up the required paths
    (when-not (.exists resources-dir)
      (.mkdir resources-dir)
      (println "created resources directory" (.getPath resources-dir)))
    (when-not (.exists war-dir)
      (.mkdir war-dir)
      (println "created war directory" (.getPath war-dir)))
    (when-not (.exists WEB-INF-dir)
      (.mkdir WEB-INF-dir)
      (println "created WEB-INF directory" (.getPath WEB-INF-dir)))
    ;; write a base entry point servlet file
    (let [src-dir (File. (:source-path project))
          src-base-namespace-dir (File. src-dir (dash_ prj-application))
          entry-servlet-file (File. src-base-namespace-dir (str prj-servlet ".clj"))]
      (when-not (.exists src-base-namespace-dir)
        (.mkdir src-base-namespace-dir)
        (println "created source base namespace directory" (.getPath src-base-namespace-dir)))
      (when-not (.exists entry-servlet-file)
        (with-open [writer (FileWriter. entry-servlet-file)]
          (.write writer (format app-servlet-src
                                 (_dash prj-application)
                                 prj-servlet
                                 (_dash prj-application))))
        (println "created base entry point servlet" (.getPath entry-servlet-file))))
    ;; add required configuration files
    (let [in-web-xml (-> (clojure.lang.RT/baseLoader)
                         (.getResourceAsStream "web.xml"))
          in-appengine-web-xml (-> (clojure.lang.RT/baseLoader)
                                   (.getResourceAsStream "appengine-web.xml"))
          out-web-xml (File. WEB-INF-dir "web.xml")
          out-appengine-web-xml (File. WEB-INF-dir "appengine-web.xml")]
      (when-not (.exists out-web-xml)
        (xpath-replace-all in-web-xml out-web-xml
                           {"//display-name" prj-display-name
                            "//servlet-class" (str (dash_ prj-application)
                                                   "."
                                                   (dash_ prj-servlet))})
        (println "web.xml written to" (.getPath out-web-xml)))
      (when-not (.exists out-appengine-web-xml)
        (xpath-replace-all in-appengine-web-xml out-appengine-web-xml
                           {"//application" prj-application})
        (println "appengine-web.xml written to" (.getPath out-appengine-web-xml))))))

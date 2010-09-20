(ns leiningen.appengine-new
  "Create the skeleton of a Google App Engine application."
  (:use appengine-magic.utils)
  (:import [java.io File FileWriter]))


(def app-servlet-src
     (str "(ns %s.%s\n"
          "  (:gen-class :extends javax.servlet.http.HttpServlet)\n"
          "  (:use %s.core)\n"
          "  (:use [appengine-magic.servlet :only [servlet]]))\n"
          "\n"
          "(defn -service [this request response]\n"
          "  (servlet %s-app))\n"))


(def app-core-ns-src
     (str "(ns %s.core\n"
          "  (:require [appengine-magic.core :as am]))\n"
          "\n"
          "\n"
          "(defn %s-app-handler [request]\n"
          "  {:status 200\n"
          "   :headers {\"Content-Type\" \"text/plain\"}\n"
          "   :body \"Hello, world!\"})\n"
          "\n"
          "\n"
          "(am/def-appengine-app %s-app #'%s-app-handler)"))


(defn appengine-new [project]
  (let [resources-dir (File. (:resources-path project))
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")
        prj-application (:appengine-application project)
        prj-display-name (:appengine-display-name project)
        prj-servlet "app_servlet"]
    (println "making a skeleton for a Google App Engine application")
    ;; verify required entries
    (when-not prj-application
      (println ":appengine-application required in project.clj (a string usable as an identifier)")
      (System/exit 1))
    (when-not prj-display-name
      (println ":appengine-display-name required in project.clj (a free-form string)")
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
    ;; write some base source files
    (let [src-dir (File. (:source-path project))
          src-base-namespace-dir (File. src-dir (dash_ prj-application))
          entry-servlet-file (File. src-base-namespace-dir (str prj-servlet ".clj"))
          core-ns-file (File. src-base-namespace-dir "core.clj")]
      (when-not (.exists src-base-namespace-dir)
        (.mkdir src-base-namespace-dir)
        (println "created source base namespace directory" (.getPath src-base-namespace-dir)))
      ;; write a base entry point servlet file
      (when-not (.exists entry-servlet-file)
        (with-open [writer (FileWriter. entry-servlet-file)]
          (.write writer (format app-servlet-src
                                 (_dash prj-application)
                                 prj-servlet
                                 (_dash prj-application)
                                 (_dash prj-application))))
        (println "created base entry point servlet" (.getPath entry-servlet-file)))
      ;; write a core namespace file
      (when-not (.exists core-ns-file)
        (with-open [writer (FileWriter. core-ns-file)]
          (.write writer (format app-core-ns-src
                                 (_dash prj-application)
                                 (_dash prj-application)
                                 (_dash prj-application)
                                 (_dash prj-application))))
        (println "created core namespace file" (.getPath core-ns-file))))
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

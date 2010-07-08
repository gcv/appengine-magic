(ns leiningen.appengine-new
  "Create the skeleton of a Google App Engine application."
  (:import [java.io File FileInputStream FileWriter InputStream]
           org.xml.sax.InputSource
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.xpath XPathFactory XPathConstants]
           javax.xml.transform.TransformerFactory
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult))


(def app-servlet-src
     (str "(ns %s.%s\n"
          "  (:gen-class :extends javax.servlet.http.HttpServlet)\n"
          "  ;; Replace the ... below with the namespace which defines your appengine-app.\n"
          "  (:use %s....)\n"
          "  (:use [ring.util.servlet :only [defservice]]))\n"
          "\n"
          ";; Replace the ... below with the var containing your appengine-app.\n"
          "(defservice ...)\n"))


(defn dash_ [s]
  (.replaceAll s "-" "_"))


(defn _dash [s]
  (.replaceAll s "_" "-"))


(defn xpath-replace-all [input out-file expr-map]
  (let [input (if (instance? String input) (File. input) input)
        out-file (if (instance? String out-file) (File. out-file) out-file)
        in-stream (if (instance? InputStream input) input (FileInputStream. input))
        input-source (InputSource. in-stream)
        doc (.parse (.. (DocumentBuilderFactory/newInstance) newDocumentBuilder) input-source)
        xpath (.newXPath (XPathFactory/newInstance))]
    (doseq [[xpath-raw-expr new-value] expr-map]
      (let [xpath-expr (.compile xpath xpath-raw-expr)
            xpath-expr-nodes (.evaluate xpath-expr doc XPathConstants/NODESET)]
        (dotimes [i (.getLength xpath-expr-nodes)]
          (.setTextContent (.item xpath-expr-nodes i) new-value))))
    (let [source (DOMSource. doc)
          result (StreamResult. out-file)
          transformer (.newTransformer (TransformerFactory/newInstance))]
      (.transform transformer source result))))


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
      (if (.exists out-web-xml)
          (println out-web-xml "already exists, not overwriting")
          (do (xpath-replace-all in-web-xml out-web-xml
                                 {"//display-name" prj-display-name
                                  "//servlet-class" (str (dash_ prj-application)
                                                         "."
                                                         (dash_ prj-servlet))})
              (println "web.xml written to" (.getPath out-web-xml))))
      (if (.exists out-appengine-web-xml)
          (println out-appengine-web-xml "already exists, not overwriting")
          (do (xpath-replace-all in-appengine-web-xml out-appengine-web-xml
                                 {"//application" prj-application})
              (println "appengine-web.xml written to" (.getPath out-appengine-web-xml)))))
    ;; done
    (println "Google App Engine skeleton for" prj-display-name "created")))

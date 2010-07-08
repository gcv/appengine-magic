(ns leiningen.appengine-new
  "Create the skeleton of a Google App Engine application."
  (:import [java.io File FileInputStream InputStream]
           org.xml.sax.InputSource
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.xpath XPathFactory XPathConstants]
           javax.xml.transform.TransformerFactory
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult))


(defn xpath-replace-all [input out-file xpath-raw-expr new-value]
  (let [input (if (instance? String input) (File. input) input)
        out-file (if (instance? String out-file) (File. out-file) out-file)
        in-stream (if (instance? InputStream input) input (FileInputStream. input))
        input-source (InputSource. in-stream)
        doc (.parse (.. (DocumentBuilderFactory/newInstance) newDocumentBuilder) input-source)
        xpath (.newXPath (XPathFactory/newInstance))
        xpath-expr (.compile xpath xpath-raw-expr)
        xpath-expr-nodes (.evaluate xpath-expr doc XPathConstants/NODESET)]
    (dotimes [i (.getLength xpath-expr-nodes)]
      (.setTextContent (.item xpath-expr-nodes i) new-value))
    (let [source (DOMSource. doc)
          result (StreamResult. out-file)
          transformer (.newTransformer (TransformerFactory/newInstance))]
      (.transform transformer source result))))


(defn appengine-new [project]
  (let [resources-dir (File. (:resources-path project))
        war-dir (File. resources-dir "war")
        WEB-INF-dir (File. war-dir "WEB-INF")]
    (println "making new App Engine project")
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
    ;; add required configuration files
    (let [in-web-xml (-> (clojure.lang.RT/baseLoader)
                         (.getResourceAsStream "web.xml"))
          in-appengine-web-xml (-> (clojure.lang.RT/baseLoader)
                                   (.getResourceAsStream "appengine-web.xml"))
          out-web-xml (File. WEB-INF-dir "web.xml")
          out-appengine-web-xml (File. WEB-INF-dir "appengine-web.xml")]
      (if (.exists out-web-xml)
          (println out-web-xml "already exists, not overwriting")
          (do (xpath-replace-all in-web-xml out-web-xml "//display-name" "Coronado Bay")
              (println "web.xml written to" (.getPath out-web-xml))))
      (if (.exists out-appengine-web-xml)
          (println out-appengine-web-xml "already exists, not overwriting")
          (do (xpath-replace-all in-appengine-web-xml out-appengine-web-xml
                                 "//application" "coronado-bay")
              (println "appengine-web.xml written to" (.getPath out-appengine-web-xml)))))
    ;; done
    (println "App Engine project" "Coronado Bay" "created; you should add it to source control")))

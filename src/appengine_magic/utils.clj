(ns appengine-magic.utils
  (:import [java.io File FileInputStream FileWriter InputStream]
           org.xml.sax.InputSource
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.xpath XPathFactory XPathConstants]
           javax.xml.transform.TransformerFactory
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult))


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

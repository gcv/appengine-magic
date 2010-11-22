(ns appengine-magic.utils
  (:import [java.io File FileInputStream FileWriter InputStream OutputStream]
           java.nio.ByteBuffer
           [java.nio.channels Channel Channels ReadableByteChannel WritableByteChannel]
           org.xml.sax.InputSource
           javax.xml.parsers.DocumentBuilderFactory
           [javax.xml.xpath XPathFactory XPathConstants]
           javax.xml.transform.TransformerFactory
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult))


;;; Adapted from: http://groups.google.com/group/clojure/msg/5206fac13144ea99
(defmacro record
  "A dynamic factory for Clojure record objects. Takes either a map of key-value
   pairs to be used for the record, or just the key-value pairs as keyword
   arguments. Example: (record Record :a 1 :b 2) or (record Record {:a 1 :b 2})."
  [record-type & args]
  (let [vals-map (if (= 1 (count args))
                     (first args)
                     (apply hash-map args))]
    `(let [constructor# (first (.getDeclaredConstructors ~record-type))
           number-constructor-parameters# (alength (.getParameterTypes constructor#))]
       (merge (.newInstance constructor#
                            (make-array Object number-constructor-parameters#))
              ~vals-map))))


(defn copy-stream [^InputStream input, ^OutputStream output]
  (with-open [^ReadableByteChannel in-channel (Channels/newChannel input)
              ^WritableByteChannel out-channel (Channels/newChannel output)]
    (let [^ByteBuffer buf (ByteBuffer/allocateDirect (* 4 1024))]
      (loop []
        (when-not (= -1 (.read in-channel buf))
          (.flip buf)
          (.write out-channel buf)
          (.compact buf)
          (recur)))
      (.flip buf)
      (loop [] ; drain the buffer
        (when (.hasRemaining buf)
          (.write out-channel buf)
          (recur))))))


(defn dash_ [s]
  (.replaceAll s "-" "_"))


(defn _dash [s]
  (.replaceAll s "_" "-"))


;;; TODO: xpath-replace-all and xpath-value could both use some macro to help
;;; avoid the repeated Java code.
(defn xpath-replace-all [input out-file expr-map]
  (let [input (if (instance? String input) (File. input) input)
        out-file (if (instance? String out-file) (File. out-file) out-file)
        in-stream (if (instance? InputStream input) input (FileInputStream. input))]
    (try
      (let [input-source (InputSource. in-stream)
            doc (.parse (.. (DocumentBuilderFactory/newInstance) newDocumentBuilder)
                        input-source)
            xpath (.newXPath (XPathFactory/newInstance))]
        (doseq [[xpath-raw-expr new-value] expr-map]
          (let [xpath-expr (.compile xpath xpath-raw-expr)
                xpath-expr-nodes (.evaluate xpath-expr doc XPathConstants/NODESET)]
            (dotimes [i (.getLength xpath-expr-nodes)]
              (.setTextContent (.item xpath-expr-nodes i) new-value))))
        (let [source (DOMSource. doc)
              result (StreamResult. out-file)
              transformer (.newTransformer (TransformerFactory/newInstance))]
          (.transform transformer source result)))
      ;; clean up
      (finally
       (when (instance? FileInputStream in-stream)
         (.close in-stream))))))


;;; TODO: xpath-replace-all and xpath-value could both use some macro to help
;;; avoid the repeated Java code.
(defn xpath-value [input raw-xpath-expr]
  (let [input (if (instance? String input) (File. input) input)
        in-stream (if (instance? InputStream input) input (FileInputStream. input))
        input-source (InputSource. in-stream)]
    (try
      (let [doc (.parse (.. (DocumentBuilderFactory/newInstance) newDocumentBuilder)
                        input-source)
            xpath (.newXPath (XPathFactory/newInstance))
            xpath-expr (.compile xpath raw-xpath-expr)
            xpath-expr-nodes (.evaluate xpath-expr doc XPathConstants/NODESET)]
        (doall (map #(.getTextContent (.item xpath-expr-nodes %))
                    (range (.getLength xpath-expr-nodes)))))
      ;; clean up
      (finally
       (when (instance? FileInputStream in-stream)
         (.close in-stream))))))

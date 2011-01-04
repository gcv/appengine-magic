(ns appengine-magic.utils
  (:import [java.io File FileInputStream FileWriter InputStream OutputStream]
           java.nio.ByteBuffer
           [java.nio.channels Channel Channels ReadableByteChannel WritableByteChannel]
           org.xml.sax.InputSource
           [javax.xml parsers.DocumentBuilderFactory
            xpath.XPathFactory xpath.XPathConstants
            transform.TransformerFactory transform.dom.DOMSource
            transform.stream.StreamResult]))


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


(defn derefify-future
  "Cribbed from clojure.core/future-call. Returns the result of a custom
   function of the future itself for deref."
  [f & {:keys [deref-fn] :or {deref-fn (fn [f] (.get f))}}]
  (reify
    ;; clojure.lang.IDeref interface
    clojure.lang.IDeref
    (deref [this] (deref-fn this))
    ;; java.util.concurrent.Future interface
    java.util.concurrent.Future
    (get [_] (.get f))
    (get [_ timeout unit] (.get f timeout unit))
    (isCancelled [_] (.isCancelled f))
    (isDone [_] (.isDone f))
    (cancel [_ interrupt?] (.cancel f interrupt?))))


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


(defn os-type []
  (let [os-name (.toLowerCase (System/getProperty "os.name"))]
    (cond (.startsWith os-name "mac os x")      :mac
          (.startsWith os-name "windows")       :windows
          (.startsWith os-name "linux")         :linux
          (re-matches #".*bsd.*" os-name)       :bsd
          (or (.startsWith os-name "solaris")
              (.startsWith os-name "sunos")
              (.startsWith os-name "irix")
              (.startsWith os-name "hp-ux")
              (.startsWith os-name "aix")
              (re-matches #".*unix.*" os-name)) :unix
              :else nil)))

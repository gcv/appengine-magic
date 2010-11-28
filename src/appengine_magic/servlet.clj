;;; This code is adapted from Ring (http://github.com/mmcgrana/ring).
;;;
;;; Required changes from Ring:
;;;   1. Remove dependencies which use Java classes blacklisted in App Engine.
;;;   2. Include raw cookie data retrieved using the servlet API. This does not
;;;      preclude using Ring's cookie middleware, but is required separately:
;;;      App Engine services do not always use standard-compliant cookies which
;;;      the Ring middleware parses correctly.


(ns appengine-magic.servlet
  (:use [appengine-magic.utils :only [copy-stream]])
  (:import [java.io File FileInputStream InputStream ByteArrayInputStream OutputStream]
           [javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse]))


(defn- get-headers [^HttpServletRequest request]
  (reduce (fn [headers, ^String name]
            (assoc headers (.toLowerCase name) (.getHeader request name)))
          {}
          (enumeration-seq (.getHeaderNames request))))


(defn- make-request-map [^HttpServlet servlet
                         ^HttpServletRequest request
                         ^HttpServletResponse response]
  {:servlet            servlet
   :response           response
   :request            request
   :servlet-context    (.getServletContext servlet)
   :server-port        (.getServerPort request)
   :server-name        (.getServerName request)
   :remote-addr        (.getRemoteAddr request)
   :uri                (.getRequestURI request)
   :query-string       (.getQueryString request)
   :scheme             (keyword (.getScheme request))
   :request-method     (keyword (.toLowerCase (.getMethod request)))
   :headers            (get-headers request)
   :content-type       (.getContentType request)
   :content-length     (.getContentLength request)
   :character-encoding (.getCharacterEncoding request)
   :body               (.getInputStream request)})


(defn- set-response-headers [^HttpServletResponse response, headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
        (.setHeader response key val-or-vals)
        (doseq [val val-or-vals]
          (.addHeader response key val))))
  ;; Use specific servlet API methods for some headers:
  (.setCharacterEncoding response "UTF-8")
  (when-let [content-type (get headers "Content-Type")]
    (.setContentType response content-type)))


(defn- set-response-body [^HttpServletResponse response, body]
  (cond
   ;; just a string
   (string? body)
   (with-open [writer (.getWriter response)]
     (.print writer body))
   ;; any Clojure seq
   (seq? body)
   (with-open [writer (.getWriter response)]
     (doseq [chunk body]
       (.print writer (str chunk))
       (.flush writer)))
   ;; a Java InputStream
   (instance? InputStream body)
   (with-open [out (.getOutputStream response)
               ^InputStream b body]
     (copy-stream b out)
     (.flush out))
   ;; serve up a File
   (instance? File body)
   (let [^File f body]
     (with-open [stream (FileInputStream. f)]
       (set-response-body response stream)))
   ;; serve up a byte array
   (instance? (class (byte-array 0)) body)
   (with-open [in (ByteArrayInputStream. body)]
     (set-response-body response in))
   ;; nothing
   (nil? body) nil
   ;; unknown
   :else (throw (RuntimeException. (str "handler response body unknown" body)))))


(defn- adapt-servlet-response [^HttpServletResponse response,
                               {:keys [commit? status headers body]
                                :or {commit? true}}]
  (when commit?
    (if status
        (.setStatus response status)
        (throw (RuntimeException. "handler response status not set")))
    (when headers (set-response-headers response headers))
    (when body (set-response-body response body))))


(defn make-servlet-service-method [ring-handler]
  (fn [^HttpServlet servlet, ^HttpServletRequest request, ^HttpServletResponse response]
    (let [response-map (doall (ring-handler (make-request-map servlet request response)))]
      (when-not response-map
        (throw (RuntimeException. "handler returned nil (no response map)")))
      (adapt-servlet-response response response-map))))


(defn servlet [ring-handler]
  (proxy [HttpServlet] []
    (service [^HttpServletRequest request, ^HttpServletResponse response]
      ((make-servlet-service-method ring-handler) this request response))))

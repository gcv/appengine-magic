(ns appengine-magic.services.urlfetch
  (:import [com.google.appengine.api.urlfetch
            URLFetchServiceFactory
            FetchOptions
            FetchOptions$Builder
            HTTPHeader
            HTTPRequest
            HTTPMethod])
  (:require [clojure.contrib.string :as string]
            [appengine-magic.services.memcache :as memcache]))

(defonce *urlfetch-service* (atom nil))

(defn get-urlfetch-service []
  (do (when (nil? @*urlfetch-service*)
	(reset! *urlfetch-service* (URLFetchServiceFactory/getURLFetchService)))
      @*urlfetch-service*))

(defn- urlify [url] (if (string? url) (java.net.URL. url) url))

(defrecord HTTPResponse [content
			 final-url
			 headers
			 response-code])

(defn- parse-headers [headers]
  (zipmap (map #(keyword (.getName %)) headers)
	  (map #(.getValue %) headers)))

(defn- make-headers [header-map]
  (map
   (fn [[name-key val]]
     (com.google.appengine.api.urlfetch.HTTPHeader.
      (string/as-str name-key)
      val))
   header-map))

(defn- parse-response
  [^com.google.appengine.api.urlfetch.HTTPResponse r]
  (HTTPResponse. (.getContent r)
		 (.getFinalUrl r)
		 (parse-headers (.getHeaders r))
		 (.getResponseCode r)))

(defn make-request
  [url &
   {:keys [method headers payload allow-truncate follow-redirects deadline]
    :or {method :get
          headers {}
          payload nil
          allow-truncate FetchOptions/DEFAULT_ALLOW_TRUNCATE
          follow-redirects FetchOptions/DEFAULT_FOLLOW_REDIRECTS
          deadline FetchOptions/DEFAULT_DEADLINE}}]
  (let [fetch-options (FetchOptions$Builder/withDefaults)]
    (if allow-truncate
      (.allowTruncate fetch-options)
      (.disallowTruncate fetch-options))
    (if follow-redirects
      (.followRedirects fetch-options)
      (.doNotFollowRedirects fetch-options))
    (.setDeadline fetch-options deadline)
    (let [method-obj (method {:delete HTTPMethod/DELETE
                              :get    HTTPMethod/GET
                              :head   HTTPMethod/HEAD
                              :post   HTTPMethod/POST
                              :put    HTTPMethod/PUT})
          request    (HTTPRequest. url method-obj fetch-options)]
      (doseq [h (make-headers headers)] (.addHeader request h))
      (when-not (nil? payload)
        (.setPayload request payload))
      request)))

(defn fetch
  "Fetch a URL using AppEngine's URLFetch service.

  url can be either a string or a java.net.URL object.

  Optional parameters:
    :method           :get (the default), :delete, :head, :post or :put.
    :headers          A map from :name to string.
    :payload          Java byte array
    :allow-truncate   If true, allow appengine to truncate a big response
                      without error. If false, throw an exception instead.
    :follow-redirects Self-explanatory boolean.
    :deadline         Deadline for the request, in seconds.

  Note that :allow-truncate, :follow-redirects and :deadline use the
  AppEngine defaults, whatever they are."
  
  [url & opts]
  (parse-response (.fetch (get-urlfetch-service)
                          (apply make-request (urlify url) opts))))

(defn- derefify-future
  "Cribbed from clojure.core/future-call, but returns the result of a
   custom function of no-args for deref."  
  [f deref-fn]
  (reify
   clojure.lang.IDeref
   (deref [_] (deref-fn))
   java.util.concurrent.Future
   (get [_] (.get f))
   (get [_ timeout unit] (.get f timeout unit))
   (isCancelled [_] (.isCancelled f))
   (isDone [_] (.isDone f))
   (cancel [_ interrupt?] (.cancel f interrupt?))))

(defn fetch-async
  "Just like fetch, but returns a future-like object."
  [url & opts]
  (let [f (.fetchAsync (get-urlfetch-service)
                       (apply make-request (urlify url) opts))]
    (derefify-future f #(parse-response (.get f)))))

;; A 2-level cache for fetch results; first in memory, then memcache.
(def *memcache-namespace* "appengine-magic.services.urlfetch")
(def *memory-cache* (atom {}))
(defn- cache-response [url response]
  ;; save unnecessary memcache calls by checking for relevant headers
  (if (or (:Last-Modified (:headers response))
          (:ETag (:headers response)))
    (do (swap! *memory-cache* assoc url response)
        (memcache/put! url response :namespace *memcache-namespace*)
        response)))
(defn- uncache-response [url]
  (if-let [response (@*memory-cache* url)]
    response
    (memcache/get url :namespace *memcache-namespace*)))

(defn- make-cache-sensitive-request [old-response url & opts]
  (let [request (apply make-request url opts)]
    (if old-response
      (if-let [etag (:ETag (:headers old-response))]
        (.setHeader request (HTTPHeader. "If-None-Match" etag))
        (if-let [last-modified (:Last-Modified (:headers old-response))]
          (.setHeader request
                      (HTTPHeader. "If-Modified-Since" last-modified)))))
    request))

(defn memcached-fetch
  "Like fetch, but caches its results in memcache and uses ETag or
   Last-Modified headers to avoid doing full-fetches when possible."
  [url & opts]
  (let [url          (urlify url)
        old-response (uncache-response url)
        request      (apply make-cache-sensitive-request old-response url opts)
        response     (parse-response (.fetch (get-urlfetch-service) request))]
    (if (= 304 (:response-code response))
      old-response
      (cache-response url response))))

(defn memcached-fetch-async
  "Like fetch-async, but caches its results like memcached-fetch."
  [url & opts]
  (let [url          (urlify url)
        old-response (uncache-response url)
        request      (apply make-cache-sensitive-request old-response url opts)
        responsef    (.fetchAsync (get-urlfetch-service) request)]
    (derefify-future responsef
                     (fn []
                       (let [response (parse-response (.get responsef))]
                         (if (= 304 (:response-code response))
                           old-response
                           (cache-response url response)))))))

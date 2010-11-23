(ns appengine-magic.services.url-fetch
  (:import [com.google.appengine.api.urlfetch
            URLFetchServiceFactory
            FetchOptions
            FetchOptions$Builder
            HTTPHeader
            HTTPRequest
            HTTPMethod]))

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
      (if (keyword? name-key)
          (.substring (str name-key) 1)
          (str name-key))
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
    (.setDeadline fetch-options (double deadline))
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

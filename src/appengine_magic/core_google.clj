(in-ns 'appengine-magic.core)

(import '[java.io File FileInputStream BufferedInputStream]
        'com.google.apphosting.api.ApiProxy)


(defn open-resource-stream [resource-name]
  (let [f (File. resource-name)]
    (BufferedInputStream. (FileInputStream. resource-name))))


(defn appengine-base-url [& {:keys [https?] :or {https? false}}]
  (when (= :dev-appserver (appengine-environment-type))
    (throw (RuntimeException.
            "appengine-magic.core/appengine-base-url not supported in dev-appserver.sh")))
  (str (if https? "https" "http")
       "://"
       (-> (ApiProxy/getCurrentEnvironment) .getAppId) ".appspot.com"))


(defmacro def-appengine-app [app-var-name handler & [args]]
  `(def ~app-var-name ~handler))

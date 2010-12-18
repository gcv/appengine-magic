(in-ns 'appengine-magic.core)

(import '[java.io File FileInputStream BufferedInputStream])


(defn appengine-base-url [& {:keys [https?] :or {https? false}}]
  (when (= :dev-appserver (appengine-environment-type))
    (throw (RuntimeException.
            "appengine-magic.core/appengine-base-url not supported in dev-appserver.sh")))
  (str (if https? "https" "http")
       "://" (appengine-app-id) ".appspot.com"))


(defmacro def-appengine-app [app-var-name handler & [args]]
  `(def ~app-var-name ~handler))

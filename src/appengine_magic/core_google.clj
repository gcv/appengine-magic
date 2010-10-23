(in-ns 'appengine-magic.core)

(import '[java.io File FileInputStream BufferedInputStream])


(defn open-resource-stream [resource-name]
  (let [f (File. resource-name)]
    (BufferedInputStream. (FileInputStream. resource-name))))


(defmacro def-appengine-app [app-var-name handler & [args]]
  `(def ~app-var-name ~handler))

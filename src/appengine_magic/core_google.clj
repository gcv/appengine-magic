(in-ns 'appengine-magic.core)


(defmacro def-appengine-app [app-var-name handler & [args]]
  `(def ~app-var-name ~handler))

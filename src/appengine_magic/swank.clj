;;; This code is adapted from Ring (http://github.com/mmcgrana/ring) and patches
;;; by James Reeves (https://github.com/weavejester/ring/blob/908f38ec3e583c3f0ee193ede0ee1f1608f385c9/ring-devel/src/ring/middleware/swank.clj)
;;;
;;; XXX: If this makes it into mainline Ring, remove this file and use
;;; wrap-swank from the Ring distribution.

(ns appengine-magic.swank)


(defn- make-swank-middleware []
  (eval
   '(fn [handler]
      (let [conn swank.core.connection/*current-connection*]
        (fn [request]
          (swank.core.connection/with-connection conn
            (handler request)))))))


(defn wrap-swank [handler]
  (if (find-ns 'swank.core.connection)
      (let [middleware (make-swank-middleware)]
        (middleware handler))
      handler))

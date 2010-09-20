(ns appengine-magic.servlet
  (:import (javax.servlet.http HttpServlet HttpServletRequest HttpServletResponse)))


(defn servlet [ring-handler]
  (proxy [HttpServlet] []
    (service [request response]
       ...)))

(ns appengine-magic.services.user
  (:import [com.google.appengine.api.users User UserService UserServiceFactory]))


(defonce *default-user-service* (atom nil))


(defn- get-user-service [& {:keys [service]}]
  (if (nil? service)
      (do (when (nil? @*default-user-service*)
            (reset! *default-user-service* (UserServiceFactory/getUserService)))
          @*default-user-service*)
      service))


(defn current-user [& {:keys [service]}]
  (let [service (get-user-service :service service)]
    (.getCurrentUser service)))


(defn login-url [& {:keys [service destination]
                    :or {destination "/"}}]
  (let [service (get-user-service :service service)]
    (.createLoginURL service destination)))


(defn logout-url [& {:keys [service destination]
                     :or {destination "/"}}]
  (let [service (get-user-service :service service)]
    (.createLogoutURL service destination)))

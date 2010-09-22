(ns appengine-magic.services.user
  (:import [com.google.appengine.api.users User UserService UserServiceFactory]))


(defonce *default-user-service* (atom nil))


(defn- ensure-user-service [user-service]
  (when (nil? @user-service)
    (reset! user-service (UserServiceFactory/getUserService))))


(defn current-user [& {:keys [user-service]
                       :or {user-service *default-user-service*}}]
  (ensure-user-service user-service)
  (.getCurrentUser @user-service))


(defn login-url [& {:keys [user-service destination]
                    :or {user-service *default-user-service*
                         destination "/"}}]
  (ensure-user-service user-service)
  (.createLoginURL @user-service destination))


(defn logout-url [& {:keys [user-service destination]
                     :or {user-service *default-user-service*
                          destination "/"}}]
  (ensure-user-service user-service)
  (.createLogoutURL @user-service destination))

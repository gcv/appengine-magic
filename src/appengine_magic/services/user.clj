(ns appengine-magic.services.user
  (:import [com.google.appengine.api.users User UserService UserServiceFactory]))


(defonce ^:dynamic *user-service* (atom nil))


(defn get-user-service []
  (when (nil? @*user-service*)
    (reset! *user-service* (UserServiceFactory/getUserService)))
  @*user-service*)


(defn current-user []
  (.getCurrentUser (get-user-service)))


(defn user-logged-in? []
  (.isUserLoggedIn (get-user-service)))


(defn user-admin? []
  (.isUserAdmin (get-user-service)))


(defn login-url [& {:keys [destination]
                    :or {destination "/"}}]
  (.createLoginURL (get-user-service) destination))


(defn logout-url [& {:keys [destination]
                     :or {destination "/"}}]
  (.createLogoutURL (get-user-service) destination))


(defn get-email [#^User user]
  (.getEmail user))


(defn get-nickname [#^User user]
  (.getNickname user))


(defn get-auth-domain [#^User user]
  (.getAuthDomain user))

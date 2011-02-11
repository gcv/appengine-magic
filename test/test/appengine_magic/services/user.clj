(ns test.appengine-magic.services.user
  (:use clojure.test)
  (:require [appengine-magic.services.user :as du]
            [appengine-magic.testing :as ae-testing]))

(use-fixtures :each (ae-testing/local-services :all :hook-helper (ae-testing/admin-login "hoge@fuga.com")))

(deftest basics
  (let [user (du/current-user)]
    (are [x y] (= x y)
      true (du/user-logged-in?)
      true (du/user-admin?)
      false (nil? user)
      "hoge@fuga.com" (du/get-email user)
      "fuga.com" (du/get-auth-domain user)
      "hoge" (du/get-nickname user)
      )
    )
  )

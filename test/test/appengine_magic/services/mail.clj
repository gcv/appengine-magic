(ns test.appengine-magic.services.mail
  (:use clojure.test)
  (:require [appengine-magic.services.mail :as mail]
            [appengine-magic.testing :as ae-testing]))


(use-fixtures :each (ae-testing/local-services :all))


(deftest basics
  (let [msg (mail/make-message :to "one@example.com"
                               :from "two@example.com"
                               :subject "test"
                               :text-body "hello world")]
    (mail/send msg)))

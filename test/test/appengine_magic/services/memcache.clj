(ns test.appengine-magic.services.memcache
  (:use clojure.test)
  (:require [appengine-magic.testing :as ae-testing]
            [appengine-magic.services.memcache :as ae-memcache]))


(use-fixtures :each (ae-testing/local-services :all))


(deftest stats
  (is (= (appengine-magic.services.memcache.Statistics. 0 0 0 0 0 0)
         (ae-memcache/statistics))))


(deftest basic-ops
  (is (not (ae-memcache/contains? "one")))
  (is (nil? (ae-memcache/get "one")))
  (is (ae-memcache/put "one" 1))
  (is (ae-memcache/contains? "one"))
  (is (= 1 (ae-memcache/get "one")))
  (is (ae-memcache/put "two" 2))
  (is (ae-memcache/delete "one"))
  (is (not (ae-memcache/contains? "one")))
  (is (ae-memcache/contains? "two"))
  (is (= 2 (ae-memcache/get "two")))
  (is (ae-memcache/increment "two" 1))
  (is (= 3 (ae-memcache/get "two")))
  (is (ae-memcache/increment "two" 3))
  (is (= 6 (ae-memcache/get "two")))
  (ae-memcache/clear-all)
  (is (not (ae-memcache/contains? "two"))))


(deftest sequential-ops
  (is (ae-memcache/put-map {"one" 1, "two" 2, "three" 3, "four", 4}))
  (is (ae-memcache/contains? "one"))
  (is (ae-memcache/contains? "two"))
  (is (ae-memcache/contains? "three"))
  (is (ae-memcache/contains? "four"))
  (is (ae-memcache/delete ["one" "three"]))
  (is (not (ae-memcache/contains? "one")))
  (is (ae-memcache/contains? "two"))
  (is (not (ae-memcache/contains? "three")))
  (is (ae-memcache/contains? "four"))
  (is (ae-memcache/increment-map {"two" (long 10), "four" (long 15)}))
  (is (= {"two" 12, "four" 19} (ae-memcache/get ["two" "four"]))))


(deftest namespaced-ops
  (ae-memcache/put "one" 1 :namespace "Alpha")
  (ae-memcache/put "one" 10 :namespace "Bravo")
  (ae-memcache/put "one" 100 :namespace "Charlie")
  (ae-memcache/put "two" 101 :namespace "Charlie")
  (is (not (ae-memcache/contains? "one")))
  (is (= 1 (ae-memcache/get "one" :namespace "Alpha")))
  (is (= 10 (ae-memcache/get "one" :namespace "Bravo")))
  (is (= {"one" 100, "two" 101}
         (ae-memcache/get ["one" "two"] :namespace "Charlie")))
  (ae-memcache/delete "one" :namespace "Bravo")
  (is (ae-memcache/contains? "one" :namespace "Alpha"))
  (is (ae-memcache/contains? "one" :namespace "Charlie"))
  (ae-memcache/increment "two" 1 :namespace "Charlie")
  (ae-memcache/increment-map {"one" (long 3), "two" (long 4)} :namespace "Charlie")
  (is (= {"one" 103, "two" 106}
         (ae-memcache/get ["one" "two"] :namespace "Charlie")))
  (is (= 1 (ae-memcache/get "one" :namespace "Alpha"))))


(deftest policy-replace
  (ae-memcache/put "one" 1 :policy :replace-only)
  (is (not (ae-memcache/contains? "one")))
  (ae-memcache/put "one" 1)
  (is (ae-memcache/contains? "one"))
  (is (= 1 (ae-memcache/get "one")))
  (ae-memcache/put "one" 100 :policy :add-if-not-present)
  (is (= 1 (ae-memcache/get "one")))
  (ae-memcache/put "one" 101)
  (is (= 101 (ae-memcache/get "one"))))

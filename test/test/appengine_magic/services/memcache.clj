(ns test.appengine-magic.services.memcache
  (:use clojure.test)
  (:require [appengine-magic.testing :as ae-testing]
            [appengine-magic.services.memcache :as memcache]))


(use-fixtures :each (ae-testing/local-services :all))


(deftest stats
  (is (= (appengine-magic.services.memcache.Statistics. 0 0 0 0 0 0)
         (memcache/statistics))))


(deftest basic-ops
  (is (not (memcache/contains? "one")))
  (is (nil? (memcache/get "one")))
  (is (memcache/put! "one" 1))
  (is (memcache/contains? "one"))
  (is (= 1 (memcache/get "one")))
  (is (memcache/put! "two" 2))
  (is (memcache/delete! "one"))
  (is (not (memcache/contains? "one")))
  (is (memcache/contains? "two"))
  (is (= 2 (memcache/get "two")))
  (is (memcache/increment! "two" 1))
  (is (= 3 (memcache/get "two")))
  (is (memcache/increment! "two" 3))
  (is (= 6 (memcache/get "two")))
  (memcache/clear-all!)
  (is (not (memcache/contains? "two"))))


(deftest sequential-ops
  (is (memcache/put-map! {"one" 1, "two" 2, "three" 3, "four", 4}))
  (is (memcache/contains? "one"))
  (is (memcache/contains? "two"))
  (is (memcache/contains? "three"))
  (is (memcache/contains? "four"))
  (is (memcache/delete! ["one" "three"]))
  (is (not (memcache/contains? "one")))
  (is (memcache/contains? "two"))
  (is (not (memcache/contains? "three")))
  (is (memcache/contains? "four"))
  (is (memcache/increment-map! {"two" (long 10), "four" (long 15)}))
  (is (= {"two" 12, "four" 19} (memcache/get ["two" "four"]))))


(deftest namespaced-ops
  (memcache/put! "one" 1 :namespace "Alpha")
  (memcache/put! "one" 10 :namespace "Bravo")
  (memcache/put! "one" 100 :namespace "Charlie")
  (memcache/put! "two" 101 :namespace "Charlie")
  (is (not (memcache/contains? "one")))
  (is (= 1 (memcache/get "one" :namespace "Alpha")))
  (is (= 10 (memcache/get "one" :namespace "Bravo")))
  (is (= {"one" 100, "two" 101}
         (memcache/get ["one" "two"] :namespace "Charlie")))
  (memcache/delete! "one" :namespace "Bravo")
  (is (memcache/contains? "one" :namespace "Alpha"))
  (is (memcache/contains? "one" :namespace "Charlie"))
  (memcache/increment! "two" 1 :namespace "Charlie")
  (memcache/increment-map! {"one" (long 3), "two" (long 4)} :namespace "Charlie")
  (is (= {"one" 103, "two" 106}
         (memcache/get ["one" "two"] :namespace "Charlie")))
  (is (= 1 (memcache/get "one" :namespace "Alpha"))))


(deftest policy-replace
  (memcache/put! "one" 1 :policy :replace-only)
  (is (not (memcache/contains? "one")))
  (memcache/put! "one" 1)
  (is (memcache/contains? "one"))
  (is (= 1 (memcache/get "one")))
  (memcache/put! "one" 100 :policy :add-if-not-present)
  (is (= 1 (memcache/get "one")))
  (memcache/put! "one" 101)
  (is (= 101 (memcache/get "one"))))

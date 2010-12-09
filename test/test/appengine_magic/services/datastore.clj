(ns test.appengine-magic.services.datastore
  (:use clojure.test)
  (:require [appengine-magic.services.datastore :as ds]
            [appengine-magic.testing :as ae-testing]))


(use-fixtures :each (ae-testing/local-services :all))


(ds/defentity Author [^:key name])
(ds/defentity Article [^:key title author body comment-count])
(ds/defentity Comment [^:key subject article author body])


(deftest basics
  (let [alice (Author. "Alice")
        bob (Author. "Bob")
        charlie (Author. "Charlie")]
    (ds/save! alice)
    (ds/save! [bob charlie])
    ;; basic retrieval
    (let [alice-retrieved (ds/retrieve Author "Alice")
          alice-queried (first (ds/query :kind Author :filter (= :name "Alice")))
          [bob-retrieved charlie-retrieved] (ds/retrieve Author ["Bob" "Charlie"])]
      (is (= alice alice-retrieved))
      (is (= alice alice-queried))
      (is (= bob bob-retrieved))
      (is (= charlie charlie-retrieved)))
    ;; sorted query
    (let [[charlie-queried bob-queried alice-queried] (ds/query :kind Author
                                                                :sort [[:name :desc]])]
      (is (= alice alice-queried))
      (is (= bob bob-queried))
      (is (= charlie charlie-queried)))
    ;; deletion
    (ds/delete! bob)
    (is (= [] (ds/query :kind Author :filter (= :name "Bob"))))
    ;; just count
    (is (= 2 (ds/query :kind Author :count-only? true)))))


(deftest transactions
  (let [alice (Author. "Alice")
        article (Article. "Article 1" alice "The fine article." 0)]
    (ds/save! [alice article])
    (let [comment-1 (ds/new* Comment ["FP" article alice "Comment 1."] :parent article)
          comment-2 (ds/new* Comment ["SP" article alice "Comment 2."] :parent article)]
      (ds/with-transaction
        (ds/save! (assoc article :comment-count (inc (:comment-count article))))
        (ds/save! comment-1))
      (ds/with-transaction
        (let [article (ds/retrieve Article "Article 1")]
          (ds/save! (assoc article :comment-count (inc (:comment-count article))))
          (ds/save! comment-2))))
    (let [article (ds/retrieve Article "Article 1")]
      (is (= 2 (:comment-count article))))))

(ns test.appengine-magic.services.datastore
  (:use clojure.test)
  (:require [appengine-magic.services.datastore :as ae-ds]
            [appengine-magic.testing :as ae-testing]))


(use-fixtures :each (ae-testing/local-services :all))


(ae-ds/defentity Author [^:key name])
(ae-ds/defentity Article [^:key title author body comment-count])
(ae-ds/defentity Comment [^:key subject article author body])


(deftest basics
  (let [alice (Author. "Alice")
        bob (Author. "Bob")
        charlie (Author. "Charlie")]
    (ae-ds/save! alice)
    (ae-ds/save! [bob charlie])
    ;; basic retrieval
    (let [alice-retrieved (ae-ds/retrieve Author "Alice")
          alice-queried (first (ae-ds/query :kind Author :filter (= :name "Alice")))
          [bob-retrieved charlie-retrieved] (ae-ds/retrieve Author ["Bob" "Charlie"])]
      (is (= alice alice-retrieved))
      (is (= alice alice-queried))
      (is (= bob bob-retrieved))
      (is (= charlie charlie-retrieved)))
    ;; sorted query
    (let [[charlie-queried bob-queried alice-queried] (ae-ds/query :kind Author
                                                                   :sort [[:name :desc]])]
      (is (= alice alice-queried))
      (is (= bob bob-queried))
      (is (= charlie charlie-queried)))
    ;; deletion
    (ae-ds/delete! bob)
    (is (= [] (ae-ds/query :kind Author :filter (= :name "Bob"))))
    ;; just count
    (is (= 2 (ae-ds/query :kind Author :count-only? true)))))


(deftest transactions
  (let [alice (Author. "Alice")
        article (Article. "Article 1" alice "The fine article." 0)]
    (ae-ds/save! [alice article])
    (let [comment-1 (ae-ds/new* Comment ["FP" article alice "Comment 1."] :parent article)
          comment-2 (ae-ds/new* Comment ["SP" article alice "Comment 2."] :parent article)]
      (ae-ds/with-transaction
        (ae-ds/save! (assoc article :comment-count (inc (:comment-count article))))
        (ae-ds/save! comment-1))
      (ae-ds/with-transaction
        (let [article (ae-ds/retrieve Article "Article 1")]
          (ae-ds/save! (assoc article :comment-count (inc (:comment-count article))))
          (ae-ds/save! comment-2))))
    (let [article (ae-ds/retrieve Article "Article 1")]
      (is (= 2 (:comment-count article))))))

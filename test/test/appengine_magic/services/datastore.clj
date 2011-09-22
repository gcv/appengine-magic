(ns test.appengine-magic.services.datastore
  (:use clojure.test)
  (:require [appengine-magic.services.datastore :as ds]
            [appengine-magic.testing :as ae-testing])
  (:import com.google.appengine.api.datastore.KeyFactory))


(use-fixtures :each (ae-testing/local-services :all))


(ds/defentity Author [^:key name])
(ds/defentity Article [^:key title author body comment-count])
(ds/defentity Comment [^:key subject article author body])
(ds/defentity Note [author body])
(ds/defentity Parent [name child-counter])
(ds/defentity Child [name parent])


(deftest basics
  (let [alice (Author. "Alice")
        bob (Author. "Bob")
        charlie (Author. "Charlie")]
    (is (= alice (ds/save! alice)))
    (is (= [bob charlie] (ds/save! [bob charlie])))
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
    (is (= [alice article] (ds/save! [alice article])))
    (let [comment-1 (ds/new* Comment ["FP" article alice "Comment 1."] :parent article)
          comment-2 (ds/new* Comment ["SP" article alice "Comment 2."] :parent article)]
      (ds/with-transaction
        (ds/save! (assoc article :comment-count (inc (:comment-count article))))
        (is (= comment-1 (ds/save! comment-1))))
      (ds/with-transaction
        (let [article (ds/retrieve Article "Article 1")]
          (ds/save! (assoc article :comment-count (inc (:comment-count article))))
          (is (= comment-2 (ds/save! comment-2))))))
    (let [article (ds/retrieve Article "Article 1")]
      (is (= 2 (:comment-count article))))))


(deftest records-in-records
  (let [alice (ds/save! (Author. "Alice"))
        bob (ds/save! (Author. "Bob"))
        article-1 (ds/save! (Article. "Article 1" alice "The fine article." 0))
        article-2 (ds/save! (Article. "Article 2" bob "TFA 2." 0))
        article-3 (ds/save! (Article. "Article 3" alice "TFA 3." 0))]
    (is (= 2 (count (ds/query :kind Article :filter (= :author alice)))))))


(deftest key-strings
  (is (= "Note(\"1\")" (ds/key-str "Note" "1")))
  (is (= "Note(1)" (ds/key-str "Note" 1)))
  (is (= "Note(\"1\")" (ds/key-str Note "1")))
  (is (= "Note(1)" (ds/key-str Note 1)))
  (is (= "Note(3)" (ds/key-str Note 3) (str (KeyFactory/createKey "Note" (long 3)))))
  (is (thrown? IllegalArgumentException (ds/key-str (Note. 1 2))))
  (let [alice (ds/save! (Author. "Alice"))
        note-1 (ds/save! (Note. alice "Note 1."))
        note-2 (ds/save! (Note. alice "Note 2."))]
    (is (= "Author(\"Alice\")" (ds/key-str alice)))
    (is (= (str (ds/get-key-object note-1)) (ds/key-str note-1)))
    (is (= (str (ds/get-key-object note-2)) (ds/key-str note-2)))
    (is (= (ds/key-str (KeyFactory/keyToString (ds/get-key-object note-2)))
           (ds/key-str note-2)))))


(deftest auto-id-transactions
  (let [p (ds/save! (Parent. "alpha" 0))
        c1 (ds/new* Child ["bravo" p] :parent p)
        c2 (ds/new* Child ["charlie" p] :parent p)]
    (let [p (ds/retrieve Parent 1)
          c1 (ds/with-transaction
               (ds/save! (assoc p :child-counter (inc (:child-counter p))))
               (ds/save! c1))]
      (is (= "Parent(1)/Child(2)" (ds/key-str c1))))
    (let [p (ds/retrieve Parent 1)
          c2 (ds/with-transaction
               (ds/save! (assoc p :child-counter (inc (:child-counter p))))
               (ds/save! c2))]
      (is (= "Parent(1)/Child(3)" (ds/key-str c2)))
      (is (= 2 (:child-counter (ds/retrieve Parent 1)))))))


(deftest fancy-new*
  (let [title "Anonymous On Anonymity"
        body "Anonymity is important. The end."
        a (ds/new* Article {:title title
                            :body body
                            :comment-count 0})]
    (is (nil? (:author a)))
    (is (= title (:title a)))
    (is (= body (:body a)))))


(deftest query-params-regression
  (let [a1 (Author. "A1")
        a2 (Author. "A2")]
    (ds/save! [a1 a2])
    (is (ds/query :kind Author :chunk-size 10 :prefetch-size 10))))


(deftest count-many-objects
  (let [max 1200
        make-author (fn make-author [x]
                      (ds/save! (Author. (str "Author " x))))]
    (dotimes [i max]
      (make-author i))
    (is (= max (ds/query :kind Author :count-only? true)))))

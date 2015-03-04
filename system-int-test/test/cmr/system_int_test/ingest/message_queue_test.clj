(ns cmr.system-int-test.ingest.message-queue-test
  "Tests behavior of ingest and indexer under different message queue failure scenarios."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.index-util :as index-util]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.test-environment :as test-env]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest message-queue-concept-history-test
  (test-env/only-with-real-message-queue
    (let [coll1 (dc/collection-concept {:native-id "C1" :concept-id "C1-PROV1" :revision-id 1})
          coll2 (dc/collection-concept {:native-id "C2" :concept-id "C2-PROV1" :revision-id 1})
          coll3 (dc/collection-concept {:native-id "C3" :concept-id "C3-PROV1" :revision-id 1})
          _ (ingest/ingest-concepts [coll1 coll2 coll3
                                     (assoc coll2 :revision-id 2)
                                     (assoc coll2 :revision-id 3)])
          _ (index-util/wait-until-indexed)
          messages (index-util/get-message-queue-history)
          concept-history (index-util/concept-history messages)]
      (testing "successfully processed concepts"
        (are [concept-id revision-id states] (= states (get concept-history [concept-id revision-id]))
             "C1-PROV1" 1 [{:action "enqueue" :result "initial"}
                           {:action "process" :result "processed"}]
             "C2-PROV1" 1 [{:action "enqueue" :result "initial"}
                           {:action "process" :result "processed"}]
             "C2-PROV1" 2 [{:action "enqueue" :result "initial"}
                           {:action "process" :result "processed"}]
             "C2-PROV1" 3 [{:action "enqueue" :result "initial"}
                           {:action "process" :result "processed"}]
             "C3-PROV1" 1 [{:action "enqueue" :result "initial"}
                           {:action "process" :result "processed"}])))))

;; Setup provider
;; Test 1 - Initial index fails and retries once, completes successfully on retry
;; Turn message failures on - implement new endpoint, call new endpoint
;; Ingest collection and granule - normal way
;; Verify the collection and granule are in Oracle - metadata-db find concepts
;; Wait for at least one retry - TODO figure out how
;; Verify the collection and granule are not indexed - search returns 0 results
;; Turn on normal processing on messages - call new endpoint
;; Wait until the indexing queue is empty - wait-for-indexed
;; Verify the collection and granule are indexed - search returns correct results

;; Test 2 - all retries fail
;; Turn message failures on
;; Ingest one collection and one granule
;; Verify the collection and granule are in Oracle
;; Wait until the indexing queue is empty
;; Verify the collection and granule are not indexed
;; Manually verify retry intervals
;; Manually verify number of retries
;; Manually verify there is a message logged that we can create a Splunk alert against to
;; determine what data to index
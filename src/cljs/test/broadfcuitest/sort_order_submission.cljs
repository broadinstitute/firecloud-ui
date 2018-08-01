(ns broadfcuitest.sort-order-submission
  (:require
   [cljs.test :refer [deftest is testing]]
   [broadfcui.page.workspace.monitor.common :refer [sort-order-submission]]
   [broadfcui.page.workspace.monitor.common :refer [sort-order-sub-status]]
   [broadfcui.page.workspace.monitor.common :refer [sort-order-wf-status]]
   [broadfcui.utils :as utils]
   [broadfcui.page.workspace.monitor.common :as moncommon]))

(deftest check-sort-order-submission
  (let [mixed [{:status "Done" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
               {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
               {:status "Submitted"}
               {:status "Aborting" :workflowStatuses {:Failed 3}}
               {:status "Aborted" :workflowStatuses {:Aborted 3}}
               {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
               {:status "Done" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
               {:status "Done" :workflowStatuses {:Succeeded 3}}
               {:status "Submitted"}
               {:status "Aborting" :workflowStatuses {:blah 3}}
               {:status "Aborted" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
               {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
               {:status "Blah"}
               {:status "Done"}
               {:status "Done" :workflowStatuses {:blah 1}}]
        expected [
                  {:status "Submitted"}
                  {:status "Submitted"}
                  {:status "Aborting" :workflowStatuses {:Failed 3}}
                  {:status "Aborting" :workflowStatuses {:blah 3}}
                  {:status "Aborted" :workflowStatuses {:Aborted 3}}
                  {:status "Aborted" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Aborted 1 :Failed 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Aborted 2 :Succeeded 3}}
                  {:status "Done" :workflowStatuses {:Succeeded 3}}
                  {:status "Done"}
                  {:status "Done" :workflowStatuses {:blah 1}}
                  {:status "Blah"}]]

    (testing "mixed bag of submission statuses and underlying workflow statuses sorted"
      (is (= expected
             (sort-by (fn [submission]
                (sort-order-submission (:status submission) (:workflowStatuses submission))) mixed))))))

(deftest check-sort-order-wf-status
  (testing "wf running status 10"
    (is (= 10 (sort-order-wf-status "Running")))
    (is (= 10 (sort-order-wf-status "Submitted")))
    (is (= 10 (sort-order-wf-status "Queued")))
    (is (= 10 (sort-order-wf-status "Launching"))))
  (testing "wf failure status 21-23"
    (is (= 21 (sort-order-wf-status "Aborting")))
    (is (= 22 (sort-order-wf-status "Aborted")))
    (is (= 23 (sort-order-wf-status "Failed"))))
  (testing "wf success status 30"
    (is (= 30 (sort-order-wf-status "Succeeded"))))
  (testing "wf unknown status 80"
    (is (= 80 (sort-order-wf-status "blahblah")))))

(deftest check-sort-order-sub-status
  (testing "status 40 when sub statuses contain Failed"
    (is (= 40 (sort-order-sub-status {:Failed 1 :Aborted 1 :Succeeded 1}))))
  (testing "status 50 when sub statuses contain Aborted"
    (is (= 50 (sort-order-sub-status {:Aborted 1 :Succeeded 2}))))
  (testing "status 60 when sub statuses are all Success"
    (is (= 60 (sort-order-sub-status {:Succeeded 4}))))
  (testing "status 70 when sub status unknown"
    (is (= 70 (sort-order-sub-status {:blah 1})))))

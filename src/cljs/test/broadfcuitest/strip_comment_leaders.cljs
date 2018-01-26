(ns broadfcuitest.strip-comment-leaders
  (:require
   [cljs.test :refer [deftest is testing]]
   [broadfcui.page.method-repo.create-method :refer [strip-comment-leaders]]
   [broadfcui.utils :as utils]
   ))


(deftest check-strip-comment-leaders
  (testing "uniform comments, trim resulting lines"
    (is (= ["A"
            "B"
            "C"]
           (strip-comment-leaders
            ["###  A"
             "### B"
             "###C"]))))
  (testing "extra hashes are preserved"
    (is (= ["# A"
            "B"
            "C"]
           (strip-comment-leaders
            ["##### A"
             "#### B"
             "#### C"]))))
  (testing "lines with too few hashes are untouched"
    (is (= ["A"
            "B"
            "C"
            "#D"
            "E"]
           (strip-comment-leaders
            ["## A"
             "## B"
             "## C"
             "#D"
             "##E"])))))

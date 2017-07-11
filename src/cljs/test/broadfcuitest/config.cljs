(ns broadfcuitest.config
  (:require
    [cljs.test :refer [deftest is testing]]
    [broadfcui.config :as config]
    ))

(deftest check-config
  (let [valid-config {"apiUrlRoot" "foo"
                      "googleClientId" "foo"
                      "tcgaNamespace" "foo"}]
    (testing "empty config"
      (is (= [false '("missing required key apiUrlRoot"
                      "missing required key googleClientId"
                      "missing required key tcgaNamespace")]
             (config/check-config {}))))
    (testing "valid config"
      (is (= [true '()] (config/check-config valid-config))))
    (testing "invalid type in required key"
      (is (= [false '("value for apiUrlRoot must be a non-empty string")]
             (config/check-config (assoc valid-config "apiUrlRoot" 17)))))
    (testing "invalid type in optional key"
      (is (= [false '("value for isDebug must be a boolean")]
             (config/check-config (assoc valid-config "isDebug" "true")))))
    (testing "unexpected key"
      (is (= [false '("unexpected key foo")]
             (config/check-config (assoc valid-config "foo" true)))))))

;(cljs.test/run-tests)

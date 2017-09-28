(ns broadfcuitest.components.buttons
  (:require
   [cljs.test :refer [deftest is testing]]
   [broadfcui.components.buttons :as buttons]
   ))


(deftest check-default-test-id
  (testing "basic one-word button"
    (is (= "foo-button" (buttons/make-default-test-id {:text "Foo"}))))
  (testing "button with spaces"
    (is (= "do-a-thing-button" (buttons/make-default-test-id {:text "Do A Thing"}))))
  (testing "strips punctuation"
    (is (= "launch-modal-button" (buttons/make-default-test-id {:text "Launch Modal..."}))))
  (testing "text plus icon"
    (is (= "create-new-thing-button" (buttons/make-default-test-id {:text "Create New Thing..." :icon :add-new}))))
  (testing "icon alone"
    (is (= "collapse-button" (buttons/make-default-test-id {:icon :collapse})))))

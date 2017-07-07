(ns broadfcuitest.style
  (:require
    [cljs.test :refer [deftest is testing]]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))

(deftest create-text-field
  (let [text-field-map {:placeholder "test" :disabled false :value nil}
        text-field (style/create-text-field text-field-map)
        attribute-map (second text-field)
        {:keys [type style placeholder disabled value]} attribute-map]
    (testing "for a rendered text field, merged type exists"
      (is (not (nil? type))))
    (testing "for a rendered text field, merged style exists"
      (is (not (nil? style))))
    (testing "for a rendered text field, actual text value exists"
      (is (not (nil? placeholder))))
    (testing "for a rendered text field, boolean false value exists"
      (is (not (nil? disabled)))
      (not disabled))
    (testing "for a rendered text field, nil :value is replaced with empty string"
      (is (= value "")))))

(deftest create-search-field
  (let [text-field-map {:placeholder "test" :disabled false :value nil}
        text-field (style/create-search-field text-field-map)
        attribute-map (second text-field)
        {:keys [type style placeholder disabled value]} attribute-map]
    (testing "for a rendered search field, merged type exists"
      (is (not (nil? type))))
    (testing "for a rendered search field, merged style exists"
      (is (not (nil? style))))
    (testing "for a rendered search field, actual text value exists"
      (is (not (nil? placeholder))))
    (testing "for a rendered search field, boolean false value exists"
      (is (not (nil? disabled)))
      (not disabled))
    (testing "for a rendered text field, nil :value is replaced with empty string"
      (is (= value "")))))

;(cljs.test/run-tests)

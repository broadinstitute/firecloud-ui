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

(deftest transform-style-map-to-css
  (testing "a map with 1 style is correctly converted to css"
    (is (= "background:black;"
           (style/transform-style-map-to-css {:background "black"}))))
  (testing "a map with multiple styles is correctly converted to css"
    (is (= "background:black;color:white;width:1rem;"
           (style/transform-style-map-to-css {:background "black" :color "white" :width "1rem"}))))
  (testing "a number value has px appended to it, a non-number does not"
    (is (= "background:black;height:20px;width:1rem;"
           (style/transform-style-map-to-css {:background "black" :height 20 :width "1rem"}))))
  (testing "!important is appended when appropriate"
    (is (= "background:black !important;height:20px !important;width:1rem !important;"
           (style/transform-style-map-to-css {:background "black" :height 20 :width "1rem"} true)))))

;(cljs.test/run-tests)

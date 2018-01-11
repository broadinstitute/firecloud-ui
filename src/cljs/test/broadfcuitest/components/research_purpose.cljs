; import ReactTestUtils from 'react-dom/test-utils';
; dmohs.react

(ns broadfcuitest.components.research-purpose
  (:require
   [cljs.test :refer-macros [deftest testing is are use-fixtures]]
   [cljs-react-test.simulate :as tu]
   [cljs-react-test.utils :as sim]
   [dmohs.react :as react]
   [cljs.test :refer [deftest is testing]]
   [broadfcui.page.library.research-purpose :refer [ResearchPurposeSection]]
   [broadfcui.utils :as utils]
   ))

(def ^:dynamic c)

;(use-fixtures :each (fn [test-fn]
;                      (binding [c (tu/new-container!)]
;                        (test-fn)
;                        (tu/unmount! c))))

;(deftest show-modal
;  (testing "can we reference the component at all"
;    (is (some? [ResearchPurposeSection {:research-purpose-values nil :on-search nil}])))) ; this is just a vector literal, not a react comp...

(deftest a-react-test
  (let [app-state (atom {})
        ;_ (react test-component app-state {:target c})
        ;component (react/render (react/create-element ResearchPurposeSection {:research-purpose-values nil :on-search nil}) (utils/get-app-root-element))
        ;component (react/render (react/create-element ResearchPurposeSection {:research-purpose-values nil :on-search nil}) (utils/get-app-root-element))
        ;component (react/create-element ResearchPurposeSection)
        ]
    (testing "does something happen?"
      (utils/cljslog "something did happen")
     ;(cljs-react-test.simulate.click component nil))
    )))
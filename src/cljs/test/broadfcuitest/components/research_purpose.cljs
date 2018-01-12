(ns broadfcuitest.components.research-purpose
  (:require
   [dmohs.react :as react]
   [cljs.test :refer [deftest is testing]]
   [broadfcui.page.library.research-purpose :refer [ResearchPurposeSection]]
   [broadfcui.page.library.library-page :as library]
   [broadfcui.utils :as utils]
   ))

(deftest translate-research-purpose
  (testing "empty RP"
    (is (= (library/translate-research-purpose {:ds {}}) {"NMDS" false, "NCTRL" false, "NAGR" false, "POA" false, "NCU" false, "DS" []})))
  (testing "complicated RP"
    (is (= (library/translate-research-purpose {:poa true,
                                                :ds
                                                {"http://purl.obolibrary.org/obo/DOID_0050433"
                                                 "fatal familial insomnia",
                                                 "http://purl.obolibrary.org/obo/DOID_4325"
                                                 "Ebola hemorrhagic fever"},
                                                :control true}) {"NMDS" false,
                                                                 "NCTRL" true,
                                                                 "NAGR" false,
                                                                 "POA" true,
                                                                 "NCU" false,
                                                                 "DS"
                                                                 ["http://purl.obolibrary.org/obo/DOID_0050433"
                                                                  "http://purl.obolibrary.org/obo/DOID_4325"]}))))

;; Can't interact with the component because I haven't figured out how to import that part of React
(deftest a-react-test
  (let [component
        (react/render (react/create-element ResearchPurposeSection
                                            {:research-purpose-values nil
                                             :on-search (fn [options] (is (= {:ds {}} options)))}) (utils/get-app-root-element))]
    (testing "empty RP component test"
      (component :-search))))
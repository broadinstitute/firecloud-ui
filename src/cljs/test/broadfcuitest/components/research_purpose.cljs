(ns broadfcuitest.components.research-purpose
  (:require
   [cljs.test :refer [deftest is testing]]
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

(ns org.broadinstitute.firecloud-ui.common.icons
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]))

(def ^:private icon-keys {:activity ""
                          :angle-left ""
                          :angle-right ""
                          :document ""
                          :gear ""
                          :information ""
                          :locked ""
                          :pencil ""
                          :plus ""
                          :remove ""
                          :search ""
                          :share ""
                          :status-done ""
                          :status-warning ""
                          :status-warning-triangle ""
                          :trash-can ""
                          :view-mode-list ""
                          :view-mode-tiles ""
                          :x ""})

(defn font-icon [props key]
  [:span (utils/deep-merge {:style {:fontFamily "fontIcons"}} props) (key icon-keys)])

(defn icon-text [key] (key icon-keys))

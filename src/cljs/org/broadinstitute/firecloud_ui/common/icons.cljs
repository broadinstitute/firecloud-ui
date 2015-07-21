(ns org.broadinstitute.firecloud-ui.common.icons
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]))

(def ^:private icon-keys {:activity ""
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

(defn font-icon [style key]
  [:span (utils/deep-merge {:style {:fontFamily "fontIcons"}} style) (key icon-keys)])

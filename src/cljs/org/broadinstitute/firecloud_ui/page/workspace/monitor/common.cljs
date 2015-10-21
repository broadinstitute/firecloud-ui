(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.common
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    ))


(defn- all-success? [submission]
  (and (every? #(= "Succeeded" (% "status")) (submission "workflows"))
    (zero? (count (submission "notstarted")))))


(defn render-date [date]
  (let [m (js/moment date)]
    (str (.format m "L [at] LTS") " (" (.fromNow m) ")")))


(defn icon-for-wf-status [status]
  (cond (= "Succeeded" status)
        (icons/font-icon {:style {:color (:success-green style/colors)
                                  :fontSize 12 :marginRight 4}}
                         :status-done)
        (or (= "Running" status) (= "Submitted" status))
        [:span {:style {:backgroundColor (:running-blue style/colors) :position "relative"
                        :width 16 :height 16 :display "inline-block" :borderRadius 3
                        :verticalAlign "middle" :marginTop -4 :marginRight 4}}
         (style/center {} [comps/RunningIcon {:size 12}])]
        :else
        [:span {:style {:backgroundColor (:exception-red style/colors) :position "relative"
                        :width 16 :height 16 :display "inline-block" :borderRadius 3
                        :verticalAlign "middle" :marginTop -4 :marginRight 4}}
         (style/center {} [comps/ExceptionIcon {:size 12}])]))

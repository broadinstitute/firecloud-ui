(ns broadfcui.page.style-guide
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(defn- create-nav-link [label]
  (style/create-link {:text label
                      :href (str "#" (string/lower-case label))}))

(defn- style-nav [body-id]
  [Sticky
   {:outer-style {:float "right" :width 200}
    :anchor body-id
    :inner-style {:padding "0.5rem" :border style/standard-line :width 200
                  :maxHeight "calc(100vh - 3rem)" :overflow "auto"}
    :contents [:ul {:className "vertical menu" :data-magellan ""}
               [:li {} [:span {} "Overview"]
                [:ul {:className "nested vertical menu"}
                 [:li {} (create-nav-link "Summary")]
                 [:li {} (create-nav-link "Hierarchy")]]]
               [:li {} [:span {} "Conventions"]
                [:ul {:className "nested vertical menu"}
                 [:li {} (create-nav-link "Units")]
                 [:li {} (create-nav-link "Links")]
                 [:li {} (create-nav-link "Switches")]
                 [:li {} (create-nav-link "Buttons")]]]
               [:li {} [:span {} "Styles"]
                [:ul {:className "nested vertical menu"}
                 [:li {} (create-nav-link "Colors")]
                 [:li {} (create-nav-link "Icons")]]]
               [:li {} [:span {} "Components"]
                [:ul {:className "nested vertical menu"}
                 [:li {} (create-nav-link "Modals")]
                 [:li {} (create-nav-link "Infoboxes")]
                 [:li {} (create-nav-link "Tooltips")]]]]}])

(def ^:private section-break
  [:hr {:style {:border style/standard-line}}])

(defn- create-sub-head [label]
  [:div {:id (string/lower-case label) :data-magellan-target (string/lower-case label)
         :style {:fontSize "1.2rem" :fontWeight 500 :paddingTop "0.5rem"}}
   label])

(defn- create-code-block [text]
  [:div {:style {:maxWidth "80%" :paddingBottom "0.25rem"}}
   [CodeMirror {:mode "clojure" :text text :line-numbers? false}]])

(def ^:private overview
  [:section {}
   [:h2 {:style {:marginBottom "0.5rem"}} "Overview"]
   (create-sub-head "Summary")
   [:p {} "FireCloud's font is "
    [:a {:href "https://fonts.google.com/specimen/Roboto" :target "_blank"} "Roboto" icons/external-link-icon]
    ", and icons come from "
    [:a {:href "http://fontawesome.io/icons/" :target "_blank"} "Font Awesome" icons/external-link-icon]
    ". We use some widgets from "
    [:a {:href "http://foundation.zurb.com/sites/docs/" :target "_blank"} "Foundation" icons/external-link-icon] "."]
   [:p {} "When you're working on any part of FireCloud, remember
    that its purpose is to put the user in touch with their data. That is, FireCloud should never come
    between its users and what they came to do. It may seem obvious, but it's important to keep in mind."]

   (create-sub-head "Hierarchy")
   [:p {} "FireCloud's navigational and conceptual hierarchy breaks down like this: at the top
   (see the main nav) are the three primary sections of the site, " [:strong {} "Workspaces"] ", "
    [:strong {} "Data Library"] ", and the " [:strong {} "Method Repository"]
    ". There are also the management pages in the user menu, but those are secondary."]
   [:p {} "Each of the primary sections leads to a table where the user can select an entity
   to work with, and each of those (workspaces, methods, configs) then has its own controls and
   sub-sections. So: these top level entities are inviolable. Whenever they're referenced, remember
   to treat them as the independant entities that they are."]])

(def ^:private conventions
  [:section {}
   [:h2 {:style {:marginBottom "0.5rem"}} "Conventions"]
   (create-sub-head "Units")
   [:p {} "We prefer " (style/create-code-sample "rem") " over " (style/create-code-sample "em") ", "
    (style/create-code-sample "ex") ", " (style/create-code-sample "px") ", etc. for size values, since these
    are always the same size wherever they are used. If you're unfamiliar with these units, find out more "
    [:a {:href "https://developer.mozilla.org/en-US/docs/Web/CSS/length" :target "_blank"}
     "at the MDN" icons/external-link-icon] "."]

   (create-sub-head "Links")
   [:p {} "Internal links are created using " (style/create-code-sample "style/create-link") ", and "
    (style/create-link {:text "look like this"}) "."]
   (create-code-block "(style/create-link {:text \"link text\" :onClick #(...)})")
   [:p {} "Links that go to an external location should be created as regular "
    (style/create-code-sample "[:a]")
    "'s, and followed by an "
    (style/create-code-sample "icons/external-link-icon") ", so that they "
    [:a {:href "javascript:;"} "look like this" icons/external-link-icon] "."]
   (create-code-block "[:a {:href \"url\" :target \"_blank\"} \"link text\" icons/external-link-icon]")

   (create-sub-head "Switches")
   [:p {} "As an alternative to checkboxes, we have switches."]
   [:div {:style {:marginBottom "0.5rem"}}
    (common/render-foundation-switch {:on-change identity})]
   (create-code-block "(common/render-foundation-switch {:on-change #(...)})")
   [:p {} "Under the hood, these are just checkboxes, but they should be used for forms that don't
   have a submit button. See the example of workspace notification control: toggling the switch
   saves the new value."]

   (create-sub-head "Buttons")
   [:p {} "To create a button, use " (style/create-code-sample "comps/Button") "."]
   [:p {} "When the button is for manipulating an entity (workspace, method, config), follow these
   conventions when its action is not available:"]
   [:ol {}
    [:li {} "If the button's action isn't currently possible, but the user can do something to
     change this, disable it." [:br] "For example, a workspace is locked, so it cannot be deleted."]
    [:li {:style {:paddingTop "0.5rem"}} "If there's nothing the user can do about it (e.g. they
     must be granted a new permission), hide the button instead." [:br] "For example, a user is a
     reader on a workspace, so the delete workspace button is hidden."]]
   [:p {} "When the button is for a non-entity action, such as creating a new workspace, always make
   it visible."]])

(defn- render-color-swatch [color]
  [:div {:style {:padding "1rem" :backgroundColor (style/colors color) :width "10%"
                 :textAlign "center"}}
   [:span {:style {:backgroundColor "rgba(255, 255, 255, 0.8)"
                   :padding "0.2rem" :borderRadius "0.2rem"}}
    (name color)]])

(defn- render-icon-sample [icon]
  [:div {:style {:padding "0.5rem 1rem" :border style/standard-line :width 175}}
   (icons/icon {:style {:marginRight "0.5rem"} :className "fa-fw"} icon) (name icon)])

(def ^:private styles
  [:section {}
   [:h2 {:style {:marginBottom "0.5rem"}} "Styles"]
   (create-sub-head "Colors")
   [:p {} "Firecloud defines the following colors in " (style/create-code-sample "style/colors") ":"]
   [:div {:style {:display "flex" :flexWrap "wrap"}}
    (map render-color-swatch (sort (keys style/colors)))]
   [:p {} "Pay attention to the names of the colors, and you'll be fine. Reference them like this:"]
   (create-code-block "(:color-name style/colors)")
   [:p {} "Often, " (style/create-code-sample "line-default") " isn't used directly. It's common to just use "
    (style/create-code-sample "style/standard-line") " instead."]

   (create-sub-head "Icons")
   [:p {} "Firecloud defines the following icons in " (style/create-code-sample "icons/icon-keys") ":"]
   [:div {:style {:display "flex" :flexWrap "wrap"}}
    (map render-icon-sample (sort (keys icons/icon-keys)))]
   [:p {} "Use them like this:"]
   (create-code-block "(icons/icon {} :icon-name)")])

(def ^:private components
  [:section {}
   [:h2 {:style {:marginBottom "0.5rem"}} "Components"]
   (create-sub-head "Modals")
   [:p {} "We have a lot of options for creating modals. On a fundamental level, you " [:em {} "could"]
    " just use " (style/create-code-sample "modal/push-modal") ", but don't do that. You'd have to define the
    modal from scratch, which would be awful. Instead, use " (style/create-code-sample "comps/push-message")
    " and its ilk, which includes methods for quickly creating confirmation modals, alerts, etc."]
   [:p {} " Any button that spawns a modal should have an ellipsis on the end of its label, "
    [:em {} "unless"] " that modal is just an \"are you sure?\" confirmation."]
   [:p {} "Modal titles, the labels of buttons that open them, and
   their confirm buttons should share terminology as much as possible, specifically they should use
   a consistent verb. For example, a button marked \"Delete\" could open a modal titled
   \"Delete Method\" with a confirmation button labeled \"Delete\". Consistency is key."]

   (create-sub-head "Infoboxes")
   [:p {} "Observe, the ever-useful infobox:"
    (common/render-info-box {:text "Can we be friends?"})]
   (create-code-block "(common/render-info-box {:text \"Infobox text or element here.\"]})")
   [:p {} "An infobox is an instance of a " (style/create-code-sample "common/FoundationDropdown") ", but it should
   only be used when there's a contextual explanation to be displayed. The most interactivity inside
   of it should be an external link."]

   (create-sub-head "Tooltips")
   [:p {} "There are basically two types of tooltips in FireCloud. In the data tables, every cell
   has a title attribute, so hovering over them spawns a standard browser tooltip. The purpose of
   these is to show the full text contents of a cell, regardless of its width."]
   [:p {} "We also have Foundation's tooltips, "
    [common/FoundationTooltip {:text "which look like this." :tooltip "Ooooh, aaaah."}]]
   (create-code-block "[common/FoundationTooltip {:text \"Text or element with tooltip\" :tooltip \"Tooltip contents\"}]")
   [:p {} "These are used for description or explanation. Generally, it's something the user could
   figure out either from context or from clicking, but we want to be explicit."]])

(react/defc- Page
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :body-id (gensym "style")))
   :render
   (fn [{:keys [locals]}]
     (let [{:keys [body-id]} @locals]
       [:div {:style {:padding "1rem"}}
        [:style {} ; Yeah, yeah, they're overrides, so sue me.
         ".menu .active {background-color: #457fd2; color: white !important;}
         .menu {padding: 0;}
         .menu > li > span {display: block; padding: 0.7rem 1rem; line-height: 1;}
         p {margin: 0.5rem 0;}
         .CodeMirror {height: auto; font-family: Menlo, monospace; font-size: 12px;}"]
        [:h1 {} "Style Guide"]
        section-break
        (style-nav body-id)
        [:div {:id body-id :style {:marginRight 225 :lineHeight "1.4rem"}}
         overview
         section-break
         conventions
         section-break
         styles
         section-break
         components]]))})

(defn add-nav-paths []
  (nav/defpath
   :styles
   {:component Page
    :regex #"styles\S*"
    :make-props (fn [] {})
    :make-path (fn [] "styles")}))

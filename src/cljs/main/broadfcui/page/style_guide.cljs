(ns broadfcui.page.style-guide
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    [broadfcui.nav :as nav]
    [broadfcui.utils :as u]
    [clojure.string :as string]))

(defn- nav-link [label]
  (style/create-link {:text label
                      :href (str "#" (string/lower-case label))}))

(def ^:private style-nav
  [:div {:data-sticky-container "" :style {:float "right" :width 200}}
   [:div {:data-sticky "" :className "sticky" :data-anchor "guide"
          :style {:padding "0.5rem" :border style/standard-line :width 200}}
    [:ul {:className "vertical menu" :data-magellan ""}
     [:li {} [:span {} "Overview"]
      [:ul {:className "nested vertical menu"}
       [:li {} (nav-link "Hierarchy")]]]
     [:li {} [:span {} "Conventions"]
      [:ul {:className "nested vertical menu"}
       [:li {} (nav-link "Links")]]]
     [:li {} [:span {} "Styles"]
      [:ul {:className "nested vertical menu"}
       [:li {} (nav-link "Colors")]]]
     [:li {} [:span {} "Components"]
      [:ul {:className "nested vertical menu"}
       [:li {} (nav-link "Modals")]]]]]])

(def ^:private section-break
  [:hr {:style {:border style/standard-line}}])

(defn- sub-head [label]
  [:div {:id (string/lower-case label) :data-magellan-target (string/lower-case label)
         :style {:fontSize "1.2rem" :fontWeight 500}}
   label])

(defn- code-sample [text]
  [:code {:style {:backgroundColor (:background-dark style/colors) :color "white" :fontWeight "bold"
                  :padding "0.2rem" :borderRadius "0.2rem" :margin "0 0.1rem"}}
   text])

(def ^:private overview
  [:section {:id "overview" :data-magellan-target "overview"}
   [:h2 {} "Overview"]
   [:p {} "FireCloud's font is Roboto, and icons come from FontAwesome."]
   [:p {:style {:paddingBottom "0.5rem"}} "When you're working on any part of FireCloud, remember
    that its purpose is to put the user in touch with their data. That is, FireCloud should come
    between its users and what they came to do. It may seem obvious, but it's important to keep in mind."]

   (sub-head "Hierarchy")
   [:p {} "FireCloud's navigational and conceptual hierarchy breaks down like this: at the top
   (see the main nav) are the three primary sections of the site. There are also the management
   pages in the user menu, but those are secondary."]
   [:p {} "Each of the primary sections leads to a table where the user can select an entity
   to work with, and each of those (workspaces, methods, configs) then has its own controls and
   sub-sections. So: these top level entities are inviolable. Whenever they're referenced, remember
   to treat them as the independant entities that they are."]])

(def ^:private conventions
  [:section {:id "conventions" :data-magellan-target "conventions"}
   [:h2 {} "Conventions"]
   [:div {} (sub-head "Links")
    [:p {} "Internal links are created using " (code-sample "style/create-link") ", and "
     (style/create-link {:text "look like this"}) "."]
    [:p {} "Links that go to an external location should be created as regular "
     (code-sample "[:a]")
     "'s, and followed by an "
     (code-sample "icons/external-link-icon") ", so that they "
     [:a {:href "javascript:;"} "look like this" icons/external-link-icon] "."]]])

(defn- color-swatch [color]
  [:div {:style {:padding "1rem" :backgroundColor (style/colors color) :width "10%"
                 :textAlign "center"}}
   [:span {:style {:backgroundColor "rgba(255, 255, 255, 0.8)"
                   :padding "0.2rem" :borderRadius "0.2rem"}}
    (name color)]])

(def ^:private styles
  [:section {:id "styles" :data-magellan-target "styles"}
   [:h2 {} "Styles"]
   (sub-head "Colors")
   [:p {} "Firecloud defines the following colors in " (code-sample "style/colors") ":"]
   [:div {:style {:display "flex" :flexWrap "wrap"}}
    (map #(color-swatch %) (sort (keys style/colors)))]
   [:p {} "Pay attention to the names of the colors, and you'll be fine."]
   ])

(def ^:private components
  [:section {:id "components" :data-magellan-target "components"}
   [:h2 {} "Components"]
   (sub-head "Modals")
   [:p {} "We have a lot of options for creating modals. On a fundamental level, you " [:em {} "could"]
    " just use " (code-sample "modal/push-modal") ", but don't do that, you'd have to define the
    modal from scratch, it would be awful. Instead, use " (code-sample "comps/push-message") " and
    its ilk, which includes methods for quickly creating confirmation modals, alerts, etc."]
   [:p {} " Any button that spawns a modal should have an ellipsis on the end of its label, "
    [:em {} "unless"] " that modal is just an \"are you sure?\" confirmation."]
   [:p {} "Modal titles, the labels of buttons that open them, and their confirm buttons should share
   terminology as much as possible, specifically they should use a consistent verb. For example, a
   button marked \"Delete\" could open a modal titled \"Delete Method\" "]])

(react/defc Page
  {:render
   (fn [_]
     [:div {:style {:padding "1rem"}}
      [:style {}                                            ; Yeah, yeah, they're overrides, so sue me.
       ".menu .active {background-color: #457fd2; color: white !important;}
       .menu {padding: 0;}
       .menu > li > span {display: block; padding: 0.7rem 1rem; line-height: 1;}
       p {margin: 0.5rem 0;}"]
      [:h1 {} "Style Guide"]
      [:p {} "What components should I be using? When do I leave a link underlined? The answers are all here."]
      section-break
      style-nav
      [:div {:id "guide" :style {:marginRight 225 :lineHeight "1.4rem"}}
       overview
       section-break
       conventions
       section-break
       styles
       section-break
       components]])
   :component-did-mount
   (fn [{:keys [this]}]
     (.foundation (js/$ (react/find-dom-node this)))
     (js* "$(window).trigger('load');"))})

(defn add-nav-paths []
  (nav/defpath
    :styles
    {:component Page
     :regex #"styles\S*"
     :make-props (fn [] {})
     :make-path (fn [] "styles")}))

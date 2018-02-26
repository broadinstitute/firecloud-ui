(ns broadfcui.utils.react
  (:require
   [broadfcui.utils :as utils]
   ))


(defn log-methods [prefix defined-methods]
  (utils/map-kv (fn [method-name method]
                  [method-name
                   (fn [& args]
                     (utils/log (str prefix " - " (name method-name)))
                     (apply method args))])
                defined-methods))


(defn with-window-listeners [listeners-map defined-methods]
  (let [did-mount
        (fn [{:keys [locals] :as data}]
          (doseq [[event function] listeners-map]
            (let [func (partial function data)]
              (swap! locals assoc (str "WINDOWLISTENER " event) func)
              (.addEventListener js/window event func)))
          (when-let [defined-did-mount (:component-did-mount defined-methods)]
            (defined-did-mount data)))
        will-unmount
        (fn [{:keys [locals] :as data}]
          (doseq [[event _] listeners-map]
            (.removeEventListener js/window event (@locals (str "WINDOWLISTENER " event))))
          (when-let [defined-will-unmount (:component-will-unmount defined-methods)]
            (defined-will-unmount data)))]
    (assoc defined-methods
      :component-did-mount did-mount
      :component-will-unmount will-unmount)))


(defn track-initial-render [defined-methods]
  (let [will-mount
        (fn [{:keys [locals] :as data}]
          (swap! locals assoc :initial-render? true)
          (when-let [defined-will-mount (:component-will-mount defined-methods)]
            (defined-will-mount data)))
        did-mount
        (fn [{:keys [locals] :as data}]
          (swap! locals dissoc :initial-render?)
          (when-let [defined-did-mount (:component-did-mount defined-methods)]
            (defined-did-mount data)))]
    (assoc defined-methods
      :component-will-mount will-mount
      :component-did-mount did-mount)))

(ns attn.app
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [goog.dom :as dom]
            [goog.Uri :as uri]))

(defn trace [x]
  (js/console.log (pr-str x))
  x)

(rf/register-handler
 :startup
 rf/debug
 (fn [db [_ v]]
   (merge db v)))

(rf/register-sub
 :access-token
 (fn [db [k]]
   (reaction (get @db k))))

(defn app []
  (let [acc-tkn (rf/subscribe [:access-token])]
    (if @acc-tkn
      [:div "Check out your " [:a {:href (str "/feeds/" @acc-tkn ".json")} "feed"] "."]
      [:div [:a.btn.bg-green.rounded {:href "/auth"} "sign in"]])))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (.get qd "access-token")}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

(ns attn.app
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [goog.dom :as dom]
            [goog.Uri :as uri]
            [goog.net.XhrIo :as xhr]
            [cljs.reader :as reader]))

(defn trace [x]
  (js/console.log (pr-str x))
  x)

(defn edn-xhr
  "Make a request to uri and call callback cb with response read as edn"
  [uri cb]
  (xhr/send
   uri
   (fn [e]
     (-> (.. e -target getResponseText)
         reader/read-string
         cb))))

(rf/register-handler
 :startup
 rf/debug
 (fn [db [_ v]]
   (when (:access-token v)
     (rf/dispatch [:get-tweets]))
   (merge db v)))

(rf/register-handler
 :tweets
 rf/debug
 (fn [db [_ tweets]]
   (trace (count tweets))
   (update db :tweets #(reduce conj % tweets))))

(rf/register-handler
 :get-tweets
 rf/debug
 (fn [db _]
   (edn-xhr (str "/feeds/" (:access-token db) ".edn")
            #(rf/dispatch [:tweets %]))
   db))

(rf/register-sub
 :access-token
 (fn [db [k]]
   (reaction (get @db k))))

(rf/register-sub
 :tweets
 (fn [db [k]]
   (reaction (get @db k))))

(defn app []
  (let [acc-tkn (rf/subscribe [:access-token])
        tweets (rf/subscribe [:tweets])]
    (if @acc-tkn
      [:div "Check out your "
       (when (seq @tweets)
         [:p "count " (str (:tweets @tweets))])

       [:a {:on-click #(rf/dispatch [:get-tweets])} "feed"] "."]
      [:div [:a.btn.bg-green.rounded {:href "/auth"} "sign in"]])))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (.get qd "access-token")
     :tweets       #{}}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

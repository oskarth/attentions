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
   (when (and (:access-token v) (not (seq (:tweets db))))
     (rf/dispatch [:get-tweets]))
   (merge v db)))

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
   (reaction (reverse (sort-by :id (get @db k))))))



(defn tweet [tweet]
  (let [rt-or-t  (or (:retweeted-status tweet) tweet)
        entities (:entities tweet)]
    ;; (trace tweet)
    [:div.flex.flex-center.p2
     [:div.mr2.p0
      [:img.rounded {:src (-> rt-or-t :user :profile-image-url)
                     :style {:width "48px" :height "48px"}}]]
     [:span (:text rt-or-t)]
     (when (:retweeted-status tweet)
       [:span.ml3.bold.gray "RT"])]))

(defn app []
  (let [acc-tkn (rf/subscribe [:access-token])
        tweets (rf/subscribe [:tweets])]
      [:div.container.mt4
       [:div#timeline.col-10.mx-auto
        [:h1 "Attentions"]
        (if @acc-tkn
          [:div
           [:p "Check out your " [:a {:on-click #(rf/dispatch [:get-tweets])} "feed"] "."]
           (for [t @tweets]
             ^{:key (:id t)}
             [tweet t])]
          [:div [:a.btn.bg-green.white.rounded {:href "/auth"} "Sign in with Twitter"]])]]))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (.get qd "access-token")
     :tweets       #{}}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

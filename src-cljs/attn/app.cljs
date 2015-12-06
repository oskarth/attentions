(ns attn.app
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [attn.localstorage :as ls]
            [goog.dom :as dom]
            [goog.Uri :as uri]
            [goog.net.XhrIo :as xhr]
            [cljs.reader :as reader]))

(enable-console-print!)

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
   (when (and (:access-token v)
              (not (seq (:tweets db))))
     (rf/dispatch [:get-tweets]))
   (when-let [at (:access-token v)]
     (ls/set! :access-token at))
   (merge v db)))

(rf/register-handler
 :tweets
 (fn [db [_ tweets]]
   (update db :tweets #(reduce conj % tweets))))

(rf/register-handler
 :get-tweets
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

(defn fortunate? [prob]
  (> (* prob 100) (rand-int 100)))

;; TODO: add relevance into mix
(defn select-tweets [tweets]
  (let [get-nick #(:screen-name (:user %))
        nicks-freq (reduce
                    #(assoc %1 (get-nick %2) (inc (%1 (get-nick %2) 0)))
                    {}
                    tweets)
        nicks-tweets (zipmap (map get-nick tweets) tweets)
        freq-prob (zipmap (keys nicks-freq)
                          (map #(/ 1 (second %)) nicks-freq))]
    (vals (filter #(fortunate? (get freq-prob (first %)))
                  nicks-tweets))))


(def entity-type-mapping
  {:urls ::url, :user-mentions ::mention,
   :hashtags ::hashtag, :symbols ::symbol
   :media ::media})

(defn get-entities [tweet]
  (->> (for [[t es] (:entities tweet)]
        (map #(assoc % :type (get entity-type-mapping t t)) es))
       (apply concat)
       (sort-by :indices)))

(defn separate-at-indices [string idcs]
  (let [indexed (map-indexed (fn [idx ch] [idx ch]) string)
        ibs     (map first idcs)
        in-idc? (fn [[ib ie] idx] (and (>= idx ib) (< idx ie)))
        within-idcs? (fn [[idx _]] (some #(in-idc? % idx) idcs))]
    (->> (remove
          (fn [xs] (every? within-idcs? xs))
          (partition-by within-idcs? indexed))
         (map #(apply str (map second %))))))

(defn fill [coll size]
  (concat coll (repeat (- size (count coll)) nil)))

(defn alternate
  "Like interleave but make sure colls are fully exhausted.
   If a coll is not long enough interleave with nils"
  [c1 c2]
  (let [cnt (max (count c1) (count c2))]
    (interleave (fill c1 cnt) (fill c2 cnt))))

(defn entity [ent]
  (let [t "https://twitter.com/"]
    (case (:type ent)
      ::url     [:a {:href (:url ent)}
                 (:display-url ent)]
      ::media   [:a {:href (:url ent)}
                 (:display-url ent)]
      ::mention [:a {:href (str t (:screen-name ent))}
                 (str "@" (:screen-name ent))]
      ::hashtag [:a {:href (str t "hashtag/" (:text ent))}
                 (str "#" (:text ent))]
      ::symbol  [:span "Uhm...?"]
      [:span (pr-str ent)])))

(defn tweet-text [tweet]
  (let [txt  (:text tweet)
        ents (get-entities tweet)
        idcs (map :indices ents)
        sepd (separate-at-indices txt idcs)]
    (into [:span.h5]
          (if (= 0 (ffirst idcs))
            (alternate (map entity ents) sepd)
            (alternate sepd (map entity ents))))))

(defn tweet [tweet]
  (let [rt-or-t  (or (:retweeted-status tweet) tweet)
        entities (:entities tweet)]
    ;; (trace tweet)
    [:div.flex.flex-center.p2
     [:div.mr2.p0
      [:img.rounded {:src (-> rt-or-t :user :profile-image-url)
                     :style {:width "48px" :height "48px"}}]]
     [:div.relative
      (when (:retweeted-status tweet)
        (trace tweet)
        [:span.h6.block.gray.absolute
         {:style {:top "-15px"}} "Retweeted by @"
         (-> tweet :user :screen-name)])
      [tweet-text rt-or-t]]]))

(defn app []
  (let [acc-tkn (rf/subscribe [:access-token])
        tweets (rf/subscribe [:tweets])]
      [:div.container.mt4
       [:div#timeline.col-8.mx-auto
        [:h1 "Attentions"]
        (if @acc-tkn
          [:div
           [:p "Check out your " [:a {:on-click #(rf/dispatch [:get-tweets])} "feed"] "."]
           (for [t (select-tweets @tweets)]
             ^{:key (:id t)}
             [tweet t])]
          [:div [:a.btn.bg-green.white.rounded {:href "/auth"} "Sign in with Twitter"]])]]))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (or (.get qd "access-token") (ls/get :access-token))
     :tweets       #{}}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

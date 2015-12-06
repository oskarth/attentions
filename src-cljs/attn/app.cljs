(ns attn.app
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [attn.localstorage :as ls]
            [goog.dom :as dom]
            [goog.string :as gstring]
            [goog.Uri :as uri]
            [goog.net.XhrIo :as xhr]
            [cljs.reader :as reader]))

(enable-console-print!)

(defn trace [x]
  (js/console.log (pr-str x))
  x)

(defn push-state!
  ([state title] (.pushState js/history state title))
  ([state title path] (.pushState js/history state title path)))

(defn edn-xhr
  "Make a request to uri and call callback cb with response read as edn"
  ([uri cb]
   (edn-xhr uri cb #(js/console.error "Xhr request failed" %)))
  ([uri cb fail-cb]
   (xhr/send
    uri
    (fn [e]
      (if (= 200 (.. e -target getStatus))
        (-> (.. e -target getResponseText)
            reader/read-string cb)
        (fail-cb (.-target e)))))))

(rf/register-handler
 :startup
 rf/debug
 (fn [db [_ v]]
   (let [tweets (merge (ls/get :tweets) (:tweets db))
         at     (or (:access-token v) (ls/get :access-token))]
     (when (and at (empty? tweets))
       (rf/dispatch [:get-tweets]))
     (when at (ls/set! :access-token at))
     (push-state! {} "Attentions" "/")
     (-> db
         (assoc :access-token at)
         (assoc :tweets tweets)))))

(rf/register-handler
 :de-authenticate
 rf/debug
 (fn [db [_ tweets]]
   (ls/dissoc! :access-token)
   (dissoc db :access-token)))

(rf/register-handler
 :tweets
 (fn [db [_ tweets]]
   (let [old (or (:tweets db) {})
         new (reduce #(assoc-in %1 [:tweets (:id %2)] %2) old tweets)]
     (println "arg type" (type tweets))
     (println "db type" (type old))
     (ls/set! :tweets (let [new-val (:tweets new)]
                        (println (count new-val) "items in localstorage")
                        (into {} (take 300 new-val))))
     (assoc db :tweets new))))

(rf/register-handler
 :favstats
 rf/debug
 (fn [db [_ stat-map]]
   (ls/update! :favstats merge stat-map)
   (update db :favstats merge stat-map)))

(rf/register-handler
 :get-tweets
 rf/debug
 (fn [db _]
   (edn-xhr (str "/feeds/" (:access-token db) ".edn")
            #(rf/dispatch [:tweets %])
            #(rf/dispatch [:de-authenticate]))
   db))

(rf/register-handler
 :get-favstats
 rf/debug
 (fn [db _]
   (edn-xhr (str "/favstats/" (:access-token db) ".edn")
            #(rf/dispatch [:favstats %]))
   db))

(rf/register-handler
 :toggle-hidden
 rf/debug
 (fn [db _]
   (update db :show-hidden? not)))

(rf/register-sub
 :show-hidden?
 (fn [db [k]]
   (reaction (get @db k))))

(rf/register-sub
 :access-token
 (fn [db [k]]
   (reaction (get @db k))))

(rf/register-sub
 :tweets
 (fn [db [k]]
   (reaction (:tweets @db))))

(rf/register-sub
 :tweets-enriched
 (fn [db [k]]
   (let [tweets   (rf/subscribe [:tweets])
         selected? (rf/subscribe [:selected-tweets])]
     (->> (vals @tweets)
          (mapv (fn [t] (assoc t ::selected (@selected? (:id t)))))
          (sort-by :id)
          reverse
          reaction))))
     ;; (reaction
     ;;  (reverse 
     ;;   (sort-by :id (-> (fn [t] (assoc t ::selected (@selected? (:id t))))
     ;;                    (mapv ))))))))

(rf/register-sub
 :favstats
 (fn [db [k]]
   (reaction (get @db k))))

(defn fortunate? [prob]
  (> (* prob 100) (rand-int 100)))

;; TODO: Factor out / remove the general factors.
(defn calculate-prob [freq fav [nfreq nfav]]
  (let [;;compression-factor (/ 200 100) ;; 2
        ;;default-prob (/ 1 compression-factor) ;; 0.5
        ;;new-nfav (+ nfav 200) ;; account for fav count skew below
        ;;fav-to-freq-factor (/ new-nfav nfreq) ;; 5x more favs than freq, i.e. discount that much

        adjusted-fav (if fav (inc fav) 1)
        fav-to-freq (/ adjusted-fav freq)
        relevance (/ fav-to-freq (+ 0.5 fav-to-freq))]
    (println "FREQ FAV REL" freq adjusted-fav relevance)
    relevance))

(defn select-tweets [tweets favstats]
  (let [get-nick #(:screen-name (:user %))
        nicks (map get-nick tweets)
        nicks-freq (reduce
                    #(assoc %1 (get-nick %2) (inc (%1 (get-nick %2) 0)))
                    {}
                    tweets)
        favs-present (filter #(get nicks-freq (first %)) favstats)
        nicks-favs (zipmap (map first favs-present) (map second favs-present))
        ;; XXX: Remove stats?
        stats [(reduce + (vals nicks-favs))
               (reduce + (vals nicks-freq))]
        probs (zipmap nicks (map #(calculate-prob (get nicks-freq %) (get nicks-favs %) stats) nicks))]
     (filter #(fortunate? (get probs (get-nick %))) tweets)))

(rf/register-sub
 :selected-tweets
 (fn [db [k]]
   (let [tweets   (rf/subscribe [:tweets])
         favstats (rf/subscribe [:favstats])]
     (reaction (set (map :id (select-tweets (vals @tweets) @favstats)))))))

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
            (alternate (map entity ents) (map gstring/unescapeEntities sepd))
            (alternate (map gstring/unescapeEntities sepd) (map entity ents))))))

(defn tweet [tweet]
  (let [rt-or-t  (or (:retweeted-status tweet) tweet)
        entities (:entities tweet)]
    ;; (trace tweet)
    [:div.flex.flex-center.p2
     [:div.mr2.p0
      [:img.rounded {:src (-> rt-or-t :user :profile-image-url)
                     :width "48px" :height "48px" :style {:max-width "none"}}]]
     [:div.relative
      (when (:retweeted-status tweet)
        [:span.h6.block.gray.absolute
         {:style {:top "-15px"}} "Retweeted by @"
         (-> tweet :user :screen-name)])
      [tweet-text rt-or-t]]]))

(defn heading []
  [:h1 "Attentions"
   [:span.h5.ml2.gray.regular "Made by "
    [:a {:href "https://twitter.com/oskarth"} "@oskarth"] " & "
    [:a {:href "https://twitter.com/martinklepsch"} "@martinklepsch"]]])

(defn app []
  (let [acc-tkn   (rf/subscribe [:access-token])
        enriched  (rf/subscribe [:tweets-enriched])
        show-hdn? (rf/subscribe [:show-hidden?])]
    [:div.container.mt4
     [:div#timeline.col-8.mx-auto
      [heading]
      (if @acc-tkn
        [:div
         [:p
          [:a.btn.border.rounded.mr2 {:on-click #(do (rf/dispatch [:get-tweets])
                                                     (rf/dispatch [:get-favstats]))}
           "Refresh feed"]
          [:a.btn.border.rounded {:on-click #(rf/dispatch [:toggle-hidden])}
           (if @show-hdn? "Hide stuff" "Show hidden")]]
         (doall
          (for [t @enriched]
            (if (or (::selected t) @show-hdn?)
              ^{:key (:id t)} [:div {:class (if (::selected t) "" "fade")} [tweet t]])))]
        [:div [:a.btn.bg-green.white.rounded {:href "/auth"} "Sign in with Twitter"]])]]))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (or (.get qd "access-token") (ls/get :access-token))}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

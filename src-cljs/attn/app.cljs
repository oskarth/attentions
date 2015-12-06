(ns attn.app
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as rf]
            [attn.localstorage :as ls]
            [goog.dom :as dom]
            [goog.string :as gstring]
            [goog.Uri :as uri]
            [goog.net.XhrIo :as xhr]
            [goog.i18n.DateTimeFormat.Format]
            [cljs.reader :as reader])
  (:import [goog.i18n DateTimeFormat]))

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
 (fn [db [_ tweets]]
   (ls/dissoc! :access-token)
   (dissoc db :access-token)))

(rf/register-handler
 :tweets
 rf/debug
 (fn [db [_ tweets]]
   (let [old (or (:tweets db) {})
         new (reduce #(assoc %1 (:id %2) %2) old tweets)]
     ;; (println "arg type" (type tweets))
     ;; (println "db type" (type old))
     (ls/set! :tweets (do
                        (println (count new) "items in localstorage")
                        (into {} (take 300 new))))
     (assoc db :tweets new))))

(rf/register-handler
 :favstats
 (fn [db [_ stat-map]]
   (ls/update! :favstats merge stat-map)
   (update db :favstats merge stat-map)))

(rf/register-handler
 :get-tweets
 (fn [db _]
   (edn-xhr (str "/feeds/" (:access-token db) ".edn")
            #(rf/dispatch [:tweets %])
            #(rf/dispatch [:de-authenticate]))
   db))

(rf/register-handler
 :get-favstats
 (fn [db _]
   (edn-xhr (str "/favstats/" (:access-token db) ".edn")
            #(rf/dispatch [:favstats %]))
   db))

(rf/register-handler
 :toggle-hidden
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
         rel-map  (rf/subscribe [:relevance-map])
         selected (rf/subscribe [:selected-tweets])]
     ;; (println "tweets" (count @tweets))
     ;; (println "selected" (count @selected))
     (->> (vals @tweets)
          (mapv (fn [t]
                  (-> t
                      (assoc ::selected (@selected (:id t)))
                      (assoc ::relevance-score (get @rel-map (:id t))))))
          (sort-by :id)
          reverse
          reaction))))

(rf/register-sub
 :favstats
 (fn [db [k]]
   (reaction (get @db k))))

(defn fortunate? [prob]
  (> (* prob 100) (rand-int 100)))

(defn coarse-prob [prob]
  (cond (< prob 0.35) 0.25
        (< prob 0.60)  0.5
        (< prob 0.85) 0.75
        :else         1))

(defn roughly-equal? [x y] (< (Math/abs (- x y)) 0.1))

(defn prob->relevance [prob]
  (cond (roughly-equal? prob 0.25) :ok
        (roughly-equal? prob 0.5)  :good
        (roughly-equal? prob 0.75) :great
        :else                      :amazing))

(defn relevance->prob [kw]
  (cond (= kw :ok)    0.25
        (= kw :good)  0.5
        (= kw :great) 0.75
        :else         1))

(defn calculate-prob [freq fav]
  (let [adjusted-fav (if fav (inc fav) 1)
        fav-to-freq (/ adjusted-fav freq)
        relevance (/ fav-to-freq (+ 0.5 fav-to-freq))]
    (coarse-prob relevance)))

(defn select-tweets [tweets favstats]
  (let [get-nick #(:screen-name (:user %))
        nicks (map get-nick tweets)
        id-tweets (zipmap (map :id tweets) tweets)
        nicks-freq (reduce
                    #(assoc %1 (get-nick %2) (inc (%1 (get-nick %2) 0)))
                    {}
                    tweets)
        favs-present (filter #(get nicks-freq (first %)) favstats)
        nicks-favs (zipmap (map first favs-present) (map second favs-present))
        probs (zipmap nicks (map #(calculate-prob (get nicks-freq %) (get nicks-favs %)) nicks))
        relevance-map (zipmap (map :id tweets)
                              (map #(prob->relevance (get probs (get-nick %))) tweets))]
    relevance-map))

(rf/register-sub
 :relevance-map
 (fn [db [k]]
   (let [tweets   (rf/subscribe [:tweets])
         favstats (rf/subscribe [:favstats])]
     (reaction (select-tweets (vals @tweets) @favstats)))))

(rf/register-sub
 :selected-tweets
 (fn [db [k]]
   (let [tweets   (rf/subscribe [:tweets])
         rel-map  (rf/subscribe [:relevance-map])]
     (reaction (set (map first (filter #(fortunate? (relevance->prob (val %))) @rel-map)))))))

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

(def date-fmt (DateTimeFormat. goog.i18n.DateTimeFormat.Format/SHORT_TIME))

(defn tweet [tweet]
  (let [rt-or-t  (or (:retweeted-status tweet) tweet)
        entities (:entities tweet)]
    [:div.flex.flex-center.p2
     [:div.mr2.p0
      [:img.rounded {:src (-> rt-or-t :user :profile-image-url)
                     :width "48px" :height "48px" :style {:max-width "none"}}]]
     [:div.relative
      (when (:retweeted-status tweet)
        [:span.h6.block.gray.absolute
         {:style {:top "-15px"}} "Retweeted by @"
         (-> tweet :user :screen-name)])
      [tweet-text rt-or-t]
      (let [date (js/Date. (js/Date.parse (:created-at rt-or-t)))]
        [:span.gray.h6.ml1 (.format date-fmt date)])
      (if-let [score (::relevance-score tweet)]
        [:span.gray.h6 " / Score: "
         [:span (name score)]])]]))

(defn heading []
  [:div
   [:h1 "Attentions"
   [:span.h5.ml2.gray.regular "Made by "
    [:a {:href "https://twitter.com/oskarth"} "@oskarth"] " & "
    [:a {:href "https://twitter.com/martinklepsch"} "@martinklepsch"]]]
   [:h3.h3 "Beat information overflow."]
   [:p {:style {:line-height 1.6}}
    "A modified Twitter timeline that filters out frequent posters and promotes people whose tweets you often like."]])

(defn app []
  (let [acc-tkn   (rf/subscribe [:access-token])
        enriched  (rf/subscribe [:tweets-enriched])
        show-hdn? (rf/subscribe [:show-hidden?])]
    ;; (println "enriched cnt" (count @enriched))
    ;; (println "enriched uniq ids cnt" (count (set (map :id @enriched))))
    ;; (println "enriched first" (first @enriched))
    ;; (doseq [t @enriched] (println (::selected t)))
    (fn []
      [:div.container.mt2.p2
       [:div#timeline.lg-col-8.sm-col-10.col-12.mx-auto
        [heading]
        (if @acc-tkn
          [:div
           [:p
            [:a.btn.border.rounded.mr2 {:on-click #(do (rf/dispatch [:get-tweets])
                                                       (rf/dispatch [:get-favstats]))}
             "Refresh feed"]
            [:a.btn.border.rounded {:on-click #(rf/dispatch [:toggle-hidden])}
             (if @show-hdn? "Hide stuff" "Show hidden")]
            [:a.btn.maroon.regular {:on-click #(rf/dispatch [:de-authenticate])} "Sign out"]]
           (doall
            (for [t @enriched]
              (if (or (::selected t) @show-hdn?)
                ^{:key (:id t)} [:div {:class (if (::selected t) "" "fade")} [tweet t]])))]
          [:div [:a.btn.bg-green.white.rounded {:href "/auth"} "Sign in with Twitter"]])]])))

(defn get-startup-data []
  (let [qd (.getQueryData (uri/parse js/location))]
    {:access-token (or (.get qd "access-token") (ls/get :access-token))}))

(defn init []
  (rf/dispatch-sync [:startup (get-startup-data)])
  (reagent/render-component [app] (dom/getElement "container")))

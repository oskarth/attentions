(ns attn.api
  (:require [clojure.java.io :as io]
            [ring.util.response :as r]
            [ring.middleware.params :as mp]
            [bidi.ring :as bring]
            [clj-http.client :as http]
            [oauth.client :as oauth]
            [clojure.data.json :as json]
            [camel-snake-kebab.core :as case]))

(def secrets (read-string (slurp (io/resource "secrets.edn"))))

(defn oauth-callback-uri []
  (if (System/getenv "ATTN_PROD")
    (do (println "using production callback uri")
        "http://attentions.oskarth.com/oauth_callback")
    (do (println "using development callback uri")
        "http://127.0.0.1:3000/oauth_callback")))

(def consumer
  (oauth/make-consumer (:consumer-key secrets)
                       (:consumer-secret secrets)
                       "https://api.twitter.com/oauth/request_token"
                       "https://api.twitter.com/oauth/access_token"
                       "https://api.twitter.com/oauth/authorize"
                       :hmac-sha1))

(defonce access-tokens
  ^{:doc "Map from access token to access token map.
Note that the key called oauth_token refers to the access token."}
  (atom {}))

(defonce req-tokens (atom {}))

(defn get-req-token
  "Get us a new token or return an existing one"
  ([]
   (println "Getting a fresh request-token")
   (let [tkn (oauth/request-token consumer (oauth-callback-uri))]
     (swap! req-tokens assoc (:oauth_token tkn) tkn)
     (println "Request token retrieved:\n" tkn)
     tkn))
  ([oauth-token]
   (get @req-tokens oauth-token)))

(defn sign-in-url []
  (oauth/user-approval-uri consumer (:oauth_token (get-req-token))))

(defn twitter-api-req [api-uri acc-token api-params]
  (let [params  (oauth/credentials consumer
                                   (:oauth_token acc-token)
                                   (:oauth_token_secret acc-token)
                                   :GET api-uri api-params)]
    (-> (http/get api-uri {:query-params (merge params api-params)})
        :body (json/read-str :key-fn case/->kebab-case-keyword))))

(defn get-tweets [access-token]
  (twitter-api-req "https://api.twitter.com/1.1/statuses/home_timeline.json"
                   access-token {:count 200}))

(defn get-favs [access-token]
  (twitter-api-req "https://api.twitter.com/1.1/favorites/list.json"
                   access-token {:count 200}))

(def app-routes
  ["/" {"" (fn [req] {:status 200
                      :body (slurp (io/resource "index.html"))
                      :headers {"Content-Type" "text/html"}})

        "index.html" (fn [req] {:status 200 :body "ex"})

        ["feeds/" :access-token ".edn"]
        (fn [{:keys [route-params] :as req}]
          (if-let [tkn (get @access-tokens (:access-token route-params))]
            (do (println tkn)
                {:status 200 :body (pr-str (get-tweets tkn))})
            {:status 401}))

        ["favstats/" :access-token ".edn"]
        (fn [{:keys [route-params] :as req}]
          (if-let [tkn (get @access-tokens (:access-token route-params))]
            (do (println tkn)
                {:status 200
                 :body (let [favs  (get-favs tkn)
                             nicks (map #(-> % :user :screen-name) favs)]
                         (pr-str (reduce #(update %1 %2 (fnil inc 0)) {} nicks)))})
            {:status 401}))

        ;; OAuth Flow
        "auth"
        (fn [req] (r/redirect (sign-in-url)))
        "oauth_callback"
        (fn [req]
          (let [verifier (get-in req [:params "oauth_verifier"])
                tkn      (get-in req [:params "oauth_token"])
                acc-map  (oauth/access-token consumer (get-req-token tkn) verifier)
                access-token (:oauth_token acc-map)]
            (swap! access-tokens assoc access-token acc-map)
            (r/redirect (str "/?access-token=" access-token))))
        [""] (bring/->Resources {:prefix "public/"})}])

(def handler
  (-> app-routes bring/make-handler mp/wrap-params))

;; Workaround for auth bug, eval locally
;; (oauth/request-token consumer "http://attentions.oskarth.com/oauth_callback")

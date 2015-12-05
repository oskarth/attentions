(ns attn.api
  (:require [clojure.java.io :as io]
            [ring.util.response :as r]
            [ring.middleware.params :as mp]
            [bidi.ring :as bring]
            [clj-http.client :as http]
            [oauth.client :as oauth]))

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

;; (defn new-request-token [consumer]
;;   (oauth/request-token consumer oauth_callback_uri))

;; we need the token and the token secret for /auth as well as for
;; the callback route because of that we need to save it somewhere

(defonce access-tokens
  "Map from access token to access token map. Note that the key called oauth_token refers to the access token."
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

(defn get-tweets [access-token]
  (let [api-uri "https://api.twitter.com/1.1/statuses/home_timeline.json"
        acc-tkn (get @access-tokens access-token)
        params  (oauth/credentials consumer
                                   (:oauth_token acc-tkn)
                                   (:oauth_token_secret acc-tkn)
                                   :GET api-uri)]
    (http/get api-uri {:query-params params})))

(def app-routes
  ["/" {"" (fn [req] {:status 200
                      :body (slurp (io/resource "index.html"))
                      :headers {"Content-Type" "text/html"}})

        "index.html" (fn [req] {:status 200 :body "ex"})

        ["feeds/" :access-token ".json"]
        (fn [req] {:status 200 :body (get-tweets (-> req :route-params :access-token))})

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
            (r/redirect (str "/?oauth-token=" access-token))))
        [""] (bring/->Resources {:prefix "public/"})}])

(def handler
  (-> app-routes bring/make-handler mp/wrap-params))

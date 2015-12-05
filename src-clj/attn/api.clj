(ns attn.api
  (:require [bidi.ring :as bring]
            [oauth.client :as oauth]))

(def secrets (read-string (slurp "resources/secrets.edn")))

(def oauth_callback_uri "http://attentions.oskarth.com/oauth_callback")

(defn new-consumer []
  (oauth/make-consumer (:consumer-key secrets)
                       (:consumer-secret secrets)
                       "https://api.twitter.com/oauth/request_token"
                       "https://api.twitter.com/oauth/access_token"
                       "https://api.twitter.com/oauth/authorize"
                       :hmac-sha1))

(defn new-request-token [consumer]
  (oauth/request-token consumer oauth_callback_uri))

(def handler
  (bring/make-handler
   ["/" {"index.html" (fn [req] {:status 200 :body "ex"})
         ["articles/" :id "/article.html"] (fn [req] {:status 200 :body "Article"})
         "oauth_callback" (fn [req] (clojure.pprint/pprint req) (:status 200 :body req))}]))

(do
  (println "So far all good")
  (def consumer (new-consumer))
  (println "Made a consumer")
  (def token (new-request-token consumer))
  (println "All done"))

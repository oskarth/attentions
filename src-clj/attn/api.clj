(ns attn.api
  (:require [clojure.java.io :as io]
            [ring.util.response :as r]
            [ring.middleware.params :as mp]
            [bidi.ring :as bring]
            [oauth.client :as oauth]))

(def secrets (read-string (slurp (io/resource "secrets.edn"))))

(defn oauth-callback-uri [dev?]
  (if dev?
    "http://127.0.0.1:3000/oauth_callback"
    "http://attentions.oskarth.com/oauth_callback"))

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

(def tokens (atom {}))

(defn get-token
  "Get us a new token or return an existing one"
  ([]
   (println "Getting a fresh request-token")
   (let [tkn (oauth/request-token consumer (oauth-callback-uri true))]
     (swap! tokens assoc (:oauth_token tkn) tkn)
     (println "Request token retrieved:\n" tkn)
     tkn))
  ([oauth-token]
   (get @tokens oauth-token)))

(defn sign-in-url []
  (println "Getting request token")
  (let [tkn (get-token)
        uri (oauth/user-approval-uri consumer (:oauth_token tkn))]
    (println "URI generated")
    uri))

(def app-routes
  ["/" {"" (fn [_] {:status 200 :body "Hello World!" :headers {"Content-Type" "text/plain"}})
        "index.html" (fn [req] {:status 200 :body "ex"})
        ;; ["articles/" :id "/article.html"] (fn [req] {:status 200 :body "Article"})
        ;; OAuth Flow
        "auth" (fn [req] (r/redirect (sign-in-url)))
        "oauth_callback" (fn [req]
                           (let [verifier (get-in req [:params "oauth_verifier"])
                                 tkn      (get-in req [:params "oauth_token"])
                                 access-token (oauth/access-token consumer (get-token tkn) verifier)]
                             {:status 200 :body req :headers {"Content-Type" "text/plain"}}))}])

(def handler
  (-> app-routes bring/make-handler mp/wrap-params))

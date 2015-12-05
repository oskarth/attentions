(ns attn.api
  (:require [bidi.ring :as bring]))

(def handler
  (bring/make-handler ["/" {"index.html" (fn [req] {:status 200 :body "ex"})
                            ["articles/" :id "/article.html"] (fn [req] {:status 200 :body "Article"})}]))

(ns attn.localstorage
  (:require [goog.storage.Storage :as Storage]
            [goog.storage.mechanism.HTML5LocalStorage :as html5localstore]
            [cljs.reader :as reader])
  (:refer-clojure :exclude [get]))

;; https://github.com/funcool/hodgepodge
(defn- storage []
  (let [mech (goog.storage.mechanism.HTML5LocalStorage.)
        store (goog.storage.Storage. mech)]
    store))

(defn get
  "Gets value from local storage."
  [key]
  (let [store (storage)]
    (some-> (.get store (str key))
            reader/read-string)))

;; set! is a special form directly embedded in the AST
;; and this can't be excluded like get 
(defn set!*
  "Stores key value in local storage."
  [key val]
  (let [store (storage)]
    (.set store (str key) (pr-str val))))
(def set! set!*)

(defn update!
  [k f & args]
  (let [current-val (get k)]
    (set!* k (apply f current-val args))))

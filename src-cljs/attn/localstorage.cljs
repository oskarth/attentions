(ns attn.localstorage
  (:require [goog.storage.Storage :as Storage]
            [goog.storage.mechanism.HTML5LocalStorage :as html5localstore]
            [cljs.reader :as reader])
  (:refer-clojure :exclude [get dissoc!]))

;; https://github.com/funcool/hodgepodge
(defn- storage []
  (let [mech (goog.storage.mechanism.HTML5LocalStorage.)
        store (goog.storage.Storage. mech)]
    store))

(defn get
  "Gets value from local storage."
  [key]
  (some-> (.get (storage) (str key))
          reader/read-string))

;; set! is a special form directly embedded in the AST
;; and this can't be excluded like get 
(defn set!*
  "Stores key value in local storage."
  [key val]
  (.set (storage) (str key) (pr-str val)))
(def set! set!*)

(defn update!
  [k f & args]
  (set!* k (apply f (get k) args)))

(defn dissoc!
  [k]
  (.remove (storage) (str k)))

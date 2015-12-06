(set-env!
 :source-paths    #{"sass" "src-cljs" "src-clj"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "1.7.170-3"   :scope "test"]
                 [adzerk/boot-cljs-repl     "0.2.0"      :scope "test"]
                 [adzerk/boot-reload        "0.4.2"      :scope "test"]
                 [pandeiro/boot-http        "0.7.1-SNAPSHOT" :scope "test"]
                 [deraen/boot-sass          "0.1.1"      :scope "test"]
                 ;; client
                 [org.clojure/clojurescript "1.7.170"]
                 [reagent "0.5.0"]
                 [re-frame "0.5.0"]
                 ;; server
                 [bidi "1.22.1"]
                 [camel-snake-kebab "0.3.2"]
                 [clj-http "2.0.0"]
                 [clj-oauth "1.5.3"]
                 [org.clojure/data.json "0.2.6"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]]
 '[deraen.boot-sass      :refer [sass]])

(deftask build []
  (comp (cljs)
     (sass)))

(deftask run
  [p prod bool "Run in production mode"]
  (comp (serve :httpkit true
               :reload  true
               :nrepl   {:port 3001}
               :handler 'attn.api/handler)
        (if prod identity (watch))
        ;; (if prod identity (repl :server true))
        ;; (if prod identity (cljs-repl))
        (if prod identity (reload))
        (build)
        (if prod (wait) identity)))

(deftask production []
  (task-options! cljs {:optimizations :advanced
                       :source-map true
                       :compiler-options {:pseudo-names true}}
                 sass {:output-style :compressed}
                 serve {:port 8080})
  identity)

(deftask development []
  (task-options! cljs   {:optimizations :none
                         :source-map true}
                 reload {:on-jsload 'attn.app/init
                         :asset-path "/public"}
                 sass   {:line-numbers true
                         :source-maps  true})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))

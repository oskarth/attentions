(set-env!
 :source-paths    #{"sass" "src-cljs" "src-clj"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "1.7.48-6"   :scope "test"]
                 [adzerk/boot-cljs-repl     "0.2.0"      :scope "test"]
                 [adzerk/boot-reload        "0.4.1"      :scope "test"]
                 [pandeiro/boot-http        "0.7.1-SNAPSHOT" :scope "test"]
                 [deraen/boot-sass          "0.1.1"      :scope "test"]
                 [org.clojure/clojurescript "1.7.122"]
                 [reagent "0.5.0"]
                 [bidi "1.21.1"]
                 [clj-oauth "1.5.3"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]]
 '[deraen.boot-sass      :refer [sass]]
 '[oauth.client          :as oauth])

(deftask build []
  (comp (speak)
     (cljs)
     #_(sass :output-dir "css")))

(deftask run
  [p prod bool "Run in production mode"]
  (comp (serve :httpkit true
            :handler 'attn.api/handler)
     (if prod identity (watch))
     (if prod identity (cljs-repl))
     (if prod identity (reload))
     (build)
     (if prod (wait) identity)))

(deftask production []
  (task-options! cljs {:optimizations :advanced}
                 ;;sass {:output-style :compressed}
                 serve {:port 8080})
  identity)

(deftask development []
  (task-options! cljs   {:optimizations :none
                         :source-map true}
                 reload {:on-jsload 'attn.app/init}
                 sass   {:line-numbers true
                         :source-maps  true})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
     (run)))

(defproject clout "1.1.0"
  :description "A HTTP route matching library"
  :url "http://github.com/weavejester/clout"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [buster-cljs "0.1.0-SNAPSHOT"]]
  :plugins [[lein-cljsbuild "0.2.9"]
            [lein-dalap "0.1.0-SNAPSHOT"]]
  :hooks [leiningen.dalap]
  :profiles
  {:dev {:dependencies [[org.clojure/tools.namespace "0.2.1"]
                        [ring-mock "0.1.1"]]}}

  :source-paths ["src/clj"]
  :test-paths ["test/clj"]

  :cljsbuild
  {:builds
   [{:id "dev"
     :source-path "src/cljs"
     :compiler
     {:optimizations :simple
      :pretty-print true
      :output-to "resources/js/clout_dev.js"}}
    ;;
    {:id "browser-test"
     :source-path "test/cljs"
     :compiler
     {:optimizations :simple
      :pretty-print true
      :externs ["externs/buster.js"]
      :libraries ["resources/js/clout_dev.js"]
      :output-to "resources/js/clout_browser_test.js"}}
    ;;
    {:id "node-test"
     :source-path "test/cljs"
     :compiler
     {:optimizations :simple
      :pretty-print true
      :target :node
      :externs ["externs/buster.js"]
      :libraries ["resources/js/clout_dev.js"]
      :output-to "resources/js/clout_node_test.js"}}]})

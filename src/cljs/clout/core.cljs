;; This file was generated with lein-dalap from
;;
;; src/clj/clout/core.clj @ Wed Dec 05 07:56:51 UTC 2012
;;
(ns clout.core "Library for parsing the Rails routes syntax." (:require [goog.string :as gstring] [clojure.string :as string] [goog.Uri :as uri]))
(do (defn re-matcher [re s] (let [re (js* "new RegExp(re.source, 'g')") match (.exec re s)] (js-obj "lookingAt" (fn [] (and match (= (.-index match) 0))) "matches" (fn [] (and match (= s (aget match 0)))) "start" (fn [] (and match (.-index match))) "end" (fn [] (if match (.-lastIndex re) (throw (js/Error. "invalid state")))) "groupCount" (fn [] (or (and match (.-length match)) 0)) "group" (fn [& [i]] (and match (aget match (or i 0))))))))
(def re-chars (set "\\.*+|?()[]{}$^"))
(defn- re-escape "Escape all special regex chars in a string." [s] (string/escape s (reduce (fn [m c] (assoc m c (str \\ c))) {} re-chars)))
(defn re-groups* "More consistant re-groups that always returns a vector of groups, even if\n  there is only one group." [matcher] (for [i (range (.groupCount matcher))] (do (.group matcher (int (inc i))))))
(defn path-decode "Decode a path segment in a URI. Defaults to using UTF-8 encoding." ([path] (path-decode path "UTF-8")) ([path encoding] (-> (string/replace path "+" (gstring/urlEncode "+")) (gstring/urlDecode))))
(defn- assoc-vec "Associate a key with a value. If the key already exists in the map, create a\n  vector of values." [m k v] (assoc m k (if-let [cur (m k)] (if (vector? cur) (conj cur v) [cur v]) v)))
(defn- assoc-keys-with-groups "Create a hash-map from a series of regex match groups and a collection of\n  keywords." [groups keys] (reduce (fn [m [k v]] (assoc-vec m k v)) {} (map vector keys groups)))
(defn- request-url "Return the complete URL for the request." [request] (str (name (:scheme request)) "://" (get-in request [:headers "host"]) (:uri request)))
(defn- path-info "Return the path info for the request." [request] (or (:path-info request) (:uri request)))
(defprotocol Route (route-matches [route request] "If the route matches the supplied request, the matched keywords are\n    returned as a map. Otherwise, nil is returned."))
(defrecord CompiledRoute [re keys absolute?] Route (route-matches [route request] (let [path-info (if absolute? (request-url request) (path-info request)) matcher (re-matcher re path-info)] (if (.matches matcher) (assoc-keys-with-groups (re-groups* matcher) keys)))))
(defn- lex-1 "Lex one symbol from a string, and return the symbol and trailing source." [src clauses] (some (fn [[re action]] (let [matcher (re-matcher re src)] (if (.lookingAt matcher) (do [(if (fn? action) (action matcher) action) (subs src (.end matcher))])))) (partition 2 clauses)))
(defn- lex "Lex a string into tokens by matching against regexs and evaluating\n   the matching associated function." [src & clauses] (loop [results [] src src clauses clauses] (if-let [[result src] (lex-1 src clauses)] (let [results (conj results result)] (if (= src "") results (recur results src clauses))))))
(defn- absolute-url? "True if the path contains an absolute or scheme-relative URL." [path] (boolean (re-matches #"(https?:)?//.*" path)))
(def -word-regexp #":([a-zA-Z_][\w\-]*)")
(def -literal-regexp #"(:[^a-zA-Z_*]|[^:*])+")
(defn route-compile "Compile a path string using the routes syntax into a uri-matcher struct." ([path] (route-compile path {})) ([path regexs] (let [splat #"\*" word -word-regexp literal -literal-regexp word-group (fn* [p1__1252#] (do (keyword (.group p1__1252# 1)))) word-regex (fn* [p1__1253#] (regexs (word-group p1__1253#) "[^/,;?]+"))] (CompiledRoute. (re-pattern (apply str (lex path splat "(.*)" #"^//" "https?://" word (fn [w] (let [w (word-regex w)] (str "(" (if (regexp? w) (.-source w) w) ")"))) literal (fn* [p1__1254#] (do (re-escape (.group p1__1254#))))))) (remove nil? (lex path splat :* word word-group literal nil)) (absolute-url? path)))))
(extend-type string Route (route-matches [route request] (route-matches (route-compile route) request)))
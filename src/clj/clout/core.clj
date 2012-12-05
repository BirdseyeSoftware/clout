^{:cljs
  '(ns clout.core
     "Library for parsing the Rails routes syntax."
     (:require [goog.string :as gstring]
               [clojure.string :as string]
               [goog.Uri :as uri]))}
(ns clout.core
  "Library for parsing the Rails routes syntax."
  (:require [clojure.string :as string])
  (:import java.util.regex.Matcher
           [java.net URLDecoder URLEncoder]))

;; (defn log [& args]
;;   ^{:cljs '(.log js/console (apply str args))}
;;   (apply println args))

#_(:cljs
   (defn re-matcher [re s]
     (let [re (js* "new RegExp(re.source, 'g')")
           match (.exec re s)
           matcher (js-obj)]
       ;; (log "* " re " " s)
       ;; NOTE: we avoid setting this attributes
       ;; through js-obj given that it doesn't work
       ;; on advanced compilation mode
       (set! (.-lookingAt matcher)
             (fn []
               (and match
                    (= (.-index match) 0))))
       (set! (.-matches matcher)
             (fn []
               (and match
                    (= s (aget match 0)))))
       (set! (.-start matcher)
             (fn []
               (and match
                    (.-index match))))
       (set! (.-end matcher)
             (fn []
               (if match
                 (.-lastIndex re)
                 (throw (js/Error. "invalid state")))))
       (set! (.-groupCount matcher)
             (fn []
               (or (and match (.-length match))
                   0)))
       (set! (.-group matcher)
             (fn [& [i]]
               (and match
                    (aget match (or i 0)))))
       matcher)))

;; ^:clj
;; (defn re-matcher [re s]
;;   (log "* " re " " s)
;;   (clojure.core/re-matcher re s))

;; Regular expression utilties

(def ^{:private true} re-chars
  (set "\\.*+|?()[]{}$^"))

(defn- re-escape
  "Escape all special regex chars in a string."
  [s]
  (string/escape
    s
    (reduce (fn [m c] (assoc m c (str \\ c))) {} re-chars)))

(defn re-groups*
  "More consistant re-groups that always returns a vector of groups, even if
  there is only one group."
  [^Matcher matcher]
  (for [i (range (.groupCount matcher))]
    (do
      ;; (log ".group - " (.group matcher (int (inc i))))
      (.group matcher (int (inc i))))))

;; Route matching

(defn path-decode
  "Decode a path segment in a URI. Defaults to using UTF-8 encoding."
  ([path]
     (path-decode path "UTF-8"))
  ([path encoding]
     (-> (string/replace path "+"
                         ^{:cljs '(gstring/urlEncode "+")}
                         (URLEncoder/encode "+" encoding))
         ^{:cljs '(gstring/urlDecode)}
         (URLDecoder/decode encoding))))

(defn- assoc-vec
  "Associate a key with a value. If the key already exists in the map, create a
  vector of values."
  [m k v]
  (assoc m k
    (if-let [cur (m k)]
      (if (vector? cur)
        (conj cur v)
        [cur v])

      v)))

(defn- assoc-keys-with-groups
  "Create a hash-map from a series of regex match groups and a collection of
  keywords."
  [groups keys]
  (reduce
    (fn [m [k v]] (assoc-vec m k v))
    {}
    (map vector keys groups)))

(defn- request-url
  "Return the complete URL for the request."
  [request]
  (str (name (:scheme request))
       "://"
       (get-in request [:headers "host"])
       (:uri request)))

(defn- path-info
  "Return the path info for the request."
  [request]
  (or (:path-info request)
      (:uri request)))

(defprotocol Route
  (route-matches [route request]
    "If the route matches the supplied request, the matched keywords are
    returned as a map. Otherwise, nil is returned."))

(defrecord CompiledRoute [re keys absolute?]
  Route
  (route-matches [route request]
    (let [path-info (if absolute?
                      (request-url request)
                      (path-info request))
          matcher   (re-matcher re path-info)]
      ;; (log ".matches - " (.matches matcher))
      (if (.matches matcher)
        (assoc-keys-with-groups
          ^{:cljs '(re-groups* matcher)}
          (map path-decode (re-groups* matcher))
          keys)))))

;; Compile routes

(defn- lex-1
  "Lex one symbol from a string, and return the symbol and trailing source."
  [src clauses]
  (some
    (fn [[re action]]
      (let [matcher (re-matcher re src)]
        (if (.lookingAt matcher)
          (do
            ;; (log ".lookingAt - " (.lookingAt matcher))
            ;; (log ".end - " (.end matcher))
            [(if (fn? action) (action matcher) action)
             (subs src (.end matcher))]))))
    (partition 2 clauses)))

(defn- lex
  "Lex a string into tokens by matching against regexs and evaluating
   the matching associated function."
  [src & clauses]
  (loop [results []
         src     src
         clauses clauses]
    (if-let [[result src] (lex-1 src clauses)]
      (let [results (conj results result)]
        (if (= src "")
          results
          (recur results src clauses))))))

(defn- absolute-url?
  "True if the path contains an absolute or scheme-relative URL."
  [path]
  (boolean (re-matches #"(https?:)?//.*" path)))

^{:cljs '(def -word-regexp #":([a-zA-Z_][\w\-]*)")}
(def -word-regexp #":([\p{L}_][\p{L}_0-9-]*)")

^{:cljs '(def -literal-regexp #"(:[^a-zA-Z_*]|[^:*])+")}
(def -literal-regexp #"(:[^\p{L}_*]|[^:*])+")

(defn route-compile
  "Compile a path string using the routes syntax into a uri-matcher struct."
  ([path]
    (route-compile path {}))
  ([path regexs]
    (let [splat   #"\*"
          word    -word-regexp
          literal -literal-regexp
          word-group #(do
                        ;; (log ".group - " (.group ^Matcher % 1))
                        (keyword (.group ^Matcher % 1)))
          word-regex #(regexs (word-group %) "[^/,;?]+")]
      (CompiledRoute.
        (re-pattern
          (apply str
            (lex path
              splat   "(.*)"
              #"^//"  "https?://"
              word    ^{:cljs
                        '(fn [w]
                           (let [w (word-regex w)]
                             (str "("
                                  (if (regexp? w) (.-source w) w)
                                  ")")))}
                      (fn [w] (str "(" (word-regex w) ")"))
              literal #(do
                         ;; (log ".group - " (.group ^Matcher %))
                         (re-escape (.group ^Matcher %))))))
        (remove nil?
          (lex path
            splat   :*
            word    word-group
            literal nil))
        (absolute-url? path)))))

(extend-type String
  Route
  (route-matches [route request]
    (route-matches (route-compile route) request)))

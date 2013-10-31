(ns query-server.web-server
    (:require
        [compojure.handler :as handler]
        [compojure.route :as route]
        [clojure.data.json :as json]
        [clojure.string :as str]
        [clojure.java.io :as io]
        [utilities.core :as util]
        [utilities.shutil :as sh]
        [clj-time.core :as time]
    )
    (:use
        [compojure.core :only (defroutes GET PUT POST DELETE HEAD ANY)]
        [ring.adapter.jetty :only (run-jetty)]
        [clj-time.coerce :only (to-long)]
        [slingshot.slingshot :only (try+ throw+)]
    )
    (:import
        [java.security MessageDigest]
        [java.nio.charset StandardCharsets]
        [java.nio.file Path]
        [java.sql SQLException]
    )
)

(defn log-in [params]
    (println "POST /sql/" (pr-str params))
    (let [
        email (:email params)
        psw (:password params)
        ]
        (if (and
                (= email "a@b.c")
                (= psw "123")
            )
            {
                :status 201
                :headers {
                    "Content-Type" "text/html"
                }
                :cookies {"user_id" {:value "12345" :path "/sql/"}}
                :body "
<!doctype html>
<html>
<head>
<meta http-equiv='refresh' content='1;url=/sql/'>
</head>
</html>
"
            }
            {
                :status 401
            }
        )
    )
)

(defn authenticate [cookies]
    (when-not (and
            cookies
            (contains? cookies "user_id")
            (= (:value (cookies "user_id")) "12345")
        )
        (throw+
            {
                :status 401
                :headers {"Content-Type" "application/json"}
                :body (json/write-str {})
            }
        )
    )
)

(def results (ref {}))
(def csv (ref {}))

(defn progress [qid result total-stages current-stage]
    (if (< current-stage total-stages)
        (do
            (dosync
                (let [
                    log (-> (@results qid) (:log) (.concat (format "stage %d\n" current-stage)))
                    ]
                    (alter results
                        update-in [qid]
                        assoc :progress [current-stage total-stages] :log log
                    )
                )
            )
            (Thread/sleep 600)
            (recur qid result total-stages (inc current-stage))
        )
        (do
            (let [now (to-long (time/now))]
                (dosync
                    (alter results
                        update-in [qid]
                        assoc
                            :result result
                            :status "succeeded"
                            :progress [total-stages total-stages]
                            :url (format "queries/%d/csv" qid)
                            :duration (- now (:submit_time (@results qid)))
                            :count 54321
                    )
                )
                (Thread/sleep 3000)
                (dosync
                    (alter csv
                        assoc qid (str/join "\n"
                            (cons
                                (str/join "," (:titles result))
                                (map #(str/join "," %) (:values result))
                            )
                        )
                    )
                )
            )
        )
    )
)

(defn do-query [qid query]
    (let [progress-stages (inc (rand-int 10))]
        (try
            (if (= query "select * from smile")
                (let [result {
                        :titles ["id" "item"]
                        :values [
                            [1 "hehe"]
                            [2 "haha"]
                            [3 "xixi"]
                        ]
                    }
                    ]
                    (future (progress qid result progress-stages 0))
                )
                (throw (SQLException.))

            )
        (catch SQLException ex
            (dosync
                (alter results update-in [qid] assoc :status "failed" :error "invalid sql")
            )
        ))
        {
            :status 201
            :headers {
                "Content-Type" "application/json"
            }
            :body (json/write-str {:id qid})
        }
    )
)

(defn submit-query [params cookies]
    (println "/sql/queries/" (pr-str params) (pr-str cookies))
    (try+
        (authenticate cookies)
        (let [
            {:keys [app version db query]} params
            qid (rand-int 10000)
            now (to-long (time/now))
            ]
            (dosync
                (alter results assoc qid {
                    :status "running"
                    :query query
                    :log ""
                    :submit_time now
                })
            )
            (do-query qid query)
        )
    (catch map? ex
        ex
    ))
)

(defn check-qid [qid]
    (when-not (contains? @results qid)
        (throw+
            {
                :status 404
                :headers {"Content-Type" "application/json"}
                :body (json/write-str {})
            }
        )
    )
)

(defn get-result [qid]
    (try+
        (let [qid (Long/parseLong qid)]
            (println (format "GET /sql/queries/%d/" qid))
            (dosync
                (check-qid qid)
                (let [log (:log (@results qid))]
                    (alter results update-in [qid] assoc :log "")
                    {
                        :status 200
                        :headers {"Content-Type" "application/json"}
                        :body (json/write-str (assoc (@results qid) :log log))
                    }
                )
            )
        )
    (catch map? ex
        ex
    ))
)

(defn get-meta [cookies]
    (println "GET /sql/meta" (pr-str cookies))
    (try+
        (authenticate cookies)
        {
            :status 200
            :headers {"Content-Type" "application/json"}
            :body (json/write-str
                [
                    {
                        :type "namespace"
                        :name "WoW"
                        :children [
                            {
                                :type "namespace"
                                :name "panda"
                                :children [
                                    {
                                        :type "namespace"
                                        :name "db"
                                        :children [
                                            {
                                                :type "table"
                                                :name "smile"
                                                :children [
                                                    {
                                                        :name "item"
                                                        :type "varchar(255)"
                                                    }
                                                    {
                                                        :name "id"
                                                        :type "integer primary key autoincrement"
                                                    }
                                                ]
                                                :samples [
                                                    ["hehe" 1]
                                                    ["haha" 2]
                                                ]
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    }
                ]
            )
        }
    (catch map? ex
        ex
    ))
)

(def saved-queries (ref {}))

(defn get-saved-queries [cookies]
    (println "GET /sql/saved/" (pr-str cookies))
    (try+
        (authenticate cookies)
        (let [r (dosync
                (vec (for [
                    [id v] @saved-queries
                    ]
                    (assoc v :id id)
                ))
            )
            ]
            {
                :status 200
                :headers {"Content-Type" "application/json"}
                :body (json/write-str r)
            }
        )
    (catch map? ex
        ex
    ))
)

(defn check-saved-query-no-duplicated-name? [name]
    (let [ids (for [
                [id v] @saved-queries
                :when (= name (:name v))
            ]
            id
        )
        ]
        (when-not (empty? ids)
            (throw+
                {
                    :status 409
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {:id (first ids)})
                }
            )
        )
    )
)

(defn add-query [params cookies]
    (println "POST /sql/saved/" (pr-str params) (pr-str cookies))
    (try+
        (authenticate cookies)
        (let [
            {:keys [name app version db query]} params
            new-id (rand-int 1000)
            ]
            (dosync
                (check-saved-query-no-duplicated-name? name)

                (alter saved-queries assoc new-id {
                    :name name
                    :app app
                    :version version
                    :db db
                    :query query
                })
                {
                    :status 201
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {:id new-id})
                }
            )
        )
    (catch map? ex
        ex
    ))
)

(defn check-saved-query-id? [id]
    (when-not (contains? @saved-queries id)
        (throw+
            {
                :status 404
                :headers {"Content-Type" "application/json"}
                :body (json/write-str {})
            }
        )
    )
)

(defn delete-saved-query [cookies params]
    (let [
        id (->> params (:qid) (Long/parseLong))
        ]
        (println (format "DELETE /sql/saved/%d/" id) (pr-str params) (pr-str cookies))
        (try+
            (authenticate cookies)
            (dosync
                (check-saved-query-id? id)

                (alter saved-queries dissoc id)
                {
                    :status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {})
                }
            )
        (catch map? ex
            ex
        ))
    )
)

(defn download [qid]
    (println (format "GET queries/%d/csv" qid))
    (if-let [r (@csv qid)]
        {
            :status 200
            :headers {"Content-Type" "text/csv"}
            :body r
        }
        {
            :status 404
        }
    )
)

(defn sniff [qid]
    (println (format "HEAD queries/%d/csv" qid))
    (if-let [r (@csv qid)]
        {
            :status 200
            :headers {"Content-Type" "application/json"}
            :body "{}"
        }
        {
            :status 404
            :headers {"Content-Type" "application/json"}
            :body "{}"
        }
    )
)

(defn list-queries [cookies]
    (println "GET /sql/queries/" (pr-str cookies))
    (try+
        (authenticate cookies)
        (let [
            r (dosync
                (into []
                    (for [
                        [k v] @results
                        :let [{:keys [query status url submit-time duration]} v]
                        ]
                        (merge {
                                :id k
                                :query query
                                :status status
                                :submit_time submit-time
                            }
                            (if duration {:duration duration} {})
                            (if url {:url url} {})
                        )
                    )
                )
            )
            ]
            {
                :status 200
                :headers {"Content-Type" "application/json"}
                :body (json/write-str r)
            }
        )
    (catch map? ex
        ex
    ))
)

; collector adminstration

(defn admin-main [cookies dir]
    (println "GET /sql/admin.html" cookies)
    (try+
        (authenticate cookies)
        (slurp (.toFile (sh/getPath dir "admin.html")))
    (catch map? ex
        (slurp (.toFile (sh/getPath dir "index.html")))
    ))
)

(def collectors (ref {
    1 {
        :name "xixi"
        :url "http://1.1.1.1:1111/xixi"
        :status "running"
        :recent-sync (to-long (time/now))
    }
    2 {
        :name "hehe"
        :url "http://2.2.2.2:2222/hehe"
        :status "stopped"
        :recent-sync (to-long (time/now))
    }
    3 {
        :name "haha"
        :url "http://3.3.3.3:3333/haha"
        :status "no-sync"
    }
    4 {
        :name "hoho"
        :url "http://4.4.4.4:4444/hoho"
        :status "abandoned"
        :reason "really?"
    }
    5 {
        :name "yoyo"
        :url "http://5.5.5.5:5555/yoyo"
        :status "abandoned"
        :reason "really?"
        :recent-sync (to-long (time/now))
    }
}))

(defn check-collector [msg pred]
    (let [cids (for [
            [cid v] @collectors
            :when (pred cid v)
            ]
            cid
        )
        ]
        (when-not (empty? cids)
            (throw+
                {
                    :status 409
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {
                        :error msg
                        :collector (first cids)
                    })
                }
            )
        )
    )
)

(defn check-duplicated-name?
    ([cid name]
        (check-collector "duplicated name"
            (fn [c v]
                (and (not= cid c) (= name (:name v)))
            )
        )
    )

    ([name]
        (check-collector "duplicated name"
            (fn [c v]
                (= name (:name v))
            )
        )
    )
)

(defn check-duplicated-url?
    ([cid url]
        (check-collector "duplicated url"
            (fn [c v]
                (and (not= cid c) (= url (:url v)))
            )
        )
    )

    ([url]
        (check-collector "duplicated url"
            (fn [c v]
                (= url (:url v))
            )
        )
    )
)

(defn is-collector-no-sync? [cid]
    (if-let [v (@collectors cid)]
        (when-not (= (:status v) "no-sync")
            (throw+
                {
                    :status 403
                    :headers {"Content-Type" "application/json"}
                    :body "null"
                }
            )
        )
    )
)

(defn does-collector-exist? [cid]
    (when-not (contains? @collectors cid)
        (throw+
            {
                :status 404
                :headers {"Content-Type" "application/json"}
                :body "null"
            }
        )
    )
)

(defn add-collector [params cookies]
    (println "POST /sql/collectors" params cookies)
    (try+
        (authenticate cookies)
        (let [
            name (:name params)
            url (:url params)
            cid (rand-int 1000)
            ]
            (dosync
                (check-duplicated-name? name)
                (check-duplicated-url? url)

                (let [r {:status "no-sync" :name name :url url}]
                    (alter collectors assoc cid r)
                    {
                        :status 201
                        :headers {"Content-Type" "application/json"}
                        :body (json/write-str (assoc r :id cid))
                    }
                )
            )
        )
    (catch map? ex
        ex
    ))
)

(defn list-collectors [cookies]
    (println "GET /sql/collectors/" cookies)
    (try+
        (authenticate cookies)

        (let [r (dosync
                (for [
                    [cid v] @collectors
                    ]
                    (assoc v :id cid)
                )
            )
            ]
            {
                :status 200
                :headers {"Content-Type" "application/json"}
                :body (json/write-str r)
            }
        )
    (catch map? ex
        ex
    ))
)

(defn delete-collector [params cookies]
    (try+
        (let [
            cid (-> params (:cid) (Long/parseLong))
            reason (-> params (:reason))
            ]
            (println (format "DELETE /sql/collectors/%d" cid) cookies)
            (authenticate cookies)
            (when-not reason
                (throw+ {
                    :status 409
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {
                        :error "require reason"
                    })
                })
            )
            (dosync
                (does-collector-exist? cid)

                (alter collectors update-in [cid] assoc :reason reason)
                {
                    :status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {})
                }
            )
        )
    (catch map? ex
        ex
    ))
)

(defn edit-collector [params cookies]
    (try+
        (authenticate cookies)
        (let [
            cid (Long/parseLong (:cid params))
            name (:name params)
            url (:url params)
            ]
            (println (format "PUT /sql/collectors/%d" cid) cookies name url)
            (dosync
                (does-collector-exist? cid)
                (is-collector-no-sync? cid)
                (check-duplicated-name? cid name)
                (check-duplicated-url? cid url)

                (alter collectors update-in [cid] assoc :name name :url url)
                {
                    :status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/write-str {})
                }
            )
        )
    (catch map? ex
        ex
    ))
)

(defn app [opts]
    (handler/site
        (defroutes app-routes
            (GET "/sql" {}
                {
                    :status 200
                    :headers {"Content-Type" "text/html"}
                    :body "
<!doctype html>
<html>
<head>
<meta http-equiv='refresh' content='1;url=/sql/'>
</head>
</html>
"
                }
            )
            (POST "/sql/" {:keys [params]}
                (log-in params)
            )

            (GET "/sql/meta" {:keys [cookies]}
                (get-meta cookies)
            )

            (POST "/sql/queries/" {:keys [params cookies]}
                (submit-query params cookies)
            )
            (GET "/sql/queries/" {:keys [cookies]}
                (list-queries cookies)
            )
            (GET "/sql/queries/:qid/" [qid]
                (get-result qid)
            )
            (HEAD "/sql/queries/:qid/csv" [qid]
                (sniff (Long/parseLong qid))
            )
            (GET "/sql/queries/:qid/csv" [qid]
                (download (Long/parseLong qid))
            )


            (POST "/sql/saved/" {:keys [cookies params]}
                (add-query params cookies)
            )
            (GET "/sql/saved/" {:keys [cookies]}
                (get-saved-queries cookies)
            )
            (DELETE "/sql/saved/:qid/" {:keys [cookies params]}
                (delete-saved-query cookies params)
            )

            (GET "/sql/" {:keys [cookies]}
                (if (and cookies (get cookies "user_id"))
                    (slurp (.toFile (sh/getPath (:dir opts) "query.html")))
                    (slurp (.toFile (sh/getPath (:dir opts) "index.html")))
                )
            )

            ; the following is for collector adminitration page

            (POST "/sql/collectors/" {:keys [params cookies]}
                (add-collector params cookies)
            )
            (GET "/sql/collectors/" {:keys [cookies]}
                (list-collectors cookies)
            )
            (DELETE "/sql/collectors/:cid" {:keys [params cookies]}
                (delete-collector params cookies)
            )
            (PUT "/sql/collectors/:cid" {:keys [params cookies body]}
                (edit-collector params cookies)
            )

            (route/files "/sql/" {:root (:dir opts) :allow-symlinks? true})
            (route/not-found "Not Found")
        )
    )
)

(defn start [opts]
    (run-jetty (app opts)
        {
            :port (:port opts)
            :join? true
        }
    )
)

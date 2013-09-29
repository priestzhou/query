(ns query-server.web-server
    (:require
        [compojure.handler :as handler]
        [compojure.route :as route]
        [clojure.data.json :as json]
        [clojure.string :as str]
        [utilities.core :as util]
        [utilities.shutil :as sh]
        [clj-time.core :as time]
    )
    (:use
        [compojure.core :only (defroutes GET PUT POST DELETE HEAD ANY)]
        [ring.adapter.jetty :only (run-jetty)]
        [clj-time.coerce :only (to-long)]
    )
    (:import
        [java.security MessageDigest]
        [java.nio.charset StandardCharsets]
        [java.nio.file Path]
        [java.sql SQLException]
    )
)

(defn authenticate [email psw]
    (when (and (= email "a@b.c") (= psw "123"))
        12345
    )
)

(defn extract-user-id [cookies]
    (when-let [user-id (cookies "user_id")]
        (:value user-id)
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
                            :duration (- now (:submit-time (@results qid)))
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
    (let [
        user-id (extract-user-id cookies)
        {:keys [app version db query]} params
        qid (rand-int 10000)
        now (to-long (time/now))
        ]
        (println "POST queries/ " (pr-str {:user_id user-id :app app :version version :db db :query query}))
        (dosync
            (alter results assoc qid {
                :status "running" 
                :query query
                :log "" 
                :submit-time now
            })
        )
        (do-query qid query)
    )
)

(defn get-result [qid]
    (let [
        qid (Long/parseLong qid)
        _ (println (format "GET queries/%d/" qid))
        result (dosync
            (let [r (@results qid)]
                (alter results update-in [qid] assoc :log "")
                r
            )
        )
        ]
        {
            :status 200
            :headers {"Content-Type" "application/json"}
            :body (json/write-str result)
        }
    )
)

(defn get-meta [cookies]
    (let [user-id (extract-user-id cookies)]
        (println "GET meta" (pr-str {:user_id user-id}))
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
                                                :columns [
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
    )
)

(def saved-queries (ref {}))

(defn get-saved-queries [cookies]
    (let [user-id (extract-user-id cookies)]
        (println (format "GET saved/ %s" (pr-str {:user_id user-id})))
        {
            :status 200
            :headers {"Content-Type" "application/json"}
            :body (json/write-str @saved-queries)
        }
    )
)

(defn add-query [params cookies]
    (let [
        user-id (extract-user-id cookies)
        {:keys [name app version db query]} params
        qname name
        _ (println (format "POST saved/?name=%s&app=%s&version=%s&db=%s&query=%s %s" app version db qname query (pr-str {:user_id user-id})))
        r (dosync
            (let [qid (for [
                    [id {:keys [name]}] @saved-queries
                    :when (= name qname)
                    ] 
                    id
                )
                ]
                (if (empty? qid)
                    (let [new-id (rand-int 1000)]
                        (alter saved-queries assoc new-id {
                            :name qname 
                            :app app
                            :version version
                            :db db
                            :query query
                        })
                        new-id
                    )
                    nil
                )
            )
        )
        ]
        (if r
            {
                :status 201
                :headers {"Content-Type" "text/plain"}
                :body (format "%d" r)
            }
            {
                :status 400
                :headers {"Content-Type" "text/plain"}
                :body qname
            }
        )
    )
)

(defn delete-saved-query [cookies params]
    (let [
        user-id (extract-user-id cookies)
        id (->> params (:qid) (Long/parseLong))
        _ (println (format "DELETE saved/%d/ %s" id (pr-str {:user_id user-id})))
        r (dosync
            (if-let [q (@saved-queries id)]
                (do
                    (alter saved-queries dissoc id)
                    id
                )
            )
        )
        ]
        (if r
            {
                :status 200
            }
            {
                :status 404
            }
        )
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
    (let [
        user-id (extract-user-id cookies)
        _ (println "GET queries/" (pr-str {:user_id user-id}))
        r (dosync
            (let [ks (keys @results)]
                (into {}
                    (for [
                        k ks
                        :let [v (@results k)]
                        :let [{:keys [query status url submit-time duration]} v]
                        ]
                        [k (merge 
                                {:query query :status status :submit-time submit-time}
                                (if duration {:duration duration} {})
                                (if url {:url url} {})
                            )
                        ]
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
            (POST "/sql/" {params :params}
                (if-let [auth (authenticate (:email params) (:password params))]
                    {
                        :status 201
                        :headers {
                            "Content-Type" "text/html"
                        }
                        :cookies {"user_id" {:value auth :path "/sql/" :max-age 36000}}
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

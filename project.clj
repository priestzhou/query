(defproject query "0.1.0-SNAPSHOT"
  :description "a mock server for querying"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
    [org.clojure/clojure "1.5.1"]
    [ring "1.2.0"]
    [compojure "1.1.5"]
    [korma "0.3.0-RC5"]
    [org.clojure/data.json "0.2.3"]
    [slingshot "0.10.3"]]
  :main query-server.main
  :aot [query-server.main])

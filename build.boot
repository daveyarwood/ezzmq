(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [org.zeromq/jeromq   "0.3.5"]
                  [adzerk/bootlaces    "0.1.13" :scope "test"]
                  [adzerk/boot-test    "1.1.2"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0")
(bootlaces! +version+)

(task-options!
  pom {:project 'ezzmq
       :version +version+
       :description "A small library of opinionated ZeroMQ boilerplate for Clojure"
       :url "https://github.com/daveyarwood/ezzmq"
       :scm {:url "https://github.com/daveyarwood/ezzmq"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask deploy
  "Builds uberjar, installs it to local Maven repo, and deploys it to Clojars."
  []
  (comp (build-jar) (push-release)))

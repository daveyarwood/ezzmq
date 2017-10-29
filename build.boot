(set-env!
  :source-paths #{"src"}
  :resource-paths #{"test"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [org.zeromq/jeromq   "0.4.2"]
                  [clj-wallhack        "1.0.1"]
                  [potemkin            "0.4.4"]
                  [adzerk/bootlaces    "0.1.13" :scope "test"]
                  [adzerk/boot-test    "1.2.0"]])

(require '[adzerk.boot-test          :refer :all]
         '[adzerk.bootlaces          :refer :all]
         '[boot.from.io.aviso.ansi   :as    ansi]
         '[boot.from.me.raynes.conch :as    sh]
         '[clojure.java.io           :as    io]
         '[clojure.string            :as    str]
         '[ezzmq.util                :as    util])

(import '[java.net ServerSocket])

(def +version+ "0.7.2")
(bootlaces! +version+)

(task-options!
  pom {:project 'io.djy/ezzmq
       :version +version+
       :description "A small library of opinionated ZeroMQ boilerplate for Clojure"
       :url "https://github.com/daveyarwood/ezzmq"
       :scm {:url "https://github.com/daveyarwood/ezzmq"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}}
  test {:include #"-test$"})

(deftask deploy
  "Builds uberjar, installs it to local Maven repo, and deploys it to Clojars."
  []
  (comp (build-jar) (push-release)))

(comment
  "An example takes the form:
   [example1 [process1 process2 process3 ...]]

   A process takes the form:
   [\"process-name\" :foo 1 :bar 2 :baz 3]

   where :foo, :bar and :baz are keyword options.

   Supported keyword options for a process:

   :ports - a list of symbols referring to a port used by the example. The
   :ports of the processes in an example are coordinated such that the same
   symbol refers to the same port. Each symbol is mapped to a random available
   port when the example is run. The ports are provided, in order, as the
   first arguments to the process.

   :args - a list of additional arguments provided to the process after the
   port arguments

   :times - the number of instances of the process to start")

(def all-examples
  [["reqrep"
    [["hwserver" :ports '[a]]
     ["hwclient" :ports '[a]]]]
   ["pubsub"
    [["wuserver" :ports '[a]]
     ["wuclient" :ports '[a] :args [27713]]
     ["wuclient" :ports '[a] :args [27278]]]]
   ["pushpull"
    [["taskvent" :ports '[vent sink] :args [3000]]
     ["tasksink" :ports '[sink]]
     ["taskwork" :ports '[vent sink] :times 3]]]
   ["polling"
    [["mspoller" :ports '[a b]]
     ["taskvent" :ports '[a _] :args [1000]]
     ["wuserver" :ports '[b]]]]
   ["interrupt"
    [["interrupt" :args [5000]]]]
   ["rrbroker"
    [["hwclient" :ports '[frontend] :times 2]
     ["rrbroker" :ports '[frontend backend]]
     ["rrworker" :ports '[backend] :times 2]]]
   ["pubsubproxy"
    [["wuserver" :ports '[a]]
     ["wuproxy" :ports '[a b]]
     ["wuclient" :ports '[b] :args [27713]]
     ["wuclient" :ports '[b] :args [27278]]]]
   ["pushpull-with-kill-signal"
    [["taskvent" :ports '[vent sink] :args [3000]]
     ["tasksink2" :ports '[sink ctrl]]
     ["taskwork2" :ports '[vent sink ctrl] :times 3]]]
   ["mtserver"
    [["mtserver" :ports '[a]]
     ["hwclient" :ports '[a] :times 3]]]
   ["mtrelay"
    [["mtrelay"]]]
   ["pubsub-sync"
    [["syncpub" :ports '[a b] :args [4 10000 500]]
     ["syncsub" :ports '[a b] :times 4]]]
   ["pubsub-envelope"
    [["psenvpub" :ports '[a]]
     ["psenvsub" :ports '[a]]]]
   ["identity"
    [["identity"]]]])

(def examples-map (delay (into {} all-examples)))

(defn find-example
  [example]
  (or (@examples-map example) (util/errfmt "Example not found: %s" example)))

(let [ports (atom #{})]
  (defn find-open-port
    []
    (let [port (with-open [socket (ServerSocket. 0)]
                 (.getLocalPort socket))]
      (if (@ports port)
        (recur)
        (do
          (swap! ports conj port)
          port)))))

(def examples-directory "examples/zguide")

(def color-fns
  [ansi/bold-black
   ansi/bold-red
   ansi/bold-green
   ansi/bold-yellow
   ansi/bold-blue
   ansi/bold-magenta
   ansi/bold-cyan
   ansi/red
   ansi/green
   ansi/yellow
   ansi/blue
   ansi/magenta
   ansi/cyan
   ansi/white
   #_ansi/black ;; impossible to see on a black background
   ])

(defn run-process
  [[color-fn process command] timeout-ms]
  (let [proc   (apply sh/proc command)
        say    #(println (color-fn (format "[%s]" process)) %)
        funnel #(with-open [rdr (io/reader %)]
                  (doseq [line (line-seq rdr)]
                    (say line)))]
    (future (funnel (:out proc)))
    (future (funnel (:err proc)))
    (future (sh/exit-code proc timeout-ms))))

(defn run-example
  [example timeout-ms]
  (println "-------------------------")
  (println "Running example:" example)
  (let [processes
        (->> (find-example example)
             (map (fn [[process & opts]]
                    (merge {:process process}
                           (apply hash-map opts)))))

        port-numbers
        (as-> processes x
          (map :ports x)
          (apply concat x)
          (set x)
          (zipmap x (repeatedly find-open-port)))

        processes
        (->> processes
             (map (fn [{:keys [process ports args times]
                        :or   {ports [] args [] times 1}}]
                    (let [script (format "%s/%s.boot"
                                         examples-directory
                                         process)
                          ports  (map port-numbers ports)
                          cmd    (->> (concat [script] ports args)
                                      (map str))]
                      (repeat times [process cmd]))))
             (apply concat)
             (map cons (cycle color-fns)))]
    (println "Starting processes:"
             (str/join ", " (map second processes)))
    (println "-------------------------")
    (doall (pmap #(deref (run-process % timeout-ms)) processes))))

(deftask examples
  [l ls              bool  "List all of the examples."
   e example EXAMPLE [str] "An example to run."
   a all             bool  "Run all examples."
   t timeout TIMEOUT int   "The number of seconds to wait before stopping the example. (default: 20 seconds)"]
  (let [timeout-ms (* 1000 (or timeout 20))]
    (cond
      ls
      (println (str/join \newline (map first all-examples)))

      example
      (doseq [example example]
        (run-example example timeout-ms))

      all
      (doseq [example (map first all-examples)]
        (run-example example timeout-ms))

      :else
      (util/errfmt "Invalid options. See `boot examples --help.` for usage."))))

(deftask update-examples
  "Updates ezzmq dependency in each example script to the current version
   specified here in build.boot."
  []
  (with-pass-thru _
    (doseq [file (->> "examples" io/file file-seq (drop 2))
            :let [content (slurp file)
                  updated-content (str/replace
                                    content
                                    #"(io\.djy/ezzmq) \"[^\"]+\""
                                    (format "$1 \"%s\"" +version+))]]
      (spit file updated-content))))

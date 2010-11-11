(ns speclj.running
  (:use
    [speclj.exec :only (pass-result fail-result)]
    [speclj.reporting :only (report-runs report-pass report-fail active-reporter)]
    [speclj.components :only [reset-with]]))

(defn secs-since [start]
  (/ (double (- (System/nanoTime) start)) 1000000000.0))

(defn- eval-components [components]
  (doseq [component components] ((.body component))))

(defn- eval-characteristic [characteristic]
  (let [description @(.description characteristic)]
    (eval-components @(.befores description))
    ((.body characteristic))
    (eval-components @(.afters description))
    (doseq [with @(.withs description)] (reset-with with))))

(defn- do-characteristic [characteristic reporter]
  (let [start-time (System/nanoTime)]
    (try
      (eval-characteristic characteristic)
      (report-pass reporter)
      (pass-result characteristic (secs-since start-time))
      (catch Exception e
        (report-fail reporter)
        (fail-result characteristic (secs-since start-time) e)))))

(defn- do-characteristics [characteristics description reporter]
  (doall
    (for [characteristic characteristics]
      (do-characteristic characteristic reporter))))

(defn- do-description [description reporter]
  (eval-components @(.before-alls description))
  (let [results (do-characteristics @(.charcteristics description) description reporter)]
    (eval-components @(.after-alls description))
    results))

(defprotocol Runner
  (run [this description reporter])
  (report [this reporter]))

(deftype SingleRunner []
  Runner
  (run [this description reporter]
    (let [results (do-description description reporter)]
      (report-runs reporter results)))
  (report [this reporter]
    ))

(deftype MultiRunner [results]
  Runner
  (run [this description reporter]
    (let [run-results (do-description description reporter)]
      (swap! results into run-results)))
  (report [this reporter]
    (report-runs reporter @results)))

(defn new-multi-runner []
  (MultiRunner. (atom [])))

(def default-runner (new-multi-runner))
(declare *runner*)

(defn active-runner []
  (if (bound? #'*runner*)
    *runner*
    default-runner))

(defn submit-description [description]
  (run (active-runner) description (active-reporter)))


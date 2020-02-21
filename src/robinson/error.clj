(ns robinson.error
  (:require
            [clojure.stacktrace :as st]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [taoensso.nippy :as nippy])
  (:import [java.io DataInputStream DataOutputStream]))

(def ^:private encountered (atom #{}))

(defn- encountered?
  [h]
  (-> encountered
    deref
    (contains? h)))

(defn write-exception
  [state e h]
  (try
    (let [log-path (str "log/" h ".log")
          save-path (str "log/" h ".edn")]
      (with-open [o (io/output-stream save-path)]
        (nippy/freeze-to-out! (DataOutputStream. o) (get state :world)))
      (with-open [o (io/writer (io/output-stream log-path))]
        (binding [*out* o]
          (log/error (str e))
          (st/print-stack-trace e)
          (st/print-cause-trace e))))
    (catch Throwable t
      (log/error t))))

(defn log-exception
  [state e]
  ; determine e hash
  (let [h (hash (Throwable->map e))]
    (when-not (encountered? h)
      (swap! encountered conj h)
      (log/error e)
      (write-exception state e h))))
    
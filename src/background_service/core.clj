(ns background-service.core
  (:require [clojure.core.async :refer [thread]])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader PrintWriter InputStreamReader]))

;; listen on a port
;; loop and recur
;; block until message is received
;; echo the message
;; exit

(def port 2019)

(defn -main []
  (let [server (ServerSocket. port)]
    (println "Echo service started...")
    (let [client (.accept server) ;; this blocks until a connection is made
          autoflush true
          output (-> client
                     .getOutputStream
                     (PrintWriter. autoflush))
          input (-> client
                    .getInputStream
                    InputStreamReader.
                    BufferedReader.)]
      (loop []
        (let [message (.readLine input)]
          (println message)
          (if message
            (do (thread
                  (.println output "Processing...")
                  (Thread/sleep 5000)
                  (.println output (->> message
                                        reverse
                                        (apply str))))
                (recur))))))))

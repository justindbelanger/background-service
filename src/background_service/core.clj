(ns background-service.core
  (:require [clojure.core.async :refer [thread]])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader PrintWriter InputStreamReader]))

;; listen on a port
;; loop and recur
;; block until message is received
;; echo the message
;; exit
(defn -main []
  (let [server (ServerSocket. 2019) ;; TODO move Java stuff into a little library
        client (.accept server) ;; this blocks until a connection is made
        output (-> client
                   .getOutputStream
                   (PrintWriter. true))
        input (-> client
                  .getInputStream
                  InputStreamReader.
                  BufferedReader.)]
    (println "Echo service started...")
    (loop []
        (let [message (.readLine input)]
          (println message)
          (if message
            (do (thread
                  (.println output "Processing...")
                  (Thread/sleep 10000)
                  (.println output (-> message
                                       reverse
                                       str)))
                (recur)))))))

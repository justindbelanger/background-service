(ns background-service.core
  (:require [clojure.core.async :refer [pipe <!! >!! chan go-loop <! >! thread]])
  (:import [java.net ServerSocket]
           [java.io BufferedReader PrintWriter InputStreamReader]))

(defn client-chan [port]
  (let [server (ServerSocket. port)
        c-chan (chan)]
    (println "Echo service started...")
    (go-loop []
      (let [client (.accept server)]
        (println "Client connected...")
        (>! c-chan client))
      (recur))
    c-chan))

(defn request-chan [client]
  (let [autoflush true
        output (-> client
                   .getOutputStream
                   (PrintWriter. autoflush))
        input (-> client
                  .getInputStream
                  InputStreamReader.
                  BufferedReader.)
        r-chan (chan)]
    (go-loop []
      (let [request (.readLine input)]
        (println "Client sent: " request)
        (>! r-chan {:request request
                    :output (fn [o] (.println output o))}))
      (recur))
    r-chan))

(defn all-requests-chan [clients]
  (let [r-chan (chan)]
    (go-loop []
      (let [client (<! clients)
            req-chan (request-chan client)]
        (pipe req-chan r-chan))
      (recur))
    r-chan))

(defn response-chan [requests]
  (let [r-chan (chan)]
    (go-loop []
      (let [{:keys [request] :as request-info} (<! requests)]
        (>! r-chan (assoc request-info :response "Processing..."))
        (>! r-chan (assoc request-info :response (->> request
                                                      reverse
                                                      (apply str)))))
      (recur))
    r-chan))

(defn send-each [responses]
  (loop []
    (let [{:keys [response output]} (<!! responses)]
      (output response))
    (recur)))

(def port 2019)

(defn -main []
  (let [c-chan (client-chan port)
        reqs-chan (all-requests-chan c-chan)
        resp-chan (response-chan reqs-chan)]
    (send-each resp-chan)))

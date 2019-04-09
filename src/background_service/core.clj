(ns background-service.core
  (:require [clojure.core.async :refer [<!! >!! chan go-loop <! >! thread]])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader PrintWriter InputStreamReader]))

;; TODO make this an interface that is implemented in CLJ and in CLJS
(defn server-socket [port]
  (ServerSocket. port))

(defn client-chan [port]
  (let [server (server-socket port)
        c-chan (chan)]
    (println "Echo service started...")
    (go-loop []
      (let [client (.accept server)]
        (println "Client connected...")
        (>! c-chan client))
      (recur))
    c-chan))

(defn request-chan [client]
  (let [r-chan (chan)]
    (go-loop []
      (let [autoflush true
            output (-> client
                       .getOutputStream
                       (PrintWriter. autoflush))
            input (-> client
                      .getInputStream
                      InputStreamReader.
                      BufferedReader.)
            request (.readLine input)]
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
        (>! r-chan (<! req-chan)))
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

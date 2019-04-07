(ns background-service.core
  (:require [clojure.core.async :refer [<!! >!! chan go-loop <! >! thread]])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader PrintWriter InputStreamReader]))

;;; BEGIN DISCUSSION

;; listen on a port
;; loop and recur
;; block until message is received
;; echo the message
;; exit

;; /when/ a client connects,
;; then asynchronously set up streams for reading and writing with their socket
;; /when/ a client sends a message,
;; then asynchronously process their message and respond

;; when a client connects,
;; then set up input and output to communicate with them
;; when a client sends a message,
;; then process the message

;; right now we have two, nested layers of polling
;; a minor improvement would be to switch to two, nested layers of callbacks
;; a major improvement would be to skip that and go directly to a flattened, single layer of event handling
;; in Clojure we have some asynchrony tools. perhaps a channel would help us?

;; here's a pseudocode sketch
(comment
  (go-loop
     (sub my-chan :client-connected
          (fn [] ...))
   (sub my-chan :response-generated
        (fn [] ))
   (sub my-chan :request-received
        (fn [] (go (>! my-chan {:response (reverse message)}))))
   #_(let [msg (<! my-chan)]
       (sub )
       (case msg
         ))))

;; yet another try...
;; three channels: 1. the clients as they connect; 2. the requests as sent by each client; 3. the responses as they are processed by the server
(comment
  (defn make-client-chan [server]
    (let [client-chan (chan)]
      (go (>! client-chan (.accept server)))))

  (defn main-event-loop [server]
    (let [client-chan (make-client-chan server)
         request-chan (make-request-chan client-chan)
         response-chan (make-response-chan request-chan)]
     (go-loop
         (let [[value current-chan] (alts! [client-chan request-chan response-chan])]
           (condp = current-chan
             client-chan ...
             ...))))))
;; see https://github.com/bhauman/dotsters/blob/master/src/dots/core.cljs

;; advantages:
;; - one loop
;; - one area for defining behaviours upon specific events (almost like a state machine, eh?)
;; - can isolate the dependencies upon java classes to a smaller number of places in the code
;; disadvantages:
;; - anyone can listen for and interact with a client, not only a single part of the application
;; - a channel might be overkill for this situation. (what other tool(s) could I use?)
;; - overhead of using a channel instead of mere loops and threads/other asynchrony primitives
;; - modifying and extending the channel can happen anywhere in the code

;;; How would we tackle heterogeneous events in a UI situation?
;;; multiple channels or just one?
(comment (case :type message
               :mouse-event (handle-mouse event)))
;;; What happens if our application has some state that is affected based on asynchronous events/data?
;;; Should we coordindate the updates to the shared, global state with atoms, refs, agents, ...?

;;; END DISCUSSION

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

#_(defn -main []
  (let [server (ServerSocket. port)]
    (println "Echo service started...")
    ;; accept blocks until a connection is made
    (loop []
      (let [client (.accept server)]
        (println "Client connected...")
        (thread
          (let [autoflush true
                output (-> client
                           .getOutputStream
                           (PrintWriter. autoflush))
                input (-> client
                          .getInputStream
                          InputStreamReader.
                          BufferedReader.)]
            (loop []
              (let [message (.readLine input)]
                (println (str "Client sent: " message))
                (if message
                  (do (thread
                        (.println output "Processing...")
                        (Thread/sleep 5000)
                        (.println output (->> message
                                              reverse
                                              (apply str))))
                      (recur))))))))
      (recur))))

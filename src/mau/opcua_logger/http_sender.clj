;********************************************************************************
; Copyright (c) 2024 Jens Hofer
; 
;  This program and the accompanying materials are made available under the
;  terms of the Eclipse Public License 2.0 which is available at
;  http://www.eclipse.org/legal/epl-2.0.
;
;  This Source Code may also be made available under the following Secondary Licenses
;  when the conditions for such availability set forth in the Eclipse Public License, 
;  v. 2.0 are satisfied: GNU General Public License v3.0 or later 
;
;  SPDX-License-Identifier: EPL-2.0 OR GPL-3.0-or-later
; *******************************************************************************/

(ns mau.opcua-logger.http-sender
  (:require [mau.opcua-logger.poll-scheduler :as s]
            [clojure.core.async :as asy]
            [clojure.tools.logging :as log]
            [mau.opcua-logger.configuration :as conf]
            )
  (:import [java.net.http HttpClient HttpRequest HttpResponse]
           [java.net.http HttpResponse$BodyHandler HttpResponse$BodyHandlers HttpRequest$BodyPublishers]
           [java.net URI]
           [java.time Duration]))

(def client
  (-> (HttpClient/newBuilder)
      (.connectTimeout (Duration/ofSeconds 10)) ; todo configuration file
      (.build)))

(defn request-builder
  [uri-str]
  (HttpRequest/newBuilder (URI/create uri-str)))

(defn transform-opc-result
  [opc-result db-ids]
  (let [{:keys [value node timestamp]} opc-result
        val-id (get db-ids node)]
    (if (some? val-id)      
      {:value value 
       :timestamp timestamp
       :value-id (get db-ids node)})))

(defn transform-opc-results
  [opc-results db-ids]
  (reduce #(if (= (:status %2) 0)
             (conj %1 (transform-opc-result %2 db-ids))
             %1) 
          [] opc-results))

(defn send-result
  ([req-builder res]
  (send-result req-builder res 3))
  ([req-builder res retry]
    (let [request (-> req-builder
                      (.POST (HttpRequest$BodyPublishers/ofString (prn-str res)))
                      (.build))]
      (try
        (.send client request (HttpResponse$BodyHandlers/ofString))
        (catch Exception e
          (if (> retry 0)
            (Thread/sleep 1000)
            (send-result req-builder res (dec retry))))))))

(defn write-local-result
  [res]
  (spit "./logging/results.edn" (prn-str res) :append true))

(defn http-sender
  [uri chan db-ids continue?]
  (let [req-builder (request-builder uri)]
    (loop [results (asy/poll! chan)]
      (if (some? results)
        (loop [more-results (asy/poll! chan)
               all-results [(transform-opc-results results db-ids)]]
          (log/debug more-results)
          (if (some? more-results)
              (recur (asy/poll! chan)
                     (conj all-results (transform-opc-results more-results db-ids)))
              (send-result req-builder all-results)))
        (do (log/debug "Before sleeping")
            (Thread/sleep 100))) ; todo aus config
      (if @continue?
        (recur (asy/poll! chan))))))

(defn local-sender
  [chan db-ids continue?]
  (loop [results (asy/poll! chan)]
    (if (some? results)
      (loop [more-results (asy/poll! chan)
             all-results [(transform-opc-results results db-ids)]]
        (if (some? more-results)
            (recur (asy/poll! chan)
                   (conj all-results (transform-opc-results more-results db-ids)))
            (write-local-result all-results)))
      (Thread/sleep 100)) ; todo aus config
    (if @continue?
        (recur (asy/poll! chan)))))

(comment

(def continue (atom true))
(def opc2http (asy/chan (asy/sliding-buffer 10000)))
(def db-ids conf/datapoint-id-map)

(let [f (future (local-sender s/opc2http db-ids continue))]
  (do
    (Thread/sleep 10000)
    (compare-and-set! continue true false)
    @f
    (compare-and-set! continue false true)))
)

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

(ns mau.opcua-logger.poll-scheduler
  (:require [mau.opcua-logger.milo-client :as milo]
            [clojure.core.async :as asy]
            [clojure.tools.logging :as log]
            [mau.opcua-logger.configuration]))

(defn dp-min-ts-comp
  [x y]
  (let [c (compare (:next-poll x) (:next-poll y))]
    (if (= c 0) 
      (compare (:node x) (:node y))
      c)))

(defn update-dp
  [dp]
  (assoc dp :next-poll (+ (System/currentTimeMillis) (:poll-int dp))))

(defn update-queue
  [queue dps]
  (if (seq dps)
    (let [disj-queue (apply disj queue dps)]
     (reduce #(conj %1 (update-dp %2)) disj-queue dps))
    queue))

(defn scheduler
  [client-uri chan dp-vec continue?]
  (let [client (-> (milo/get-endpoint client-uri)
                   (milo/configure-opcua-client)
                   (milo/connect-client))]
    (log/info "Start scheduler with endpoint: " client-uri)
    (loop [queue (apply sorted-set-by dp-min-ts-comp dp-vec)
           db2read ()]
      (if (empty? db2read)
        (Thread/sleep 100) ; todo aus config
        (do
          (log/debug "OPC read")
          (asy/>!! chan (milo/read-values 
                             client 
                             (reduce #(conj %1 (:NodeId %2)) [] db2read)))
          ))
      (let [updated-queue (update-queue queue db2read)]
        (do 
          (log/debug "Updated queue: " updated-queue)
          (if @continue?
            (recur updated-queue 
                   (subseq updated-queue <= {:next-poll (System/currentTimeMillis)}))
            (do  (println "close opc") (milo/close-connection client))))))))

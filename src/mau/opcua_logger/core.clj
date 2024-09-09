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

(ns mau.opcua-logger.core
  (:require [mau.opcua-logger.configuration :as conf]
            [clojure.core.async :as asy]
            [clojure.tools.logging :as log]
            [mau.opcua-logger.poll-scheduler :as sch]
            [mau.opcua-logger.http-sender :as snd]))

(def continue-scheduler (atom true))
(def continue-sender (atom true))

; Shutdown hook fn
(defn shutdown
  []
  (compare-and-set! continue-scheduler true false)
  (compare-and-set! continue-sender true false))

(defn startup-write-local
  []
  (log/info "Start with local sender")
  (let [logger-config conf/logger-config
        opc-server-config conf/opc-server-config
        datapoint-config conf/datapoint-config
        opc2http (asy/chan (asy/sliding-buffer 10000))]
    ; Start threads
    (future (sch/scheduler (:opc-server-url opc-server-config)
                            opc2http
                            datapoint-config
                            continue-scheduler))
    (future (snd/local-sender opc2http
                              conf/datapoint-id-map
                              continue-sender))))

(defn startup-http
  []
  (log/info "Start with http sender")
  (let [logger-config conf/logger-config
        opc-server-config conf/opc-server-config
        datapoint-config conf/datapoint-config
        opc2http (asy/chan (asy/sliding-buffer 10000))]
    ; Start threads
    (future (sch/scheduler (:opc-server-url opc-server-config)
                            opc2http
                            datapoint-config
                            continue-scheduler))
    (future (snd/http-sender (:db-server-url logger-config)
                              opc2http
                              conf/datapoint-id-map
                              continue-sender))))

(defn -main
  [& args]
  (log/info "Starting ...")
  (cond
    (nil? (:write-local conf/logger-config)) (startup-http)
    (= (:write-local conf/logger-config) false) (startup-http)
    (= (:write-local conf/logger-config) true) (startup-write-local)
    :else (System/exit 0))

  ; Add shutdown hook
  (.addShutdownHook (Runtime/getRuntime) 
                    (Thread. ^Runnable shutdown))

  ; Spin main thread forever
  (while true
    (Thread/sleep 10000)))


(comment
(let [f (future (do (startup-write-local)
                    (while @continue-sender
                      (Thread/sleep 2000))))]
  (Thread/sleep 200000)
  (compare-and-set! continue-scheduler true false)
  (Thread/sleep 10000)
  (compare-and-set! continue-sender true false)
  (Thread/sleep 2000)
  (compare-and-set! continue-scheduler false true)
  (compare-and-set! continue-sender false true))

(def c (asy/chan (asy/sliding-buffer 10000)))
)

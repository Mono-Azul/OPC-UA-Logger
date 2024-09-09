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

(ns mau.opcua-logger.configuration
  (:require [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [mau.opcua-logger.milo-client :as milo]))

(def logger-config-file "./configuration/logger-configuration.edn")
(def opc-server-config-file "./configuration/opcua-server-configuration.edn")
(def datapoint-config-file  "./configuration/datapoint-configuration.csv")

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data)
            (map keyword)
            repeat)
	  (rest csv-data)))

(defn format-dp-config-csv
  [raw-list]
  (->> raw-list
    (mapv #(conj % [:next-poll (System/currentTimeMillis)]
                   [:poll-int (Long/parseLong (:poll-int %))]
                   [:NodeId (milo/build-nodeid (:node %))]))
    (mapv #(update % :dtype keyword))))

(defn read-csv
  [file]
  (with-open [reader (io/reader file)]
    (doall
      (csv-data->maps (csv/read-csv reader)))))

(defn get-edn-config
  [file]
  (edn/read-string (slurp (io/reader file))))

; Load at startup and throw if not found
(def logger-config
  (try
    (get-edn-config logger-config-file)
    (catch Exception e
      (log/error "Logger configuration not found! " e)
      (throw e)
      )))

(def opc-server-config
  (try
    (get-edn-config opc-server-config-file)
    (catch Exception e
      (log/error "OPC server configuration not found! " e)
      (throw e)
      )))

(def datapoint-config
  (try
    (format-dp-config-csv (read-csv datapoint-config-file))
    (catch Exception e
      (log/error "Datapoint configuration not found! " e)
      (throw e)
      )))

(def datapoint-id-map
  (reduce #(assoc %1 (:node %2) (Integer/parseInt (:id %2))) {} datapoint-config))


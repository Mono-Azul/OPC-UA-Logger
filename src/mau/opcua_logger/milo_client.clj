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

(ns mau.opcua-logger.milo-client
  (:import [org.eclipse.milo.opcua.stack.core.types.enumerated TimestampsToReturn MonitoringMode]
           [org.eclipse.milo.opcua.stack.core.types.builtin Variant QualifiedName NodeId DataValue LocalizedText]
           [org.eclipse.milo.opcua.stack.core.types.structured ReadValueId MonitoringParameters MonitoredItemCreateRequest]
           [org.eclipse.milo.opcua.stack.core StatusCodes AttributeId Stack]
           [org.eclipse.milo.opcua.stack.core.security SecurityPolicy]
           [org.eclipse.milo.opcua.stack.client DiscoveryClient]
           [org.eclipse.milo.opcua.sdk.client OpcUaClient]
           [org.eclipse.milo.opcua.sdk.core.nodes VariableNode]
           [org.eclipse.milo.opcua.sdk.client.api.config OpcUaClientConfig]
           [org.eclipse.milo.opcua.sdk.client.api.identity AnonymousProvider]
           [org.eclipse.milo.opcua.stack.core.types.builtin.unsigned Unsigned UByte]
           [com.google.common.collect Lists]
           [java.util Arrays]))


(set! *warn-on-reflection* true)

(defn get-endpoint
  [uri]
  (first
    (filter #(= (SecurityPolicy/None)
                (SecurityPolicy/fromUri (.getSecurityPolicyUri %)))
      (-> (DiscoveryClient/getEndpoints uri)
        (.get)
        ))))

(defn configure-opcua-client
  ([endpoint]
   (let [random (rand-int 99999)]  
   (configure-opcua-client endpoint
			   (str "Random client: " random)
			   (str "uri:random:" random)
			   5000)))
  ([endpoint app-name app-uri timeout]
  (-> (OpcUaClientConfig/builder)
    (.setApplicationName (LocalizedText/english app-name))
    (.setApplicationUri app-uri)
    (.setEndpoint endpoint)
    (.setIdentityProvider (AnonymousProvider.))
    (.setRequestTimeout (Unsigned/uint timeout))
    (.build)
    (OpcUaClient/create))))

(defn connect-client
  [client]
  (-> (.connect client)
      (.get)))

(defn close-connection
  [client]
  (-> (.disconnect client)
      (.get))
  (Stack/releaseSharedResources))

(defn build-nodeid
  ([^String node-string]
   (NodeId/parse node-string))
  ([^Integer node-ns ^String node-ident]
   (NodeId. node-ns node-ident)))

(defn build-value-map
  [^DataValue data-value ^NodeId node]
  (hash-map :node (.toParseableString node)
            :value (-> (.getValue data-value)
                       (.getValue))
            :timestamp (-> (.getSourceTime data-value)
                           (.getJavaTime)) 
            :status (-> (.getStatusCode data-value)
                        (.getValue))
            :status-string (-> (.getStatusCode data-value)
                               (.getValue)
                               (StatusCodes/lookup)
                               (.get)
                               (aget 0))))

(defn read-value
  ([^OpcUaClient client ^NodeId node]
   (read-value client node 500.0))
  ([^OpcUaClient client ^NodeId node max-age]
   (-> (.readValue client max-age (TimestampsToReturn/Both) node)
       (.get)
       (build-value-map node))))

(defn read-values
  ([^OpcUaClient client node-list]
   (read-values client node-list 500.0))
  ([^OpcUaClient client node-list max-age]
   (map build-value-map
     (-> (.readValues client max-age (TimestampsToReturn/Both) node-list)
         (.get))
     node-list)))

(defn write-value
  [^OpcUaClient client ^NodeId node value]
  (-> (.writeValue client node 
        (DataValue. (Variant. value)))
      (.get)
      (.getValue)
      (StatusCodes/lookup)
      (.get)
      (aget 0)))


(defn build-node-map
  [^VariableNode var-node ^NodeId node]
  (hash-map :node (.toParseableString node)
            :access-level (-> (.getAccessLevel var-node)
                             (.intValue))
            ; :array-dimension (-> (.getArrayDimensions var-node)
            ;                      (.get))
            :browse-name (-> (.getBrowseName var-node)
                             (.getName))
            :data-type (-> (.getDataType var-node)
                           (.toString))
            :description (-> (.getDescription var-node)
                             (.getText))
            :display-name (-> (.getDisplayName var-node)
                              (.getText))
            :minimum-sampling-intervall (-> (.getMinimumSamplingInterval var-node))
            :user-access-level (-> (.getUserAccessLevel var-node)
                                   (.intValue))
            :user-write-mask (-> (.getUserWriteMask var-node)
                                  (.longValue))
            :value-rank (-> (.getValueRank var-node)
                            (.longValue))
            :write-mask (-> (.getWriteMask var-node)
                            (.longValue))
            ))


(defn read-node
  [^OpcUaClient client ^NodeId node]
  (-> (.getAddressSpace client)
      (.getVariableNode node)
      (build-node-map node)))


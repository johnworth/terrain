(ns terrain.services.metadata.apps
  (:use [clojure.java.io :only [reader]]
        [clojure-commons.client :only [build-url-with-query]]
        [terrain.util.config]
        [terrain.util.transformers :only [secured-params]]
        [terrain.util.service])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure.string :as string]
            [terrain.clients.iplant-groups :as ipg]
            [terrain.clients.apps :as dm]
            [terrain.clients.apps.raw :as apps-client]
            [terrain.clients.notifications :as dn]
            [terrain.util.email :as email]))

(defn- apps-request
  "Prepares a apps request by extracting only the body of the client request and sets the
   forwarded request's content-type to json."
  [req]
  (assoc (select-keys req [:body]) :content-type :json))

(defn- apps-url
  "Adds the name and email of the currently authenticated user to the apps URL with the given
   relative URL path."
  [query & components]
  (apply build-url-with-query (apps-base-url)
                              (secured-params query)
                              components))

(defn import-tools
  "This service will import deployed components into the DE and send
   notifications if notification information is included and the deployed
   components are successfully imported."
  [json]
  (let [response (apps-client/admin-add-tools json)]
    (dorun (map dn/send-tool-notification (:tools json)))
    response))

(defn- postprocess-tool-request
  "Postprocesses a tool request update."
  [res]
  (if (<= 200 (:status res) 299)
    (let [tool-req (cheshire/decode-stream (reader (:body res)) true)]
      (success-response tool-req))
    res))

(defn submit-tool-request
  "Submits a tool request on behalf of the user found in the request params."
  [body]
  (let [tool-req     (apps-client/submit-tool-request body)
        username     (string/replace (:submitted_by tool-req) #"@.*" "")
        user-details (ipg/format-like-trellis (ipg/lookup-subject-add-empty username username))]
    (email/send-tool-request-email tool-req user-details)
    tool-req))

(defn admin-list-tool-requests
  "Lists the tool requests that were submitted by any user."
  [params]
  (success-response (dm/admin-list-tool-requests params)))

(defn update-tool-request
  "Updates a tool request with comments and possibly a new status."
  [req request-id]
  (let [url (apps-url {} "admin" "tool-requests" request-id "status")
        req (apps-request req)]
    (postprocess-tool-request (forward-post url req))))

(defn get-tool-request
  "Lists details about a specific tool request."
  [request-id]
  (client/get (apps-url {} "admin" "tool-requests" request-id)
              {:as :stream}))

(defn send-support-email
  "Sends a support email from the user."
  [body]
  (email/send-support-email (cheshire/decode-stream (reader body)))
  (success-response))

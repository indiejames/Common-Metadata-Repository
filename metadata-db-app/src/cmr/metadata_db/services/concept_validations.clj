(ns cmr.metadata-db.services.concept-validations
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.concepts :as cc]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]))

(defn concept-type-missing-validation
  [concept]
  (when-not (:concept-type concept)
    [(msg/missing-concept-type)]))

(defn provider-id-missing-validation
  [concept]
  (when-not (:provider-id concept)
    [(msg/missing-provider-id)]))

(defn native-id-missing-validation
  [concept]
  (when-not (:native-id concept)
    [(msg/missing-native-id)]))

(def concept-type->required-extra-fields
  "A map of concept type to the required extra fields"
  {:collection #{:short-name :version-id :entry-id :entry-title}
   :granule #{:parent-collection-id :granule-ur}})

(defn extra-fields-missing-validation
  "Validates that the concept is provided with extra fields and that all of them are present and not nil."
  [concept]
  (if-let [extra-fields (:extra-fields concept)]
    (map #(msg/missing-extra-field %)
         (set/difference (concept-type->required-extra-fields (:concept-type concept))
                         (set (keys extra-fields))))
    [(msg/missing-extra-fields)]))

(defn nil-fields-validation
  "Validates that none of the fields are nil."
  [concept]
  (reduce-kv (fn [acc field value]
               (if (nil? value)
                 (conj acc (msg/nil-field field))
                 acc))
             []
             (dissoc concept :revision-date :revision-id)))

(defn datetime-validator
  [field-path]
  (fn [concept]
    (when-let [value (get-in concept field-path)]
      (try
        (p/parse-datetime value)
        nil
        (catch clojure.lang.ExceptionInfo e
          (let [data (ex-data e)]
            (:errors data)))))))

(defn nil-extra-fields-validation
  "Validates that among the extra fields, only delete-time and version-id can sometimes be nil."
  [concept]
  (nil-fields-validation (apply dissoc (:extra-fields concept) [:delete-time :version-id])))

(defn concept-id-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (cc/concept-id-validation concept-id)))

(defn concept-id-match-fields-validation
  [concept]
  (when-let [concept-id (:concept-id concept)]
    (when-not (cc/concept-id-validation concept-id)
      (let [{:keys [concept-type provider-id]} (cc/parse-concept-id concept-id)]
        (when-not (and (= concept-type (:concept-type concept))
                       (= provider-id (:provider-id concept)))
          [(msg/invalid-concept-id concept-id (:provider-id concept) (:concept-type concept))])))))


(def concept-validation
  "Validates a concept and returns a list of errors"
  (util/compose-validations [concept-type-missing-validation
                             provider-id-missing-validation
                             native-id-missing-validation
                             concept-id-validation
                             extra-fields-missing-validation
                             nil-fields-validation
                             nil-extra-fields-validation
                             concept-id-match-fields-validation
                             (datetime-validator [:revision-date])
                             (datetime-validator [:extra-fields :delete-time])]))

(def validate-concept
  "Validates a concept. Throws an error if invalid."
  (util/build-validator :invalid-data concept-validation))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations for concept find

(def supported-collection-parameters
  "Set of parameters supported by find for collections"
  #{:provider-id :entry-title :entry-id :short-name :version-id})

(def granule-supported-parameter-combinations
  "Supported parameter combination sets for granule find"
  #{#{:provider-id :granule-ur}
    #{:provider-id :native-id}})

(defmulti supported-parameter-combinations-validation
  "Validates the find parameters for a concept type"
  (fn [params]
    (:concept-type params)))

(defmethod supported-parameter-combinations-validation :collection
  [params]
  (let [params (dissoc params :concept-type)
        diff (clojure.set/difference (set (keys params)) supported-collection-parameters)]
    (when (not-empty diff)
      [(msg/find-not-supported :collection diff)])))

(defmethod supported-parameter-combinations-validation :granule
  [{:keys [concept-type] :as params}]
  (let [params (dissoc params :concept-type)]
    (when-not (contains? granule-supported-parameter-combinations (set (keys params)))
      [(msg/find-not-supported-combination concept-type (keys params))])))

(defmethod supported-parameter-combinations-validation :default
  [{:keys [concept-type] :as params}]
  [(msg/find-not-supported-combination concept-type (keys (dissoc params :concept-type)))])

(def find-params-validation
  "Validates parameters for finding a concept"
  (util/compose-validations [supported-parameter-combinations-validation]))

(def validate-find-params
  "Validates find parameters. Throws an eror if invalid."
  (util/build-validator :bad-request find-params-validation))

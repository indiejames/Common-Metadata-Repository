(ns cmr.ingest.validation.platform-validation
  "Provides functions to validate the platforms during collection update"
  (:require
   [clojure.set :as s]
   [clojure.string :as str]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]))

(defn deleted-platform-searches
  "Returns granule searches for deleted platforms. We should not delete platforms in a collection
  that are still referenced by existing granules. This function builds the search parameters
  for identifying such invalid deletions."
  [context concept-id concept prev-concept]
  (let [platform-alias-map (get (humanizer-alias-cache/get-humanizer-alias-map context) "platform")
        current-platforms (map :ShortName (:Platforms concept))
        previous-platforms (map :ShortName (:Platforms prev-concept))
        platform-aliases (mapcat #(get platform-alias-map %) (map str/upper-case current-platforms))
        deleted-non-alias-platform-names (s/difference
                                           (set previous-platforms)
                                           (set (concat current-platforms platform-aliases)))]
    (for [name deleted-non-alias-platform-names]
      {:params {"platform[]" name
                :collection-concept-id concept-id}
       :error-msg (format (str "Collection Platform [%s] is referenced by existing"
                               " granules, cannot be removed.") name)})))

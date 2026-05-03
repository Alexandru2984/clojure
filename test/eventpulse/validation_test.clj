(ns eventpulse.validation-test
  (:require [clojure.test :refer [deftest is testing]]
            [eventpulse.validation :as validation]))

(deftest event-validation
  (testing "valid event"
    (is (:valid? (validation/validate-event {:source "weather-sim"
                                             :level "warning"
                                             :type "high_cpu"
                                             :message "Simulation tick delayed"
                                             :metadata {:cpu 91}}))))
  (testing "missing required field"
    (is (false? (:valid? (validation/validate-event {:source "weather-sim"
                                                     :level "warning"
                                                     :type "high_cpu"})))))
  (testing "invalid level"
    (is (false? (:valid? (validation/validate-event {:source "weather-sim"
                                                     :level "warn"
                                                     :type "high_cpu"
                                                     :message "bad"})))))
  (testing "unknown field"
    (is (false? (:valid? (validation/validate-event {:source "weather-sim"
                                                     :level "warning"
                                                     :type "high_cpu"
                                                     :message "bad"
                                                     :extra true}))))))

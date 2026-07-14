(ns bakeryops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [bakeryops.registry :as registry]))

;; ──────────────────────── Baking Temperature Safety ──────────────────────

(deftest baking-temp-out-of-range-test
  (testing "temperature within range returns false (no violation)"
    (is (false? (registry/baking-temp-out-of-range? 200 190 210))))

  (testing "temperature at minimum boundary returns false"
    (is (false? (registry/baking-temp-out-of-range? 190 190 210))))

  (testing "temperature at maximum boundary returns false"
    (is (false? (registry/baking-temp-out-of-range? 210 190 210))))

  (testing "temperature below minimum returns true (violation)"
    (is (true? (registry/baking-temp-out-of-range? 189 190 210))))

  (testing "temperature above maximum returns true (violation)"
    (is (true? (registry/baking-temp-out-of-range? 211 190 210)))))

;; ──────────────────────── Baking Time Safety ──────────────────────

(deftest baking-time-exceeded-test
  (testing "time within limit returns false (no violation)"
    (is (false? (registry/baking-time-exceeded? 40 50))))

  (testing "time at limit returns false"
    (is (false? (registry/baking-time-exceeded? 50 50))))

  (testing "time exceeding limit returns true (violation)"
    (is (true? (registry/baking-time-exceeded? 51 50)))))

;; ──────────────────────── Moisture Target ──────────────────────

(deftest moisture-out-of-target-test
  (testing "moisture at target with no tolerance returns false"
    (is (false? (registry/moisture-out-of-target? 38 38 2))))

  (testing "moisture within tolerance range returns false"
    (is (false? (registry/moisture-out-of-target? 37 38 2))))

  (testing "moisture below tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 35 38 2))))

  (testing "moisture above tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 41 38 2)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))

;; ──────────────────────── Scale Calibration ──────────────────────

(deftest scale-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Assume calibrated 30 days ago
    #?(:clj
       (let [now (System/currentTimeMillis)
             30-days-ago (- now (* 30 24 60 60 1000))]
         (is (false? (registry/scale-calibration-overdue? 30-days-ago now))))
       :cljs
       (let [now (.now js/Date)
             30-days-ago (- now (* 30 24 60 60 1000))]
         (is (false? (registry/scale-calibration-overdue? 30-days-ago now)))))))

  (testing "overdue calibration returns true (violation)"
    #?(:clj
       (let [now (System/currentTimeMillis)
             190-days-ago (- now (* 190 24 60 60 1000))]
         (is (true? (registry/scale-calibration-overdue? 190-days-ago now))))
       :cljs
       (let [now (.now js/Date)
             190-days-ago (- now (* 190 24 60 60 1000))]
         (is (true? (registry/scale-calibration-overdue? 190-days-ago now)))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 45 50))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 50 50))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 51 50)))))

;; ──────────────────────── Allergen Labeling ──────────────────────

(deftest allergen-label-risk-test
  (testing "declared allergens match formulation returns false (no risk)"
    (let [formula #{:wheat :milk}
          declared #{:wheat :milk}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "declared allergens exceed formulation returns false (conservative)"
    (let [formula #{:wheat}
          declared #{:wheat :milk}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "formulation allergen undeclared returns true (risk)"
    (let [formula #{:wheat :milk}
          declared #{:wheat}]
      (is (true? (registry/allergen-label-risk? formula declared)))))))

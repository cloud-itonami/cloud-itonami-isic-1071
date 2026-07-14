(ns bakeryops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [bakeryops.governor :as governor]
            [bakeryops.facts :as facts]))

;; ──────────────────────── Hard Violations ──────────────────────

(deftest spec-basis-violation-test
  (testing "proposal with no jurisdiction citation is a hard violation"
    (let [req {:op :log-production-batch :subject "batch-001"}
          prop {:cites [] :value {:jurisdiction nil}}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :no-spec-basis) (:violations result)))))

  (testing "proposal with proper citation passes spec basis check"
    (let [req {:op :log-production-batch :subject "batch-001"}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural}}
          result (governor/check req {:actor-id "gov-1"} prop {})]
      (is (false? (:hard? result))))))

;; ──────────────────────── Baking Temperature Violations ──────────────────────

(deftest baking-temp-violation-test
  (testing "batch with baking temp out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 180
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :baking-temp-out-of-range) (:violations result)))))

  (testing "batch with baking temp in range passes"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 200
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:hard? result))))))

;; ──────────────────────── Moisture Violations ──────────────────────

(deftest moisture-violation-test
  (testing "batch with moisture out of range triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 200
                            :baking-time-minutes 35
                            :moisture-percent 45
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :moisture-out-of-target) (:violations result))))))

;; ──────────────────────── Allergen Labeling Violations ──────────────────────

(deftest allergen-label-violation-test
  (testing "batch with undeclared allergens triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 200
                            :baking-time-minutes 35
                            :moisture-percent 38
                            :ingredients [:flour/wheat :eggs/large :butter/clarified]
                            :declared-allergens #{:wheat}
                            :sanitation-score 85
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :allergen-label-mismatch) (:violations result))))))

;; ──────────────────────── Escalation (Low Confidence) ──────────────────────

(deftest low-confidence-escalation-test
  (testing "low confidence proposal escalates even when hard checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 200
                            :baking-time-minutes 35
                            :moisture-percent 38
                            :ingredients [:flour/wheat]
                            :declared-allergens #{:wheat}
                            :sanitation-score 85
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :schedule-maintenance :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.5}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── High Stakes Escalation ──────────────────────

(deftest high-stakes-escalation-test
  (testing "log-production-batch escalates even when all checks pass"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :baking-temp-c 200
                            :baking-time-minutes 35
                            :moisture-percent 38
                            :ingredients [:flour/wheat]
                            :declared-allergens #{:wheat}
                            :sanitation-score 85
                            :evidence-checklist [:formulation-record :baking-log :temperature-log
                                                 :moisture-test :allergen-declaration :weight-check]
                            :jurisdiction :jp/prefectural}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.95}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (false? (:ok? result)))
      (is (true? (:escalate? result)))
      (is (false? (:hard? result))))))

;; ──────────────────────── Already Processed Violation ──────────────────────

(deftest already-processed-violation-test
  (testing "batch already processed triggers hard violation"
    (let [batch-id "batch-001"
          store {:batches {batch-id
                           {:product-type :bread/white-loaf
                            :processed? true}}}
          req {:op :log-production-batch :subject batch-id}
          prop {:cites [{:spec "ISO-12345"}] :value {:jurisdiction :jp/prefectural} :confidence 0.8}
          result (governor/check req {:actor-id "gov-1"} prop store)]
      (is (true? (:hard? result)))
      (is (some #(= (:rule %) :already-processed) (:violations result))))))

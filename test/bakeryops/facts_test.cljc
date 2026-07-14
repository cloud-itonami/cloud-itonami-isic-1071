(ns bakeryops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [bakeryops.facts :as facts]))

;; ──────────────────────── Product Type Lookups ──────────────────────

(deftest product-type-by-id-test
  (testing "white loaf product type exists"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (some? p))
      (is (= (:id p) :bread/white-loaf))
      (is (= (:baking-temp-c-min p) 190))
      (is (= (:baking-temp-c-max p) 210))))

  (testing "croissant product type exists"
    (let [p (facts/product-type-by-id :pastry/croissant)]
      (is (some? p))
      (is (= (:baking-temp-c-min p) 200))
      (is (= (:baking-temp-c-max p) 220))))

  (testing "nonexistent product type returns nil"
    (is (nil? (facts/product-type-by-id :bread/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP prefectural jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)]
      (is (some? j))
      (is (true? (:allergen-declaration-required j)))
      (is (contains? (:major-allergens j) :wheat))))

  (testing "US FDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/fda)]
      (is (some? j))
      (is (contains? (:major-allergens j) :sesame))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Allergen Lookups ──────────────────────

(deftest ingredient-allergens-test
  (testing "wheat flour has wheat allergen"
    (let [a (facts/ingredient-allergens :flour/wheat)]
      (is (= (:primary-allergen a) :wheat))
      (is (contains? (:cross-contact-risk a) :tree-nuts))))

  (testing "eggs have eggs allergen"
    (let [a (facts/ingredient-allergens :eggs/large)]
      (is (= (:primary-allergen a) :eggs))))

  (testing "nonexistent ingredient returns nil"
    (is (nil? (facts/ingredient-allergens :unknown/ingredient)))))

;; ──────────────────────── Baking Safety Predicates ──────────────────────

(deftest baking-temp-in-range-test
  (testing "temp within range passes"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (true? (facts/baking-temp-in-range? 200 p)))))

  (testing "temp below minimum fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/baking-temp-in-range? 180 p)))))

  (testing "temp above maximum fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/baking-temp-in-range? 220 p))))))

(deftest baking-time-in-range-test
  (testing "time within range passes"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (true? (facts/baking-time-in-range? 38 p)))))

  (testing "time below minimum fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/baking-time-in-range? 25 p)))))

  (testing "time above maximum fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/baking-time-in-range? 50 p))))))

(deftest moisture-in-range-test
  (testing "moisture within tolerance passes"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (true? (facts/moisture-in-range? 38 p)))))

  (testing "moisture at lower tolerance boundary passes"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (true? (facts/moisture-in-range? 36 p)))))

  (testing "moisture below range fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/moisture-in-range? 35 p)))))

  (testing "moisture above range fails"
    (let [p (facts/product-type-by-id :bread/white-loaf)]
      (is (false? (facts/moisture-in-range? 41 p))))))

;; ──────────────────────── Allergen Traceability ──────────────────────

(deftest formulation-allergen-set-test
  (testing "white bread formulation collects wheat allergen"
    (let [ingredients [:flour/wheat :salt/sea :yeast/active-dry]
          allergens (facts/formulation-allergen-set ingredients)]
      (is (contains? allergens :wheat))))

  (testing "croissant formulation includes multiple allergens"
    (let [ingredients [:flour/wheat :butter/clarified :eggs/large]
          allergens (facts/formulation-allergen-set ingredients)]
      (is (contains? allergens :wheat))
      (is (contains? allergens :milk))
      (is (contains? allergens :eggs))))

  (testing "allergen-free ingredients produce empty set"
    (let [ingredients [:salt/sea :sugar/white]
          allergens (facts/formulation-allergen-set ingredients)]
      (is (empty? allergens)))))

(deftest allergen-declaration-complete-test
  (testing "declaration matches formulation for jurisdiction"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          ingredients [:flour/wheat]
          declared #{:wheat}]
      (is (true? (facts/allergen-declaration-complete? j ingredients declared)))))

  (testing "incomplete declaration fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          ingredients [:flour/wheat :eggs/large]
          declared #{:wheat}]
      (is (false? (facts/allergen-declaration-complete? j ingredients declared)))))

  (testing "extra declarations pass (conservative)"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          ingredients [:flour/wheat]
          declared #{:wheat :milk}]
      (is (true? (facts/allergen-declaration-complete? j ingredients declared))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:formulation-record :baking-log :temperature-log
                    :moisture-test :allergen-declaration :weight-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:formulation-record :baking-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))

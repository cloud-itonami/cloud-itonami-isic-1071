(ns bakeryops.facts
  "Reference facts for bakery-products manufacturing: product-type baking
  parameters (temperature/time/moisture windows), jurisdiction allergen-
  declaration and evidence-checklist requirements, and per-ingredient
  allergen data. This namespace contains pure lookup functions for
  regulatory/food-safety compliance checks -- the Governor calls these to
  independently validate proposals; the advisor's confidence is never
  sufficient on its own."
  (:require [clojure.set :as set]))

(def product-types
  "Valid bakery product categories and their safe baking windows."
  {:bread/white-loaf
   {:id :bread/white-loaf
    :name "食パン"
    :baking-temp-c-min 190
    :baking-temp-c-max 210
    :baking-time-min-minutes 30
    :baking-time-max-minutes 45
    :moisture-target-percent 38
    :moisture-tolerance-percent 2}

   :bread/whole-wheat
   {:id :bread/whole-wheat
    :name "全粒粉パン"
    :baking-temp-c-min 195
    :baking-temp-c-max 215
    :baking-time-min-minutes 32
    :baking-time-max-minutes 48
    :moisture-target-percent 36
    :moisture-tolerance-percent 2}

   :pastry/croissant
   {:id :pastry/croissant
    :name "クロワッサン"
    :baking-temp-c-min 200
    :baking-temp-c-max 220
    :baking-time-min-minutes 16
    :baking-time-max-minutes 22
    :moisture-target-percent 22
    :moisture-tolerance-percent 3}

   :cake/sponge
   {:id :cake/sponge
    :name "スポンジケーキ"
    :baking-temp-c-min 165
    :baking-temp-c-max 180
    :baking-time-min-minutes 25
    :baking-time-max-minutes 35
    :moisture-target-percent 28
    :moisture-tolerance-percent 3}})

(defn product-type-by-id [id]
  (get product-types id))

(def jurisdictions
  "Bakery-products jurisdictions and their allergen-declaration and
  evidence-checklist requirements."
  {:jp/prefectural
   {:id :jp/prefectural
    :name "日本 (食品表示法・都道府県)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :eggs :milk :peanuts :tree-nuts :sesame :soy}
    :required-evidence
    [:formulation-record
     :baking-log
     :temperature-log
     :moisture-test
     :allergen-declaration
     :weight-check]}

   :us/fda
   {:id :us/fda
    :name "United States (FDA/FALCPA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :eggs :milk :peanuts :tree-nuts :sesame :soy}
    :required-evidence
    [:formulation-record
     :baking-log
     :temperature-log
     :moisture-test
     :allergen-declaration
     :weight-check]}

   :eu/efsa
   {:id :eu/efsa
    :name "European Union (EFSA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :eggs :milk :peanuts :tree-nuts :sesame :soy :celery :mustard}
    :required-evidence
    [:formulation-record
     :baking-log
     :temperature-log
     :moisture-test
     :allergen-declaration
     :weight-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(def ingredient-allergen-table
  "Per-ingredient primary allergen and cross-contact risk, used to derive a
  formulation's allergen set for label-accuracy verification. Ingredients
  with no allergen relevance map to nil."
  {:flour/wheat      {:primary-allergen :wheat :cross-contact-risk #{:tree-nuts :soy}}
   :flour/rye        {:primary-allergen :wheat :cross-contact-risk #{:tree-nuts}}
   :eggs/large       {:primary-allergen :eggs :cross-contact-risk #{}}
   :butter/clarified {:primary-allergen :milk :cross-contact-risk #{}}
   :milk/whole       {:primary-allergen :milk :cross-contact-risk #{}}
   :peanut/paste     {:primary-allergen :peanuts :cross-contact-risk #{:tree-nuts}}
   :almond/meal      {:primary-allergen :tree-nuts :cross-contact-risk #{:peanuts}}
   :sesame/seed      {:primary-allergen :sesame :cross-contact-risk #{}}
   :soy/lecithin     {:primary-allergen :soy :cross-contact-risk #{}}
   :salt/sea         nil
   :sugar/white      nil
   :yeast/active-dry nil
   :water/filtered   nil})

(defn ingredient-allergens [id]
  (get ingredient-allergen-table id))

(defn formulation-allergen-set
  "Given a formulation's ingredient-id list, return the set of primary
  allergens actually present. Non-allergenic / unknown ingredient ids
  contribute nothing."
  [ingredients]
  (into #{}
        (keep (fn [id] (:primary-allergen (ingredient-allergens id))))
        ingredients))

(defn allergen-declaration-complete?
  "Verify that `declared` allergens are a superset of the formulation's
  actual allergens for `ingredients`. Extra (conservative) declarations
  pass; omissions fail. `jurisdiction` is accepted for call-site symmetry
  with other facts lookups."
  [_jurisdiction ingredients declared]
  (set/subset? (formulation-allergen-set ingredients) (set declared)))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list is
  present in `evidence`. `jurisdiction` may be a resolved jurisdiction map
  (as returned by `jurisdiction-by-id`) or a raw jurisdiction id -- both
  call conventions are in use (tests pass a resolved map; the Governor
  passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn baking-temp-in-range?
  "Positive-sense convenience predicate: does `temp-c` fall within
  `product`'s safe baking window (inclusive)?"
  [temp-c product]
  (boolean
   (and (some? product)
        (>= temp-c (:baking-temp-c-min product))
        (<= temp-c (:baking-temp-c-max product)))))

(defn baking-time-in-range?
  "Positive-sense convenience predicate: does `minutes` fall within
  `product`'s expected baking-time window (inclusive)?"
  [minutes product]
  (boolean
   (and (some? product)
        (>= minutes (:baking-time-min-minutes product))
        (<= minutes (:baking-time-max-minutes product)))))

(defn moisture-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s moisture tolerance window (inclusive) around its target?"
  [percent product]
  (boolean
   (and (some? product)
        (let [target (:moisture-target-percent product)
              tol (:moisture-tolerance-percent product)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(ns bakeryops.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `cerealops.store-contract-test` (cloud-itonami-isic-0111)."
  (:require [clojure.test :refer [deftest is]]
            [bakeryops.store :as store]))

(def ^:private batch-data
  {:product-type :bread/white-loaf
   :jurisdiction :jp/prefectural
   :baking-temp-c 200
   :baking-time-minutes 35
   :moisture-percent 38})

(defn- exercise [s]
  (store/register-batch! s "batch-x" batch-data)
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:batch/id is :db.unique/identity) the same way
  ;; MemStore's plain `assoc` re-registration does.
  (store/register-batch! s "batch-x" (assoc batch-data :baking-temp-c 205))
  (store/mark-processed! s "batch-x")
  (store/append-ledger! s {:t :committed :op :log-production-batch :subject "batch-x"})
  (store/append-ledger! s {:t :approval-requested :op :coordinate-shipment :subject "batch-x"})
  {:batch  (store/production-batch (store/snapshot s) "batch-x")
   :absent (store/production-batch (store/snapshot s) "no-such-batch")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:batch m) (:batch d)))
    (is (= 205 (:baking-temp-c (:batch m))) "re-registration upserts, not forks history")
    (is (true? (:processed? (:batch m))) "mark-processed! flips the flag, preserving other fields")
    (is (true? (:processed? (:batch d))))
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))

(deftest datomic-store-seeded-batches
  (let [dat (store/datomic-store {:initial-batches
                                   {"batch-y" {:product-type :cake/sponge}}})]
    (is (= {:product-type :cake/sponge}
           (store/production-batch (store/snapshot dat) "batch-y")))
    (is (empty? (store/ledger dat)))))

(deftest mark-shipment-finalized-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)]
    (store/register-batch! mem "batch-z" batch-data)
    (store/register-batch! dat "batch-z" batch-data)
    (store/mark-shipment-finalized! mem "batch-z")
    (store/mark-shipment-finalized! dat "batch-z")
    (is (true? (:shipment-finalized? (store/production-batch (store/snapshot mem) "batch-z"))))
    (is (true? (:shipment-finalized? (store/production-batch (store/snapshot dat) "batch-z"))))
    (is (= 200 (:baking-temp-c (store/production-batch (store/snapshot dat) "batch-z")))
        "mark-shipment-finalized! preserves other fields")))

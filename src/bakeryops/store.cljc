(ns bakeryops.store
  "Store abstraction for bakery-products production batches.

  Two layers:

    1. PURE VALUE HELPERS (`production-batch` / `batch-already-processed?`
       / `batch-shipment-finalized?` / `log-batch` / `finalize-shipment` /
       `stage-batch` / `mark-processed` / `audit-trail` / `append-fact`) --
       plain functions over an immutable `{:batches {batch-id batch-map}}`
       value. `bakeryops.governor`'s independent checks call these
       directly against whatever snapshot they're handed (a raw test
       fixture map, or `(snapshot store)` below) -- this is this actor's
       original seam and stays unchanged so the Governor never has to
       care whether it's looking at a plain map or a live `Store`.

    2. `Store` PROTOCOL -- the backend seam every other cloud-itonami
       actor in this fleet uses (mirrors `cerealops.store`,
       cloud-itonami-isic-0111): `MemStore` (atom, deterministic default
       for dev/tests/demo) and `DatomicStore` (`langchain.db`-backed,
       Datomic-API-compatible EAV store; pure `.cljc`, swappable to a real
       Datomic Local or kotoba-server pod via `langchain.db`'s `:db-api`).
       Both hold the SAME `{:batches {...}}` shape the pure helpers above
       expect (`snapshot` returns it) plus an append-only audit ledger
       (`ledger`/`append-ledger!`) -- this actor's core missing plumbing
       until now. `bakeryops.operation`'s `:commit` graph node appends
       every committed/held/approval-rejected decision fact here, so a
       batch's full operating history is always a query over an immutable
       log. Both backends pass the same contract
       (test/bakeryops/store_contract_test.cljc).

  A production batch is the minimal unit of work: one mix/bake run of a
  bakery product, tracked from formulation through baking, inspection, and
  shipment. Representative batch keys:
    - :product-type keyword product id (see `bakeryops.facts/product-types`)
    - :jurisdiction keyword jurisdiction id (see `bakeryops.facts/jurisdictions`)
    - :baking-temp-c / :baking-time-minutes / :moisture-percent actuals
    - :sanitation-score 0-100 plant hygiene score
    - :scale-last-calibration-date epoch-ms of last mixing-scale calibration
    - :weight-variance-grams finished-product weight drift from target
    - :ingredients formulation ingredient ids
    - :declared-allergens set of declared allergen keywords
    - :evidence-checklist evidence items present for the batch
    - :safety-concern-raised? / :safety-concern-resolved? food-safety flag
    - :processed? true once a `:log-production-batch` proposal commits
    - :shipment-finalized? true once a `:coordinate-shipment` proposal commits"
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

;; ----------------------- pure value helpers (unchanged seam) -----------------------

(defn production-batch
  "Retrieve a batch by id, or nil if it does not exist / is not yet
  registered."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn batch-already-processed?
  "True only if the batch exists and has already been marked processed."
  [st batch-id]
  (true? (:processed? (production-batch st batch-id))))

(defn batch-shipment-finalized?
  "True only if the batch exists and its shipment has already been
  finalized."
  [st batch-id]
  (true? (:shipment-finalized? (production-batch st batch-id))))

(defn log-batch
  "Register/update `batch-data` under `batch-id` and mark it processed
  (one-way flag). Used once a `:log-production-batch` proposal commits
  against a FRESH batch record (wholesale replace, e.g. operator
  onboarding a batch it also wants marked processed in one step)."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] (assoc batch-data :processed? true)))

(defn finalize-shipment
  "Mark an existing batch's shipment as finalized (one-way flag), leaving
  all other fields untouched. Used once a `:coordinate-shipment` proposal
  commits."
  [st batch-id]
  (assoc-in st [:batches batch-id :shipment-finalized?] true))

(defn stage-batch
  "Register/update a batch's PRE-COMMIT data (formulation, baking
  parameters, sanitation, evidence-checklist, jurisdiction -- whatever
  `bakeryops.governor`'s independent checks validate) WITHOUT marking it
  processed. Used by tests, simulation, and plant-operator/telemetry
  onboarding BEFORE any `:log-production-batch` proposal is made --
  `governor/already-processed-violations` exists precisely to enforce
  that a batch isn't logged twice, which only means something if staging
  (this fn) and committing (`mark-processed`) are distinct steps."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] batch-data))

(defn mark-processed
  "Flip `:processed?` true on an ALREADY-STAGED batch record, preserving
  every other field (unlike `log-batch`, which replaces the batch's data
  wholesale). Used by the `:commit` graph node once a
  `:log-production-batch` proposal clears the Governor -- the shape
  `bakeryops.governor`'s independent baking-temp/time/moisture/sanitation/
  allergen checks validate."
  [st batch-id]
  (update-in st [:batches batch-id] assoc :processed? true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))

;; ----------------------------- Store protocol -----------------------------

(defprotocol Store
  (snapshot [store]
    "The current `{:batches {batch-id batch-map}}` value -- the exact
    shape `bakeryops.governor`/the pure helpers above expect. Pass this
    straight to `governor/check` or any `production-batch`-family fn.")
  (register-batch! [store batch-id batch-data]
    "Register/update a batch's pre-commit data (`stage-batch`). Used by
    tests, simulation, and plant-operator/telemetry onboarding.")
  (mark-processed! [store batch-id]
    "Flip `:processed?` true on an already-staged batch (`mark-processed`).
    Used by the `:commit` graph node for `:log-production-batch`.")
  (mark-shipment-finalized! [store batch-id]
    "Flip `:shipment-finalized?` true on an existing batch
    (`finalize-shipment`). Used by the `:commit` graph node for
    `:coordinate-shipment`.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns fact."))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [state-atom ledger-atom]
  Store
  (snapshot [_store] @state-atom)
  (register-batch! [_store batch-id batch-data]
    (swap! state-atom stage-batch batch-id batch-data)
    batch-data)
  (mark-processed! [_store batch-id]
    (swap! state-atom mark-processed batch-id)
    nil)
  (mark-shipment-finalized! [_store batch-id]
    (swap! state-atom finalize-shipment batch-id)
    nil)
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-batches` is an optional map of
  batch-id -> batch-record (pre-commit data, as staged via
  `register-batch!`/`stage-batch`)."
  [& [{:keys [initial-batches] :or {initial-batches {}}}]]
  (MemStore. (atom {:batches initial-batches}) (atom [])))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:batch/payload` is stored as an EDN string blob (via
  `langchain-store.core`) so `langchain.db` doesn't try to expand an
  opaque, caller-defined batch record into sub-entities. The identity-
  schema builder, EDN-blob codec and seq-keyed event-log read/append are
  the shared kotoba-lang/langchain-store machinery (ADR-2607141600) --
  the seam ~190 actors hand-roll; this store keeps only its domain
  wiring."
  (ls/identity-schema [:batch/id :ledger/seq]))

(defrecord DatomicStore [conn]
  Store
  (snapshot [_store]
    {:batches (into {}
                (map (fn [[bid p]] [bid (ls/dec* p)]))
                (d/q '[:find ?bid ?p
                       :where [?e :batch/id ?bid] [?e :batch/payload ?p]]
                     (d/db conn)))})
  (register-batch! [_store batch-id batch-data]
    (d/transact! conn [{:batch/id batch-id :batch/payload (ls/enc batch-data)}])
    batch-data)
  (mark-processed! [store batch-id]
    (let [current (production-batch (snapshot store) batch-id)]
      (d/transact! conn [{:batch/id batch-id
                          :batch/payload (ls/enc (assoc current :processed? true))}]))
    nil)
  (mark-shipment-finalized! [store batch-id]
    (let [current (production-batch (snapshot store) batch-id)]
      (d/transact! conn [{:batch/id batch-id
                          :batch/payload (ls/enc (assoc current :shipment-finalized? true))}]))
    nil)
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `initial-batches`
  (batch-id -> batch-record); empty when omitted."
  [& [{:keys [initial-batches] :or {initial-batches {}}}]]
  (let [s (->DatomicStore (d/create-conn schema))]
    (doseq [[batch-id batch-data] initial-batches]
      (register-batch! s batch-id batch-data))
    s))

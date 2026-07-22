(ns bakeryops.advisor
  "BakeryOpsAdvisor -- the contained LLM/decision node. This actor's
  intelligence layer proposes plant-operations coordination actions
  (production-batch logging, equipment-maintenance scheduling,
  food-safety concern flags, finished-product shipment coordination)
  based on batch state and operator input. The advisor is SEALED into
  the `:advise` step of the operation flow; every proposal is routed
  through the independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals are
  always censored by:
    1. Governor (jurisdiction citation, evidence completeness,
       baking-temp/time/moisture/sanitation/scale-calibration/weight-
       variance/allergen-label gates, food-safety-flag resolution,
       double-commit guards)
    2. Human plant-operator sign-off (both real actuation events --
       `:log-production-batch` / `:coordinate-shipment` -- ALWAYS
       escalate, same as `:flag-food-safety-concern`)

  Current implementation is a mock advisor for testing. Production should
  use langchain/Claude or similar LLM backend (same seam point as
  `cerealops.advisor`, cloud-itonami-isic-0111)."
  )

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence. `:cites` are jurisdiction/
    spec citation maps (e.g. {:spec \"ISO-12345\"}) -- see
    `bakeryops.governor`'s spec-basis check."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op subject jurisdiction]} request]
      (case op
        :log-production-batch
        {:op :log-production-batch
         :effect :propose
         :value {:jurisdiction jurisdiction}
         :cites [{:spec (str "batch-" subject "-formulation-record")}]
         :summary "Production batch logging proposed from staged plant telemetry/operator entry"
         :confidence 0.9}

        :schedule-maintenance
        {:op :schedule-maintenance
         :effect :propose
         :value {:equipment (:equipment request "oven")
                 :note (:note request "routine preventive maintenance")}
         :cites [{:spec "Equipment-Manual"}]
         :summary "Equipment maintenance scheduling proposed (oven/mixer/scale)"
         :confidence 0.9}

        :flag-food-safety-concern
        {:op :flag-food-safety-concern
         :effect :propose
         :value {:jurisdiction jurisdiction
                 :concern (:concern request "unspecified concern")}
         :cites [{:spec "Plant-HACCP-Plan"}]
         :summary "Food-safety concern flagged for plant-operator review"
         :confidence 0.85}

        :coordinate-shipment
        {:op :coordinate-shipment
         :effect :propose
         :value {:jurisdiction jurisdiction}
         :cites [{:spec (str "batch-" subject "-shipment-manifest")}]
         :summary "Finished-product shipment coordination proposed"
         :confidence 0.9}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})

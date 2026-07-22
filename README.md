# cloud-itonami-isic-1071: Bakery Products Coordination Actor

**ISIC Rev. 5 1071** — Manufacture of Bakery Products

A distributed actor for autonomous, compliant coordination of bakery-products manufacturing plant operations: batch formulation → baking → moisture/weight inspection → allergen labeling → finished-product logistics. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Oven/mixing-line operation and food-safety certification authority remain exclusive to licensed bakery plant staff and regulators.

**Maturity: `:implemented`.** `src/bakeryops/` implements the
`BakeryOpsAdvisor` (`bakeryops.advisor`) and the independent
`BakeryGovernor` (`bakeryops.governor`), composed by
`bakeryops.operation/build` following the itonami actor pattern
(ADR-2607011000): `intake -> advise -> govern -> decide -> commit |
request-approval -> commit | hold`, compiled to a real `langgraph-clj`
`StateGraph` (`langgraph.graph/state-graph` + `compile-graph`,
mirroring `cerealops.operation`, cloud-itonami-isic-0111) with
`interrupt-before #{:request-approval}` and checkpoint-based
human-in-the-loop resume for the two real actuation events
(`:log-production-batch` / `:coordinate-shipment`) and food-safety
concern flagging. Every commit/hold/approval-rejected decision fact is
appended to `bakeryops.store`'s append-only audit ledger
(`ledger`/`append-ledger!`), implemented on both `MemStore` and a
`DatomicStore` (backed by `langchain.db` via `kotoba-lang/langchain-store`)
that pass the same store-contract test
(`test/bakeryops/store_contract_test.cljc`). The demo runner
(`clojure -M:dev:run`) drives the compiled graph end-to-end through a
commit path, an escalate→approve→commit path, an escalate→reject→hold
path, and a hard-hold path, printing the resulting audit ledger.

## Scope

This actor coordinates **plant-operations workflow** for bakery-products manufacturing:
- Production batch logging (formulation, baking parameters, evidence checklist)
- Equipment maintenance scheduling (ovens, mixers, scales)
- Food-safety concern escalation (allergen cross-contact, contamination, defects)
- Finished-product shipment coordination

**Out of scope:**
- Direct mixing/baking-line equipment control (plant staff exclusive)
- Food-safety certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything safety- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch baking-line/mixing control or food-safety certification
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete, or the batch record isn't registered (`:evidence-incomplete`)
  - Baking temperature out of the product's safe range (`:baking-temp-out-of-range`)
  - Baking time exceeded (`:baking-time-exceeded`)
  - Moisture outside target tolerance (`:moisture-out-of-target`)
  - Plant sanitation score insufficient (`:sanitation-score-insufficient`)
  - Mixing/dough scale calibration overdue (`:scale-calibration-overdue`)
  - Finished-product weight variance excessive (`:weight-variance-excessive`)
  - Allergen label mismatch — declared allergens don't cover the formulation (`:allergen-label-mismatch`)
  - Unresolved food-safety flag (`:food-safety-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
  - `:coordinate-shipment` against a batch that was never registered (`:batch-not-registered`)
- **Escalate** (human sign-off always required):
  - `:log-production-batch` / `:coordinate-shipment` — real actuation events, always require plant-operator sign-off even when the Governor is otherwise clean
  - `:flag-food-safety-concern` — a food-safety concern is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-production-batch`** — Log formulation → baking → inspection batch into production records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for ovens/mixers/scales (routine, low risk)
- **`:flag-food-safety-concern`** — Surface a food-safety or allergen-labeling concern (always escalates)
- **`:coordinate-shipment`** — Finalize shipment of finished product (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct baking-line/mixing control, or food-safety certification — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Module structure

Mirrors `cloud-itonami-isic-0111` (`cerealops.*`) module-for-module:

- `bakeryops.facts` — reference data: product-type baking windows, jurisdiction
  allergen/evidence requirements, ingredient allergen table
- `bakeryops.registry` — pure independent verification functions
  (temp/time/moisture/sanitation/calibration/weight/allergen)
- `bakeryops.store` — pure `{:batches ...}` value helpers PLUS a `Store`
  protocol: batch staging/lookup + append-only audit ledger, implemented by
  `MemStore` (in-memory, default) and `DatomicStore` (`langchain.db`-backed,
  via `kotoba-lang/langchain-store`)
- `bakeryops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/
  decision node; a real-LLM `Advisor` implementation is the documented next
  seam, same as every sibling cloud-itonami actor's advisor)
- `bakeryops.governor` — `BakeryGovernor`: ten independent hard checks +
  escalation gates
- `bakeryops.operation` — `run-operation` (the original thin
  proposal-through-governor driver) plus `build`, which compiles the
  `langgraph-clj` `StateGraph`: advise → govern → decide → commit |
  request-approval → commit | hold, with `interrupt-before` +
  checkpoint-based resume for escalated operations
- `bakeryops.sim` — demo runner (`clojure -M:dev:run`)

## Testing

```bash
clojure -M:dev:test   # run the test suite (langgraph/langchain-store resolved via local sibling checkouts)
clojure -M:lint       # clj-kondo, 0 errors / 0 warnings
clojure -M:dev:run    # demo runner -- drives the compiled StateGraph end-to-end
```

`:dev` pins the transitive `langchain` dependency to the in-monorepo local
checkout (`../../kotoba-lang/langchain`) for offline workspace development;
a standalone fork should override `deps.edn`'s `:local/root` coordinates
with git coordinates (see `deps.edn`'s own comment, and Standalone Use below).

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langchain-store {:git/url "https://github.com/kotoba-lang/langchain-store" :git/tag "v0.1.0"}}
 :aliases {:dev {:override-deps
                 {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}}}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).

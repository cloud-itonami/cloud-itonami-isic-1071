# Contributing to cloud-itonami-isic-1071

Thank you for your interest in contributing to the Bakery Operations actor.

## Scope

This repository is a specialization of the cloud-itonami architecture for ISIC
1071 (manufacture of bakery products). **Maturity: `:implemented`** —
`bakeryops.operation/build` compiles a real `langgraph-clj` `StateGraph`
(`intake -> advise -> govern -> decide -> commit | request-approval ->
commit | hold`, `interrupt-before` + checkpoint-based resume) with every
commit/hold/approval-rejected decision landing in `bakeryops.store`'s
append-only ledger (`MemStore` and a `DatomicStore` backed by
`kotoba-lang/langchain-store`). Contributions should:

1. Extend or correct the **Governor rules** (food-safety constraints)
2. Add **product types** or **jurisdictional requirements** to the facts registry
3. Improve **test coverage** for bakery-specific scenarios
4. Clarify **documentation** and ADRs
5. Implement a real LLM `Advisor` (the sealed `bakeryops.advisor/Advisor`
   injection point) or a real Datomic/kotoba-server backend for
   `DatomicStore` (currently `langchain.db`'s in-process implementation)

## Prohibited Changes

Do **not**:

- Add direct baking-line control (oven operation remains exclusive to plant staff)
- Modify the Governor to allow LLM confidence to override food-safety hard holds
- Add JVM-only code (all source must be `.cljc` / portable)
- Change the AGPL-3.0-or-later license

## Process

1. Open an issue describing your proposed change
2. Link to the relevant ADR (ADR-2607122200 or later)
3. Submit a pull request against `main`
4. Ensure all tests pass: `clojure -M:dev:test`
5. Run linter: `clojure -M:lint`

## Code Style

- Use `.cljc` for all source (no `.clj` or `.cljs` only)
- Follow Clojure conventions (kebab-case, docstrings on public fns)
- Governor rules must be pure, side-effect-free predicates
- Test all new facts and registry entries

## Questions?

File an issue or reach out to the maintainers.

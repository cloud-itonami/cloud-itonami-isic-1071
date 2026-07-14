(ns bakeryops.phase
  "Phase machine: the states a bakery batch transits through.

  State machine:
    :intake -> :design -> :produce -> :inspect -> :package -> :audit -> :archived

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the bakery production workflow."
  [:intake :design :produce :inspect :package :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :design :produce :inspect :package :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (.indexOf phase-sequence from-phase)
              to-idx (.indexOf phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))

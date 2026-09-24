# Testing Strategy for the Intelligent Tutoring System

The transition from a simple objective function to a multi-objective, stochastic, heuristic-driven Intelligent Tutoring System (ITS) requires a complete overhaul of the testing paradigm. Traditional "given input X, assert exact output Y" unit tests are insufficient and will lead to flaky builds.

This document outlines the testing architecture for the ITS.

> **Updated in EOA-4b.** The `/api/v1/optimizer/**` path was removed, and with it the fatigue,
> dropout-risk and cognitive-load-calculator components this document used to target. What remains
> is the `POST /plans` path and the genetic core behind it; the sections below name only what
> exists.

## 1. Test Classifications

### A. Deterministic Component Tests
Testing the pure, stateless functions within the pipeline.
*   **Target:** `HybridRetentionEngine`, `FitnessEvaluator`, `SinapseLoadBudget`, `SinapseMinimumDays`, the two `ImportanceStrategy` implementations.
*   **Strategy:** Provide fixed inputs and assert exact expected outputs. For example, assert that `FitnessEvaluator.explain` reconstructs, bit for bit, the aggregate that `evaluate` returns — the identity `ga/fitness/FitnessBreakdownTest` pins.

### B. Property-Based Testing
Instead of testing exact outputs, we test the *properties* or invariants that must hold true regardless of the input.
*   **Target:** Crossover and Mutation Operators.
*   **Strategy:** Generate 100 random chromosomes, mutate them, and assert invariants:
    *   *Invariant 1:* Mutation must never change the total available TimeSlot duration.
    *   *Invariant 2:* Crossover must never produce an invalid `StudyMethodology` enum.

### C. Constraint Validation Tests (Integration)
Testing the heuristic schedulers and the `ChromosomeRepairer`.
*   **Target:** `HybridHeuristicScheduler`, `SpacedRepetitionRepairer`.
*   **Strategy:**
    *   *No Overlap:* Assert that no two `TacticalStudyBlock` start/end times intersect.
    *   *Review Enforcement:* Assert that if a topic's retention probability is < 0.85, the final output *always* contains a `SPACED_REPETITION_REVIEW` block.
    *   *Prerequisites:* Assert that the scheduled set is a prefix of a topological order of the `HARD` edges — `plan/PlanOutputInvariants` refuses any plan that breaks it.

### D. Probabilistic & Convergence Tests (GA End-to-End)
Testing the emergent behavior of the entire GA.
*   **Target:** `sinapse.GeneticPlanEngine.plan()`, through `plan.PlanEngineSelector`.
*   **Strategy:** We cannot assert the exact schedule generated. What is asserted instead is
    reproducibility, and the counter-proof that reproducibility is not constancy: the same request
    and seed produce the same plan on any thread (`plan/PlanEngineDeterminismTest`), different seeds
    reach different plans where the instance has slack, and a seed left installed never leaks into
    the next request on that pool thread.

## 2. Managing Stochasticity (Reproducibility)

The biggest threat to CI stability is the inherent randomness of a Genetic Algorithm.

### Seed-Based Deterministic Execution
We must replace all bare `new Random()` calls with a globally injected `RandomProvider` bean.
*   **In Production:** The provider uses a true random seed (e.g., `SecureRandom`).
*   **In Testing:** The `@SpringBootTest` injects a fixed-seed provider (e.g., `new Random(42)`).
This guarantees that for a given test input, the crossover choices, mutation triggers, and initial population generation will be exactly identical on every single execution, allowing us to safely assert specific macro-behaviors without flakiness.

## 3. Performance & Stress Testing

The GA is highly CPU-bound. The transition to MOOP adds significant overhead (evaluating fatigue curves, retention decay, etc., for every individual in every generation).
*   **Load Testing (JMeter/Gatling):** Simulate concurrent plan requests. The primary metric is *Thread Exhaustion*, and it matters more than it used to: since EOA-4b `POST /plans` runs the search **on the Tomcat worker thread**. The dedicated `ThreadPoolTaskExecutor`, its bounded queue and the 30-second timeout left with the asynchronous job flow, so there is no longer a bulkhead between the search and the HTTP connector. Sizing `plan.engine.ga.generations` and `plan.engine.ga.population-size` against the connector's thread count is now a deployment concern.
*   **Benchmarking (JMH):** Write JMH microbenchmarks for the `FitnessEvaluator.evaluate()` method. If evaluating a single chromosome takes more than 1ms, the GA will fail SLA targets (e.g., 500 pop * 100 gen = 50,000 evaluations = 50 seconds). The heuristic math must be kept hyper-optimized.
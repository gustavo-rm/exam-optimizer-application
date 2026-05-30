# Testing Strategy for the Intelligent Tutoring System

The transition from a simple objective function to a multi-objective, stochastic, heuristic-driven Intelligent Tutoring System (ITS) requires a complete overhaul of the testing paradigm. Traditional "given input X, assert exact output Y" unit tests are insufficient and will lead to flaky builds.

This document outlines the testing architecture for the ITS.

## 1. Test Classifications

### A. Deterministic Component Tests
Testing the pure, stateless functions within the pipeline.
*   **Target:** `FatigueAndEnergyModel`, `HybridRetentionEngine`, `DropoutRiskPredictor`, `FitnessEvaluator`.
*   **Strategy:** Provide mocked inputs and assert exact expected outputs. For example, assert that `calculateBurnoutRisk` returns exactly `0.2` when the fatigue parameter crosses the `50.0` threshold.

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
    *   *Fatigue Thresholds:* Assert that if the `FatigueAndEnergyModel` flags acute burnout, the repairer forcibly truncates the schedule.
    *   *Review Enforcement:* Assert that if a subject's retention probability is < 0.85, the final output *always* contains a `SPACED_REPETITION_REVIEW` block.
    *   *Emergency Mode:* Assert that passing `emergencyMode=true` results in 0 `PASSIVE_READING` blocks.

### D. Probabilistic & Convergence Tests (GA End-to-End)
Testing the emergent behavior of the entire GA.
*   **Target:** `StudyOptimizerService.optimize()`.
*   **Strategy:** We cannot assert the exact schedule generated. Instead, we run the GA 50 times and assert statistical boundaries:
    *   *Convergence:* Assert that the final Generation's Best Fitness is strictly greater than the Initial Generation's Best Fitness in 95% of runs.
    *   *Adaptive Scheduling:* Assert that when a "High Risk" student profile is provided, the average total scheduled hours across 50 runs is statistically significantly lower than the average hours for a "Low Risk" profile.

## 2. Managing Stochasticity (Reproducibility)

The biggest threat to CI stability is the inherent randomness of a Genetic Algorithm.

### Seed-Based Deterministic Execution
We must replace all bare `new Random()` calls with a globally injected `RandomProvider` bean.
*   **In Production:** The provider uses a true random seed (e.g., `SecureRandom`).
*   **In Testing:** The `@SpringBootTest` injects a fixed-seed provider (e.g., `new Random(42)`).
This guarantees that for a given test input, the crossover choices, mutation triggers, and initial population generation will be exactly identical on every single execution, allowing us to safely assert specific macro-behaviors without flakiness.

## 3. Performance & Stress Testing

The GA is highly CPU-bound. The transition to MOOP adds significant overhead (evaluating fatigue curves, retention decay, etc., for every individual in every generation).
*   **Load Testing (JMeter/Gatling):** Simulate concurrent optimization requests. The primary metric is *Thread Exhaustion*. The existing `ThreadPoolTaskExecutor` (with its 30-second CompletableFuture timeout) must be validated to ensure it returns 408 Timeouts gracefully rather than crashing the Tomcat thread pool.
*   **Benchmarking (JMH):** Write JMH microbenchmarks for the `FitnessEvaluator.evaluate()` method. If evaluating a single chromosome takes more than 1ms, the GA will fail SLA targets (e.g., 500 pop * 100 gen = 50,000 evaluations = 50 seconds). The heuristic math must be kept hyper-optimized.
# Numerical Stability and Precision Audit: ITS Optimizer

## 1. Executive Summary
As the Intelligent Tutoring System (ITS) transitions to a Multi-Objective Optimization Problem (MOOP) using a weighted sum fitness pipeline, the mathematical operations within the Genetic Algorithm (GA) become significantly more complex.

The GA is a massive "hot loop." Evaluating a population of 500 individuals over 100 generations requires 50,000 passes through the `FitnessEvaluator`. Therefore, balancing numerical stability against raw CPU throughput is critical.

This audit identifies potential risks involving floating-point instability, domination imbalance, and precision drift, and recommends optimal data types for the ITS.

## 2. Analysis of the Mathematical Pipeline

### A. Weighted Objective Aggregation
*   **Current State:** The `FitnessEvaluator` iterates through `FitnessObjective` instances, summing `calculateReward() * getWeight()`. Rewards currently use `Math.log(1.0 + days)`.
*   **Risk - Domination Imbalance:** If we add a new objective (e.g., "Minimize Overlap") that returns a raw score of `1000.0`, it will completely dominate the `ScoreGainObjective` (which returns values like `1.79`). A standard weighted sum fails when objective ranges are un-normalized.
*   **Risk - Floating Point Instability:** Iteratively summing many small `double` values can lead to precision loss (catastrophic cancellation), although less likely to be fatal in a GA than in financial systems.

### B. Penalty Scaling
*   **Current State:** Penalties (Fatigue, Dropout Risk) are calculated as multiplicative factors (`0.0` to `1.0`).
*   **Risk - Fitness Saturation (The Zero Problem):** Multiplicative penalties compound. If a student is burned out (penalty 0.1) and misses mandatory reviews (constraint penalty 0.5), the fitness drops geometrically. If fitness approaches `0.0`, the GA loses gradient information. A population where 90% of individuals have fitness `0.001` becomes a random walk because Selection operators (like Roulette Wheel) can no longer distinguish between slightly bad and catastrophically bad individuals.

## 3. Data Type Evaluation for Hot Loops

### A. `double` (Primitive Floating-Point)
*   **Pros:** Hyper-fast. Mapped directly to CPU registers. Perfect for trigonometric calculations (Biphasic energy curves) and exponential decay (Ebbinghaus forgetting curve).
*   **Cons:** Vulnerable to precision drift (`0.1 + 0.2 = 0.30000000000000004`). Cannot accurately represent exact decimal fractions.
*   **Verdict:** **Primary Choice for GA Heuristics**. The stochastic nature of a GA means that micro-drifts in fitness (e.g., `17.917001` vs `17.917002`) do not alter the fundamental selection probabilities enough to derail convergence.

### B. `BigDecimal`
*   **Pros:** Infinite precision. Zero drift. Perfect for financial transactions.
*   **Cons:** Objects must be allocated on the heap for every operation. In a hot loop (50,000 evaluations * 10 blocks * 3 constraints), this will cause massive Young Generation Garbage Collection (GC) pressure, completely halting the optimization threads.
*   **Verdict:** **Strictly Prohibited in the GA Pipeline**. `BigDecimal` should only be used if we ever attach explicit financial cost modeling to schedules, and even then, only outside the evolutionary loop.

### C. Scaled Integer Arithmetic (`long`)
*   **Pros:** Fast, no GC overhead, exact precision (e.g., representing 1.5 hours as 90 minutes).
*   **Cons:** Harder to implement complex math (logarithms, exponential decay) without writing custom fixed-point math libraries.
*   **Verdict:** **Recommended for Duration and Capacity**. Time should always be represented as `long durationMinutes` rather than `double durationHours`. However, final fitness and probabilities should remain `double`.

## 4. Architectural Recommendations & Remediation

1.  **Objective Normalization:**
    *   **Recommendation:** Do not sum raw objective scores. Implement a normalization layer. Every `FitnessObjective` must return a value strictly bounded between `0.0` and `100.0`. The `FitnessEvaluator` then applies the weights to these normalized percentages. This prevents Domination Imbalance.

2.  **Additive Penalties over Multiplicative Penalties:**
    *   **Recommendation:** To prevent Fitness Saturation, transition from multiplicative factors (`score *= penalty`) to additive penalty offsets (`score -= penaltyWeight * penaltySeverity`). This maintains a linear gradient, allowing the GA to navigate out of "valleys of death."

3.  **Use `double` for Fitness, `long` for Tactical Metrics:**
    *   **Recommendation:** Ensure `CognitiveLoadCalculator` and `HybridRetentionEngine` stick to `double` for probabilities. Ensure `TimeSlot` and `TacticalStudyBlock` strictly use `long` (minutes) to prevent fractional time drift (e.g., losing 1 second every time a block is split).

4.  **Fitness Floor:**
    *   **Recommendation:** Ensure the `FitnessEvaluator` enforces a hard floor (`Math.max(1.0, score)`). A fitness of exactly `0.0` or negative values will break Roulette Wheel selection algorithms.

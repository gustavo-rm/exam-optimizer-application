# Fatigue and Energy Model Architecture

## 1. The Fallacy of Linear Fatigue
Simplistic scheduling models assume human energy and fatigue scale linearly (e.g., studying 4 hours is twice as tiring as studying 2 hours, and energy is constant throughout the day). This leads to unrealistic schedules that look optimal mathematically but cause real-world burnout.

This architecture introduces a non-linear, multi-dimensional `FatigueAndEnergyModel` that accurately maps the human capacity for learning.

## 2. Core Dimensions of the Model

### A. Intraday Energy Variation (The Biphasic Curve)
Human energy levels typically follow a biphasic curve throughout the day, dictated by circadian rhythms.
We categorize students into three `Chronotypes`:
*   **Morning Larks:** Peak energy early morning (8 AM - 11 AM), sharp post-lunch dip, moderate recovery in late afternoon, steep drop in evening.
*   **Night Owls:** Low energy in the morning, steady climb through the afternoon, peak energy late at night (9 PM - 1 AM).
*   **Intermediate (Third Bird):** The standard bell curve. Peak around 10 AM, post-lunch dip, secondary peak around 4 PM.

*Heuristic approach:* We use continuous trigonometric functions (e.g., sine waves offset by chronotype phase shifts) to map a specific `LocalTime` to an expected energy multiplier (0.5 to 1.5).

### B. Interday Fatigue Accumulation
Fatigue doesn't reset to zero simply because a new day starts. If a student schedules 8-hour days back-to-back, the *starting* fatigue on Day 3 is significantly higher than Day 1.
*Heuristic approach:* Fatigue carries over. We apply a compounding decay factor. For example, 20% of today's generated fatigue carries over to tomorrow's baseline. A rest day (0 hours) allows this accumulated fatigue to flush out exponentially.

### C. Cognitive Overload & Emotional Exhaustion
Different subjects and methodologies drain different "tanks":
*   **Cognitive Load:** Drained by complex problem-solving (Math, Active Recall). Recovered relatively quickly with sleep.
*   **Emotional Load:** Drained by anxiety-inducing subjects or repeated failures. Recovered slowly; requires actual leisure or "win" days.

### D. Burnout Thresholds
Instead of linearly degrading fitness, we implement a "cliff" threshold. If cumulative interday fatigue or acute intraday cognitive overload crosses the Burnout Threshold, the student's expected retention plummets to near-zero, and the GA applies a massive penalty. This forces the optimizer to insert rest days.

## 3. Integration into the GA Fitness Pipeline

The model acts as a core service injected into the `FitnessEvaluator`.
Specifically, it replaces the simplistic math in `FatigueAndSustainabilityPenalty`.
1. The evaluator passes the chronological `TacticalStudyPlan` to the model.
2. The model simulates the plan day-by-day, hour-by-hour.
3. It calculates the delta between the student's *Energy Budget* and the plan's *Load*.
4. It returns a Burnout Risk Factor and an Overall Exhaustion Penalty.

## 4. Path to Machine Learning (ML)
Currently, this model uses expert heuristics (mathematical curves and threshold rules). This guarantees **explainability**: we can trace exactly why the scheduler rejected a 10 PM math block for a Morning Lark.
To evolve this to ML:
1. **Data Collection:** Track the delta between the *Expected Schedule* and *Actual Execution* via the Chatbot telemetry.
2. **Feature Engineering:** Features include `TimeOfDay`, `Chronotype`, `Methodology`, `CurrentStreakDays`.
3. **Model:** Train a Gradient Boosting Regressor (XGBoost) to predict `Actual_Minutes_Completed` or `Retention_Score`. Once trained, the ML model's inference engine will replace the trigonometric heuristics in the `FatigueAndEnergyModel`.
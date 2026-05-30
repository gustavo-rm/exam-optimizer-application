# Dropout Risk Predictor Architecture

## 1. The Challenge of Disengagement
A mathematically optimal study schedule is worthless if the student abandons the platform. The Intelligent Tutoring System (ITS) must identify the warning signs of burnout and actively adapt the generated schedules to maximize long-term consistency, even if it means sacrificing short-term point yield.

## 2. Risk Detection Heuristics
The `DropoutRiskPredictor` calculates a continuous "Risk Score" (0.0 to 1.0) based on compounding factors:
*   **Excessive Fatigue/Density:** Is the proposed schedule continuously pushing the student near their burnout threshold without adequate recovery?
*   **Emotional Overload:** Does the schedule stack too many high-emotional-load subjects (e.g., subjects the student fears or consistently fails) back-to-back?
*   **Repeated Task Failures (History):** If telemetry shows the student missed 40% of their scheduled blocks last week, scheduling a *more* intense week will almost guarantee churn.
*   **Declining Consistency:** A sudden drop in active days or shortening of completed durations relative to historical baselines.

## 3. Adaptive Scheduling Behaviors
When the `DropoutRiskPredictor` flags a high risk (e.g., > 0.7), the GA and Heuristic Scheduler must adapt their behavior to prioritize retention over progression:
*   **Recovery Days:** The scheduler drastically lowers the cognitive/emotional load cap, forcing the GA to choose passive, low-load methodologies.
*   **Motivational Wins:** The system prioritizes scheduling subjects the student is already good at, using fast, rewarding methodologies (like easy flashcards) to rebuild confidence.
*   **Schedule Triage:** Dropping minor/low-ROI tasks to create immediate breathing room (buffer zones).

## 4. Integration into the Fitness Pipeline
We introduce the `DropoutRiskPenalty` to the MOOP evaluator.
Unlike the `FatiguePenalty` (which looks at physiological exhaustion limits), the `DropoutRiskPenalty` evaluates the psychological sustainability of the *trend*.
*   If the student's historical risk profile is high, and the proposed schedule fails to include a "Recovery Day" (a day with load < 30% of capacity), the schedule is heavily penalized.
*   If the student's profile is healthy, the penalty is neutral, allowing the GA to pursue pure score maximization.

*Crucially*, the system does not punish the student. By penalizing *intense schedules* when risk is high, the system automatically rewards lighter, easier schedules, gracefully guiding the student back into a consistent routine.

## 5. Evolution to Machine Learning (ML)
Currently, the predictor relies on deterministic heuristics (e.g., `Risk = 0.4 * ConsistencyDrop + 0.6 * Fatigue`).
To transition to ML:
1. **Target Variable:** Define churn (e.g., 0 completed blocks for 7 consecutive days).
2. **Features:** Extract trailing 14-day completion rates, average daily emotional load, and average self-reported motivation levels.
3. **Model:** Train a Survival Analysis model (like Cox Proportional Hazards) or an LSTM/RNN to predict the probability of churn within the next 3 days. The output of this ML model will seamlessly replace the heuristic formula in the `DropoutRiskPredictor`.

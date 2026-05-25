# Retention Engine Architecture

## 1. The Challenge of Retention
A schedule that merely allocates study hours but fails to review material guarantees knowledge decay. To evolve into an Intelligent Tutoring System (ITS), we must integrate cognitive science principles—specifically the **Ebbinghaus Forgetting Curve** and **Spaced Repetition**.

## 2. Algorithm Evaluation

### A. The Leitner System (Bucket/Box Method)
*   **Concept:** Flashcards are sorted into boxes. A correct answer moves the card to a less frequent review box (e.g., Box 1 = daily, Box 2 = every 3 days).
*   **Pros:** Extremely simple to implement. Fast.
*   **Cons:** Too rigid for dynamic macro-scheduling. It assumes discrete, atomic flashcards rather than continuous subject comprehension.

### B. SuperMemo-2 (SM-2)
*   **Concept:** Calculates an optimal "Easiness Factor" (EF) and an "Inter-Repetition Interval" (I) based on a subjective quality grade (0-5) of the review.
*   **Pros:** The gold standard for spaced repetition. Highly proven over decades.
*   **Cons:** Designed for atomic flashcards, not aggregate "Subject" level knowledge.

### C. Custom Hybrid Heuristic (Recommended)
*   **Concept:** We adapt the core SM-2 math (Easiness Factor and Interval scaling) but apply it at the **Subject/Topic Level**. The "Quality Grade" is derived implicitly from the student's *Knowledge Gap* updates and explicitly from *Practice Exam* scores.
*   **Pros:** Fits our existing macro-planning model. Allows us to calculate a continuous "Retention Probability" curve for any subject at any time.

## 3. The Forgetting Curve Model
Retention $R$ at time $t$ (days since last review) is modeled as:
$$ R = e^{-\frac{t}{S}} $$
Where $S$ is the **Stability** (or memory strength) of the subject.
*   A new subject has a low $S$ (e.g., $S=1$ day). The curve drops fast.
*   After successful spaced reviews, $S$ increases exponentially (e.g., $S=4, S=10, S=25$). The curve flattens out.

## 4. Integration with the Genetic Algorithm

The Retention Engine shifts the ITS from a purely generative optimizer to a **reactive, constraint-bound** optimizer.

### A. The Mandatory Review Constraint
If the calculated $R$ for a subject drops below a critical threshold (e.g., 80%) during the scheduled week, a Spaced Repetition Review becomes **Mandatory**.
*   **In the GA Fitness Pipeline:** We implement a `MandatoryReviewConstraint`. If a generated `TacticalStudyPlan` fails to include a `SPACED_REPETITION_REVIEW` block for a subject that requires it, the plan suffers a massive fitness penalty.

### B. The Chromosome Repairer
The `ChromosomeRepairer` (in the tactical layer) will actively query the `RetentionEngine`. If mandatory reviews are missing, it will forcibly overwrite low-priority passive reading blocks with mandatory review blocks.

## 5. Subject-Specific Retention Behavior
Not all subjects decay equally. A subject heavily reliant on memorization (e.g., Constitutional Law, Anatomy) decays faster than a subject reliant on conceptual frameworks (e.g., Math, Physics). The `Stability` multiplier must be modified by the subject's intrinsic `CognitiveLoad` and the student's historical affinity for it.

## 6. Path to Machine Learning Calibration
The initial engine relies on SM-2 heuristics. To evolve to ML:
1.  **Telemetry:** Log predicted $R$ against actual performance on Practice Exams.
2.  **Half-Life Regression:** Train an ML model (like Duolingo's Half-Life Regression) to predict the exact $S$ (Stability) based on temporal features (time since last review, total reviews) and student features (chronotype, baseline knowledge gap).
3.  **Dynamic Scheduling:** The ML inference engine will dictate the exact optimal hour for review to maximize the spacing effect.

# Algorithmic Approaches for Tactical Scheduling in ITS

Moving from an aggregate "hours per day" model to a granular "time slot" model requires an algorithmic paradigm shift. We must match cognitive load to fluctuating human energy levels while handling real-world chaos (interruptions).

## 1. Algorithmic Candidates

### A. Bin Packing (First Fit / Best Fit Decreasing)
*   **Concept:** Treat `TimeSlots` (or Availability Windows) as "bins" of a certain capacity (duration & energy), and `StudyBlocks` as "items".
*   **Pros:** Extremely fast. Easy to implement.
*   **Cons:** Very rigid. Fails spectacularly when objectives are highly non-linear or conflicting (e.g., trying to fit a high-priority, high-stress block into a small evening bin when the student is tired). Does not handle "catch-up" logic naturally.

### B. Constraint Satisfaction Problem (CSP) Solvers (e.g., OptaPlanner/Timefold, Choco)
*   **Concept:** Define variables (Blocks), domains (Time Slots), and constraints (No overlap, energy <= capacity). The solver exhaustively searches for a valid/optimal state.
*   **Pros:** Guarantees hard constraints are never violated. Excellent at finding optimal solutions for tightly constrained problems (e.g., mandatory spaced repetition rules).
*   **Cons:** NP-Hard. Computation time can explode with scale (many slots, many blocks). Often acts as a "black box," making it hard to debug *why* a specific schedule was chosen.

### C. Hybrid GA + Heuristic Scheduling (Recommended)
*   **Concept:** Use the Genetic Algorithm (GA) to dictate the *Strategic Macro-Plan* (Which subjects? How many total hours? Which days?). Use a deterministic *Heuristic Scheduler* to perform the *Tactical Micro-Plan* (Placing those hours into specific time slots using intelligent greedy heuristics).
*   **Pros:**
    *   **Scalable:** The GA searches a smaller, manageable space (Days/Subjects). The Heuristic runs in O(N log N) time to pack the slots.
    *   **Context-Aware:** The heuristic can easily match a high-intensity block (e.g., Practice Exam) to a high-energy window (e.g., Saturday Morning).
    *   **Resilient:** If a student is interrupted, we do not need to re-run the expensive GA. We simply re-run the fast Heuristic Scheduler on the remaining macro-plan.

## 2. Managing Real-World Chaos

To support a robust ITS, the Tactical Scheduler must handle the following:

### A. Interruptions & Catch-up Logic
*   **Mechanism:** The heuristic scheduler reads the student's "Completed Blocks" history. If a block was missed, its underlying "Macro-Plan Priority" increases dynamically. On the next generation/reschedule, the heuristic greedy algorithm will place it first.

### B. Buffer Zones
*   **Mechanism:** The heuristic should not pack availability windows to 100% capacity. It should enforce a "Buffer Constraint" (e.g., leave 15% of the window blank). This absorbs minor overruns without cascading delays to the rest of the day.

### C. Emergency Scheduling
*   **Mechanism:** Triggered when the exam is imminent or the student is severely behind. The heuristic overrides standard spaced-repetition and emotional load rules. It prioritizes highest-ROI subjects using high-intensity methodologies (Active Recall) and consumes buffer zones.

## 3. Matching Intensity to Energy Levels

The core of the Heuristic Scheduler is the "Energy Matcher".
*   Each `AvailabilityWindow` has a predicted `EnergyLevel` (e.g., High in morning, Low after work).
*   Each `StudyMethodology` has a `CognitiveLoad` and `EmotionalLoad`.
*   The heuristic sorts windows by Energy and blocks by Load, attempting to match High-High and Low-Low to prevent burnout and maximize efficiency.

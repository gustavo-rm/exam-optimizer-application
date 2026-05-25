# Tactical Scheduling Architecture: Intelligent Tutoring System

## 1. Operator Paradigm: Constraint-Preserving vs. Repair-Based

As the system evolves from allocating generic "study days" to a tactical, time-slot-based Intelligent Tutoring System (ITS), the constraints become significantly tighter. We must respect non-overlapping time slots, energy budgets, mandatory spaced repetition reviews, and emergency mode triage constraints.

### Constraint-Preserving Operators
These operators are designed to *never* produce an invalid chromosome.
*   **Pros:** Fitness evaluations are fast because we don't waste cycles evaluating invalid offspring or running expensive repair logic. The population is guaranteed to be 100% executable.
*   **Cons:** Very difficult to implement for complex scheduling problems. Crossover, in particular, often breaks temporal constraints (e.g., swapping a morning block from Parent A with an afternoon block from Parent B might overlap with an existing noon block). Implementing a truly constraint-preserving crossover for variable-length, multi-attribute time slots can become a bottleneck itself.

### Repair-Based Operators (Recommended)
These operators apply traditional or domain-specific perturbations and then invoke a deterministic "repair" function to fix any violations (e.g., overlapping slots, energy overdraw).
*   **Pros:** Much easier to implement complex crossover and mutation logic. It maintains high genetic diversity. It allows the algorithm to briefly "step outside" the feasible search space to cross valleys, repairing the result back to the edge of the feasible space (often where the global optimum lies).
*   **Cons:** Repair functions can be computationally expensive.

### Recommendation for ITS
**Hybrid Approach with Heavy Repair:**
Use **Repair-Based Operators** as the primary mechanism. The chromosome should be designed such that simple constraints (e.g., valid study methodology) are preserved by design, but complex temporal and energy constraints are handled by a dedicated `ChromosomeRepairer`.
*   If a mutation causes a block to overlap, the repairer shifts it to the next available slot or trims its duration.
*   If an energy budget is exceeded, the repairer downgrades the methodology (e.g., from "Active Recall" to "Passive Reading") or drops the lowest-priority block.
*   If "Emergency Mode" is active, the repairer actively strips out long-term review sessions and replaces them with high-yield triage blocks.

## 2. Chromosome Representation

To reduce invalid population generation, the representation must change from a simple `Map<Subject, Integer>` (days) to a structured chronological timeline.

A good approach is an **Availability-Bounded Timeline**. The chromosome isn't a free-floating array of blocks; it is a fixed grid of the student's *actual availability windows*, where genes represent the *content* of those windows.

*   `Gene`: A `StudyBlock` (Subject, Methodology, Duration).
*   `Chromosome`: A mapping of `TimeSlot` -> `StudyBlock`. By fixing the `TimeSlot` grid to the student's availability, we eliminate the constraint of "allocating time when the student isn't available" by design. Overlaps are impossible because a `TimeSlot` can only hold one `StudyBlock`.

## 3. Recommended Mutation Strategies

1.  **Methodology Mutation:** Randomly changes the `StudyMethod` of a block (e.g., Reading -> Flashcards) while keeping the subject and time.
2.  **Subject Swap Mutation:** Swaps the subjects of two blocks of equal duration to explore different chronological orderings.
3.  **Intensity Mutation (Energy Management):** In response to fatigue accumulation, lowers the duration or intensity of late-day blocks.

## 4. Recommended Crossover Strategies

1.  **Day-Boundary Crossover:** Splice parents at midnight boundaries. Parent A provides Monday-Wednesday, Parent B provides Thursday-Sunday. This perfectly preserves intraday temporal constraints and energy budgets.
2.  **Methodology Inheritance Crossover:** Inherit the *timeline* (subjects and slots) from Parent A, but inherit the *study methodologies* from Parent B.

## 5. Mandatory Constraints (The Repair Layer)

The `ChromosomeRepairer` must enforce:
*   **Spaced Repetition:** If the chromosome lacks a mandatory review block dictated by the `RetentionEngine`, the repairer forcibly inserts it, overwriting a low-priority block.
*   **Fatigue Caps:** If the cumulative cognitive load of a day exceeds the student's daily maximum, the repairer truncates the last block.

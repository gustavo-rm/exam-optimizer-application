# Performance and Memory Optimization Review

This document details the optimizations applied to the hot paths of the Genetic Algorithm (GA) engine to reduce Garbage Collection (GC) pressure and improve execution speed.

## 1. The Bottleneck: Inner Loop Allocations

Genetic Algorithms evaluate millions of individuals during a standard run (e.g., population 500 * 1000 generations = 500,000 evaluations). Any object allocation, stream processing, or unnecessary abstraction inside the `crossover`, `mutation`, or `fitness` methods multiplies exponentially, leading to severe Young Generation GC pressure and potential pauses.

## 2. Optimizations Implemented

### A. Stream API Removal
The Java Stream API creates significant overhead (Spliterators, lambda capturing, intermediate collection wrappers). While elegant, it is highly detrimental in a GA inner loop.
- **Population Analytics:** Replaced `individuals.stream().max()`, `min()`, and `average()` in `Population.java` with traditional `for` loops.
- **Fitness Loop:** Removed `individuals.parallelStream().forEach()` from the fitness calculation. Parallel streams introduce excessive Thread/ForkJoinPool overhead and contention, especially since the GA runs are already offloaded to a bounded `@Async` thread pool. Replacing it with a simple `for` loop vastly improves throughput per request.
- **Study Plan Summation:** Replaced `daysPerSubject.values().stream().mapToInt(Integer::intValue).sum()` in `StudyPlan.java` with a traditional `for` loop that aggregates the integers directly.
- **Study Plan Factory:** Replaced `minimumDaysPerSubject.values().stream().mapToInt(Integer::intValue).sum()` with an iterative loop.

### B. Fast List Removal in Crossover
During the repair phase of both `RepairingCrossover.java` and `WeightedAverageCrossover.java`, the code iteratively removed elements from an `ArrayList` when they hit constraint limits (`subjects.remove(randomSubject)`).
- `ArrayList.remove(Object)` is an $O(N)$ operation because it must scan the list and then shift all subsequent elements left in memory.
- **Optimization:** Implemented $O(1)$ fast-removal by swapping the element to be removed with the last element in the list and then popping the last element.

## 3. JVM Implications and High Concurrency

These optimizations ensure that each evolution cycle creates fewer temporary objects (like stream iterators). By reducing the allocation rate, the JVM's Young Generation fills up slower, resulting in fewer Minor GC pauses.

Furthermore, removing `parallelStream` from the internal fitness calculation ensures that high-concurrency environments do not suffer from ForkJoinPool starvation. Each HTTP request processing a GA is mapped to a dedicated thread in `optimizerTaskExecutor`, ensuring predictable scaling up to the core limit.

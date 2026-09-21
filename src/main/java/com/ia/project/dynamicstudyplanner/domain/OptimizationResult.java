package com.ia.project.dynamicstudyplanner.domain;

/**
 * A data transfer object (DTO) that encapsulates the final result of the optimization process.
 * This is an immutable value object, ensuring that the result data cannot be changed after creation.
 *
 * @param plan The best study plan found by the algorithm.
 * @param fitness The fitness score of the best plan. This represents the calculated "potential score".
 * @param generationsRun The total number of generations the algorithm ran for.
 * @param executionTimeMillis The total time in milliseconds the optimization process took to complete.
 */
public record OptimizationResult(
        StudyPlan plan,
        double fitness,
        int generationsRun,
        long executionTimeMillis,
        FitnessBreakdown fitnessBreakdown
) {

    /**
     * Resultado sem decomposição da fitness.
     *
     * <p>Existe para os pontos que só precisam do plano e do número — testes e mapeadores. O
     * caminho de produção usa o construtor canônico, porque a decomposição é o que responde "por
     * que este plano" (GAP-07).
     */
    public OptimizationResult(StudyPlan plan, double fitness, int generationsRun,
            long executionTimeMillis) {
        this(plan, fitness, generationsRun, executionTimeMillis, null);
    }
}

package com.ia.project.dynamicstudyplanner.ga.fitness;

import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.ConstraintValidator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.FitnessObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FitnessPenalty;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates the fitness of a plan as a normalised weighted sum.
 *
 * <pre>
 *   fitness = clamp( SUM_o w_o * O_o(plan)  -  SUM_c lambda_c * severity_c(plan) , 0, 1 )
 *             * PRODUCT_p tacticalPenalty_p(plan)
 * </pre>
 *
 * <b>Reference documentation: {@code docs/revisao-ag/05-fitness-function.md}.</b> That page lists
 * every term, the learning-science result behind it, its weight and the justification for that
 * weight. Changing anything here without updating it leaves the product unable to explain its own
 * planning decisions.
 *
 * <h2>Why a weighted sum, and not a Pareto front</h2>
 *
 * Deliberate choice, recorded in {@code docs/revisao-ag/02-formulacao.md}: scalarisation only
 * reaches the convex hull of the Pareto front, but it returns the single plan the API contract is
 * shaped around, needs no UI to choose among solutions, and does not duplicate the multi-objective
 * formulation that belongs to the project's research line. What makes the choice defensible is not
 * a claim of optimality but the sensitivity study that reports how far the plan moves when the
 * weights are perturbed.
 *
 * <h2>Three properties this aggregation guarantees</h2>
 * <ol>
 *   <li><b>Every objective is normalised to [0,1] and the weights sum to 1</b>, so the aggregate is
 *       in [0,1] and comparable across exams, students and releases. Before this, the objective
 *       carried the exam's own units and two subjects in one sum could differ by 250,000x, which
 *       made fitness values incomparable and any benchmark meaningless
 *       (docs/revisao-ag/01-auditoria-fitness.md §3.1).</li>
 *   <li><b>Constraint violations are subtracted, not multiplied.</b> A multiplicative factor makes
 *       the cost of a violation proportional to the plan's own quality — a good plan pays more for
 *       the same mistake than a bad one — and compounds geometrically towards zero, where the search
 *       loses all gradient. Subtracting a graded severity keeps the cost of a violation the same
 *       wherever it happens.</li>
 *   <li><b>The weight sum is asserted at construction.</b> A weight edited without rebalancing the
 *       others silently rescales the fitness and invalidates every recorded benchmark number, so it
 *       fails fast instead.</li>
 * </ol>
 *
 * <h2>The multiplicative penalties</h2>
 *
 * {@code FitnessPenalty} beans still multiply, because they express "this plan is unusable" rather
 * than "this plan scores less". In the macro path they all return 1.0 by design — they only have
 * data to act on for a {@code TacticalStudyPlan} — so today the product is the identity. Keeping the
 * hook costs one multiplication and preserves the tactical layer's contract.
 */

public class FitnessEvaluator {

    /** Tolerance for the weight-sum assertion, to absorb double representation only. */
    private static final double WEIGHT_SUM_TOLERANCE = 1e-9;

    private final List<FitnessObjective> objectives;
    private final List<FitnessPenalty> penalties;
    private final List<ConstraintValidator> constraints;

    public FitnessEvaluator(
            List<FitnessObjective> objectives,
            List<FitnessPenalty> penalties,
            List<ConstraintValidator> constraints
    ) {
        this.objectives = objectives;
        this.penalties = penalties;
        this.constraints = constraints;
        assertWeightsSumToOne(objectives);
    }

    /**
     * Fails fast when the configured objective weights do not sum to 1.
     * <p>
     * Without this, adding an objective and forgetting to rebalance would quietly change the scale
     * of every fitness value the system reports, and no test would notice until the benchmark
     * numbers stopped matching the documentation.
     */
    private static void assertWeightsSumToOne(List<FitnessObjective> objectives) {
        if (objectives == null || objectives.isEmpty()) {
            return;
        }
        double sum = objectives.stream().mapToDouble(FitnessObjective::getWeight).sum();
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalStateException(String.format(
                    "Os pesos dos objetivos devem somar 1.0, mas somam %.6f. "
                            + "Rebalanceie FitnessWeights e atualize docs/revisao-ag/05-fitness-function.md.",
                    sum));
        }
    }

    public double evaluate(StudyPlan plan, EvolutionContext context) {
        // 1. Weighted sum of the normalised objectives.
        double score = 0.0;
        for (FitnessObjective objective : objectives) {
            score += objective.calculateReward(plan, context) * objective.getWeight();
        }

        // 2. Subtract graded constraint violations.
        for (ConstraintValidator constraint : constraints) {
            score -= constraint.getPenaltyWeight() * constraint.violationSeverity(plan, context);
        }

        double bounded = Math.clamp(score, 0.0, 1.0);

        // 3. Multiplicative penalties. Reserved for the tactical path; identity in the macro path.
        double finalFitness = bounded;
        for (FitnessPenalty penalty : penalties) {
            finalFitness *= penalty.calculatePenaltyFactor(plan, context);
        }

        return finalFitness;
    }

    /**
     * A mesma fitness, com os termos que a produziram (GAP-07).
     *
     * <h2>Por que este método existe ao lado de {@link #evaluate}, e não no lugar dele</h2>
     *
     * A decomposição aloca: duas listas e um objeto por termo. {@code evaluate} é chamado uma vez
     * por indivíduo por geração — até meio milhão de vezes no pior caso que a API aceita — e este
     * repositório já pagou caro por alocação nesse laço (pendências P17 e P18). Fazer
     * {@code evaluate} delegar aqui tornaria a igualdade entre os dois estrutural, e é tentador
     * justamente por isso; custaria uma alocação por avaliação, que é o preço que aquelas duas
     * pendências foram abertas para remover.
     *
     * <p>Então a aritmética é <b>repetida</b>, na mesma ordem e com as mesmas operações, e a
     * igualdade vira responsabilidade de teste em vez de consequência do desenho. É uma troca
     * declarada, não um descuido: {@code FitnessBreakdownTest} confere termo a termo que
     * {@code explain(...).aggregate()} é bit a bit o que {@code evaluate(...)} devolve.
     *
     * <p>Chamado <b>uma vez por requisição</b>, sobre o melhor indivíduo, para montar a resposta.
     *
     * @param plan    o plano a explicar
     * @param context o contexto da evolução
     * @return a fitness decomposta; {@code aggregate()} é o valor que {@link #evaluate} devolve
     */
    public FitnessBreakdown explain(StudyPlan plan, EvolutionContext context) {
        List<FitnessBreakdown.Term> termos = new ArrayList<>();

        // 1. Weighted sum of the normalised objectives — mesma ordem de evaluate.
        double score = 0.0;
        for (FitnessObjective objective : objectives) {
            double valor = objective.calculateReward(plan, context);
            double peso = objective.getWeight();
            double contribuicao = valor * peso;
            score += contribuicao;
            termos.add(new FitnessBreakdown.Term(objective.name(),
                    FitnessBreakdown.TermKind.OBJECTIVE, valor, peso, contribuicao));
        }

        // 2. Subtract graded constraint violations.
        for (ConstraintValidator constraint : constraints) {
            double severidade = constraint.violationSeverity(plan, context);
            double peso = constraint.getPenaltyWeight();
            double contribuicao = peso * severidade;
            score -= contribuicao;
            termos.add(new FitnessBreakdown.Term(constraint.name(),
                    FitnessBreakdown.TermKind.CONSTRAINT, severidade, peso, -contribuicao));
        }

        double bounded = Math.clamp(score, 0.0, 1.0);

        // 3. Multiplicative penalties.
        List<FitnessBreakdown.Penalty> fatores = new ArrayList<>();
        double finalFitness = bounded;
        double produto = 1.0;
        for (FitnessPenalty penalty : penalties) {
            double fator = penalty.calculatePenaltyFactor(plan, context);
            finalFitness *= fator;
            produto *= fator;
            fatores.add(new FitnessBreakdown.Penalty(penalty.name(), fator));
        }

        return new FitnessBreakdown(termos, fatores, score, bounded, produto, finalFitness);
    }
}

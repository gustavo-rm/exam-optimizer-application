package com.ia.project.dynamicstudyplanner.ga.config;

import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MandatoryReviewConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.constraint.MinimumDaysConstraint;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.CognitiveLoadObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.RetentionObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.ScoreGainObjective;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.DropoutRiskPenalty;
import com.ia.project.dynamicstudyplanner.ga.fitness.penalty.FatigueAndSustainabilityPenalty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * One fitness composition per path, declared here rather than assembled by the container.
 *
 * <h2>Why these are declared and not autowired</h2>
 *
 * {@code FitnessEvaluator} used to be a {@code @Component} whose term lists Spring filled with every
 * matching bean. That is why there could only ever be one aggregate: the container has no way to
 * know that a term is meaningless for a particular caller. It also meant the <b>order</b> of the
 * terms — and therefore the order of a floating-point sum — was classpath-scan order, which nothing
 * pinned.
 *
 * <p>Declaring both compositions fixes both problems at once: each path states its own terms, and
 * the order is written down. The concurso list below is in the same order
 * {@code GaResultadoInalteradoTest} constructs by hand, so the order the reference signatures were
 * measured against is now the order production uses.
 *
 * <h2>Only the concurso composition is declared here</h2>
 *
 * The SINAPSE one lives with its adapter, in {@code sinapse.SinapseFitnessConfig}. That is the point
 * of "declared per path": a path states its own terms, next to the code that knows which data it
 * has. It is also what keeps the dependency one-way — {@code sinapse} depends on {@code ga}, and
 * declaring the SINAPSE composition here would point {@code ga} back at {@code sinapse} and close a
 * module cycle that {@code ModuleBoundaryTest} refuses.
 *
 * @see FitnessComposition for why an inert term is worse than an absent one
 */
@Configuration
public class FitnessCompositionConfig {

    /** The published name of the concurso path in {@code FitnessBreakdown}. */
    public static final String CONCURSO_PATH = "concurso";

    /**
     * The concurso composition: every term, at the weights their own classes declare.
     *
     * <p>This is the path that has the data. {@code StudentProfileDto} requires the psychological
     * state and the self-assessed gaps, the API validates them, and both multiplicative penalties
     * therefore have something to read — in the macro path they still return {@code 1.0} because
     * they need a {@code TacticalStudyPlan}, which is a separate limitation recorded on
     * {@code FitnessPenalty} and not a missing input.
     */
    @Bean
    public FitnessComposition concursoFitnessComposition(
            ScoreGainObjective scoreGain,
            RetentionObjective retention,
            CognitiveLoadObjective cognitiveLoad,
            MinimumDaysConstraint minimumDays,
            MandatoryReviewConstraint mandatoryReview,
            DropoutRiskPenalty dropoutRisk,
            FatigueAndSustainabilityPenalty fatigue) {

        return new FitnessComposition(CONCURSO_PATH,
                List.of(scoreGain, retention, cognitiveLoad),
                List.of(minimumDays, mandatoryReview),
                List.of(dropoutRisk, fatigue));
    }

    /**
     * The evaluator the concurso path injects.
     *
     * <p>{@code @Primary} so that {@code EvolutionContextAssembler}, which asks for a
     * {@code FitnessEvaluator} by type and predates the existence of a second one, keeps resolving
     * to the path it has always used. The SINAPSE engine asks for its composition by name instead.
     */
    @Bean
    @Primary
    public FitnessEvaluator concursoFitnessEvaluator(FitnessComposition concursoFitnessComposition) {
        return concursoFitnessComposition.evaluator();
    }
}

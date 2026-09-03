package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * O3 — how much of the syllabus is still remembered on exam day (Ebbinghaus).
 *
 * <pre>
 *   O3(plan)   = SUM_s  retentionWeight(s) * coverage(s)
 *   coverage(s)= min(1, days_s / requiredSessions(s))
 *   requiredSessions(s) = planningHorizonDays / tau(s)
 * </pre>
 *
 * Bounded in [0,1] because the retention weights sum to 1 and every coverage term is capped at 1.
 * Full rationale, weight and citations: {@code docs/revisao-ag/05-fitness-function.md}.
 * <p>
 * The weights are <b>tempered</b> rather than equal to the exam-value weights O1 uses — see
 * {@code EvolutionContext.temper}. While both objectives shared one weighting they agreed to starve
 * the same subjects, which is what made the fitness anti-correlated with retention above an
 * effective importance dispersion of about 30:1
 * ({@code docs/revisao-ag/06-regime-alta-carga.md}).
 *
 * <h2>What it models</h2>
 *
 * Ebbinghaus's forgetting curve, {@code R = exp(-t/S)}, says recall decays with the time since the
 * last review, and spaced repetition counters it by revisiting material before recall falls too far.
 * Recall reaches {@code e^-1} after exactly one stability interval, so sessions spaced further apart
 * than {@code tau} let the subject slip below the threshold at which
 * {@code HybridRetentionEngine} declares a review overdue. Over a horizon of {@code H} days a
 * subject therefore needs about {@code H / tau} sessions to stay current, and this objective scores
 * the importance-weighted fraction of the syllabus that gets them.
 *
 * <h2>Why it is capped, and why that matters</h2>
 *
 * The cap is what makes the term concave, and concavity is what makes it reward spreading. Once a
 * subject has enough sessions to stay above the threshold, further days on it earn nothing here, so
 * the marginal value moves to whichever subject is still under-covered. That is the direct
 * counterweight to concentration — and concentration is precisely the mechanism by which the old
 * fitness ended up <em>anti-correlated</em> with retention: docs/revisao-ag/03-validacao.md §5
 * measured Spearman between fitness and retention at -0.83 to -0.94 across the instances where the
 * business metric could discriminate. A term whose reward saturates cannot produce that inversion.
 *
 * <h2>Honest limits</h2>
 *
 * This is a <b>mean-field approximation, not spaced repetition</b>. The macro chromosome is
 * {@code Map<Subject, Integer>} — a count of days with no position in the calendar and no order
 * (docs/revisao-ag/01-auditoria-fitness.md §3.3) — so the objective can only reason about how many
 * sessions a subject gets, assuming the scheduler spreads them roughly evenly across the horizon,
 * which is what {@code StudyScheduleGenerator} does. It cannot express <em>when</em> a review
 * happens, cannot model expanding intervals, and must not be described as implementing SM-2 or
 * spaced repetition proper. Doing that faithfully requires a time-indexed encoding, which is a
 * separate decision recorded in {@code docs/revisao-ag/02-formulacao.md}.
 */
@Component
public class RetentionObjective implements FitnessObjective {

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        Map<Subject, Double> importance = context.retentionWeights();
        Map<Subject, Double> requiredPerSubject = context.requiredSessionsPerSubject();
        int horizon = context.planningHorizonDays();

        double score = 0.0;
        for (Map.Entry<Subject, Integer> entry : plan.getDaysPerSubject().entrySet()) {
            Subject subject = entry.getKey();
            int days = entry.getValue();

            double weight = importance.getOrDefault(subject, 0.0);
            // Pre-calculado uma vez por execucao no contexto (achado F4). A busca com retorno para
            // o calculo direto cobre a disciplina que esteja no plano e nao no edital — caso em que
            // o peso e 0.0 e o termo nao contribui, mas o divisor ainda precisa ser valido.
            Double emCache = requiredPerSubject.get(subject);
            double required = emCache != null ? emCache : LearningModel.requiredSessions(subject, horizon);
            double coverage = Math.min(1.0, days / required);

            score += weight * coverage;
        }
        return score;
    }

    @Override
    public double getWeight() {
        return FitnessWeights.RETENTION;
    }
}

package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates all the contextual information required for a single evolution step.
 * This avoids "parameter drilling" by bundling related parameters into one object.
 * <p>
 * The fitness terms that consume these fields, their formulas and the reasoning behind their
 * weights are documented in {@code docs/revisao-ag/05-fitness-function.md}. Adding a field here
 * because a new fitness term needs it means that document has to be updated too.
 *
 * @param importanceScores        Raw personalised importance per subject, in the exam's own scoring
 *                                units. Kept because the scheduler and the baselines still rank by
 *                                it; the fitness itself uses {@link #normalizedImportance}.
 * @param normalizedImportance    Importance projected onto the simplex, so the values sum to 1.
 *                                See {@link #normalize}.
 * @param retentionWeights        Importance tempered by {@link #RETENTION_TEMPERING} and
 *                                renormalised. Flatter than {@link #normalizedImportance}; see
 *                                {@link #temper} for why retention is not weighted by exam value.
 * @param minimumDaysPerSubject   Coverage floor per subject, from {@code BaselineCalculator}.
 * @param studentState            Self-reported stress, fatigue and motivation. Enters the fitness
 *                                indirectly, through the daily cognitive-load budget.
 * @param fitnessEvaluator        The configured fitness pipeline.
 * @param retentionProfile        Review history. Empty in the macro path.
 * @param planStartDate           First day of the plan.
 * @param engagementProfile       Behavioural history. Baseline in the macro path.
 * @param planningHorizonDays     Calendar days between the plan start and the exam. Drives the
 *                                spacing estimate in the retention objective.
 * @param hoursPerStudyDay        Study hours a single plan day represents, derived from the
 *                                student's weekly availability.
 * @param maxDailyCognitiveLoad   Sustainable daily load budget from {@code CognitiveLoadCalculator}.
 *                                Already reflects the student's psychological state.
 */
public record EvolutionContext(
        Map<Subject, Double> importanceScores,
        Map<Subject, Double> normalizedImportance,
        Map<Subject, Double> retentionWeights,
        Map<Subject, Double> requiredSessionsPerSubject,
        Map<Subject, Integer> minimumDaysPerSubject,
        StudentState studentState,
        FitnessEvaluator fitnessEvaluator,
        RetentionProfile retentionProfile,
        LocalDate planStartDate,
        EngagementProfile engagementProfile,
        int planningHorizonDays,
        int hoursPerStudyDay,
        int maxDailyCognitiveLoad
) {

    /**
     * Inicia a construção de um contexto.
     *
     * <h2>Por que existe um construtor passo a passo</h2>
     *
     * Até a etapa 03c este contexto era montado por um método fábrica com <b>dez parâmetros
     * posicionais</b>, quatro deles objetos anuláveis. Era o maior custo de manutenção medido no
     * repositório ({@code docs/qualidade/03-diagnostico-estrutura.md}, achado E1): a análise de
     * co-mudança sobre 58 commits apontou {@code EvolutionContext} e {@code StudyOptimizerService}
     * como o par que mais muda junto, e o motivo era este — acrescentar um campo obrigava a tocar em
     * nove locais de chamada.
     *
     * <p>O sintoma mais visível estava nos testes, que precisavam escrever
     * {@code of(Map.of(), Map.of(), null, null, null, null, null, 180, 4, 20)}: cinco {@code null}
     * consecutivos, em que trocar dois argumentos de lugar compilava sem erro.
     *
     * <p>Com o construtor passo a passo, cada valor é nomeado no ponto de uso, os campos que só
     * existem no caminho tático podem simplesmente ser omitidos, e um campo novo não quebra nenhum
     * chamador existente. Decisão registrada em
     * {@code docs/adr/0004-construtor-passo-a-passo-do-contexto.md}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Construtor passo a passo do {@link EvolutionContext}.
     *
     * <p>Cinco valores são <b>obrigatórios</b>, porque todo caminho de execução os fornece:
     * {@link #importanceScores}, {@link #minimumDaysPerSubject}, {@link #planningHorizonDays},
     * {@link #hoursPerStudyDay} e {@link #maxDailyCognitiveLoad}. Omitir qualquer um faz
     * {@link #build()} falhar dizendo qual falta.
     *
     * <p>Os cinco restantes são <b>opcionais</b> e valem {@code null} quando omitidos, que é
     * exatamente o que os chamadores do caminho macro passavam antes. Não há mudança de
     * comportamento: {@code studentState}, {@code fitnessEvaluator}, {@code retentionProfile},
     * {@code planStartDate} e {@code engagementProfile} continuam podendo ser nulos, e os
     * consumidores continuam guardando contra isso.
     */
    public static final class Builder {

        private Map<Subject, Double> importanceScores;
        private Map<Subject, Integer> minimumDaysPerSubject;
        private StudentState studentState;
        private FitnessEvaluator fitnessEvaluator;
        private RetentionProfile retentionProfile;
        private LocalDate planStartDate;
        private EngagementProfile engagementProfile;
        private Integer planningHorizonDays;
        private Integer hoursPerStudyDay;
        private Integer maxDailyCognitiveLoad;

        private Builder() {
        }

        /** Obrigatório. Importância personalizada bruta por disciplina, nas unidades do edital. */
        public Builder importanceScores(Map<Subject, Double> importanceScores) {
            this.importanceScores = importanceScores;
            return this;
        }

        /** Obrigatório. Piso de cobertura por disciplina, vindo do {@code BaselineCalculator}. */
        public Builder minimumDaysPerSubject(Map<Subject, Integer> minimumDaysPerSubject) {
            this.minimumDaysPerSubject = minimumDaysPerSubject;
            return this;
        }

        /** Obrigatório. Dias de calendário entre o início do plano e a prova. */
        public Builder planningHorizonDays(int planningHorizonDays) {
            this.planningHorizonDays = planningHorizonDays;
            return this;
        }

        /** Obrigatório. Horas de estudo que um dia de plano representa. */
        public Builder hoursPerStudyDay(int hoursPerStudyDay) {
            this.hoursPerStudyDay = hoursPerStudyDay;
            return this;
        }

        /** Obrigatório. Orçamento diário sustentável de carga cognitiva. */
        public Builder maxDailyCognitiveLoad(int maxDailyCognitiveLoad) {
            this.maxDailyCognitiveLoad = maxDailyCognitiveLoad;
            return this;
        }

        /** Opcional. Estresse, fadiga e motivação autodeclarados. */
        public Builder studentState(StudentState studentState) {
            this.studentState = studentState;
            return this;
        }

        /** Opcional. O pipeline de fitness configurado. */
        public Builder fitnessEvaluator(FitnessEvaluator fitnessEvaluator) {
            this.fitnessEvaluator = fitnessEvaluator;
            return this;
        }

        /** Opcional. Histórico de revisões; vazio no caminho macro. */
        public Builder retentionProfile(RetentionProfile retentionProfile) {
            this.retentionProfile = retentionProfile;
            return this;
        }

        /** Opcional. Primeiro dia do plano. */
        public Builder planStartDate(LocalDate planStartDate) {
            this.planStartDate = planStartDate;
            return this;
        }

        /** Opcional. Histórico comportamental; linha de base no caminho macro. */
        public Builder engagementProfile(EngagementProfile engagementProfile) {
            this.engagementProfile = engagementProfile;
            return this;
        }

        /**
         * Monta o contexto, derivando a importância normalizada e os pesos de retenção uma única
         * vez — a fitness é avaliada até meio milhão de vezes por requisição e não pode recalcular
         * isso a cada chamada.
         *
         * @throws IllegalStateException se algum valor obrigatório não tiver sido informado; a
         *                               mensagem nomeia quais faltam
         */
        public EvolutionContext build() {
            List<String> faltando = new ArrayList<>();
            if (importanceScores == null) {
                faltando.add("importanceScores");
            }
            if (minimumDaysPerSubject == null) {
                faltando.add("minimumDaysPerSubject");
            }
            if (planningHorizonDays == null) {
                faltando.add("planningHorizonDays");
            }
            if (hoursPerStudyDay == null) {
                faltando.add("hoursPerStudyDay");
            }
            if (maxDailyCognitiveLoad == null) {
                faltando.add("maxDailyCognitiveLoad");
            }
            if (!faltando.isEmpty()) {
                throw new IllegalStateException(
                        "EvolutionContext incompleto: falta informar " + String.join(", ", faltando));
            }

            Map<Subject, Double> normalized = normalize(importanceScores);
            return new EvolutionContext(
                    importanceScores,
                    normalized,
                    temper(normalized),
                    requiredSessions(importanceScores.keySet(), planningHorizonDays),
                    minimumDaysPerSubject,
                    studentState,
                    fitnessEvaluator,
                    retentionProfile,
                    planStartDate,
                    engagementProfile,
                    planningHorizonDays,
                    hoursPerStudyDay,
                    maxDailyCognitiveLoad
            );
        }
    }

    /**
     * Pré-calcula, uma vez por execução, quantas sessões cada disciplina exige.
     *
     * <h2>Por que isto está aqui e não no objetivo de fitness</h2>
     *
     * {@code LearningModel.requiredSessions(disciplina, horizonte)} depende apenas da carga
     * cognitiva da disciplina e do horizonte de planejamento — <b>os dois fixos durante toda a
     * evolução</b>. Era, ainda assim, chamada uma vez por disciplina, por indivíduo, por geração.
     *
     * <p>Medido no achado F4 de {@code docs/qualidade/05-diagnostico-performance.md}:
     * <b>12.012.000 chamadas</b> no pior caso (500 indivíduos × 1000 gerações × 24 disciplinas)
     * para <b>24 resultados distintos</b>, a 17,29 ns cada — <b>208 ms</b>, cerca de 9 % do tempo
     * do algoritmo.
     *
     * <h2>Estratégia de invalidação</h2>
     *
     * O cache vive <b>dentro do contexto</b>, e o contexto é criado uma vez por requisição e nunca
     * alterado. Ele nasce e morre com a otimização, exatamente como {@code normalizedImportance} e
     * {@code retentionWeights}, que já seguiam este padrão.
     *
     * <p>Essa escolha é deliberada e vale registrar o que ela evita: um cache <i>estático</i> em
     * {@code LearningModel} seria mais fácil de escrever e seria um defeito. As chaves
     * ({@code Subject}, horizonte) vêm da requisição, então o mapa cresceria sem limite ao longo da
     * vida do processo, e um edital com a mesma disciplina sob outro horizonte leria valor de
     * outra requisição. Amarrar o cache ao ciclo de vida do dado que o originou dispensa política
     * de expiração: não há como ficar obsoleto aquilo que morre junto com a pergunta.
     */
    private static Map<Subject, Double> requiredSessions(Set<Subject> subjects, int planningHorizonDays) {
        Map<Subject, Double> porDisciplina = new HashMap<>(subjects.size() * 2);
        for (Subject subject : subjects) {
            porDisciplina.put(subject, LearningModel.requiredSessions(subject, planningHorizonDays));
        }
        return Collections.unmodifiableMap(porDisciplina);
    }

    /**
     * Projects raw importance onto the unit simplex: every value is divided by the total, so the
     * weights sum to 1 and become dimensionless.
     * <p>
     * This is the fix for the dominance problem measured in
     * {@code docs/revisao-ag/01-auditoria-fitness.md} §2.1.4. Raw importance is
     * {@code questionCount x axisWeight x knowledgeGap}, and the API's own validation limits allow
     * two subjects in the same sum to differ by a factor of 250,000. Under a plain sum that makes
     * the allocation winner-take-all: the marginal gain of the dominant subject stays above every
     * other subject's for the entire budget, and only the minimum-days floor keeps the rest of the
     * syllabus alive. Normalising removes the payload's ability to set the scale and makes fitness
     * values comparable between different exams, which is what allows the benchmark harness to
     * track quality over time at all.
     *
     * @param raw importance per subject, in the exam's scoring units
     * @return importance summing to 1; a uniform distribution when every input is zero or absent,
     *         which happens only for a degenerate payload in which no subject scores any points
     */
    public static Map<Subject, Double> normalize(Map<Subject, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        double total = raw.values().stream()
                .mapToDouble(v -> Math.max(0.0, v == null ? 0.0 : v))
                .sum();

        Map<Subject, Double> normalized = new HashMap<>(raw.size());
        if (total <= 0.0) {
            double uniform = 1.0 / raw.size();
            for (Subject subject : raw.keySet()) {
                normalized.put(subject, uniform);
            }
            return Collections.unmodifiableMap(normalized);
        }

        for (Map.Entry<Subject, Double> entry : raw.entrySet()) {
            double value = entry.getValue() == null ? 0.0 : Math.max(0.0, entry.getValue());
            normalized.put(entry.getKey(), value / total);
        }
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * Exponent applied to the normalised importance to obtain the retention weights.
     * <p>
     * {@code 0.5} is the tempered midpoint between uniform weighting ({@code 0}) and full
     * exam-value weighting ({@code 1}). Under it, a subject worth 100x another on the exam is
     * weighted 10x for retention purposes.
     */
    public static final double RETENTION_TEMPERING = 0.5;

    /**
     * Flattens the importance distribution for the retention objective.
     * <p>
     * <b>Why retention is not weighted by exam value.</b> O1 asks "how many exam points can this plan
     * earn?", which is correctly weighted by what each subject is worth. O3 asks a different
     * question: "how much of what was studied survives to exam day?" The cost of forgetting is not
     * proportional to exam value — forgetting a low-weight subject wastes the study days already
     * spent on it, and those days cost the student the same regardless of the subject's weight. So
     * the natural weighting for retention is flatter than the weighting for score.
     * <p>
     * <b>What it fixes.</b> While both objectives used the identical weight, they agreed to starve
     * the same subjects, and nothing in the fitness pushed back. The controlled sweep in
     * {@code docs/revisao-ag/06-regime-alta-carga.md} showed the consequence: above an effective
     * importance dispersion of roughly 30:1, the correlation between fitness and predicted retention
     * turns negative and deepens with dispersion — maximising the fitness made retention worse.
     * Tempering gives the retention term a weighting of its own, so it can dissent.
     */
    public static Map<Subject, Double> temper(Map<Subject, Double> normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Map.of();
        }

        Map<Subject, Double> tempered = new HashMap<>(normalized.size());
        double total = 0.0;
        for (Map.Entry<Subject, Double> entry : normalized.entrySet()) {
            double value = Math.pow(Math.max(0.0, entry.getValue()), RETENTION_TEMPERING);
            tempered.put(entry.getKey(), value);
            total += value;
        }

        if (total <= 0.0) {
            double uniform = 1.0 / normalized.size();
            normalized.keySet().forEach(s -> tempered.put(s, uniform));
            return Collections.unmodifiableMap(tempered);
        }

        final double sum = total;
        tempered.replaceAll((subject, value) -> value / sum);
        return Collections.unmodifiableMap(tempered);
    }

}

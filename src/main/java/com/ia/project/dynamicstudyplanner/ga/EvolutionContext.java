package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.PlanningItemIndex;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * @param importanceScores        Raw personalised importance per planning item, in the exam's own scoring
 *                                units. Kept because the scheduler and the baselines still rank by
 *                                it; the fitness itself uses {@link #normalizedImportance}.
 * @param normalizedImportance    Importance projected onto the simplex, so the values sum to 1.
 *                                See {@link #normalize}.
 * @param retentionWeights        Importance tempered by {@link #RETENTION_TEMPERING} and
 *                                renormalised. Flatter than {@link #normalizedImportance}; see
 *                                {@link #temper} for why retention is not weighted by exam value.
 * @param minimumDaysPerItem   Coverage floor per item, from {@code SinapseMinimumDays}.
 * @param softPrerequisitesPerItem Order preferences per item, from {@code SoftPrerequisiteEdges};
 *                             {@code null} when the caller has none. Read only by
 *                             {@code SoftPrerequisiteOrderConstraint}.
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
 * @param maxDailyCognitiveLoad   Sustainable daily load budget, from {@code SinapseLoadBudget}.
 *                                Already reflects the student's psychological state.
 * @param geneVectors             Os mesmos dados por disciplina acima, projetados na ordem canônica
 *                                do cromossomo (pendência P18). É o que a evolução lê no caminho
 *                                quente; os mapas ficam para a fronteira e para os testes. Ver
 *                                {@link GeneVectors}.
 */
public record EvolutionContext(
        Map<PlanningItem, Double> importanceScores,
        Map<PlanningItem, Double> normalizedImportance,
        Map<PlanningItem, Double> retentionWeights,
        Map<PlanningItem, Double> requiredSessionsPerItem,
        Map<PlanningItem, Integer> minimumDaysPerItem,
        Map<PlanningItem, Set<PlanningItem>> softPrerequisitesPerItem,
        FitnessEvaluator fitnessEvaluator,
        RetentionProfile retentionProfile,
        LocalDate planStartDate,
        int planningHorizonDays,
        int hoursPerStudyDay,
        int maxDailyCognitiveLoad,
        GeneVectors geneVectors
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
     * {@link #importanceScores}, {@link #minimumDaysPerItem}, {@link #planningHorizonDays},
     * {@link #hoursPerStudyDay} e {@link #maxDailyCognitiveLoad}. Omitir qualquer um faz
     * {@link #build()} falhar dizendo qual falta.
     *
     * <p>Os cinco restantes são <b>opcionais</b> e valem {@code null} quando omitidos, que é
     * exatamente o que os chamadores do caminho macro passavam antes. Não há mudança de
     * comportamento: {@code fitnessEvaluator}, {@code retentionProfile} e {@code planStartDate}
     * continuam podendo ser nulos, e os consumidores continuam guardando contra isso.
     *
     * <p>{@code studentState} e {@code engagementProfile} saíram em EOA-4b, com os dois termos de
     * fitness que os liam. Os dois vinham de {@code StudentProfileDto.state} e eram preenchidos só
     * pelo caminho de concurso; a plataforma não coleta estado psicológico nem histórico de
     * engajamento, e a decisão de coletá-los é jurídica antes de ser de engenharia. Ver
     * {@code sinapse/SinapseFitnessConfig}.
     */
    public static final class Builder {

        private Map<PlanningItem, Double> importanceScores;
        private List<PlanningItem> items;
        private Map<PlanningItem, Integer> minimumDaysPerItem;
        private Map<PlanningItem, Set<PlanningItem>> softPrerequisitesPerItem;
        private FitnessEvaluator fitnessEvaluator;
        private RetentionProfile retentionProfile;
        private LocalDate planStartDate;
        private Integer planningHorizonDays;
        private Integer hoursPerStudyDay;
        private Integer maxDailyCognitiveLoad;

        private Builder() {
        }

        /** Obrigatório. Importância bruta por item, na escala da estratégia que a produziu. */
        public Builder importanceScores(Map<PlanningItem, Double> importanceScores) {
            this.importanceScores = importanceScores;
            return this;
        }

        /**
         * Opcional. A ordem dos itens, que passa a ser <b>a ordem dos genes do cromossomo</b> (pendência P18).
         *
         * <p>Omitir cai na ordem de iteração de {@link #importanceScores}, que é o que alguns
         * testes fazem. A produção informa explicitamente, com a lista de tópicos do pedido: é a
         * diferença entre uma ordem declarada pelo chamador e uma ordem que vem de um detalhe
         * interno de {@code HashMap}, livre para mudar numa atualização de JDK. Ver
         * {@link PlanningItemIndex}.
         *
         * @param items os itens na ordem em que serão planejados
         * @return este construtor
         */
        public Builder items(List<PlanningItem> items) {
            this.items = items;
            return this;
        }

        /** Obrigatório. Piso de cobertura por item, vindo de {@code SinapseMinimumDays}. */
        public Builder minimumDaysPerItem(Map<PlanningItem, Integer> minimumDaysPerItem) {
            this.minimumDaysPerItem = minimumDaysPerItem;
            return this;
        }

        /** Obrigatório. Dias de calendário do horizonte, contando o primeiro e o último. */
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

        /**
         * Opcional. Preferências de ordem: para cada item, os itens que idealmente o precedem.
         *
         * <p>Omitir deixa o mapa nulo, e {@code SoftPrerequisiteOrderConstraint} devolve zero — que
         * é o que um chamador sem arestas SOFT quer dizer. Preencher com um mapa vazio significa a
         * mesma coisa e é igualmente aceito: "não há preferência" e "não sei" não se distinguem
         * aqui, porque nenhuma das duas permite violar uma.
         *
         * <p>É um mapa por item e, portanto, sujeito à regra de ordenação deste repositório: quem o
         * informa usa {@code LinkedHashMap}/{@code TreeMap}, nunca {@code Map.copyOf}. A soma de
         * gravidades do termo é de ponto flutuante e a ordem entra na conta.
         */
        public Builder softPrerequisitesPerItem(
                Map<PlanningItem, Set<PlanningItem>> softPrerequisitesPerItem) {

            this.softPrerequisitesPerItem = softPrerequisitesPerItem;
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
            if (minimumDaysPerItem == null) {
                faltando.add("minimumDaysPerItem");
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

            Map<PlanningItem, Double> normalized = normalize(importanceScores);
            Map<PlanningItem, Double> tempered = temper(normalized);
            PlanningItemIndex index = PlanningItemIndex.of(
                    items != null ? items : importanceScores.keySet());
            return new EvolutionContext(
                    importanceScores,
                    normalized,
                    tempered,
                    requiredSessions(importanceScores.keySet(), planningHorizonDays),
                    minimumDaysPerItem,
                    softPrerequisitesPerItem,
                    fitnessEvaluator,
                    retentionProfile,
                    planStartDate,
                    planningHorizonDays,
                    hoursPerStudyDay,
                    maxDailyCognitiveLoad,
                    new GeneVectors(index, minimumDaysPerItem, normalized, tempered, planningHorizonDays)
            );
        }
    }

    /**
     * Pré-calcula, uma vez por execução, quantas sessões cada item exige.
     *
     * <h2>Por que isto está aqui e não no objetivo de fitness</h2>
     *
     * {@code LearningModel.requiredSessions(banda, horizonte)} depende apenas da banda de
     * dificuldade do item e do horizonte de planejamento — <b>os dois fixos durante toda a
     * evolução</b>. Era, ainda assim, chamada uma vez por item, por indivíduo, por geração.
     *
     * <p>Medido no achado F4 de {@code docs/qualidade/05-diagnostico-performance.md}:
     * <b>12.012.000 chamadas</b> no pior caso (500 indivíduos × 1000 gerações × 24 itens)
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
     * ({@code PlanningItem}, horizonte) vêm da requisição, então o mapa cresceria sem limite ao longo da
     * vida do processo, e um plano com o mesmo item sob outro horizonte leria valor de
     * outra requisição. Amarrar o cache ao ciclo de vida do dado que o originou dispensa política
     * de expiração: não há como ficar obsoleto aquilo que morre junto com a pergunta.
     */
    private static Map<PlanningItem, Double> requiredSessions(Set<PlanningItem> items, int planningHorizonDays) {
        Map<PlanningItem, Double> porItem = new HashMap<>(items.size() * 2);
        for (PlanningItem item : items) {
            porItem.put(item, LearningModel.requiredSessions(item.difficultyBand(), planningHorizonDays));
        }
        return Collections.unmodifiableMap(porItem);
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
     * <h2>A ordem de iteração do mapa devolvido é aritmética, não cosmética</h2>
     *
     * Era um {@code HashMap}. Um {@link LinkedHashMap} custa o mesmo e <b>preserva a ordem de
     * {@code raw}</b>, e essa ordem entra numa conta: {@link #temper} soma
     * {@code Math.pow(peso, 0.5)} percorrendo este mapa, e soma de ponto flutuante não é
     * associativa. Com {@code HashMap}, a ordem vinha dos códigos de espalhamento das chaves — e
     * {@code PlanningItem.hashCode()} não é o de {@code Subject.hashCode()}. Medido: com 24 itens,
     * <b>todos</b> os pesos de retenção saíam 1 ulp deslocados da execução anterior à migração, e a
     * fitness publicada com eles.
     *
     * <p>Declarar a ordem torna a soma independente do tipo da chave — e também do detalhe interno
     * do {@code HashMap}, que o contrato de {@code java.util.Map} não especifica e que uma
     * atualização de JDK pode mudar. É o mesmo argumento que
     * {@code PlanningItemIndex} faz para a ordem dos genes, aplicado a um lugar onde ele tinha
     * passado despercebido. Com ele, as 18 assinaturas de referência do AG ficaram idênticas bit a
     * bit antes e depois da migração.
     *
     * @param raw importance per planning item, in the exam's scoring units
     * @return importance summing to 1, iterating in {@code raw}'s order; a uniform distribution
     *         when every input is zero or absent, which happens only for a degenerate payload in
     *         which no item scores any points
     */
    public static Map<PlanningItem, Double> normalize(Map<PlanningItem, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        double total = raw.values().stream()
                .mapToDouble(v -> Math.max(0.0, v == null ? 0.0 : v))
                .sum();

        Map<PlanningItem, Double> normalized = new LinkedHashMap<>();
        if (total <= 0.0) {
            double uniform = 1.0 / raw.size();
            for (PlanningItem item : raw.keySet()) {
                normalized.put(item, uniform);
            }
            return Collections.unmodifiableMap(normalized);
        }

        for (Map.Entry<PlanningItem, Double> entry : raw.entrySet()) {
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
    public static Map<PlanningItem, Double> temper(Map<PlanningItem, Double> normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Map.of();
        }

        Map<PlanningItem, Double> tempered = new HashMap<>(normalized.size());
        double total = 0.0;
        for (Map.Entry<PlanningItem, Double> entry : normalized.entrySet()) {
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

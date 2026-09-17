package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.SubjectIndex;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;

import java.util.Map;

/**
 * Os dados por disciplina que a evolução consulta a cada gene, já projetados em vetores alinhados à
 * ordem canônica do cromossomo.
 *
 * <h2>Por que isto existe (pendência P18)</h2>
 *
 * Piso de dias mínimos, importância normalizada, peso de retenção e sessões exigidas são
 * <b>constantes durante toda a otimização</b> — dependem do edital e do perfil do aluno, não do
 * indivíduo sendo avaliado. Ainda assim eram lidos de {@code Map<Subject, ...>} uma vez por gene,
 * por indivíduo, por geração: no pior caso admitido pela API, dezenas de milhões de cálculos de
 * hash para reler os mesmos vinte e quatro números.
 *
 * <p>Projetados uma vez aqui, viram leitura de posição de vetor. É a mesma ideia que já valia para
 * {@code requiredSessionsPerSubject} desde o achado F4 — memorizar o que não muda — levada até o
 * fim: além de não recalcular, também não reprocurar.
 *
 * <h2>Alinhamento</h2>
 *
 * Todos os vetores seguem {@link #index()}. A posição {@code i} de qualquer um deles fala da mesma
 * disciplina que a posição {@code i} dos genes de um plano alinhado ao mesmo índice. Quem tiver um
 * plano de outra origem deve alinhá-lo antes, com
 * {@code StudyPlan.genesAlignedTo(SubjectIndex)}.
 *
 * <p>Imutável na prática: os vetores são criados aqui e nunca saem da classe.
 */
public final class GeneVectors {

    /** Piso implícito quando o contexto não declara mínimo para uma disciplina. */
    private static final int DEFAULT_MINIMUM_DAYS = 1;

    private final SubjectIndex index;
    private final int[] minimumDays;
    private final double[] normalizedImportance;
    private final double[] retentionWeights;
    private final double[] requiredSessions;

    /**
     * Projeta os mapas do contexto sobre a ordem canônica.
     *
     * @param index                 a ordem dos genes; nunca {@code null}
     * @param minimumDaysPerSubject pisos por disciplina; ausência vale {@value #DEFAULT_MINIMUM_DAYS}
     * @param normalizedImportance  importância projetada no simplex; ausência vale 0
     * @param retentionWeights      importância temperada para retenção; ausência vale 0
     * @param planningHorizonDays   horizonte de planejamento, para as sessões exigidas
     */
    GeneVectors(SubjectIndex index,
                Map<Subject, Integer> minimumDaysPerSubject,
                Map<Subject, Double> normalizedImportance,
                Map<Subject, Double> retentionWeights,
                int planningHorizonDays) {
        this.index = index;
        this.minimumDays = index.projectInts(minimumDaysPerSubject, DEFAULT_MINIMUM_DAYS);
        this.normalizedImportance = index.projectDoubles(normalizedImportance, 0.0);
        this.retentionWeights = index.projectDoubles(retentionWeights, 0.0);

        this.requiredSessions = new double[index.size()];
        for (int i = 0; i < this.requiredSessions.length; i++) {
            this.requiredSessions[i] = LearningModel.requiredSessions(index.subject(i), planningHorizonDays);
        }
    }

    /** @return a ordem canônica a que todos os vetores estão alinhados */
    public SubjectIndex index() {
        return index;
    }

    /** @return quantos genes tem um cromossomo desta otimização */
    public int size() {
        return minimumDays.length;
    }

    /**
     * @param posicao posição do gene
     * @return o piso de dias da disciplina nessa posição
     */
    public int minimumDays(int posicao) {
        return minimumDays[posicao];
    }

    /**
     * @param posicao posição do gene
     * @return a importância normalizada da disciplina nessa posição
     */
    public double normalizedImportance(int posicao) {
        return normalizedImportance[posicao];
    }

    /**
     * @param posicao posição do gene
     * @return o peso de retenção da disciplina nessa posição
     */
    public double retentionWeight(int posicao) {
        return retentionWeights[posicao];
    }

    /**
     * @param posicao posição do gene
     * @return quantas sessões a disciplina nessa posição exige no horizonte do plano
     */
    public double requiredSessions(int posicao) {
        return requiredSessions[posicao];
    }

    /**
     * Recusa um plano que tenha mais genes do que esta ordem descreve.
     *
     * <h2>Por que esta verificação existe</h2>
     *
     * Com o cromossomo indexado, a ordem dos genes é do <b>contexto</b>, não de cada plano: é o que
     * permite recombinar dois indivíduos posição a posição. A consequência é que um contexto montado
     * sem disciplinas — {@code importanceScores} vazio e nenhum {@code subjects()} informado —
     * descreve uma evolução <b>sem genes</b>, e os operadores devolveriam planos vazios em silêncio.
     *
     * <p>Uma comparação de tamanhos por operação genética é barata o bastante para não medir, e
     * troca um resultado errado e mudo por uma falha que diz o que fazer.
     *
     * @param plan o plano que o operador vai transformar
     * @throws IllegalStateException se o contexto descrever menos genes que o plano
     */
    public void requireCovers(StudyPlan plan) {
        if (plan.getIndex().size() > size()) {
            throw new IllegalStateException(
                    "O contexto da evolucao descreve " + size() + " gene(s), mas o plano tem "
                            + plan.getIndex().size() + ". Informe as disciplinas no contexto, com "
                            + "EvolutionContext.builder().subjects(...) ou importanceScores(...).");
        }
    }

    /**
     * Vetor de pisos, para o reparo de genes.
     *
     * <p>Devolve o vetor interno, sem cópia: ele é lido uma vez por volta do laço de reparo, e
     * copiá-lo por recombinação recolocaria o custo que esta classe existe para tirar. <b>Quem
     * recebe não altera.</b> O único chamador é {@code ChildGeneRepair}, que só lê.
     *
     * @return os pisos por posição, alinhados a {@link #index()}
     */
    public int[] minimumDaysVector() {
        return minimumDays;
    }
}

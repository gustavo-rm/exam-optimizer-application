package com.ia.project.dynamicstudyplanner.service.scheduler.strategy;

/**
 * As composições nomeadas de estratégias de alocação diária.
 *
 * <h2>Por que esta classe existe</h2>
 *
 * Até a etapa 03b a cadeia de produção era montada à mão em três lugares — o serviço de produção, o
 * medidor dos benchmarks e um teste de casos de borda —, cada um repetindo o mesmo aninhamento de
 * três decoradores. O medidor até documentava a réplica em comentário: <i>"exactly the chain
 * DynamicStudyPlannerService composes in production"</i>.
 *
 * <p>O risco não era estético. Trocar a ordem dos decoradores em produção deixaria as duas cópias
 * medindo e testando uma cadeia que o aluno não recebe — em silêncio, sem nenhuma falha
 * ({@code docs/qualidade/03-diagnostico-estrutura.md}, achado E2). Com uma única definição, mudar a
 * produção muda automaticamente o que os benchmarks medem e o que os testes verificam.
 *
 * <h2>Como a cadeia se lê</h2>
 *
 * Os decoradores se aplicam de dentro para fora:
 * <ol>
 *   <li>{@link InterleavedCriticalStrategy} decide <b>quais</b> disciplinas estudar no dia — as mais
 *       críticas, intercaladas;</li>
 *   <li>{@link CognitiveLoadBalancingStrategy} decide <b>quanto</b> cabe, podando o que exceder o
 *       orçamento diário de carga;</li>
 *   <li>{@link ReviewFocusedStrategy} decide se uma <b>revisão</b> entra antes do conteúdo novo.</li>
 * </ol>
 *
 * <p>Decisão registrada em {@code docs/adr/0003-composicao-de-producao-unica.md}.
 */
public final class AllocationChains {

    private AllocationChains() {
    }

    /**
     * A cadeia que o aluno recebe em produção.
     *
     * @param maxDailyCognitiveLoad orçamento diário de carga cognitiva, calculado por
     *                              {@code CognitiveLoadCalculator} a partir do perfil e do edital
     */
    public static AllocationStrategy production(int maxDailyCognitiveLoad) {
        return new ReviewFocusedStrategy(
                new CognitiveLoadBalancingStrategy(
                        new InterleavedCriticalStrategy(),
                        maxDailyCognitiveLoad));
    }

    /**
     * A cadeia de produção <b>sem o podador de carga</b>.
     *
     * <p>Existe para medição, não para entrega. O podador descarta blocos até caber no orçamento, o
     * que faria todo planejador reportar zero dias de sobrecarga e esconderia a diferença entre eles;
     * esta variante revela a carga que o plano macro realmente implica. Usada por
     * {@code benchmarks/.../MetricsCalculator}.
     */
    public static AllocationStrategy productionWithoutLoadPruning() {
        return new ReviewFocusedStrategy(new InterleavedCriticalStrategy());
    }
}

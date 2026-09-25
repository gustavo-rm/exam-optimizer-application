package com.ia.project.dynamicstudyplanner.sinapse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O adaptador SINAPSE não alcança nada que exija dado que a plataforma não envia.
 *
 * <h2>Por que a garantia é estrutural e não sobre valores</h2>
 *
 * O critério é "nenhum campo de {@code StudentProfileDto} é preenchido com constante no caminho
 * SINAPSE". Escrito como asserção sobre valores — "o nível de estresse não é 3.0" — seria fraco de
 * duas formas: passaria com {@code 2.9}, e só cobriria os campos que alguém se lembrasse de listar.
 *
 * <p>A forma forte é proibir o <b>alcance</b>. Se o adaptador nunca constrói
 * {@code StudentProfile}, {@code StudentState} ou {@code Exam}, e nunca chama as três calculadoras
 * que os exigem, então não existe campo a preencher e a questão não se coloca. Este teste lê os
 * {@code import} do pacote e reprova se qualquer um desses tipos aparecer.
 *
 * <h2>Por que cada tipo está na lista</h2>
 *
 * <ul>
 *   <li>{@code StudentProfile} exige lacunas de conhecimento autoavaliadas e estado psicológico. A
 *       plataforma não coleta nenhum dos dois.</li>
 *   <li>{@code CognitiveLoadCalculator} tolera a ausência: o valor médio de lacuna cai para
 *       {@code 3.0} e os modificadores de estado são descartados em silêncio. O orçamento sai com
 *       uma constante embutida e sem sinal de que algo faltou.</li>
 *   <li>{@code ImportanceCalculator} leva a lacuna para {@code importanceScores} — o peso do termo
 *       de maestria e, via {@code temper}, o de retenção.</li>
 *   <li>{@code BaselineCalculator} a leva para {@code minimumDaysPerItem}, que
 *       {@code MinimumDaysConstraint} subtrai a 0,50.</li>
 * </ul>
 *
 * <p>É esta última parte que torna o teste necessário em vez de zeloso: <b>uma única constante
 * inventada reponderaria os dois objetivos mais pesados e a restrição mais pesada de uma só vez</b>,
 * e cada um deles continuaria reportando um número. A regra que impede isso não é uma escolha de
 * valor, é não ter acesso ao tipo.
 *
 * <h2>O que o adaptador usa no lugar</h2>
 *
 * Importância de uma {@code ImportanceStrategy} escolhida por requisição, piso de dias derivado de
 * que todo tópico em escopo precisa de uma sessão ({@code SinapseEvolutionContexts}), e teto de
 * carga de disponibilidade e dificuldade média ({@code SinapseLoadBudget}).
 */
@DisplayName("Adaptador SINAPSE: isolamento do que exige dado ausente")
class SinapseAdapterIsolationTest {

    private static final Path ADAPTER =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner/sinapse");

    /**
     * Arquivos cujo {@code Map.copyOf} e consultado por {@code get} e nunca iterado.
     *
     * <p>Um mapa assim nao tem como levar a ordem embaralhada por execucao da JVM ate uma soma de
     * ponto flutuante. Acrescentar um arquivo aqui exige dize-lo no Javadoc dele — a lista e uma
     * afirmacao verificavel, nao uma valvula de escape.
     */
    private static final List<String> LOOKUP_ONLY_MAPS = List.of(
            "EffortTierBands.java", "ImportanceStrategies.java");

    /**
     * Os tipos que exigiam dado que a plataforma não envia, e que <b>não existem mais</b>.
     *
     * <h2>A proibição virou ausência em EOA-4b, e isso é mais forte</h2>
     *
     * Até EOA-4b esta lista alimentava uma varredura de {@code import} sobre {@code sinapse/}: o
     * adaptador não podia alcançar {@code StudentProfile}, {@code StudentState}, {@code Exam},
     * {@code Subject}, {@code EngagementProfile} nem as três calculadoras de concurso. A remoção do
     * caminho de concurso levou todos eles, e um tipo que não existe não pode ser importado por
     * ninguém — não só pelo adaptador.
     *
     * <p>O teste mudou de forma junto: em vez de varrer <i>imports</i>, ele confere que cada um
     * destes nomes continua ausente do classpath. Reintroduzir qualquer um é uma decisão, não um
     * detalhe de implementação — coletar estado psicológico autodeclarado vinculado a identidade,
     * com menores no piloto de ensino médio, é decisão sob a LGPD, e voltar a planejar em disciplina
     * de concurso desfaz EOA-4. Nos dois casos este teste é onde a conversa recomeça.
     */
    private static final List<String> REMOVED = List.of(
            "domain.StudentProfile",
            "domain.StudentState",
            "domain.Chronotype",
            "domain.exam.Exam",
            "domain.exam.Subject",
            "domain.exam.SubjectPlanningItemMapper",
            "domain.engagement.EngagementProfile",
            "domain.engagement.DropoutRiskAlgorithm",
            "domain.fatigue.FatigueAlgorithm",
            "service.calculation.CognitiveLoadCalculator",
            "service.calculation.ImportanceCalculator",
            "service.calculation.BaselineCalculator",
            "service.calculation.engagement.DropoutRiskPredictor",
            "service.calculation.fatigue.FatigueAndEnergyModel",
            "ga.fitness.objective.CognitiveLoadObjective",
            "ga.fitness.penalty.DropoutRiskPenalty",
            "ga.fitness.penalty.FatigueAndSustainabilityPenalty");

    @Test
    @DisplayName("os tipos que exigiam dado ausente continuam sem existir, no disco e no classpath")
    void osTiposQueExigiamDadoAusenteContinuamAusentes() {
        List<String> ressuscitados = new ArrayList<>();
        for (String name : REMOVED) {
            String fqn = "com.ia.project.dynamicstudyplanner." + name;
            if (Files.exists(Path.of("src/main/java/com/ia/project/dynamicstudyplanner",
                    name.replace('.', '/') + ".java"))) {
                ressuscitados.add(name + " (em disco)");
                continue;
            }
            try {
                Class.forName(fqn);
                ressuscitados.add(name + " (no classpath)");
            } catch (ClassNotFoundException esperado) {
                // Ausente, que e o estado correto.
            }
        }

        assertThat(ressuscitados)
                .as("""
                        Um tipo do caminho de concurso voltou a existir.

                        Se a intencao foi reaproveitar um termo que le estado do estudante: ele
                        pede StudentState ou EngagementProfile, e monta-los obriga a inventar o
                        estado psicologico. A plataforma nao o envia, e um valor neutro ali nao
                        suavizaria um termo — faria um termo inerte aparentar estar ativo, que e
                        pior que a ausencia dele. Coletar estado psicologico autodeclarado
                        vinculado a identidade e decisao sob a LGPD, com menores no piloto de
                        ensino medio, e nao e decisao de engenharia.

                        Se a intencao foi voltar a planejar em disciplina de concurso: isso desfaz
                        EOA-4, em que a unidade de planejamento passou a ser o PlanningItem.

                        Ver docs/SINAPSE_ADAPTER.md secao 2.""")
                .isEmpty();
    }

    @Test
    @DisplayName("nenhum mapa por item usa Map.copyOf: a ordem dele e sorteada por execucao da JVM")
    void nenhumMapaPorItemUsaMapCopyOf() throws IOException {
        // A ordem de iteracao de um mapa devolvido por Map.copyOf e embaralhada por uma semente
        // aleatoria POR EXECUCAO DA JVM — documentado como "unspecified and subject to change".
        // EvolutionContext.normalize soma os valores da importancia para dividir pelo total, e soma
        // de ponto flutuante nao e associativa: a ordem entra na conta. Com Map.copyOf, o mesmo
        // pedido com a mesma semente produzia planos diferentes em execucoes diferentes da JVM.
        //
        // Um teste de comportamento NAO pega isso: dentro de uma execucao a ordem e estavel, entao
        // rodar o mesmo pedido mil vezes no mesmo processo passa sempre. Foi encontrado por
        // PlanEngineDeterminismTest falhando 1 em 3 execucoes separadas. A unica guarda que
        // funciona e estrutural.
        //
        // As excecoes declaradas: mapas lidos por get, nunca iterados em ordem, cujo embaralhamento
        // por execucao da JVM nao alcanca aritmetica nenhuma. Cada um diz isso no proprio Javadoc.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ADAPTER)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (LOOKUP_ONLY_MAPS.contains(name)) {
                    continue;
                }
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.contains("Map.copyOf(") && !line.trim().startsWith("//")
                            && !line.trim().startsWith("*")) {
                        offenders.add(name + ": " + line.trim());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("Use Collections.unmodifiableMap sobre um LinkedHashMap. Map.copyOf da "
                        + "imutabilidade e tira a ordem declarada, e a ordem entra na soma de ponto "
                        + "flutuante que normaliza as importancias. Se este mapa e so consultado por "
                        + "get, diga isso no Javadoc e acrescente o arquivo a excecao acima.")
                .isEmpty();
    }

}

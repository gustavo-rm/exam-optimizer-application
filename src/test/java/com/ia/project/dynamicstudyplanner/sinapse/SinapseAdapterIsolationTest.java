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
 * Importância de {@code goals[].priority} ({@code TopicImportance}), piso de dias da definição de
 * que todo tópico em escopo precisa de uma sessão ({@code SinapseEvolutionContexts}), e teto de
 * carga de disponibilidade e dificuldade média ({@code SinapseLoadBudget}).
 */
@DisplayName("Adaptador SINAPSE: isolamento do que exige dado ausente")
class SinapseAdapterIsolationTest {

    private static final Path ADAPTER =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner/sinapse");

    /**
     * Os tipos que o caminho SINAPSE não pode alcançar, com o motivo no nome do teste que falha.
     *
     * <p>{@code StudentState} e as três calculadoras por exigirem dado ausente; {@code Exam} e
     * {@code Subject} porque são a modelagem de concurso e alcançá-los significaria que o núcleo
     * voltou a falar de disciplina em vez de item de planejamento.
     */
    private static final List<String> FORBIDDEN = List.of(
            "domain.StudentProfile",
            "domain.StudentState",
            "domain.exam.Exam",
            "domain.exam.Subject",
            "domain.engagement.EngagementProfile",
            "service.calculation.CognitiveLoadCalculator",
            "service.calculation.ImportanceCalculator",
            "service.calculation.BaselineCalculator");

    @Test
    @DisplayName("nenhuma classe do adaptador importa tipo que exija dado que a plataforma nao envia")
    void nenhumaClasseAlcancaOQueExigeDadoAusente() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ADAPTER)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : FORBIDDEN) {
                    if (source.contains("import com.ia.project.dynamicstudyplanner." + forbidden + ";")) {
                        violations.add(ADAPTER.relativize(file) + " -> " + forbidden);
                    }
                }
            }
        }

        assertThat(violations)
                .as("""
                        O adaptador SINAPSE passou a alcancar um tipo que exige dado que a
                        plataforma nao envia.

                        Se a intencao foi reaproveitar um calculo do caminho de concurso: ele vai
                        pedir um StudentProfile, e montar um obriga a inventar as lacunas de
                        conhecimento e o estado psicologico. Nenhuma das duas coisas e enviada, e
                        nenhuma cai apenas no orcamento de carga: a lacuna reponderaria a maestria,
                        a retencao E o piso de dias minimos. Uma constante ali nao suaviza um termo,
                        reponderaria a funcao inteira sem que nada parecesse errado.

                        Coletar estado psicologico autodeclarado vinculado a identidade e decisao
                        sob a LGPD, com menores no piloto de ensino medio, e nao e decisao de
                        engenharia.

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
        // EffortTierBands e a excecao declarada: seu mapa e lido por get e nunca iterado.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ADAPTER)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("EffortTierBands.java")) {
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

    @Test
    @DisplayName("a lista de tipos proibidos nao ficou obsoleta: todos ainda existem")
    void aListaNaoFicouObsoleta() {
        // Um tipo renomeado sairia da lista sem que ninguem notasse, e o teste passaria a nao
        // proteger nada. Cada entrada e conferida contra o disco.
        List<String> missing = FORBIDDEN.stream()
                .filter(name -> !Files.exists(Path.of(
                        "src/main/java/com/ia/project/dynamicstudyplanner",
                        name.replace('.', '/') + ".java")))
                .toList();

        assertThat(missing)
                .as("estes tipos proibidos nao existem mais: renomeie-os na lista ou remova-os, "
                        + "senao a proibicao deixou de valer em silencio")
                .isEmpty();
    }
}

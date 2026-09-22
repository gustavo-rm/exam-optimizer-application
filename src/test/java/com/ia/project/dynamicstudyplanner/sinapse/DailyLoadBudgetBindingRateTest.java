package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.PlanEngines;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mede se o teto de carga diária <b>morde</b>, em vez de assumir que importa.
 *
 * <h2>Por que esta medição decide se o termo fica</h2>
 *
 * Um termo pode estar na composição e nunca restringir nada: o teto fica tão acima da demanda que
 * {@code calculateReward} devolve 1,0 para todo plano que a busca considera. Na fitness publicada
 * isso é indistinguível de um termo que olhou e não achou problema — e é exatamente o que este
 * caminho existe para não fazer, entrando por outra porta.
 *
 * <p>Então a escolha entre manter e remover o termo é <b>medição, não opinião</b>. Se a taxa de "o
 * teto foi vinculante" fosse ~0 num conjunto razoável de instâncias, a decisão correta passaria a
 * ser removê-lo e renormalizar 0,50/0,30 para 0,625/0,375 — que é uma mudança de configuração
 * ({@code plan.fitness.sinapse.daily-load-budget}), não de código.
 *
 * <h2>Qual variável o conjunto varre, e por quê essa</h2>
 *
 * Depois de a demanda e o teto passarem a usar o <b>mesmo</b> número de horas
 * ({@link SinapseLoadBudget#of}), as horas se cancelam: o termo morde quando a dificuldade média
 * ponderada pela alocação excede a referência de dificuldade média. Então a variável que governa é a
 * <b>mistura de dificuldade do edital</b>, não o aperto do calendário — varrer o calendário mediria
 * o arredondamento, que era precisamente o defeito corrigido.
 *
 * <p>O resultado vai para a saída padrão para ser transcrito no PR, e as asserções travam as duas
 * pontas: o termo <b>consegue</b> morder num edital difícil e <b>não</b> morde num fácil. Um termo
 * que morde sempre é tão inútil quanto um que nunca morde — nenhum dos dois discrimina.
 */
@DisplayName("Teto de carga diaria: taxa de 'foi vinculante'")
class DailyLoadBudgetBindingRateTest {

    /** Uma instância do conjunto de medição. */
    private record Instance(String name, PlanRequest request) {
    }

    /**
     * Instâncias que variam a mistura de dificuldade sobre o mesmo calendário.
     *
     * <p>Dez dias de três horas em todas, e o que muda é a faixa de esforço dos quatro tópicos — de
     * todos {@code SHORT} a todos {@code EXTENDED}. É a variável que decide se o teto morde; ver a
     * nota de classe.
     */
    private static List<Instance> instances() {
        return List.of(
                new Instance("todos SHORT", withTiers("SHORT", "SHORT", "SHORT", "SHORT")),
                new Instance("3 SHORT 1 LONG", withTiers("SHORT", "SHORT", "SHORT", "LONG")),
                new Instance("2 SHORT 2 STANDARD", withTiers("SHORT", "SHORT", "STANDARD", "STANDARD")),
                new Instance("mistura plena", withTiers("SHORT", "STANDARD", "LONG", "EXTENDED")),
                new Instance("2 LONG 2 EXTENDED", withTiers("LONG", "LONG", "EXTENDED", "EXTENDED")),
                new Instance("3 EXTENDED 1 LONG", withTiers("EXTENDED", "EXTENDED", "EXTENDED", "LONG")),
                new Instance("todos EXTENDED",
                        withTiers("EXTENDED", "EXTENDED", "EXTENDED", "EXTENDED")));
    }

    /** O mesmo pedido, com as quatro faixas de esforço escolhidas. */
    private static PlanRequest withTiers(String first, String second, String third, String fourth) {
        List<PlanRequest.AvailabilitySlot> availability = new ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            String date = String.format(Locale.ROOT, "2026-09-%02d", day);
            availability.add(PlanRequests.slot(date + "T09:00:00Z", date + "T12:00:00Z"));
        }
        return PlanRequests.builder()
                .withAvailability(List.copyOf(availability))
                .withTopics(List.of(
                        PlanRequests.topic(PlanRequests.TOPIC_1, PlanRequests.SUBJECT_FIRST,
                                1, 30, first),
                        PlanRequests.topic(PlanRequests.TOPIC_2, PlanRequests.SUBJECT_FIRST,
                                2, 60, second),
                        PlanRequests.topic(PlanRequests.TOPIC_3, PlanRequests.SUBJECT_SECOND,
                                1, 108, third),
                        PlanRequests.topic(PlanRequests.TOPIC_4, PlanRequests.SUBJECT_SECOND,
                                2, 180, fourth)))
                .build();
    }

    private static String key(String suffix) {
        return "objective." + DailyLoadBudgetObjective.NAME + suffix;
    }

    @Test
    @DisplayName("a taxa e medida, reportada, e o termo discrimina entre instancias")
    void aTaxaEMedidaEOTermoDiscrimina() {
        List<String> rows = new ArrayList<>();
        int binding = 0;

        for (Instance instance : instances()) {
            Map<String, Object> fitness =
                    PlanEngines.genetic().plan(instance.request()).fitness();

            assertThat(fitness)
                    .as("o termo tem de se reportar em toda instancia, senao a medicao nao existe")
                    .containsKey(key(".binding"))
                    .containsKey(key(".excess-ratio"))
                    .containsKey(key(".ceiling"));

            boolean bound = (boolean) fitness.get(key(".binding"));
            binding += bound ? 1 : 0;
            rows.add(String.format(Locale.ROOT,
                    "  %-14s ceiling=%-3s binding=%-5s excess=%s  aggregate=%.6f",
                    instance.name(), fitness.get(key(".ceiling")), bound,
                    fitness.get(key(".excess-ratio")), (double) fitness.get("aggregate")));
        }

        int total = instances().size();
        System.out.printf("%n<<<DAILY-LOAD-BUDGET BINDING RATE>>>%n%s%n  rate = %d/%d = %.0f%%%n"
                        + "<<<END>>>%n",
                String.join("\n", rows), binding, total, 100.0 * binding / total);

        assertThat(binding)
                .as("""
                        O teto de carga diaria nunca foi vinculante em nenhuma instancia.

                        Um termo que nunca restringe nada e decorativo: na fitness publicada ele e
                        indistinguivel de um termo que olhou e nao achou problema, que e exatamente
                        o que este caminho existe para nao fazer.

                        A decisao correta passa a ser remove-lo e renormalizar 0,50/0,30 para
                        0,625/0,375 — plan.fitness.sinapse.daily-load-budget=false.""")
                .isPositive();
        assertThat(binding)
                .as("um termo que morde em TODA instancia tambem nao discrimina: se o teto nunca "
                        + "sobra, ele funciona como constante e nao como medida de carga")
                .isLessThan(total);
    }

    @Test
    @DisplayName("edital difícil excede o teto; edital fácil nao")
    void editalDificilExcedeEFacilNao() {
        Map<String, Object> dificil = PlanEngines.genetic()
                .plan(withTiers("EXTENDED", "EXTENDED", "EXTENDED", "EXTENDED")).fitness();
        Map<String, Object> facil = PlanEngines.genetic()
                .plan(withTiers("SHORT", "SHORT", "SHORT", "SHORT")).fitness();

        assertThat((boolean) dificil.get(key(".binding")))
                .as("faixa 4 em todo o edital contra uma referencia de 2,5 tem de exceder")
                .isTrue();
        assertThat((boolean) facil.get(key(".binding")))
                .as("faixa 1 em todo o edital nao tem de exceder nada")
                .isFalse();
    }

    @Test
    @DisplayName("desligado por configuracao, o termo desaparece do relatorio e os pesos renormalizam")
    void desligadoDesapareceEOsPesosRenormalizam() {
        PlanRequest dificil = withTiers("EXTENDED", "EXTENDED", "EXTENDED", "EXTENDED");
        PlanResponse comTeto = PlanEngines.genetic(true).plan(dificil);
        PlanResponse semTeto = PlanEngines.genetic(false).plan(dificil);

        assertThat(comTeto.fitness()).containsKey(key(".binding"));
        assertThat(semTeto.fitness())
                .as("termo desativado nao aparece com zero: nao aparece")
                .doesNotContainKey(key(".binding"))
                .doesNotContainKey(key(""))
                .doesNotContainKey(key(".weight"));

        // isCloseTo e nao isEqualTo: 0,30/0,80 e 0.37499999999999994 em ponto flutuante binario. Os
        // pesos sao COMPUTADOS a partir de FitnessWeights, e nao escritos a mao, exatamente para
        // nao poderem divergir dele — o preco e que a renormalizacao nao cai num decimal exato.
        assertThat((double) semTeto.fitness().get("objective.syllabusMastery.weight"))
                .as("0,50 / 0,80")
                .isCloseTo(0.625, org.assertj.core.api.Assertions.within(1e-12));
        assertThat((double) semTeto.fitness().get("objective.retention.weight"))
                .as("0,30 / 0,80")
                .isCloseTo(0.375, org.assertj.core.api.Assertions.within(1e-12));
    }
}

package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.plan.PlanEngines;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
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
 * {@code AvailabilityWindow.expectedEnergyLevel} não influencia o plano, porque não há dado para ele.
 *
 * <h2>Por que isto é teste e não comentário</h2>
 *
 * O record exige um terceiro componente que prevê a energia do aluno na janela, e a plataforma não
 * envia sinal algum de energia. As opções eram deixá-lo num valor plausível do meio da escala ou
 * tornar a ausência visível. {@code AvailabilityWindows} usa um sentinela fora da faixa documentada
 * e nada no caminho o lê — e "nada o lê" é exatamente o tipo de afirmação que apodrece em prosa.
 *
 * <p>É a mesma regra que a composição de fitness aplica aos termos: um campo que aparenta modelar
 * algo e não modela é pior do que um campo declaradamente ausente.
 */
@DisplayName("Adaptador SINAPSE: a energia da janela nao e usada")
class SinapseEnergyIsUnusedTest {

    private static final Path ADAPTER =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner/sinapse");

    @Test
    @DisplayName("o sentinela esta fora da faixa documentada de 1.0 a 5.0")
    void oSentinelaEstaForaDaFaixa() {
        List<AvailabilityWindow> windows =
                AvailabilityWindows.of(PlanRequests.builder().build().availability());

        assertThat(windows).isNotEmpty();
        assertThat(windows).allSatisfy(window -> assertThat(window.expectedEnergyLevel())
                .as("um 3.0 plausivel seria indistinguivel de uma observacao")
                .isEqualTo(AvailabilityWindows.ENERGY_NOT_MEASURED)
                .isLessThan(1.0));
    }

    @Test
    @DisplayName("so a classe que preenche o campo o menciona: nenhum passo do caminho o le")
    void nenhumPassoDoCaminhoLeOCampo() throws IOException {
        // A forma honesta da afirmacao. Comparar dois planos em tempo de execucao nao serve: a
        // energia e atribuida dentro de AvailabilityWindows.of, entao um teste de fora nao tem como
        // varia-la, e a comparacao passaria mesmo que algum passo lesse o campo. O que se pode
        // verificar e o alcance: quem menciona expectedEnergyLevel neste pacote.
        List<String> mentions = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ADAPTER)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains("expectedEnergyLevel")) {
                    mentions.add(file.getFileName().toString());
                }
            }
        }

        assertThat(mentions)
                .as("Um passo do caminho SINAPSE passou a ler expectedEnergyLevel. A plataforma nao "
                        + "envia sinal de energia algum: o campo carrega um sentinela fora da faixa "
                        + "documentada, e ordenar ou ponderar por ele produz um resultado que "
                        + "aparenta considerar a energia do aluno sem nenhum dado por tras. Se a "
                        + "intencao e usar energia, ela precisa vir no contrato primeiro.")
                .containsExactly("AvailabilityWindows.java");
    }

    @Test
    @DisplayName("a conversao de instante para hora local e exata nos dois sentidos")
    void aConversaoEhExataNosDoisSentidos() {
        // UTC como transporte, nao como interpretacao: o que entra na resposta e bit a bit o que
        // chegou, entao o plano nao depende do fuso em que o aluno esta.
        PlanRequest request = PlanRequests.builder().build();
        PlanResponse response = PlanEngines.genetic().plan(request);

        assertThat(response.sessions()).isNotEmpty();
        assertThat(response.sessions()).allSatisfy(session -> assertThat(
                AvailabilityWindows.toInstant(
                        AvailabilityWindows.toLocal(session.scheduledStart())))
                .isEqualTo(session.scheduledStart()));
    }
}

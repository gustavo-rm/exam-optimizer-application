package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O construtor passo a passo do {@link EvolutionContext} — achado E1 da etapa 03.
 *
 * <h2>O que estes testes protegem</h2>
 *
 * A montagem do contexto era um método fábrica com dez parâmetros posicionais e nove locais de
 * chamada. O risco não era teórico: quatro dos dez eram objetos anuláveis do mesmo "formato" para o
 * compilador, então trocar dois de lugar compilava sem erro e produzia um contexto silenciosamente
 * errado.
 *
 * <p>O construtor passo a passo remove esse modo de falha nomeando cada valor no ponto de uso. Estes
 * testes travam as três propriedades de que isso depende: os obrigatórios são exigidos, os
 * opcionais podem ser omitidos, e os campos derivados continuam sendo calculados na construção.
 */
@DisplayName("Construtor passo a passo do EvolutionContext")
class EvolutionContextBuilderTest {

    private static final Subject MATEMATICA = new Subject("Matematica", 20, 3);
    private static final Subject PORTUGUES = new Subject("Portugues", 10, 2);

    /** O mínimo que todo caminho de execução fornece. */
    private static EvolutionContext.Builder minimo() {
        return EvolutionContext.builder()
                .importanceScores(Map.of(MATEMATICA, 30.0, PORTUGUES, 10.0))
                .minimumDaysPerSubject(Map.of(MATEMATICA, 5, PORTUGUES, 3))
                .planningHorizonDays(180)
                .hoursPerStudyDay(4)
                .maxDailyCognitiveLoad(20);
    }

    @Nested
    @DisplayName("Valores obrigatorios")
    class Obrigatorios {

        @Test
        @DisplayName("o minimo basta para construir")
        void oMinimoBasta() {
            assertThatCode(() -> minimo().build()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cada obrigatorio ausente e nomeado na mensagem de erro")
        void cadaObrigatorioAusenteEhNomeado() {
            // A mensagem precisa dizer QUAL falta: um erro generico de construcao obrigaria quem o
            // recebe a abrir o codigo do construtor para descobrir.
            assertThatThrownBy(() -> EvolutionContext.builder()
                    .minimumDaysPerSubject(Map.of()).planningHorizonDays(1)
                    .hoursPerStudyDay(1).maxDailyCognitiveLoad(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("importanceScores");

            assertThatThrownBy(() -> EvolutionContext.builder()
                    .importanceScores(Map.of()).planningHorizonDays(1)
                    .hoursPerStudyDay(1).maxDailyCognitiveLoad(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("minimumDaysPerSubject");

            assertThatThrownBy(() -> EvolutionContext.builder()
                    .importanceScores(Map.of()).minimumDaysPerSubject(Map.of())
                    .hoursPerStudyDay(1).maxDailyCognitiveLoad(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("planningHorizonDays");

            assertThatThrownBy(() -> EvolutionContext.builder()
                    .importanceScores(Map.of()).minimumDaysPerSubject(Map.of())
                    .planningHorizonDays(1).maxDailyCognitiveLoad(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hoursPerStudyDay");

            assertThatThrownBy(() -> EvolutionContext.builder()
                    .importanceScores(Map.of()).minimumDaysPerSubject(Map.of())
                    .planningHorizonDays(1).hoursPerStudyDay(1).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maxDailyCognitiveLoad");
        }

        @Test
        @DisplayName("varios obrigatorios ausentes sao reportados de uma vez")
        void variosAusentesSaoReportadosJuntos() {
            // Reportar um por vez faria quem constroi descobrir os cinco em cinco tentativas.
            assertThatThrownBy(() -> EvolutionContext.builder().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAll("importanceScores", "minimumDaysPerSubject",
                            "planningHorizonDays", "hoursPerStudyDay", "maxDailyCognitiveLoad");
        }
    }

    @Nested
    @DisplayName("Valores opcionais")
    class Opcionais {

        @Test
        @DisplayName("omitidos ficam nulos — o mesmo que os chamadores passavam antes")
        void omitidosFicamNulos() {
            // Preserva exatamente a semantica anterior: o caminho macro passava null nestes cinco,
            // e os consumidores guardam contra isso. A refatoracao nao muda comportamento.
            EvolutionContext contexto = minimo().build();

            assertThat(contexto.studentState()).isNull();
            assertThat(contexto.fitnessEvaluator()).isNull();
            assertThat(contexto.retentionProfile()).isNull();
            assertThat(contexto.planStartDate()).isNull();
            assertThat(contexto.engagementProfile()).isNull();
        }

        @Test
        @DisplayName("informados chegam intactos, cada um no seu campo")
        void informadosChegamIntactos() {
            // A assercao que o formato posicional nao permitia fazer: verificar que cada valor foi
            // parar no campo certo. Com dez parametros posicionais, trocar dois compilava.
            StudentState estado = new StudentState(3.0, 2.0, 4.0, Chronotype.NIGHT_OWL);
            RetentionProfile retencao = new RetentionProfile(Map.of());
            LocalDate inicio = LocalDate.of(2026, 3, 2);
            EngagementProfile engajamento = EngagementProfile.baseline();

            EvolutionContext contexto = minimo()
                    .studentState(estado)
                    .retentionProfile(retencao)
                    .planStartDate(inicio)
                    .engagementProfile(engajamento)
                    .build();

            assertThat(contexto.studentState()).isSameAs(estado);
            assertThat(contexto.retentionProfile()).isSameAs(retencao);
            assertThat(contexto.planStartDate()).isEqualTo(inicio);
            assertThat(contexto.engagementProfile()).isSameAs(engajamento);
        }

        @Test
        @DisplayName("os tres valores numericos chegam nos campos certos, e nao trocados entre si")
        void osNumericosNaoSeTrocam() {
            // Tres int seguidos eram o trecho mais perigoso da assinatura antiga: para o compilador
            // sao indistinguiveis. Valores deliberadamente distintos para que uma troca falhe aqui.
            EvolutionContext contexto = EvolutionContext.builder()
                    .importanceScores(Map.of(MATEMATICA, 1.0))
                    .minimumDaysPerSubject(Map.of(MATEMATICA, 1))
                    .planningHorizonDays(111)
                    .hoursPerStudyDay(222)
                    .maxDailyCognitiveLoad(333)
                    .build();

            assertThat(contexto.planningHorizonDays()).isEqualTo(111);
            assertThat(contexto.hoursPerStudyDay()).isEqualTo(222);
            assertThat(contexto.maxDailyCognitiveLoad()).isEqualTo(333);
        }
    }

    @Nested
    @DisplayName("Campos derivados")
    class Derivados {

        @Test
        @DisplayName("a importancia normalizada e os pesos de retencao sao calculados na construcao")
        void derivadosSaoCalculadosNaConstrucao() {
            // A derivacao acontece uma vez, e nao a cada avaliacao: a fitness roda ate meio milhao
            // de vezes por requisicao. Esta propriedade e a razao de o contexto ter uma fabrica em
            // vez de ser construido direto pelo construtor do record.
            EvolutionContext contexto = minimo().build();

            assertThat(contexto.normalizedImportance().values().stream().mapToDouble(Double::doubleValue).sum())
                    .as("a importancia normalizada soma 1 (projecao no simplex)")
                    .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(contexto.retentionWeights().values().stream().mapToDouble(Double::doubleValue).sum())
                    .as("os pesos de retencao tambem somam 1, apos a temperagem")
                    .isEqualTo(1.0, org.assertj.core.data.Offset.offset(1e-9));

            assertThat(contexto.retentionWeights().get(MATEMATICA))
                    .as("a temperagem achata a distribuicao: o peso de retencao da disciplina "
                            + "dominante fica abaixo do peso de edital dela")
                    .isLessThan(contexto.normalizedImportance().get(MATEMATICA));
        }

        @Test
        @DisplayName("a importancia bruta e preservada ao lado da normalizada")
        void aImportanciaBrutaEhPreservada() {
            EvolutionContext contexto = minimo().build();

            assertThat(contexto.importanceScores())
                    .as("o agendador e as baselines ainda ordenam pela importancia bruta")
                    .containsEntry(MATEMATICA, 30.0)
                    .containsEntry(PORTUGUES, 10.0);
        }
    }

    @Test
    @DisplayName("o construtor canonico de 12 argumentos so e usado pelo proprio construtor")
    void oConstrutorCanonicoNaoVazaParaForaDoBuilder() throws Exception {
        // EvolutionContext e um record publico, entao o construtor canonico — com DOZE argumentos
        // posicionais, dois deles campos derivados — e publico por exigencia da linguagem. Ele e
        // pior que a fabrica que esta etapa removeu.
        //
        // Nao da para escondê-lo, mas da para verificar que ninguem o usa. Se alguem passar a usar,
        // este teste falha e aponta o caminho certo: EvolutionContext.builder().
        List<String> usos;
        try (Stream<Path> arquivos = Stream.concat(
                Files.walk(Path.of("src/main/java")),
                Stream.concat(Files.walk(Path.of("src/test/java")), Files.walk(Path.of("benchmarks/java"))))) {
            usos = arquivos
                    .filter(p -> p.toString().endsWith(".java"))
                    // O proprio EvolutionContext o chama, de dentro do construtor passo a passo,
                    // que e o unico lugar legitimo. E esta classe de teste cita o trecho na
                    // mensagem de falha, entao tambem se exclui — do contrario acusaria a si mesma.
                    .filter(p -> !p.endsWith("EvolutionContext.java"))
                    .filter(p -> !p.endsWith("EvolutionContextBuilderTest.java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8).contains("new EvolutionContext(");
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(Path::toString)
                    .toList();
        }

        assertThat(usos)
                .as("""
                        Alguem passou a chamar o construtor canonico do EvolutionContext.

                        Ele tem doze argumentos posicionais, incluindo normalizedImportance e
                        retentionWeights, que sao DERIVADOS e precisam ser calculados a partir da
                        importancia bruta. Construi-lo a mao e mais arriscado que a fabrica de dez
                        parametros que a etapa 03c removeu.

                        Use EvolutionContext.builder().""")
                .isEmpty();
    }
}

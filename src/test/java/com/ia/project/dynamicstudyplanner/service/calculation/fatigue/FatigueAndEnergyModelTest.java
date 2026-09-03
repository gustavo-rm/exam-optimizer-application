package com.ia.project.dynamicstudyplanner.service.calculation.fatigue;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Rede de segurança para a refatoração de {@link FatigueAndEnergyModel} (etapa 04b, achado L5).
 *
 * <h2>O que é um teste de caracterização</h2>
 *
 * Um <b>teste de caracterização</b> (<i>characterization test</i>) descreve o que o código
 * <b>faz hoje</b>, sem julgar se está certo. Ele não valida a regra de negócio — existe para que
 * qualquer mudança de comportamento durante uma refatoração apareça como falha em vez de passar
 * despercebida.
 *
 * <p>Era indispensável aqui: {@code FatigueAndEnergyModel} estava a <b>1,3 % de instruções
 * cobertas</b> (3 de 235). Refatorar um método de complexidade 9 sem cobertura é reescrever no
 * escuro.
 *
 * <p><b>Todos os números fixados abaixo foram medidos no código anterior à refatoração</b>, por
 * varredura, e não escolhidos. Três deles contrariaram o que eu esperava, e é por isso que a
 * caracterização veio antes da refatoração e não depois.
 *
 * <h2>O que a medição revelou</h2>
 *
 * <ol>
 *   <li><b>A metodologia de estudo não afeta o resultado.</b> As cinco metodologias produzem valores
 *       idênticos para o mesmo número de blocos. A causa está em
 *       {@code oRamoDeCargaLeveNuncaEExecutado}.</li>
 *   <li><b>A função é descontínua.</b> A rampa gradual para em <b>0,5</b> e o esgotamento agudo
 *       devolve <b>0,1</b>. Nenhuma entrada produz valor entre os dois — é um degrau, não a queda
 *       "exponencial" que o Javadoc anterior prometia (achado C1).</li>
 *   <li><b>O ramo de esgotamento crônico é inalcançável pela API.</b> Exige
 *       {@code fatigueLevel >= 8}, e {@code StudentStateDto} valida com {@code @DecimalMax("5.0")}.</li>
 * </ol>
 *
 * <p>Os pontos 1 e 3 são decisões de domínio, não de refatoração — a refatoração desta etapa
 * <b>preservou os dois exatamente</b>. Estão registrados como pendências <b>P11</b> e <b>P12</b> em
 * {@code docs/qualidade/04b-correcao-escrita.md}.
 */
@DisplayName("FatigueAndEnergyModel: caracterizacao do comportamento medido")
class FatigueAndEnergyModelTest {

    private final FatigueAndEnergyModel modelo = new FatigueAndEnergyModel();

    private static final Subject DISCIPLINA = new Subject("Portugues", 10, 3);
    private static final LocalDate DIA = LocalDate.of(2026, 3, 2);

    private static StudentState estadoComFadiga(double fadiga) {
        return new StudentState(3.0, fadiga, 3.0, Chronotype.INTERMEDIATE);
    }

    /**
     * Um plano com {@code blocos} blocos de 60 minutos, <b>todos dentro do mesmo dia civil</b>,
     * começando à meia-noite.
     *
     * <p>O limite de 24 blocos não é decoração: o modelo zera a fadiga diária a cada mudança de
     * {@code getDayOfYear()}. Uma fixture que transborda para o dia seguinte mede outra coisa —
     * foi o primeiro erro que cometi ao escrever este arquivo.
     */
    private static TacticalStudyPlan planoDeUmDia(int blocos, StudyMethodology metodologia) {
        if (blocos > 24) {
            throw new IllegalArgumentException("nao cabem " + blocos + " blocos de 1h num dia so");
        }
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        for (int i = 0; i < blocos; i++) {
            LocalDateTime de = DIA.atStartOfDay().plusHours(i);
            agenda.put(new TimeSlot(de, de.plusHours(1)),
                    new TacticalStudyBlock(DISCIPLINA, metodologia, 60));
        }
        return new TacticalStudyPlan(agenda);
    }

    /** Um bloco de 60 minutos por dia, às 8h, em {@code dias} dias consecutivos. */
    private static TacticalStudyPlan planoDeVariosDias(int dias, StudyMethodology metodologia) {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        for (int d = 0; d < dias; d++) {
            LocalDateTime de = DIA.plusDays(d).atTime(8, 0);
            agenda.put(new TimeSlot(de, de.plusHours(1)),
                    new TacticalStudyBlock(DISCIPLINA, metodologia, 60));
        }
        return new TacticalStudyPlan(agenda);
    }

    @Nested
    @DisplayName("calculateBurnoutRisk")
    class RiscoDeEsgotamento {

        @Test
        @DisplayName("estado nulo devolve 1.0 — sem penalidade quando nao se sabe nada do aluno")
        void estadoNuloNaoPenaliza() {
            assertThat(modelo.calculateBurnoutRisk(
                    planoDeUmDia(2, StudyMethodology.ACTIVE_RECALL), null)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("agenda vazia devolve 1.0 — nada agendado nao cansa ninguem")
        void agendaVaziaNaoPenaliza() {
            assertThat(modelo.calculateBurnoutRisk(new TacticalStudyPlan(Map.of()), estadoComFadiga(3.0)))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("a rampa gradual cai linearmente com a carga do dia")
        void aRampaCaiLinearmente() {
            // Valores medidos com fadiga inicial 1.0. Passo constante entre pontos igualmente
            // espacados: e uma reta, nao a exponencial que o Javadoc anterior prometia.
            StudentState estado = estadoComFadiga(1.0);
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(4, StudyMethodology.ACTIVE_RECALL), estado))
                    .isCloseTo(0.8533, within(1e-4));
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(8, StudyMethodology.ACTIVE_RECALL), estado))
                    .isCloseTo(0.7733, within(1e-4));
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(12, StudyMethodology.ACTIVE_RECALL), estado))
                    .isCloseTo(0.6933, within(1e-4));
        }

        @Test
        @DisplayName("a fadiga inicial do aluno desloca a rampa para baixo")
        void aFadigaInicialDeslocaARampa() {
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(4, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(1.0)))
                    .isCloseTo(0.8533, within(1e-4));
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(4, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(3.0)))
                    .isCloseTo(0.7200, within(1e-4));
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(4, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(5.0)))
                    .isCloseTo(0.5867, within(1e-4));
        }

        @Test
        @DisplayName("a funcao e descontinua: a rampa para em 0.5 e o degrau agudo cai para 0.1")
        void aFuncaoEDescontinuaEntreMeioEUmDecimo() {
            // Piso da rampa, alcancavel com entrada valida (fadiga 5, 12h num dia).
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(12, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(5.0)))
                    .as("a rampa e limitada por baixo em 0.5")
                    .isEqualTo(0.5);

            // Mais carga no mesmo dia nao desce suavemente: pula para 0.1.
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(18, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(5.0)))
                    .as("nenhuma entrada produz valor entre 0.1 e 0.5: e um salto, nao uma curva")
                    .isEqualTo(0.1);
        }

        @Test
        @DisplayName("o ramo de carga leve nunca e executado — a metodologia nao muda o resultado")
        void oRamoDeCargaLeveNuncaEExecutado() {
            // O modelo escolhe entre multiplicador 1.5 e 0.8 comparando a carga do bloco com a
            // energia esperada. Medido: a energia esperada e limitada a 1.5, e o bloco MAIS LEVE
            // possivel (leitura passiva, 15 min) ja tem carga 2.048. A comparacao e sempre
            // verdadeira, entao o ramo de 0.8 e inalcancavel e a curva bifasica por cronotipo,
            // computada logo acima, nao influencia nada. Ver pendencia P12.
            for (StudyMethodology metodologia : StudyMethodology.values()) {
                assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(8, metodologia), estadoComFadiga(3.0)))
                        .as("metodologia %s deveria mudar a fadiga, e nao muda", metodologia)
                        .isCloseTo(0.6400, within(1e-4));
            }
        }

        @Test
        @DisplayName("esgotamento cronico devolve 0.2 — mas so com fadiga fora da faixa que a API aceita")
        void esgotamentoCronicoExigeFadigaAcimaDoQueAApiPermite() {
            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(2, StudyMethodology.PASSIVE_READING), estadoComFadiga(10.0)))
                    .as("com fadiga 10 o ramo cronico responde")
                    .isEqualTo(0.2);

            assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(2, StudyMethodology.PASSIVE_READING), estadoComFadiga(5.0)))
                    .as("com fadiga 5 — o maximo que StudentStateDto aceita — o ramo nao e alcancado")
                    .isNotEqualTo(0.2);
        }

        @Test
        @DisplayName("carga espalhada por muitos dias converge, por causa do fator de arrasto diario")
        void cargaEspalhadaConverge() {
            // A fadiga acumulada e multiplicada por FATIGUE_CARRYOVER_RATE a cada virada de dia, o
            // que a faz convergir em vez de crescer. Um bloco por dia estabiliza em ~0.975.
            assertThat(modelo.calculateBurnoutRisk(planoDeVariosDias(8, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(1.0)))
                    .isCloseTo(0.975, within(1e-3));
            assertThat(modelo.calculateBurnoutRisk(planoDeVariosDias(16, StudyMethodology.ACTIVE_RECALL), estadoComFadiga(1.0)))
                    .isCloseTo(0.975, within(1e-3));
        }

        @Test
        @DisplayName("o resultado nunca sai do intervalo [0.1, 1.0]")
        void oResultadoFicaNoIntervaloEsperado() {
            for (double fadiga : new double[]{1.0, 3.0, 5.0}) {
                for (int blocos = 1; blocos <= 24; blocos += 3) {
                    for (StudyMethodology m : StudyMethodology.values()) {
                        assertThat(modelo.calculateBurnoutRisk(planoDeUmDia(blocos, m), estadoComFadiga(fadiga)))
                                .as("fadiga=%.1f blocos=%d metodologia=%s", fadiga, blocos, m)
                                .isBetween(0.1, 1.0);
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("getExpectedEnergyLevel")
    class NivelDeEnergia {

        @Test
        @DisplayName("cada cronotipo tem seu pico onde a curva manda")
        void cadaCronotipoTemSeuPico() {
            assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(9, 0), Chronotype.MORNING_LARK)).isEqualTo(1.5);
            assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(14, 0), Chronotype.NIGHT_OWL)).isEqualTo(1.5);
            assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(10, 0), Chronotype.INTERMEDIATE)).isEqualTo(1.5);
        }

        @Test
        @DisplayName("a curva fica sempre entre 0.5 e 1.5, em qualquer hora e cronotipo")
        void aCurvaRespeitaOsLimites() {
            for (Chronotype c : Chronotype.values()) {
                for (int hora = 0; hora < 24; hora++) {
                    assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(hora, 30), c))
                            .as("cronotipo=%s hora=%d", c, hora)
                            .isBetween(0.5, 1.5);
                }
            }
        }

        @Test
        @DisplayName("a madrugada e penalizada para todos os cronotipos")
        void aMadrugadaEPenalizada() {
            for (Chronotype c : Chronotype.values()) {
                assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(3, 0), c))
                        .as("cronotipo=%s as 3h", c)
                        .isLessThan(modelo.getExpectedEnergyLevel(LocalTime.of(12, 0), c));
            }
        }

        @Test
        @DisplayName("longe dos dois picos, a energia volta para a base de 1.0")
        void longeDoPicoAEnergiaVoltaParaABase() {
            // MORNING_LARK: picos em 9h (raio 4h) e 15h (raio 3h). As 20h esta fora dos dois.
            assertThat(modelo.getExpectedEnergyLevel(LocalTime.of(20, 0), Chronotype.MORNING_LARK))
                    .isEqualTo(1.0);
        }
    }
}

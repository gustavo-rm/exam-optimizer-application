package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneVectors;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava o contrato da representação indexada do cromossomo (pendência P18).
 *
 * <h2>O que mudou e por que precisa de teste próprio</h2>
 *
 * O plano de estudos deixou de ser um {@code Map<Subject, Integer>} e passou a ser um {@code int[]}
 * alinhado a um {@link SubjectIndex} compartilhado por toda a população. A mudança é o que tirou o
 * cálculo de hash, o desembrulho de {@code Integer} e a alocação de nó de cada gene — e trouxe uma
 * regra nova que antes não existia: <b>a ordem dos genes é do contexto, não de cada plano</b>.
 *
 * <p>Essa regra tem uma consequência afiada. Dois planos alinhados ao mesmo índice podem ser
 * recombinados posição a posição, sem consultar disciplina nenhuma; dois planos alinhados a índices
 * diferentes, não. Este arquivo fixa os dois lados: que o caminho rápido está certo, e que o
 * caminho de segurança existe e funciona para um plano vindo da fronteira.
 *
 * <p>O complemento em nível de sistema é {@code GaResultadoInalteradoTest}, que trava a
 * reprodutibilidade do plano inteiro para uma semente fixa.
 */
@DisplayName("Cromossomo indexado: a ordem dos genes e do contexto")
class CromossomoIndexadoTest {

    private static final Subject PORTUGUES = new Subject("Portugues", 20, 2);
    private static final Subject MATEMATICA = new Subject("Matematica", 15, 5);
    private static final Subject DIREITO = new Subject("Direito", 25, 4);
    private static final Subject AUSENTE = new Subject("Ausente", 1, 1);

    private static final List<Subject> EDITAL = List.of(PORTUGUES, MATEMATICA, DIREITO);

    @Nested
    @DisplayName("SubjectIndex: a ordem canonica")
    class OrdemCanonica {

        @Test
        @DisplayName("preserva a ordem em que as disciplinas foram apresentadas")
        void preservaAOrdemApresentada() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);

            assertThat(ordem.size()).isEqualTo(3);
            assertThat(ordem.subject(0)).isEqualTo(PORTUGUES);
            assertThat(ordem.subject(1)).isEqualTo(MATEMATICA);
            assertThat(ordem.subject(2)).isEqualTo(DIREITO);
            assertThat(ordem.subjects()).containsExactly(PORTUGUES, MATEMATICA, DIREITO);
        }

        @Test
        @DisplayName("a ordem inversa produz outro indice — a ordem e mesmo significativa")
        void aOrdemInversaProduzOutroIndice() {
            // Contraprova do teste acima: sem ela, uma implementacao que ordenasse tudo
            // internamente (por nome, por exemplo) passaria no primeiro e quebraria o cruzamento.
            List<Subject> invertido = new ArrayList<>(EDITAL);
            java.util.Collections.reverse(invertido);
            SubjectIndex ordem = SubjectIndex.of(invertido);

            assertThat(ordem.subject(0)).isEqualTo(DIREITO);
            assertThat(ordem.positionOf(PORTUGUES)).isEqualTo(2);
        }

        @Test
        @DisplayName("disciplina repetida ocupa uma posicao so, a primeira em que apareceu")
        void repetidaOcupaUmaPosicaoSo() {
            SubjectIndex ordem = SubjectIndex.of(
                    List.of(PORTUGUES, MATEMATICA, PORTUGUES, DIREITO, MATEMATICA));

            assertThat(ordem.size()).isEqualTo(3);
            assertThat(ordem.subjects()).containsExactly(PORTUGUES, MATEMATICA, DIREITO);
        }

        @Test
        @DisplayName("nulo, vazio e lista so de nulos dao o indice vazio")
        void entradasDegeneradasDaoOIndiceVazio() {
            assertThat(SubjectIndex.of(null).size()).isZero();
            assertThat(SubjectIndex.of(List.of()).size()).isZero();
            assertThat(SubjectIndex.of(Arrays.asList((Subject) null, null)).size()).isZero();
        }

        @Test
        @DisplayName("disciplina de fora do indice tem posicao -1")
        void disciplinaDeForaTemPosicaoNegativa() {
            assertThat(SubjectIndex.of(EDITAL).positionOf(AUSENTE)).isEqualTo(-1);
        }

        @Test
        @DisplayName("projetar um mapa alinha os valores e completa o que falta com o padrao")
        void projetarAlinhaEcompleta() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);

            int[] inteiros = ordem.projectInts(Map.of(MATEMATICA, 7), 1);
            assertThat(inteiros).containsExactly(1, 7, 1);

            double[] reais = ordem.projectDoubles(Map.of(DIREITO, 0.5), 0.0);
            assertThat(reais).containsExactly(0.0, 0.0, 0.5);

            assertThat(ordem.projectInts(null, 3)).containsExactly(3, 3, 3);
            assertThat(ordem.projectDoubles(null, 2.0)).containsExactly(2.0, 2.0, 2.0);
        }

        @Test
        @DisplayName("o mapa reconstruido sai na ordem canonica, nao na do hash")
        void oMapaReconstruidoSaiNaOrdemCanonica() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);

            assertThat(ordem.toMap(new int[]{10, 20, 30}))
                    .containsExactly(
                            Map.entry(PORTUGUES, 10),
                            Map.entry(MATEMATICA, 20),
                            Map.entry(DIREITO, 30));
        }
    }

    @Nested
    @DisplayName("StudyPlan: os dois construtores")
    class ConstrucaoDoPlano {

        @Test
        @DisplayName("o construtor canonico le os genes por posicao e soma o total uma vez")
        void construtorCanonico() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);
            StudyPlan plano = new StudyPlan(ordem, new int[]{10, 20, 30});

            assertThat(plano.getIndex()).isSameAs(ordem);
            assertThat(plano.daysAt(0)).isEqualTo(10);
            assertThat(plano.daysAt(2)).isEqualTo(30);
            assertThat(plano.getTotalDays()).isEqualTo(60);
            assertThat(plano.getDaysForSubject(MATEMATICA)).isEqualTo(20);
            assertThat(plano.getDaysForSubject(AUSENTE)).isZero();
        }

        @Test
        @DisplayName("vetor de tamanho diferente do indice e recusado, nao truncado em silencio")
        void vetorDeTamanhoErradoERecusado() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);

            assertThatThrownBy(() -> new StudyPlan(ordem, new int[]{1, 2}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("3 disciplina")
                    .hasMessageContaining("2 posicao");
        }

        @Test
        @DisplayName("indice e vetor nulos dao um plano vazio, sem quebrar")
        void nulosDaoPlanoVazio() {
            StudyPlan plano = new StudyPlan((SubjectIndex) null, null);

            assertThat(plano.getIndex().size()).isZero();
            assertThat(plano.getTotalDays()).isZero();
            assertThat(plano.getDaysPerSubject()).isEmpty();
        }

        @Test
        @DisplayName("o construtor de fronteira deriva a ordem do proprio mapa")
        void construtorDeFronteira() {
            Map<Subject, Integer> dias = new LinkedHashMap<>();
            dias.put(DIREITO, 5);
            dias.put(PORTUGUES, 8);
            StudyPlan plano = new StudyPlan(dias);

            assertThat(plano.getIndex().subjects()).containsExactly(DIREITO, PORTUGUES);
            assertThat(plano.daysAt(0)).isEqualTo(5);
            assertThat(plano.getTotalDays()).isEqualTo(13);
        }

        @Test
        @DisplayName("mapa nulo produz um plano vazio")
        void mapaNuloProduzPlanoVazio() {
            StudyPlan plano = new StudyPlan((Map<Subject, Integer>) null);

            assertThat(plano.getTotalDays()).isZero();
            assertThat(plano.getDaysPerSubject()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Alinhamento: o caminho rapido e o de seguranca")
    class Alinhamento {

        @Test
        @DisplayName("plano ja alinhado: os genes saem na mesma ordem, por copia")
        void planoJaAlinhado() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);
            StudyPlan plano = new StudyPlan(ordem, new int[]{10, 20, 30});

            assertThat(plano.genesAlignedTo(ordem)).containsExactly(10, 20, 30);
            for (int i = 0; i < ordem.size(); i++) {
                assertThat(plano.daysAt(ordem, i)).isEqualTo(plano.daysAt(i));
            }
        }

        @Test
        @DisplayName("plano de outra ordem: cada gene e reposicionado pela disciplina")
        void planoDeOutraOrdem() {
            // O caso que o caminho de seguranca existe para atender: um plano vindo da fronteira,
            // montado a partir de um mapa, cuja ordem nao e a do contexto. Ler por posicao sem
            // reposicionar trocaria os dias de uma disciplina pelos de outra em silencio.
            SubjectIndex ordem = SubjectIndex.of(EDITAL);
            Map<Subject, Integer> dias = new LinkedHashMap<>();
            dias.put(DIREITO, 30);
            dias.put(PORTUGUES, 10);
            dias.put(MATEMATICA, 20);
            StudyPlan daFronteira = new StudyPlan(dias);

            assertThat(daFronteira.getIndex()).isNotSameAs(ordem);
            assertThat(daFronteira.genesAlignedTo(ordem)).containsExactly(10, 20, 30);
            assertThat(daFronteira.daysAt(ordem, 0)).isEqualTo(10);
            assertThat(daFronteira.daysAt(ordem, 2)).isEqualTo(30);
        }

        @Test
        @DisplayName("disciplina que falta no plano vale zero na posicao dela")
        void disciplinaQueFaltaValeZero() {
            SubjectIndex ordem = SubjectIndex.of(EDITAL);
            StudyPlan parcial = new StudyPlan(Map.of(MATEMATICA, 20));

            assertThat(parcial.genesAlignedTo(ordem)).containsExactly(0, 20, 0);
        }
    }

    @Nested
    @DisplayName("GeneVectors: os dados por disciplina projetados na ordem dos genes")
    class VetoresDoContexto {

        @Test
        @DisplayName("piso, importancia e peso de retencao saem alinhados ao indice")
        void dadosSaemAlinhados() {
            GeneVectors vetores = contexto().geneVectors();

            assertThat(vetores.index().subjects()).containsExactly(PORTUGUES, MATEMATICA, DIREITO);
            assertThat(vetores.size()).isEqualTo(3);
            assertThat(vetores.minimumDays(1)).isEqualTo(9);
            assertThat(vetores.minimumDaysVector()).containsExactly(5, 9, 5);
            // A importancia normalizada soma 1 e respeita a ordem do edital: Direito tem o maior
            // numero de questoes, entao o maior peso.
            assertThat(vetores.normalizedImportance(2)).isGreaterThan(vetores.normalizedImportance(1));
            assertThat(vetores.retentionWeight(0)).isPositive();
            assertThat(vetores.requiredSessions(0)).isPositive();
        }

        @Test
        @DisplayName("um contexto com menos genes que o plano e recusado com mensagem que ensina")
        void contextoComMenosGenesERecusado() {
            // Sem esta guarda o operador devolveria um plano vazio em silencio, que e o que
            // acontecia quando um contexto era montado sem disciplina alguma.
            EvolutionContext semDisciplinas = EvolutionContext.builder()
                    .importanceScores(Map.of())
                    .minimumDaysPerSubject(Map.of())
                    .planningHorizonDays(180)
                    .hoursPerStudyDay(4)
                    .maxDailyCognitiveLoad(20)
                    .build();
            StudyPlan plano = new StudyPlan(SubjectIndex.of(EDITAL), new int[]{10, 20, 30});

            assertThatThrownBy(() -> semDisciplinas.geneVectors().requireCovers(plano))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("0 gene")
                    .hasMessageContaining("subjects(");
        }

        @Test
        @DisplayName("um contexto que cobre o plano passa sem reclamar")
        void contextoQueCobrePassa() {
            GeneVectors vetores = contexto().geneVectors();
            StudyPlan plano = new StudyPlan(vetores.index(), new int[]{10, 20, 30});

            vetores.requireCovers(plano);
            vetores.requireCovers(new StudyPlan(Map.of(MATEMATICA, 20)));
        }

        @Test
        @DisplayName("sem subjects(), a ordem vem das importancias — e o que os testes usam")
        void semSubjectsAOrdemVemDasImportancias() {
            Map<Subject, Double> importancias = new LinkedHashMap<>();
            importancias.put(DIREITO, 2.0);
            importancias.put(PORTUGUES, 1.0);

            EvolutionContext contexto = EvolutionContext.builder()
                    .importanceScores(importancias)
                    .minimumDaysPerSubject(Map.of())
                    .planningHorizonDays(180)
                    .hoursPerStudyDay(4)
                    .maxDailyCognitiveLoad(20)
                    .build();

            assertThat(contexto.geneVectors().index().subjects())
                    .containsExactly(DIREITO, PORTUGUES);
        }
    }

    @Nested
    @DisplayName("O caminho de seguranca vale para os operadores geneticos")
    class OperadoresComPlanoDaFronteira {

        @Test
        @DisplayName("cruzar um plano da fronteira com um alinhado nao troca os dias de disciplina")
        void cruzarPlanoDaFronteiraNaoTrocaDias() {
            // Os dois pais tem a MESMA alocacao, expressa em ordens diferentes. Qualquer que seja o
            // ponto de corte sorteado, o filho tem de sair com essa mesma alocacao: se a leitura
            // por posicao ignorasse a ordem do pai da fronteira, sairiam os dias de outra
            // disciplina, e o teste acusaria.
            EvolutionContext contexto = contexto();
            SubjectIndex ordem = contexto.geneVectors().index();

            Individual alinhado = new Individual(new StudyPlan(ordem, new int[]{10, 20, 30}));
            Map<Subject, Integer> daFronteira = new LinkedHashMap<>();
            daFronteira.put(DIREITO, 30);
            daFronteira.put(MATEMATICA, 20);
            daFronteira.put(PORTUGUES, 10);
            Individual desalinhado = new Individual(new StudyPlan(daFronteira));
            alinhado.setFitness(0.5);
            desalinhado.setFitness(0.5);

            var cruzamento = new com.ia.project.dynamicstudyplanner.ga.strategy.crossover
                    .RepairingCrossover();
            for (int tentativa = 0; tentativa < 20; tentativa++) {
                StudyPlan filho = cruzamento.crossover(alinhado, desalinhado, 1.0, contexto).getPlan();

                assertThat(filho.getIndex()).isSameAs(ordem);
                assertThat(filho.getDaysForSubject(PORTUGUES)).isEqualTo(10);
                assertThat(filho.getDaysForSubject(MATEMATICA)).isEqualTo(20);
                assertThat(filho.getDaysForSubject(DIREITO)).isEqualTo(30);
            }
        }

        @Test
        @DisplayName("a media ponderada de dois planos iguais devolve o mesmo plano")
        void mediaPonderadaDeIguaisDevolveOMesmo() {
            EvolutionContext contexto = contexto();
            SubjectIndex ordem = contexto.geneVectors().index();

            Individual alinhado = new Individual(new StudyPlan(ordem, new int[]{10, 20, 30}));
            Map<Subject, Integer> daFronteira = new LinkedHashMap<>();
            daFronteira.put(DIREITO, 30);
            daFronteira.put(MATEMATICA, 20);
            daFronteira.put(PORTUGUES, 10);
            Individual desalinhado = new Individual(new StudyPlan(daFronteira));
            alinhado.setFitness(0.7);
            desalinhado.setFitness(0.3);

            var cruzamento = new com.ia.project.dynamicstudyplanner.ga.strategy.crossover
                    .WeightedAverageCrossover();
            StudyPlan filho = cruzamento.crossover(alinhado, desalinhado, 1.0, contexto).getPlan();

            assertThat(filho.getDaysForSubject(PORTUGUES)).isEqualTo(10);
            assertThat(filho.getDaysForSubject(MATEMATICA)).isEqualTo(20);
            assertThat(filho.getDaysForSubject(DIREITO)).isEqualTo(30);
        }

        @Test
        @DisplayName("sem cruzamento, o filho e uma copia do pai mais apto")
        void semCruzamentoOFilhoCopiaOPaiMaisApto() {
            // Taxa 0.0: o sorteio nunca passa, entao o caminho tomado e o do retorno antecipado.
            EvolutionContext contexto = contexto();
            SubjectIndex ordem = contexto.geneVectors().index();

            Individual fraco = new Individual(new StudyPlan(ordem, new int[]{10, 20, 30}));
            Individual forte = new Individual(new StudyPlan(ordem, new int[]{30, 20, 10}));
            fraco.setFitness(0.1);
            forte.setFitness(0.9);

            var reparador = new com.ia.project.dynamicstudyplanner.ga.strategy.crossover
                    .RepairingCrossover();
            var media = new com.ia.project.dynamicstudyplanner.ga.strategy.crossover
                    .WeightedAverageCrossover();

            assertThat(reparador.crossover(fraco, forte, 0.0, contexto).getPlan())
                    .isSameAs(forte.getPlan());
            assertThat(reparador.crossover(forte, fraco, 0.0, contexto).getPlan())
                    .isSameAs(forte.getPlan());
            assertThat(media.crossover(fraco, forte, 0.0, contexto).getPlan())
                    .isSameAs(forte.getPlan());
            assertThat(media.crossover(forte, fraco, 0.0, contexto).getPlan())
                    .isSameAs(forte.getPlan());
        }
    }

    /** Contexto com as tres disciplinas do edital, na ordem do edital. */
    private static EvolutionContext contexto() {
        Map<Subject, Double> importancias = new LinkedHashMap<>();
        Map<Subject, Integer> pisos = new LinkedHashMap<>();
        for (Subject disciplina : EDITAL) {
            importancias.put(disciplina, (double) disciplina.questionCount());
            pisos.put(disciplina, disciplina == MATEMATICA ? 9 : 5);
        }
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .subjects(EDITAL)
                .minimumDaysPerSubject(pisos)
                .planningHorizonDays(180)
                .hoursPerStudyDay(4)
                .maxDailyCognitiveLoad(20)
                .build();
    }
}

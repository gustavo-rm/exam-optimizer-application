package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.GeneticAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.config.GeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.ga.generator.PopulationGenerator;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Orquestra uma otimização: prepara, popula, evolui e empacota o resultado.
 *
 * <h2>O que esta classe faz — e o que ela deixou de fazer</h2>
 *
 * O diagnóstico de estrutura registrou como achado <b>E8</b> que esta classe, com 192 linhas,
 * misturava quatro assuntos: cálculo de domínio, orquestração do AG, observabilidade e montagem do
 * contexto da evolução. Três motivos independentes de mudança disputavam o mesmo arquivo.
 *
 * <p>Restou aqui <b>a orquestração</b>, e só ela: a sequência de passos e o laço de gerações. Os
 * outros assuntos foram para colaboradores com um motivo de mudança cada:
 *
 * <ul>
 *   <li>{@link EvolutionContextAssembler} — o que a evolução precisa saber (absorveu também as
 *       calculadoras de domínio, que existiam apenas para alimentá-lo).</li>
 *   <li>{@link OptimizationMetrics} — quantas rodaram e quanto demoraram.</li>
 * </ul>
 *
 * <p>O teste de leitura: um método público de dez linhas em que cada passo é uma chamada nomeada.
 * Quem precisa saber <i>como</i> o contexto é montado abre a outra classe; quem precisa entender a
 * sequência não é obrigado a passar por ela.
 */
@Service
public class StudyOptimizerService {

    private static final Logger log = LoggerFactory.getLogger(StudyOptimizerService.class);

    /** A cada quantas gerações o progresso vai para o log de rastreio. */
    private static final int INTERVALO_DE_RASTREIO = 5;

    private final EvolutionContextAssembler contextAssembler;
    private final GeneticAlgorithmFactory gaFactory;
    private final PopulationGenerator populationGenerator;
    private final OptimizationMetrics metrics;

    public StudyOptimizerService(EvolutionContextAssembler contextAssembler,
                                 GeneticAlgorithmFactory gaFactory,
                                 PopulationGenerator populationGenerator,
                                 OptimizationMetrics metrics) {
        this.contextAssembler = contextAssembler;
        this.gaFactory = gaFactory;
        this.populationGenerator = populationGenerator;
        this.metrics = metrics;
    }

    /**
     * Roda o algoritmo genético sem semente declarada.
     *
     * <p>Não toca em {@link RandomProvider}: a fonte da thread corrente é usada como está. É o que
     * mantém funcionando quem semeia por fora antes de chamar — {@code GaResultadoInalteradoTest} e
     * os benchmarks — e é por isso que esta sobrecarga existe em vez de passar {@code null} no
     * ponto de chamada.
     *
     * @param exam           o edital, com todas as regras e disciplinas
     * @param profile        o perfil do estudante, com todos os fatores pessoais
     * @param totalDays      total de dias "ideais" que o AG pode alocar
     * @param numGenerations número de gerações que o algoritmo vai rodar
     * @param populationSize tamanho da população em cada geração
     * @return o melhor plano encontrado, sua fitness e os dados da execução
     */
    public OptimizationResult optimize(
            Exam exam,
            StudentProfile profile,
            int totalDays,
            int numGenerations,
            int populationSize
    ) {
        return optimize(exam, profile, totalDays, numGenerations, populationSize, null);
    }

    /**
     * Roda o algoritmo genético, opcionalmente com uma semente declarada.
     *
     * <h2>Por que a semente é fixada AQUI, e não em cada ponto de entrada</h2>
     *
     * Este método é o único lugar por onde toda execução do AG passa: o caminho síncrono
     * ({@code DynamicStudyPlannerService}), o assíncrono
     * ({@code OptimizationJobService.GenerateStudyPlanUseCaseHolder}) e os benchmarks chamam todos
     * por aqui. Fixar em um ponto só evita que um ponto de entrada futuro nasça sem a restauração —
     * que é a metade que importa.
     *
     * <h2>Por que o {@code finally} não é zelo</h2>
     *
     * {@code @Async("optimizerTaskExecutor")} <b>reaproveita as threads do pool</b>. Sem a
     * restauração, a semente instalada por uma requisição continuaria instalada na thread quando a
     * próxima requisição caísse nela, e uma execução que não pediu semente alguma passaria a
     * produzir o plano de outra — de forma intermitente, dependendo de qual thread a atendeu. A
     * extensão de teste {@code support/RandomProviderIsolation} resolve o mesmo problema entre
     * testes, pela mesma razão; aqui a ideia é reaproveitada, não o código.
     *
     * <p>Com {@code randomSeed} nulo o provedor <b>não é tocado</b>, nem para ler nem para escrever.
     *
     * @param exam           o edital, com todas as regras e disciplinas
     * @param profile        o perfil do estudante, com todos os fatores pessoais
     * @param totalDays      total de dias "ideais" que o AG pode alocar
     * @param numGenerations número de gerações que o algoritmo vai rodar
     * @param populationSize tamanho da população em cada geração
     * @param randomSeed     semente para tornar a execução reproduzível, ou {@code null} para
     *                       deixar a fonte da thread como está
     * @return o melhor plano encontrado, sua fitness e os dados da execução
     */
    public OptimizationResult optimize(
            Exam exam,
            StudentProfile profile,
            int totalDays,
            int numGenerations,
            int populationSize,
            Long randomSeed
    ) {
        if (randomSeed == null) {
            return run(exam, profile, totalDays, numGenerations, populationSize);
        }
        Random anterior = RandomProvider.getInstance();
        RandomProvider.setInstance(new Random(randomSeed));
        try {
            return run(exam, profile, totalDays, numGenerations, populationSize);
        } finally {
            RandomProvider.setInstance(anterior);
        }
    }

    private OptimizationResult run(
            Exam exam,
            StudentProfile profile,
            int totalDays,
            int numGenerations,
            int populationSize
    ) {
        // O contexto e capturado para que a decomposicao da fitness (GAP-07) possa ser montada
        // DEPOIS do bloco medido. Montar o contexto continua dentro dele, onde sempre esteve: tira-lo
        // dali mudaria o que executionTimeMillis e a metrica reportam.
        EvolutionContext[] capturado = new EvolutionContext[1];
        OptimizationMetrics.Timed<Individual> melhor = metrics.recordRun(() -> {
            EvolutionContext context = contextAssembler.assemble(exam, profile);
            capturado[0] = context;
            GeneticAlgorithm ga = gaFactory.create();
            Population inicial = populationGenerator.generate(exam, totalDays, populationSize, context);
            return runEvolution(inicial, ga, numGenerations, context).getFittest();
        });

        return new OptimizationResult(
                melhor.resultado().getPlan(),
                melhor.resultado().getFitness(),
                numGenerations,
                melhor.duracaoMs(),
                decompor(melhor.resultado(), capturado[0])
        );
    }

    /**
     * Decompõe a fitness do melhor indivíduo, uma vez por execução.
     *
     * <p>Fora do bloco medido, porque é construção de resposta e não otimização. Uma chamada, sobre
     * um indivíduo — não as centenas de milhares que {@code evaluate} atende dentro do laço.
     *
     * @param melhor  o indivíduo vencedor
     * @param context o contexto que a execução usou
     * @return a decomposição, ou {@code null} quando o contexto não trouxe avaliador — o que na
     *         prática não ocorre, já que sem ele nenhum indivíduo teria fitness para comparar
     */
    private static FitnessBreakdown decompor(Individual melhor, EvolutionContext context) {
        if (context == null || context.fitnessEvaluator() == null) {
            return null;
        }
        return context.fitnessEvaluator().explain(melhor.getPlan(), context);
    }

    /**
     * Roda o laço principal da evolução pelo número de gerações pedido.
     *
     * @param initialPopulation a população inicial
     * @param ga                o motor genético já configurado
     * @param numGenerations    o número de gerações a rodar
     * @param context           o contexto da evolução
     * @return a população final, mais evoluída
     */
    private Population runEvolution(Population initialPopulation, GeneticAlgorithm ga,
                                    int numGenerations, EvolutionContext context) {
        Population population = initialPopulation;
        for (int i = 0; i < numGenerations; i++) {
            population = ga.evolvePopulation(population, context);

            if (i % INTERVALO_DE_RASTREIO == 0 && log.isTraceEnabled()) {
                log.trace(
                        "Generation {} | Best Fitness: {} | Avg Fitness: {} | Worst Fitness: {}",
                        String.format("%-4d", i),
                        String.format("%-8.2f", population.getFittest().getFitness()),
                        String.format("%-8.2f", population.getAverageFitness()),
                        String.format("%-8.2f", population.getWorst().getFitness())
                );
            }
        }
        log.debug("Evolution complete after {} generations. Final best fitness: {}",
                numGenerations, String.format("%.2f", population.getFittest().getFitness()));
        return population;
    }
}

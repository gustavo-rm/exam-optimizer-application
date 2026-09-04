package com.ia.project.dynamicstudyplanner.util;

import java.util.Random;

/**
 * Ponto único de acesso à aleatoriedade do algoritmo genético — uma fonte <b>por thread</b>.
 *
 * <h2>Por que um ponto de acesso estático, e não injeção de dependência</h2>
 *
 * Os operadores genéticos — seleção, cruzamento, mutação, reparo — sorteiam em pontos muito
 * internos. Injetar a fonte por construtor obrigaria a atravessá-la por toda a hierarquia de
 * estratégias, e o ganho seria só arquitetural. O acesso estático mantém um <b>lugar único</b> onde
 * a semente pode ser fixada em teste, que é o que de fato importa.
 *
 * <h2>Por que NÃO é {@code SecureRandom}</h2>
 *
 * Até a etapa 05b o padrão era {@code new SecureRandom()}. Isso custava caro e não comprava nada:
 *
 * <ul>
 *   <li><b>~31 % de todo o tempo de CPU</b> do algoritmo genético era gasto dentro de
 *       {@code sun.security.provider} — medido por amostragem do JFR sobre a aplicação real
 *       ({@code docs/qualidade/05-diagnostico-performance.md}, achado F1).</li>
 *   <li>Trocar a fonte cortou <b>41 % do pior caso</b> e <b>54 % do caso típico</b>, sem alterar
 *       nenhuma decisão do algoritmo.</li>
 * </ul>
 *
 * <p>{@code SecureRandom} passa cada número por SHA-1 e consulta entropia do sistema operacional,
 * porque o requisito dele é ser <b>imprevisível para um adversário</b>. Um algoritmo genético não
 * tem adversário: ele precisa de números <b>bem distribuídos</b>, e é só isso que
 * {@link Random} promete e entrega.
 *
 * <p><b>Verificação de segurança feita antes da troca.</b> Todos os dez usos desta classe em
 * {@code src/main} estão dentro de operadores do AG — {@code TournamentSelection},
 * {@code HybridCrossover}, {@code RepairingCrossover}, {@code WeightedAverageCrossover},
 * {@code ChildGeneRepair}, {@code AbstractMutationStrategy}, {@code CreepMutation},
 * {@code StudyPlanFactory} e os dois operadores táticos. <b>Nenhum</b> gera identificador, token,
 * senha, nonce, material de sessão ou qualquer valor que um terceiro pudesse tentar prever para
 * obter vantagem. Se algum dia um uso desses aparecer, ele deve instanciar {@code SecureRandom}
 * diretamente no ponto de uso — <b>não</b> voltar o padrão daqui.
 *
 * <h2>Por que {@code java.util.Random} e não algo ainda mais rápido</h2>
 *
 * Existem geradores mais rápidos ({@code SplittableRandom}, {@code ThreadLocalRandom}, as famílias
 * {@code L64X128MixRandom} do JDK 17+). Nenhum foi adotado, por duas razões:
 *
 * <ol>
 *   <li><b>{@code ThreadLocalRandom} não é semeável.</b> A reprodutibilidade das execuções foi
 *       conquistada a duras penas — {@code RobustnessMain} só passou a reportar 8 de 8 instâncias
 *       reproduzíveis depois que os sorteios foram roteados para cá. Perdê-la seria trocar um
 *       problema medido por outro pior.</li>
 *   <li><b>Testes e benchmarks já usam {@code new Random(semente)}.</b> Adotar aqui a mesma classe
 *       que eles usam elimina uma assimetria que era, ela própria, um achado: o
 *       {@code ProductionGeneticAlgorithm} declara medir a produção "as shipped" e trocava, na
 *       primeira linha, o componente que respondia por 41 % do tempo. Agora produção e medição
 *       usam o mesmo gerador, e o número do benchmark passa a valer para produção.</li>
 * </ol>
 *
 * <h2>Por que uma fonte POR THREAD, e não uma só para o processo (achado E3)</h2>
 *
 * Até a etapa 06b esta classe guardava <b>uma única</b> instância {@code static} para o processo
 * inteiro. {@code Random} é seguro para uso concorrente — sincroniza a semente por
 * <i>compare-and-set</i> (CAS: a thread lê o valor, calcula o próximo e só grava se ninguém tiver
 * mudado no meio; se mudou, refaz) — então não havia corrupção. Havia desperdício: sob concorrência,
 * esse "refaz" é trabalho jogado fora, e o algoritmo sorteia em quinze pontos dos laços mais
 * quentes.
 *
 * <p>O custo foi <b>medido</b> na etapa 06, rodando otimizações completas em paralelo e comparando
 * a fonte única contra uma por thread (medianas de 5 baterias):
 *
 * <pre>
 *   threads   fonte única   uma por thread   custo da fonte única
 *         1        233 ms           238 ms   -2 % (ruído)
 *         2        269 ms           256 ms   +5 %
 *         4        393 ms           231 ms   +70 %
 *         8        741 ms           457 ms   +62 %
 * </pre>
 *
 * <p>Repare na coluna do meio: com uma fonte por thread, 4 threads fazem 4× o trabalho no mesmo
 * tempo que 1 thread — escalonamento praticamente linear. A fonte única destruía isso, e a réplica
 * entregava ~59 % da capacidade que o hardware permitia.
 *
 * <p><b>A reprodutibilidade continua intacta, e é por isso que não se usou
 * {@code ThreadLocalRandom}.</b> A semente é fixável por thread: um teste que chama
 * {@link #setInstance} e roda o algoritmo na mesma thread — que é o que todos fazem — obtém
 * exatamente o mesmo plano de antes. As 9 assinaturas de referência da etapa 05b foram conferidas
 * depois da mudança e não mudaram.
 */
public final class RandomProvider {

    /**
     * Uma fonte por thread. Cada thread que pedir a primeira vez recebe a sua, e a mantém — o que
     * elimina a disputa pela semente sem exigir que ninguém passe a fonte por parâmetro.
     *
     * <p>O consumo é limitado pelo número de threads que rodam otimizações (o pool do executor mais
     * as threads de teste), não pelo número de requisições.
     */
    private static final ThreadLocal<Random> POR_THREAD = ThreadLocal.withInitial(Random::new);

    private RandomProvider() {
    }

    public static Random getInstance() {
        return POR_THREAD.get();
    }

    /**
     * Fixa a fonte de aleatoriedade <b>da thread atual</b>. Usado por testes e benchmarks para
     * tornar uma execução reproduzível.
     *
     * <p><b>O alcance mudou na etapa 06b e isso é deliberado.</b> Antes, esta chamada trocava a
     * fonte do processo inteiro; agora troca só a da thread que chama. Para o uso real não há
     * diferença — quem semeia roda o algoritmo em seguida, na mesma thread. Para o uso indevido há:
     * semear numa thread esperando afetar outra deixou de funcionar, e isso é uma correção, não uma
     * regressão. Duas execuções concorrentes com sementes distintas agora não interferem uma na
     * outra, o que antes era exatamente o que acontecia.
     *
     * <p>A extensão {@code support/RandomProviderIsolation} restaura a fonte anterior ao fim de
     * cada teste, para que uma semente não vaze para o teste seguinte na mesma thread.
     */
    public static void setInstance(Random random) {
        POR_THREAD.set(random);
    }
}

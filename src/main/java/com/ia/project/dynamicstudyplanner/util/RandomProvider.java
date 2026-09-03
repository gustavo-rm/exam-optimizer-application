package com.ia.project.dynamicstudyplanner.util;

import java.util.Random;

/**
 * Fonte única de aleatoriedade do algoritmo genético.
 *
 * <h2>Por que um singleton, e não injeção de dependência</h2>
 *
 * Os operadores genéticos — seleção, cruzamento, mutação, reparo — sorteiam em pontos muito
 * internos. Injetar a fonte por construtor obrigaria a atravessá-la por toda a hierarquia de
 * estratégias, e o ganho seria só arquitetural. O singleton mantém um <b>ponto único</b> onde a
 * semente pode ser fixada em teste, que é o que de fato importa.
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
 * <p>{@code Random} é seguro para uso concorrente (usa <i>compare-and-set</i> sobre a semente), o
 * que importa porque a avaliação de fitness roda em várias threads. A contenção está incluída nos
 * números acima: o A/B foi medido com a fitness paralela ligada.
 */
public final class RandomProvider {

    private static Random instance = new Random();

    private RandomProvider() {
    }

    public static Random getInstance() {
        return instance;
    }

    /**
     * Fixa a fonte de aleatoriedade. Usado por testes e benchmarks para tornar uma execução
     * reproduzível.
     *
     * <p>A extensão {@code support/RandomProviderIsolation} restaura a fonte anterior ao fim de
     * cada teste, para que uma semente não vaze para o teste seguinte.
     */
    public static void setInstance(Random random) {
        instance = random;
    }
}

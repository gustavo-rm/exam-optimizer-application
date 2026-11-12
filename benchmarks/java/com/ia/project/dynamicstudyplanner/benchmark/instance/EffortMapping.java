package com.ia.project.dynamicstudyplanner.benchmark.instance;

/**
 * Eixo faixa de esforço da medição de curadoria (EOA-1).
 *
 * <h2>A faixa entra no Core por dois canais, e só um deles é configuração</h2>
 *
 * <ol>
 *   <li><b>Minutos.</b> A plataforma converte a faixa em {@code estimatedMinutes} pela tabela
 *       {@code sinapse.curriculum.effort-tier-duration} ({@code application.yml}: 25, 50, 90 e 150
 *       minutos, declarados "AN ASSUMPTION, NOT A FINDING"). O Core recebe só o número. Os três motores
 *       leem este canal.</li>
 *   <li><b>Banda de dificuldade.</b> {@code EffortTierBands} converte a mesma faixa em 1..4, fixo no
 *       código e não configurável, e a banda alimenta {@code LearningModel} (constante de tempo do
 *       domínio, sessões exigidas) e o teto de carga. Só os dois motores genéticos leem este canal; o
 *       guloso nunca lê {@code effortTier}.</li>
 * </ol>
 *
 * Por isso "todas as bandas valendo o mesmo em minutos" <b>não</b> é inerte por construção para o
 * genético: a banda continua chegando por (2). Os níveis abaixo separam os dois canais:
 *
 * <pre>
 *   UNIFORM   toda faixa STANDARD, 50 min        o curador não anotou faixa nenhuma: os dois canais mudos
 *   FLAT      faixas do catálogo, todas 50 min   só o canal (2); para o guloso é idêntico a UNIFORM
 *   NARROW    faixas do catálogo, 35/50/67/87    os dois canais, minutos comprimidos
 *   PLATFORM  faixas do catálogo, 25/50/90/150   os dois canais, a tabela de hoje da plataforma
 * </pre>
 *
 * <p>{@code NARROW} é a média geométrica entre {@code FLAT} e {@code PLATFORM} — cada valor é
 * {@code 50 × (platform / 50)^½}, arredondado —, para que a tabela de hoje fique no meio de uma
 * escala e não na ponta dela: a calibração pelo piloto pode mover os números para qualquer lado.
 */
public enum EffortMapping {

    /** Nenhuma faixa anotada: toda faixa é {@code STANDARD}. */
    UNIFORM("uniform", false, 50, 50, 50, 50),

    /** Faixas anotadas, minutos todos iguais. */
    FLAT("tiers-flat", true, 50, 50, 50, 50),

    /** Faixas anotadas, minutos comprimidos. */
    NARROW("tiers-narrow", true, 35, 50, 67, 87),

    /** Faixas anotadas, a tabela atual da plataforma. */
    PLATFORM("tiers-platform", true, 25, 50, 90, 150);

    /** O conjunto fechado, na ordem ordinal. */
    public static final String[] TIERS = {"SHORT", "STANDARD", "LONG", "EXTENDED"};

    /** A faixa que um tópico recebe quando ninguém anotou. */
    public static final int DEFAULT_TIER = 1;

    private final String id;
    private final boolean tiersCurated;
    private final int[] minutes;

    EffortMapping(String id, boolean tiersCurated, int... minutes) {
        this.id = id;
        this.tiersCurated = tiersCurated;
        this.minutes = minutes;
    }

    /** @return o nome gravado no CSV */
    public String id() {
        return id;
    }

    /**
     * A faixa que vai no pedido.
     *
     * @param curatedTier a faixa latente do tópico, índice em {@link #TIERS}
     * @return o índice em {@link #TIERS} que o pedido carrega neste nível
     */
    public int tierSent(int curatedTier) {
        return tiersCurated ? curatedTier : DEFAULT_TIER;
    }

    /**
     * Os minutos de uma faixa neste nível.
     *
     * @param tier índice em {@link #TIERS}
     * @return {@code estimatedMinutes}
     */
    public int minutesOf(int tier) {
        return minutes[tier];
    }

    /** @return a tabela, {@code 25/50/90/150}, para gravar no CSV */
    public String table() {
        return minutes[0] + "/" + minutes[1] + "/" + minutes[2] + "/" + minutes[3];
    }
}

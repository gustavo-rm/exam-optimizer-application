package com.ia.project.dynamicstudyplanner.plan;

import java.util.Arrays;
import java.util.List;

/**
 * Como um motor trata as preferências de ordem {@code SOFT}: corrigindo-as ou precificando-as.
 *
 * <h2>Por que este eixo existe</h2>
 *
 * Até EOA-11 a política vinha presa à representação, e o experimento não sabia separá-las:
 *
 * <ul>
 *   <li>o motor macro ({@code ga}) <b>reparava</b> — {@link PrerequisiteOrderRepairer} reordena
 *       dentro do que as arestas rígidas permitem, sem consultar a fitness;</li>
 *   <li>o motor de linha do tempo ({@code ga-timeline}) <b>precificava</b> — o termo
 *       {@code SoftPrerequisiteOrderConstraint} entra na busca com peso {@code λ}.</li>
 * </ul>
 *
 * <p>Logo a comparação entre os dois media <b>duas</b> mudanças de uma vez: a representação e a
 * política. A varredura de λ ({@code docs/revisao-ag/11-varredura-peso-ordem.md}) mostrou que a
 * política não se reduz ao peso — nenhum λ admissível alcança o total de inversões do reparo —, o que
 * confirma que são eixos distintos e não dois pontos do mesmo eixo.
 *
 * <p>Com este enum os dois cruzam, e as quatro células do fatorial dizem qual fator explica o quê.
 *
 * <h2>Reparo FIXA a métrica; peso apenas a precifica</h2>
 *
 * A diferença é categórica e vale escrever: o reparo é lexicográfico — remove inversões até onde as
 * arestas rígidas deixam, custe o que custar nas outras dimensões. O peso é uma troca: a busca aceita
 * uma inversão quando o resto do plano compensa. Incentivo não iguala garantia na dimensão garantida,
 * e esperar que igualasse seria confundir os dois mecanismos.
 */
public enum PrecedencePolicy {

    /** Reparo lexicográfico: remove inversões sem consultar a fitness. */
    LEXICOGRAPHIC("lexicographic"),

    /** Preço ponderado: a inversão residual entra na fitness e a busca decide. */
    WEIGHTED("weighted");

    /** A chave de {@code algorithmParams} que carrega a escolha. */
    public static final String PARAM = "precedence";

    /** A chave sob a qual a política escolhida é ecoada em {@code fitness}. */
    public static final String FITNESS_KEY = "precedence-policy";

    private final String id;

    PrecedencePolicy(String id) {
        this.id = id;
    }

    /** @return o identificador estável desta política */
    public String id() {
        return id;
    }

    /**
     * A política de um identificador.
     *
     * @param id o identificador
     * @return a política, ou {@code null} quando o identificador não é de nenhuma
     */
    public static PrecedencePolicy byId(String id) {
        return Arrays.stream(values())
                .filter(policy -> policy.id.equals(id))
                .findFirst()
                .orElse(null);
    }

    /** @return os identificadores, para uma mensagem de erro em que o chamador possa agir */
    public static List<String> ids() {
        return Arrays.stream(values()).map(PrecedencePolicy::id).toList();
    }
}

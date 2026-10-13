package com.ia.project.dynamicstudyplanner.plan;

import org.springframework.boot.info.GitProperties;

/**
 * Qual código produziu um plano: o commit do build, estampado em {@code fitness.build}.
 *
 * <h2>Por que não {@code metadata.coreVersion}</h2>
 *
 * {@code coreVersion} é a versão Maven, que ficou em {@code 2.0.1} por mais de cento e sessenta
 * commits; ela não distingue um build de outro. Com os hiperparâmetros do AG sendo do Core (D4,
 * {@code docs/adr/0009-hiperparametros-sao-do-core.md}), saber qual código rodou é o que torna um
 * plano registrado reproduzível. Mudar {@code coreVersion} seria mudar o significado de um campo do
 * contrato; uma chave a mais no mapa aberto de {@code fitness} não muda o contrato.
 *
 * <h2>De onde vem</h2>
 *
 * De {@code git.properties}, gerado no build pelo {@code git-commit-id-maven-plugin} e exposto pelo
 * Spring Boot como {@link GitProperties}. O valor é o SHA completo do commit, com o sufixo
 * {@value #DIRTY_SUFFIX} quando a árvore tinha mudanças não commitadas — um build sujo não é o commit
 * que nomeia, e dizer só o SHA afirmaria o contrário.
 *
 * <p>Sem o arquivo — um build feito fora de um clone git — o valor é {@value #UNKNOWN}. Falhar na
 * subida seria a regra deste repositório para configuração obrigatória, mas aqui impediria um build a
 * partir de um pacote de fontes sem dizer nada que o {@code unknown} já não diga.
 *
 * @param id o SHA do commit, com o sufixo de sujeira quando houver, ou {@value #UNKNOWN}
 */
public record BuildIdentity(String id) {

    /** A chave em {@code fitness}. */
    public static final String FITNESS_KEY = "build";

    /** O valor quando o build não registrou commit. */
    public static final String UNKNOWN = "unknown";

    /** O sufixo de um build feito com mudanças não commitadas. */
    public static final String DIRTY_SUFFIX = "-dirty";

    /**
     * @param git o conteúdo de {@code git.properties}, ou {@code null} quando o build não o gerou
     * @return a identidade do build
     */
    public static BuildIdentity from(GitProperties git) {
        if (git == null || git.getCommitId() == null) {
            return new BuildIdentity(UNKNOWN);
        }
        boolean dirty = Boolean.parseBoolean(git.get("dirty"));
        return new BuildIdentity(git.getCommitId() + (dirty ? DIRTY_SUFFIX : ""));
    }
}

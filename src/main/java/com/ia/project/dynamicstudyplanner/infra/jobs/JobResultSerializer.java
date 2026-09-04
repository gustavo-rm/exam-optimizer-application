package com.ia.project.dynamicstudyplanner.infra.jobs;

import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;

/**
 * Converte o resultado de domínio no texto que vai para o registro do trabalho.
 *
 * <h2>Por que esta interface existe — uma dependência invertida</h2>
 *
 * O serviço que executa os trabalhos precisa guardar o resultado <b>no formato que o cliente vai
 * ler</b>, que é o DTO da camada de API. Fazer o serviço chamar os mapeadores da API diretamente
 * criaria um ciclo {@code service → api → service}, e módulos em ciclo não podem ser compilados,
 * testados nem extraídos separadamente — é a mesma classe de problema que a etapa 03b desfez entre
 * {@code ga} e {@code service} (ADR-0001).
 *
 * <p>A saída é a inversão: o <b>porto</b> mora aqui, junto de quem precisa dele, e a
 * <b>implementação</b> mora na API, junto dos mapeadores que ela usa. O serviço passa a depender de
 * uma interface do próprio módulo, e a seta que atravessa a fronteira aponta em um sentido só.
 *
 * <p>{@code ModuleBoundaryTest} verifica isso: a primeira versão desta etapa reprovou lá, e foi o
 * teste que apontou o defeito antes de ele chegar ao repositório.
 */
public interface JobResultSerializer {

    /**
     * @param resultado o resultado do domínio
     * @return o corpo da resposta já serializado, pronto para ir ao armazenamento
     */
    String serializar(FullPlannerResult resultado);
}

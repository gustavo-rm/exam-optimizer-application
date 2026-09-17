package com.ia.project.dynamicstudyplanner.api.exception;

import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Requisições bem formadas que o domínio recusa por violarem uma regra de negócio.
 *
 * <h2>O que distingue esta classe de {@link RequestErrorAdvice}</h2>
 *
 * A separação não é por código HTTP, é por <b>natureza da causa</b>. Em {@link RequestErrorAdvice}
 * estão os casos em que a API <i>não conseguiu entender</i> o que foi enviado: JSON quebrado, tipo
 * incompatível, campo obrigatório ausente, verbo errado. Aqui estão os casos em que a API entendeu
 * perfeitamente e mesmo assim não pode atender — pedir um plano de estudos para um edital cujas
 * disciplinas exigem, somadas, mais dias do que existem até a prova.
 *
 * <p>Essa é exatamente a distinção que a RFC 9110 faz entre <b>400</b> ({@code Bad Request}: a
 * requisição não foi compreendida) e <b>422</b> ({@code Unprocessable Content}: foi compreendida,
 * mas as instruções não podem ser cumpridas). A diferença é prática para quem consome a API: um
 * {@code 400} se corrige mudando o formato do envio; um {@code 422} só se corrige mudando o
 * <i>pedido</i> — adiar a data da prova, remover disciplinas, aumentar o tempo disponível.
 *
 * <h2>Por que um único tratador basta aqui</h2>
 *
 * Todas as regras de negócio do domínio sinalizam pela mesma exceção,
 * {@link DomainException}. Isso é deliberado: o domínio não conhece HTTP, e não deveria ter uma
 * exceção por status. A tradução para {@code 422} acontece uma vez só, nesta borda.
 *
 * <p>O critério para decidir se uma condição é regra de negócio ({@code DomainException}, 422) ou
 * defeito de quem chama ({@code IllegalArgumentException}, 400) está registrado no
 * <b>ADR-0005</b> — foi ele que motivou a reclassificação de duas verificações de
 * {@code StudyPlanFactory} na etapa 03d.
 *
 * <h2>Regra de log</h2>
 *
 * Nível {@code WARN}, nunca {@code ERROR}: recusar um pedido inviável é o sistema funcionando, não
 * falhando. A mensagem da {@link DomainException} descreve a <b>regra</b> e seus limites numéricos
 * (dias exigidos contra dias disponíveis), não dado pessoal do estudante — a mesma regra de log de
 * {@link RequestErrorAdvice} vale aqui e é travada por {@code security/LogPrivacyTest}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BusinessRuleErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(BusinessRuleErrorAdvice.class);

    /**
     * Regra de negócio violada por uma requisição válida. Devolve <b>422</b>.
     *
     * <p>O {@code detail} repassa a mensagem do domínio porque ela é o único caminho pelo qual o
     * cliente descobre <i>qual</i> regra barrou o pedido e com quais números — sem isso, o 422 seria
     * indistinguível de uma recusa arbitrária.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(
            DomainException ex, HttpServletRequest request) {

        log.warn("Domain rule violated on path {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "domain-rule-violation",
                        ex.getMessage(), request));
    }
}

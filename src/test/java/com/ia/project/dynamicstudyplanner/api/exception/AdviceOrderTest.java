package com.ia.project.dynamicstudyplanner.api.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a ordem de consulta dos três tratadores de erro.
 *
 * <h2>Que regressão este teste impede</h2>
 *
 * {@link InfrastructureErrorAdvice} declara {@code @ExceptionHandler(Exception.class)} — um
 * <i>catch-all</i>, que casa com qualquer exceção. Dentro de uma mesma classe isso é inofensivo,
 * porque o Spring escolhe o tratador de tipo mais específico. <b>Entre classes diferentes o critério
 * é outro:</b> os {@code @RestControllerAdvice} são percorridos na ordem de {@code @Order} e vence o
 * primeiro que tenha algum tratador aplicável, específico ou não.
 *
 * <p>Se a infraestrutura passar à frente, todo erro de cliente — JSON malformado, verbo errado,
 * campo inválido — cai no catch-all e vira <b>500</b>. É exatamente o defeito que a etapa 02b
 * corrigiu (achado S8 de {@code docs/qualidade/02-diagnostico-seguranca.md}), com três consequências
 * medidas: cliente informado errado, pilha completa em nível ERRO a cada erro banal, e alerta por
 * taxa de 5xx inutilizado.
 *
 * <p>A regressão é silenciosa: apagar uma anotação {@code @Order} compila, sobe a aplicação e só
 * aparece como status errado em produção. Os testes de ponta a ponta pegariam o sintoma; este pega a
 * causa, e diz o nome dela.
 *
 * <h2>Por que comparar pelo comparador do Spring, e não pelos números</h2>
 *
 * O que importa não é o valor de cada {@code @Order}, é a <b>ordem relativa</b> que o Spring vai
 * usar. Afirmar os números fixaria uma escolha arbitrária e quebraria o teste em qualquer
 * renumeração legítima. Usar {@link AnnotationAwareOrderComparator} — o mesmo que o Spring aplica
 * para ordenar os <i>advices</i> — verifica a propriedade que de fato importa.
 */
@DisplayName("Contrato de erro: a ordem dos tres @RestControllerAdvice")
class AdviceOrderTest {

    @Test
    @DisplayName("requisicao vem antes de regra de negocio, que vem antes de infraestrutura")
    void aOrdemDeConsultaVaiDoEspecificoParaOCatchAll() {
        // Fora de ordem de proposito: e a ordenacao que precisa acertar, nao a lista de entrada.
        List<Object> advices = new java.util.ArrayList<>(List.of(
                new InfrastructureErrorAdvice(),
                new BusinessRuleErrorAdvice(),
                new RequestErrorAdvice()));

        AnnotationAwareOrderComparator.sort(advices);

        assertThat(advices)
                .as("o catch-all de Exception precisa ser o ultimo consultado, "
                        + "senao todo erro de cliente vira 500")
                .extracting(advice -> advice.getClass().getSimpleName())
                .containsExactly("RequestErrorAdvice", "BusinessRuleErrorAdvice",
                        "InfrastructureErrorAdvice");
    }

    @Test
    @DisplayName("os tres estao anotados como @RestControllerAdvice e com @Order explicita")
    void osTresDeclaramAnotacaoEOrdem() {
        for (Class<?> advice : List.of(RequestErrorAdvice.class, BusinessRuleErrorAdvice.class,
                InfrastructureErrorAdvice.class)) {

            assertThat(advice.getAnnotation(RestControllerAdvice.class))
                    .as("%s precisa ser registrado como advice para tratar erro algum", advice.getSimpleName())
                    .isNotNull();
            assertThat(advice.getAnnotation(Order.class))
                    .as("%s sem @Order explicita cai na ordem indefinida, e a ordem e o contrato aqui",
                            advice.getSimpleName())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("so a infraestrutura declara o catch-all de Exception")
    void oCatchAllViveApenasNaInfraestrutura() {
        assertThat(tratadoresDe(InfrastructureErrorAdvice.class))
                .as("a rede de seguranca do 500 e responsabilidade declarada desta classe")
                .contains(Exception.class);

        for (Class<?> advice : List.of(RequestErrorAdvice.class, BusinessRuleErrorAdvice.class)) {
            assertThat(tratadoresDe(advice))
                    .as("%s nao pode declarar catch-all: sendo consultada antes, engoliria tudo",
                            advice.getSimpleName())
                    .doesNotContain(Exception.class, Throwable.class, RuntimeException.class);
        }
    }

    /** Todos os tipos de excecao declarados em {@code @ExceptionHandler} de uma classe. */
    private List<Class<?>> tratadoresDe(Class<?> advice) {
        return java.util.Arrays.stream(advice.getDeclaredMethods())
                .map(metodo -> metodo.getAnnotation(ExceptionHandler.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(anotacao -> java.util.Arrays.stream(anotacao.value()))
                .collect(java.util.stream.Collectors.toList());
    }
}

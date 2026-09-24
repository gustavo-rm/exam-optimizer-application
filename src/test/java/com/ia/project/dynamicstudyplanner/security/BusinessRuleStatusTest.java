package com.ia.project.dynamicstudyplanner.security;

import com.ia.project.dynamicstudyplanner.api.exception.BusinessRuleErrorAdvice;
import com.ia.project.dynamicstudyplanner.api.exception.RequestErrorAdvice;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import com.ia.project.dynamicstudyplanner.ga.factory.StudyPlanFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O {@code 422} deixou de ser decorativo — achado E3.
 *
 * <h2>O que estava errado</h2>
 *
 * A etapa 02 concluiu que o tratador de {@code 422} era <b>inalcançável pela API</b>, e a etapa 03
 * encontrou a causa raiz: {@code DomainException} era lançada uma única vez no código inteiro,
 * enquanto treze {@code IllegalArgumentException} carregavam regras de negócio e viravam
 * {@code 400}. Metade do contrato de erro documentado no {@code README.md} era decorativa.
 *
 * <h2>A distinção que este teste trava</h2>
 *
 * A RFC 9110 separa as duas situações com precisão: <b>400</b> é "não consegui entender a
 * requisição"; <b>422</b> é "entendi perfeitamente, mas não posso processar". Quem pede um plano
 * cujo piso de dias mínimos não cabe no orçamento enviou uma requisição impecável — é a instância
 * que é insatisfazível. Devolver 400 sugere que ele digitou algo errado; devolver 422 diz a verdade.
 *
 * <h2>Por que este teste deixou de subir o contexto em EOA-4b</h2>
 *
 * Ele postava um edital de 25 disciplinas no endpoint síncrono de concurso e lia o 422
 * pela porta HTTP. O endpoint saiu, e no caminho que ficou <b>nenhuma requisição chega a produzir
 * uma {@code DomainException}</b>: {@code GeneticPlanEngine} pisa o orçamento no somatório dos
 * pisos antes de gerar a população, e {@code PlanRequestGuard} recusa o que sobraria — com um 422
 * próprio, do controlador, que {@code plan/PlanControllerErrorMappingTest} cobre.
 *
 * <p>Fingir um cenário HTTP que não existe seria pior do que não testar: passaria a proteger uma
 * encenação. O que continua valendo, e é o que este arquivo trava, são as duas metades separadas —
 * o domínio ainda distingue regra de negócio de argumento malformado, e o <i>advice</i> ainda
 * traduz essa distinção em 422 contra 400.
 */
@DisplayName("E3: regra de negocio violada devolve 422")
class BusinessRuleStatusTest {

    private static final String PATH = "/plans";

    private static MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setRequestURI(PATH);
        return request;
    }

    @Test
    @DisplayName("piso de dias minimos acima do orcamento e DomainException, nao IllegalArgument")
    void pisoAcimaDoOrcamentoEhRegraDeNegocio() {
        PlanningItem um = new PlanningItem("T1", "T1", 3);
        PlanningItem dois = new PlanningItem("T2", "T2", 3);

        assertThatThrownBy(() -> new StudyPlanFactory().createRandomPlan(null,
                List.of(um, dois), 5, Map.of(um, 10, dois, 10)))
                .as("""
                        O pedido e bem formado: quem chama nao tem nada a corrigir na sintaxe.
                        O que o impede e uma regra de negocio — os topicos exigem mais dias do que
                        o orcamento tem. Isso e 422, nao 400 (RFC 9110).""")
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Total minimum study days required");
    }

    @Test
    @DisplayName("o advice traduz DomainException em 422 com corpo RFC 7807")
    void oAdviceTraduzEm422() {
        ResponseEntity<ProblemDetail> resposta = new BusinessRuleErrorAdvice()
                .handleDomainException(
                        new DomainException("Total minimum study days required (20) exceeds "
                                + "total available days (5)."),
                        requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ProblemDetail corpo = resposta.getBody();
        assertThat(corpo).isNotNull();
        assertThat(corpo.getStatus()).isEqualTo(422);
        assertThat(corpo.getTitle()).isEqualTo("Unprocessable Entity");
        assertThat(String.valueOf(corpo.getType())).endsWith("domain-rule-violation");
        assertThat(corpo.getDetail())
                .as("o detalhe repassa a mensagem do dominio: sem ela o 422 seria indistinguivel "
                        + "de uma recusa arbitraria")
                .contains("Total minimum study days required");
    }

    @Test
    @DisplayName("o 400 continua sendo 400 para erro de sintaxe — a distincao nao foi perdida")
    void erroDeSintaxeContinua400() {
        // Contraprova. Reclassificar demais seria tao errado quanto reclassificar de menos: se todo
        // erro virasse 422, o cliente perderia a informacao de que ha algo a corrigir no pedido.
        ResponseEntity<ProblemDetail> resposta = new RequestErrorAdvice()
                .handleIllegalArgumentException(
                        new IllegalArgumentException("Total available days cannot be negative."),
                        requisicao());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getStatus()).isEqualTo(400);
    }
}

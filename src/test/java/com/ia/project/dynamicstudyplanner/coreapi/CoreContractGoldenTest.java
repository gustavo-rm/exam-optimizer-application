package com.ia.project.dynamicstudyplanner.coreapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Trava a forma de fio do contrato do Core contra um documento de referência.
 *
 * <h2>Por que um documento dourado, e não um módulo Maven compartilhado</h2>
 *
 * A decisão é <b>não publicar artefato compartilhado</b>: cada repositório mantém a sua cópia dos
 * records e os dois validam a serialização contra documentos de referência idênticos. O preço é
 * explícito — publicar um módulo faria o compilador acusar uma divergência, e sem ele quem acusa é
 * este teste. A contrapartida é não ter um artefato para versionar, publicar e sincronizar entre
 * dois ciclos de release.
 *
 * <p>Os documentos em {@code src/test/resources/contract/} são <b>byte a byte</b> os mesmos que
 * {@code sinapse-platform} mantém, e do outro lado existe um teste gêmeo lendo-os
 * ({@code br.com.sinapse.platform.coreclient.CoreContractGoldenTest}). Uma mudança nos records de
 * qualquer um dos lados que não chegue também aos documentos é exatamente a divergência que este
 * teste existe para encontrar.
 *
 * <h2>O mapeador é o da aplicação, não um novo</h2>
 *
 * A forma de fio é decidida tanto pela configuração quanto pelos records: a inclusão de nulos
 * decide se um campo ausente aparece, e {@code WRITE_DATES_AS_TIMESTAMPS} decide se um instante é
 * texto ou número. Um {@code new ObjectMapper()} aqui afirmaria uma configuração que não existe em
 * produção — passaria verde enquanto o serviço real emitisse outra coisa. Daí o
 * {@code @SpringBootTest}: é o {@code ObjectMapper} do contexto que serializa.
 *
 * <h2>Três mecanismos, porque nenhum sozinho fecha a verificação</h2>
 *
 * <ol>
 *   <li>a ida e volta pega campo renomeado, campo com outro tipo e qualquer desvio de valor;</li>
 *   <li>a comparação percorre a <b>união</b> das chaves dos dois lados, então um campo removido do
 *       record aparece como ausente na saída — e é reportado, não ignorado;</li>
 *   <li>a varredura de componentes pega um campo <b>acrescentado</b>. Nenhum dos outros dois pega:
 *       um componente novo que o documento não exercita simplesmente não é comparado.</li>
 * </ol>
 *
 * <p><b>Nota sobre chave desconhecida.</b> O teste gêmeo da plataforma afirma no Javadoc que a
 * desserialização recusa propriedade desconhecida. Nas duas aplicações
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} está no padrão do Spring Boot, que é <b>desligado</b>, então
 * essa recusa não acontece de nenhum dos lados. É o item 2 acima que cobre o caso, por outro
 * caminho.
 */
@SpringBootTest
@DisplayName("Contrato do Core v1.0: forma de fio travada nos documentos de referencia")
class CoreContractGoldenTest {

    /** Documento de referência do pedido, idêntico ao do sinapse-platform. */
    private static final String REQUEST_GOLDEN = "contract/plan-request-v1.0.json";

    /** Documento de referência da resposta, idêntico ao do sinapse-platform. */
    private static final String RESPONSE_GOLDEN = "contract/plan-response-v1.0.json";

    /**
     * O que fazer diante de uma falha. Vai junto de cada asserção porque a instrução útil nunca é
     * "faça o teste passar" — é que a forma mudou e o outro repositório ainda não sabe.
     */
    private static final String LEMBRETE =
            "A forma de fio do contrato do Core nao bate mais com o documento de referencia. "
                    + "Se a mudanca foi intencional: suba PlanRequest.VERSION e sincronize o "
                    + "documento em sinapse-platform, na mesma mudanca logica.";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("o record do pedido serializa exatamente para o seu documento de referencia")
    void oPedidoSerializaParaOSeuDocumentoDeReferencia() throws IOException {
        assertRoundTrip(REQUEST_GOLDEN, PlanRequest.class);
    }

    @Test
    @DisplayName("o record da resposta serializa exatamente para o seu documento de referencia")
    void aRespostaSerializaParaOSeuDocumentoDeReferencia() throws IOException {
        assertRoundTrip(RESPONSE_GOLDEN, PlanResponse.class);
    }

    @Test
    @DisplayName("todo componente de todo record e exercitado pelos documentos de referencia")
    void todoComponenteEhExercitadoPelosDocumentos() throws IOException {
        assertEveryComponentCovered(REQUEST_GOLDEN, PlanRequest.class);
        assertEveryComponentCovered(RESPONSE_GOLDEN, PlanResponse.class);
    }

    /**
     * A versão nos documentos é a versão em que o código está.
     *
     * <p>Um documento de referência escrito contra outra versão é pior que nenhum: continuaria
     * passando enquanto descreve uma forma que ninguém envia.
     */
    @Test
    @DisplayName("os documentos declaram a versao em que o contrato esta")
    void osDocumentosDeclaramAVersaoDoContrato() throws IOException {
        assertThat(read(REQUEST_GOLDEN, PlanRequest.class).contractVersion())
                .as("plan-request dourado contra PlanRequest.VERSION. " + LEMBRETE)
                .isEqualTo(PlanRequest.VERSION);
        assertThat(read(RESPONSE_GOLDEN, PlanResponse.class).contractVersion())
                .as("plan-response dourado contra PlanRequest.VERSION. " + LEMBRETE)
                .isEqualTo(PlanRequest.VERSION);
    }

    /**
     * Os dois documentos são um exemplo só, não dois.
     *
     * <p>A plataforma recusa uma resposta cuja semente não seja a que ela enviou
     * ({@code docs/CORE_CONTRACT_SURVEY.md} §3, verificação 5), então um par que discordasse nisso
     * jamais ocorreria numa execução real e seria uma coisa enganosa para este serviço testar.
     */
    @Test
    @DisplayName("os dois documentos sao uma troca coerente, e nao dois exemplos soltos")
    void osDoisDocumentosSaoUmaTrocaCoerente() throws IOException {
        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);
        PlanResponse response = read(RESPONSE_GOLDEN, PlanResponse.class);

        assertThat(response.metadata().randomSeed())
                .as("a resposta devolve a semente que o pedido enviou")
                .isEqualTo(request.randomSeed());

        Set<UUID> planejados = request.topics().stream()
                .map(PlanRequest.Topic::id)
                .collect(Collectors.toSet());
        assertThat(response.sessions()).allSatisfy(sessao ->
                assertThat(planejados)
                        .as("toda sessao agendada cita um topico que o pedido enviou")
                        .contains(sessao.topicId()));
    }

    /**
     * As quatro bandas de esforço, uma por tópico.
     *
     * <p>{@code effortTier} atravessa o fio como {@code String} e não há enum no pacote do contrato
     * para enumerá-la, então este documento é o único registro executável do conjunto fechado. Um
     * consumidor que o leia sabe os quatro valores que precisa aceitar — e que precisa recusar um
     * quinto no adaptador, em vez de assumir um padrão.
     */
    @Test
    @DisplayName("o pedido carrega as quatro bandas de esforco, todas como string")
    void oPedidoCarregaAsQuatroBandasComoString() throws IOException {
        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);

        assertThat(request.topics()).extracting(PlanRequest.Topic::effortTier)
                .as("o conjunto fechado de bandas de esforco, como texto")
                .containsExactlyInAnyOrder("SHORT", "STANDARD", "LONG", "EXTENDED");

        JsonNode dourado = tree(REQUEST_GOLDEN);
        assertThat(dourado.get("topics")).allSatisfy(topico ->
                assertThat(topico.get("effortTier").isTextual())
                        .as("effortTier e uma string JSON, nao um token de enum nem um numero")
                        .isTrue());
    }

    /**
     * As duas formas que um consumidor confunde.
     *
     * <p>As duas dizem "não se sabe nada deste tópico", e não são a mesma coisa. Um tópico ausente
     * de {@code history} não teve sessão alguma dentro da janela configurada da plataforma; um
     * tópico presente sem {@code lastStudiedAt} e sem notas teve sessão na janela, mas nenhuma que
     * fechasse com duração registrada. O documento carrega as duas para que este serviço tenha um
     * exemplo de cada contra o que ser testado.
     */
    @Test
    @DisplayName("o pedido distingue topico sem historico de topico ausente do historico")
    void oPedidoDistingueTopicoSemHistoricoDeTopicoAusente() throws IOException {
        PlanRequest request = read(REQUEST_GOLDEN, PlanRequest.class);

        assertThat(request.history())
                .as("um topico que esta no historico sem ultimo estudo e sem nota alguma")
                .anySatisfy(entrada -> {
                    assertThat(entrada.lastStudiedAt()).isNull();
                    assertThat(entrada.recallRatings()).isEmpty();
                    assertThat(entrada.sessionCount()).isZero();
                });

        Set<UUID> comHistorico = request.history().stream()
                .map(PlanRequest.TopicHistory::topicId)
                .collect(Collectors.toSet());
        assertThat(request.topics()).extracting(PlanRequest.Topic::id)
                .as("um topico que nao tem entrada nenhuma no historico")
                .anySatisfy(id -> assertThat(comHistorico).doesNotContain(id));

        JsonNode entrada = tree(REQUEST_GOLDEN).get("history").get(2);
        assertThat(entrada.has("lastStudiedAt"))
                .as("no documento a chave esta AUSENTE, e nao presente com valor nulo")
                .isFalse();
        assertThat(entrada.get("recallRatings"))
                .as("recallRatings e um array vazio e nunca ausente, porque o record faz copia "
                        + "defensiva e uma chave faltando quebraria a vinculacao")
                .isEmpty();
    }

    /**
     * A tolerância a nulo é assimétrica entre os dois records, e a assimetria é do contrato.
     *
     * <p>{@code PlanResponse} <b>aceita</b> {@code sessions} e {@code fitness} nulos e os troca por
     * coleção vazia ({@code docs/CORE_CONTRACT_SURVEY.md} §2.2); {@code PlanRequest} <b>recusa</b>,
     * porque {@code List.copyOf} estoura em nulo (§2.1). Uma resposta sem a chave {@code sessions}
     * é o caso real que a coerção cobre — ela chega como lista vazia, e é a plataforma que decide
     * recusá-la, com {@code CoreProtocolException}, por não ter sessão alguma (§3, verificação 3).
     *
     * <p>Os documentos de referência trazem as duas chaves preenchidas, então nenhum deles passa
     * por este caminho. Sem este teste a coerção fica sem cobertura e a assimetria some da
     * documentação executável.
     */
    @Test
    @DisplayName("a resposta tolera colecao nula e o pedido a recusa — a assimetria e do contrato")
    void aRespostaToleraColecaoNulaEOPedidoARecusa() {
        PlanResponse semColecoes = new PlanResponse("1.0", null, null, null);

        assertThat(semColecoes.sessions())
                .as("sessions nula vira lista vazia, e nao NullPointerException")
                .isEmpty();
        assertThat(semColecoes.fitness())
                .as("fitness nula vira mapa vazio")
                .isEmpty();
        assertThat(semColecoes.metadata())
                .as("metadata NAO e coagida: continua nula, e quem recusa e a plataforma")
                .isNull();

        assertThatNullPointerException()
                .as("o pedido e o lado estrito: List.copyOf recusa nulo em vez de coagir")
                .isThrownBy(() -> new PlanRequest("1.0", null, null, List.of(), List.of(),
                        List.of(), List.of(), Map.of(), 1L));
    }

    // -----------------------------------------------------------------------------------
    // Maquinaria
    // -----------------------------------------------------------------------------------

    /**
     * Lê o documento para dentro do record, escreve o record de volta e compara as duas árvores
     * campo a campo.
     *
     * <p>A ordem das chaves não é comparada: ela vem da ordem de declaração do record e não faz
     * parte do que qualquer um dos lados promete. A <b>presença</b> é comparada nos dois sentidos,
     * porque um campo que o outro repositório não conhece é justamente a divergência procurada.
     */
    private void assertRoundTrip(String recurso, Class<?> tipo) throws IOException {
        JsonNode esperado = tree(recurso);
        JsonNode obtido = objectMapper.readTree(objectMapper.writeValueAsString(read(recurso, tipo)));

        List<String> diferencas = new ArrayList<>();
        compare("", esperado, obtido, diferencas);

        assertThat(diferencas)
                .as("%s, desserializado em %s e serializado de volta. %s",
                        recurso, tipo.getSimpleName(), LEMBRETE)
                .isEmpty();
    }

    /** Percorre as duas árvores juntas e nomeia cada lugar em que discordam. */
    private static void compare(String caminho, JsonNode esperado, JsonNode obtido,
            List<String> diferencas) {

        if (esperado.isObject() && obtido.isObject()) {
            compareObjetos(caminho, esperado, obtido, diferencas);
            return;
        }
        if (esperado.isArray() && obtido.isArray()) {
            compareArrays(caminho, esperado, obtido, diferencas);
            return;
        }
        if (!sameValue(esperado, obtido)) {
            diferencas.add(caminho + ": o documento de referencia tem " + abbreviate(esperado)
                    + ", o record serializado tem " + abbreviate(obtido));
        }
    }

    /**
     * Compara dois objetos pela UNIÃO das chaves dos dois lados.
     *
     * <p>É a união, e não as chaves do documento, que faz a verificação valer nos dois sentidos:
     * campo que sumiu do record e campo que apareceu nele são ambos reportados, nomeando o caminho.
     */
    private static void compareObjetos(String caminho, JsonNode esperado, JsonNode obtido,
            List<String> diferencas) {

        for (String campo : union(esperado, obtido)) {
            String filho = caminho.isEmpty() ? campo : caminho + "." + campo;
            if (!obtido.has(campo)) {
                diferencas.add(filho + ": ausente do record serializado, presente no documento "
                        + "de referencia como " + abbreviate(esperado.get(campo)));
            } else if (!esperado.has(campo)) {
                diferencas.add(filho + ": presente no record serializado como "
                        + abbreviate(obtido.get(campo)) + ", ausente do documento de referencia");
            } else {
                compare(filho, esperado.get(campo), obtido.get(campo), diferencas);
            }
        }
    }

    /** Compara dois arrays posição a posição, reportando antes a diferença de tamanho. */
    private static void compareArrays(String caminho, JsonNode esperado, JsonNode obtido,
            List<String> diferencas) {

        if (esperado.size() != obtido.size()) {
            diferencas.add(caminho + ": o documento de referencia tem " + esperado.size()
                    + " elemento(s), o record serializado tem " + obtido.size());
            return;
        }
        for (int indice = 0; indice < esperado.size(); indice++) {
            compare(caminho + "[" + indice + "]", esperado.get(indice), obtido.get(indice),
                    diferencas);
        }
    }

    /**
     * Igualdade de valor, com uma concessão aos tipos de nó do Jackson.
     *
     * <p>Um componente {@code long} escreve um {@code LongNode}, enquanto o mesmo literal sai do
     * documento como {@code IntNode} quando cabe num int — e os dois não são iguais entre si, por
     * mais iguais que os números sejam. Inteiro e fracionário continuam separados: {@code 0} virar
     * {@code 0.0} é mudança de forma e é reportada.
     */
    private static boolean sameValue(JsonNode esperado, JsonNode obtido) {
        if (esperado.isNumber() && obtido.isNumber()) {
            return esperado.isIntegralNumber() == obtido.isIntegralNumber()
                    && esperado.decimalValue().compareTo(obtido.decimalValue()) == 0;
        }
        return esperado.equals(obtido);
    }

    /**
     * Reprova quando um record declara componente que os documentos de referência nunca exercitam.
     *
     * <p>É a verificação que pega um campo acrescentado. A ida e volta não pega: um componente novo
     * que o documento não menciona não entra na comparação por nenhum dos lados.
     *
     * <p>Os componentes são reunidos por tipo de record, somando todas as ocorrências daquele tipo,
     * para que um componente deliberadamente ausente de uma instância — {@code lastStudiedAt} no
     * tópico sem sessão fechada — fique coberto pelas instâncias que o carregam.
     */
    private void assertEveryComponentCovered(String recurso, Class<?> tipo) throws IOException {
        Map<Class<?>, Set<String>> observados = new HashMap<>();
        collect(tipo, tree(recurso), observados);

        observados.forEach((tipoDoRecord, chaves) -> {
            Set<String> descobertos = Arrays.stream(tipoDoRecord.getRecordComponents())
                    .map(RecordComponent::getName)
                    .filter(nome -> !chaves.contains(nome))
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(descobertos)
                    .as("%s declara componente(s) que %s nunca exercita, entao um consumidor que "
                            + "leia o documento jamais os veria. %s",
                            tipoDoRecord.getSimpleName(), recurso, LEMBRETE)
                    .isEmpty();
        });
    }

    /** Percorre o documento guiado pelos tipos dos records, anotando com que chaves cada um aparece. */
    private static void collect(Type tipo, JsonNode no, Map<Class<?>, Set<String>> observados) {
        if (ausente(no)) {
            return;
        }
        Class<?> bruto = rawTypeOf(tipo);
        if (bruto != null && bruto.isRecord() && no.isObject()) {
            collectRecord(bruto, no, observados);
            return;
        }
        // Um componente Map e opaco por contrato — algorithmParams e fitness nao sao interpretados
        // por lado nenhum —, entao nada abaixo dele e forma que algum repositorio prometa.
        if (no.isArray() && tipo instanceof ParameterizedType parametrizado) {
            collectElementos(parametrizado, no, observados);
        }
    }

    /** Anota as chaves com que este record apareceu e desce por cada um dos seus componentes. */
    private static void collectRecord(Class<?> bruto, JsonNode no,
            Map<Class<?>, Set<String>> observados) {

        Set<String> chaves = observados.computeIfAbsent(bruto, chave -> new LinkedHashSet<>());
        no.fieldNames().forEachRemaining(chaves::add);
        for (RecordComponent componente : bruto.getRecordComponents()) {
            collect(componente.getGenericType(), no.get(componente.getName()), observados);
        }
    }

    /** Desce por cada elemento de um array, guiado pelo último argumento de tipo da coleção. */
    private static void collectElementos(ParameterizedType parametrizado, JsonNode no,
            Map<Class<?>, Set<String>> observados) {

        Type[] argumentos = parametrizado.getActualTypeArguments();
        for (JsonNode elemento : no) {
            collect(argumentos[argumentos.length - 1], elemento, observados);
        }
    }

    /** Nó que não existe, é nulo ou está faltando: não há forma alguma a observar abaixo dele. */
    private static boolean ausente(JsonNode no) {
        return no == null || no.isNull() || no.isMissingNode();
    }

    private static Class<?> rawTypeOf(Type tipo) {
        if (tipo instanceof Class<?> bruto) {
            return bruto;
        }
        if (tipo instanceof ParameterizedType parametrizado
                && parametrizado.getRawType() instanceof Class<?> bruto) {
            return bruto;
        }
        return null;
    }

    private static Set<String> union(JsonNode esperado, JsonNode obtido) {
        Set<String> campos = new LinkedHashSet<>();
        for (Iterator<String> nomes = esperado.fieldNames(); nomes.hasNext(); ) {
            campos.add(nomes.next());
        }
        for (Iterator<String> nomes = obtido.fieldNames(); nomes.hasNext(); ) {
            campos.add(nomes.next());
        }
        return campos;
    }

    /** Mantem a mensagem de falha em uma linha quando a divergencia e um objeto aninhado inteiro. */
    private static String abbreviate(JsonNode no) {
        String texto = no.toString();
        return texto.length() <= 120 ? texto : texto.substring(0, 117) + "...";
    }

    private <T> T read(String recurso, Class<T> tipo) throws IOException {
        try (InputStream fluxo = new ClassPathResource(recurso).getInputStream()) {
            return objectMapper.readValue(fluxo, tipo);
        }
    }

    private JsonNode tree(String recurso) throws IOException {
        try (InputStream fluxo = new ClassPathResource(recurso).getInputStream()) {
            return objectMapper.readTree(fluxo);
        }
    }
}

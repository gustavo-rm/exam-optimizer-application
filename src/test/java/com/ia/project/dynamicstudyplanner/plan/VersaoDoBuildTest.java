package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A versão reportada é a do build, e não um texto mantido em dia à mão.
 *
 * <h2>O que pode dar errado aqui, e por que só um teste pega</h2>
 *
 * {@code baseline.core.version=@project.version@} só vira um número porque o
 * {@code spring-boot-starter-parent} configura <i>resource filtering</i> para
 * {@code application*.properties} com {@code @} como delimitador. Nada no código diz isso: se
 * alguém declarar um bloco {@code <resources>} próprio no {@code pom.xml} sem repetir a
 * configuração, ou mover a propriedade para um arquivo que não case com o padrão, o valor passa a
 * chegar como o literal {@code "@project.version@"} — <b>não vazio, não nulo, e completamente
 * inútil</b> como atribuição de qual build produziu um plano.
 *
 * <p>Por isso as três asserções são separadas: não vazio, não o literal, e igual à versão que o
 * {@code pom.xml} declara. A terceira é a que amarra o valor à fonte da verdade em vez de a um
 * formato plausível.
 *
 * <h2>Por que a chave mudou em EOA-4b</h2>
 *
 * A propriedade conferida era {@code app.core.version}, lida por {@code OptimizationResultDto}. Ela
 * saiu com o caminho de concurso, e a versão que hoje chega a um cliente é
 * {@code baseline.core.version}, em {@code metadata.coreVersion}. O mecanismo — e o modo de quebrar
 * — é exatamente o mesmo; o que mudou foi onde o número sai.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Versao do build: coreVersion vem do pom, filtrada pelo Maven")
class VersaoDoBuildTest {

    /** {@code <version>} do próprio projeto, não a de um pai ou de uma dependência. */
    private static final Pattern VERSAO_DO_PROJETO = Pattern.compile(
            "<artifactId>DynamicStudyPlanner</artifactId>\\s*<version>([^<]+)</version>");

    @Value("${baseline.core.version}")
    private String versaoInjetada;

    @Autowired
    private PlanEngineSelector motores;

    private static String versaoDeclaradaNoPom() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher encontrado = VERSAO_DO_PROJETO.matcher(pom);
        assertThat(encontrado.find())
                .as("o pom.xml precisa declarar a versao do proprio artefato")
                .isTrue();
        return encontrado.group(1).trim();
    }

    @Test
    @DisplayName("a propriedade nao esta vazia e nao e o literal @project.version@")
    void aPropriedadeFoiSubstituida() {
        assertThat(versaoInjetada)
                .as("sem o resource filtering do parent, o valor chega como o proprio marcador")
                .isNotBlank()
                .isNotEqualTo("@project.version@")
                .doesNotContain("@");
    }

    @Test
    @DisplayName("a propriedade bate exatamente com a versao declarada no pom.xml")
    void aPropriedadeBateComOPom() throws IOException {
        assertThat(versaoInjetada).isEqualTo(versaoDeclaradaNoPom());
    }

    @Test
    @DisplayName("e e isso que chega ao cliente, como metadata.coreVersion")
    void aVersaoChegaNaResposta() throws IOException {
        PlanResponse resposta = motores.plan(PlanRequests.builder().build());

        assertThat(resposta.metadata().coreVersion())
                .as("a atribuicao so serve se sair na resposta, nao apenas na configuracao")
                .isEqualTo(versaoDeclaradaNoPom());
    }

    /**
     * {@code coreVersion} não distingue um build de outro; {@code fitness.build} distingue.
     *
     * <p>O valor esperado é lido do {@code git.properties} que o {@code git-commit-id-maven-plugin}
     * gerou neste mesmo build, e não reescrito aqui: o teste prova o caminho do arquivo até a
     * resposta. Quando o arquivo não tem commit (build fora de um clone git), o esperado é
     * {@code unknown}, e o teste continua valendo.
     */
    @Test
    @DisplayName("fitness.build chega na resposta com o commit que o build registrou")
    void oCommitDoBuildChegaNaResposta() throws IOException {
        Properties gerado = new Properties();
        try (InputStream arquivo = new ClassPathResource("git.properties").getInputStream()) {
            gerado.load(arquivo);
        }
        String commit = gerado.getProperty("git.commit.id");
        String esperado = commit == null ? BuildIdentity.UNKNOWN
                : commit + (Boolean.parseBoolean(gerado.getProperty("git.dirty"))
                        ? BuildIdentity.DIRTY_SUFFIX : "");

        PlanResponse resposta = motores.plan(PlanRequests.builder().build());

        assertThat(resposta.fitness()).containsEntry(BuildIdentity.FITNESS_KEY, esperado);
        assertThat(resposta.fitness().get(BuildIdentity.FITNESS_KEY))
                .as("o build nao e a versao Maven")
                .isNotEqualTo(resposta.metadata().coreVersion());
    }

    @Test
    @DisplayName("o git.properties embarcado nao carrega nome nem e-mail de ninguem")
    void oArquivoNaoCarregaDadoPessoal() throws IOException {
        Properties gerado = new Properties();
        try (InputStream arquivo = new ClassPathResource("git.properties").getInputStream()) {
            gerado.load(arquivo);
        }

        assertThat(gerado.stringPropertyNames()).isSubsetOf("git.commit.id", "git.dirty");
    }
}

package com.ia.project.dynamicstudyplanner.api;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResultDto;
import com.ia.project.dynamicstudyplanner.api.mapper.OptimizationResultMapper;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A versão reportada é a do build, e não um texto mantido em dia à mão.
 *
 * <h2>O que pode dar errado aqui, e por que só um teste pega</h2>
 *
 * {@code app.core.version=@project.version@} só vira um número porque o
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
 */
@SpringBootTest
@DisplayName("Versao do build: coreVersion vem do pom, filtrada pelo Maven")
class VersaoDoBuildTest {

    /** {@code <version>} do próprio projeto, não a de um pai ou de uma dependência. */
    private static final Pattern VERSAO_DO_PROJETO = Pattern.compile(
            "<artifactId>DynamicStudyPlanner</artifactId>\\s*<version>([^<]+)</version>");

    @Value("${app.core.version}")
    private String versaoInjetada;

    @Autowired
    private OptimizationResultMapper mapper;

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
    @DisplayName("e e isso que chega ao cliente, como coreVersion")
    void aVersaoChegaNaResposta() throws IOException {
        OptimizationResultDto dto = mapper.toDto(
                new OptimizationResult(new StudyPlan(Map.of()), 0.5, 10, 42L));

        assertThat(dto.coreVersion())
                .as("a atribuicao so serve se sair na resposta, nao apenas na configuracao")
                .isEqualTo(versaoDeclaradaNoPom());
    }
}

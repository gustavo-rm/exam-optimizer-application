package com.ia.project.dynamicstudyplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * Aplicação.
 *
 * <h2>Por que a auto-configuração de Redis está excluída</h2>
 *
 * A etapa 06b passou o estado compartilhado (baldes de limite de taxa e registro de trabalhos) para
 * um Redis opcional. A auto-configuração do Spring Boot criaria, sempre, uma fábrica de conexões
 * apontando para {@code localhost:6379} e um indicador de saúde que reporta DOWN quando não há
 * Redis — fazendo {@code /actuator/health} responder 503 numa instalação de uma réplica só, que é
 * exatamente o cenário em que Redis não é necessário.
 *
 * <p>O cliente é criado por {@code SharedStateConfig}, e só quando
 * {@code api.shared-state.redis.enabled=true}. Assim a dependência não impõe infraestrutura a quem
 * roda uma instância só, e quem replica declara isso explicitamente.
 */
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
public class DynamicStudyPlannerApplication {

    /**
     * Ponto de entrada da aplicação.
     *
     * <p>O modificador {@code public} é obrigatório: o goal {@code repackage} do
     * {@code spring-boot-maven-plugin} procura um {@code public static void main} em
     * {@code target/classes} para gravar o {@code Main-Class} do jar executável. Sem ele,
     * {@code mvn package} falha com <i>"Unable to find main class"</i> e o artefato de implantação
     * descrito no README não chega a existir. Ver
     * {@code docs/qualidade/01b-correcao-testes.md}.
     */
    public static void main(String[] args) {
        SpringApplication.run(DynamicStudyPlannerApplication.class, args);
    }

}

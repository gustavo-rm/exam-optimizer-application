package com.ia.project.dynamicstudyplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação.
 *
 * <h2>Não há mais estado compartilhado entre réplicas</h2>
 *
 * Esta classe excluía a auto-configuração de Redis, porque a etapa 06b tinha passado os baldes do
 * limite de taxa e o registro dos trabalhos assíncronos para um Redis opcional e a auto-configuração
 * criaria, sempre, uma fábrica de conexões para {@code localhost:6379} e um indicador de saúde que
 * reportava DOWN sem Redis — fazendo {@code /actuator/health} responder 503 numa instalação de uma
 * réplica só.
 *
 * <p>EOA-4b removeu os dois consumidores com o caminho de concurso, e as dependências de Redis
 * saíram do {@code pom.xml} junto. Sem elas não há o que excluir: a exclusão nomeava classes que já
 * não estão no classpath. <b>O serviço não guarda estado nenhum</b> — cada requisição a
 * {@code POST /plans} é atendida inteira na thread que a recebeu.
 */
@SpringBootApplication
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

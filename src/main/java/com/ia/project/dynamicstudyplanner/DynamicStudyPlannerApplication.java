package com.ia.project.dynamicstudyplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

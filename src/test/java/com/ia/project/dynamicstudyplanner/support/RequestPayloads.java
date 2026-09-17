package com.ia.project.dynamicstudyplanner.support;

import java.time.LocalDate;

/**
 * Cargas JSON válidas para o endpoint {@code POST /api/v1/optimizer/generate}.
 *
 * <h2>Por que a data da prova é relativa a hoje aqui</h2>
 *
 * Em testes de unidade a etapa 01b substituiu {@code LocalDate.now()} por âncoras fixas, porque a
 * data era só um dado de entrada. Aqui é diferente: {@code DynamicStudyPlannerService} chama
 * {@code LocalDate.now()} <b>dentro do código de produção</b> para marcar o início do cronograma, e
 * {@code StudyScheduleGenerator} itera de lá até a data da prova. Uma âncora fixa no passado
 * produziria cronograma vazio e o teste deixaria de exercitar o caminho que pretende cobrir.
 *
 * <p>Enquanto a produção depender do relógio, o teste do fluxo completo precisa depender dele
 * também. Tornar isso injetável (um {@code Clock} do Spring) é mudança em código de produção e está
 * registrada como pendência P3 em {@code docs/qualidade/01b-correcao-testes.md}.
 */
public final class RequestPayloads {

    private RequestPayloads() {
    }

    /** Horizonte confortável: 240 dias cobrem o orçamento de 120 dias de estudo com folga. */
    public static final int DIAS_ATE_A_PROVA = 240;

    public static final int TOTAL_STUDY_DAYS = 120;

    /** As três disciplinas do edital sintético usado nos testes de API. */
    public static final String DISCIPLINA_GK = "Portugues";
    public static final String DISCIPLINA_ESP_1 = "Direito Constitucional";
    public static final String DISCIPLINA_ESP_2 = "Informatica";

    /**
     * Uma requisição completa e válida: um edital com três disciplinas em dois grupos, um perfil de
     * aluno com lacunas declaradas, disponibilidade em cinco dias da semana e estado psicológico
     * preenchido.
     */
    public static String requisicaoValida() {
        return requisicaoValida(TOTAL_STUDY_DAYS);
    }

    public static String requisicaoValida(int totalStudyDays) {
        LocalDate dataProva = LocalDate.now().plusDays(DIAS_ATE_A_PROVA);
        return """
                {
                  "exam": {
                    "name": "Concurso Sintetico 2026",
                    "examDate": "%s",
                    "generalKnowledgeTotalScore": 40.0,
                    "generalKnowledgeSubjects": [
                      { "name": "%s", "questionCount": 20, "cognitiveLoad": 2 }
                    ],
                    "specificKnowledgeAxes": [
                      {
                        "id": 1,
                        "name": "Eixo Juridico",
                        "weight": 2.5,
                        "subjects": [
                          { "name": "%s", "questionCount": 30, "cognitiveLoad": 5 }
                        ]
                      },
                      {
                        "id": 2,
                        "name": "Eixo Tecnologia",
                        "weight": 1.5,
                        "subjects": [
                          { "name": "%s", "questionCount": 25, "cognitiveLoad": 4 }
                        ]
                      }
                    ]
                  },
                  "studentProfile": {
                    "name": "Aluno de Teste",
                    "knowledgeGaps": { "%s": 2.0, "%s": 4.5, "%s": 3.0 },
                    "weeklyAvailability": {
                      "MONDAY": 3, "TUESDAY": 3, "WEDNESDAY": 3, "THURSDAY": 3, "FRIDAY": 2,
                      "SATURDAY": 6, "SUNDAY": 4
                    },
                    "state": {
                      "stressLevel": 3.0,
                      "fatigueLevel": 2.0,
                      "motivationLevel": 4.0,
                      "chronotype": "INTERMEDIATE"
                    }
                  },
                  "gaConfig": {
                    "totalStudyDays": %d,
                    "numGenerations": 30,
                    "populationSize": 20
                  }
                }
                """.formatted(dataProva, DISCIPLINA_GK, DISCIPLINA_ESP_1, DISCIPLINA_ESP_2,
                DISCIPLINA_GK, DISCIPLINA_ESP_1, DISCIPLINA_ESP_2, totalStudyDays);
    }
}

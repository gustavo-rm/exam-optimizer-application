package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Reúne tudo o que o algoritmo genético precisa saber antes de a evolução começar.
 *
 * <h2>Por que isto saiu de {@code StudyOptimizerService}</h2>
 *
 * O diagnóstico de estrutura (achado <b>E8</b>) apontou quatro assuntos convivendo numa classe de
 * 192 linhas: cálculo de domínio, orquestração do AG, observabilidade e montagem do contexto. Ao
 * separá-los ficou claro que <b>dois deles eram um só</b>: as três calculadoras
 * ({@code BaselineCalculator}, {@code ImportanceCalculator}, {@code CognitiveLoadCalculator}) e o
 * {@code FitnessEvaluator} não tinham nenhum outro consumidor dentro do serviço — existiam
 * exclusivamente para alimentar o {@code prepareContext}. "Cálculo" não era uma responsabilidade
 * paralela à "montagem do contexto"; era o conteúdo dela.
 *
 * <p>O que se ganha com a separação é um <b>motivo único para editar este arquivo</b>: acrescentar
 * um dado ao contexto da evolução. Antes, esse motivo dividia o arquivo com "mudar quais métricas
 * são publicadas" e "mudar como o laço de gerações roda", e as três coisas se atropelavam no mesmo
 * histórico de commits.
 *
 * <h2>Por que esta classe está em {@code service} e não em {@code ga}</h2>
 *
 * Ela produz um {@link EvolutionContext}, que mora em {@code ga}. Colocá-la lá seria natural — e
 * recriaria o ciclo {@code ga} ↔ {@code service} desfeito na etapa 03b, porque ela depende das
 * calculadoras de {@code service.calculation}. A direção permitida é {@code service} → {@code ga};
 * {@code arquitetura/ModuleBoundaryTest} reprova a inversa.
 */
@Service
public class EvolutionContextAssembler {

    private final BaselineCalculator baselineCalculator;
    private final ImportanceCalculator importanceCalculator;
    private final CognitiveLoadCalculator cognitiveLoadCalculator;
    private final FitnessEvaluator fitnessEvaluator;

    public EvolutionContextAssembler(BaselineCalculator baselineCalculator,
                                     ImportanceCalculator importanceCalculator,
                                     CognitiveLoadCalculator cognitiveLoadCalculator,
                                     FitnessEvaluator fitnessEvaluator) {
        this.baselineCalculator = baselineCalculator;
        this.importanceCalculator = importanceCalculator;
        this.cognitiveLoadCalculator = cognitiveLoadCalculator;
        this.fitnessEvaluator = fitnessEvaluator;
    }

    /**
     * Monta o contexto de uma otimização, a partir do edital e do perfil do estudante.
     *
     * <p>Os dados de planejamento — data de início, horizonte e carga diária — são propriedades da
     * <i>requisição</i>, calculadas uma vez por otimização e constantes durante toda a evolução. É
     * por isso que vivem no contexto e não são recalculadas a cada geração.
     *
     * @param exam    o edital, com disciplinas e data da prova
     * @param profile o perfil do estudante, com disponibilidade e estado psicológico
     */
    public EvolutionContext assemble(Exam exam, StudentProfile profile) {
        Map<Subject, Integer> minimumDaysPerSubject = baselineCalculator.calculateMinimumDays(exam, profile);
        Map<Subject, Double> importanceScores = importanceCalculator.calculatePersonalizedImportance(exam, profile);

        // Perfis vazios de proposito: a avaliacao macro do AG nao tem historico de revisoes nem de
        // engajamento para consultar. A camada tatica os hidrata depois, com dados reais.
        RetentionProfile retentionProfile = new RetentionProfile(Map.of());
        EngagementProfile engagementProfile = EngagementProfile.baseline();

        // Dados de planejamento que os termos corrigidos da fitness exigem. Ver
        // docs/revisao-ag/05-fitness-function.md: o horizonte governa a estimativa de espacamento
        // por tras do termo de retencao, e o orcamento de carga diaria (que ja incorpora estresse,
        // fadiga e motivacao) limita o termo de carga cognitiva.
        LocalDate planStartDate = LocalDate.now();
        int planningHorizonDays = Math.max(1,
                (int) ChronoUnit.DAYS.between(planStartDate, exam.getExamDate()));
        int hoursPerStudyDay = Math.max(1,
                (int) Math.ceil(profile.getTotalWeeklyHours() / 7.0));
        int maxDailyCognitiveLoad = cognitiveLoadCalculator.calculate(profile, exam);

        return EvolutionContext.builder()
                .importanceScores(importanceScores)
                .minimumDaysPerSubject(minimumDaysPerSubject)
                .studentState(profile.getState())
                .fitnessEvaluator(fitnessEvaluator)
                .retentionProfile(retentionProfile)
                .planStartDate(planStartDate)
                .engagementProfile(engagementProfile)
                .planningHorizonDays(planningHorizonDays)
                .hoursPerStudyDay(hoursPerStudyDay)
                .maxDailyCognitiveLoad(maxDailyCognitiveLoad)
                .build();
    }
}

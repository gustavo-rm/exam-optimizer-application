package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the chromosome: a complete allocation of study days for each subject.
 * This class is an immutable value object. Once a StudyPlan is created, it cannot be changed,
 * which ensures the integrity of the genetic algorithm's solutions.
 */
@SuppressWarnings("ClassCanBeRecord")
@Getter
public class StudyPlan {

    private final Map<Subject, Integer> daysPerSubject;

    /** Soma dos dias alocados, calculada na construcao. Ver a nota no construtor. */
    private final int totalDays;

    /**
     * Construtor canônico. <b>Recebe a posse do mapa</b>: quem constrói um plano entrega o mapa e
     * não o altera mais.
     *
     * <h2>O contrato, e por que ele está escrito aqui</h2>
     *
     * O mapa é exposto apenas por {@link Collections#unmodifiableMap} — uma <i>visão</i>, não uma
     * cópia. Quem passou o mapa continua tecnicamente podendo alterá-lo por trás. Isso sempre foi
     * verdade nesta classe; o que mudou na etapa 05b é que agora <b>importa</b>, porque o total de
     * dias passou a ser calculado uma vez só (achado F5).
     *
     * <p>Os dez pontos de construção em {@code src/main} e nos benchmarks passam um mapa local que
     * sai de escopo em seguida — nenhum retém a referência. O invariante que isso produz está
     * travado por {@code domain/StudyPlanInvarianteTest}, que verifica, sobre planos produzidos
     * pelos operadores genéticos reais, que {@code getTotalDays()} bate com a soma do mapa.
     *
     * <h2>Por que a cópia defensiva foi rejeitada — com número</h2>
     *
     * A versão segura por construção seria copiar o mapa aqui. Foi implementada e <b>medida</b>:
     * um {@code LinkedHashMap} novo por plano custa mais do que a memoização economiza, porque
     * planos são construídos uma vez por descendente — 500.500 vezes no pior caso.
     *
     * <pre>
     * pior caso (24 disc., 1000 ger., 500 pop.), mediana de 9 execuções:
     *   sem memoização, sem cópia .................. 1385 ms   (linha de base)
     *   com memoização E cópia defensiva ........... 1688 ms   PIOR em 22 %
     *   com memoização, sem cópia .................. 1318 ms   melhor em 4,8 %
     * </pre>
     *
     * A cópia custaria ~370 ms para proteger contra um uso que nenhum chamador faz. A escolha foi
     * o contrato explícito mais o teste de invariante, e não a proteção paga em todo descendente.
     *
     * <h2>Estratégia de invalidação do total</h2>
     *
     * <b>Não existe, e isso é uma afirmação, não um esquecimento.</b> Não há método nesta classe
     * que altere a alocação: o campo é {@code final}, o mapa só sai por visão não modificável, e o
     * total é derivado uma vez no construtor. Não há evento capaz de tornar o valor obsoleto, logo
     * não há o que invalidar.
     *
     * <p>O dia em que alguém acrescentar um método que mude a alocação, este cálculo precisa mudar
     * junto — e é por isso que ele mora no construtor, onde é impossível não ver.
     *
     * @param daysPerSubject dias por disciplina; a posse passa para o plano
     */
    public StudyPlan(Map<Subject, Integer> daysPerSubject) {
        this.daysPerSubject = daysPerSubject == null
                ? Map.of()
                : Collections.unmodifiableMap(daysPerSubject);
        this.totalDays = this.daysPerSubject.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Retrieves the number of days allocated to a specific subject.
     *
     * @param subject The subject to query.
     * @return The number of allocated days, or 0 if the subject is not in the plan.
     */
    public int getDaysForSubject(Subject subject) {
        return this.daysPerSubject.getOrDefault(subject, 0);
    }

    /**
     * Calculates the total number of days in the entire study plan.
     *
     * @return The sum of all allocated days across all subjects.
     */
    public int getTotalDays() {
        return this.totalDays;
    }

    /**
     * Checks if the plan satisfies all provided minimum day constraints.
     * This brings validation logic out of the GA framework and into the domain object itself.
     *
     * @param minimumDaysPerSubject The map of constraints.
     * @return {@code true} if all constraints are met, {@code false} if at least one is violated.
     */
    public boolean meetsMinimumConstraints(Map<Subject, Integer> minimumDaysPerSubject) {
        for (Map.Entry<Subject, Integer> entry : daysPerSubject.entrySet()) {
            int allocatedDays = entry.getValue();
            int minimumDays = minimumDaysPerSubject.getOrDefault(entry.getKey(), 1);
            if (allocatedDays < minimumDays) {
                return false; // Found a violation.
            }
        }
        return true; // No violations found.
    }
}

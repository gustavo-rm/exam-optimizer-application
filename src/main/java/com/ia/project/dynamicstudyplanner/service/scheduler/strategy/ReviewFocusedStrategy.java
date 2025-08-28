package examoptimizer.service.scheduler.strategy;

import examoptimizer.domain.StudyBlock;
import examoptimizer.domain.exam.Subject;

import java.time.LocalDate;
import java.util.*;

/**
 * An advanced strategy that incorporates Spaced Repetition.
 * It dedicates the first hour of a study day to reviewing the subject that hasn't
 * been studied for the longest time. It then delegates the allocation of the
 * remaining hours to a fallback strategy.
 */
public class ReviewFocusedStrategy implements AllocationStrategy {

    private final AllocationStrategy fallbackStrategy; // A estratégia a ser usada após a revisão

    /**
     * Constructs a ReviewFocusedStrategy.
     * @param fallbackStrategy The strategy to use for the remaining hours after the review block.
     */
    public ReviewFocusedStrategy(AllocationStrategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }

    @Override
    public List<StudyBlock> allocateHours(AllocationContext context) {
        if (context.availableHoursToday() == 0 || context.hoursToSchedulePerSubject().isEmpty()) {
            return Collections.emptyList();
        }

        List<StudyBlock> dailyBlocks = new ArrayList<>();

        // --- 1. Lógica da Repetição Espaçada ---
        // Encontra a matéria que foi estudada há mais tempo (ou nunca)
        Optional<Subject> reviewSubject = findSubjectForReview(context.hoursToSchedulePerSubject(), context.lastStudiedDateMap());

        if (reviewSubject.isPresent()) {
            Subject subjectToReview = reviewSubject.get();

            // Aloca a primeira hora do dia para a revisão
            dailyBlocks.add(new StudyBlock(subjectToReview, 1));
            context.hoursToSchedulePerSubject().computeIfPresent(subjectToReview, (s, hours) -> hours - 1);

            // Se só havia uma hora disponível, o trabalho está feito
            if (context.availableHoursToday() == 1) {
                return dailyBlocks;
            }
        }

        // --- 2. Delega o Resto do Trabalho ---
        // Para as horas restantes, usa a estratégia de fallback
        int remainingHours = context.availableHoursToday() - dailyBlocks.size();
        if (remainingHours > 0) {
            dailyBlocks.addAll(fallbackStrategy.allocateHours(new AllocationContext(remainingHours, context.hoursToSchedulePerSubject(), context.lastStudiedDateMap())));
        }

        return dailyBlocks;
    }

    /**
     * Finds the subject that has gone the longest without being studied.
     */
    private Optional<Subject> findSubjectForReview(Map<Subject, Double> hoursToSchedule, Map<Subject, LocalDate> lastStudied) {
        return hoursToSchedule.keySet().stream()
                .filter(subject -> hoursToSchedule.get(subject) > 0.5)
                // Compara as datas, tratando as nunca estudadas (nulas) como as mais antigas
                .min(Comparator.comparing(subject -> lastStudied.getOrDefault(subject, LocalDate.MIN)));
    }
}
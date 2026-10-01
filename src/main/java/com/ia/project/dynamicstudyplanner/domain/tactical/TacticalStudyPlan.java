package com.ia.project.dynamicstudyplanner.domain.tactical;

import java.util.Collections;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import java.util.BitSet;
import java.util.HashMap;

/**
 * The Chromosome for the Tactical Intelligent Tutoring System.
 * Unlike the strategic StudyPlan (which maps PlanningItem -> Days),
 * this maps specific TimeSlots -> TacticalStudyBlocks.
 * <p>
 * By using TimeSlots as the keys (loci), we guarantee that blocks cannot overlap,
 * fundamentally enforcing a constraint-preserving architecture at the base level.
 */
public class TacticalStudyPlan extends StudyPlan {

    /** Maior valor que {@code LocalDateTime.getDayOfYear()} devolve, em ano bissexto. */
    private static final int DAYS_IN_YEAR = 366;

    private final Map<TimeSlot, TacticalStudyBlock> schedule;

    public TacticalStudyPlan(Map<TimeSlot, TacticalStudyBlock> schedule) {
        super(extractDaysPerItem(schedule));
        this.schedule = schedule == null ? Map.of() : Collections.unmodifiableMap(schedule);
    }

    /**
     * Dias distintos em que cada item aparece.
     *
     * <h2>{@code BitSet} no lugar de {@code HashSet<Integer>} (pendência G17)</h2>
     *
     * A contagem é a mesma; o que mudou é o custo. A versão anterior alocava <b>um {@code HashSet}
     * por item</b> — e um {@code HashSet} é um {@code HashMap} por dentro, com um nó por dia — mais
     * um {@code Integer} por dia inserido. Medido com JFR sobre a busca do motor de linha do tempo,
     * este método sozinho respondia por <b>24% das amostras de execução</b>, e
     * {@code HashMap$Node} mais {@code HashMap$Node[]} lideravam a alocação com folga.
     *
     * <p>Isso não importava enquanto um plano tático era construído uma vez por requisição, que é o
     * caso de {@code SessionPlacement}. O motor de linha do tempo constrói vários por descendente,
     * milhares de vezes por requisição.
     *
     * <p>{@code getDayOfYear()} está em {@code 1..366}, então um {@code BitSet} dimensionado a 367
     * nunca cresce, e {@code cardinality()} é exatamente o {@code size()} que o conjunto dava.
     *
     * <h2>Os dois mapas seguem sendo {@code HashMap}, e isso é deliberado</h2>
     *
     * A ordem de iteração do mapa devolvido alimenta {@code PlanningItemIndex.of} no construtor de
     * {@code StudyPlan}, e portanto decide a <b>ordem canônica dos genes</b> deste plano. Trocar por
     * {@code LinkedHashMap} mudaria essa ordem — e com ela a ordem de somas de ponto flutuante rio
     * abaixo. Mesmas chaves, mesma ordem de inserção, mesmo tipo: a ordem é idêntica à de antes.
     *
     * <p>Que a ordem dos genes de um plano tático dependa de {@code PlanningItem.hashCode()} é
     * fragilidade registrada em <b>G6</b> e não é o que esta correção resolve — ela só deixa de
     * pagar caro por ela.
     */
    private static Map<PlanningItem, Integer> extractDaysPerItem(Map<TimeSlot,
            TacticalStudyBlock> schedule) {
        if (schedule == null) {
            return Map.of();
        }

        Map<PlanningItem, BitSet> itemDays = new HashMap<>();
        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : schedule.entrySet()) {
            itemDays.computeIfAbsent(entry.getValue().item(), key -> new BitSet(DAYS_IN_YEAR + 1))
                    .set(entry.getKey().startTime().getDayOfYear());
        }

        Map<PlanningItem, Integer> daysPerItem = new HashMap<>();
        for (Map.Entry<PlanningItem, BitSet> entry : itemDays.entrySet()) {
            daysPerItem.put(entry.getKey(), entry.getValue().cardinality());
        }
        return daysPerItem;
    }

    public Map<TimeSlot, TacticalStudyBlock> getSchedule() {
        return schedule;
    }

    /**
     * Calculates the total cumulative cognitive load of this specific plan.
     */
    public double calculateTotalCognitiveLoad() {
        return schedule.values().stream()
                .mapToDouble(block -> block.item().difficultyBand() * block.methodology().getCognitiveLoadMultiplier(
                        ) * (block.durationMinutes() / 60.0))
                .sum();
    }
}

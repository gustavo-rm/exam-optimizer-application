package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Topics as the core's planning unit, and the way back.
 *
 * <h2>The translation</h2>
 *
 * <pre>
 *   id             = topic.id().toString()
 *   name           = topic.id().toString()
 *   difficultyBand = EffortTierBands.bandOf(topic.effortTier())
 * </pre>
 *
 * <h2>{@code name} repeats the identifier because the platform sends no name</h2>
 *
 * The contract is explicit that a topic's {@code code} and {@code name} are <b>not sent</b>. So
 * there is no human-readable label to carry, and the alternatives were to invent one
 * ({@code "Topic 3"}) or to leave the component null. Repeating the identifier is the only option
 * that adds nothing: anything that renders this field shows a UUID, which is visibly an identifier
 * and not a label somebody wrote. Nothing on this path renders it — the response carries
 * {@code topicId}, not a name — so the component exists here only because {@link PlanningItem}
 * declares it.
 *
 * <p>This is also why the reverse lookup is by {@code id} and not by {@code name}: they happen to
 * be equal today, and a future contract version that starts sending names must not silently change
 * which map key the plan is read back through.
 *
 * <h2>Order is the gene order</h2>
 *
 * The list comes out in the order the topics arrived, because that order becomes the chromosome's
 * gene order via {@code PlanningItemIndex} — it decides where a crossover cuts and which item each
 * repair draw lands on. The platform's ordering is declared input, not an implementation detail of
 * a hash table.
 */
public final class TopicPlanningItems {

    private TopicPlanningItems() {
    }

    /**
     * Translates every topic, preserving order.
     *
     * @param topics the topics as they arrived
     * @return the planning items in the same order
     * @throws com.ia.project.dynamicstudyplanner.plan.PlanRejectedException on an unknown tier
     */
    public static List<PlanningItem> of(List<PlanRequest.Topic> topics) {
        return topics.stream().map(TopicPlanningItems::toItem).toList();
    }

    /** @return the planning item for one topic */
    public static PlanningItem toItem(PlanRequest.Topic topic) {
        String id = topic.id().toString();
        return new PlanningItem(id, id, EffortTierBands.bandOf(topic.effortTier()));
    }

    /**
     * The way back, for the output adapter: which topic each item came from.
     *
     * @param topics the topics as they arrived
     * @return item {@literal ->} topic id, in arrival order
     */
    public static Map<PlanningItem, UUID> topicIdsByItem(List<PlanRequest.Topic> topics) {
        Map<PlanningItem, UUID> byItem = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : topics) {
            byItem.put(toItem(topic), topic.id());
        }
        return Collections.unmodifiableMap(byItem);
    }

    /**
     * Per-topic estimated minutes, keyed by item.
     *
     * <p>{@code estimatedMinutes} is the one quantitative field on a topic that the platform has
     * actually scaled to this student ({@code docs/CORE_CONTRACT_SURVEY.md} §1.8), so it is what
     * decides how long a session lasts. The band decides how the fitness values the topic; the
     * minutes decide how much calendar it occupies. Keeping the two jobs apart is why neither is
     * derived from the other here.
     *
     * @param topics the topics as they arrived
     * @return item {@literal ->} estimated minutes
     */
    public static Map<PlanningItem, Integer> estimatedMinutesByItem(List<PlanRequest.Topic> topics) {
        Map<PlanningItem, Integer> byItem = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : topics) {
            byItem.put(toItem(topic), topic.estimatedMinutes());
        }
        return Collections.unmodifiableMap(byItem);
    }
}

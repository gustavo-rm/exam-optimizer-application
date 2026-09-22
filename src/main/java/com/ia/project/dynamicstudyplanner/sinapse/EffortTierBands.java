package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place {@code effortTier} becomes a {@code difficultyBand}.
 *
 * <h2>The mapping</h2>
 *
 * <pre>
 *   SHORT    -> 1
 *   STANDARD -> 2
 *   LONG     -> 3
 *   EXTENDED -> 4
 * </pre>
 *
 * Order-preserving and nothing else: the tiers are an ordinal scale and so is the band, so the
 * mapping is the identity between two namings of the same four positions. Band {@code 5} is
 * reachable by {@code PlanningItem} and unreachable from this path, because the platform's scale has
 * four positions and inventing a fifth would claim a distinction the curator never made.
 *
 * <h2>Two uncalibrated models are stacked here, and the stack is the point</h2>
 *
 * This mapping is the seam where a curator's ordinal judgement enters a quantitative learning model,
 * and <b>both ends of it are assumptions rather than findings</b>:
 *
 * <ol>
 *   <li>The platform's own {@code application.yml} says of its tier-to-minutes table, in those
 *       words, that it is <b>"AN ASSUMPTION, NOT A FINDING"</b>;</li>
 *   <li>{@code LearningModel} turns the band into a time constant with
 *       {@code TAU_AT_AVERAGE_LOAD = 10.0} days at {@code AVERAGE_COGNITIVE_LOAD = 3.0}, two
 *       constants chosen for plausibility and never fitted to data.</li>
 * </ol>
 *
 * So a plan's mastery term rests on a chain of two unobserved calibrations, and the band travelling
 * through this class is where they meet. Both are on the list of parameters to calibrate against
 * the pilot's data; see {@code docs/SINAPSE_ADAPTER.md} §4. This does not block the implementation —
 * it blocks the result being presented as a calibrated model.
 *
 * <h2>Why an unknown tier is refused rather than defaulted</h2>
 *
 * {@code Topic.effortTier} is a {@code String} in the contract, deliberately and verifiably: the
 * platform sends {@code effortTier().name()} and declares no enum, so a value outside the closed set
 * arrives here <b>without a deserialisation error</b>. Choosing a default band for it would feed the
 * heaviest-weighted term of the fitness ({@code SYLLABUS_MASTERY}, 0.50) with a number nobody chose,
 * and the plan would look exactly as trustworthy as a correct one. The request is refused with
 * {@code 422} naming the value received.
 *
 * <p>The greedy baseline does <b>not</b> validate the tier, and that difference is deliberate rather
 * than an inconsistency: it allocates by {@code estimatedMinutes} and never reads the band, so for it
 * the field is inert cargo. Refusing a request over a field the answer does not depend on would be
 * the wrong trade. Here the answer depends on it.
 */
public final class EffortTierBands {

    /**
     * The closed set, in ordinal order, mapped to the band each tier means.
     *
     * <p>{@code Map.copyOf} is safe <b>here and only here</b> in this package: this map is read by
     * {@code get} and never iterated, so its iteration order — which {@code Map.copyOf} randomises
     * with a per-JVM-run salt — cannot reach any arithmetic. Every other map in the adapter is an
     * {@code unmodifiableMap} over a {@code LinkedHashMap}; see {@code TopicImportance} for what
     * went wrong when one of them was not, and {@code SinapseAdapterIsolationTest} for the guard.
     */
    private static final Map<String, Integer> BANDS = bands();

    private EffortTierBands() {
    }

    private static Map<String, Integer> bands() {
        Map<String, Integer> bands = new LinkedHashMap<>();
        bands.put("SHORT", 1);
        bands.put("STANDARD", 2);
        bands.put("LONG", 3);
        bands.put("EXTENDED", 4);
        return Map.copyOf(bands);
    }

    /** @return the tiers this adapter understands, in ordinal order, for an actionable refusal */
    public static List<String> knownTiers() {
        return List.of("SHORT", "STANDARD", "LONG", "EXTENDED");
    }

    /**
     * The band a tier means.
     *
     * @param effortTier the string the platform sent
     * @return the ordinal band, 1 to 4
     * @throws PlanRejectedException with {@code 422} when the tier is null, blank or outside the
     *                               closed set, naming the value received
     */
    public static int bandOf(String effortTier) {
        Integer band = effortTier == null ? null : BANDS.get(effortTier);
        if (band == null) {
            throw new PlanRejectedException("unknown-effort-tier",
                    "effortTier must be one of " + knownTiers() + ". The genetic engine maps the "
                            + "tier to the difficulty band that drives the mastery term, so an "
                            + "unrecognised tier is refused rather than given a default band.",
                    List.of(String.valueOf(effortTier)));
        }
        return band;
    }

    /**
     * Checks every topic's tier before any planning starts.
     *
     * <p>All offenders in one refusal, rather than one per round trip: the caller fixes the payload
     * once. Reported by identifier, never by content.
     *
     * @param topics the topics as they arrived
     * @throws PlanRejectedException naming every topic whose tier is unusable
     */
    public static void checkEvery(List<PlanRequest.Topic> topics) {
        List<String> offending = topics.stream()
                .filter(topic -> topic.effortTier() == null || !BANDS.containsKey(topic.effortTier()))
                .map(topic -> topic.id() + "=" + topic.effortTier())
                .toList();
        if (!offending.isEmpty()) {
            throw new PlanRejectedException("unknown-effort-tier",
                    "effortTier must be one of " + knownTiers() + " for every topic. The genetic "
                            + "engine maps the tier to the difficulty band that drives the mastery "
                            + "term, so an unrecognised tier is refused rather than given a "
                            + "default band.",
                    offending);
        }
    }
}

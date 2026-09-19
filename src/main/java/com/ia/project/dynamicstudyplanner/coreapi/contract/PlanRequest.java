package com.ia.project.dynamicstudyplanner.coreapi.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything this optimiser is told about a student, in one document.
 *
 * <p><strong>Mirror of the platform's record, not an adaptation of it.</strong> The authoritative
 * declaration lives in {@code sinapse-platform}, at
 * {@code br.com.sinapse.platform.coreclient.contract.PlanRequest}, and is transcribed component by
 * component in {@code docs/CORE_CONTRACT_SURVEY.md} §2.1. Names, types, order and nullability here
 * are that declaration and nothing else. There is deliberately no shared Maven artifact: both sides
 * keep their own copy and both pin serialisation against the same reference documents. See the
 * README section "Core contract (v1.0)".
 *
 * <p><strong>{@link Topic#effortTier} is a {@code String}, and must stay one.</strong> The platform
 * sends {@code topic.effortTier().name()} and declares no {@code EffortTier} enum in its contract
 * package. Introducing one here would make this service reject, at deserialisation, a band the
 * platform considers valid — and the failure would surface as {@code CORE_UNAVAILABLE}, which
 * points at the network rather than at the contract. Checking the closed set
 * ({@code SHORT}, {@code STANDARD}, {@code LONG}, {@code EXTENDED}) belongs to the adapter
 * (EOA-2 and EOA-5), not to this record.
 *
 * <p>Nullability below is what the code enforces, not what prose wishes: {@code List.copyOf} and
 * {@code Map.copyOf} in the compact constructor throw {@code NullPointerException} on a null
 * argument, so those components are non-null by construction; every other component is
 * structurally nullable, even where nothing sends null today.
 *
 * <h2>Inclusão de nulos: aqui, e não na configuração global</h2>
 *
 * A forma de fio depende tanto do mapeador quanto do record. A plataforma declara
 * {@code spring.jackson.default-property-inclusion: non_null} e por isso <b>omite</b> um campo
 * nulo: {@code lastStudiedAt} some do documento quando o tópico não tem sessão fechada. Esta
 * aplicação não declara chave {@code spring.jackson} alguma, então vale o padrão {@code ALWAYS} do
 * Jackson, e o mesmo campo sairia como {@code "lastStudiedAt": null} — outro documento.
 *
 * <p>A anotação é o equivalente local daquela linha de configuração. Ela se repete <b>por tipo</b>
 * porque {@code @JsonInclude} não alcança record aninhado: vale para os componentes do tipo em que
 * está, e {@code lastStudiedAt} é componente de {@link TopicHistory}. Ligar a chave global fecharia
 * tudo com uma linha só, e foi recusado: mudaria a carga de todos os endpoints de
 * {@code /api/v1/**}, que é muito mais do que este contrato pede.
 *
 * <p><b>Não a remova por parecer ruído.</b> O record equivalente na plataforma não tem anotação
 * nenhuma, então um espelhamento literal a apagaria — e {@code CoreContractGoldenTest} reprova
 * nomeando o campo exato no instante em que isso acontecer.
 *
 * @param contractVersion version of this contract the payload was written against, so that a
 *                        receiver which does not understand it can refuse rather than misread it
 * @param horizon         the stretch of calendar being planned
 * @param availability    the concrete intervals of the horizon in which the student can study,
 *                        already resolved from their weekly routine by the platform
 * @param goals           the subjects being pursued, with their deadlines and priorities
 * @param topics          every topic in scope, with the effort estimated for this student
 * @param prerequisites   the edges among those topics, with strength and provenance
 * @param history         what the student has already studied, per topic
 * @param algorithmParams parameters the run is to use, as configured by the platform
 * @param randomSeed      seed of the pseudo-random generator, chosen by the platform
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanRequest(
        String contractVersion,
        Horizon horizon,
        List<AvailabilitySlot> availability,
        List<Goal> goals,
        List<Topic> topics,
        List<PrerequisiteEdge> prerequisites,
        List<TopicHistory> history,
        Map<String, Object> algorithmParams,
        long randomSeed) {

    /** The version this contract is at. A change to any shape here changes this string. */
    public static final String VERSION = "1.0";

    /** Defensive copies: a snapshot that changed after being taken would not be a snapshot. */
    public PlanRequest {
        availability = List.copyOf(availability);
        goals = List.copyOf(goals);
        topics = List.copyOf(topics);
        prerequisites = List.copyOf(prerequisites);
        history = List.copyOf(history);
        algorithmParams = Map.copyOf(algorithmParams);
    }

    /**
     * The stretch of calendar being planned.
     *
     * @param start first day, inclusive
     * @param end   last day
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Horizon(LocalDate start, LocalDate end) {
    }

    /**
     * One concrete interval, inside the horizon, in which the student can study.
     *
     * <p><strong>Already expanded.</strong> What the student declared is a recurring weekly window
     * in their own zone; the platform turns those into the actual instants of the horizon before
     * sending them, because the party that holds the account is the party that knows the zone. This
     * side does no calendar arithmetic on them.
     *
     * @param start when the interval opens
     * @param end   when it closes
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AvailabilitySlot(Instant start, Instant end) {
    }

    /**
     * A subject the student intends to get through.
     *
     * <p>Every topic of the subject is in scope with it; there is no exclusion list. The goal's own
     * identifier is not sent, only {@code subjectId}.
     *
     * @param subjectId  subject
     * @param targetDate when the student would like to be done, or {@code null}. A prioritisation
     *                   pressure and never the horizon
     * @param priority   1 to 5, higher meaning more pressing. Documented, not validated here
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Goal(UUID subjectId, LocalDate targetDate, int priority) {
    }

    /**
     * A topic in scope, with how long it is expected to take this student.
     *
     * <p>The minutes are not a curated number: a curator judges an ordinal band, the band is mapped
     * to minutes by the platform's configuration, and that mapping is scaled by what this student
     * has actually needed. The band travels alongside the minutes so that a calibrated estimate can
     * be told from a default one. The topic's {@code code} and {@code name} are not sent.
     *
     * @param id               topic
     * @param subjectId        subject it belongs to
     * @param position         its curricular position within the subject
     * @param effortTier       the ordinal band a curator judged, as a plain string — see the class
     *                         Javadoc for why this is not an enum
     * @param estimatedMinutes what that band means for this student, in minutes
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Topic(
            UUID id,
            UUID subjectId,
            int position,
            String effortTier,
            int estimatedMinutes) {
    }

    /**
     * A directed prerequisite between two topics.
     *
     * <p>{@code prerequisiteTopicId} is studied before {@code dependentTopicId}. The edge's own
     * {@code id} and {@code sourceReference} are not sent.
     *
     * @param prerequisiteTopicId topic that comes first
     * @param dependentTopicId    topic that depends on it
     * @param strength            whether the edge is a constraint or a preference
     * @param provenance          where the edge came from
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrerequisiteEdge(
            UUID prerequisiteTopicId,
            UUID dependentTopicId,
            EdgeStrength strength,
            EdgeProvenance provenance) {
    }

    /**
     * What the student has already done on one topic.
     *
     * <p>Absent from {@code history} and present with nothing recorded are two different states. A
     * topic with no entry had no session inside the platform's history window at all; a topic with
     * an entry but no {@code lastStudiedAt} and no ratings did have one, but none that closed with
     * a recorded duration.
     *
     * @param topicId       topic
     * @param sessionCount  how many closed sessions with a recorded duration it has
     * @param totalMinutes  how much effective time went into it
     * @param lastStudiedAt when the most recent of those sessions ended, or {@code null}
     * @param recallRatings the student's judgements over time, oldest first
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TopicHistory(
            UUID topicId,
            int sessionCount,
            long totalMinutes,
            Instant lastStudiedAt,
            List<RecallRating> recallRatings) {

        /** Defensive copy. */
        public TopicHistory {
            recallRatings = List.copyOf(recallRatings);
        }
    }
}

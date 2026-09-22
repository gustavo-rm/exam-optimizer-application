package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;

import java.time.Instant;
import java.util.UUID;

/**
 * One session that has been given a slot, before it is given a sequence number.
 *
 * <p>It is {@code PlanResponse.ScheduledSession} minus {@code sequenceIndex}, and it exists only so
 * that the index can be assigned in one final pass over the finished list. Assigning it during
 * allocation would risk a gap: a topic whose revision does not fit is dropped whole, and the numbers
 * its study session had already consumed would leave a hole. {@code sequenceIndex} has to be
 * contiguous, so it is counted last, when the list can no longer change.
 *
 * @param topicId         the topic being studied or revised
 * @param kind            new ground or going back over it
 * @param start           when the block opens
 * @param durationMinutes how long the block runs
 */
public record PlacedSession(UUID topicId, SessionKind kind, Instant start, int durationMinutes) {
}

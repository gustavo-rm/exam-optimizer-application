package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The planning horizon as a half-open interval of instants, {@code [from, until)}.
 *
 * <h2>Design assumption: the horizon is read in UTC, and nothing else can be done here</h2>
 *
 * {@code horizon} is a pair of {@code LocalDate}; {@code availability} is a list of absolute
 * {@code Instant}s. Comparing the two needs a time zone, and <b>no zone crosses the wire</b>: the
 * platform resolves the student's zone when it expands their weekly routine into absolute instants
 * and keeps the zone to itself ({@code docs/CORE_CONTRACT_SURVEY.md} §2.1, {@code AvailabilitySlot}).
 *
 * <p>So this side declares the assumption instead of guessing: the first day starts at
 * {@code 00:00Z} and the last day ends at {@code 24:00Z}. The consequence is stated and bounded — for
 * a student far from UTC, an availability window near the edge of the horizon can fall partly outside
 * these bounds, and {@link AvailabilityAllocator} then clips it. The plan loses that sliver of time;
 * it never emits a session the platform's own horizon does not cover. Under-scheduling is the safe
 * direction, because {@code scheduledStart} inside the horizon is one of the four invariants the
 * platform does <b>not</b> check.
 *
 * @param from  first instant of the horizon, inclusive
 * @param until first instant after the horizon, exclusive
 */
record HorizonBounds(Instant from, Instant until) {

    /**
     * Reads the bounds off a request whose horizon {@link PlanRequestGuard} has already accepted.
     *
     * @param horizon the request's horizon; both dates are non-null by then
     * @return the half-open interval the horizon denotes in UTC
     */
    static HorizonBounds of(PlanRequest.Horizon horizon) {
        return new HorizonBounds(
                horizon.start().atStartOfDay(ZoneOffset.UTC).toInstant(),
                horizon.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    /** Whether an instant falls inside the horizon. */
    boolean contains(Instant instant) {
        return !instant.isBefore(from) && instant.isBefore(until);
    }
}

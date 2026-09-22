package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * The availability the platform resolved, as the tactical layer's window type.
 *
 * <h2>No time-zone arithmetic happens here, and that is the whole contract</h2>
 *
 * {@code AvailabilitySlot} carries {@code Instant}s the platform already expanded from the student's
 * weekly routine <b>in the account's own zone</b> — the party that holds the account is the party
 * that knows the zone, and the contract says this side does no calendar arithmetic on them.
 *
 * <p>{@link AvailabilityWindow} predates this path and is declared in {@code LocalDateTime}. So a
 * conversion is unavoidable, and {@link ZoneOffset#UTC} is used as a <b>carrier, not an
 * interpretation</b>: the instant's UTC representation becomes the wall-clock fields, and
 * {@link #toInstant} maps them straight back. The round trip is exact and total, so nothing in the
 * plan depends on which zone the student is in — the windows come out in the same order, with the
 * same durations, and the instants that go into the response are bit-for-bit the ones that arrived.
 *
 * <p><b>Reading the result as local time would be wrong.</b> A window that opens at 19:00 in São
 * Paulo appears here as 22:00. Nothing on this path reads the fields as a time of day: placement
 * uses durations and ordering only. The moment something wants to know "is this the evening?", it
 * needs the zone, and the zone is not in the contract — that is a conversation with the platform,
 * not a default to pick here.
 *
 * <h2>{@code expectedEnergyLevel} has no source, so it is a declared sentinel</h2>
 *
 * The record's third component predicts the student's energy in the window (1.0 to 5.0). The platform
 * sends <b>no energy signal of any kind</b>, and the honest options were to leave the field at a
 * plausible middle value or to make its absence visible. It is set to {@link #ENERGY_NOT_MEASURED}
 * and <b>nothing on this path reads it</b>: the SINAPSE placement never sorts or weights by energy.
 *
 * <p>That claim is a test, not a comment. {@code SinapseEnergyIsUnusedTest} plans the same request
 * twice with different energy values on every window and asserts the two plans are identical, which
 * fails the moment a future change starts reading the field. A middle value would have made the same
 * mistake this repository refuses elsewhere: a term that appears to model something and does not.
 */
public final class AvailabilityWindows {

    /**
     * The value every window carries, because energy is not measured on this path.
     *
     * <p>Zero rather than a plausible 3.0: it is outside the documented 1.0–5.0 range, so it cannot
     * be mistaken for an observation, and any code that starts weighting by it produces a visibly
     * degenerate result rather than a subtly wrong one.
     */
    public static final double ENERGY_NOT_MEASURED = 0.0;

    private AvailabilityWindows() {
    }

    /**
     * Converts every slot, in chronological order.
     *
     * @param availability the slots as they arrived
     * @return the windows, sorted by start then end
     */
    public static List<AvailabilityWindow> of(List<PlanRequest.AvailabilitySlot> availability) {
        return availability.stream()
                .map(slot -> new AvailabilityWindow(
                        toLocal(slot.start()), toLocal(slot.end()), ENERGY_NOT_MEASURED))
                .sorted(Comparator.comparing(AvailabilityWindow::startTime)
                        .thenComparing(AvailabilityWindow::endTime))
                .toList();
    }

    /** The carrier conversion: an instant's UTC fields, with no zone applied. */
    public static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** The exact inverse of {@link #toLocal}. */
    public static Instant toInstant(LocalDateTime local) {
        return local.toInstant(ZoneOffset.UTC);
    }
}

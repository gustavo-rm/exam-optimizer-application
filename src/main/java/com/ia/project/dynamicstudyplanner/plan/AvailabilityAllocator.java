package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lays blocks into the student's availability windows in one forward pass.
 *
 * <h2>Sequential, and never backtracking</h2>
 *
 * The allocator holds a cursor. Each block is placed at the cursor if it fits in the current window;
 * otherwise the cursor moves to the next window and tries again. It never returns to a window it has
 * left, so time a short block could have used behind the cursor stays unused.
 *
 * <p>That is the greedy part, and it is a choice with a name: first-fit-forward. Best-fit — scanning
 * every remaining window for the gap a block fills most tightly — would waste less time, and is
 * exactly the kind of packing search the genetic algorithm is there to do. Forward-only allocation
 * buys two properties instead: the placement of block <i>n</i> depends on nothing after it, and the
 * output is chronological by construction, which is what makes {@code sequenceIndex} a count rather
 * than a sort.
 *
 * <p>Two invariants fall out of the cursor being monotonic. Sessions never overlap, because each one
 * starts where the last ended or later. And sessions never leave a window, because a block is only
 * placed once it has been checked to end at or before the window's close — no block is split across
 * two windows, even adjacent ones.
 *
 * <h2>Windows are clipped to the horizon, and overlap is absorbed</h2>
 *
 * Each window is intersected with the horizon read in UTC ({@link HorizonBounds}), and the empty
 * results are dropped; windows are then sorted by opening instant, then closing instant. Two windows
 * that overlap need no special handling: the cursor is already past the second one's opening, so it
 * simply carries on, and no block is placed twice in the same minute.
 *
 * <h2>One instance per request</h2>
 *
 * The cursor is mutable state, so an instance belongs to exactly one call and is never shared. The
 * scheduler builds one inside the method that serves a request, which is what keeps the scheduler
 * itself stateless and safe on any number of threads.
 */
public final class AvailabilityAllocator {

    /** Windows, clipped to the horizon and sorted. */
    private final List<Window> windows;

    /** Index of the window the cursor is in. */
    private int current;

    /** The first instant still free. Only ever moves forward. */
    private Instant cursor;

    private AvailabilityAllocator(List<Window> windows) {
        this.windows = windows;
        this.current = 0;
        this.cursor = windows.isEmpty() ? Instant.EPOCH : windows.get(0).start();
    }

    /**
     * Prepares the windows of one request.
     *
     * @param request the request, already accepted by {@link PlanRequestGuard}
     * @return a fresh allocator, positioned at the first usable minute
     */
    public static AvailabilityAllocator over(PlanRequest request) {
        HorizonBounds horizon = HorizonBounds.of(request.horizon());
        List<Window> windows = request.availability().stream()
                .map(slot -> clip(slot, horizon))
                .filter(window -> window.start().isBefore(window.end()))
                .sorted(Comparator.comparing(Window::start).thenComparing(Window::end))
                .toList();
        return new AvailabilityAllocator(windows);
    }

    private static Window clip(PlanRequest.AvailabilitySlot slot, HorizonBounds horizon) {
        Instant start = slot.start().isBefore(horizon.from()) ? horizon.from() : slot.start();
        Instant end = slot.end().isAfter(horizon.until()) ? horizon.until() : slot.end();
        return new Window(start, end);
    }

    /**
     * Places one block, advancing the cursor past it.
     *
     * @param durationMinutes how long the block needs; positive
     * @return when the block starts, or empty when no remaining window can hold it
     */
    public Optional<Instant> place(int durationMinutes) {
        while (current < windows.size()) {
            Window window = windows.get(current);
            if (cursor.isBefore(window.start())) {
                cursor = window.start();
            }
            Instant finish = cursor.plus(durationMinutes, ChronoUnit.MINUTES);
            if (!finish.isAfter(window.end())) {
                Instant start = cursor;
                cursor = finish;
                return Optional.of(start);
            }
            current++;
        }
        return Optional.empty();
    }

    /**
     * Every minute the student offered, after clipping. Reported in {@code fitness}.
     *
     * <p>Windows are summed as they were sent, so two that overlap are counted twice. The platform
     * expands availability day by day and sorts it before sending
     * ({@code docs/CORE_CONTRACT_SURVEY.md} §4, item 7), so overlap does not occur in its traffic;
     * merging the windows here to make the sum exact would be a rewrite of the request performed for
     * the sake of one reported number, and allocation does not need it — the cursor already refuses
     * to place a block twice in the same minute.
     */
    public long availableMinutes() {
        return windows.stream()
                .mapToLong(window -> ChronoUnit.MINUTES.between(window.start(), window.end()))
                .sum();
    }

    /**
     * One interval of usable time.
     *
     * @param start when it opens, inclusive
     * @param end   when it closes, exclusive
     */
    private record Window(Instant start, Instant end) {
    }
}

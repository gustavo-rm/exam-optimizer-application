package com.ia.project.dynamicstudyplanner.baseline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three determinism rules, read off the source of this package.
 *
 * <h2>Why read the source rather than only the behaviour</h2>
 *
 * {@code BaselineDeterminismTest} compares two runs and would catch a difference that actually
 * happened. It would not catch one waiting to happen: a {@code HashSet} whose iteration order is
 * stable on this JDK and this key set, and different on the next. The three constructs banned below
 * are the ones that produce exactly that kind of latent difference, so they are banned by
 * construction, in the style of {@code arquitetura/ModuleBoundaryTest}.
 *
 * <p>Comments are stripped before scanning: this package documents each of these constructs by name,
 * and a check that could be satisfied by rewording a comment would be worse than none.
 */
@DisplayName("Determinism rules of the baseline package")
class BaselineDeterminismRulesTest {

    private static final Path SOURCES =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner/baseline");

    /** Unordered collections: their iteration order is a function of hashes, not of the request. */
    private static final List<String> UNORDERED = List.of("new HashMap", "new HashSet",
            "HashMap::new", "HashSet::new", "new Hashtable", "Collectors.toSet(",
            "Collectors.toMap(", "Collectors.toUnmodifiableSet(", "Collectors.toUnmodifiableMap(");

    /** Wall-clock time: the reference instant has to come from the request. */
    private static final List<String> WALL_CLOCK = List.of("Instant.now(", "LocalDate.now(",
            "LocalDateTime.now(", "ZonedDateTime.now(", "OffsetDateTime.now(", "Clock.system",
            "System.currentTimeMillis(", "System.nanoTime(");

    /** Randomness: the seed is echoed, never consumed. */
    private static final List<String> RANDOMNESS = List.of("RandomProvider", "new Random",
            "Math.random(", "ThreadLocalRandom", "SecureRandom", "UUID.randomUUID(");

    private static List<String> occurrencesOf(List<String> banned) throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
                banned.stream()
                        .filter(code::contains)
                        .forEach(construct -> found.add(file.getFileName() + " -> " + construct));
            }
        }
        return found;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    @Test
    @DisplayName("no iteration over an unordered collection can reach the output")
    void noUnorderedCollections() throws IOException {
        assertThat(occurrencesOf(UNORDERED))
                .as("""
                        An unordered collection appeared in the baseline package.

                        Its iteration order is a function of hash codes and table size, not of the
                        request, so a plan built through it is reproducible only by accident. Use a
                        LinkedHashMap when insertion order is the order, or a TreeMap/TreeSet over an
                        explicit comparator when a sort is meant.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no wall-clock reading on the decision path")
    void noWallClock() throws IOException {
        assertThat(occurrencesOf(WALL_CLOCK))
                .as("""
                        A wall-clock reading appeared in the baseline package.

                        The reference instant comes from the request — horizon.start at 00:00Z — so
                        that the same request answers the same way tomorrow. Reading the clock would
                        make the revision rule, and with it the whole plan, depend on when it ran.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no source of randomness, and no use of RandomProvider")
    void noRandomness() throws IOException {
        assertThat(occurrencesOf(RANDOMNESS))
                .as("""
                        A source of randomness appeared in the baseline package.

                        This scheduler echoes randomSeed and never consumes it: it is the
                        deterministic condition of the experiment, and the comparison it exists to
                        support only means something if it does not sample.""")
                .isEmpty();
    }
}

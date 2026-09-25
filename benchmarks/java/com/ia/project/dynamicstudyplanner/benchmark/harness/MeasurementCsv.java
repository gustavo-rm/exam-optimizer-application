package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the rows out, one line each, with the header the README declares.
 *
 * <h2>Quoting, and why it is not optional</h2>
 *
 * Nothing this harness emits contains a comma today — the identifiers are hyphenated and the numbers
 * are written under {@link java.util.Locale#ROOT}. The quoting is here anyway, because the day a
 * value does contain one the failure is silent: a shifted column that still parses, in a file that
 * the report's numbers are read from. Writing it correctly costs six lines.
 */
public final class MeasurementCsv {

    private MeasurementCsv() {
    }

    /**
     * Writes the file, creating the directory if it does not exist.
     *
     * @param target    where to write
     * @param termNames the composition's terms, in composition order; the per-term columns
     * @param rows      the rows, in run order
     */
    public static void write(Path target, List<String> termNames, List<MeasurementRow> rows) {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(line(MeasurementRow.header(termNames)));
        rows.forEach(row -> lines.add(line(row.cells(termNames))));
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, lines, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write " + target, failure);
        }
    }

    private static String line(List<String> cells) {
        return String.join(",", cells.stream().map(MeasurementCsv::quoted).toList());
    }

    private static String quoted(String cell) {
        if (cell.indexOf(',') < 0 && cell.indexOf('"') < 0 && cell.indexOf('\n') < 0) {
            return cell;
        }
        return '"' + cell.replace("\"", "\"\"") + '"';
    }
}

package com.ia.project.dynamicstudyplanner.diagnostic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Escrita mínima de CSV para o diagnóstico: ponto decimal, aspas só quando preciso.
 *
 * <p>O diretório de saída vem da propriedade de sistema {@value #OUT_PROPERTY}; sem ela, é
 * {@code target/diagnostic}. Nada é escrito em {@code src/}: o que for publicado é copiado de lá
 * à mão, depois de lido.
 */
final class Csv {

    static final String OUT_PROPERTY = "diagnostic.out";

    private Csv() {
    }

    static Path outputDirectory() {
        return Path.of(System.getProperty(OUT_PROPERTY, "target/diagnostic"));
    }

    static void write(String file, String header, List<String> rows) {
        List<String> lines = new ArrayList<>(rows.size() + 1);
        lines.add(header);
        lines.addAll(rows);
        try {
            Path directory = outputDirectory();
            Files.createDirectories(directory);
            Files.write(directory.resolve(file), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code NaN} é escrito como {@code NaN}, e não omitido: neste diagnóstico ele é um achado. */
    static String num(double value) {
        return Double.isNaN(value) ? "NaN" : String.format(Locale.ROOT, "%.6f", value);
    }

    static String quote(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return "\"" + text.replace("\"", "\"\"").replace('\n', ' ') + "\"";
    }
}

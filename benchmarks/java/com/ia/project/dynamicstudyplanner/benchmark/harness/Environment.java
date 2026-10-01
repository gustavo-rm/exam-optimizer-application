package com.ia.project.dynamicstudyplanner.benchmark.harness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * O ambiente em que uma medição rodou, registrado — e a parte dele que é <b>invariante</b>.
 *
 * <h2>Por que a versão do JDK é uma invariante e não só proveniência</h2>
 *
 * A reprodutibilidade desta medição é <b>condicional ao JDK</b>, e a condição é precisa. A ordem
 * canônica dos genes de um plano tático sai da ordem de iteração de um {@code HashMap}
 * ({@code TacticalStudyPlan.extractDaysPerItem}, fragilidade registrada em G6). Essa ordem é
 * determinística para hashes fixos — ao contrário de {@code Map.copyOf}, cuja semente muda por
 * execução — mas é <b>detalhe de implementação</b> da biblioteca padrão: não é especificada, e uma
 * atualização de JDK pode mudá-la.
 *
 * <p>Então duas execuções sob JDKs diferentes podem divergir sem que nada no código tenha mudado, e
 * comparar os números de uma com os da outra seria comparar duas medições que não compartilham a
 * condição. Recusar é melhor que avisar: um aviso num log de 2 880 linhas não é lido.
 *
 * <p>Isto <b>não é</b> uma afirmação de que o JDK {@value #EXPECTED_JDK_MAJOR} é o único que funciona.
 * É a afirmação de que os números publicados foram medidos nele, e que quem rodar noutro produz um
 * conjunto novo, não uma continuação deste.
 *
 * <h2>O resto é proveniência</h2>
 *
 * Tempo de parede depende da máquina — medido na pendência G17, a mediana do guloso variou de 138 para
 * 167 µs entre duas execuções sem mudança de código. Um CSV com colunas de tempo e sem registro da
 * máquina convida exatamente à comparação que G17 mostrou ser inválida.
 */
public final class Environment {

    /** O JDK maior em que os números publicados foram medidos. */
    public static final int EXPECTED_JDK_MAJOR = 21;

    private Environment() {
    }

    /**
     * Recusa um JDK diferente do medido.
     *
     * @throws IllegalStateException quando a versão maior difere, com a razão
     */
    public static void requireExpectedJdk() {
        int major = Runtime.version().feature();
        if (major != EXPECTED_JDK_MAJOR) {
            throw new IllegalStateException(String.format(
                    "This harness was calibrated on JDK %d and is running on JDK %d.%n"
                    + "  Refusing rather than warning. The canonical gene order of a tactical plan "
                    + "comes from HashMap iteration order (G6), which is deterministic for fixed "
                    + "hash codes but is an unspecified implementation detail: a JDK change can "
                    + "move it, and then two runs diverge with no code change.%n"
                    + "  Numbers measured here would not be a continuation of the published set. "
                    + "To measure on this JDK deliberately, raise EXPECTED_JDK_MAJOR and re-measure "
                    + "every condition, treating the result as a new series.",
                    EXPECTED_JDK_MAJOR, major));
        }
    }

    /** @return o ambiente, uma linha por propriedade */
    public static List<String> fingerprint() {
        Runtime runtime = Runtime.getRuntime();
        return List.of(
                "measured-at=" + Instant.now(),
                "java.version=" + System.getProperty("java.version"),
                "java.vendor=" + System.getProperty("java.vendor"),
                "java.vm.name=" + System.getProperty("java.vm.name"),
                "java.vm.version=" + System.getProperty("java.vm.version"),
                "jdk.major=" + Runtime.version().feature(),
                "expected.jdk.major=" + EXPECTED_JDK_MAJOR,
                "os.name=" + System.getProperty("os.name"),
                "os.arch=" + System.getProperty("os.arch"),
                "os.version=" + System.getProperty("os.version"),
                "availableProcessors=" + runtime.availableProcessors(),
                "maxMemoryMiB=" + runtime.maxMemory() / (1024 * 1024));
    }

    /**
     * Escreve o ambiente ao lado de um CSV de resultados.
     *
     * <p>Num arquivo irmão e não em colunas: o ambiente é o mesmo para todas as linhas de uma
     * execução, e repeti-lo 2 880 vezes seria carregar a mesma constante num arquivo que já é grande.
     *
     * @param results o CSV que esta execução escreveu
     */
    public static void writeBeside(Path results) {
        String name = results.getFileName().toString().replaceFirst("\\.csv$", "") + "-environment.txt";
        Path target = results.toAbsolutePath().getParent().resolve(name);
        try {
            Files.write(target, fingerprint(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write " + target, failure);
        }
    }
}

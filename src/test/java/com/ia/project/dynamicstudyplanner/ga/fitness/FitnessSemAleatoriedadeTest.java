package com.ia.project.dynamicstudyplanner.ga.fitness;

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
 * Nenhuma classe sob {@code ga/fitness} consome aleatoriedade.
 *
 * <h2>A invariante era implícita, e implícita não é invariante</h2>
 *
 * A fitness precisa ser <b>função pura do plano e do contexto</b>. Um sorteio dentro dela faria o
 * mesmo plano receber notas diferentes a cada reavaliação, e as consequências não são sutis: o
 * torneio de seleção compararia indivíduos por números que mudam durante a comparação, o
 * elitismo carregaria adiante um indivíduo cuja nota não se sustenta, e a detecção de estagnação
 * veria melhora onde houve ruído. Acima de tudo, a reprodutibilidade por semente — que este
 * repositório trata como requisito, não conforto — deixaria de valer: a <b>quantidade</b> e a
 * <b>ordem</b> dos sorteios passariam a depender de quantas vezes cada indivíduo foi avaliado.
 *
 * <p>Nada garantia isso até aqui. Valia porque nenhuma das sete classes de fitness por acaso
 * sorteia, e a primeira que sorteasse não encontraria nada que a impedisse.
 *
 * <h2>Limitação conhecida desta verificação: varredura de FONTE</h2>
 *
 * <b>Este teste lê texto de arquivo, não bytecode.</b> Ele pega a referência direta — {@code import
 * ...RandomProvider}, {@code new Random(}, {@code Math.random()} — e <b>não pega dependência
 * transitiva</b>: uma classe de fitness que chamasse um colaborador que, lá dentro, sorteia,
 * passaria por aqui sem acusar. Também não pega reflexão nem uma fonte alcançada por interface.
 *
 * <p>A verificação que fecharia esse buraco é de grafo de chamadas — ArchUnit resolveria, e
 * <b>ArchUnit não é dependência deste projeto</b>. Acrescentar uma exige decisão de quem mantém o
 * {@code pom.xml}, então a varredura de fonte é o que existe: pega o caso direto, que é o provável,
 * e declara o que não pega em vez de deixar supor que cobre tudo.
 */
@DisplayName("Fitness e funcao pura: nada sob ga/fitness sorteia")
class FitnessSemAleatoriedadeTest {

    private static final Path FONTES =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner/ga/fitness");

    /** Toda porta de entrada para um sorteio que um arquivo desta árvore poderia citar. */
    private static final List<String> ALEATORIEDADE = List.of(
            "RandomProvider", "new Random", "Random(", "Math.random(", "ThreadLocalRandom",
            "SecureRandom", "SplittableRandom", "UUID.randomUUID(", "Collections.shuffle(");

    /** Comentário não é código: o Javadoc pode citar {@code RandomProvider} para explicar a regra. */
    private static String semComentarios(String fonte) {
        return fonte.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    @Test
    @DisplayName("nenhum arquivo de ga/fitness cita uma fonte de aleatoriedade")
    void nenhumArquivoCitaAleatoriedade() throws IOException {
        List<String> encontrados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String codigo = semComentarios(Files.readString(arquivo, StandardCharsets.UTF_8));
                ALEATORIEDADE.stream()
                        .filter(codigo::contains)
                        .forEach(termo -> encontrados.add(arquivo.getFileName() + " -> " + termo));
            }
        }

        assertThat(encontrados)
                .as("""
                        Uma classe de fitness passou a sortear.

                        A fitness tem de ser funcao pura do plano e do contexto. Com um sorteio
                        dentro dela, o mesmo plano recebe notas diferentes a cada reavaliacao: o
                        torneio compara numeros que mudam durante a comparacao, e a quantidade e a
                        ordem dos sorteios passam a depender de quantas vezes cada individuo foi
                        avaliado — o que acaba com a reproducao por semente.

                        Se o termo novo precisa mesmo existir, ele nao pertence a ga/fitness.""")
                .isEmpty();
    }

    /** A varredura so vale se estiver lendo alguma coisa. */
    @Test
    @DisplayName("a varredura esta mesmo lendo os arquivos de fitness")
    void aVarreduraLeOsArquivos() throws IOException {
        try (Stream<Path> arquivos = Files.walk(FONTES)) {
            assertThat(arquivos.filter(p -> p.toString().endsWith(".java")).count())
                    .as("um teste que varre um diretorio vazio passa sempre e nao prova nada")
                    .isGreaterThanOrEqualTo(7);
        }
    }
}

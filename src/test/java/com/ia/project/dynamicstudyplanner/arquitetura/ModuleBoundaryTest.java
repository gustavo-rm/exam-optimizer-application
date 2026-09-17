package com.ia.project.dynamicstudyplanner.arquitetura;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava as fronteiras entre os módulos de topo, lendo os {@code import} do código-fonte.
 *
 * <h2>Por que um teste, e não uma convenção escrita</h2>
 *
 * A etapa 03 encontrou um ciclo {@code ga} ↔ {@code service} formado por quatro imports diretos, e a
 * etapa 03b o desfez movendo contratos para {@code domain}. Nada impede que o próximo import direto
 * o recrie — e um ciclo de dependência não produz erro de compilação nem falha de teste. Só volta a
 * aparecer quando alguém tentar extrair um módulo, meses depois.
 *
 * <p>Este teste não usa biblioteca de análise arquitetural: lê os arquivos, extrai os imports do
 * próprio projeto e verifica duas regras. É pequeno o bastante para ser lido inteiro por quem ele
 * reprovar, o que importa mais aqui do que sofisticação — a mensagem de falha precisa explicar a
 * regra a quem nunca leu o diagnóstico.
 */
@DisplayName("Fronteiras entre modulos")
class ModuleBoundaryTest {

    private static final Path RAIZ =
            Path.of("src/main/java/com/ia/project/dynamicstudyplanner");
    private static final String PACOTE_BASE = "com.ia.project.dynamicstudyplanner";
    private static final Pattern IMPORT_INTERNO =
            Pattern.compile("^import\\s+(?:static\\s+)?" + Pattern.quote(PACOTE_BASE) + "\\.([\\w.]+);",
                    Pattern.MULTILINE);

    /** Mapa módulo de topo -> módulos de topo que ele importa. */
    private Map<String, Set<String>> grafoDeModulos() throws IOException {
        Map<String, Set<String>> grafo = new HashMap<>();
        try (Stream<Path> arquivos = Files.walk(RAIZ)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String origem = moduloDe(RAIZ.relativize(arquivo).toString().replace('/', '.'));
                String fonte = Files.readString(arquivo, StandardCharsets.UTF_8);
                Matcher m = IMPORT_INTERNO.matcher(fonte);
                while (m.find()) {
                    String destino = moduloDe(m.group(1));
                    if (!destino.equals(origem)) {
                        grafo.computeIfAbsent(origem, k -> new HashSet<>()).add(destino);
                    }
                }
            }
        }
        return grafo;
    }

    /** Primeiro segmento do caminho é o módulo de topo; classes na raiz do pacote viram "(raiz)". */
    private static String moduloDe(String caminhoOuImport) {
        String primeiro = caminhoOuImport.split("\\.")[0];
        return Character.isUpperCase(primeiro.charAt(0)) ? "(raiz)" : primeiro;
    }

    @Test
    @DisplayName("nao ha ciclo de dependencia entre modulos de topo")
    void naoHaCicloEntreModulos() throws IOException {
        Map<String, Set<String>> grafo = grafoDeModulos();

        List<String> ciclos = new ArrayList<>();
        for (var entrada : grafo.entrySet()) {
            for (String destino : entrada.getValue()) {
                if (grafo.getOrDefault(destino, Set.of()).contains(entrada.getKey())
                        && entrada.getKey().compareTo(destino) < 0) {
                    ciclos.add(entrada.getKey() + " <-> " + destino);
                }
            }
        }

        assertThat(ciclos)
                .as("""
                        Um ciclo de dependencia entre modulos voltou.

                        Modulos em ciclo nao podem ser compilados, testados nem extraidos
                        separadamente. O ciclo ga <-> service existia ate a etapa 03b e foi desfeito
                        movendo os contratos de calculo para domain (ADR-0001).

                        Se o novo import precisa mesmo existir, a correcao quase sempre e a mesma:
                        mover a INTERFACE para o modulo de quem a consome, ou para domain, deixando
                        a implementacao onde esta.""")
                .isEmpty();
    }

    @Test
    @DisplayName("o dominio nao depende de nenhum outro modulo do projeto")
    void oDominioNaoDependeDeNinguem() throws IOException {
        Set<String> dependenciasDoDominio = grafoDeModulos().getOrDefault("domain", Set.of());

        assertThat(dependenciasDoDominio)
                .as("""
                        O dominio passou a depender de outro modulo do projeto.

                        Ele e a camada de politica: tudo depende dele e ele nao depende de nada.
                        Manter isso e o que permite que qualquer reorganizacao futura se apoie no
                        dominio com seguranca.""")
                .isEmpty();
    }

    @Test
    @DisplayName("o dominio nao depende de framework de infraestrutura")
    void oDominioNaoDependeDeFramework() throws IOException {
        List<String> violacoes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(RAIZ.resolve("domain"))) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    if (linha.startsWith("import org.springframework")
                            || linha.startsWith("import jakarta.")
                            || linha.startsWith("import io.swagger")
                            || linha.startsWith("import com.fasterxml")) {
                        violacoes.add(RAIZ.relativize(arquivo) + " -> " + linha.trim());
                    }
                }
            }
        }

        assertThat(violacoes)
                .as("""
                        Uma classe de dominio passou a importar framework.

                        O dominio deve permanecer puro: sem Spring, sem Jakarta, sem Jackson, sem
                        anotacao de serializacao. A unica dependencia externa tolerada e o Lombok,
                        que e processador em tempo de compilacao e nao deixa rastro em execucao.

                        Se um objeto de dominio precisa ser serializado, o lugar da anotacao e o DTO
                        em api/dto, nao o modelo.""")
                .isEmpty();
    }
}

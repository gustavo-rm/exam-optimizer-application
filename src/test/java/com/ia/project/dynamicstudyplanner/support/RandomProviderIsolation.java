package com.ia.project.dynamicstudyplanner.support;

import com.ia.project.dynamicstudyplanner.util.RandomProvider;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Random;

/**
 * Isola o estado estático global de {@link RandomProvider} entre testes.
 *
 * <h2>O problema que esta extensão resolve</h2>
 *
 * {@code RandomProvider} guarda a fonte de aleatoriedade do algoritmo genético num campo
 * {@code static} mutável. Testes que precisam de determinismo gravam nele
 * ({@code RandomProvider.setInstance(new Random(seed))}), e o {@code ProductionGeneticAlgorithm}
 * dos benchmarks faz o mesmo. Uma semente deixada instalada vaza para o próximo teste, que passa a
 * rodar com aleatoriedade fixa sem saber — e o resultado depende então da ordem de execução.
 *
 * <p>Antes desta extensão a contenção era por disciplina: {@code GaEdgeCasesTest} tinha um
 * {@code @AfterEach} próprio restaurando {@code SecureRandom}. Isso funciona enquanto todo teste
 * futuro lembrar de fazer o mesmo. A extensão troca disciplina por garantia estrutural.
 *
 * <h2>Como está registrada</h2>
 *
 * Registro automático, via {@code src/test/resources/junit-platform.properties}
 * ({@code junit.jupiter.extensions.autodetection.enabled=true}) e o arquivo de serviço em
 * {@code META-INF/services}. Aplica-se a <b>todos</b> os testes do classpath de teste — incluindo os
 * de {@code benchmarks/java} — sem que nenhuma classe precise se anotar.
 *
 * <h2>Limite conhecido — resolvido na etapa 06b</h2>
 *
 * Até a etapa 06b esta extensão isolava apenas testes executados <b>em sequência</b>: o campo de
 * {@code RandomProvider} era um {@code static} do processo, então dois testes em paralelo
 * disputariam o mesmo campo e a restauração de um sobrescreveria a semente do outro.
 *
 * <p>A correção do achado E3 tornou a fonte <b>local à thread</b>. Como {@code beforeEach} e
 * {@code afterEach} rodam na mesma thread do teste, guardar e repor passou a ser naturalmente
 * isolado: rodar testes em paralelo deixou de ser um problema para esta extensão.
 */
public class RandomProviderIsolation implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(RandomProviderIsolation.class);

    private static final String KEY = "randomProviderBefore";

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put(KEY, RandomProvider.getInstance());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Random previous = context.getStore(NAMESPACE).remove(KEY, Random.class);
        // Se o teste instalou uma semente fixa, devolve a fonte que estava instalada antes dele.
        // Na ausência de valor guardado (caminho que não deveria ocorrer), volta ao padrão de
        // produção em vez de deixar a semente do teste instalada.
        // O padrao de reposicao acompanha o de producao: Random, nao SecureRandom (achado F1 da
        // etapa 05). Antes desta correcao a extensao reinstalava SecureRandom, que deixaria o
        // teste seguinte medindo um gerador que producao nao usa mais.
        RandomProvider.setInstance(previous != null ? previous : new Random());
    }
}

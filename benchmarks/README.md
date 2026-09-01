# Benchmarks — AG de produção vs. baselines

Módulo de medição isolado. **Nada aqui é servido em produção.**

O relatório com os resultados e a análise está em
[`docs/revisao-ag/03-validacao.md`](../docs/revisao-ag/03-validacao.md).

## Isolamento

`benchmarks/java` é anexado como **test source root** pelo `build-helper-maven-plugin`
(ver `pom.xml`). O `spring-boot-maven-plugin` empacota apenas classes de `src/main`, portanto este
código não entra no jar da aplicação. A separação é estrutural, não convencional.

Este módulo **lê** classes de produção e nunca as modifica: o sistema sob teste é o
`StudyOptimizerService` real, com os hiperparâmetros de `DefaultGeneticAlgorithmFactory`.

## Como rodar

### Teste de regressão (parte da suíte normal)

```bash
./mvnw test
```

`GeneticAlgorithmVsBaselinesTest` roda 7 instâncias × 6 estratégias × 5 repetições em ~2 s e falha
se o AG ficar mais de 2% abaixo do baseline guloso por prioridade. A justificativa do limiar está
no Javadoc da classe e em `03-validacao.md` §7.

### Relatório completo

```bash
./mvnw -q test-compile
./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt -Dmdep.includeScope=test
java -cp "target/classes:target/test-classes:$(cat /tmp/cp.txt)" \
     com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain
```

Roda as 8 instâncias com 7 repetições (~4 s), escreve `benchmarks/results/resultados.csv` e imprime
as tabelas Markdown usadas no relatório.

### Estudo de robustez

```bash
java -cp "target/classes:target/test-classes:$(cat /tmp/cp.txt)" \
     com.ia.project.dynamicstudyplanner.benchmark.robustness.RobustnessMain
```

Determinismo (mesma seed, duas execuções), variância entre 12 seeds e varredura de hiperparâmetros.
Escreve `benchmarks/results/robustez.csv`. Alimenta
[`docs/revisao-ag/04-robustez.md`](../docs/revisao-ag/04-robustez.md).

Os testes de casos de borda correspondentes ficam em
`src/test/java/…/ga/GaEdgeCasesTest.java` — são testes unitários de classes de produção, não
benchmarks, e por isso vivem junto com a suíte normal.

## Estrutura

| Pacote | Conteúdo |
|---|---|
| `instance` | `InstanceLibrary` — 8 instâncias sintéticas determinísticas (seed fixa, data-âncora constante) |
| `strategy` | Os planejadores comparados, todos implementando `PlanningStrategy` |
| `metric` | `MetricsCalculator` — fitness, tempo e métricas de negócio, via classes de produção |
| `harness` | `BenchmarkHarness` (execução + agregação) e `BenchmarkMain` (CSV + tabelas) |

### Planejadores

| id | Papel |
|---|---|
| `ag-producao` | Sistema sob teste: `StudyOptimizerService.optimize()` real |
| `guloso-prioridade` | Baseline 1 (contrato do teste): dias proporcionais ao peso de edital |
| `uniforme` | Baseline 2: divisão igual entre disciplinas |
| `aleatorio` | Baseline 3: sorteio respeitando só as restrições rígidas |
| `melhor-de-n-aleatorios` | Controle: melhor de N sorteios, N = população inicial do AG. Isola o valor da evolução |
| `otimo-exato` | Controle: guloso marginal, ótimo exato da fitness atual (ver `01-auditoria-fitness.md` Apêndice A) |

## Ao alterar o AG de produção

1. Rode `./mvnw test`. Se `knownDeficiencyListIsStillAccurate` falhar, a lista de instâncias
   deficientes mudou — investigue antes de editá-la.
2. Rode o relatório completo e atualize `docs/revisao-ag/03-validacao.md` com os novos números.
3. Se acrescentar um `FitnessObjective`, `FitnessPenalty` ou `ConstraintValidator` em produção,
   adicione-o também a `BenchmarkHarness.productionFitnessEvaluator()` — as listas são explícitas de
   propósito, e esquecê-las invalidaria silenciosamente a comparação.

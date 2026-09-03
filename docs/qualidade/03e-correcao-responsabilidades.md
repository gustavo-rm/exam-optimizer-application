# 03e — Correção do achado E8: responsabilidades do otimizador

- **Data:** 2026-09-03
- **Branch:** `release/v4.0`
- **Achado atendido:** **E8** (nível 3) de [`03-diagnostico-estrutura.md`](./03-diagnostico-estrutura.md)
- **Achados fechados junto:** **E12** e **E13** (nível 4) — tocavam exatamente as linhas movidas
- **ADR produzido:** [ADR-0006](../adr/0006-separacao-de-responsabilidades-do-otimizador.md)

---

## Resumo em uma tela

| | Antes | Depois |
|---|---|---|
| Classes | 1 | 3 |
| Maior classe | 192 linhas / 122 de código | 122 linhas / **70** de código |
| Responsabilidades por classe | 4 | 1 |
| Total de linhas de código | 122 | 157 (**+35** — ver §5) |
| Cobertura das classes envolvidas | 76,8 % instruções | **100 %** |
| Métodos cobertos | 7 de 8 | **12 de 12** |
| Testes | 164 | **173** (+9) |
| Cobertura total (BUNDLE) | 82,64 % instr. / 61,05 % ramos | **84,04 % / 61,80 %** |
| Suíte | verde | **verde** (`mvn clean verify`, 173/173) |

**E8 foi fechado por completo**, e com ele E12 e E13 — que não eram escopo declarado, mas viviam nas
mesmas linhas e não sobreviveriam à movimentação de qualquer forma.

---

## 1. O achado

> **E8** — `StudyOptimizerService` mistura 4 responsabilidades (cálculo, orquestração, métricas,
> montagem de contexto).

E, no corpo do diagnóstico (§3.2):

```java
private final BaselineCalculator baselineCalculator;        // 1. cálculo de domínio
private final ImportanceCalculator importanceCalculator;    // 1.
private final ... CognitiveLoadCalculator ... = new ...();  // 1.
private final GeneticAlgorithmFactory gaFactory;            // 2. orquestração do AG
private final PopulationGenerator populationGenerator;      // 2.
private final ... FitnessEvaluator fitnessEvaluator;        // 2.
private final Counter optimizationRunsCounter;              // 3. observabilidade
private final Timer optimizationTimer;                      // 3.
```

> Mais **4. a montagem do `EvolutionContext`** […]. Consequência prática: mudar como a fitness é
> montada, mudar quais métricas são publicadas e mudar quais calculadoras alimentam o contexto são
> três motivos independentes para editar o mesmo arquivo.

---

## 2. Quatro assuntos, três classes — e por que não quatro

Esta é a decisão que precisava de justificativa, porque contraria a leitura literal do achado.

| Classe | Responsabilidade | Muda quando… |
|---|---|---|
| `EvolutionContextAssembler` | **1 + 4** — o que a evolução precisa saber | um dado novo entra no contexto |
| `OptimizationMetrics` | **3** — quantas rodaram, quanto demoraram | uma métrica nova é publicada |
| `StudyOptimizerService` | **2** — a sequência de passos | o fluxo da otimização muda |

Ao separar, ficou visível que **1 e 4 eram o mesmo assunto**. As três calculadoras e o
`FitnessEvaluator` não tinham nenhum outro consumidor dentro do serviço — cada um era lido numa
única linha, toda ela dentro de `prepareContext`. "Cálculo de domínio" não era uma responsabilidade
paralela à "montagem do contexto"; era **o conteúdo dela**.

Uma quarta classe seria uma que apenas repassa quatro dependências adiante: indireção sem
informação. O critério aplicado é **um motivo de mudança por classe**, não a contagem de
substantivos — quatro assuntos identificáveis não implicam quatro classes. Registrado em
[ADR-0006](../adr/0006-separacao-de-responsabilidades-do-otimizador.md).

### O que sobrou no orquestrador

O método público inteiro, depois da extração:

```java
public OptimizationResult optimize(Exam exam, StudentProfile profile,
                                   int totalDays, int numGenerations, int populationSize) {
    OptimizationMetrics.Timed<Individual> melhor = metrics.recordRun(() -> {
        EvolutionContext context = contextAssembler.assemble(exam, profile);
        GeneticAlgorithm ga = gaFactory.create();
        Population inicial = populationGenerator.generate(exam, totalDays, populationSize, context);
        return runEvolution(inicial, ga, numGenerations, context).getFittest();
    });

    return new OptimizationResult(melhor.resultado().getPlan(), melhor.resultado().getFitness(),
            numGenerations, melhor.duracaoMs());
}
```

Cada passo é uma chamada nomeada. Os comentários `// Step 1:` … `// Step 5:` da versão anterior
foram removidos porque deixaram de ter função: eles existiam para dar nome a blocos de código que
agora têm nome próprio.

---

## 3. Uma medição, dois destinos

O tempo de execução tem dois consumidores: o `Timer` do Micrometer, que alimenta o monitoramento, e
o campo `executionTimeMillis` do `OptimizationResult`, que volta ao cliente na resposta da API.

`OptimizationMetrics.recordRun` mede **uma vez** e entrega os dois, em vez de expor `Counter` e
`Timer` para o serviço cronometrar por fora:

```java
public <T> Timed<T> recordRun(Supplier<T> execucao) {
    runsCounter.increment();
    long inicio = System.nanoTime();
    try {
        T resultado = execucao.get();
        return new Timed<>(resultado, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
    } finally {
        durationTimer.record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
    }
}
```

A ordem da versão anterior foi preservada deliberadamente: o contador incrementa **antes** da
execução e o cronômetro registra em `finally`. Essas duas garantias existiam no código antigo e
**nenhum teste as exercitava** — eram verdadeiras por acidente de escrita, não por contrato
verificado. Agora estão travadas por `OptimizationMetricsTest`, com o motivo escrito no teste:

> Se só o sucesso fosse medido, uma degradação que faz tudo estourar o prazo apareceria como
> **queda** no volume e **melhora** na duração média.

---

## 4. O que a separação tornou testável

Este é o argumento do E8 em forma executável. Antes, montar o contexto era um método `private`:
verificar se o horizonte de planejamento estava certo exigia rodar uma otimização inteira e
inspecionar o plano no fim — um teste caro, lento e indireto, que falharia por dezenas de razões sem
relação com o que se queria verificar.

**+9 testes, todos inviáveis de escrever antes da separação:**

| Arquivo | Testes | O que trava |
|---|---|---|
| `service/EvolutionContextAssemblerTest` | 4 | Contexto completo (nenhum campo nulo por esquecimento); horizonte = distância até a prova, **nunca menor que 1** (zero produziria divisão por zero no termo de retenção); horas por dia derivadas da disponibilidade semanal, nunca zero; mesmo pedido → mesmo contexto |
| `service/OptimizationMetricsTest` | 3 | O contador conta **tentativas**, não sucessos; a falha lenta ainda entra na amostra de duração; a duração devolvida e a registrada vêm da mesma medição |
| `service/StudyOptimizerServiceTest` | 2 | O bloco de rastreio dentro do laço de gerações; a ligação entre duração medida e duração relatada |

Sobre o **bloco de rastreio**: ele só executa com o log em `TRACE`, o que nenhum teste ligava — daí
`runEvolution` estar a 40 % de cobertura. Não é preciosismo de cobertura: esse bloco chama
`getWorst()` e `getAverageFitness()`, que **não aparecem em nenhum outro caminho do código**. Se uma
delas falhasse numa população degenerada, o defeito só apareceria em produção, e justamente quando
alguém tivesse ligado o rastreio para investigar outro problema.

Resultado: as três classes estão a **100 % de instruções cobertas** e **12 de 12 métodos**, contra
76,8 % e 7 de 8 antes.

---

## 5. O custo, dito com o número

**As linhas de código aumentaram: 122 → 157 (+35).** Três construtores e três conjuntos de `import`
custam mais do que as 20 linhas do método privado economizado.

Quem esperava "refatorar reduz código" não vai encontrar isso aqui, e seria desonesto apresentar a
etapa assim. O que mudou foi outra coisa:

- a **maior classe** caiu de 122 para **70** linhas de código;
- cada uma das três tem **um** motivo de mudança, em vez de uma ter três;
- entender uma otimização de ponta a ponta agora exige abrir três arquivos — custo simétrico do
  benefício de que quem só quer a sequência não precise passar pela montagem do contexto.

---

## 6. E12 e E13: fechados de carona, e uma correção ao diagnóstico

Nenhum dos dois era escopo declarado. Ambos viviam exatamente nas linhas movidas e não
sobreviveriam à movimentação de qualquer forma — não fazia sentido preservá-los para uma etapa
futura.

**E12 — `CognitiveLoadCalculator` obtido com `new` num serviço e por injeção em outro, no mesmo
fluxo.** A versão com `new` escapava de qualquer substituição por dublê em teste. Agora é injetada
em `EvolutionContextAssembler`; a classe já era um `@Service`, então o `new` nunca teve razão de
ser. Não resta nenhum `new CognitiveLoadCalculator()` em `src/main`.

**E13 — nomes totalmente qualificados em declaração de campo.** As três ocorrências do arquivo
(linhas 36, 37 e 40) viraram `import`.

> **Correção ao diagnóstico.** O achado E13 afirmava que o padrão ocorria *"em duas linhas do mesmo
> arquivo e em nenhum outro lugar do repositório"*. **Isso não se sustenta.** Uma varredura em
> `src/main` encontra nomes totalmente qualificados em **9 outros arquivos**, e pelo menos um deles
> — `api/dto/StudentStateDto.java:36`, um componente de `record`, que é declaração de campo — cai na
> definição estrita do achado. A instância que este trabalho tocava está corrigida; **o padrão mais
> amplo continua aberto** e não foi perseguido, porque isso seria alargar a etapa por conta própria.

---

## 7. Arquivos tocados

### Produção

| Arquivo | O quê |
|---|---|
| `service/StudyOptimizerService.java` | 192 → 122 linhas; só orquestração |
| `service/EvolutionContextAssembler.java` | **novo** — calculadoras + montagem do contexto |
| `service/OptimizationMetrics.java` | **novo** — contador, cronômetro e `Timed<T>` |

### Testes e benchmarks

| Arquivo | O quê |
|---|---|
| `service/EvolutionContextAssemblerTest.java` | **novo** — 4 testes |
| `service/OptimizationMetricsTest.java` | **novo** — 3 testes |
| `service/StudyOptimizerServiceTest.java` | **novo** — 2 testes |
| `ga/GaEdgeCasesTest.java` | fixture atualizada; montagem do assembler isolada num método |
| `benchmarks/.../ProductionGeneticAlgorithm.java` | fiação atualizada; o ponto de substituição do `BaselineCalculator` passou a ser o assembler |

Sobre o benchmark: `WeightTradeoffMain` precisa trocar o `BaselineCalculator` para precificar uma
mudança no piso de cobertura **sem fazê-la**. Antes, o calculador era entregue ao orquestrador, que
apenas o repassava. Agora vai para quem de fato o usa. A costura ficou explícita em vez de
incidental — e o Javadoc do benchmark foi atualizado para dizer isso.

### Documentação

| Arquivo | O quê |
|---|---|
| `docs/adr/0006-separacao-de-responsabilidades-do-otimizador.md` | **novo** |
| `docs/adr/README.md` | índice |
| `docs/qualidade/03e-correcao-responsabilidades.md` | este arquivo |

---

## 8. Validação

`mvn clean verify`, com o *gate* de cobertura ativo:

```
[INFO] Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.13:check (check-coverage-floor) ---
[INFO] BUILD SUCCESS
```

- **173 testes, 0 falhas** (eram 164 ao fim da etapa 03d).
- **Nenhum teste existente precisou mudar de asserção.** Só a *fiação* de dois arquivos
  (`GaEdgeCasesTest`, `ProductionGeneticAlgorithm`) foi atualizada, porque o construtor do serviço
  mudou. Isso é o esperado numa extração que preserva comportamento: se alguma asserção tivesse
  precisado mudar, seria sinal de que a refatoração alterou o que o sistema faz.
- **`ModuleBoundaryTest` verde**: a nova classe está em `service` e importa `ga`, que é a direção
  permitida. Pôr o montador junto do `EvolutionContext`, em `ga`, seria mais coeso e recriaria o
  ciclo desfeito na etapa 03b.
- **Contrato OpenAPI intacto**: `OpenApiContractTest` passa — nada disto é observável pela API.
- A suíte foi executada após cada mudança estrutural, não só no fim.

---

## 9. O que ficou de fora, e por quê

| Item | Situação |
|---|---|
| **Elevar o piso do *gate* de cobertura** | O piso está em 77 % de instruções e a medição atual é 84,04 % — a folga cresceu por três etapas seguidas. Elevar o piso é o que mantém um *ratchet* fazendo seu trabalho, mas mexer no *gate* afeta todo trabalho em curso de outras pessoas. **Fica como recomendação, não como mudança feita.** |
| **O padrão amplo de nomes qualificados** (9 arquivos) | Aberto. Ver §6 — é maior do que o E13 descrevia, e persegui-lo seria alargar a etapa sem pedido. |
| **Achados E9, E10, E11** e os demais de nível 4 | Fora do escopo desta etapa. Continuam abertos em `03-diagnostico-estrutura.md`. |
| **E2** (composição de fitness duplicada) | Decidido na etapa 03b como duplicação intencional vigiada (ADR-0003). Sem mudança aqui. |

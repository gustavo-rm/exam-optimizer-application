# ADR-0006 — Três classes para quatro responsabilidades no otimizador

- **Estado:** Aceito
- **Data:** 2026-09-03
- **Etapa:** 03e — correção do achado E8
- **Achado atendido:** E8 (`StudyOptimizerService` mistura 4 responsabilidades)

## Contexto

`StudyOptimizerService` tinha 192 linhas e, segundo o diagnóstico de estrutura, **quatro assuntos
distintos**:

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

Mais **4. a montagem do `EvolutionContext`**, num método privado de 20 linhas.

O custo não era estético. Mudar quais métricas são publicadas, mudar como a fitness é montada e
mudar quais calculadoras alimentam o contexto eram **três motivos independentes para editar o mesmo
arquivo** — e o histórico de commits não os distinguia. Havia ainda uma consequência de teste: a
montagem do contexto era um método `private`, então verificar se o horizonte de planejamento estava
certo exigia rodar uma otimização inteira e inspecionar o plano no fim.

## Decisão

**Extrair duas classes, não três — e dizer por quê.**

| Classe | Responsabilidade | Motivo de mudança |
|---|---|---|
| `EvolutionContextAssembler` | **1 + 4** — o que a evolução precisa saber | Um dado novo entra no contexto |
| `OptimizationMetrics` | **3** — quantas rodaram, quanto demoraram | Uma métrica nova é publicada |
| `StudyOptimizerService` | **2** — a sequência de passos | O fluxo da otimização muda |

O ponto que precisa ficar registrado é a **fusão de 1 com 4**, porque ela contraria a leitura literal
do achado.

As três calculadoras e o `FitnessEvaluator` **não tinham nenhum outro consumidor dentro do serviço**:
`baselineCalculator`, `importanceCalculator`, `cognitiveLoadCalculator` e `fitnessEvaluator` eram
lidos exclusivamente dentro de `prepareContext`, em uma linha cada. "Cálculo de domínio" não era uma
responsabilidade paralela à "montagem do contexto" — era **o conteúdo dela**. Separá-las produziria
uma classe cuja única função seria repassar quatro dependências adiante, o que é indireção sem
informação.

O critério aplicado é o de Parnas, não a contagem de substantivos: **uma classe por motivo de
mudança**. Quatro assuntos identificáveis não implicam quatro classes; implicam que se conte quantos
motivos independentes de mudança existem. São três.

### Decisão secundária: a duração é medida uma vez

O tempo de execução tem dois destinos — o `Timer` do Micrometer e o campo `executionTimeMillis` do
`OptimizationResult`, que volta ao cliente na resposta da API. `OptimizationMetrics.recordRun`
devolve `Timed<T>` (resultado + duração) em vez de expor `Counter` e `Timer` para o chamador
cronometrar por fora. São o mesmo tempo; dois relógios só poderiam divergir.

## Consequências

**A favor:**

- **Cada responsabilidade passou a ser verificável isoladamente.** As três classes foram de 76,8 %
  para **100 %** de instruções cobertas, e a suíte ganhou 9 testes que antes eram inviáveis de
  escrever — entre eles as garantias do `finally` da medição (o contador conta tentativas, não
  sucessos; uma falha lenta ainda entra na amostra de duração), que eram verdadeiras por acidente de
  escrita e não por contrato verificado.
- **O ponto de substituição dos benchmarks ficou explícito.** `WeightTradeoffMain` precisa trocar o
  `BaselineCalculator` para precificar uma mudança no piso de cobertura sem fazê-la. Antes, o
  calculador era entregue ao orquestrador, que apenas o repassava; agora vai para
  `EvolutionContextAssembler`, que é quem de fato o usa.
- **Dois achados menores caíram junto**, porque tocavam exatamente as linhas movidas: **E12**
  (`CognitiveLoadCalculator` obtido com `new` num serviço e por injeção em outro, no mesmo fluxo — a
  versão com `new` escapava de qualquer substituição em teste) e **E13** (nomes totalmente
  qualificados em declaração de campo).

**Contra, e aceito:**

- **Mais código, não menos.** As linhas de código passaram de 122 para 157: três construtores e três
  conjuntos de `import` custam mais do que as 20 linhas do método privado economizado. O ganho é a
  **maior classe cair de 122 para 70 linhas de código** e cada uma ter um motivo de mudança — não
  volume.
- **Mais um salto para quem lê o fluxo inteiro.** Entender uma otimização de ponta a ponta agora
  exige abrir três arquivos em vez de um. É o custo simétrico do benefício: quem só quer a sequência
  não precisa mais passar pela montagem do contexto, e quem só quer a montagem não precisa passar
  pelo laço de gerações.
- **`EvolutionContextAssembler` mora em `service`, não em `ga`.** Ela produz um `EvolutionContext`,
  que vive em `ga` — colocá-la lá seria mais coeso e **recriaria o ciclo `ga` ↔ `service`** desfeito
  na etapa 03b (ADR-0001), porque ela depende das calculadoras de `service.calculation`.
  `ModuleBoundaryTest` reprova a inversa.

## Alternativas consideradas

**Quatro classes, uma por assunto listado no achado.** Descartada pela razão acima: a classe de
"cálculo" não teria comportamento próprio, só repasse. Indireção que não separa motivos de mudança
não é separação de responsabilidades — é uma camada a mais para atravessar.

**Mover a montagem do contexto para `EvolutionContext` (uma fábrica estática no próprio record).**
Descartada: `EvolutionContext` está em `ga`, e a montagem depende de `service.calculation`. Seria o
ciclo `ga` ↔ `service` de volta, agora pela porta da frente. O ADR-0004 já havia decidido que o
contexto se monta por construtor passo a passo; quem sabe *com quais valores* preenchê-lo é a camada
de serviço.

**Deixar `Counter` e `Timer` expostos e cronometrar no serviço.** Descartada: mantém o serviço
ciente de `System.nanoTime` e de `TimeUnit`, e permite que a duração relatada ao cliente e a
registrada no monitoramento venham de medições diferentes.

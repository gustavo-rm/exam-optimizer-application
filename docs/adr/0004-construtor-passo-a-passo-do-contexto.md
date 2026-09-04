# ADR-0004 — `EvolutionContext` é montado por construtor passo a passo

- **Estado:** Aceito
- **Data:** 2026-09-02
- **Etapa:** 03c — correção do achado E1
- **Achado atendido:** E1 (dez parâmetros posicionais, nove locais de chamada)

## Contexto

`EvolutionContext.of()` recebia **dez parâmetros posicionais**, quatro deles objetos anuláveis, e era
chamado de **nove lugares** — produção, quatro classes de teste e três programas de benchmark.

A etapa 03 mediu o custo disso e não o inferiu. A análise de co-mudança sobre 58 commits apontou
`EvolutionContext` e `StudyOptimizerService` como **o par que mais muda junto no repositório** (5
commits), cruzando fronteira de módulo. A causa era mecânica: acrescentar um campo ao contexto
obrigava a editar os nove locais.

O modo de falha mais grave não era o esforço, era o silêncio. Quatro dos dez parâmetros eram objetos
anuláveis, e três eram `int` consecutivos — para o compilador, indistinguíveis entre si. Trocar dois
de lugar **compilava sem erro** e produzia um contexto errado. O sintoma aparecia nos testes:

```java
EvolutionContext.of(Map.of(), Map.of(), null, null, null, null, null, 180, 4, 20);
```

Cinco `null` consecutivos, e ninguém consegue dizer qual é qual sem abrir a assinatura.

## Decisão

**Substituir a fábrica posicional por um construtor passo a passo, e removê-la.**

```java
EvolutionContext.builder()
        .importanceScores(importanceScores)
        .minimumDaysPerSubject(minimumDaysPerSubject)
        .planningHorizonDays(180)
        .hoursPerStudyDay(4)
        .maxDailyCognitiveLoad(20)
        .build();
```

Cinco valores são **obrigatórios**, porque todo caminho de execução os fornece: `importanceScores`,
`minimumDaysPerSubject`, `planningHorizonDays`, `hoursPerStudyDay` e `maxDailyCognitiveLoad`. Omitir
qualquer um faz `build()` falhar nomeando **todos** os que faltam de uma vez.

Cinco são **opcionais** e valem `null` quando omitidos — exatamente o que os chamadores do caminho
macro passavam antes. **Não há mudança de comportamento.**

Os nomes dos métodos são idênticos aos componentes do record, para que quem lê o contexto e quem o
constrói usem o mesmo vocabulário.

## Alternativas consideradas

**Manter `of()` como atalho, além do construtor.** Descartada: o problema não é a ausência de uma
alternativa melhor, é a presença da pior. Deixar as duas mantém o modo de falha disponível, e o
próximo chamador vai copiar a linha que já existe.

**Agrupar parâmetros em objetos intermediários** — por exemplo um `PlanningWindow(horizonte, horas,
carga)`. Descartada por ora: resolveria o trecho de três `int` consecutivos, que é o mais perigoso,
mas cria tipos novos cujo único propósito é agrupar, e a decisão de quais campos andam juntos merece
ser tomada com mais evidência de uso do que existe hoje. O construtor passo a passo já elimina o modo
de falha; se um agrupamento se mostrar natural depois, ele pode ser introduzido por dentro sem tocar
nos chamadores.

**Usar Lombok `@Builder`.** Descartada: geraria um construtor com os doze componentes, incluindo os
dois **derivados** (`normalizedImportance` e `retentionWeights`), permitindo construir um contexto com
derivação inconsistente. A derivação na construção é o motivo de o contexto ter fábrica.

**Tornar o record uma classe para esconder o construtor canônico.** Descartada: um record público
exige construtor canônico público — não há como escondê-lo. Converter para classe custaria reescrever
doze acessores e todos os usos, para um ganho que um teste resolve (ver Consequências).

## Consequências

**A favor:**

- Cada valor é nomeado no ponto de uso. Trocar dois de lugar deixou de compilar, porque não há mais
  posição a trocar.
- Um campo novo **não quebra nenhum chamador existente** — é a co-mudança que a etapa 03 mediu,
  eliminada na origem.
- Os testes ficaram mais curtos e mais honestos: os campos que só existem no caminho tático são
  omitidos em vez de passados como `null`, o que também documenta quais o teste realmente usa.
- `build()` reporta **todos** os obrigatórios ausentes de uma vez, e não um por tentativa.

**Contra, e aceito:**

- Mais código na classe: o construtor passo a passo acrescenta ~110 linhas ao `EvolutionContext`.
  É o custo direto de trocar dez posições por dez nomes.
- Erro de campo faltando passou de **compilação** para **execução**. É a troca clássica desse padrão,
  e é aceitável aqui porque os cinco obrigatórios são fornecidos por todo caminho de execução e o
  `build()` roda uma vez por requisição, não dentro do laço de avaliação.
- O construtor canônico de **doze** argumentos continua público, por exigência da linguagem, e é
  pior que a fábrica removida. Não dá para escondê-lo; dá para verificar que ninguém o usa, e é o que
  `EvolutionContextBuilderTest.oConstrutorCanonicoNaoVazaParaForaDoBuilder` faz.

**Verificação de que o comportamento não mudou:** a suíte inclui dois testes que travam números do
algoritmo genético com sementes fixas — `GeneticAlgorithmVsBaselinesTest`, que compara o AG contra
seis baselines em oito instâncias, e `CorrelationAggregateTest.RealDataRegression`, que fixa a
trajetória de correlação −0,909 → −0,460 → −0,106. Ambos passam sem alteração.

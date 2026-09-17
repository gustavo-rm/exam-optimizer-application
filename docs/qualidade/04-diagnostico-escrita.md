# Etapa 04 — Diagnóstico de legibilidade e manutenibilidade

- **Data:** 2026-09-03
- **Commit analisado:** `3c396c1` (topo de `release/v4.0`)
- **Escopo:** todo o código-fonte Java do repositório — 122 arquivos em `src/main/java`,
  mais `src/test/java` e `benchmarks/java` onde indicado
- **Natureza desta etapa:** **diagnóstico**. Nenhuma linha de código foi alterada. Todos os números
  abaixo foram medidos com ferramenta executada agora, não estimados.

---

## Por que legibilidade e manutenção são o mesmo assunto

A tarefa pede as duas coisas juntas, e a razão aparece na prática deste repositório: **código difícil
de ler não *causa* um problema de manutenção — ele já *é* o problema de manutenção.** Ninguém
quebra um sistema porque o `if` estava mal indentado; quebra porque leu errado o que o código fazia.

Este diagnóstico foi ordenado por esse critério: um achado sobe se torna a próxima mudança mais lenta
ou mais arriscada, e desce se é apenas desagradável de olhar.

### Nota sobre o que já foi corrigido nas etapas anteriores

Três dos itens que esta etapa mediria como problemas graves **já não existem**, e é justo dizê-lo
antes dos números, para que a leitura não pareça mais favorável do que a realidade:

| Antes | Etapa que corrigiu | Estado hoje |
|---|---|---|
| `GlobalExceptionHandler` com 447 linhas e 16 tratadores | 03d | 3 classes, a maior com 145 linhas de código |
| `StudyOptimizerService` com 192 linhas e 4 responsabilidades | 03e | 3 classes, a maior com 70 linhas de código |
| `EvolutionContext.of()` com 10 parâmetros posicionais | 03c | construtor passo a passo |

As seções 1 e 2 abaixo, portanto, medem um repositório **que acabou de passar por três correções
estruturais**. Os resultados saudáveis ali não são mérito de escrita disciplinada desde o início.

### Ferramentas usadas

Nenhuma delas está configurada no repositório. Todas foram executadas para este diagnóstico, por
linha de comando, **sem alterar o `pom.xml`**:

| Ferramenta | Versão | Para quê |
|---|---|---|
| JaCoCo (relatório XML já gerado pelo build) | 0.8.13 | complexidade ciclomática e tamanho de método, medidos sobre o *bytecode* |
| Checkstyle (`google_checks` e uma configuração neutra escrita para esta etapa) | 3.6.0 | violações de estilo e de limiar |
| PMD | 7.7.0 | complexidade cognitiva, nomenclatura, imports |
| CPD (detector de cópia-e-cola, parte do PMD) | 7.7.0 | duplicação literal |
| `versions-maven-plugin` + metadados do Maven Central | 2.18.0 | atraso de versão |

---

## 1. Complexidade ciclomática

**Complexidade ciclomática** é a contagem de caminhos de execução distintos dentro de uma função.
Uma função sem nenhum `if` tem complexidade 1; cada `if`, `while`, `case` ou `&&` acrescenta pelo
menos um caminho. O número importa por dois motivos práticos: é aproximadamente **o número mínimo de
testes** necessários para exercitar a função inteira, e é uma medida direta de quanto um leitor
precisa segurar na cabeça de uma vez.

Medida sobre o *bytecode* pelo JaCoCo, em **355 métodos** de `src/main`:

| Faixa | Métodos |
|---|---:|
| ≥ 10 (limiar comum de alerta) | **1** |
| ≥ 8 | 6 |
| ≥ 5 | 23 |
| ≤ 4 | 332 |

### Os piores ofensores

| CC | Método | Local |
|---:|---|---|
| **10** | `DayBoundaryCrossover#crossover` | `ga/tactical/strategy/crossover/DayBoundaryCrossover.java:22` |
| **9** | `FatigueAndEnergyModel#calculateBurnoutRisk` | `service/calculation/fatigue/FatigueAndEnergyModel.java:36` |
| 8 | `DropoutRiskPredictor#calculateRiskScore` | `service/calculation/engagement/DropoutRiskPredictor.java:27` |
| 8 | `SpacedRepetitionRepairer#repair` | `ga/tactical/repair/SpacedRepetitionRepairer.java:30` |
| 8 | `MandatoryReviewConstraint#isValid` | `ga/fitness/constraint/MandatoryReviewConstraint.java:34` |
| 8 | `ClientIpResolver#resolve` | `api/controller/ClientIpResolver.java:83` |
| 7 | `StudyPlanFactory#createRandomPlan` | `ga/factory/StudyPlanFactory.java:53` |
| 7 | `EvolutionContext$Builder#build` | `ga/EvolutionContext.java:185` |
| 7 | `EvolutionContext#normalize` | `ga/EvolutionContext.java:243` |

**Este é um resultado bom, e precisa ser lido com honestidade.** Um único método acima de 10 em 355
não é um repositório com problema de complexidade. Vale, porém, registrar de onde vêm os quatro
primeiros:

- `DayBoundaryCrossover` e `SpacedRepetitionRepairer` são da **camada tática**, que o diagnóstico
  de estrutura (achado E6, decisão ADR-0002) manteve deliberadamente apesar de estar fora do
  caminho de execução da API. São os dois métodos mais complexos do repositório e **não rodam em
  produção**.
- `ClientIpResolver#resolve` (CC 8) foi escrito por mim na etapa 02b. A complexidade é a lógica de
  proxy confiável, e cada ramo tem teste em `ClientIpResolverTest` (13 testes em 4 grupos).
- `EvolutionContext$Builder#build` (CC 7) é a validação de obrigatórios do construtor passo a passo
  criado na etapa 03c — sete campos, sete verificações. É complexidade que substituiu um defeito
  pior (dez parâmetros posicionais).

### O único caso onde a complexidade se soma a outros problemas

PMD mede também a **complexidade cognitiva** — uma variante que pesa aninhamento, porque três `if`
encaixados são muito mais difíceis de ler do que três `if` em sequência, ainda que a complexidade
ciclomática seja a mesma. Um único método foi apontado:

```
SpacedRepetitionRepairer#repair — complexidade cognitiva 21 (limiar 15)
ga/tactical/repair/SpacedRepetitionRepairer.java:29
```

Também camada tática, também fora do caminho de execução.

O caso que **importa de fato** é `FatigueAndEnergyModel#calculateBurnoutRisk`, porque nele a
complexidade se combina com outros três problemas — 30 linhas executáveis (o método mais longo do
repositório), 26 números mágicos e um comentário que descreve comportamento errado. Está detalhado
na seção 10, achado **L5**.

---

## 2. Tamanho de arquivos e funções

### Arquivos

Medindo **linhas de código** (excluindo linhas em branco, comentários e Javadoc), em 122 arquivos:

| Métrica | Valor |
|---|---:|
| Total de linhas de código | 3.666 |
| Mediana por arquivo | **25** |
| Percentil 90 | 59 |
| Máximo | **163** |

Os maiores:

| Linhas totais | Linhas de código | Arquivo |
|---:|---:|---|
| 317 | 163 | `ga/EvolutionContext.java` |
| 261 | 145 | `api/exception/RequestErrorAdvice.java` |
| 174 | 99 | `service/StudyScheduleGenerator.java` |
| 161 | 74 | `ga/GeneticAlgorithmBuilder.java` |
| 152 | 84 | `ga/GeneticAlgorithm.java` |

**Nenhum arquivo é desproporcional.** A distância entre o maior (163) e a mediana (25) parece
grande, mas 163 linhas de código é um arquivo perfeitamente legível. Os dois maiores são também os
dois com mais Javadoc explicativo — `EvolutionContext` tem 154 linhas de comentário para 163 de
código, quase 1:1, e isso é intencional: é a classe que carrega o ADR-0004 no próprio corpo.

### Funções

| Métrica | Valor |
|---|---:|
| Métodos analisados | 355 |
| Com ≥ 40 linhas executáveis | **0** |
| Com ≥ 30 | 1 |
| Com ≥ 20 | 8 |

| Linhas | Método | Local |
|---:|---|---|
| **30** | `FatigueAndEnergyModel#calculateBurnoutRisk` | `service/calculation/fatigue/FatigueAndEnergyModel.java:36` |
| 22 | `EvolutionContextAssembler#assemble` | `service/EvolutionContextAssembler.java:72` |
| 22 | `DayBoundaryCrossover#crossover` | `ga/tactical/strategy/crossover/DayBoundaryCrossover.java:22` |
| 21 | `DropoutRiskPredictor#calculateRiskScore` | `service/calculation/engagement/DropoutRiskPredictor.java:27` |
| 21 | `SpacedRepetitionRepairer#repair` | `ga/tactical/repair/SpacedRepetitionRepairer.java:30` |
| 21 | `StudentProfileMapper#toDomain` | `api/mapper/StudentProfileMapper.java:39` |

Só duas classes ultrapassam limiares estruturais do PMD, e nenhuma das duas é preocupante:
`RequestErrorAdvice` (`api/exception/RequestErrorAdvice.java:67`) e `EvolutionContext$Builder`
(`ga/EvolutionContext.java:100`) são apontadas por **TooManyMethods** — a primeira tem 10 tratadores
de erro, um por tipo de exceção; a segunda tem 10 métodos `Builder`, um por campo. Em ambas, "muitos
métodos" é a forma correta do problema, não um sintoma.

Um limiar real foi ultrapassado, e vale registrar:

```
ParameterNumber — ga/GeneticAlgorithm.java:43 — mais de 7 parâmetros (encontrados 8)
```

É o construtor de `GeneticAlgorithm`, o mesmo padrão que o achado **E1** encontrou em
`EvolutionContext.of()` e que a etapa 03c corrigiu com um construtor passo a passo. Aqui há
atenuante: `GeneticAlgorithmBuilder` já existe e é o caminho normal de construção — o construtor de
8 parâmetros é o alvo do *builder*, não a interface pública.

**Conclusão da seção 2: item limpo.** Não há arquivo nem função desproporcional.

---

## 3. Duplicação de código

**Duplicação** aqui é código copiado, não conceito repetido. O risco é sempre o mesmo: quem corrige
um defeito numa cópia **não sabe que a outra existe**, e o defeito sobrevive.

Medida com CPD, o detector de cópia-e-cola do PMD, que compara sequências de *tokens* (unidades
léxicas — palavras-chave, identificadores, operadores) e por isso ignora diferenças de formatação e
de comentário.

### 3.1 O que a ferramenta encontra

Em `src/main` isolado:

| Limiar | Blocos duplicados | Linhas |
|---|---:|---:|
| 100 tokens (padrão) | **1** | 34 |
| 40 tokens | 7 | 84 |

Incluindo `benchmarks/java`, a 75 tokens: **5 blocos, 103 linhas**.

**O achado principal:**

```
34 linhas / 185 tokens
  ga/strategy/crossover/RepairingCrossover.java:60
  ga/strategy/crossover/WeightedAverageCrossover.java:61
```

É o método `repairChildGenes`, **idêntico linha a linha nos dois arquivos** — verificado por `diff`
após remoção dos comentários de fim de linha: 21 linhas, zero diferenças. O Javadoc de 8 linhas
também é o mesmo, palavra por palavra.

```java
private void repairChildGenes(Map<Subject, Integer> childGenes, int targetDaySum,
                              Map<Subject, Integer> minimumDaysPerSubject) {
    int currentDaySum = childGenes.values().stream().mapToInt(Integer::intValue).sum();
    int difference = targetDaySum - currentDaySum;
    List<Subject> subjects = new ArrayList<>(childGenes.keySet());
    while (difference != 0 && !subjects.isEmpty()) {
        ...
                // If we can't subtract from this subject, remove it from consideration
                // to prevent infinite loops.
                subjects.remove(randomSubject);
```

**Por que este é o pior tipo de duplicação:** o método garante a invariante central do plano — que a
soma de dias alocados seja exatamente o orçamento pedido, sem violar o piso mínimo de nenhuma
disciplina. Ele contém inclusive uma guarda contra laço infinito. Se alguém descobrir um caso em que
essa guarda falha e corrigir num arquivo, **o outro operador de cruzamento continua com o defeito**,
e o sintoma será um plano inválido produzido de forma intermitente — porque qual dos dois operadores
roda depende de um sorteio em `HybridCrossover`.

Os outros quatro blocos entre esses mesmos dois arquivos (19L, 14L, 6L, 3L) mostram que as duas
classes são amplamente paralelas: divergem no miolo do cruzamento e coincidem em todo o resto.

Os dois blocos restantes são de menor consequência: `WeightSensitivityMain` × `WeightTradeoffMain`
(26L e 14L — dois programas de benchmark com a mesma montagem de tabela) e
`HighLoadInstanceLibrary.java:114` × `:148` (10L, duas instâncias de teste construídas do mesmo
jeito).

Um terceiro merece nota por ser **semântico e não literal**:

```
3 linhas / 45 tokens
  ga/fitness/constraint/MandatoryReviewConstraint.java:48
  ga/tactical/repair/SpacedRepetitionRepairer.java:36
```

Ambos percorrem as disciplinas perguntando `retentionAlgorithm.isReviewMandatory(subject, ...)`. É a
mesma pergunta ao domínio, feita em dois lugares — o tipo de duplicação que vira inconsistência
quando a regra de "revisão obrigatória" mudar.

### 3.2 A duplicação que a ferramenta **não** encontra — e que é a mais grave

`BenchmarkHarness.buildContext` (`benchmarks/.../harness/BenchmarkHarness.java:162`) é uma **cópia
manual** de `EvolutionContextAssembler.assemble` (`service/EvolutionContextAssembler.java:72`).

O CPD não a detecta porque os nomes de variável diferem (`minimumDays` × `minimumDaysPerSubject`,
`importance` × `importanceScores`) e a origem dos dados difere (`instance.exam()` × `exam`). Mas as
**dez chamadas do construtor são as mesmas, na mesma ordem**, com as mesmas derivações:

| | Produção | Benchmark |
|---|---|---|
| Perfil de retenção | `new RetentionProfile(Map.of())` | idem |
| Perfil de engajamento | `EngagementProfile.baseline()` | idem |
| Horas por dia | `max(1, ceil(semanais/7))` | idem |
| Carga diária | `cognitiveLoadCalculator.calculate(...)` | `new CognitiveLoadCalculator().calculate(...)` |
| Data de início | `LocalDate.now()` | `instance.planStartDate()` — **diferença deliberada e documentada** |

O próprio código admite a duplicação e explica por quê:

```java
// Mirrors StudyOptimizerService.prepareContext field for field, including the planning data
// the corrected fitness terms read. The one deliberate difference is planStartDate: ...
```

**O problema é que a garantia depende de disciplina humana, e a disciplina já falhou uma vez.** O
comentário aponta para `StudyOptimizerService.prepareContext` — método que **não existe mais** desde
a etapa 03e. E a consequência é séria: o `ProductionGeneticAlgorithm` declara, no próprio Javadoc,
que existe justamente para não ser "uma transcrição que possa divergir da produção"
(*"Nothing here is a transcription that could drift from production"*). Um campo novo no
`EvolutionContext` entra em produção e **não entra no benchmark** — sem erro de compilação, porque o
construtor passo a passo do ADR-0004 permite omitir campos. Os números publicados passariam a medir
outro contexto.

Este é o mesmo padrão que o achado **E2** identificou para a composição da fitness, e que o ADR-0003
decidiu manter como "duplicação intencional, agora vigiada" — vigiada por um teste que compara as
duas composições. **Aqui não há teste equivalente.**

---

## 4. Convenções de nomenclatura

Esta seção precisa começar pelos **não-achados**, porque três dos itens que a tarefa pede
explicitamente vieram limpos, e dizer isso é mais útil do que inventar problemas.

### 4.1 Variáveis de uma letra: 10 ocorrências, todas legítimas

Busca em todo `src/main` por identificadores de um caractere:

```
DefaultPopulationGenerator.java:31   int i     TournamentSelection.java:66   int i
GeneticAlgorithm.java:79             int i     StudyPlanFactory.java:91      int i
RepairingCrossover.java:53           int i     ClientIpResolver.java:101     int i
HybridHeuristicScheduler.java:45     int i     InterleavedCriticalStrategy.java:35  int i
StudyOptimizerService.java:105       int i     StudyScheduleGenerator.java:169     long i
```

**Todas são contadores de laço `for`** — exatamente o "contexto óbvio" que a própria tarefa excetua.
Item limpo.

O PMD sinaliza 22 "nomes curtos", mas o exame dos 22 mostra que são `ex` (exceção, em 18 tratadores
de erro), `id`, `ip` e `ga`. Nenhum é obscuro; o limiar do PMD é de 3 caracteres, o que torna a
regra inútil para Java. **Zero achados reais.**

### 4.2 Abreviações obscuras: uma

```java
// domain/exam/Exam.java:95-99
int totalGKQuestions = getGeneralKnowledgeTotalQuestions();
if (totalGKQuestions <= 0) { ... }
double valuePerQuestion = generalKnowledgeTotalScore / totalGKQuestions;
```

`GK` = *general knowledge* (conhecimentos gerais). Aparece **três vezes, todas neste método**, e em
nenhum outro lugar do repositório — que usa `generalKnowledge` por extenso em toda parte, inclusive
na linha logo abaixo. É uma inconsistência isolada, não um padrão.

`MdcTaskDecorator` usa `MDC` (*Mapped Diagnostic Context*), mas isso é o nome oficial da API do
SLF4J — não é abreviação do projeto.

**Os nomes de classe são o ponto forte do repositório.** Dos 122 arquivos, nenhum usa abreviação
inventada: `FatigueAndSustainabilityPenalty`, `InterleavedCriticalStrategy`,
`SpacedRepetitionRepairer` são longos e dizem o que fazem.

### 4.3 A inconsistência real: o idioma, e ela piorou por minha causa

O achado **E11** da etapa 03 registrou idioma misturado sem convenção declarada, e mediu **6 de 118
classes** com Javadoc em português — todas tocadas pelas etapas 01b e 02b.

**Medição de hoje: 31 de 122 classes**, ou seja, **um quarto do código de produção**.

| Onde | Idioma |
|---|---|
| Nomes de classe, método, variável | inglês, sem exceção |
| Mensagens de exceção e de API | inglês, sem exceção |
| Javadoc de produção | **inglês em 91 classes, português em 31** |
| `@DisplayName` de teste | português, em 23 arquivos |
| Nomes de método de teste | inglês (`shouldX`) em 22 métodos; o restante em português sem acento |

As 31 classes em português são exatamente as tocadas nas etapas 01b, 02b, 03b, 03c, 03d e 03e desta
varredura — incluindo as três criadas ontem (`RequestErrorAdvice`, `EvolutionContextAssembler`,
`OptimizationMetrics`).

**A escalada de 6 para 31 é consequência direta deste trabalho, e é responsabilidade minha.** Cada
relatório foi escrito em português a pedido, e eu levei o português para o Javadoc junto. O
resultado é um arquivo típico de hoje: nome de classe em inglês, Javadoc em português, mensagem de
exceção em inglês e teste com `@DisplayName` em português. Quatro idiomas de decisão num arquivo só.

Isso não é ambiguidade sobre o que fazer — a convenção que o código já pratica de fato é clara e
consistente (**identificadores e tudo que sai pela API em inglês; explicação em português**). O que
falta é ela estar **escrita em algum lugar**, porque hoje quem chega não tem como saber se o
português é a regra nova ou uma anomalia das últimas semanas.

### 4.4 Nomenclatura de constantes: 11 ocorrências, e a regra é que está errada

O PMD sinaliza 11 vezes que `private static final Logger log` não obedece o padrão de constante
`[A-Z][A-Z_0-9]*`. As 11 são consistentes entre si, e `log` minúsculo é a convenção do SLF4J e do
Spring Boot. **É a regra do PMD que não se aplica**, não o código. Registrado para que ninguém
"corrija" 11 arquivos por causa de um relatório de ferramenta.

### 4.5 O que continua aberto do E9

A etapa 03 identificou **seis sufixos para o mesmo papel** (`Calculator`, `Model`, `Engine`,
`Predictor`, `Evaluator`, `Generator`) em classes sem estado que recebem dados de domínio e devolvem
um cálculo. Nada mudou desde então — e as classes criadas depois disso acrescentaram um sétimo
(`Assembler`) e um oitavo (`Advice`, herdado do Spring). Continua sendo o achado de nomenclatura de
maior impacto, e continua sendo o **E9**.

---

## 5. Comentários desatualizados ou enganosos

Duas verificações mecânicas vieram limpas, e uma leitura dirigida encontrou quatro casos reais.

### 5.1 O que está limpo (verificado, não presumido)

| Verificação | Resultado |
|---|---|
| `@param` documentando parâmetro que não existe | **0** em 122 arquivos |
| `{@link X}` para tipo inexistente | **3**, todos benignos (`Comparable` da JDK; duas classes aninhadas do próprio arquivo) |
| Referências `Arquivo.java:linha` **dentro de comentários de código** | **0** quebradas |

A disciplina de Javadoc deste repositório é acima da média. Não há um único `@param` órfão.

### 5.2 Os quatro comentários que mentem

**C1 — o comentário que descreve a curva errada.**
`service/calculation/fatigue/FatigueAndEnergyModel.java:29-32`:

> *"1.0 means perfectly sustainable. Lower values indicate higher risk, **dropping exponentially**
> if the burnout threshold is breached."*

O código não tem nada de exponencial. Tem **dois penhascos e uma rampa linear**:

```java
if (dailyFatigue > BURNOUT_THRESHOLD / 2) { return 0.1; }   // linha 74 — degrau
...
if (cumulativeFatigue > BURNOUT_THRESHOLD) { return 0.2; }  // linha 82 — degrau
return Math.max(0.5, 1.0 - (cumulativeFatigue / (BURNOUT_THRESHOLD * 1.5)));  // linha 87 — linear
```

É o comentário mais perigoso do repositório, porque descreve o **formato** de uma função de
penalidade — exatamente o que alguém precisa saber antes de calibrar pesos de fitness. Quem confiar
nele vai esperar uma transição suave e encontrar descontinuidades. Note ainda que a rampa linear tem
piso `0.5` enquanto o degrau devolve `0.2`: a função **salta de 0,5 para 0,2** ao cruzar o limiar.
Isso pode ser intencional, mas nenhum comentário diz que é.

**C2 — dois comentários apontam para um método que não existe mais.**

```java
// benchmarks/.../harness/BenchmarkHarness.java:151
* Builds the evolution context exactly as {@code StudyOptimizerService.prepareContext} does,

// benchmarks/.../harness/BenchmarkHarness.java:168
// Mirrors StudyOptimizerService.prepareContext field for field, ...
```

`prepareContext` deixou de existir na etapa 03e — virou `EvolutionContextAssembler.assemble`. **Fui
eu quem criou essa defasagem, ontem**, ao mover o método sem seguir suas referências para dentro de
`benchmarks/`. É o mesmo achado da seção 3.2, visto pelo lado do comentário.

**C3 — comentário afirma um comportamento que a etapa 03d mudou.**

```java
// benchmarks/.../instance/InstanceLibrary.java:253-254
* Raises the day budget when the minimum-days floor computed by {@link BaselineCalculator}
* would exceed it, which {@code StudyPlanFactory} rejects with an IllegalArgumentException.
```

Esse caso exato — piso de dias mínimos maior que o orçamento — passou a lançar `DomainException` na
etapa 03d (ADR-0005), justamente porque não era erro de argumento. O comentário nomeia a exceção
errada.

**C4 — descrição de estrutura superada.**

```java
// benchmarks/.../robustness/WeightTradeoffMain.java:388
* {@code StudyOptimizerService} builds its own context and computes the floor itself
```

Não mais: quem monta o contexto é o `EvolutionContextAssembler`. Mesma origem que C2.

**Os quatro estão em `benchmarks/`, exceto C1.** Isso não é coincidência: `benchmarks/java` é
compilado no escopo de teste mas não tem teste próprio, então nada ali é exercitado por uma suíte
que falharia ao ler um comentário errado. É a região do repositório onde comentários apodrecem sem
resistência.

### 5.3 Referências de linha quebradas nos documentos

Fora do código, **13 referências `Arquivo.java:linha` apontam para fora do arquivo** (contra 180 que
continuam válidas):

| Documento | Referências quebradas |
|---|---|
| `docs/revisao-ag/00-diagnostico.md` | 5 (4 para `StudyOptimizerService.java`, que encolheu de 192 para 122 linhas; 1 para `FatigueAndSustainabilityPenalty.java`) |
| `docs/revisao-ag/01-auditoria-fitness.md` | 4 (incluindo `GlobalExceptionHandler.java:91-104`, arquivo apagado na 03d) |
| `docs/qualidade/02-diagnostico-seguranca.md` | 3 (todas para `GlobalExceptionHandler.java`) |
| `docs/qualidade/03d-correcao-contrato-de-erro.md` | 1 (cita a referência quebrada acima, de propósito) |

**Estes casos são diferentes dos comentários da seção 5.2, e a diferença importa.** Todos os quatro
documentos são **relatórios datados** — retratos do que era verdade num momento. Um relatório de
diagnóstico não deve ser reescrito quando o código muda, pela mesma razão que um ADR aceito não é
editado: o valor está no registro histórico. O problema não é que estejam desatualizados; é que
**nada avisa o leitor de que a coordenada não vale mais**.

Há uma exceção que **não** é retrato histórico e sim rastreamento vivo:
`docs/revisao-ag/04-robustez.md` mantém uma tabela de 7 pendências abertas (P1–P7), e **P6 já foi
fechada** pela etapa 03d. A tabela ainda diz, na linha 248, *"**Mantido**, testado. Ver P6 sobre 400
vs 422"*, e na linha 339 lista P6 como pendência aberta. Um leitor que consulte essa tabela hoje vai
trabalhar em cima de uma informação falsa.

---

## 6. Débito de TODO / FIXME

**Quantos existem: zero.**

Busca por `TODO`, `FIXME`, `XXX`, `HACK`, `WIP` e `TBD` em `src/`, `benchmarks/` e em todo o
repositório fora de `docs/`, incluindo `.java`, `.properties`, `.xml` e `.yml`:

```
0 ocorrências
```

Como consequência, as outras duas perguntas da tarefa — há quanto tempo existem, quantos ainda são
relevantes — não têm sobre o que incidir.

### Mas zero não é a boa notícia que parece

O débito existe; ele só **não mora no código**. Está em prosa, em documentos:

| Fonte | Achados registrados |
|---|---:|
| `docs/revisao-ag/` (P1–P10, R1–R28) | 38 |
| `docs/qualidade/` (S1–S16, E1–E15) | 28 |
| **Total de identificadores de achado nos documentos** | **66** |

Destes 66, apenas **14 são citados de algum comentário de código**, em 16 arquivos — e todas essas
citações foram escritas por mim, nas etapas 01b a 03e.

O efeito prático é concreto e verificável. `EvolutionContextAssembler.assemble` faz, na linha 85:

```java
LocalDate planStartDate = LocalDate.now();
```

A pendência **P7** de `docs/revisao-ag/04-robustez.md` — aberta desde 30/08/2026 — diz exatamente:
*"`LocalDate.now()` dentro da lógica. Injetar `Clock` altera construtores e wiring do Spring."*
Nada no arquivo indica isso. Quem for mexer nessa linha não tem como saber que a questão já foi
levantada, discutida e adiada — e o custo já apareceu: o teste que escrevi ontem
(`EvolutionContextAssemblerTest`) precisou calcular `LocalDate.now()` por conta própria para
comparar, o que o torna sensível ao relógio.

**O achado, portanto, não é "faltam TODOs".** É que o repositório mantém 66 itens de débito
conhecido numa camada de documentação que **não é lida no momento em que a decisão é tomada** — que
é dentro do editor, olhando a linha que se vai mudar.

---

## 7. Lint e formatação

### 7.1 Ferramenta configurada: nenhuma

Confirmado por busca no `pom.xml`, na raiz e em `.github/`: não há Checkstyle, Spotless, PMD,
SpotBugs, Error Prone, SonarQube, `.editorconfig` nem `maven-enforcer-plugin`. É o mesmo achado da
etapa 00 (§2.2), **inalterado**. O `README.md` continua registrando a ausência como recomendação
futura: *"Linting and Formatting: (Assuming IDE defaults, further configuration via Checkstyle or
Spotless is recommended)"*.

O único portão automático existente é o **gate de cobertura JaCoCo** criado na etapa 01b, que roda
em `mvn verify` e no CI. Ele verifica cobertura de teste, não estilo.

### 7.2 Rodando uma ferramenta hoje: os números

**Checkstyle 3.6.0 com `google_checks.xml`, sobre os 123 arquivos de `src/main`:**

| Regra | Violações |
|---|---:|
| `Indentation` | **1.915** |
| `LineLength` | 292 |
| `CustomImportOrder` | 166 |
| `JavadocParagraph` | 153 |
| `EmptyLineSeparator` | 46 |
| `MissingJavadocMethod` | 18 |
| `RequireEmptyLineBeforeBlockTagGroup` | 15 |
| `AvoidStarImport` | 9 |
| demais (11 regras) | 37 |
| **Total** | **2.651** |

**Este total de 2.651 é enganoso, e apresentá-lo sem ressalva seria desonesto.** Dele, **1.915 —
72%** — são da regra `Indentation`, porque o `google_checks` exige recuo de **2 espaços** e o
projeto usa **4**. Verifiquei a consistência interna do projeto separadamente:

```
recuo de primeiro nível dentro de classe:  627 ocorrências de 4 espaços,
                                           265 de 8, 49 de 12, 16 de 16, 8 de 20, 6 de 24
arquivos com caractere de tabulação:       0 de 122
```

Ou seja: o projeto é **100% consistente em 4 espaços, sem uma única tabulação**. As 1.915 violações
não medem desordem do projeto; medem a distância entre a escolha do projeto e a de um guia de estilo
que ninguém adotou. O mesmo vale, em menor grau, para `LineLength` (limite 100 do Google) e
`CustomImportOrder`.

### 7.3 A medição honesta: uma configuração neutra de estilo

Para separar "diferente do Google" de "problema sob qualquer critério", escrevi uma configuração
Checkstyle contendo apenas regras que apontam **defeito ou limiar**, e nenhuma que expresse
preferência de formatação. Resultado sobre `src/main`:

| Regra | Violações | Natureza |
|---|---:|---|
| `MagicNumber` | **93** | número sem nome embutido na lógica |
| `LineLength` (limite 120, não 100) | 80 | linhas realmente longas |
| `NewlineAtEndOfFile` | 14 | arquivo sem quebra de linha final |
| `AvoidStarImport` | 9 | `import java.util.*` |
| `NeedBraces` | 8 | `if` de uma linha sem chaves |
| `UnusedImports` | 5 | import morto |
| `FinalClass` | 1 | `util/RandomProvider.java:12` |
| `HideUtilityClassConstructor` | 1 | `DynamicStudyPlannerApplication.java:6` |
| `ParameterNumber` | 1 | `ga/GeneticAlgorithm.java:43` (8 parâmetros) |
| `ClassFanOutComplexity` | 1 | `api/exception/RequestErrorAdvice.java:65` (21, limite 20) |
| **Total** | **213** | |

Para referência de escala do `LineLength`: das 7.101 linhas de `src/main`, a mediana tem **44**
caracteres, o percentil 95 tem 101 e a máxima tem **217**. 377 linhas passam de 100 caracteres e 81
passam de 120. A concentração é reveladora: **13 das 80 estão em
`api/controller/OptimizerController.java`**, todas nas anotações `@ApiResponse` — documentação
OpenAPI inline, não lógica.

Os `import` mortos, nominalmente (5, também confirmados pelo PMD):

```
api/controller/RateLimitingFilter.java:8   io.github.bucket4j.Refill
api/mapper/StudyPlanMapper.java:5          ...domain.exam.Subject
ga/Individual.java:4                       ...domain.exam.Subject
ga/Individual.java:10                      java.util.Map
ga/tactical/strategy/crossover/DayBoundaryCrossover.java:8   java.time.LocalDateTime
```

O PMD acrescenta ainda **10 pares de parênteses redundantes** (concentrados em
`CognitiveLoadCalculator` e `FatigueAndEnergyModel`) e **1 parâmetro de método não usado**
(`api/controller/RateLimitingFilter.java:86`, parâmetro `key` de `createNewBucket`).

### 7.4 Onde os números mágicos se concentram

**93 números mágicos em 22 arquivos de 122.** A concentração diz onde o problema realmente está:

| Arquivo | Números mágicos |
|---|---:|
| `service/calculation/fatigue/FatigueAndEnergyModel.java` | **26** |
| `service/calculation/CognitiveLoadCalculator.java` | 16 |
| `service/calculation/engagement/DropoutRiskPredictor.java` | 10 |
| `service/calculation/retention/HybridRetentionEngine.java` | 9 |
| outros 18 arquivos | 32 |

**Os quatro primeiros somam 61 dos 93 — e são os quatro modelos de cálculo do domínio.** São
exatamente as classes onde um número *é* a regra de negócio: `1.5` é o multiplicador de fadiga quando
a carga excede a energia; `0.1` é a penalidade por esgotamento agudo. Em `FatigueAndEnergyModel`
existem **duas** constantes nomeadas (`BURNOUT_THRESHOLD`, `FATIGUE_CARRYOVER_RATE`) e 26 números
sem nome — inclusive na mesma expressão:

```java
if (dailyFatigue > BURNOUT_THRESHOLD / 2) {   // constante nomeada dividida por um 2 anônimo
     return 0.1;                               // "Massive penalty for acute daily burnout"
}
```

Aqui a legibilidade vira manutenibilidade de forma direta: quem quiser calibrar o modelo tem que
localizar 26 números espalhados pelas 132 linhas da classe e adivinhar quais são parâmetros ajustáveis e quais são
detalhe de implementação.

**Nenhuma dessas 213 violações é vista por ninguém hoje**, porque nenhuma ferramenta roda.

---

## 8. Documentação

### 8.1 README: o que confere e o que não confere

O repositório tem **um único serviço** e, portanto, um `README.md` (249 linhas). Cada afirmação
operacional foi verificada:

| Afirmação do README | Verificação | Situação |
|---|---|---|
| Java 21 | `pom.xml` declara `java.version` 21 | ✅ |
| `POST /api/v1/optimizer/generate` | única anotação de mapeamento do projeto | ✅ |
| Porta 8080 | padrão do Spring Boot, não sobrescrito | ✅ |
| `java -jar target/DynamicStudyPlanner-2.0.1.jar` | `pom.xml` versão 2.0.1 | ✅ |
| Sem banco de dados | nenhuma dependência de persistência | ✅ |
| Rate limit 5/min via Bucket4j → 429 | confere com `application.properties` e testado | ✅ |
| `permitAll()` em `/api/v1/**` | confere com `SecurityConfig` | ✅ |
| Timeout de 30 s | confere com `.orTimeout(30, TimeUnit.SECONDS)` | ✅ |
| **`cd DynamicStudyPlanner`** | o repositório clona como **`exam-optimizer-application`** | ❌ |
| **`server.forward-headers-strategy` padrão `framework`** | o valor real é **`none`** | ❌ **grave** |
| Tabela de variáveis de ambiente | **faltam 3 propriedades** criadas na etapa 02b | ❌ |
| `./mvnw clean install` | não verificável neste ambiente (ver abaixo) | ⚠️ |

**O erro grave é o `server.forward-headers-strategy`.** O README documenta `framework` como padrão.
O valor real é `none`, e o `application.properties` dedica **12 linhas de comentário** a explicar por
que não pode ser `framework` sem um proxy confiável configurado junto:

> *"Com `framework`, o Spring registra o `ForwardedHeaderFilter` […] Medido na etapa 02b — com
> `framework`, variar o cabeçalho a cada requisição burla o limite de taxa mesmo com
> `api.trusted-proxies` vazio."*

Isto é, o README instrui o leitor a usar exatamente a configuração que **reabre o achado de
segurança S12**. Não é uma imprecisão de documentação; é uma armadilha.

As três propriedades ausentes da tabela — todas criadas na etapa 02b e todas com efeito de segurança:

```
api.trusted-proxies                    (vazio)
api.security.require-https             (false)
api.security.hsts-max-age-seconds      (31536000)
```

Elas formam, junto com `server.forward-headers-strategy`, **uma única premissa de implantação que
deve ser ligada em conjunto** — como o próprio `application.properties` avisa. O README documenta uma
das quatro, e com o valor errado.

Sobre o `./mvnw`: neste ambiente ele falha ao baixar sua distribuição
(`wget: Failed to fetch .../apache-maven-3.9.11-bin.zip`), e o diretório de cache
`~/.m2/wrapper/dists/apache-maven-3.9.11/` está vazio. **Isso é limitação de rede do ambiente de
análise, não defeito do repositório** — o `maven-wrapper.properties` está bem formado e usa
`distributionType=only-script`, que baixa sob demanda. Registro apenas que **as instruções do README
não puderam ser executadas de ponta a ponta aqui**, e que todo comando documentado depende de acesso
a `repo.maven.apache.org` no primeiro uso, sem alternativa documentada.

Duas omissões menores, ambas de coisas criadas nesta varredura: o README diz *"To run the tests:
`./mvnw test`"* sem mencionar que o **gate de cobertura** só roda em `mvn verify`, e não menciona o
**workflow de CI** (`.github/workflows/ci.yml`) criado na etapa 01b.

### 8.2 Documentação de API: sincronizada no que declara, incompleta no que omite

A especificação OpenAPI é gerada por anotação e há um **teste de retrato** (`OpenApiContractTest`,
comparando com `src/test/resources/contract/openapi-snapshot.json`) que falha se o contrato mudar sem
querer. O caminho e o corpo estão corretos.

O problema é de **cobertura, não de divergência**:

| Status | No OpenAPI | A API devolve? | Prova |
|---|---|---|---|
| 200, 400, 408, 500 | sim | sim | — |
| **422** | sim | sim | `BusinessRuleStatusTest` (desde a 03d) |
| **404** | **não** | **sim** | `ClientErrorStatusTest` — *"rota inexistente devolve 404"* |
| **405** | **não** | **sim** | `ClientErrorStatusTest` — *"GET, PUT e DELETE devolvem 405 com cabeçalho Allow"* |
| **415** | **não** | **sim** | `ClientErrorStatusTest` — *"tipo de mídia não suportado devolve 415"* |
| **429** | **não** | **sim** | `ApiErrorContractTest` — *"429 … em RFC 7807"*; e o README o documenta |

**Quatro códigos de status que a API comprovadamente devolve não estão na sua documentação de API.**
Os três primeiros passaram a existir na etapa 02b (achado S8) e a especificação não foi atualizada
junto; o 429 existe desde antes. Um cliente que gere seu código a partir do OpenAPI não terá
tratamento para nenhum dos quatro.

Vale notar a inversão que a 03d desfez: até ontem o OpenAPI declarava um `422` que **nenhuma
requisição conseguia produzir**. Hoje o 422 é real. O desequilíbrio inverteu de lado.

### 8.3 Uma nota sobre a documentação desta própria varredura

`docs/qualidade/00-inventario.md` §2.4 lista `springdoc-openapi` na versão **2.5.0**. A versão real é
**2.8.17** — eu mesmo a subi na etapa 01b, ao descobrir que a 2.5.0 era binariamente incompatível com
o Spring 6.2 e quebrava `/v3/api-docs` com HTTP 500. O inventário é um documento datado e não deve
ser reescrito, mas a tabela de dependências dele é o tipo de conteúdo que as pessoas consultam como
se fosse atual.

---

## 9. Dependências desatualizadas por número de versões de atraso

Esta seção trata de **dificuldade de manutenção futura**, não de vulnerabilidade — as CVEs são
assunto da etapa 02, que registrou a ausência de SCA como ponto cego do ambiente.

O critério aqui é o **número de *releases* estáveis publicadas** desde a versão em uso, contado nos
metadados do Maven Central, ignorando pré-lançamentos (`-M`, `-RC`, `-alpha`, `-beta`). Esse número
importa porque a dificuldade de uma atualização cresce de forma mais que linear com a distância: cada
*release* pulada é um conjunto de notas de migração a ler.

| Artefato | Em uso | Última estável | **Releases atrás** |
|---|---|---|---:|
| `org.springframework.boot:spring-boot-starter-parent` | 3.5.5 | 4.1.1 | **22** |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | 3.1.0 | **6** |
| `net.logstash.logback:logstash-logback-encoder` | 7.4 | 9.0 | **3** |
| `org.jacoco:jacoco-maven-plugin` | 0.8.13 | 0.8.15 | 2 |
| `org.codehaus.mojo:build-helper-maven-plugin` | 3.6.0 | 3.6.1 | 1 |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 | 3.1.1 | **0** |
| `com.bucket4j:bucket4j-core` | 8.10.1 | 8.10.1 | **0** |

### O que os 22 releases do Spring Boot realmente contêm

```
3.5.6  3.5.7  3.5.8  3.5.9  3.5.10  3.5.11  3.5.12  3.5.13  3.5.14  3.5.15  3.5.16
4.0.0  4.0.1  4.0.2  4.0.3  4.0.4  4.0.5  4.0.6  4.0.7  4.0.8  4.1.0  4.1.1
```

A divisão muda completamente a leitura:

- **11 são correções de patch dentro da linha 3.5** (3.5.6 → 3.5.16). Atualizar até 3.5.16 é, por
  definição de versionamento semântico, compatível — 11 releases de correção que o projeto não
  recebeu, ao custo de trocar um número.
- **11 cruzam para a linha 4.x**, que é uma versão maior. Isso arrasta Spring Framework 7,
  provavelmente Jakarta EE 11, e as mudanças incompatíveis que uma linha maior traz.

**O caminho barato existe e não foi tomado.** Ficar em 3.5.16 custa uma linha e nenhuma migração; é a
diferença entre "atrasado 11 correções" e "atrasado uma linha maior inteira".

### O efeito multiplicador do parent

Doze das dezesseis dependências declaradas **não têm versão própria** — herdam do
`spring-boot-starter-parent`. Isso inclui Jackson, Logback, Micrometer, Lombok, Caffeine e todos os
*starters*. Na prática:

- **A favor:** existe **um único ponto de atualização** para a maior parte da árvore. Subir o parent
  atualiza dezenas de bibliotecas de uma vez.
- **Contra:** as três dependências com versão fixa (`logstash-logback-encoder` 7.4,
  `springdoc` 2.8.17, `jakarta.validation-api` 3.1.1) **não se movem com o parent**, e é justamente
  aí que mora o risco de incompatibilidade — foi exatamente o que aconteceu com o springdoc 2.5.0,
  que quebrou `/v3/api-docs` com HTTP 500 por incompatibilidade binária com o Spring 6.2, descoberto
  só na etapa 01b.

`springdoc` é o caso a vigiar: **6 releases atrás, e a linha 3.x existe porque o Spring Boot 4
precisa dela.** Ele é o acoplamento que decide quando a migração para o Boot 4 é possível.

### O que agrava tudo isso

**Não há nenhum mecanismo que avise sobre atualizações.** Sem Dependabot (não existe
`.github/dependabot.yml`), sem Renovate, sem `versions-maven-plugin` declarado no `pom.xml`. Os
números desta seção só existem porque rodei a ferramenta manualmente. Amanhã ninguém saberá que são
23 releases em vez de 22.

---

## 10. Lista priorizada por impacto em velocidade de manutenção futura

Ordenada por **quanto atrasa ou põe em risco a próxima mudança**. Um item sobe se torna trabalho
futuro mais lento ou mais perigoso; desce se é incômodo estético.

### Nível 1 — cobra pedágio em toda mudança, ou esconde defeito

| # | Achado | Localização exata | Custo concreto |
|---|---|---|---|
| **L1** | **Nenhuma ferramenta de lint configurada.** 213 violações reais (config neutra) e 2.651 pelo `google_checks` — nenhuma vista por ninguém | `pom.xml` (ausência), `.github/workflows/ci.yml` (só cobertura) | Todo achado desta seção é achado **novo** a cada etapa, porque nada o registra. O número só cresce, e cresce sem sinal |
| **L2** | **`repairChildGenes` duplicado byte a byte** — 34 linhas / 185 tokens, incluindo a guarda contra laço infinito | `ga/strategy/crossover/RepairingCrossover.java:71-91` × `WeightedAverageCrossover.java:72-92` | Corrigir um defeito na invariante do orçamento de dias numa cópia deixa a outra intacta. Qual roda depende de sorteio em `HybridCrossover` — o sintoma será intermitente |
| **L3** | **`BenchmarkHarness.buildContext` é cópia manual de `EvolutionContextAssembler.assemble`**, e o CPD não a enxerga | `benchmarks/.../harness/BenchmarkHarness.java:162` × `service/EvolutionContextAssembler.java:72` | Um campo novo no contexto entra em produção e **não** no benchmark, sem erro de compilação (ADR-0004 permite omitir). Os números publicados passariam a medir outro contexto. O E2 tem guarda por teste; este não tem |
| **L4** | **README documenta `server.forward-headers-strategy=framework`**, valor que reabre o achado de segurança S12 | `README.md:109` × `application.properties:20` | Quem seguir o README reabre um furo já corrigido. As outras 3 propriedades da mesma premissa de implantação nem aparecem na tabela |

### Nível 2 — atrapalha quem lê, e cobra na hora de mudar

| # | Achado | Localização exata | Custo concreto |
|---|---|---|---|
| **L5** | **`FatigueAndEnergyModel` acumula quatro problemas no mesmo método:** CC 9, 30 linhas executáveis (o mais longo do repositório), 26 números mágicos, e Javadoc que promete queda "exponencial" quando o código tem dois degraus e uma rampa linear | `service/calculation/fatigue/FatigueAndEnergyModel.java:29-88` (Javadoc 29-33, método 36-88) | É um modelo de calibração: quem for ajustar precisa entender o formato da curva, e o comentário diz o formato errado. A função salta de 0,5 para 0,2 no limiar, sem que nada explique se é intencional |
| **L6** | **OpenAPI omite 4 status que a API devolve:** 404, 405, 415, 429 | `api/controller/OptimizerController.java:55-71`; provas em `ClientErrorStatusTest` e `ApiErrorContractTest` | Cliente gerado a partir da especificação não trata nenhum dos quatro. Três surgiram na 02b e a documentação não acompanhou |
| **L7** | **Idioma misturado sem convenção escrita, e a proporção quintuplicou:** Javadoc em português em **31 de 122** classes (eram 6 na etapa 03) | as 31 estão listadas na §4.3 | Um arquivo típico tem nome em inglês, Javadoc em português, exceção em inglês e `@DisplayName` em português. Quem chega não sabe qual é a regra. **Causado por este trabalho** |
| **L8** | **93 números mágicos, 61 deles em 4 arquivos** — todos modelos de cálculo, onde o número *é* a regra | `FatigueAndEnergyModel` (26), `CognitiveLoadCalculator` (16), `DropoutRiskPredictor` (10), `HybridRetentionEngine` (9) | Calibrar o modelo exige localizar 26 números nas 132 linhas da classe e adivinhar quais são parâmetros e quais são detalhe |
| **L9** | **66 itens de débito documentado, 14 citados no código.** Zero marcadores `TODO`/`FIXME` | ex.: P7 (`LocalDate.now()` na lógica) vs. `service/EvolutionContextAssembler.java:85` | A decisão sobre uma linha é tomada olhando a linha, não o documento. P7 já cobrou: `EvolutionContextAssemblerTest` depende do relógio |

### Nível 3 — ruído mensurável, barato de resolver

| # | Achado | Localização exata | Número real |
|---|---|---|---|
| **L10** | Comentários que descrevem estrutura ou exceção que mudaram | `BenchmarkHarness.java:151,168`; `WeightTradeoffMain.java:388`; `InstanceLibrary.java:254` | 4 (3 causados pela etapa 03e, 1 pela 03d) |
| **L11** | Tabela de pendências viva com item já fechado | `docs/revisao-ag/04-robustez.md:248,339` (P6, fechada na 03d) | 1 de 7 |
| **L12** | Referências `Arquivo.java:linha` apontando para fora do arquivo, em documentos | 5 em `00-diagnostico.md`, 4 em `01-auditoria-fitness.md`, 3+1 em `docs/qualidade/` | 13 quebradas / 180 válidas |
| **L13** | `import` mortos e `import` com `*` | 5 mortos (listados na §7.3), 9 com `.*` | 14 |
| **L14** | Arquivos sem quebra de linha final; `if` sem chaves; parênteses redundantes; parâmetro não usado | 14 + 8 + 10 + 1 | 33 |
| **L15** | `totalGKQuestions` — única abreviação obscura, num arquivo que escreve `generalKnowledge` por extenso na linha seguinte | `domain/exam/Exam.java:95-99` | 3 ocorrências |

### Nível 4 — atualização de dependências

| # | Achado | Número real |
|---|---|---|
| **L16** | **Spring Boot 22 releases atrás** — mas **11 são patches dentro da 3.5** (3.5.6→3.5.16), compatíveis por definição. Só os outros 11 cruzam para a linha 4.x | 22 (11 baratos + 11 caros) |
| **L17** | `springdoc` 6 releases atrás; é o acoplamento que decide quando o Boot 4 é viável, e já quebrou a API uma vez (2.5.0 × Spring 6.2, etapa 01b) | 6 |
| **L18** | `logstash-logback-encoder` 3 releases atrás, cruzando duas linhas maiores (7.4 → 9.0) | 3 |
| **L19** | **Nenhum mecanismo avisa sobre atualizações** — sem Dependabot, Renovate ou `versions-maven-plugin` declarado | — |

---

## O que a escrita deste repositório faz certo

Uma lista de problemas sem os acertos dá uma impressão falsa. Estes vieram limpos na medição:

- **Complexidade sob controle.** 1 método acima de 10 em 355; 332 métodos com 4 ou menos caminhos.
- **Tamanho sob controle.** Mediana de 25 linhas de código por arquivo, nenhum método acima de 30
  linhas executáveis, nenhum arquivo desproporcional.
- **Formatação internamente consistente.** 4 espaços em 100% do código, zero tabulações. As 1.915
  "violações" de indentação medem discordância com o Google, não desordem própria.
- **Nomes de classe excelentes.** 122 arquivos, nenhuma abreviação inventada.
- **Variáveis de uma letra: só contadores de laço.** 10 ocorrências, todas `i` em `for`.
- **Javadoc disciplinado.** Zero `@param` órfãos, zero `{@link}` quebrados de verdade.
- **Duplicação baixa.** 1 bloco acima do limiar padrão do CPD em 3.666 linhas de código.
- **Contrato de API travado por teste de retrato**, o que impede divergência silenciosa do que está
  documentado (embora não force a documentar o que falta).

---

## Resumo em uma tela

| Item pedido | Resultado medido | Veredito |
|---|---|---|
| **1. Complexidade ciclomática** | 1 método ≥ 10, 6 ≥ 8, em 355 | ✅ saudável |
| **2. Tamanho de função/classe** | mediana 25 linhas/arquivo, máx. 163; nenhum método ≥ 40 linhas | ✅ saudável |
| **3. Duplicação** | 1 bloco de 34 linhas (CPD padrão); +1 cópia manual que o CPD não vê | ⚠️ 2 achados, 1 grave |
| **4. Nomenclatura** | 0 variáveis de uma letra fora de laço; 1 abreviação obscura; **idioma misturado em 31 de 122** | ⚠️ o idioma é o problema |
| **5. Comentários enganosos** | 0 `@param` órfãos; **4 comentários mentem**; 13 referências de linha quebradas em docs | ⚠️ 4 achados |
| **6. TODO/FIXME** | **0 marcadores** — mas 66 itens de débito em prosa, 14 citados no código | ⚠️ débito invisível de dentro do editor |
| **7. Lint/formatação** | **nenhuma ferramenta configurada**; 213 violações reais / 2.651 pelo `google_checks` | ❌ nada roda |
| **8. Documentação** | README com 3 erros, 1 deles reabre o S12; OpenAPI omite 4 status reais | ❌ 2 achados sérios |
| **9. Dependências** | Boot **22 atrás** (11 patches baratos + 11 caros); springdoc 6; nenhum aviso automático | ⚠️ caminho barato não tomado |
| **10. Lista priorizada** | 19 achados em 4 níveis | — |

**Os quatro do nível 1 (L1–L4)** são os que mudam a velocidade de manutenção: um deles esconde
defeito num operador genético (L2), outro pode invalidar silenciosamente os números publicados de
benchmark (L3), outro instrui o leitor a reabrir um furo de segurança (L4), e o quarto garante que
nenhum dos outros seja detectado automaticamente (L1).

**Nota de responsabilidade:** três achados desta lista — **L7** (idioma), **L10** (comentários
apontando para `prepareContext`) e parte de **L12** (referências quebradas) — foram introduzidos
pelas etapas anteriores desta mesma varredura, por mim. Registrá-los aqui é parte do diagnóstico, não
uma concessão.

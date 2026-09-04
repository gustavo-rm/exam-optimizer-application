# Etapa 03 — Diagnóstico de estrutura e arquitetura

**Escopo:** limites de módulo, direção de dependência, acoplamento, duplicação, ciclos, código morto
e consistência interna. Escrita/manutenibilidade, performance e escalonamento vêm depois.

**Esta etapa não altera código.** Cada achado traz arquivo, linha e trecho — não afirmação genérica.

| | |
|---|---|
| **Data** | 2026-09-01 |
| **Commit** | `5456602` |
| **Branch** | `release/v4.0` |
| **Base** | [`00`](./00-inventario.md), [`01`](./01-diagnostico-testes.md), [`01b`](./01b-correcao-testes.md), [`02`](./02-diagnostico-seguranca.md), [`02b`](./02b-correcao-seguranca.md) |

### Vocabulário usado neste documento

- **Bounded context** (contexto delimitado) — fronteira dentro da qual um modelo de domínio tem
  significado único e consistente, no vocabulário de *Domain-Driven Design*.
- **Inversão de dependência** — regra segundo a qual o módulo de política (domínio) não deve
  depender do módulo de detalhe (infraestrutura); quando um precisa do outro, a **interface pertence
  ao consumidor**, não a quem implementa.
- **Porta** (*port*) — interface pela qual a aplicação expõe ou consome capacidade, sem revelar
  implementação. `GenerateStudyPlanUseCase` é uma.
- **Coesão** — o quanto as partes de um módulo tratam do mesmo assunto. **Acoplamento** — o quanto um
  módulo depende de outro.
- **Co-mudança** (*co-change*) — dois arquivos que aparecem juntos no mesmo commit repetidas vezes.
  É evidência empírica de acoplamento, medida no histórico em vez de inferida do código.
- **Ciclo de dependência** — A depende de B e B depende de A, direta ou indiretamente. Impede
  compilar, testar ou extrair um sem o outro.

---

## Nota de escopo: quatro contextos foram nomeados, um existe

A tarefa nomeia *Educational Optimization*, *Intelligent Tutoring*, *Educational Analytics* e
*Integration Gateway*. Refiz a verificação, agora pela quarta vez nesta sequência, excluindo
`docs/` (que são os relatórios desta própria varredura):

| Termo | Arquivos |
|---|---|
| `Educational Optimization` / `EducationalOptimization` | **0** |
| `Educational Analytics` | **0** |
| `Integration Gateway` | **0** |
| `Intelligent Tutoring` | 6 — cinco documentos prospectivos de maio/2026 e **um comentário Javadoc** em `domain/tactical/TacticalStudyPlan.java:7` |

**Existe um serviço, um `pom.xml`, um `main`, um endpoint.** Não há fronteira entre contextos a
avaliar, porque não há contextos separados.

**O que este documento avalia, então:** as fronteiras que **existem de fato** — os módulos de topo
sob `com.ia.project.dynamicstudyplanner` (`api`, `usecase`, `service`, `ga`, `domain`, `config`,
`util`) —, que é o que o próprio `README.md` chama de *"bounded contexts mapped to packages"*. As
perguntas 1 a 8 são respondidas sobre elas, com o mesmo rigor que teriam entre serviços.

---

## 1. Os limites entre módulos são respeitados?

### 1.1 O grafo real de dependências

Extraído dos `import` internos de todas as 118 classes de `src/main`:

```
api      ──▶ domain, usecase
usecase  ──▶ domain
service  ──▶ domain, ga, usecase, util
ga       ──▶ domain, service, util
                    ▲
                    └── aqui está o problema
```

### 1.2 O limite que funciona — e vale dizer por quê

**`api` não conhece `service` nem `ga`.** Conversa com a aplicação exclusivamente por uma porta:

```java
// api/controller/OptimizerController.java:33
private final GenerateStudyPlanUseCase plannerUseCase;
```

`usecase/GenerateStudyPlanUseCase` tem 15 linhas e declara um único método. `api` não importa nada
de `service` nem de `ga` — verificado por busca: **zero ocorrências**. Trocar o motor de planejamento
inteiro não tocaria em nenhuma classe de `api`.

Esse é o limite mais bem desenhado do repositório e não deve ser perdido numa refatoração futura.

### 1.3 O limite que não funciona — `ga` importa `service`

Quatro classes de `ga` importam diretamente de `service`, sem contrato intermediário:

| Arquivo | Linha | Import |
|---|---|---|
| `ga/fitness/constraint/MandatoryReviewConstraint.java` | 9 | `service.calculation.retention.RetentionAlgorithm` |
| `ga/fitness/penalty/DropoutRiskPenalty.java` | 6 | `service.calculation.engagement.DropoutRiskPredictor` |
| `ga/fitness/penalty/FatigueAndSustainabilityPenalty.java` | 6 | `service.calculation.fatigue.FatigueAndEnergyModel` |
| `ga/tactical/repair/SpacedRepetitionRepairer.java` | 9 | `service.calculation.retention.RetentionAlgorithm` |

E `service/StudyOptimizerService.java` importa `ga` de volta. **É um ciclo** — tratado na seção 5.

**Nem todos os quatro são iguais, e a diferença importa para a correção:**

- `RetentionAlgorithm` **é uma interface** (`service/calculation/retention/RetentionAlgorithm.java:15`).
  O problema não é depender dela: é que ela mora no módulo de quem a **implementa**, não no de quem a
  **consome**. Ver seção 2.2.
- `DropoutRiskPredictor` e `FatigueAndEnergyModel` são **classes concretas**. `ga` depende de
  implementação, sem interface nenhuma no caminho:

```java
// ga/fitness/penalty/FatigueAndSustainabilityPenalty.java
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
```

---

## 2. Direção de dependência

### 2.1 O domínio está limpo — e isso é uma força real

Medição sobre todas as classes de `src/main`, contando imports de framework
(`org.springframework`, `jakarta`, `io.swagger`):

| Módulo | Classes que importam framework | Total |
|---|---:|---:|
| **`domain`** | **0** | 22 |
| **`usecase`** | **0** | 1 |
| `service` | 10 | 19 |
| `ga` | 17 | 39 |
| `api` | 26 | 30 |
| `config` | 5 | 5 |

**Zero de 22 classes de domínio conhecem Spring, Jakarta ou Swagger.** A única dependência externa
do domínio é o Lombok, em três classes (`Exam.java:4`, `StudyPlan.java:4`, `StudentProfile.java:4`),
e apenas `@Getter` — processador de anotação em tempo de compilação, que não deixa rastro no
*bytecode* nem exige o Lombok em execução.

Não há acesso a banco (não há banco), não há `@Entity`, não há repositório. **A regra que DDD e
arquitetura limpa esperam está respeitada onde mais importa.** Registro isso com destaque porque é a
propriedade mais fácil de perder na primeira vez que alguém precisar persistir algo.

### 2.2 A inversão que falta: a interface mora no lugar errado

`RetentionAlgorithm` é a abstração que `ga` usa para não depender do motor de retenção concreto. Mas
ela está declarada em `service.calculation.retention`, junto da implementação:

```
service/calculation/retention/
├── RetentionAlgorithm.java      ← a interface
└── HybridRetentionEngine.java   ← a implementação
```

E o consumidor está em outro módulo:

```java
// ga/fitness/constraint/MandatoryReviewConstraint.java:26-30
private final RetentionAlgorithm retentionAlgorithm;

public MandatoryReviewConstraint(RetentionAlgorithm retentionAlgorithm) {
    this.retentionAlgorithm = retentionAlgorithm;
}
```

A injeção por interface está correta — o defeito é de **posicionamento**. Pela inversão de
dependência, a interface pertence a quem a consome (`ga`, ou `domain`), e a implementação fica em
`service`. Do jeito atual, `ga` continua tendo de compilar contra `service` só para enxergar o
contrato, e é isso que fecha o ciclo da seção 5.

**Correção conceitual, para a etapa 03b:** mover `RetentionAlgorithm` para `domain` (ou para
`ga.fitness`), deixando `HybridRetentionEngine` onde está. Uma classe muda de pacote; o ciclo some.

### 2.3 O motor genético depende do framework mais do que precisaria

17 de 39 classes de `ga` importam Spring — anotações `@Component` em operadores genéticos
(`CreepMutation`, `TournamentSelection`, `HybridCrossover`). O algoritmo genético é matemática pura:
não faz E/S, não tem estado compartilhado, não precisa de ciclo de vida gerenciado.

Não é defeito grave hoje — funciona e está bem coberto por testes. É registrado porque **os
benchmarks já pagam esse preço**: `BenchmarkHarness:90` remonta a composição à mão justamente por
não poder usar o contêiner. Ver seção 4.

---

## 3. Acoplamento e coesão

### 3.1 O par mais acoplado do repositório, medido no histórico

Análise de co-mudança sobre 58 commits que tocaram `src/main/java`, ignorando commits com mais de 12
arquivos (que não informam acoplamento):

| Arquivo A | Arquivo B | Juntos | A sozinho | B sozinho |
|---|---|---:|---:|---:|
| **`EvolutionContext`** (`ga`) | **`StudyOptimizerService`** (`service`) | **5** | 6 | 11 |
| `BaselineCalculator` | `ImportanceCalculator` | 3 | 3 | 5 |
| `Individual` (`ga`) | `StudyOptimizerService` (`service`) | 3 | 4 | 11 |
| `DayBoundaryCrossover` | `MethodologyMutation` | 3 | 4 | 3 |

**O par do topo cruza a fronteira de módulo**, e é o mesmo par do ciclo da seção 1.3. Não é
coincidência: é a mesma fronteira mal desenhada aparecendo por duas medições independentes — uma
estática (imports) e uma histórica (commits).

**A causa concreta.** `EvolutionContext.of()` tem **dez parâmetros posicionais**, quatro deles
objetos anuláveis:

```java
// ga/EvolutionContext.java:63-74
public static EvolutionContext of(
        Map<Subject, Double> importanceScores,
        Map<Subject, Integer> minimumDaysPerSubject,
        StudentState studentState,
        FitnessEvaluator fitnessEvaluator,
        RetentionProfile retentionProfile,
        LocalDate planStartDate,
        EngagementProfile engagementProfile,
        int planningHorizonDays,
        int hoursPerStudyDay,
        int maxDailyCognitiveLoad
) {
```

São **nove locais de chamada** (1 em produção, 4 em testes, 3 em benchmarks, mais a definição).
Acrescentar um parâmetro obriga a tocar em todos. O sintoma mais expressivo está num teste:

```java
// src/test/.../ga/strategy/mutation/TransferMutationTest.java:24
EvolutionContext context = EvolutionContext.of(Map.of(), Map.of(), null, null, null, null, null, 180, 4, 20);
```

**Cinco `null` consecutivos.** Quem lê não tem como saber qual argumento é qual sem abrir a
assinatura, e trocar dois deles de lugar compila sem erro.

### 3.2 Responsabilidades misturadas em `StudyOptimizerService`

192 linhas, e pelo menos quatro assuntos distintos:

```java
// service/StudyOptimizerService.java:34-44
private final BaselineCalculator baselineCalculator;        // 1. cálculo de domínio
private final ImportanceCalculator importanceCalculator;    // 1.
private final ... CognitiveLoadCalculator ... = new ...();  // 1. (e ver 3.3)
private final GeneticAlgorithmFactory gaFactory;            // 2. orquestração do AG
private final PopulationGenerator populationGenerator;      // 2.
private final ... FitnessEvaluator fitnessEvaluator;        // 2.
private final Counter optimizationRunsCounter;              // 3. observabilidade
private final Timer optimizationTimer;                      // 3.
```

Mais **4. a montagem do `EvolutionContext`**, num método privado de 20 linhas (`prepareContext`,
linha 127) que é justamente o que provoca a co-mudança de 3.1.

Consequência prática: mudar como a fitness é montada, mudar quais métricas são publicadas e mudar
quais calculadoras alimentam o contexto são três motivos independentes para editar o mesmo arquivo.

### 3.3 Duas formas de obter a mesma dependência, no mesmo fluxo

```java
// service/StudyOptimizerService.java:36-37
private final com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator cognitiveLoadCalculator =
        new com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator();
```

Instanciada com `new`. Enquanto isso, no mesmo caminho de execução:

```java
// service/DynamicStudyPlannerService.java:27-31
private final CognitiveLoadCalculator cognitiveLoadCalculator;

public DynamicStudyPlannerService(..., CognitiveLoadCalculator cognitiveLoadCalculator) {
```

Injetada. **A mesma classe, obtida de duas maneiras, no mesmo fluxo** — e a versão com `new` escapa
de qualquer substituição por dublê em teste. Já registrado como R21 no inventário; continua presente.

O mesmo trecho usa **nomes totalmente qualificados** em declaração de campo, onde um `import`
resolveria (R22). Ocorre em duas linhas do mesmo arquivo e em nenhum outro lugar do repositório.

### 3.4 Uma consequência da própria etapa 02b, registrada por honestidade

`api/exception/GlobalExceptionHandler.java` é hoje **a maior classe do repositório: 447 linhas e 16
tratadores**. Eram **214 linhas e 9 tratadores** antes da etapa 02b — os outros sete foram
acrescentados por mim, para corrigir o achado S8.

A decisão continua correta — tratadores explícitos evitam a sobreposição com
`ResponseEntityExceptionHandler` —, mas o custo estrutural é real e não deve ficar implícito: uma
classe dobrou de tamanho e concentra decisões de três naturezas (erro de cliente, erro de negócio, erro de
infraestrutura). Um recorte plausível é separar por natureza em três `@RestControllerAdvice`
ordenados. Fica registrado como achado **E7**, não como pendência oculta.

---

## 4. Duplicação estrutural

Não há lógica de negócio replicada entre serviços, porque não há serviços. O que há é **a fiação de
produção remontada à mão fora da produção**, em dois pontos.

### 4.1 A cadeia de estratégias de alocação, em quatro lugares

Produção:

```java
// service/DynamicStudyPlannerService.java:64-69
AllocationStrategy finalStrategy = new ReviewFocusedStrategy(
        new CognitiveLoadBalancingStrategy(
                new InterleavedCriticalStrategy(),
                cognitiveLoadCalculator.calculate(profile, exam)
        )
);
```

Benchmark, com o comentário admitindo a réplica:

```java
// benchmarks/.../metric/MetricsCalculator.java:76-79
// Schedule 1: exactly the chain DynamicStudyPlannerService composes in production.
AllocationStrategy productionChain = new ReviewFocusedStrategy(
        new CognitiveLoadBalancingStrategy(new InterleavedCriticalStrategy(), maxDailyLoad));
```

Mais `MetricsCalculator:86` (variante sem o podador) e `GaEdgeCasesTest:426`.

### 4.2 A composição do `FitnessEvaluator`, em seis lugares fora da produção

Em produção o Spring injeta `List<FitnessObjective>`, `List<FitnessPenalty>` e
`List<ConstraintValidator>`. Fora dela, a composição é escrita à mão em `BenchmarkHarness:90`,
`WeightSensitivityMain:179`, `WeightTradeoffMain:440`, `IndividualTest:101`, `GaEdgeCasesTest:415` e
`PrerequisiteSequencingDiagnosticTest:206` — dois destes com o comentário *"The same component set
Spring wires in production"*.

**O risco concreto:** acrescentar um `FitnessObjective` novo em produção é uma linha (um
`@Component`). O Spring o injeta automaticamente e **as seis cópias continuam rodando sem ele**, em
silêncio. Os números publicados em `docs/revisao-ag/` passariam a medir uma fitness que não é a de
produção.

Uma proteção parcial foi acrescentada na etapa 01b — o teste que verifica que os objetivos fiados
pelo Spring somam 1,0 falharia se alguém acrescentasse um objetivo sem rebalancear. Mas ela não pega
o caso de um objetivo novo com peso 0, nem garante que as seis cópias tenham a mesma composição.

---

## 5. Dependências circulares

### 5.1 O ciclo que importa: `ga` ↔ `service`

```
ga.fitness.constraint.MandatoryReviewConstraint      ──▶ service.calculation.retention.RetentionAlgorithm
ga.fitness.penalty.DropoutRiskPenalty                ──▶ service.calculation.engagement.DropoutRiskPredictor
ga.fitness.penalty.FatigueAndSustainabilityPenalty   ──▶ service.calculation.fatigue.FatigueAndEnergyModel
ga.tactical.repair.SpacedRepetitionRepairer          ──▶ service.calculation.retention.RetentionAlgorithm
                                                              │
service.StudyOptimizerService ◀───────────────────────────────┘
```

**Custo prático:** nenhum dos dois módulos pode ser compilado, testado ou extraído sem o outro. Se
algum dia o motor genético virar biblioteca — que é a evolução natural de um componente com 39
classes e testes próprios —, o ciclo precisa ser desfeito antes.

**Custo hoje:** menor do que parece, e é justo dizer. Três das quatro arestas apontam para código que
nenhum caminho de produção alcança (seção 6). Se aquele código for removido, **sobra uma aresta** — a
do `MandatoryReviewConstraint` — e ela some movendo uma interface de pacote (seção 2.2).

### 5.2 Os ciclos entre pacote-pai e pacote-filho

A análise fina encontrou seis, todos do mesmo formato:

| Ciclo | Natureza |
|---|---|
| `ga` ↔ `ga.fitness` | interface no pai, implementação no filho |
| `ga` ↔ `ga.strategy.crossover` / `.mutation` / `.selection` | idem |
| `ga.fitness` ↔ `ga.fitness.constraint` / `.objective` | idem |
| `domain` ↔ `domain.schedule` | `FullPlannerResult` usa `ScheduleResult`; `ScheduleResult` usa `StudyBlock` |

**Não os classifico como defeito.** São o padrão normal em Java quando a abstração fica no pacote de
cima e as implementações em subpacotes — `EvolutionContext` (em `ga`) é usado pelos operadores (em
`ga.strategy.*`), que por sua vez são referenciados por `GeneticAlgorithm` (em `ga`). Registro para
que um verificador automático de ciclos, se for adotado, seja configurado para ignorá-los, em vez de
gerar ruído que ensine a equipe a ignorar o verificador.

---

## 6. Código morto × código sem teste

O cruzamento pedido é essencial: **"0% de cobertura" e "morto" são coisas diferentes**, e confundi-las
leva a escrever teste para código que deveria ser removido — ou a remover código que só falta testar.

Método: cruzei três sinais — referência textual por outra classe de produção, presença de anotação
Spring, e cobertura de instruções da etapa 01b.

### 6.1 Categoria A — morto de verdade: sem anotação, sem consumidor, 0% de cobertura

| Classe | Instruções | Evidência |
|---|---:|---|
| `ga.tactical.strategy.crossover.DayBoundaryCrossover` | 123 | Implementa `TacticalCrossoverStrategy`, sem anotação Spring, sem consumidor |
| `service.scheduler.strategy.RandomizedInterleavedStrategy` | 82 | Implementa `AllocationStrategy`, sem anotação, nunca construída |
| `service.scheduler.strategy.CriticalFirstStrategy` | 66 | idem |
| `ga.tactical.strategy.mutation.MethodologyMutation` | 61 | Implementa `TacticalMutationStrategy`, sem anotação, sem consumidor |
| `domain.exam.ExamType` | 15 | Enum que nenhuma classe referencia |
| `api.dto.OptimizationResponse` | 9 | Só usada pelo método `@Deprecated` `OptimizationResultMapper.toResponse` |

**Total: 356 instruções que nada instancia e nada chama.** As duas estratégias de alocação já eram
G11 em `docs/revisao-ag/`; as demais não estavam catalogadas.

### 6.2 Categoria B — instanciado pelo Spring, inalcançável pelo usuário

Esta é a categoria que o cruzamento revela e que uma leitura só de cobertura confundiria com a
anterior. **O Spring cria estes objetos na subida da aplicação** — eles existem no grafo de beans,
custam tempo de inicialização e memória — mas nenhuma requisição chega à lógica deles.

| Classe | Anotação | Cobertura | Por que é inalcançável |
|---|---|---:|---|
| `service.scheduler.tactical.HybridHeuristicScheduler` | `@Service` | 100% | Ninguém injeta `TacticalScheduler` |
| `ga.tactical.repair.SpacedRepetitionRepairer` | `@Component` | 4,9% | Ninguém injeta `ChromosomeRepairer` |
| `service.calculation.fatigue.FatigueAndEnergyModel` | `@Service` | 1,3% | Injetado em `FatigueAndSustainabilityPenalty`, que retorna 1,0 no caminho macro |
| `ga.fitness.penalty.DropoutRiskPenalty` | `@Component` | 29,8% | Injetado em `List<FitnessPenalty>`; retorna 1,0 no caminho macro |
| `ga.fitness.constraint.MandatoryReviewConstraint` | `@Component` | 22,1% | Guarda de entrada retorna `true` salvo para `TacticalStudyPlan`, que nada produz |

**`HybridHeuristicScheduler` merece destaque: 100% de cobertura e zero consumidores.** É a
demonstração mais limpa de por que o cruzamento era necessário — pela cobertura, é a classe mais bem
testada da categoria; pela estrutura, ninguém a chama. Três testes cuidam de código que nenhum
usuário alcança, enquanto a etapa 01 encontrou o orquestrador do produto com zero.

### 6.3 Categoria C — sem teste, mas vivo

Nenhuma classe cai aqui hoje. Depois das etapas 01b e 02b, todo código alcançável em produção tem
cobertura: o recorte alcançável está em **87,3% de instruções** (`01b` §8.2). É um resultado útil de
registrar — significa que **todo o código com cobertura baixa que resta é da categoria A ou B**, e
portanto a decisão pendente é de remoção, não de teste.

### 6.4 A pergunta que este diagnóstico não responde

As categorias A e B somam **cerca de 630 instruções e 84 ramos** — 9,5% do código e 17,4% dos ramos.
Removê-las é a decisão óbvia por manutenção; mantê-las é defensável se a camada tática for retomada,
que é a hipótese registrada em G5. **A escolha é de produto** — vale o trabalho de terminar o ITS? — e
não deve ser tomada por critério estrutural isolado. A seção 8 a coloca como decisão, não como tarefa.

---

## 7. Consistência interna

Não há "entre serviços" a comparar. As inconsistências abaixo são internas e todas atrapalham quem
chega ao projeto.

### 7.1 Seis sufixos para o mesmo papel

Classes sem estado que recebem dados de domínio e devolvem um número ou um objeto calculado:

| Sufixo | Classes |
|---|---|
| `...Calculator` | `BaselineCalculator`, `CognitiveLoadCalculator`, `ImportanceCalculator` |
| `...Model` | `FatigueAndEnergyModel`, `LearningModel` |
| `...Engine` | `HybridRetentionEngine` |
| `...Predictor` | `DropoutRiskPredictor` |
| `...Evaluator` | `FitnessEvaluator` |
| `...Generator` | `StudyScheduleGenerator`, `DefaultPopulationGenerator` |

Quem procura "onde se calcula a carga cognitiva" tem seis vocabulários para adivinhar. `LearningModel`
e `FatigueAndEnergyModel` compartilham sufixo e não compartilham papel — o primeiro é uma fachada
sobre retenção, o segundo uma calculadora de fadiga.

### 7.2 `DomainException` existe, e o domínio não a usa

```java
// domain/exception/DomainException.java:4
/** Base exception to all business rule violations in the domain layer. */
```

Contagem de exceções lançadas em `src/main`:

| Tipo | Ocorrências |
|---|---:|
| `IllegalArgumentException` | **13** |
| `IllegalStateException` | 2 |
| `UnsupportedOperationException` | 1 |
| `UnknownSubjectException` (etapa 02b) | 1 |
| **`DomainException`** | **1** |

A única ocorrência é `domain/exam/Exam.java:97`. Enquanto isso, regras de negócio inequívocas usam
`IllegalArgumentException`:

```java
// ga/factory/StudyPlanFactory.java:63-68
if (totalMinimumDays > totalDays) {
    throw new IllegalArgumentException(
            "Total minimum study days required (" + totalMinimumDays +
                    ") exceeds total available days (" + totalDays + ")."
    );
}
```

"O piso de dias mínimos excede os dias disponíveis" é uma regra de negócio, não um argumento
malformado.

**Isto liga estrutura a um achado anterior.** A etapa 02 concluiu que o tratador de `422` é
inalcançável pela API. A causa raiz é esta: `DomainException` mapeia para `422`,
`IllegalArgumentException` mapeia para `400`, e o domínio quase nunca lança a primeira. **A
inconsistência de nomenclatura de exceção é o que torna metade do contrato de erro decorativa.**

### 7.3 Dois motores, dois critérios de organização de pastas

```
ga/            — organizado por PAPEL TÉCNICO      service/calculation/ — por TEMA DE DOMÍNIO
├── config/                                        ├── engagement/
├── factory/                                       ├── fatigue/
├── fitness/                                       └── retention/
├── generator/
├── strategy/
└── tactical/
```

Os dois critérios são defensáveis isoladamente; conviverem sem regra declarada significa que, ao
criar uma classe nova, não há como saber onde ela vai. `service/scheduler/` usa ainda um terceiro
arranjo (`strategy/` e `tactical/` lado a lado), misturando os dois.

### 7.4 Idioma misturado, sem fronteira declarada

| Onde | Idioma |
|---|---|
| Código de produção (nomes, mensagens de exceção) | inglês |
| Javadoc de produção | inglês em 112 de 118 classes; **português em 6** — as tocadas nas etapas 01b e 02b |
| `@DisplayName` de teste | português em 16 arquivos |
| Nomes de método de teste | inglês (`shouldX`) em 9 arquivos, português nos mais recentes |

A mistura é consequência das etapas desta varredura, e eu contribuí para ela. A regra faz falta
porque hoje um arquivo pode ter Javadoc em português descrevendo um método com nome em inglês que
lança mensagem em inglês. **Uma linha de convenção resolve** — por exemplo: identificadores e
mensagens de API em inglês, explicação e teste em português. Não existe hoje.

---

## 8. Lista priorizada por impacto em manutenção

Ordenada por **quanto atrapalha quem for mexer no código**, não por pureza. Um item sobe se torna
mudanças mais caras ou mais arriscadas; desce se é incômodo estético.

### Nível 1 — Torna mudanças caras ou arriscadas hoje

| # | Achado | Evidência | Custo concreto |
|---|---|---|---|
| **E1** | **`EvolutionContext.of()` com 10 parâmetros posicionais e 9 locais de chamada** | `ga/EvolutionContext.java:63`; `TransferMutationTest:24` com cinco `null` seguidos | É a co-mudança nº 1 do repositório (5 commits). Todo campo novo no contexto obriga a tocar em 9 arquivos, e trocar dois argumentos de lugar compila |
| **E2** | **Composição de produção duplicada em 4 + 6 lugares, sem verificação** | `DynamicStudyPlannerService:64` × `MetricsCalculator:78` × `GaEdgeCasesTest:426`; `FitnessEvaluator` em 6 pontos | Um `FitnessObjective` novo entra em produção por uma anotação e **não entra nas cópias**. Os números publicados passariam a medir outra fitness |
| **E3** | **`DomainException` usada 1 vez contra 13 `IllegalArgumentException`** | `StudyPlanFactory:63-68`; `Exam.java:97` | Torna o `422` inalcançável — metade do contrato de erro é decorativa. Já mapeado na etapa 02 como sintoma; aqui está a causa |

### Nível 2 — Impede evolução estrutural

| # | Achado | Evidência | Custo concreto |
|---|---|---|---|
| **E4** | **Ciclo `ga` ↔ `service`** | 4 imports listados em §1.3 + `StudyOptimizerService` | Nenhum dos módulos compila ou é extraído sem o outro. **Barato de resolver:** 3 das 4 arestas somem com a remoção do código morto, e a 4ª com mover uma interface |
| **E5** | **`RetentionAlgorithm` mora no módulo de quem implementa** | `service/calculation/retention/RetentionAlgorithm.java:15` consumida por `ga/fitness/constraint/MandatoryReviewConstraint.java:9` | É a aresta que sobra do E4. Uma classe muda de pacote |
| **E6** | **~630 instruções e 84 ramos de código inalcançável**, em duas categorias distintas | Tabelas §6.1 e §6.2 | Todo leitor futuro paga o custo de entender código que não roda. **Mas a decisão é de produto** (retomar o ITS ou não), não estrutural |

### Nível 3 — Atrapalha a leitura e o onboarding

| # | Achado | Evidência |
|---|---|---|
| **E7** | `GlobalExceptionHandler` com 447 linhas e 16 tratadores — a maior classe do repositório; era 214 linhas e 9 tratadores antes da etapa 02b | `api/exception/GlobalExceptionHandler.java` |
| **E8** | `StudyOptimizerService` mistura 4 responsabilidades (cálculo, orquestração, métricas, montagem de contexto) | `service/StudyOptimizerService.java:34-44,127` |
| **E9** | Seis sufixos (`Calculator`, `Model`, `Engine`, `Predictor`, `Evaluator`, `Generator`) para o mesmo papel | §7.1 |
| **E10** | `ga/` organiza por papel técnico, `service/calculation/` por tema de domínio, `service/scheduler/` mistura os dois | §7.3 |
| **E11** | Idioma misturado sem convenção declarada — e as etapas 01b/02b pioraram isso | §7.4 |

### Nível 4 — Pontuais

| # | Achado | Evidência |
|---|---|---|
| **E12** | `CognitiveLoadCalculator` obtida com `new` num serviço e por injeção em outro, no mesmo fluxo | `StudyOptimizerService:36-37` × `DynamicStudyPlannerService:27` |
| **E13** | Nomes totalmente qualificados em declaração de campo, só nesse arquivo | `StudyOptimizerService:36-37,40` |
| **E14** | Operadores genéticos anotados com `@Component` sem precisarem de ciclo de vida gerenciado | 17 de 39 classes de `ga` |
| **E15** | Seis ciclos pai↔filho de pacote — **não são defeito**, mas precisam ser ignorados explicitamente se um verificador for adotado | §5.2 |

---

## O que a estrutura faz certo

Três propriedades que não devem ser perdidas numa refatoração, e que só aparecem se alguém as
procurar:

1. **O domínio é puro.** Zero de 22 classes com dependência de framework. É a base sobre a qual
   qualquer reorganização futura pode se apoiar com segurança.
2. **A fronteira `api` → `usecase` é real.** `api` não conhece `service` nem `ga`; conversa por uma
   porta de 15 linhas. Trocar o motor inteiro não tocaria o `api`.
3. **O agrupamento dos operadores genéticos é coeso.** `HybridCrossover`, `RepairingCrossover`,
   `WeightedAverageCrossover` e `TournamentSelection` co-mudam entre si em 3 commits e quase não
   co-mudam com o resto — sinal de que aquele pacote tem uma fronteira bem escolhida.

---

## Resumo em uma tela

- **Um contexto, não quatro.** *Educational Analytics* e *Integration Gateway* têm zero ocorrências;
  *Intelligent Tutoring* existe num Javadoc e em cinco documentos prospectivos. O diagnóstico avalia
  os módulos que existem, com o mesmo rigor.
- **O domínio está limpo** — 0 de 22 classes importam framework — e a fronteira `api` → `usecase`
  funciona. São as duas forças estruturais reais.
- **Existe um ciclo `ga` ↔ `service`**, com quatro arestas nomeadas. Três somem se o código
  inalcançável for removido; a quarta some movendo uma interface de pacote.
- **O maior custo de manutenção não é o ciclo: é `EvolutionContext.of()` com 10 parâmetros
  posicionais** e 9 locais de chamada — a co-mudança nº 1 do repositório, medida em 58 commits.
- **A composição de produção é remontada à mão em até 10 lugares** fora da produção, sem nada
  verificando que continuam iguais.
- **`DomainException` é lançada 1 vez contra 13 `IllegalArgumentException`** — e é essa
  inconsistência que torna o `422` inalcançável, ligando este diagnóstico ao da etapa 02.
- **~630 instruções inalcançáveis, em duas categorias que a cobertura sozinha confundiria**: 356 que
  nada instancia, e ~275 que o Spring instancia mas nenhuma requisição alcança —
  `HybridHeuristicScheduler` entre elas, com 100% de cobertura e zero consumidores.

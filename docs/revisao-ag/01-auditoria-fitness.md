# Auditoria Crítica da Função de Fitness — SINAPSE / Exam Optimizer

**Documento anterior:** [`00-diagnostico.md`](./00-diagnostico.md) (mapa descritivo, sem juízo de valor)
**Este documento:** auditoria crítica. Avalia fidelidade teórica, calibração, escala e
inércia de cada termo, e propõe alternativas de correção **sem implementá-las**.
**Commit auditado:** `7b8eacc` (código idêntico a `814f9d8`; nenhuma alteração de código feita)
**Data:** 2026-08-30

**Método.** Toda afirmação abaixo é (a) uma citação literal do código, (b) uma derivação
algébrica explicitada no Apêndice, ou (c) marcada como juízo de engenharia. Onde a auditoria
depende de uma invariante do código, a invariante é provada por enumeração exaustiva dos
produtores daquele objeto.

---

## 0. Veredito em uma página

A fitness declara cinco componentes. **Quatro não influenciam o resultado.** O que decide o
plano entregue ao aluno é um único termo.

| Componente | Situação | Prova |
|---|---|---|
| `ScoreGainObjective` | **Único componente que decide o resultado** | — |
| `MinimumDaysConstraint` (×0.5) | **Inerte na prática** — nenhum operador do AG consegue produzir um indivíduo que a viole | §1.2 (enumeração dos 5 produtores de `StudyPlan`) |
| `MandatoryReviewConstraint` | **Inerte por tipo** — guarda `instanceof TacticalStudyPlan` nunca satisfeita no macro | §1.1 |
| `DropoutRiskPenalty` | **Inerte por tipo** — mesma guarda | §1.1 |
| `FatigueAndSustainabilityPenalty` | **Inerte para a ordenação** — é um fator constante idêntico para todos os indivíduos; provado invariante para torneio, crossover e estagnação | Apêndice B |
| Mecanismo de pesos (`getWeight()`) | **Inerte** — existe um único objetivo, com peso `1.0` | §2.1 |

Consequências que precisam ser ditas com todas as letras:

1. **Nenhum dos três fundamentos teóricos declarados influencia a otimização.** Ebbinghaus
   entra apenas por um termo inerte; Sweller não entra em termo nenhum; Ausubel não existe no
   código. A afirmação de que "a fitness incorpora os três fundamentos" **não é sustentável
   hoje** em material técnico ou comercial. Este é o achado central da auditoria.

2. **A fitness efetiva é `Σ_s ln(1 + dias_s) · importância_s`, sujeita a `Σ dias_s = D` e
   `dias_s ≥ min_s`.** Esse é um problema de maximização separável e côncava sob restrição
   linear de orçamento com limites inferiores em caixa. Ele tem **solução exata em forma
   fechada** (water-filling / KKT) e solução ótima gulosa em `O(D log n)` — ver Apêndice A,
   com exemplo numérico. Um AG estocástico de até 500 000 avaliações está aproximando, com
   erro, um problema que se resolve exatamente em microssegundos.

3. **O AG não é injustificado por ser um AG — é injustificado pela fitness atual.** As três
   teorias ausentes são exatamente o que quebraria a separabilidade e a concavidade
   (sequenciamento por pré-requisitos → ordem importa; teto diário de carga → acoplamento
   entre matérias; retenção na data da prova → o valor de um dia depende de *quando* ele
   ocorre). Corrigir a fidelidade teórica e justificar o AG são **o mesmo trabalho**, não dois.

4. **A fitness não tem dimensão temporal.** O cromossomo é `Subject → quantidade de dias`. Um
   dia de estudo seis meses antes da prova vale exatamente o mesmo que um dia na véspera.
   Esquecimento é, por definição, função do tempo decorrido. **Uma função-objetivo sem
   coordenada temporal não pode representar Ebbinghaus**, independentemente de qual termo se
   acrescente a ela. Esta é uma limitação do *encoding*, não da fitness.

---

## 1. Preliminar: qual é a fitness que de fato roda

Antes de auditar termo a termo, é preciso estabelecer o que sobra do pipeline em execução.
`FitnessEvaluator.evaluate` (`FitnessEvaluator.java:39-66`) calcula:

```
fitness = [ Σ_obj reward_obj · w_obj ] · (violou? 0.5 : 1.0) · Π_pen fator_pen
```

### 1.1 Duas guardas de tipo que nunca abrem

`MandatoryReviewConstraint.java:29` e `DropoutRiskPenalty.java:24` abrem com:

```java
if (!(plan instanceof TacticalStudyPlan tacticalPlan) || ...) { return true/1.0; }
```

Os cinco pontos do código que constroem um `StudyPlan` para a população do AG macro são:

| # | Produtor | Linha | Tipo construído |
|---|---|---|---|
| 1 | `StudyPlanFactory.createRandomPlan` | `StudyPlanFactory.java:68` | `new StudyPlan(...)` |
| 2 | `WeightedAverageCrossover.crossover` | `WeightedAverageCrossover.java:35` | `new StudyPlan(...)` |
| 3 | `RepairingCrossover.crossover` | `RepairingCrossover.java:37` | `new StudyPlan(...)` |
| 4 | `AbstractMutationStrategy.mutate` | `AbstractMutationStrategy.java:70` | `new StudyPlan(...)` |
| 5 | Clone do pai mais apto (sem crossover) | `HybridCrossover.java:27` e equivalentes | reusa o `StudyPlan` do pai |

Nenhum produz `TacticalStudyPlan`. As duas guardas retornam sempre o valor neutro.
**Confirmado: dois dos cinco componentes são código morto no caminho macro.**

### 1.2 A constraint de dias mínimos nunca é violada

Este ponto não estava no diagnóstico e é novo desta auditoria. `MinimumDaysConstraint` **não é
inerte por tipo** — ela é inerte porque *nenhum operador consegue gerar um indivíduo infrator*:

| Produtor | Por que não viola `d_s ≥ m_s` |
|---|---|
| `StudyPlanFactory` | Inicializa cada matéria em `getOrDefault(subject, 1)` e só **adiciona** dias (`StudyPlanFactory.java:58-66`) |
| `WeightedAverageCrossover` | `child_s = round((p1_s·w1 + p2_s·w2)/(w1+w2))`. Média ponderada de dois valores `≥ m_s` é `≥ m_s`; e `round(x) ≥ m_s` para `x ≥ m_s` com `m_s` inteiro |
| `RepairingCrossover` | `child_s ∈ {p1_s, p2_s}`, ambos `≥ m_s` (`RepairingCrossover.java:56-58`) |
| `repairChildGenes` (ambos crossovers) | Só subtrai sob `if (currentDays > minimumDays)`; caso contrário remove a matéria das candidatas (`RepairingCrossover.java:82-89`) |
| `CreepMutation` | Aborta retornando `false` se qualquer das duas matérias cair abaixo do mínimo (`CreepMutation.java:122-126`) |
| `TransferMutation` | Aborta se `genes.get(subjectFrom) <= minimumDays` (`TransferMutation.java:46-48`) |
| Clone do pai | Herda a viabilidade do pai |

Como a população inicial é viável e todo operador preserva a viabilidade, **por indução toda a
população é viável em toda geração**. `MinimumDaysConstraint.isValid` retorna `true` sempre; o
ramo `totalReward *= 0.5` é inalcançável em produção.

> **Nota sobre o teste existente.** `IndividualTest.shouldApplyPenaltyWhenConstraintsViolated`
> (`IndividualTest.java:42-67`) constrói **manualmente** um plano `Map.of(math, 1)` contra um
> mínimo de `5` para exercitar o multiplicador `0.5`. O teste é válido como teste unitário do
> `FitnessEvaluator`, mas testa um estado que o AG **não consegue produzir**. Ele não dá
> nenhuma garantia sobre o comportamento real do otimizador.

### 1.3 A fitness efetiva

Removidos os componentes inertes e aplicada a prova do Apêndice B (o fator de fadiga é
constante e não altera ordenação), o que o AG maximiza é:

```
argmax over d  Σ_s  ln(1 + d_s) · I_s
sujeito a       Σ_s d_s = D          (D = totalStudyDays da requisição)
                d_s ≥ m_s            (m_s = BaselineCalculator)
                d_s ∈ ℤ
```

**Um único termo. Um único peso, igual a 1.0. Nenhuma teoria de aprendizagem.**

---

## 2. Auditoria termo a termo

### T1 — `ScoreGainObjective`: `Σ ln(1 + dias_s) · importância_s`, peso `1.0`

`ga/fitness/objective/ScoreGainObjective.java:20-44`

O termo tem dois fatores com estatutos epistêmicos muito diferentes; auditá-los juntos é o que
permitiu que o problema passasse despercebido.

#### 2.1.1 Fidelidade — fator `importância_s` (peso de edital)

**Veredito: alta.** É uma leitura linear e direta das regras de pontuação do edital
(`Exam.java:93-110`): `questionCount × valorPorQuestão` para conhecimentos gerais e
`questionCount × axisWeight` para específicos. Não há teoria de aprendizagem envolvida nem
pretendida, e o código não alega o contrário. Este é o único fator da fitness cuja
operacionalização corresponde ao que o nome promete.

Ressalva: matéria não encontrada em nenhum eixo recebe importância `0.0`
(`Exam.java:107-108`) com apenas um `log.warn`. Uma matéria órfã por erro de payload é
**silenciosamente excluída da otimização** — recebe só os dias mínimos e nunca mais. Falha
silenciosa em produto comercial.

#### 2.1.2 Fidelidade — fator `ln(1 + dias)`

**Veredito: baixa.** O comentário no código é honesto — *"A logarithmic function models the
diminishing returns of studying"* (linha 34) — e **não** invoca nenhuma teoria. O problema não é
falsa rotulagem, é adequação:

- **Concavidade: correta em espírito.** Retornos decrescentes do estudo são um resultado
  robusto na literatura. `ln` captura isso.
- **Ausência de teto: teoricamente errada.** `ln(1+d)` é ilimitada superiormente. A função
  afirma que o conhecimento cresce sem limite com o número de dias — não há noção de domínio
  ou de teto de maestria. As curvas usadas na literatura (lei de potência da prática;
  aproximação exponencial a uma assíntota, `1 − e^{−kd}`) são **limitadas**. A consequência
  prática está em §2.1.4: sem saturação, uma matéria de importância alta absorve o orçamento
  inteiro porque seu ganho marginal `I_s/(1+d_s)` nunca cai o suficiente.
- **Unidade errada para o objetivo de negócio.** O termo mede *dias alocados*, não horas, não
  domínio, e não pontuação esperada na prova. O produto vende "plano que maximiza desempenho no
  concurso"; a fitness maximiza uma proxy logarítmica de alocação.
- **Sem coordenada temporal.** `d_s` é uma contagem, não uma posição no calendário. Nenhuma
  função de `d_s` pode expressar "esta matéria foi estudada há 90 dias e será esquecida até a
  prova". Ver §3.3.

#### 2.1.3 Calibração dos pesos e constantes

| Elemento | Origem | Classificação |
|---|---|---|
| `getWeight()` = `1.0` | literal em `ScoreGainObjective.java:43` | **Inerte**, não "mágico": com um único objetivo, qualquer valor positivo produz o mesmo `argmax` |
| Base do logaritmo (`e`) | `Math.log` | **Inócuo**: mudar de base é um reescalonamento constante, não altera o `argmax` |
| Offset `1 +` | literal, linha 35 | **Justificável e não documentado**: evita `ln(0) = −∞`. A escolha do offset `1` (em vez de, digamos, `0.5`) muda a curvatura na região de poucos dias, que é exatamente onde estão as matérias de baixa importância. Nunca foi calibrada |
| Forma funcional `ln` | escolha de projeto | **Número mágico estrutural**: a decisão não-calibrada aqui não é uma constante, é a *família de funções*. Não há registro, no código ou nos 7 `.md` da raiz, de por que `ln` e não uma curva com teto |

**Nenhuma calibração empírica em lugar nenhum.** Não há dados, experimento, ajuste a histórico
de alunos, nem sequer uma nota de intuição registrada.

#### 2.1.4 Escala e risco de dominação — quantificado

Este é o problema mais severo do termo, e é quantificável a partir das validações dos DTOs.

Limites que a API aceita (`SubjectDto`, `ThematicAxisDto`, `ExamDto`, `StudentProfileDto`):

| Variável | Faixa validada | Arquivo |
|---|---|---|
| `questionCount` | `[1, 500]` | `SubjectDto.java:21-22` |
| `axisWeight` | `(0, 100]` | `ThematicAxisDto.java:24-25` |
| `generalKnowledgeTotalScore` | `[0, 1000]` | `ExamDto.java:32-33` |
| `knowledgeGaps` (valor) | `[1.0, 5.0]` | `StudentProfileDto.java:29` |
| nº de matérias | até `50 + 50×50 = 2550` | `ExamDto.java:37,42`, `ThematicAxisDto.java:29` |

Daí:

```
importância de matéria de conhecimentos gerais  ≤ 1000 × 5      =     5 000
importância de matéria de conhecimentos específicos ≤ 500 × 100 × 5 = 250 000
importância mínima não-nula                                       →  ~0 (axisWeight aceita 1e-9)
importância de matéria órfã                                       =   0 exatamente
```

**A razão entre dois termos da mesma soma pode chegar a `250 000 : 1`, e nada no código
normaliza isso.** `axisWeight` sequer precisa somar 1 entre os eixos — são multiplicadores
livres.

Compare com a amplitude do outro fator:

```
ln(1 + d)  para d ∈ [1, 365]  →  [0.693, 5.902]      razão máx/mín = 8.5×
```

**A dimensão "importância" varia em até 5 ordens de grandeza; a dimensão "dias" varia menos de
1 ordem de grandeza.** O resultado é *winner-take-all*: o ganho marginal do dia seguinte para a
matéria `s` é `I_s/(1 + d_s)`; com `I_A/I_B = 10^5`, a matéria A continua batendo a matéria B
mesmo com A já em 364 dias e B em 1 dia (`250000/365 = 685` contra `1/2 = 0.5`).

**Consequência estrutural:** a única coisa que impede matérias de baixa importância de ficarem
com zero atenção é o piso `m_s` de `MinimumDaysConstraint`. Ou seja — **a diversificação do
plano é feita inteiramente pela restrição, não pelo objetivo.** O objetivo é degenerado; a
restrição está carregando o produto.

> `NUMERICAL_STABILITY_AUDIT.md` §2A previu exatamente esse modo de falha
> ("Domination Imbalance") e recomendou normalizar todo objetivo para `[0, 100]`. A recomendação
> **não foi implementada**. O documento descreve um estado desejado que o leitor pode confundir
> com o estado atual.

#### 2.1.5 Redundância

Sim, e em dois níveis:

1. **`importância_s` alimenta o objetivo E a restrição.** `BaselineCalculator.calculateMinimumDays`
   chama `ImportanceCalculator.calculatePersonalizedImportance` (`BaselineCalculator.java:39`) e
   deriva `m_s` proporcionalmente ao score normalizado. O mesmo sinal empurra o objetivo e o
   piso **na mesma direção**, comprimindo o espaço de decisão do AG.

2. **A "dificuldade percebida" é algebricamente idêntica à importância.**
   `BaselineCalculator.calculatePerceivedDifficulty` (linhas 67-73):
   ```java
   double objectiveWeight = importanceScores.getOrDefault(subject, 0.0) / knowledgeGapFactor;
   return objectiveWeight * knowledgeGapFactor;   // ≡ importanceScores.get(subject)
   ```
   O fator de lacuna entra e sai. O comentário afirma combinar dois sinais
   (*"important for the exam AND the student is weak in it"*); o código retorna um só.
   **Não existe sinal independente de "dificuldade" no sistema** — `Subject.cognitiveLoad` nunca
   é lido aqui. Além disso há divisão por `knowledgeGapFactor` sem guarda de zero (o DTO valida
   `[1.0, 5.0]`, mas o construtor de domínio `StudentProfile` aceita qualquer mapa).

#### 2.1.6 Alternativas de correção (simples → rigorosa)

**(a) Normalização da importância — barata, sem mudança teórica.**
Substituir `I_s` por `Î_s = I_s / Σ_k I_k` (simplex) e, opcionalmente, aplicar um limitador de
razão (ex.: `clamp(Î_s, 0.2·média, 5·média)`). Elimina o *winner-take-all* dirigido pelo
payload e torna o objetivo comparável entre editais diferentes. Não corrige a ausência de teto
nem a ausência de tempo. **Esforço: baixo. Ganho: elimina o modo de falha mais grave de escala.**

**(b) Curva de maestria com teto, limitada em `[0,1]`.**
Trocar `ln(1+d)` por `M_s(d) = 1 − exp(−d/τ_s)`, com `τ_s` derivado de `cognitiveLoad` e
`knowledgeGap` (matéria mais densa e mais desconhecida → `τ` maior → satura mais devagar).
Objetivo passa a ser `Σ Î_s · M_s(d_s) ∈ [0,1]`. Ganhos: (i) teto de maestria, teoricamente
correto; (ii) objetivo **normalizado por construção**, o que torna possível somar termos futuros
de Ebbinghaus/Sweller/Ausubel sem dominação; (iii) `cognitiveLoad` passa a ter efeito real no
otimizador pela primeira vez. Continua separável e côncavo — ainda resolvível exatamente, o que
é honesto reconhecer. **Esforço: médio. Ganho: fidelidade + composabilidade.**

**(c) Redefinir o objetivo como pontuação esperada na data da prova.**
`Σ Î_s · M_s(d_s) · R_s(dataProva)`, onde `R_s` é a retenção prevista na data do exame dado o
calendário de estudo. Exige mudar o cromossomo para algo indexado no tempo (alocação por
semana/período, não escalar de dias). Ganhos: (i) o objetivo passa a ser a grandeza que o
produto vende; (ii) **Ebbinghaus se torna expressável** — é o mesmo trabalho; (iii) a
separabilidade se quebra (o valor de um dia depende de quando os outros dias daquela matéria
ocorreram), o que **passa a justificar o uso de um AG**. **Esforço: alto — é uma mudança de
encoding, não de fórmula. É a única alternativa que resolve o problema de fundo.**

---

### T2 — `MinimumDaysConstraint`: multiplicador `0.5`

`ga/fitness/constraint/MinimumDaysConstraint.java` + `FitnessEvaluator.java:55-58`

#### 2.2.1 Fidelidade

**Veredito: baixa — e o conceito pretendido está mal nomeado.** Não corresponde a nenhum dos
três fundamentos; é um piso de cobertura. O Javadoc de `BaselineCalculator` afirma traduzir
*"o conceito abstrato de dificuldade"* em dias, mas §2.1.5 mostra que a quantidade calculada é
exatamente o score de importância. **A restrição garante cobertura das matérias importantes,
não das difíceis.** Nome e documentação descrevem algo que o código não faz.

Merece registro que este termo, apesar de tudo, é o que hoje **impede o plano de degenerar**
(§2.1.4) — ele é acidentalmente o componente mais importante do sistema, por um motivo que
ninguém documentou.

#### 2.2.2 Calibração

| Constante | Valor | Classificação |
|---|---|---|
| `MAX_MINIMUM_DAYS` | `15` | **Número mágico.** Decisão de produto sem justificativa registrada |
| `MIN_REQUIRED_DAYS` | `1` | **Número mágico**, defensável por bom senso |
| Multiplicador de violação | `0.5` | **Número mágico**, e o mais arbitrário do sistema |

Risco operacional associado, não documentado: com `n` matérias, `Σ m_s` pode facilmente exceder
`totalStudyDays`. Com 20 matérias e média de 6 dias mínimos, `Σ m_s = 120`; um cliente pedindo
`totalStudyDays = 100` recebe uma `IllegalArgumentException` de `StudyPlanFactory.java:50`
(roteada para `400 Bad Request` por `GlobalExceptionHandler.java:91-104`, ou para o catch-all
`500` se chegar embrulhada em `CompletionException` pelo caminho assíncrono — não verificado
em execução). O limite superior de 2550 matérias aceito pelo `ExamDto` torna esse cenário
trivialmente alcançável.

#### 2.2.3 Escala

**Problema real, independente da inércia.** O `0.5` é um fator **multiplicativo** aplicado a uma
recompensa **aditiva de magnitude ilimitada**. Duas consequências:

- O custo de violar a restrição é **proporcional à qualidade do próprio plano**. Um plano
  excelente paga uma penalidade absoluta enorme; um plano ruim paga uma penalidade pequena.
  Para uma restrição que se pretende *dura*, isso está invertido.
- É **binária e não-graduada**. Violar o mínimo de uma matéria em 1 dia custa exatamente o
  mesmo que violar todas as matérias em 10 dias. Não há gradiente que oriente os operadores
  na direção da viabilidade — o AG só saberia que está infactível, nunca o quanto.

Além disso, uma restrição "dura" implementada como fator suave **não garante viabilidade da
solução retornada**: `StudyOptimizerService.optimize` devolve `population.getFittest()`
(`StudyOptimizerService.java:102`) sem nenhuma verificação de factibilidade. Hoje isso é
inofensivo porque a população é viável por construção; deixa de ser no instante em que qualquer
operador novo puder gerar um infrator.

#### 2.2.4 Inércia

**Sim — inerte na prática, provado em §1.2.** O ramo `0.5` é inalcançável em produção. O único
teste que o cobre constrói o estado à mão.

#### 2.2.5 Alternativas

**(a) Documentar a inércia e transformar a restrição em invariante verificada.**
Como os operadores já garantem `d_s ≥ m_s`, o papel honesto deste componente é ser uma
*asserção* de invariante (falha rápida em log/métrica se algum dia for violada), não um termo
de fitness. Custo quase zero e remove um componente que hoje dá falsa impressão de proteção.
**Esforço: mínimo. Ganho: honestidade estrutural + detecção de regressão.**

**(b) Substituir por penalidade graduada, se a viabilidade deixar de ser garantida por
construção.** `penalidade = Σ_s max(0, m_s − d_s)` ponderada, subtraída (não multiplicada) da
recompensa normalizada. Dá gradiente, é proporcional à severidade e não escala com a qualidade
do plano. **Esforço: baixo. Ganho: gradiente de viabilidade.**

**(c) Repensar a origem de `m_s`.** Hoje `m_s ∝ importância`, duplicando o objetivo (§2.1.5).
Derivar o piso de um sinal genuinamente independente — cobertura mínima do edital, ou
`cognitiveLoad` (que hoje não é usado em lugar nenhum do otimizador) — faz a restrição
diversificar de fato em vez de reforçar o mesmo ranking. **Esforço: médio. Ganho: elimina a
redundância objetivo↔restrição.**

---

### T3 — `MandatoryReviewConstraint` + `HybridRetentionEngine` (Ebbinghaus)

`ga/fitness/constraint/MandatoryReviewConstraint.java` + `service/calculation/retention/HybridRetentionEngine.java`

#### 2.3.1 Fidelidade — a fórmula em si

**Veredito: média-alta para a fórmula, zero para a integração.**

A fórmula está correta e é honestamente rotulada:
```java
// HybridRetentionEngine.java:31-33
// Forgetting Curve: R = e^(-t/S)
return Math.exp(-((double) daysSinceReview) / state.getCurrentIntervalDays());
```
Isso **é** decaimento exponencial com meia-vida `S·ln 2` — a forma canônica usada para modelar
a curva de Ebbinghaus. Diferente do que ocorre em outros pontos do sistema, aqui o nome
corresponde à matemática. Este é o componente teoricamente mais sólido do repositório.

**Mas há uma inconsistência interna grave entre as duas metades do "híbrido"**, que esta
auditoria identificou e verificou algebricamente (Apêndice C):

- `S` é definido como `currentIntervalDays`, que é o **intervalo de agendamento do SM-2** —
  uma política heurística de revisão, não uma estimativa calibrada de estabilidade de memória.
  Usar um como o outro é uma troca de conceitos.
- Com `MANDATORY_REVIEW_THRESHOLD = 0.85`, a revisão vira obrigatória quando
  `e^{−t/S} ≤ 0.85`, isto é, **`t ≥ 0.1625 · S`**.
- Ou seja: o SM-2 agenda a próxima revisão para daqui a `S` dias, e o limiar de Ebbinghaus
  declara a revisão obrigatória depois de **16% desse intervalo**. Para `S = 6` dias
  (o segundo intervalo padrão do SM-2, `HybridRetentionEngine.java:59`), a revisão é
  obrigatória **no dia seguinte**.

**As duas metades do `HybridRetentionEngine` se contradizem por um fator de ~6×.** Nenhum teste
cobre isso (não há teste algum para esta classe).

Outros defeitos de fidelidade:

- `state == null → calculateRetentionProbability = 0.0` mas `isReviewMandatory = true`
  (linhas 22-24 e 38-40). Coerente entre si, porém combinado com o `RetentionProfile` vazio
  injetado no macro (`StudyOptimizerService.java:127-128`, rotulado *"Dummy"*), **toda matéria
  seria sempre obrigatória** se a guarda de tipo abrisse — a constraint viraria uma constante,
  não um discriminador.
- Divisão por `currentIntervalDays` sem guarda: `t > 0, S = 0` → `exp(−∞) = 0.0` (aceitável);
  `t = 0, S = 0` → `NaN`, e `NaN <= 0.85` é `false`, então a revisão deixa de ser obrigatória
  **silenciosamente**.
- `daysSinceReview < 0 → return 1.0` com o comentário *"Target date is in the past or today"*
  (linha 28) — o comentário está trocado: negativo significa que a data-alvo é **anterior** à
  última revisão.

#### 2.3.2 Calibração

| Constante | Valor | Classificação |
|---|---|---|
| `MANDATORY_REVIEW_THRESHOLD` | `0.85` | **Número mágico**, e alto. Nenhuma fonte citada. É ele que gera a contradição de §2.3.1 |
| `DEFAULT_EASINESS_FACTOR` | `2.5` | **Herdado do SM-2** — é o valor canônico do algoritmo original. Legítimo, mas não citado como tal no código |
| Piso de EF | `1.3` | **Herdado do SM-2**, idem |
| Intervalos `1.0` / `6.0` | | **Herdados do SM-2**, idem |

Aqui a situação é melhor do que no resto do sistema: a maior parte das constantes vem de um
algoritmo publicado. O problema é que **isso não está escrito em lugar nenhum** — um leitor não
consegue distinguir `2.5` (canônico, defensável) de `0.85` (inventado). Registrar a procedência
é barato e muda a defensabilidade do componente.

#### 2.3.3 Escala

Não aplicável hoje (a constraint é binária e inerte). **Mas é o ponto onde a falta de
normalização vai doer primeiro:** `R ∈ [0,1]` por natureza, enquanto `ScoreGainObjective`
devolve valores da ordem de `10^3`–`10^5` (§2.1.4). Promover retenção a objetivo sem normalizar
o T1 antes faria o termo de Ebbinghaus ser aritmeticamente invisível — cerca de 5 ordens de
grandeza abaixo. **A ordem correta de correção é: normalizar T1 primeiro, introduzir retenção
depois.**

#### 2.3.4 Inércia

**Sim — inerte por guarda de tipo (§1.1).** Além disso, seria degenerada mesmo se a guarda
abrisse, por causa do `RetentionProfile` vazio (§2.3.1).

#### 2.3.5 Alternativas

**(a) Corrigir o limiar e documentar a procedência das constantes.** Tornar o limiar coerente
com o intervalo do SM-2 (ex.: revisão obrigatória quando `t ≥ S`, que corresponde a
`R ≈ 0.368`) ou derivar o limiar da meia-vida explicitamente. Anotar no código quais constantes
são canônicas do SM-2 e quais são escolhas próprias. Não altera a arquitetura e remove a
contradição interna. **Esforço: mínimo. Ganho: coerência interna + defensabilidade.**

**(b) Promover retenção de constraint binária a objetivo contínuo normalizado.**
`R_médio = (1/n) Σ_s R_s(dataProva) ∈ [0,1]`, somado ao objetivo já normalizado de T1 com peso
explícito. Vantagens sobre a constraint: é graduado (dá gradiente), é comparável em escala, e
mede o que importa (retenção **na data da prova**, não "houve bloco de revisão?"). Exige que o
`RetentionProfile` real seja hidratado em vez do dummy. **Esforço: médio. Ganho: Ebbinghaus
passa a influenciar de fato a otimização.**

**(c) Encoding indexado no tempo + retenção como parte do objetivo (mesma alternativa (c) de
T1).** É a única via em que "repetição espaçada" significa de fato *espaçamento* — o AG passa a
escolher *quando* revisar, não apenas *se* um bloco de revisão existe. Quebra a separabilidade e
justifica o AG. **Esforço: alto. É a correção de fundo.**

---

### T4 — `DropoutRiskPenalty`

`ga/fitness/penalty/DropoutRiskPenalty.java`

#### 2.4.1 Fidelidade

**Veredito: não se aplica aos três fundamentos — é um quarto conceito.** Modela risco de evasão
/ engajamento. Isso é uma preocupação de produto legítima e o código a rotula honestamente
(*"risk of a student abandoning the platform"*, linha 10). **Não é Sweller**, apesar de ser o
único lugar do pipeline de fitness que toca `cognitiveLoad` (via
`TacticalStudyPlan.calculateTotalCognitiveLoad()`).

Defeito estrutural relevante para um AG: a penalidade é uma **função em degraus com duas
descontinuidades**:

```
risco < 0.4                        → fator = 1.0        (exatamente)
risco ≥ 0.4 e cargaTotal < 20.0    → fator = 1.0        (exatamente)
caso contrário                     → fator = max(0.1, 1 − 0.8·risco)
```

Um cronograma com carga `19.9` recebe fator `1.0`; com `20.1`, recebe `≈0.68`. Entre os dois
não há gradiente algum — apenas platôs e um degrau. Operadores de busca não têm como aprender a
direção que reduz risco; só descobrem o penhasco ao cair nele.

#### 2.4.2 Calibração

Todas as quatro constantes são mágicas, **e duas se contradizem dentro do mesmo método**:

```java
// DropoutRiskPenalty.java:40-41
// Assume an average safe load is ~30.0 for a week (heuristic).
boolean isRecoverySchedule = totalCognitiveLoad < 20.0;
```

O comentário diz `30.0`, o código usa `20.0`. Nenhum dos dois é justificado, e a unidade de
"carga" nunca é definida em lugar nenhum do repositório. Idem para o limiar de risco `0.4`, o
fator `0.8` e o piso `0.1`.

O `DropoutRiskPredictor` a montante acumula mais seis constantes não calibradas
(`0.5`, `0.3`, `0.1`, `0.2`, `50.0`, `1.5`, `0.15` — `DropoutRiskPredictor.java:31-60`),
somadas e truncadas em `[0,1]` sem qualquer validação de que a soma faça sentido dimensional.

#### 2.4.3 Escala

O fator está corretamente confinado a `[0.1, 1.0]` — a forma multiplicativa é adequada para uma
penalidade. O problema é o mesmo de T2: **penalidade multiplicativa sobre recompensa aditiva
ilimitada**, e composição geométrica com as demais penalidades (`NUMERICAL_STABILITY_AUDIT.md`
§2B, "Fitness Saturation"). Com duas penalidades hoje devolvendo `1.0` não morde; morderá assim
que qualquer uma passar a operar.

#### 2.4.4 Inércia

**Sim — inerte por guarda de tipo (§1.1).** E duplamente neutralizada: mesmo com a guarda
aberta, `EngagementProfile.baseline()` (aluno perfeito) é injetado como *"Dummy"* em
`StudyOptimizerService.java:130-131`, forçando `riskScore` baixo.

#### 2.4.5 Alternativas

**(a) Remover do pipeline de fitness enquanto for inerte, ou marcá-la explicitamente como
inativa.** Manter um componente morto registrado como bean dá falsa impressão de que o
otimizador protege contra evasão. Em produto comercial isso é um risco de comunicação, não só
de código. **Esforço: mínimo. Ganho: o pipeline passa a descrever o que realmente faz.**

**(b) Suavizar os degraus.** Substituir os cortes por uma transição contínua (ex.: sigmoide em
torno do limiar de risco, e penalidade proporcional ao excesso de carga sobre o orçamento em
vez de um `if`). Dá gradiente e elimina os platôs. **Esforço: baixo. Ganho: buscabilidade.**

**(c) Separar "risco de evasão" (comportamental, histórico) de "carga cognitiva" (Sweller,
estrutural) em dois componentes distintos com sinais próprios.** Hoje os dois estão fundidos
num único `if` sobre `calculateTotalCognitiveLoad()`, o que impede calibrar um sem mexer no
outro e impede afirmar que Sweller está representado. **Esforço: médio. Ganho: separação de
conceitos + torna Sweller endereçável.**

---

### T5 — `FatigueAndSustainabilityPenalty` (ramo macro)

`ga/fitness/penalty/FatigueAndSustainabilityPenalty.java:37-59`

#### 2.5.1 Fidelidade

**Veredito: baixa como representação de carga cognitiva; média como representação de estado
psicológico.**

O que o termo mede é estresse, fadiga e motivação autorrelatados — construtos afetivos. A
teoria da carga cognitiva de Sweller trata da **demanda de memória de trabalho imposta pelo
material e pela instrução** (carga intrínseca, estranha e germânica). São coisas diferentes;
tratá-las como a mesma seria erro de categoria. **O código não comete esse erro** — não menciona
Sweller. Quem o comete é o `README.md:24`, que atribui Sweller ao
`CognitiveLoadBalancingStrategy`, que roda **depois** do AG e portanto não é fitness.

Registre-se: `Subject.cognitiveLoad` — o único campo do domínio que representa carga
intrínseca, validado em `[1,5]` no DTO — **não é lido por nenhum termo ativo da fitness**.
Sweller está ausente do otimizador, ponto.

#### 2.5.2 Calibração

| Constante | Valor | Classificação |
|---|---|---|
| Divisor `8.0` (×3) | `FatigueAndSustainabilityPenalty.java:43-45` | **Derivável, não documentada.** Para escala `[1,5]`, `8 = 2 × amplitude`, o que mapeia `[1,5] → [1.0, 0.5]`. A intenção é reconstruível; a justificativa de *por que* meia amplitude, não |
| Offset `0.5` na motivação | linha 45 | **Mágico.** Torna a motivação assimétrica em relação aos outros dois fatores, sem explicação |
| Gate `totalDays > 10` | linha 51 | **Mágico.** Abaixo de 11 dias a penalidade é ignorada por completo, sem justificativa |
| Teto de penalidade `0.5` | linha 55 | **Mágico** |

#### 2.5.3 Escala

O fator está em `[0.5, 1.0]`, bem-comportado. O problema não é a escala — é a §2.5.4.

#### 2.5.4 Inércia — provada

**Este é o achado mais forte desta seção.** O fator depende exclusivamente de:
- `StudentState` — idêntico para todos os indivíduos de uma execução;
- `plan.getTotalDays()` — invariante `= D` para todos os indivíduos (§1.2).

Logo `F_fadiga` é **uma constante `c ∈ [0.5, 1.0]` idêntica para todo indivíduo**. O Apêndice B
prova que essa constante não afeta:

- **seleção por torneio** — comparação `>` entre fitness, preservada por escala positiva;
- **pesos de `WeightedAverageCrossover`** — `(p₁·cw₁ + p₂·cw₂)/(cw₁ + cw₂)` é invariante a `c`;
- **detecção de estagnação** — `bestFitnessSoFar` compara a mesma série reescalada;
- **elitismo / `getFittest`** — `max` preservado por escala positiva.

**O termo é matematicamente incapaz de alterar qualquer decisão do AG.** Seu único efeito
observável é o número em `OptimizationResult.fitness` devolvido ao cliente — um valor sem
unidade que nenhum consumidor pode interpretar.

#### 2.5.5 Alternativas

**(a) Reconhecer a inércia e decidir conscientemente.** Ou o termo é removido do pipeline, ou é
reformulado para depender do indivíduo. Mantê-lo como está é pagar custo de avaliação por zero
efeito, e sustentar a impressão de que "sustentabilidade" pesa na otimização.
**Esforço: mínimo. Ganho: clareza.**

**(b) Tornar o termo dependente do indivíduo.** Enquanto o cromossomo for `Subject → dias` sem
tempo, a única variação possível entre indivíduos é a *distribuição* entre matérias. Um termo
que penalize concentração — por exemplo, carga ponderada por `cognitiveLoad`,
`Σ_s d_s · cognitiveLoad_s`, ou uma medida de dispersão da alocação — **varia entre indivíduos**
e faz `cognitiveLoad` finalmente influenciar o otimizador. É a aproximação mais próxima de
Sweller possível sem mudar o encoding, e deve ser nomeada como aproximação, não como "teoria da
carga cognitiva". **Esforço: baixo-médio. Ganho: Sweller passa de ausente a aproximado, com
rótulo honesto.**

**(c) Orçamento diário real de carga, com encoding temporal.** Sweller é uma restrição sobre
uma *janela curta* (a sessão / o dia), não sobre um agregado do plano. Representá-lo fielmente
exige saber o que acontece em cada dia — ou seja, o mesmo encoding indexado no tempo das
alternativas (c) de T1 e T3. Só então "não exceder o orçamento diário" pode ser restrição de
otimização em vez de poda posterior (que hoje **descarta silenciosamente** blocos já planejados
em `CognitiveLoadBalancingStrategy.pruneToFitDailyLoadLimit`). **Esforço: alto. É a correção de
fundo, e é a mesma obra das outras duas.**

---

## 3. Questões transversais

### 3.1 Normalização: ausente em todos os níveis

Não há normalização em nenhum ponto do pipeline:

| Nível | Situação |
|---|---|
| Entre objetivos | Não aplicável hoje (só existe um), mas nenhuma infraestrutura para quando existirem |
| Dentro do objetivo, entre matérias | **Ausente** — razão de até `250 000 : 1` (§2.1.4) |
| Entre recompensa e penalidades | **Incomparáveis por construção** — recompensa aditiva ilimitada × penalidades multiplicativas em `[0,1]` |
| Entre execuções / editais diferentes | **Ausente** — o valor de fitness não é comparável entre dois editais, o que inviabiliza qualquer benchmark ou métrica de qualidade em produção |

O último ponto tem consequência prática imediata: **é impossível instrumentar "a qualidade dos
planos melhorou" sem normalização**, porque a fitness não tem unidade estável. Isso bloqueia
qualquer avaliação empírica futura — inclusive a comparação contra baselines mais simples que o
`00-diagnostico.md` §5.2 registrou como inexistente.

### 3.2 Mistura de aritmética aditiva e multiplicativa

`reward × constraintFactor × Π penalties` mistura dois regimes:

- Um **peso** de objetivo (`getWeight()`) e um **fator** de penalidade não são grandezas
  comparáveis; não há como raciocinar sobre "quanto vale a fadiga em relação ao ganho de
  pontuação" porque uma é aditiva e a outra é proporcional.
- Penalidades multiplicativas **compõem geometricamente**: `0.5 × 0.5 × 0.5 = 0.125`. Com
  penalidades reais ativas, a fitness colapsa em direção a zero e o AG perde gradiente — o modo
  de falha que `NUMERICAL_STABILITY_AUDIT.md` §2B nomeia "Fitness Saturation" e que continua
  não endereçado.
- Nada garante `fitness > 0`. Com todas as matérias órfãs (`importância = 0`), a fitness é
  exatamente `0` para toda a população; `WeightedAverageCrossover` cai no ramo
  `totalFitness == 0` (linha 53) e produz filhos com **todos os genes zerados**, que o reparo
  então redistribui aleatoriamente. Um crossover de refinamento degenera em reinicialização
  aleatória, silenciosamente.

### 3.3 A fitness não tem dimensão temporal — e por isso Ebbinghaus é inexpressável

O cromossomo é `Map<Subject, Integer>`: quantidade, sem posição no calendário e sem ordem
(`00-diagnostico.md` §1.2). Consequências que nenhum ajuste de fórmula resolve:

- **Ebbinghaus** é `R(t) = e^{−t/S}`, função do tempo desde o estudo. Sem coordenada temporal,
  `t` não existe no genótipo. Nenhum termo adicionado a `Σ f(d_s)·I_s` pode representar
  esquecimento.
- **Ausubel / pré-requisitos** é uma restrição de **ordem** (o pré-requisito precede o
  dependente). Um mapa não tem ordem. Inexpressável por construção **neste cromossomo**.
- **Sweller** é uma restrição sobre uma **janela curta** (o dia). O plano macro não sabe o que
  acontece em cada dia. Só aproximável por agregados.

> **Precisão acrescentada em 2026-08-31 (etapa 07, gap G9).** As três afirmações acima valem para o
> cromossomo **macro**, que é o único que produção executa — e não para o repositório inteiro. Existe
> um segundo cromossomo, `domain/tactical/TacticalStudyPlan : Map<TimeSlot, TacticalStudyBlock>`,
> com posição temporal completa, já aceito pelo `FitnessEvaluator` sem mudança de assinatura
> (`00-diagnostico.md` §1.9 o registra como andaime não cabeado). Ele não é produzido por nenhum AG,
> então nada aqui muda na prática — mas a frase "inexpressável por construção", lida sem esse
> qualificador, foi usada em etapas seguintes para justificar decisões, e essa cadeia precisou ser
> corrigida em [`06-decisao-ausubel.md`](./06-decisao-ausubel.md) §2.3–§2.4.

Esta é a razão pela qual §0 afirma que corrigir a fidelidade teórica e justificar o AG são o
mesmo trabalho: os três fundamentos exigem uma coordenada temporal, e é exatamente ela que
introduz o acoplamento que torna o problema difícil o bastante para merecer um AG.

### 3.4 O AG está aproximando um problema com solução exata

Dado §1.3, o problema efetivo é maximizar uma soma separável de funções côncavas sob orçamento
linear com limites inferiores. Isso admite:

- **Solução em forma fechada** por KKT / water-filling: `d_s = max(m_s, I_s/λ − 1)`, com `λ`
  fixado pelo orçamento (derivação e exemplo numérico no Apêndice A);
- **Solução ótima inteira gulosa** em `O(D log n)`: distribuir cada dia à matéria de maior ganho
  marginal `I_s·[ln(2+d_s) − ln(1+d_s)]`, ótima por concavidade e separabilidade.

Com `D ≤ 365`, isso são no máximo 365 operações de heap. A configuração máxima do AG
(500 indivíduos × 1000 gerações = 500 000 avaliações, cada uma `O(n)`) produz uma **aproximação
estocástica, sem garantia de otimalidade e não reprodutível** (`00-diagnostico.md` §7.4) do
mesmo resultado.

Isto não é argumento contra o AG. É argumento de que **a fitness atual não sustenta a escolha
arquitetural**, e de que qualquer comparação empírica futura contra "uma alternativa mais
simples" — que o `00-diagnostico.md` §5.2 registrou como inexistente — provavelmente mostraria
o baseline guloso empatando ou ganhando. Convém saber disso antes de a comparação ser feita por
terceiros.

---

## 4. Os três fundamentos: veredito de fidelidade

| Fundamento | Onde o produto afirma estar | Onde de fato está | Influencia a fitness? | Veredito |
|---|---|---|---|---|
| **Ebbinghaus** (esquecimento / repetição espaçada) | `README.md:23` → `ReviewFocusedStrategy` | Fórmula correta em `HybridRetentionEngine` (`R = e^{−t/S}`), consumida só por termo inerte; heurística de revisão em `ReviewFocusedStrategy`, que roda **após** o AG | **Não** | Fórmula fiel, integração inexistente. O limiar `0.85` contradiz o intervalo SM-2 em ~6× (Apêndice C) |
| **Sweller** (carga cognitiva) | `README.md:24` → `CognitiveLoadBalancingStrategy` | `CognitiveLoadCalculator` + poda em `CognitiveLoadBalancingStrategy`, ambos **após** o AG. `Subject.cognitiveLoad` não é lido por nenhum termo ativo | **Não** | Ausente do otimizador. O que existe é poda posterior que descarta blocos já planejados |
| **Ausubel** (pré-requisitos / aprendizagem significativa) | `README.md:22` → `knowledgeGaps` | **Nenhuma implementação.** Zero ocorrências de pré-requisito, sequenciamento ou subsunçor no código | **Não** | Ausente. E inexpressável no encoding atual (§3.3) |

**Observação sobre a leitura de Ausubel adotada no README.** Atribuir Ausubel ao multiplicador
`knowledgeGapFactor` é uma reinterpretação que merece escrutínio: a regra implementada é
"quanto maior a lacuna, maior a prioridade", monotônica e sem noção de ancoragem. A tese de
Ausubel é que material novo precisa de subsunçores *já presentes* para gerar aprendizagem
significativa em vez de mecânica — o que implica que uma matéria cujos pré-requisitos estão
ausentes deveria ser **adiada até que o pré-requisito seja coberto**, não promovida. O
multiplicador atual não distingue "lacuna em matéria cujos pré-requisitos estão dominados" de
"lacuna em matéria cujos pré-requisitos também faltam", e nas duas situações reage igual. É uma
heurística de triagem defensável por si; **o que não é defensável é chamá-la de Ausubel**.

---

## 5. Tabela resumo

| Termo | Conceito pretendido | Fidelidade | Problema principal | Correção recomendada |
|---|---|---|---|---|
| **`ScoreGainObjective`** — fator `importância_s` | Peso de edital | **Alta** | Escala não normalizada: razão de até `250 000 : 1` entre matérias, dirigida pelo payload do cliente → *winner-take-all* | Normalizar para o simplex (`Î_s = I_s/ΣI_k`) com limitador de razão — §2.1.6(a) |
| **`ScoreGainObjective`** — fator `ln(1 + dias)` | Retornos decrescentes do estudo | **Baixa** | Ilimitada superiormente (sem teto de maestria); mede dias alocados, não desempenho; **sem coordenada temporal** | Curva com teto normalizada em `[0,1]`: `1 − e^{−d/τ_s}` — §2.1.6(b). Correção de fundo: objetivo = pontuação esperada na data da prova — §2.1.6(c) |
| **`MinimumDaysConstraint`** (×0.5) | Piso de cobertura por matéria | **Baixa** | **Inerte na prática** — nenhum operador consegue violá-la (§1.2). Quando disparasse: binária, não-graduada, e o custo escala com a qualidade do plano | Rebaixar a invariante verificada com métrica de regressão — §2.2.5(a); penalidade aditiva graduada se a viabilidade deixar de ser garantida — §2.2.5(b) |
| **`MandatoryReviewConstraint`** + `HybridRetentionEngine` | Ebbinghaus / repetição espaçada | **Média-alta** (fórmula) / **nula** (integração) | **Inerte por guarda de tipo.** Limiar `0.85` declara revisão obrigatória após 16% do intervalo que o próprio SM-2 acabou de agendar — contradição interna de ~6× | Corrigir o limiar e documentar quais constantes são canônicas do SM-2 — §2.3.5(a); promover retenção a objetivo contínuo normalizado — §2.3.5(b) |
| **`DropoutRiskPenalty`** | Risco de evasão (4º conceito, não é Sweller) | **N/A** aos três fundamentos | **Inerte por guarda de tipo**, e duplamente neutralizada pelo `EngagementProfile` dummy. Função em degraus, sem gradiente. Comentário diz `30.0`, código usa `20.0` | Remover do pipeline ou marcar como inativa enquanto for inerte — §2.4.5(a); suavizar os degraus — §2.4.5(b) |
| **`FatigueAndSustainabilityPenalty`** (ramo macro) | Sustentabilidade psicológica (**não** é Sweller) | **Baixa** como Sweller / **média** como estado afetivo | **Provadamente inerte para a ordenação** — constante idêntica para todos os indivíduos; não afeta torneio, crossover nem estagnação (Apêndice B) | Tornar o termo dependente do indivíduo, penalizando concentração ponderada por `cognitiveLoad` — §2.5.5(b). Correção de fundo: orçamento diário com encoding temporal — §2.5.5(c) |
| **Mecanismo de pesos** (`getWeight()`) | Soma ponderada multiobjetivo | **N/A** | **Inerte**: um único objetivo, peso `1.0`. O pipeline documenta um MOOP que não existe | Só passa a ter sentido depois que houver ≥ 2 objetivos normalizados — pré-requisito de §2.1.6(b) |
| **Pipeline como um todo** | MOOP por soma ponderada | **Baixa** | Mistura recompensa aditiva ilimitada com penalidades multiplicativas; sem normalização; sem piso de fitness; fitness não comparável entre editais (impede benchmark) | Normalizar T1 **antes** de introduzir qualquer termo novo — §3.1, §3.2 |

### Ordem sugerida de ataque

A dependência entre as correções não é arbitrária. Introduzir Ebbinghaus ou Sweller antes de
normalizar T1 faria os termos novos serem aritmeticamente invisíveis (§2.3.3):

1. **Normalizar `ScoreGainObjective`** (§2.1.6(a)) — desbloqueia tudo o mais.
2. **Resolver os quatro componentes inertes** — remover, reativar ou rebaixar a invariante,
   conscientemente, para que o pipeline descreva o que faz.
3. **Curva com teto normalizada em `[0,1]`** (§2.1.6(b)) — torna o objetivo composável e faz
   `cognitiveLoad` influenciar o otimizador pela primeira vez.
4. **Encoding indexado no tempo** — única via que torna Ebbinghaus, Sweller e Ausubel
   expressáveis (§3.3), e a única que justifica o AG (§3.4).

Os passos 1–3 são incrementais sobre a arquitetura atual. O passo 4 é uma mudança de encoding e
deve ser decidido como tal.

---

## Apêndice A — O problema efetivo tem solução exata

Maximizar `Σ_s I_s ln(1 + d_s)` sujeito a `Σ_s d_s = D`, `d_s ≥ m_s`.

**Contínuo (KKT / water-filling).** Lagrangiano `L = Σ I_s ln(1+d_s) − λ(Σ d_s − D)`.
Estacionaridade: `I_s/(1 + d_s) = λ` → `d_s = I_s/λ − 1`, com `d_s` fixado em `m_s` sempre que
`I_s/(1 + m_s) < λ`. `λ` é determinado pelo fechamento do orçamento.

**Inteiro (guloso).** Distribuir cada um dos `D − Σ m_s` dias livres à matéria de maior ganho
marginal `Δ_s(d) = I_s·[ln(2+d) − ln(1+d)]`. Como `Δ_s` é decrescente em `d` e o objetivo é
separável, o guloso é **exato** (argumento de troca padrão). Custo: `O(D log n)` com heap.

**Exemplo numérico.** `I = (100, 10, 1)`, `D = 30`, `m = (1,1,1)`:

- Tentativa sem restrição ativa: `λ = 111/33 = 3.364` → `d = (28.7, 1.97, −0.70)`. `d₃` viola.
- Fixando `d₃ = 1`, orçamento restante `29`: `λ = 110/31 = 3.548` → `d = (27.2, 1.82, 1)`.
- Arredondando com fechamento de orçamento: **`d* = (27, 2, 1)`**.

Note o *winner-take-all* com razão de importância de apenas `100 : 1`. Com as razões que a API
aceita (até `250 000 : 1`, §2.1.4), a matéria dominante absorve tudo que não estiver travado
pelo piso `m_s`.

## Apêndice B — Prova de que `F_fadiga` não altera nenhuma decisão

Seja `c = F_fadiga ∈ [0.5, 1.0]`, constante e idêntica para todo indivíduo de uma execução
(depende só de `StudentState`, fixo por requisição, e de `getTotalDays() = D`, invariante por
§1.2). Seja `f(x)` a fitness sem o fator e `F(x) = c·f(x)`.

1. **Torneio** (`TournamentSelection.java:70`): `F(a) > F(b) ⟺ c·f(a) > c·f(b) ⟺ f(a) > f(b)`,
   pois `c > 0`. Ordem preservada.
2. **Elitismo / `getFittest`** (`Population.java:61-63`): `argmax` invariante a escala positiva.
3. **Pesos de `WeightedAverageCrossover`** (linhas 50-59):
   `(p₁·cf₁ + p₂·cf₂)/(cf₁ + cf₂) = (p₁·f₁ + p₂·f₂)/(f₁ + f₂)`. O fator cancela exatamente.
4. **Estagnação** (`GeneticAlgorithm.java:115`): compara `F` entre gerações; como `c` é o mesmo
   em todas as gerações, é a mesma série reescalada. Detecção de melhora idêntica.

Logo `c` só altera o valor numérico devolvido em `OptimizationResult.fitness`. ∎

## Apêndice C — Contradição interna do `HybridRetentionEngine`

Revisão obrigatória quando `R ≤ 0.85`, com `R = e^{−t/S}`:

```
e^{−t/S} ≤ 0.85  ⟺  −t/S ≤ ln(0.85) = −0.1625  ⟺  t ≥ 0.1625 · S
```

O SM-2 (`processReview`, linhas 54-62) agenda a próxima revisão para `t = S`. Portanto o limiar
de Ebbinghaus dispara em **16,25% do intervalo agendado pelo SM-2**:

| `S` (intervalo SM-2, dias) | Revisão obrigatória a partir de | Intervalo agendado pelo SM-2 |
|---|---|---|
| 1 | 0,16 dia | 1 dia |
| 6 | 0,98 dia | 6 dias |
| 15 | 2,4 dias | 15 dias |
| 38 (`6 × 2.5²`) | 6,2 dias | 38 dias |

As duas metades do "híbrido" discordam por um fator de `1/0.1625 ≈ 6,15×` em todo o domínio.
Nenhum teste cobre esta classe.

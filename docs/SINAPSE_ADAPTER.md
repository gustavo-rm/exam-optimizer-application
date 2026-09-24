# Adaptador SINAPSE (EOA-5)

Como o algoritmo genético responde `POST /plans`, o que ele deixa de modelar, e por quê.

Este documento registra **decisões e suposições**, não o funcionamento — o funcionamento está no
Javadoc das classes de `sinapse/`, que é onde quem mexe no código já está olhando. O que não cabe em
Javadoc é a parte que atravessa várias classes e a parte que não é decisão de engenharia.

---

## 1. Os dois motores

`POST /plans` tem duas implementações por trás de `plan.PlanEngine`:

| id | Classe | Etapa |
|---|---|---|
| `greedy-baseline` | `baseline.GreedyBaselineEngine` | EOA-2 |
| `ga` | `sinapse.GeneticPlanEngine` | EOA-5 |

O motor é escolhido **por requisição**, em `algorithmParams.engine`. `algorithmParams` é
`Map<String, Object>` no contrato — conferido em `PlanRequest`, não suposto —, então a escolha não
inventou campo nenhum. Sem a chave, roda o motor de `plan.engine.default`
(`application-baseline-core.properties`), hoje `greedy-baseline`.

Por requisição e não por implantação porque **as duas condições do experimento precisam rodar contra
o mesmo build**: comparar dois deploys mede os deploys também. Um motor desconhecido é recusado com
`422` listando os que existem, e não trocado pelo padrão — rodar a condição A enquanto quem chamou
registrou a condição B produz medições erradas que nada a jusante consegue detectar.

A resposta **sempre** declara qual motor rodou, em `fitness.engine`. O `PlanEngineSelector` estampa a
chave depois de o motor responder, então um motor não consegue rotular a própria saída de forma
errada, nem esquecer de rotulá-la.

Ambos os motores satisfazem as mesmas invariantes de saída: as oito que `RestSinapseCore.validated`
aplica, as quatro que ele não verifica (horizonte, janela de disponibilidade, não sobreposição,
`topicId` conhecido) e a contiguidade de `sequenceIndex`. Um único teste parametrizado,
`PlanEngineInvariantTest`, exige isso de todo motor registrado — um motor novo aparece na fonte de
argumentos e herda a suíte inteira.

---

## 2. Estado psicológico: por que dois termos saem da fitness

`StudentProfileDto` exige `stressLevel`, `fatigueLevel`, `motivationLevel` e `chronotype`. **A
plataforma não coleta nenhum deles.**

Coletar estado psicológico autodeclarado vinculado à identidade é decisão sob a LGPD — com menores no
piloto de ensino médio — e **não é decisão de engenharia**. Preencher os campos com constantes
neutras para "destravar" o caminho seria tomar essa decisão por omissão, e do pior jeito: os termos
continuariam no somatório contribuindo um valor constante, e a fitness pareceria considerar fadiga e
risco de evasão sem considerar.

Portanto, no caminho SINAPSE `FatigueAndSustainabilityPenalty` e `DropoutRiskPenalty` **não fazem
parte da composição**.

### 2.1. Uma correção ao enunciado: elas não estavam no somatório

Os dois termos são `FitnessPenalty` — **fatores multiplicativos aplicados depois do `clamp`**
(`FitnessEvaluator:117-120`), não somandos ponderados. Consequência direta:

| | Antes | Depois | Renormalização |
|---|---|---|---|
| `syllabusMastery` (O1) | 0,50 | 0,50 | — |
| `retention` (O2) | 0,30 | 0,30 | — |
| `cognitiveLoad` (O4) | 0,20 | 0,20 | — |
| **soma** | **1,00** | **1,00** | **nenhuma** |
| `dropout-risk` | ×fator | removido | não é somando |
| `fatigue` | ×fator | removido | não é somando |

Os pesos dos objetivos já somam 1, e remover fatores multiplicativos não mexe em nenhum deles.
**Não há renormalização a documentar aqui**, e inventar uma descreveria mal a mudança.

Remover não é cosmético, apesar disso. No caminho SINAPSE o plano **é** um `TacticalStudyPlan`, então
as duas penalidades passariam o `instanceof` e devolveriam `1.0` de qualquer forma —
`FatigueAndSustainabilityPenalty:33` com estado nulo, `DropoutRiskPenalty:29` com perfil de
engajamento nulo. Seriam inertes e indistinguíveis de ativas, e a fitness publicada nomearia dois
termos que a função nunca aplicou.

### 2.2. Onde a renormalização realmente se coloca: o orçamento de O4

`CognitiveLoadObjective` (0,20) pontua o plano contra `maxDailyCognitiveLoad`, e o orçamento do
caminho de concurso tem três fatores, dos quais **dois não têm fonte no `PlanRequest`**:

| Fator | Fonte no concurso | Disponível no SINAPSE |
|---|---|---|
| capacidade base | horas semanais | **sim**, de `availability[]` |
| pressão | dificuldade média | **sim**, de `effortTier` |
| fadiga | lacuna autoavaliada + estado psicológico | **não** |

`CognitiveLoadCalculator` não falha na ausência: a lacuna média cai para `3.0`
(`CognitiveLoadCalculator:61-63`), o que colapsa o fator numa constante `0.95`, e os três
modificadores de estado são descartados quando o estado é nulo (`:69`). Reaproveitá-lo produziria um
orçamento com constante embutida e sem sinal de que algo faltou.

**Decisão: O4 fica, com o fator removido em vez de defaultado.** `SinapseLoadBudget` usa os dois
fatores que têm dado. O termo faz uma afirmação mais estreita honestamente, em vez de uma mais ampla
falsamente.

### 2.3. A alternativa, registrada para não ser re-derivada

Se O4 também saísse, os dois objetivos restantes renormalizariam:

| | Antes | Depois |
|---|---|---|
| `syllabusMastery` | 0,50 | **0,625** |
| `retention` | 0,30 | **0,375** |
| **soma** | 0,80 | **1,000** |

O custo dessa alternativa é que nada no caminho SINAPSE limitaria a carga diária: um plano poderia
concentrar o conteúdo mais difícil nas primeiras janelas e não pontuar pior por isso. A troca é entre
um teto de carga construído com dois terços dos fatores e nenhum teto.

### 2.4. A parte que quase passou batida: a lacuna não fica no orçamento

A razão de a regra deste pacote ser **estrutural** e não sobre valores. A lacuna de conhecimento não
alimenta só o orçamento de O4 — ela alimenta **o lado da demanda**:

| Consumidor | Alcança | Lado |
|---|---|---|
| `ImportanceCalculator:42` | `importance = baseImportance × lacuna` → peso de O1, e de O2 via `temper` | **demanda** |
| `BaselineCalculator:50,80-84` | `minimumDaysPerItem` → `MinimumDaysConstraint` (λ=0,50) e o piso da população inicial | **demanda** |
| `CognitiveLoadCalculator:60-66` | `maxDailyCognitiveLoad` | orçamento |

Em `BaselineCalculator:81-84` a lacuna *parece* cancelar — `(importância / lacuna) × lacuna` — e o
Javadoc da classe diz que a dificuldade percebida "se reduz algebricamente ao escore de importância".
Mas a `importância` **já carrega a lacuna**, então `dificuldadePercebida = baseImportance × lacuna`, e
`normalizeAndScale` divide pelo máximo: a lacuna sobrevive como peso *relativo* no piso. Ela não
cancela.

Os modificadores de estado, ao contrário, **não** chegam à demanda: `grep studentState()` devolve
exatamente dois consumidores — as duas penalidades desativadas — mais o orçamento. `chronotype` não
tem consumidor algum na fitness.

Conclusão operacional: **uma única constante inventada reponderaria os dois objetivos mais pesados e a
restrição mais pesada de uma só vez**, e cada um deles continuaria reportando um número. Por isso o
adaptador não constrói `StudentProfile`, `StudentState` nem `Exam`, e não chama as três calculadoras —
e por isso `SinapseAdapterIsolationTest` lê os `import` do pacote em vez de conferir se algum campo
vale 3,0. Proibir o alcance é forte; proibir um valor passaria com 2,9.

### 2.5. O termo de carga: renomeado, re-escalado e medido

O termo fica — mas fica com outro nome, outras constantes e uma medição que justifica a decisão.

**Renomeado para `dailyLoadBudget`.** Perdeu dois dos três insumos, então parou de ser o que o nome
antigo descrevia. `cognitiveLoad` carrega a citação de Sweller, cujo construto é sobre a carga
*dentro de um episódio de aprendizagem*; o que resta aqui é um teto de sustentabilidade calculado de
disponibilidade e dificuldade média. Uma execução arquivada tem de ser autodescritiva um ano depois,
e quem ler `cognitiveLoad` num `FitnessBreakdown` vai supor insumos que nunca estiveram lá. O nome
novo aparece no relatório; o antigo não sobrevive em chave nenhuma (`SinapseFitnessTermsTest`).

**Re-escalado — e aqui foram encontrados três defeitos, não um.** A primeira versão desta classe
copiou as constantes do caminho de concurso, e cada uma estava numa escala errada:

| # | Defeito | Efeito medido | Correção |
|---|---|---|---|
| 1 | `AVERAGE_COGNITIVE_LOAD = 3,0` (ponto médio de 1..5) contra demanda em 1..4 | teto 23% acima da demanda **no ponto médio da própria escala** | ponto médio real: **2,5** |
| 2 | interpolação de pressão dividindo por 4 (vão de 1..5) | pressão errada em toda a faixa | vão real: **3,0** |
| 3 | demanda multiplica por `ceil(horas)` **inteiro**, teto multiplicava pelas horas **não arredondadas** | a 2,14 h/dia a demanda era computada contra 3 e o teto contra 2,14 — **inflação de 40%** | os dois usam o mesmo inteiro |
| 4 | `MINIMUM_BUDGET = 5` (um valor de carga em 1..5) | o teto ficou **preso no piso em 7 de 7** instâncias medidas, e o termo não podia morder | piso **1**, o menor não degenerado em 1..4 |

Os defeitos 3 e 4 juntos produziam o sintoma mais enganoso: o termo mordia **mais** com calendário
folgado e **menos** com calendário apertado — o inverso do que um teto de carga deve fazer. Era o
arredondamento sendo medido, não a carga.

Decisão declarada: **usar o ponto médio e o vão da escala real (2,5 e 3,0), não remapear as faixas
para 1..5.** Quatro posições ordinais não cabem em cinco sem pular uma, e pular uma inventa uma
distinção que o curador nunca fez — o mesmo argumento que mantém o mapa de `effortTier` uma
identidade que preserva ordem. Corrigir constantes é aritmética; esticar as faixas seria modelagem.

**Medido.** Com demanda e teto na mesma escala, as horas se cancelam e o termo passa a medir o que
deve: se a dificuldade média *ponderada pela alocação* excede a referência. Varrendo a mistura de
dificuldade do edital sobre dez dias de três horas:

| Instância | teto | mordeu | excesso |
|---|---|---|---|
| todos SHORT | 6 | não | 0,0 |
| 3 SHORT 1 LONG | 5 | não | 0,0 |
| 2 SHORT 2 STANDARD | 5 | não | 0,0 |
| mistura plena | 5 | **sim** | 0,0857 |
| 3 EXTENDED 1 LONG | 5 | **sim** | 0,3778 |
| 2 LONG 2 EXTENDED | 5 | **sim** | 0,4286 |
| todos EXTENDED | 5 | **sim** | 0,6000 |

**Taxa de "o teto foi vinculante": 4/7 = 57%**, com o excesso crescendo monotonicamente com a
dificuldade. O termo discrimina: não é decorativo (taxa > 0) e não é constante (taxa < 100%).
`DailyLoadBudgetBindingRateTest` reproduz a tabela e trava as duas pontas.

Cada plano reporta sua própria mordida em `fitness`: `objective.dailyLoadBudget.binding`,
`.excess-ratio` e `.ceiling`. É o que transforma "manter ou remover o termo" em medição em vez de
opinião.

**Desligável por configuração.** `plan.fitness.sinapse.daily-load-budget=false` remove o termo e
**renormaliza automaticamente** 0,50/0,30 para 0,625/0,375. A renormalização tem de ser automática:
sem ela os pesos somariam 0,80, o `FitnessEvaluator` recusaria arrancar, e o contorno óbvio — deixar
como está — reescalaria em silêncio todo valor de fitness que o sistema publica, tornando execuções
anteriores incomparáveis. Com ela, "sem teto de carga" é condição executável e comparável.

### 2.6. O piso de dias mudou de fonte, e os dois caminhos deixaram de ser comparáveis

```
minutesPerStudyDay = minutos disponíveis totais / dias DISTINTOS com disponibilidade
piso(tópico)       = max(1, ceil(estimatedMinutes / minutesPerStudyDay))
```

Os dois insumos são dados reais da plataforma. **Nenhuma constante de "minutos por dia de estudo"
foi introduzida** — este caminho consegue o piso sem nenhuma, e trocar um insumo autodeclarado por
uma suposição nova seria exatamente a troca que este adaptador existe para recusar.

O denominador são **dias que têm disponibilidade**, não dias do horizonte: a pergunta que o piso
responde é "quantas sentadas este tópico leva?", e um dia em que o aluno não declarou nada não é uma
sentada. Dividir pelo horizonte encurtaria o dia de estudo aparente e inflaria todo piso de quem
estuda três noites por semana.

A versão anterior era um `1` fixo por tópico. Era defensável como definição — um tópico em escopo
precisa de ao menos uma sessão — e jogava fora `estimatedMinutes`, que é o único número calibrado de
um tópico. Um tópico de 180 minutos e um de 30 não precisam do mesmo esforço mínimo, e o payload diz
isso.

> **Consequência que precisa estar registrada: planos dos dois caminhos NÃO são comparáveis entre
> si.** O piso alimenta `MinimumDaysConstraint` — subtraída a λ 0,50, o maior peso isolado do
> agregado — e o piso da população inicial. O caminho de concurso deriva o dele da importância
> escalada por lacuna, normalizada pelo próprio máximo e limitada a 15 dias. São **grandezas
> diferentes em escalas diferentes**, então um valor de fitness de um caminho não se compara com um
> do outro. Só planos do mesmo caminho se comparam — que é o que o experimento compara.

---

## 3. Suposições de projeto

Cada uma resolve uma ambiguidade que o contrato deixa aberta, e cada uma poderia ter sido resolvida de
outro jeito defensável. Todas travadas em `SinapseAssumptionsTest`.

### 3.1. `effortTier` → `difficultyBand`

```
SHORT -> 1     STANDARD -> 2     LONG -> 3     EXTENDED -> 4
```

Preserva a ordem e nada mais: tanto a faixa quanto a banda são escalas ordinais, então o mapa é a
identidade entre duas nomenclaturas das mesmas quatro posições. A banda 5 existe em `PlanningItem` e é
inalcançável por este caminho, porque a escala da plataforma tem quatro posições e inventar uma quinta
alegaria uma distinção que o curador nunca fez.

**Faixa fora do conjunto fechado é recusada com `422` nomeando o valor recebido.** `effortTier` é
`String` no contrato, deliberadamente: a plataforma envia `effortTier().name()` e não declara enum,
então um valor inválido chega até aqui **sem erro de desserialização**. Escolher uma banda padrão
alimentaria o termo de maior peso da fitness (`SYLLABUS_MASTERY`, 0,50) com um número que ninguém
escolheu, e o plano pareceria tão confiável quanto um correto.

O motor guloso **não** valida a faixa, e a assimetria é deliberada: ele aloca por `estimatedMinutes` e
nunca lê a banda, então recusar a requisição por um campo de que a resposta não depende seria a troca
errada. Aqui a resposta depende.

### 3.2. Histórico ausente da janela de noventa dias

O histórico da plataforma cobre **noventa dias**. Um tópico ausente de `history` significa uma de duas
coisas e o payload não as distingue: nunca estudado, ou estudado há mais de noventa dias.

**Suposição: ausência é tratada como terreno novo** — nenhum estado de retenção, o que
`HybridRetentionEngine.isReviewMandatory` responde com `true`.

A alternativa é supor "há mais de noventa dias", e isso exige **inventar um `lastStudiedAt`**. A
probabilidade de retenção é `exp(-diasDesde / estabilidade)`: uma data fabricada não é uma imprecisão
pequena ali, é o insumo mais sensível do termo escolhido por nós. Tratar como terreno novo não inventa
nada e erra na direção mais barata — reensinar material que o aluno já sabe custa tempo; pular
material que ele nunca viu custa a prova. **A assimetria é o argumento**, não que "nunca" seja mais
provável.

Um tópico presente com `lastStudiedAt` nulo é o mesmo caso: o contrato distingue os dois estados, mas
ambos chegam aqui sem data, e eles não diferem no que este lado pode calcular.

O estado é reconstruído replayando as notas por `RetentionAlgorithm.processReview`, a recorrência que
a camada tática já usa. O replay é exato apesar de haver só uma data: `processReview` usa
`reviewDate` apenas como a data que armazena.

### 3.3. `RecallRating` → nota SM-2

```
AGAIN -> 1     HARD -> 2     GOOD -> 4     EASY -> 5
```

Escolhido para a recorrência manter a própria semântica, não para espalhar os quatro valores
igualmente. `processReview` trata `nota >= 3` como recall bem-sucedido, o que põe `AGAIN` e `HARD` do
lado que falha. A atualização de facilidade é `EF + (0.1 - (5-q)(0.08 + (5-q)·0.02))`, exatamente zero
em `q = 4` e positiva só em `q = 5` — então `GOOD` deixa o tópico tão fácil quanto estava e só `EASY`
o torna mais fácil. Mapear `EASY` para 4 tornaria um recall fácil indistinguível de um comum.

### 3.4. Tópico cuja disciplina não aparece em `goals`

**É planejado na prioridade 1**, a mínima que o contrato documenta — nem excluído nem recusado.

Zero deixaria o tópico agendado e **invisível**: ele receberia sua sessão de estudo, porque todo tópico
em escopo recebe uma, mas nenhum termo da fitness registraria se foi aprendido. Recusar rejeitaria um
payload que o contrato permite, já que nada nele diz que a disciplina de todo tópico precisa aparecer
em `goals`. Prioridade 1 é um piso sobre um campo que existe, não um substituto para um campo que não
existe.

Duas metas para a mesma disciplina colapsam na **maior** prioridade: o contrato não envia identificador
de meta, então duas entradas são indistinguíveis, e o máximo nunca rebaixa o que o aluno expressou.

### 3.5. `availability[]` e fuso

Os instantes vêm resolvidos no fuso da conta e **não são reinterpretados**. `AvailabilityWindow` é
declarado em `LocalDateTime`, então a conversão usa `ZoneOffset.UTC` como **transporte, não como
interpretação**: os campos UTC do instante viram o relógio de parede e a volta é exata. O plano não
depende do fuso do aluno.

Ler o resultado como hora local seria errado — uma janela que abre 19:00 em São Paulo aparece como
22:00 — e nada no caminho lê os campos como hora do dia. No momento em que algo quiser saber "isto é
noite?", precisa do fuso, e o fuso não está no contrato: é conversa com a plataforma, não um padrão a
escolher aqui.

### 3.6. `expectedEnergyLevel` não tem fonte

A plataforma não envia sinal de energia algum. O campo recebe `0.0`, **fora da faixa documentada de
1,0 a 5,0**, para não poder ser confundido com uma observação, e nada no caminho o lê.
`SinapseEnergyIsUnusedTest` confere que só a classe que preenche o campo o menciona. Um 3,0 plausível
cometeria o mesmo erro que este repositório recusa em outros lugares: um campo que aparenta modelar
algo e não modela.

### 3.7. Piso de sessões por tópico

Derivado de `estimatedMinutes` sobre o dia de estudo medido, sem constante nova. A derivação, o
motivo do denominador e a consequência para a comparabilidade entre caminhos estão em **§2.6** — está
lá e não aqui porque não é só uma suposição: muda de onde vem um insumo que alimenta o maior peso do
agregado.

### 3.8. Quando o calendário quase não cobre o conteúdo, os dois motores concordam

Propriedade observada, não decisão. O orçamento de sessões do cromossomo é o número de sessões de
duração média que caibam, com piso de uma por tópico. Quando a disponibilidade quase não cobre o
primeiro passe do edital, os dois batem: **existe uma única alocação viável e o cromossomo fica sem
grau de liberdade**. A diferença entre as duas condições do experimento só aparece onde há folga para
distribuir — o que vale saber antes de desenhar a comparação.

Consequência para os testes: uma contraprova de "sementes diferentes divergem" rodada sobre uma
instância apertada passa vacuamente com qualquer implementação. `PlanEngineDeterminismTest` usa uma
instância com folga **e** com faixas de esforço uniformes para provar que a semente chega à busca
(ela desempata ótimos simétricos), e uma com faixas distintas para registrar que, com ótimo único, a
busca converge a partir de qualquer semente — o que é a propriedade desejada, não um defeito de
semeadura.

### 3.9. `Map.copyOf` não é aceitável para um mapa por item — achado, não precaução

A ordem de iteração de um mapa devolvido por `Map.copyOf` é embaralhada por uma **semente aleatória
por execução da JVM** (o Javadoc a declara "unspecified and subject to change"). E essa ordem entra
numa conta: `EvolutionContext.normalize` soma `raw.values()` para dividir as importâncias pelo total,
e soma de ponto flutuante não é associativa.

`TopicImportance.of` — hoje `GoalPriorityImportance.of` — devolvia `Map.copyOf(...)`. Consequência observada: **o mesmo pedido com a mesma
semente produzia planos diferentes em execuções diferentes da JVM** — o total mudava no último bit, as
importâncias normalizadas com ele, e num ótimo degenerado (dois tópicos indistinguíveis para a
fitness) o desempate virava.

Pego por `PlanEngineDeterminismTest` falhando **1 em 3** execuções separadas. Vale registrar como se
encontra um bug destes: **um teste de comportamento não o pega**, porque dentro de uma execução a
ordem é estável — rodar o mesmo pedido mil vezes no mesmo processo passa sempre. A guarda que funciona
é estrutural, e está em `SinapseAdapterIsolationTest`: nenhum arquivo do pacote usa `Map.copyOf`,
exceto `EffortTierBands`, cujo mapa é lido por `get` e nunca iterado — e que diz isso no Javadoc.

Todo mapa por item no adaptador é `Collections.unmodifiableMap` sobre um `LinkedHashMap`: imutável e
com a ordem declarada. É o mesmo argumento que `PlanningItemIndex` faz para a ordem dos genes, e a
segunda vez que este repositório o aprende.

---

## 4. Parâmetros a calibrar contra os dados do piloto

`effortTier -> difficultyBand -> LearningModel` empilha **duas calibrações não observadas**. Isso não
impede a implementação; impede que o resultado seja apresentado como modelo calibrado.

| # | Parâmetro | Onde | Status |
|---|---|---|---|
| 1 | faixa → minutos | `application.yml` do `sinapse-platform` | o próprio arquivo declara, nessas palavras, que é **"AN ASSUMPTION, NOT A FINDING"** |
| 2 | `TAU_AT_AVERAGE_LOAD = 10.0` | `LearningModel` | constante escolhida por plausibilidade, nunca ajustada a dado |
| 3 | `AVERAGE_COGNITIVE_LOAD = 3.0` | `LearningModel` | idem |
| 4 | `AVERAGE_BAND = 2.5`, `BAND_SPAN = 3.0` | `DailyLoadBudgetObjective` / `SinapseLoadBudget` | re-escalados para 1..4 a partir dos de 1..5; a **forma** da fórmula é herdada, as constantes não (§2.5) |
| 4b | `MINIMUM_BUDGET = 1` | `SinapseLoadBudget` | o menor teto não degenerado nesta escala; qualquer valor maior seria um juízo sobre quanta carga um aluno com pouca disponibilidade suporta, e não há dado para um |
| 5 | pesos 0,50 / 0,30 / 0,20 | `FitnessWeights` | julgamentos de produto; o que é medido é a **estabilidade** deles (`WeightSensitivityMain`), não a otimalidade |
| 6 | `plan.engine.ga.generations` e `population-size` | `application-baseline-core.properties` | teto de custo, não ótimo ajustado: nenhum estudo diz onde a curva de qualidade achata neste caminho |
| 7 | fração de revisão (metade do primeiro passe) | `SessionPlacement.revisionMinutes` | julgamento de planejamento, duplicado de propósito do baseline para os motores poderem divergir |
| 8 | qual estratégia de importância planeja melhor | `sinapse/importance/` | **a medição que ainda não foi feita.** `prerequisite-centrality` é hipótese; nada aqui mede se ela planeja melhor do que perguntar ao aluno. As duas existem para que a comparação seja possível (§5) |

A banda de um tópico atravessa (1) na plataforma e (2)+(3) aqui antes de virar tempo na fitness. Um
plano gerado hoje é **internamente consistente e externamente não validado**: reprodutível, auditável
termo a termo por `FitnessBreakdown`, e sem nenhuma evidência de que as constantes correspondam a como
um aluno real aprende.

---

## 5. Importância: o termo dominante mudou de natureza

`ScoreGainObjective` carrega **0,50 — metade do fitness** — e calcula `importance x mastery(dias)`.

No produto de concurso, `importance` era o valor da disciplina na prova: `questionCount x peso do
eixo temático`. **Externo e objetivo** — está no edital, e nem o aluno nem o sistema o escolhem.

**O domínio SINAPSE não tem esse insumo.** O `PlanRequest` não carrega contagem de questões, peso de
eixo nem edital. Então o substituto não é uma troca de fórmula: muda **o que o termo dominante
significa**.

### 5.1. Duas estratégias, e por que duas

| id | Fonte | Natureza | Tensão |
|---|---|---|---|
| `goal-priority` *(padrão)* | `goals[].priority`, propagada aos tópicos da disciplina | **autodeclarada pelo aluno** | contraria a decisão L1 (ADR 0012, "o aluno não informa nada") |
| `prerequisite-centrality` | nº de tópicos que dependem transitivamente do tópico (arestas `HARD`) | **objetiva, estrutural** | hipótese não validada |

Não é indecisão. A opção do grafo é **hipótese**, e uma hipótese sem nada contra o quê testar não é
mensurável. A primeira é o padrão porque é a substituta cujo significado uma decisão de produto já
endossou.

A tensão com L1 é real e fica registrada: a plataforma evitou dado autodeclarado de propósito, e o
padrão de hoje alimenta metade do fitness exatamente com dado autodeclarado.

### 5.2. Escala: brutas diferentes, normalizada igual

As escalas **brutas** são incomparáveis de propósito — prioridade de 1 a 5, centralidade de 1 a n. O
que as torna comparáveis é haver **um único procedimento de normalização**:
`EvolutionContext.normalize` projeta o que chegar no simplex unitário. As estratégias devolvem bruto
e não normalizam nada.

Se uma delas normalizasse por conta própria, a outra passaria a ser medida contra outra escala e
nada acusaria — por isso `ImportanceScaleTest` é parametrizado por estratégia e afirma as duas
pontas: depois de `normalize` os pesos somam 1, e **antes** dele a soma é maior que 1 (a contraprova
de que ninguém normalizou por conta própria).

### 5.3. A centralidade conta só o que está no escopo, e essa restrição é o ponto honesto

A plataforma envia `graph.edgesTouchingSubjects(subjectIds)`
(`docs/CORE_CONTRACT_SURVEY.md` §4, item 2). Disso decorre:

- toda aresta **entre dois tópicos planejados** é enviada — o subgrafo em escopo é **completo**;
- arestas com **uma ponta fora** das disciplinas planejadas também são enviadas;
- arestas **inteiramente fora** não são enviadas.

Contar as pontas de fora alcançaria **exatamente um salto além da fronteira e pararia** — não um
fecho menor, um fecho **enviesado**, inflando quem estiver na borda do escopo. Foi a regra de parada
do enunciado que forçou a verificação, e a conclusão é que ela **não dispara**: as arestas
necessárias para centralidade transitiva **entre os tópicos planejados** estão todas presentes. O
que não está é a visão do currículo inteiro, e o código não finge tê-la.

Um tópico pode ter dependentes além do pedido. Rankeá-lo como se não tivesse é um erro menor do que
rankeá-lo por uma contagem completa para uns e truncada para outros.

### 5.4. Determinismo e ciclo

A contagem é a **cardinalidade de um conjunto**, então a ordem em que as arestas chegam não a
alcança; a travessia ainda roda sobre a adjacência ordenada de `HardPrerequisiteGraph`.
`PrerequisiteCentralityImportanceTest` embaralha o mesmo grafo em 25 ordens e exige valores
idênticos.

Ciclo `HARD` é **recusa declarada**, como em EOA-2: dentro de um ciclo "quantos dependem deste" não
tem resposta — todos dependem de todos, inclusive de si mesmos. A travessia terminaria (o conjunto
de visitados torna a alcançabilidade segura) e devolveria um número sem significado. A recusa é
`PrerequisiteCycles`, compartilhada com a ordem de estudo, para que as duas não divirjam em
categoria ou mensagem.

### 5.5. A resposta diz qual estratégia rodou

`fitness["importance-strategy"]`, sempre. Sem isso um resultado registrado **não é reproduzível**:
nada diz o que `importance` significava naquela execução, e ela alimenta metade do fitness. A chave
é estampada a partir da estratégia que de fato resolveu, não da configuração — a resposta tem de ser
reconstruível meses depois sem consultar o que o deploy daquele dia tinha configurado.

Selecionada por `algorithmParams.importance`, com padrão em
`plan.fitness.sinapse.importance-strategy`. Trocar de condição **não exige recompilar nem
reimplantar**. Um id desconhecido é recusado com `422` listando os que existem — cair no padrão
rodaria um significado enquanto quem chamou registrou outro.

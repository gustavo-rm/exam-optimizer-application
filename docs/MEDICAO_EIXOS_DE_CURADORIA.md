# Medição EOA-1 — Os eixos que decidem o que vale curar

**Pergunta:** quais distinções curadas — dispersão de importância, força da aresta, faixa de esforço —
mudam o plano, e em que regime. O resultado é a especificação do que vale curar antes de escalar a
curadoria, como pede a recomendação de validação da ADR 0014 da plataforma.
**Fora de escopo:** motor, função objetivo, pesos, alocador. Esta tarefa mede; não melhora nada.
**Código:** `benchmarks/java/.../benchmark/instance/Curation*`, `ImportanceSpread`, `EdgeStrengthMix`,
`EffortMapping`; `metric/CurationMetrics`, `metric/CurationRow`; `harness/CurationDesign`,
`harness/CurationAxesMain`, `harness/CurationAxesTest`.
**Dados:** [`benchmarks/results/curation-axes.csv`](../benchmarks/results/curation-axes.csv) e o
`-environment.txt` ao lado.

---

> ## ⚠ As seções §0 a §6 foram escritas e versionadas ANTES de a medição produzir dado
>
> O commit que as introduz não contém o CSV. As métricas de decisão, a referência de cada contraste, o
> limiar de materialidade e as previsões estão fixados aqui para que nenhum deles seja escolhido
> depois de ver os números — a mesma disciplina do relatório
> [`12`](./revisao-ag/12-desconfundindo-precedencia.md). Os resultados entram a partir da §7, num
> commit posterior.

---

## 0. O que foi verificado antes de medir

| Afirmação | Status |
|---|---|
| `results/measurement.csv` tem 1 080 linhas, todas com `invariants_ok = true` e `reproducible = true`. A primeira pergunta da ADR 0014 — o Core consome o DAG corretamente? — está respondida pelo grupo (a) e não foi reconstruída | OBSERVED (leitura do CSV no commit `9e13262`) |
| O mesmo CSV **reproduz** no HEAD: `MeasurementMain` rodado de novo dá as 1 080 linhas iguais em todas as 46 colunas comuns, salvo `elapsed_micros`. O arquivo publicado foi restaurado; esta tarefa não o regrava | OBSERVED (execução local, JDK 21.0.12, 52 s) |
| O CSV publicado **não tem** quatro colunas que o README documenta e o harness atual escreve: `precedence_policy`, `search_vital`, `search_initial_distinct`, `search_improved`. Foi gravado antes delas | OBSERVED (comparação dos cabeçalhos) |
| Não há `measurement-environment.txt` nem `soft-weight-sweep-environment.txt` em `results/`; só `precedence-cells-environment.txt`. O README diz que cada CSV tem o seu | OBSERVED (`ls benchmarks/results`) |
| `ga` contra `greedy-baseline` em inversões `SOFT`: **100 vitórias, 20 empates, 0 derrotas** em 120 pares; em tópicos agendados: **30 / 44 / 46**. Confirmado, **com uma qualificação que o enunciado omitia**: esses números são da proveniência `all`. Em `curated-textbook` são 100/20/0 e 30/37/53; em `curated`, **61/59/0** e 30/43/47 | OBSERVED (pares instância × semente, por proveniência) |
| `importance` está fixa em `goal-priority` nas 1 080 linhas, e a biblioteca usa sempre três disciplinas com prioridades 5/3/1 | OBSERVED (CSV; `InstanceLibrary.PRIORITIES`) |
| As três instâncias de catálogo real (`src/test/resources/instances`) têm **uma** disciplina e **uma** meta (prioridade 2): dispersão de importância zero. É o regime em que o produto roda hoje, e nenhuma das 1 080 linhas está nele | OBSERVED (leitura dos JSON) |
| Na plataforma, `Goal.priority` vai de 1 a 5, uma meta por disciplina, e vale 3 quando omitida | READ (`GoalRequest.java:28`, `PlanRequest.java:101-103` em other-repo@46b5b3c) |
| Na plataforma, `estimatedMinutes` sai da faixa pela tabela `sinapse.curriculum.effort-tier-duration` = 25 / 50 / 90 / 150 min, declarada "AN ASSUMPTION, NOT A FINDING", escalada por um fator por aluno (0,5–2,0) que não existe nas primeiras semanas | READ (`application.yml:370-382`, ADR 0012 L1, other-repo@46b5b3c) |
| No Core, `effortTier` vira **banda de dificuldade 1..4, fixa no código e não configurável** (`EffortTierBands`), lida pelos dois motores genéticos (`LearningModel`, teto de carga). O guloso **nunca** lê `effortTier` | READ (`EffortTierBands.java:76-82`, `GreedyBaselineScheduler.java:68-72`) |
| O catálogo de exemplo `MED-ANAT` tem 35 tópicos (4 `SHORT`, 21 `STANDARD`, 8 `LONG`, 2 `EXTENDED`) e 35 arestas, todas `CURATED`: 24 `HARD` e 11 `SOFT`; 2 das 11 `SOFT` apontam contra a posição curricular, nenhuma `HARD` aponta | OBSERVED (CSV do catálogo e `plan-request-catalogo-real-medio.json`) |

Repositório da plataforma lido em **other-repo@46b5b3c** (clone raso, só leitura, apagado depois).

---

## 1. Por que esta medição vem antes da curadoria

A ADR 0014 decide que o catálogo é artefato curado e fecha com uma validação ainda não cumprida:
verificar se o Core consome o DAG, se `HARD`/`SOFT` produz planos diferentes e se as faixas alteram a
alocação — "se alguma dessas distinções não se pagar, ela sai antes de custar curadoria em escala". A
primeira pergunta está respondida (§0). As outras duas, e a dispersão de importância que a decisão
multidisciplina produz, nunca foram variadas: a matriz publicada **registra** a força das arestas, não
a varia; mantém `importance` fixa; e não tem faixa de esforço como eixo. Os minutos de
`InstanceLibrary` são sorteados entre 30 e 180 **independentes da faixa**, então ela nem sequer tem a
forma do que a plataforma manda.

---

## 2. Os eixos

### 2.1 `importance` — dispersão das prioridades

| Nível | Prioridades (disciplina 1, demais) | CV com 2 disciplinas | O que representa |
|---|---|---|---|
| `flat` | 3, 3 | 0 | ninguém mexe no padrão; equivale a "uma meta só" em importância |
| `close` | 4, 3 | 0,143 | prioridades próximas |
| `extreme` | 5, 1 | 0,667 | as pontas da escala |

**Medida de dispersão declarada:** coeficiente de variação (desvio-padrão populacional ÷ média) da
importância por tópico **como a estratégia de produção a resolve** (`ImportanceStrategies.resolve`,
`goal-priority`). Escolhido por ser invariante de escala: o Core normaliza a importância no simplex, e
o desvio-padrão da importância normalizada encolheria com o número de tópicos, tornando 35 e 105
tópicos incomparáveis. Coluna `importance_cv`.

"Uma meta só", literalmente, é uma disciplina: é o bloco S com uma disciplina, onde os três níveis
coincidem por construção (CV = 0). No bloco F as disciplinas são duas, e `flat` é a dispersão zero.

### 2.2 Força da aresta — reclassificar, não remover

| Nível | `HARD` | Regra |
|---|---|---|
| `all-soft` | 0 | toda aresta vira preferência |
| `curated-mix` | 24 de cada 35 | a proporção do catálogo; só arestas a favor da posição |
| `all-hard` | 35 de cada 35 | toda aresta vira requisito |

> **Isto não é a ablação de proveniência.** `provenance` **remove** arestas: sob `curated`, as
> `TEXTBOOK_ORDER` e `DERIVED` deixam de existir para o motor. Este eixo **não remove nenhuma**: o
> conjunto de pares (pré-requisito, dependente) é idêntico nos três níveis, aresta por aresta, e só a
> coluna `strength` muda. Ler um eixo pelo outro é ler "o curador classificou diferente" como "o
> curador curou menos". Nesta medição todas as arestas são `CURATED` e a proveniência fica em `all`.

`all-soft` e `all-hard` são os dois contrafactuais de "não classificar": `EdgeStrength` não tem
terceiro valor. O gerador garante que o conjunto **inteiro** de arestas é acíclico, para que
`all-hard` seja sempre planejável.

### 2.3 Faixa de esforço — dois canais, e só um é configuração

A faixa chega ao Core por dois caminhos (§0): **minutos**, pela tabela da plataforma, lidos pelos três
motores; e **banda de dificuldade**, fixa no Core, lida só pelos genéticos. Por isso o nível pedido
pelo enunciado — "todas as bandas valendo o mesmo" — **não é inerte por construção** para os motores
genéticos. Os níveis separam os canais:

| Nível | Faixas enviadas | Minutos SHORT/STANDARD/LONG/EXTENDED | Canais ativos |
|---|---|---|---|
| `uniform` | todas `STANDARD` | 50 | nenhum: o curador não anotou faixa |
| `tiers-flat` | as do catálogo | 50 / 50 / 50 / 50 | só a banda (genéticos) |
| `tiers-narrow` | as do catálogo | 35 / 50 / 67 / 87 | os dois, minutos comprimidos |
| `tiers-platform` | as do catálogo | 25 / 50 / 90 / 150 | os dois, a tabela de hoje |

`tiers-narrow` é a média geométrica entre `tiers-flat` e `tiers-platform`
(`50 × (platform/50)^½`), para que a tabela atual fique no meio de uma escala: a calibração pelo
piloto pode movê-la para qualquer lado.

**O calendário não muda com a anotação.** A folga é medida contra a demanda **nominal** (faixas
latentes sob a tabela da plataforma), em todo nível. Recalculá-la por nível daria a `uniform` um
calendário ~20% menor que a `tiers-platform`, e a diferença de cobertura mediria o calendário, não a
anotação. Uma primeira execução foi interrompida para corrigir isso, **antes** de produzir dado.

**Controle negativo do guloso**, verificado no teste e não suposto: `uniform` e `tiers-flat` mandam os
mesmos minutos e o mesmo calendário, e o guloso não lê a faixa; o plano dele tem de ser idêntico
sessão por sessão (`CurationAxesTest.oGulosoNaoLeAFaixa`, passa).

### 2.4 `topics` — de uma a três disciplinas

35, 70 e 105 tópicos: uma, duas e três disciplinas com a forma do catálogo de exemplo. 35 é o piso
pedido (o catálogo de hoje); 105 cobre "duas ou três disciplinas reais" pelo alto. Tamanho e número de
disciplinas andam juntos de propósito, porque é assim que o piloto cresce — e isso é declarado como
confundimento: um efeito de tamanho aqui é efeito de "mais disciplinas", não de "disciplina maior".

### 2.5 O que o gerador copia do catálogo, e o que supõe

Copia: 35 tópicos por disciplina, a proporção de faixas, uma aresta por tópico, a maioria local
(distância de posição ≤ 8 em 85% das arestas), 2 de 35 contra a posição, 24 de 35 `HARD`, todas
`CURATED`. Supõe, porque o catálogo tem uma disciplina só: **20% de arestas entre disciplinas** quando
há mais de uma, e o calendário de `InstanceLibrary` (janelas diárias de 240 min, dias seguidos). Três
sorteios de estrutura (`replicate` 1–3) por tamanho dão a replicação entre instâncias.

---

## 3. A matriz

**Produto inteiro:** 3 tamanhos × 3 × 3 × 4 × 3 folgas × 3 sorteios = 972 instâncias × 3 motores × 5
sementes = 14 580 linhas, ~100 min estimados (medido antes: a v2 custa ~0,55 / 1,0 / 1,5 s por linha
com 35 / 70 / 105 tópicos; guloso e v1 somam menos de 5%).

**Desenhado:**

| Bloco | Pontos | O que responde |
|---|---|---|
| **F** — fatorial completo dos três eixos curados, 2 disciplinas | 3 × 3 × 4 = 36 | cada distinção e as interações entre elas, no tamanho mínimo do piloto multidisciplina |
| **S** — 1 e 3 disciplinas, cada eixo variado um de cada vez em volta do centro | 8 + 8 | se o que F acha vale no tamanho de hoje e no de três disciplinas |

Ambos cruzados com folga {0,7; 1,0; 1,5}, sorteio {1, 2, 3}, os três motores e as cinco sementes:
**52 × 9 × 15 = 7 020 linhas, ~40 min estimados.**

**Critério de redução, declarado:** não é o tempo — o produto inteiro caberia. É que metade dele mede
combinações que não respondem pergunta desta tarefa: com uma disciplina o eixo de importância é
degenerado (uma meta), e o cruzamento completo de tamanho com os três eixos curados mede uma interação
de quatro fatores que ninguém perguntou. O fatorial fica onde as interações importam (duas
disciplinas), e o tamanho é variado sobre ele.

**Centro:** `close` / `curated-mix` / `tiers-platform` — o ponto mais parecido com o que o piloto
mandaria. Folga e sorteio não têm centro: são sempre cruzados.

---

## 4. As métricas

As quatro famílias do README valem como estão; o grupo (d) continua **não comparando nada**.
Acrescentadas, todas propriedades do cronograma (grupo b):

| Coluna | Definição | Por que existe |
|---|---|---|
| `plan_hash` | 16 hex do SHA-256 das sessões (tópico, tipo, início, duração) em ordem de `sequenceIndex` | "o nível não mudou o plano" sem escolher métrica: hash igual, nenhuma métrica difere |
| `order_inversions_all_edges` | arestas de **qualquer** força com os dois tópicos agendados em que a 1.ª sessão do dependente começa antes da 1.ª do pré-requisito | `soft_inversions` muda de denominador entre níveis de força (sob `all-hard` é zero porque não há `SOFT`); esta régua é a mesma nos três |
| `order_pairs_scheduled` | o denominador da anterior | |
| `dependents_without_prerequisite` | arestas cujo dependente foi agendado e o pré-requisito não | a consequência mais crua de rebaixar uma aresta a preferência |
| `sessions_study`, `sessions_revision` | contagem por tipo | a banda decide sessões exigidas no `LearningModel` |
| `scheduled_{short,standard,long,extended}` | tópicos agendados por faixa **latente** | "que tipo de tópico ficou de fora", com a mesma régua em `uniform` |
| `importance_cv`, `minutes_cv` | §2.1; CV de `estimatedMinutes` | a dispersão efetiva, comparável entre instâncias |

Sob `all-hard`, `order_inversions_all_edges` e `dependents_without_prerequisite` têm de ser zero (a
leitura forte de `HARD` em `Invariants` implica as duas); `CurationAxesMain` **aborta** se não forem.

---

## 5. Método de análise — pré-registrado

**Pareamento.** Um contraste compara dois níveis de **um** eixo com todo o resto igual: mesmo sorteio,
mesma folga, mesma semente, mesmo motor, e — no bloco F — mesmos níveis dos outros dois eixos curados.
Nenhuma média entre instâncias substitui o par.

**Referência de cada eixo:**

| Eixo | Referência | Contrastes |
|---|---|---|
| importância | `flat` (sem dispersão) | `close − flat`, `extreme − flat` |
| força | `curated-mix` (o que a curadoria produz) | `all-soft − curated-mix`, `all-hard − curated-mix` |
| esforço | `uniform` (sem anotação) | `tiers-flat − uniform` (só a banda), `tiers-narrow − uniform`, `tiers-platform − uniform` |

**Métricas de decisão** (as que decidem "se paga"): `topics_scheduled`, `order_inversions_all_edges`,
`dependents_without_prerequisite`, `scheduled_long + scheduled_extended`, `sessions_revision`. As
demais do grupo (b) são relatadas, não decidem. `soft_inversions` só é comparada **dentro** do mesmo
nível de força.

**Para cada contraste × motor × folga** (bloco F agregando os pares sobre os níveis dos outros dois
eixos; bloco S por tamanho):

1. **taxa de plano idêntico** — fração dos pares com o mesmo `plan_hash`;
2. **efeito** — média e mediana da diferença pareada, nas unidades da métrica, e V/E/D (vitórias,
   empates, derrotas do sinal);
3. **dispersão entre sementes** — para cada par sem a semente, o desvio-padrão das cinco diferenças;
   relata-se a média desses desvios. É zero no guloso por construção, e isso é relatado, não omitido;
4. **consistência entre sorteios** — o sinal da média por sorteio, nos três.

**Limiar de materialidade, fixado agora.** Uma distinção **muda o resultado** num regime quando, em
algum motor e alguma folga, a média pareada de uma métrica de decisão tem **|Δ| ≥ 1,0** (um tópico, uma
violação de ordem, uma revisão) **e** o sinal da média por sorteio concorda nos três sorteios. Uma
distinção que muda o plano (hash) sem passar desse limiar **muda o cronograma sem mudar o resultado**,
e é relatada assim. Uma distinção que não passa o limiar em nenhum motor, folga e tamanho **não se
paga**, e a recomendação diz isso com todas as letras.

**Sem teste de significância.** Não é calculado, e a razão é de desenho: as sementes de uma mesma
instância não são independentes entre si (para o guloso são idênticas), e os sorteios de estrutura são
três — um teste sobre n = 3 unidades independentes por célula diria pouco, e um teste sobre os pares
semente-a-semente fingiria uma independência que não existe. A pergunta da ADR também não é "o efeito
é diferente de zero", é "o efeito é grande o bastante para pagar a curadoria", e para isso o tamanho do
efeito e a consistência entre sorteios são a resposta. Nenhuma frase deste relatório diz
"significativo".

**Comparação entre motores (pergunta 2).** No formato de `09`/`10`: V/E/D de `ga` contra
`greedy-baseline` em `soft_inversions` e em `topics_scheduled`, pareados por instância e semente,
**por nível de importância**, no bloco F com força e esforço no centro, e depois sobre o bloco F
inteiro. Acrescentam-se `order_inversions_all_edges` e `dependents_without_prerequisite`. Nenhuma
comparação usa `ga_objective_*`, e nenhuma conclusão sobre motor sai de uma métrica só.

---

## 6. Previsões, declaradas antes dos dados

| # | Previsão | Por quê |
|---|---|---|
| P1 | Importância `extreme − flat` muda o plano do guloso em 100% dos pares com 2 ou 3 disciplinas, e em 0% com uma | o guloso ordena por prioridade antes de posição; com uma meta, prioridade não ordena nada |
| P2 | Com uma disciplina, os três níveis de importância dão planos idênticos nos **três** motores | uma meta: importância normalizada constante, ordem do guloso inalterada |
| P3 | `all-soft − curated-mix` passa o limiar em `dependents_without_prerequisite` no guloso com folga 0,7 | sem `HARD`, nada impede o guloso de agendar um dependente cujo pré-requisito ficou de fora |
| P4 | `tiers-flat − uniform` dá plano idêntico no guloso em 100% dos pares (o controle negativo) e muda o plano dos genéticos em parte dos pares | o guloso não lê a faixa; os genéticos leem a banda |
| P5 | `tiers-platform − uniform` passa o limiar em `topics_scheduled` com folga 0,7 nos três motores | com o mesmo calendário, minutos diferentes mudam o que cabe |
| P6 | Com folga 1,5 nenhuma distinção passa o limiar em `topics_scheduled` | tudo cabe |

Previsões que falharem são relatadas como falhas, na mesma tabela, com o número.

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

---

> ## A partir daqui: resultados
>
> Escritos depois da medição, em commit posterior ao do pré-registro (`18a0422`). Todo número sai de
> `benchmarks/analysis/curation_axes.py` sobre `results/curation-axes.csv`; a saída inteira, sem
> edição, está em [`eixos-de-curadoria/analise-completa.md`](./eixos-de-curadoria/analise-completa.md).
> Onde um número vem de um recorte que o pré-registro **não** previa, ele está marcado
> **[exploratório]**.

## 7. A execução

| Item | Valor | Status |
|---|---|---|
| Linhas | 7 020 (52 pontos × 3 folgas × 3 sorteios × 3 motores × 5 sementes), nenhum aborto | OBSERVED |
| Grupo (a) | `invariants_ok` e `reproducible` verdadeiros nas 7 020 | OBSERVED |
| Régua nova sob `all-hard` | 1 890 linhas, 0 inversões de ordem, 0 dependentes sem pré-requisito | OBSERVED |
| Guloso entre sementes | 0 de 468 células com mais de um plano: dispersão entre sementes zero, medida | OBSERVED |
| Dispersão efetiva (`importance_cv`) | 1 disciplina: 0 nos três níveis; 2: 0 / 0,143 / 0,667; 3: 0 / 0,141 / 0,808 | OBSERVED |
| Tempo | 2 251 s. Mediana por linha (duas chamadas): guloso 0,2 / 0,5 / 0,8 ms, `ga` 6 / 12 / 19 ms, `ga-timeline` 197 / 461 / 680 ms com 35 / 70 / 105 tópicos | OBSERVED (`curation-axes-environment.txt`: JDK 21.0.12.1, 4 processadores) |
| Folga efetiva, 2 disciplinas, nominal 0,7 / 1,0 / 1,5 | `uniform` e `tiers-flat` 0,89 / 1,30 / 1,92; `tiers-narrow` 0,82 / 1,20 / 1,77; `tiers-platform` 0,72 / 1,05 / 1,55 | OBSERVED — consequência declarada do calendário fixo (§2.3) |

**Uma propriedade da v1 que condiciona a leitura.** Com folga 0,7, a população inicial do `ga` é
inteiramente empatada em **100%** das linhas (`search_initial_distinct = 1`, 780 de 780), nos três
tamanhos: é o G20 reaparecendo — o orçamento de sessões iguala a soma dos pisos, e a v1 não busca. Ali
o plano dela é a ordem de estudo dela mais a colocação, e um resultado da v1 com folga 0,7 **não é
evidência sobre busca**. A v2 tem população empatada em 15 de 780 linhas com folga 0,7. OBSERVED

## 8. Resposta 1 — cada distinção curada muda o plano?

### 8.1 Força da aresta (`HARD`/`SOFT`): **sim, nos três motores, e é a que mais muda**

Taxa de plano idêntico contra `curated-mix`:

| Contraste | Bloco | guloso | `ga` | `ga-timeline` |
|---|---|---|---|---|
| `all-soft` | F (2 disc.) | 180/540 | 0/540 | 0/540 |
| `all-hard` | F | 0/540 | 0/540 | 0/540 |
| `all-soft` | S, 1 disc. | 45/45 | 25/45 | 0/45 |
| `all-hard` | S, 1 disc. | 10/45 | 10/45 | 0/45 |
| `all-soft` / `all-hard` | S, 3 disc. | 0/45 | 0/45 | 0/45 |

Efeito no bloco F (média pareada; entre parênteses, DP entre sementes), as linhas que passam o limiar:

| Contraste | Motor | Folga | Inversões de ordem (todas as arestas) | Dependentes sem pré-requisito | Tópicos agendados |
|---|---|---|---|---|---|
| `all-soft − curated-mix` | guloso | 0,7 / 1,0 / 1,5 | +1,72 / **+3,22** / +3,33 (0) | **+1,61** / 0 / 0 | ≈ 0 |
| | `ga` | 1,0 / 1,5 | **+2,23** (1,27) / +2,19 | +1,25 com 0,7 | −0,57 com 1,0 (não passa) |
| | `ga-timeline` | 0,7 / 1,0 / 1,5 | **+9,21** (2,59) / **+21,38** (3,81) / +19,48 | **+7,68** (0,95) com 0,7 | **−2,22** (0,65) com 0,7 |
| `all-hard − curated-mix` | guloso | 0,7 / 1,0 / 1,5 | −2,61 / **−4,19** (0) / −4,44 | ≈ 0 | ≈ 0 |
| | `ga` | 1,0 / 1,5 | −1,44 (0,42) / −2,37 | ≈ 0 | +0,19 com 1,0 (não passa) |
| | `ga-timeline` | 0,7 / 1,0 / 1,5 | **−3,97** (1,02) / **−6,52** (1,30) / −6,53 | **−1,89** com 0,7 | +0,74 com 0,7 (não passa) |

Com três disciplinas os efeitos crescem: `all-soft` custa à v2 **−6,73 tópicos** com folga 0,7 e
+32,67 inversões com 1,0; `all-hard` custa ao guloso **−2,00 tópicos** com folga 0,7 (sinal igual nos
três sorteios) e dá à v2 **+3,53**.

**O regime em que o guloso reage, [exploratório].** No bloco F, o plano do guloso sob `all-soft` é
**idêntico** ao de `curated-mix` em **todos** os 180 pares com importância `flat`, e diferente em
todos os 360 com `close` ou `extreme` (+2,6 a +5,0 inversões). A razão é estrutural: as arestas
`HARD` do catálogo apontam todas a favor da posição curricular, e com prioridades iguais o guloso já
estuda por posição — a restrição nunca morde. Ela só passa a valer quando a prioridade das metas puxa a
ordem para longe da posição. Com uma disciplina o mesmo acontece (45/45 idênticos sob `all-soft`).

**Leitura.** A distinção não é inerte em nenhum motor. Rebaixar tudo a preferência (`all-soft`) piora
ordem e órfãos em todo motor e, na v2, também a cobertura com folga apertada. Promover tudo a
requisito (`all-hard`) melhora ordem e órfãos e quase não custa cobertura — **o único custo material de
cobertura medido é o do guloso com três disciplinas e folga 0,7 (−2 tópicos)**. Isso tem uma ressalva
que não se resolve com estes dados: `order_inversions_all_edges` trata toda aresta invertida como ruim,
então `all-hard` ganha nela por construção. Se uma aresta `SOFT` é de fato opcional, invertê-la não é
perda, e o benefício de rotulá-la `SOFT` — flexibilidade quando o calendário aperta — é exatamente o
que apareceu pouco: em cobertura, só os 2 tópicos do guloso com três disciplinas.

### 8.2 Faixa de esforço: **sim, mas por canais diferentes em cada motor**

Taxa de plano idêntico contra `uniform`:

| Contraste | guloso | `ga` | `ga-timeline` |
|---|---|---|---|
| `tiers-flat` (só a banda) | **405/405** (F), 45/45, 45/45 | 135/405 (F), 15/45, 15/45 | 0/405, 0/45, 0/45 |
| `tiers-narrow`, `tiers-platform` | 0 em todos | 0 em todos | 0 em todos |

Efeito no bloco F, folga 0,7 / 1,0 / 1,5 (DP entre sementes entre parênteses quando ≠ 0):

| Contraste | Motor | Tópicos agendados | Sessões de revisão | Tópicos LONG+EXTENDED (latentes) agendados |
|---|---|---|---|---|
| `tiers-flat − uniform` | guloso | 0 / 0 / 0 | 0 / 0 / 0 | 0 |
| | `ga` | 0 / ≈0 / 0 | 0 / 0 / 0 | ≈ 0 |
| | `ga-timeline` | 0 / 0 / 0 | **−4,15** / **−4,73** / **−8,41** (8,6) | **+3,48** com 0,7 |
| `tiers-narrow − uniform` | guloso | +0,15 / 0 / 0 | 0 | +0,22 com 0,7 |
| | `ga` | −0,22 / **−1,96** / 0 | 0 / **−7,13** / **−11,00** | −0,44 com 1,0 |
| | `ga-timeline` | +0,78 / 0 / 0 | **−4,44** / **−8,28** / **−14,60** | **+3,33** com 0,7 |
| `tiers-platform − uniform` | guloso | **−7,11** / **−6,59** / 0 | 0 | **−2,07** / −1,78 |
| | `ga` | **−6,33** / **−6,53** (0,95) / 0 | 0 / **−18,10** / **−26,00** | **−1,78** / **−2,67** |
| | `ga-timeline` | **−5,46** (0,88) / **−1,56** / 0 | **−13,42** / **−19,78** / **−23,95** | **+2,16** com 0,7 |

**Os dois canais, separados.**

* **Banda** (`tiers-flat − uniform`, minutos iguais): o guloso não a vê — controle negativo, 405 de
  405 planos idênticos. A v1 muda o cronograma em 2/3 dos pares mas não muda nenhuma métrica de
  decisão: **muda o cronograma sem mudar o resultado**. A v2 é o único motor em que a banda sozinha
  passa o limiar: 4 a 8 revisões a menos, e com folga 0,7 ~3,5 tópicos `LONG`/`EXTENDED` a mais no
  lugar de curtos, com o mesmo total de tópicos.
* **Minutos.** A diferença de cobertura de `tiers-platform` é quase toda **nível** da tabela, não
  **distinção** entre faixas — **[exploratório]**: `tiers-narrow` (média 54 min) contra `uniform`
  (50 min) não passa o limiar em tópicos em nenhum motor salvo a v1 com folga 1,0 (−1,96), e
  `tiers-platform` contra `tiers-narrow` dá −4,6 a −7,3 tópicos no guloso e na v1 (folgas 0,7 e 1,0). Com o calendário fixo,
  a tabela da plataforma pede ~24% mais minutos que "tudo STANDARD" (62 contra 50 min por tópico em
  média), e é isso que tira 5 a 7 tópicos de 70. Ou seja: o número que mais move a cobertura é a
  **calibração** da tabela, que é configuração da plataforma, não trabalho do curador.

### 8.3 Dispersão de importância: **muda o plano; o resultado, pouco** — e não é curadoria

Taxa de plano idêntico contra `flat`:

| Contraste | Bloco | guloso | `ga` | `ga-timeline` |
|---|---|---|---|---|
| `close`, `extreme` | F | 0/540 | 184/540, 183/540 | 14/540, 7/540 |
| `close`, `extreme` | S, 1 disc. | 45/45 | 45/45 | 45/45 |
| `close`, `extreme` | S, 3 disc. | 0/45 | 15/45 | 0/45 |

O guloso muda **sempre** que há duas disciplinas ou mais, e o que muda é a ordem: **+1,47 / +2,06 /
+2,11** inversões em todas as arestas (folga 0,7 / 1,0 / 1,5, DP 0, sinal igual nos três sorteios),
iguais para `close` e `extreme` — a ordem do guloso é lexicográfica, então qualquer diferença de
prioridade produz o mesmo plano. Com três disciplinas e folga 0,7 ele agenda **+4,00 tópicos**. A v1
quase não reage: as médias ficam abaixo de 1 em todas as métricas de decisão, e com folga 0,7 os planos
são idênticos (180/180) porque a v1 ali não busca (§7). A v2 passa o limiar uma vez no bloco F
(`extreme`, folga 1,5: **+5,93 revisões**, DP 6,91) e quatro vezes com três disciplinas (−1,67 tópico com
0,7, −1,27 revisão com 1,0, +1,20 e +2,67 inversões com 1,5). Com uma disciplina os três níveis dão o mesmo plano nos três motores,
como previsto.

A importância não sai da planilha do curador: sai das metas do aluno. Ela entra aqui porque decide o
**regime** — e o regime decide se a força da aresta morde (§8.1).

## 9. Resposta 2 — onde está a diferença entre os motores, com a importância ligada

Quadro de hoje (`measurement.csv`, proveniência `all`): `ga` vence o guloso em inversões `SOFT` (100 /
20 / 0) e não em cobertura (30 / 44 / 46). No regime novo, V/E/D pareados por instância e semente
(vitória = menos inversões, mais tópicos):

| `ga` contra guloso | Recorte | Inversões `SOFT` | Tópicos agendados | Inversões, todas as arestas | Órfãos |
|---|---|---|---|---|---|
| centro (força e esforço no centro) | todas as importâncias | **131 / 4 / 0** | 26 / 85 / 24 | 114 / 21 / 0 | 27 / 103 / 5 |
| bloco F inteiro | `flat` | 265 / 243 / **32** | 56 / 351 / 133 | 248 / 263 / 29 | 37 / 437 / 66 |
| | `close` | 354 / 184 / 2 | 25 / 378 / 137 | 349 / 189 / 2 | 45 / 486 / 9 |
| | `extreme` | 350 / 187 / 3 | 25 / 384 / 131 | 346 / 193 / 1 | 45 / 481 / 14 |
| | folga 1,0 | 320 / 199 / 21 | **21 / 193 / 326** | 324 / 200 / 16 | 12 / 484 / 44 |
| | total | **969 / 614 / 37** | **106 / 1 113 / 401** | 943 / 645 / 32 | 127 / 1 404 / 89 |

**Confirmado, e mais nítido.** A vantagem do `ga` continua na ordem e não na cobertura; na cobertura
ele agora **perde** mais do que ganha (106 contra 401), quase tudo com folga 1,0 (326 derrotas em 540).
O eixo novo mostra **onde a vantagem de ordem pode perder**: com importância `flat` o `ga` perde 32
pares de inversões `SOFT`; com dispersão, 5 em 1 080. A dispersão de importância amplia a vantagem de
ordem (média −0,92 com `flat`, −2,9 com `close`, −2,8 com `extreme`) porque é ela que tira o guloso da
ordem curricular (§8.1).

A v2, para registro: contra o guloso no bloco F ela agenda mais (290 / 1 250 / 80) e inverte mais
(47 / 614 / 959 em `SOFT`), em todo nível de importância. Nenhuma das duas comparações usa
`ga_objective_*`, e nenhuma conclusão sobre qual motor é melhor sai daqui: cada um ganha numa métrica
e perde noutra.

## 10. Resposta 3 — em que regime o piloto deve ficar para que a comparação possa perder

| Fator | Faixa | Por quê (medido) |
|---|---|---|
| Folga | **entre 0,7 e 1,0, com 1,0 dentro** | com 1,5 tudo cabe: **0 de 540** pares do bloco F diferem em tópicos entre `ga` e guloso, e nenhuma distinção passa o limiar em tópicos (P6). Com 0,7 a v1 não busca (§7). Com 1,0 a cobertura do `ga` perde 326 de 540: é o único regime em que a comparação de cobertura perde de verdade |
| Dispersão de importância | **incluir alunos com prioridades iguais** (o padrão 3) | é o único regime em que a vantagem de ordem do `ga` perde pares (32 contra 5) e em que o guloso segue a ordem curricular; com prioridades diferentes a comparação de ordem fica quase impossível de perder |
| Tamanho | **duas disciplinas ou mais** | com uma, a importância é degenerada (planos idênticos nos três motores) e a força da aresta não move o guloso (45/45); com três os efeitos de força e esforço crescem (§8.1, §8.2) |

Um piloto só com uma disciplina, folga larga e prioridades distintas produziria uma comparação que não
tem como perder em ordem nem como diferir em cobertura. RECOMENDAÇÃO, não decisão.

## 11. Resposta 4 — o que vale curar

**Nenhuma das duas distinções da planilha do curador é inerte. Nenhuma sai.** Mas elas não pagam do
mesmo jeito, e o tempo do curador deve seguir isso:

1. **A aresta em si (qual tópico depende de qual) é o que mais move o plano.** Toda comparação de força
   da §8.1 muda o plano em quase 100% dos pares com duas disciplinas ou mais.
2. **`HARD`/`SOFT`: manter a coluna, com `HARD` como padrão barato.** Rebaixar arestas a `SOFT` piorou
   ordem e órfãos em todo motor e não comprou cobertura (salvo 2 tópicos no guloso com três disciplinas
   e calendário apertado); tratar tudo como `HARD` quase não custou nada medido. PROPOSTA para a
   plataforma: o curador marca `SOFT` só quando sabe que a ordem é opcional, em vez de decidir aresta por
   aresta — a ressalva da §8.1 sobre a régua vale aqui.
3. **Faixa de esforço: vale curar, e é barata** (uma anotação ordinal por tópico). Ela muda o plano em
   todo motor pelos minutos, e na v2 também pela banda (revisões, que tipo de tópico entra). **Mas o que
   mais move a cobertura é a tabela de minutos, não a anotação**: a calibração de
   `effort-tier-duration` contra dado do piloto merece mais atenção que refinar faixas. Com o motor
   padrão de produção (`greedy-baseline`) a banda é inerte; só os minutos contam.
4. **Importância** não é curadoria. Ela decide o regime, e por isso entra na especificação do piloto
   (§10), não na planilha.

Se o produto adotar `ga-timeline`, a força da aresta passa a ser a distinção de maior efeito medido
(`all-soft` tira 2 a 7 tópicos com folga 0,7 e acrescenta 4 a 33 inversões), e a recomendação 2 fica mais forte.

## 12. As previsões

| # | Previsão | Resultado |
|---|---|---|
| P1 | `extreme − flat` muda o guloso em 100% com 2 ou 3 disciplinas e 0% com uma | **confirmada**: 0/540 e 0/45 idênticos; 45/45 idênticos com uma |
| P2 | uma disciplina: planos idênticos nos três motores | **confirmada**: 45/45 em cada |
| P3 | `all-soft − curated-mix` passa o limiar em órfãos no guloso com folga 0,7 | **confirmada com 2 e 3 disciplinas** (+1,61, +2,33); **falhou com uma** (planos idênticos, §8.1) |
| P4 | `tiers-flat − uniform`: guloso 100% idêntico, genéticos mudam em parte | **confirmada**: 405/405; `ga` 135/405 idênticos, `ga-timeline` 0/405 |
| P5 | `tiers-platform − uniform` passa o limiar em tópicos com folga 0,7 nos três motores | **confirmada** nos três tamanhos (F: −7,11 / −6,33 / −5,46); mas o efeito é sobretudo de nível da tabela (§8.2) |
| P6 | com folga 1,5 nenhuma distinção passa o limiar em tópicos | **confirmada** |

## 13. O que ficou sem resposta

* **Nível contra dispersão da tabela de minutos.** `uniform` vale 50 min (o que a plataforma manda sem
  anotação), não a média da tabela (62). Um nível "tudo `STANDARD` a 62 min" separaria os dois efeitos
  de §8.2; a leitura de que a cobertura é sobretudo nível vem de `tiers-narrow`, e está marcada
  exploratória. Não medido.
* **Se `SOFT` compra alguma coisa que estas métricas não veem.** Toda métrica de ordem aqui trata
  inversão como perda. O valor pedagógico de inverter uma aresta opcional não é mensurável sem dado do
  piloto.
* **Arestas entre disciplinas.** A fração de 20% é suposição: nenhum catálogo real tem mais de uma
  disciplina. Os efeitos de força com várias disciplinas dependem dela.
* **Generalidade além de três sorteios.** Três estruturas por tamanho; a consistência de sinal entre
  elas é o que o limiar exige, mas não é uma amostra de catálogos reais.
* **Proveniência e o fator por aluno** (0,5–2,0 sobre os minutos) ficaram fora, de propósito.

## 14. O que no enunciado estava errado ou incompleto

1. **"O que cada banda vale em minutos é configuração" vale para a plataforma, não para o Core.** No
   Core a faixa é também banda de dificuldade, fixa no código (`EffortTierBands`), lida pelos motores
   genéticos. Por isso "todas as bandas valendo o mesmo" **não é inerte por construção** para eles — e
   a medição confirmou: a v2 muda o plano em 405 de 405 pares só pela banda. Só para o guloso o nível é
   controle negativo.
2. **Os números 100 / 20 / 0 e 30 / 44 / 46 são da proveniência `all`.** O enunciado não dizia; em
   `curated` a vantagem de ordem é 61 / 59 / 0.
3. **`InstanceLibrary` não tem a forma do que a plataforma manda** — minutos sorteados independentes da
   faixa, faixas por `índice % 4`, metade das `SOFT` para trás de propósito. Um eixo de esforço sobre ela
   mediria uma faixa que não determina os minutos. Por isso a biblioteca nova.
4. **"O piloto rodaria fora da faixa medida"** era verdade também em importância: as instâncias de
   catálogo real têm uma meta só, regime que nenhuma das 1 080 linhas publicadas cobre.
5. **Manter a folga fixa entre níveis de esforço**, lido ao pé da letra ("mantendo tudo o mais igual"),
   teria recalculado o calendário por nível e medido o calendário em vez da anotação. Aqui o calendário
   é que fica igual (§2.3).
6. **O README dizia que cada CSV tem o seu `-environment.txt`** e descrevia 51 colunas; dois dos três
   não têm, e o harness escreve 55. Corrigido no README nesta tarefa.

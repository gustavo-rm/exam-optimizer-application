# Diagnóstico de escala com o catálogo real (35 tópicos)

Este documento **mede e relata**. Não corrige nada, não altera `src/main` nem o `pom.xml` e não ajusta
parâmetro de motor, operador genético, fitness ou `PlanOutputInvariants`. Cada afirmação está marcada:

- **[observado]**: saiu de uma execução, e o número está no CSV;
- **[hipótese]**: explicação compatível com o observado, com `arquivo:linha` e o experimento que a
  confirmaria;
- **[suposição de projeto]**: escolha deste diagnóstico que poderia ter sido outra.

Nenhum fitness de um motor é comparado com o de outro motor. O avaliador a posteriori (`PlanScoring`, do
harness EOA-M) pontua todo plano com a mesma composição, mas o número é **o objetivo do genético,
relatado por completude**, não uma comparação. Nenhuma linha deste documento afirma que um motor é
superior a outro: só se descrevem diferenças pareadas.

## 1. Veredito

1. **Rótulos pelas regras do prompt:** H1 **não** sustentada. "H2 ou H3" **sim**, no médio e no
   folgado. "H3 (escala)" **sustentada em k = 35**, com objeção (§7). 3b **inconclusivo** pela regra
   literal nos dois motores, com objeção (§6).
2. **[observado]** O `ga-timeline` recusa 30/30 execuções porque `F(plano vazio) = NaN`
   (`dailyLoadBudget`), `Population.getFittest` ordena `NaN` acima de todo número, e já há planos vazios
   na geração 0. O melhor indivíduo é vazio da geração 0 à 60.
3. **[observado]** O `ga` recusa 23/30. O cromossomo que ele evolui tem os 35 tópicos e `F` maior que o
   plano do guloso codificado. O plano encolhe **depois** da busca, na colocação, cujo primeiro tópico é
   o de 150 min (posição 34).
4. **[hipótese]** Uma sessão precisa caber inteira numa janela, a colocação para no primeiro tópico que
   não cabe, e o `ga` coloca primeiro o tópico com mais sessões. O mesmo alocador limita o guloso (2, 15
   e 33 tópicos). A causa é de colocação (H2b) e de avaliação do vazio, não de escala da busca.

## 2. Ambiente e entrada

| Item | Valor |
|---|---|
| Base | `origin/main` em `61233adc6e5c6ad506ecc443902b3079733a65f1`; branch `claude/serene-bardeen-1x0erj` |
| Linha de base | `./mvnw verify` verde em `origin/main` antes de qualquer mudança: INSTRUCTION 11143/11868 = 0,9389, BRANCH 797/914 = 0,8720 |
| JDK | OpenJDK 21.0.11+10 (build Ubuntu 24.04.2) |
| Maven | 3.9.11 (wrapper) |
| SO | Ubuntu 24.04.4 LTS, Linux 6.18.44, x86_64, contêiner de nuvem |
| Harness | `src/test/java/.../diagnostic/RealCatalogDiagnosticTest`, só com `-Ddiagnostic=true` |

Para reproduzir:

```bash
./mvnw test -Dtest=RealCatalogDiagnosticTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
```

Os CSVs saem em `target/diagnostic/`. A cópia publicada está ao lado deste arquivo:
`DIAGNOSTICO_ESCALA_REAL.csv` (passo 3) e `diagnostico-escala-real/` (3b, escala, geração 0, reduções
e trajetórias). Tudo é determinístico exceto `wall_ms`.

**Proveniência das instâncias.** Os três JSON e o README em `src/test/resources/instances/` foram
copiados byte a byte de `gustavo-rm/sinapse-platform`, commit
**`cbb5529cfe60de6e587f390db5b24655cf3d4185`**. A cópia foi feita com `git cat-file blob`, sem
reformatar e sem normalizar fim de linha, e os ids de blob coincidem com os da origem. O SHA-256 de cada
JSON foi conferido contra a tabela do README do mesmo commit, e os três batem. **A proveniência vem
do `RealCatalogInstancesTest` da plataforma, que regenera os documentos por `SnapshotAssembler` e falha
em qualquer diferença de byte, e não desta cópia.** A cópia só os traz para cá como entrada.

| Perfil | SHA-256 | Capacidade (min) | rho | Janelas | Maior janela |
|---|---|---|---|---|---|
| apertado | `dce67398…bd65f` | 760 | 0,350 | 12 | 70 min |
| médio | `db27f883…f7ca` | 1620 | 0,747 | 25 | 90 min |
| folgado | `4d6dbc43…0341` | 3240 | 1,493 | 29 | 120 min |

Os três perfis têm 35 tópicos (4×25, 21×50, 8×90 e 2×150 min, somando 2170 min), 24 arestas HARD e 11
SOFT, uma meta, prioridade 2, e histórico vazio.

## 3. Como um pedido chega ao motor, e a semântica de alocação

**[observado no código]**

- `PlanController.plan` (`plan/PlanController.java:77-78`) entrega o corpo desserializado a
  `PlanEngineSelector.plan` (`plan/PlanEngineSelector.java:87`).
- O seletor guarda todo `PlanEngine` registrado num `TreeMap` por `id()` (construtor, `:57-80`) e
  escolhe por `algorithmParams.engine` (`:98`). Sem a chave, usa `plan.engine.default`, injetado por
  `@Value` em `:58` e valendo `greedy-baseline` em `application-baseline-core.properties:44`.
- Ids registrados: `greedy-baseline`, `ga` (`sinapse/GeneticPlanEngine.java:81`) e `ga-timeline`
  (`sinapse/TimelinePlanEngine.java:99`).
- A semente é `request.randomSeed`, instalada no `RandomProvider` em `GeneticPlanEngine.java:131` e
  `TimelinePlanEngine.java:151`.
- De `algorithmParams` só se leem `engine`, `importance`, `precedence` e `provenance`.
  `generations`, `population-size` e `mutation-rate` **não são lidos em lugar nenhum** de `src/main`.

**Semântica de alocação: tudo-ou-nada.** A primeira sessão de um tópico ocupa `estimatedMinutes`
**inteiros e contíguos, dentro de uma única janela**:

- `AvailabilityAllocator.place`, `plan/AvailabilityAllocator.java:112-127`: um bloco só é posto se
  terminar até o fim da janela corrente, e nunca é dividido entre janelas;
- `SessionPlacement.placeTopic`, `sinapse/SessionPlacement.java:139-142`;
- `GreedyBaselineScheduler.placeTopic`, `baseline/GreedyBaselineScheduler.java:219-220`.

Se a primeira sessão não cabe, o tópico não entra. As revisões, de metade do tamanho, são parciais: as
que não cabem são descartadas e o tópico fica. A colocação **para** no primeiro tópico que não cabe,
sem pular para o seguinte (`SessionPlacement.java:115-117` e `GreedyBaselineScheduler.java:202`). Por
isso, para o passo 3, U é calculado por acumulação.

## 4. Parâmetros efetivos do AG

**[observado no código]** Valem estes, e não os de `algorithmParams` (o arquivo pede 400 gerações,
população 120 e mutação 0,05):

| Parâmetro | `ga` | `ga-timeline` | Onde |
|---|---|---|---|
| Gerações | 60 | 60 | `application-baseline-core.properties:59` → `GeneticSearchBudget.java:30` |
| População | 40 | 40 | `application-baseline-core.properties:60` → `GeneticSearchBudget.java:31` |
| Cruzamento | 0,95 (`hybridCrossover`) | 0,95 (`DayBoundaryCrossover`) | `DefaultGeneticAlgorithmFactory.java:39`; `TimelineSearch.java:57` |
| Mutação | 0,05 (`creepMutation`) | 0,05 (`BlockSwap` e `Methodology`) | `DefaultGeneticAlgorithmFactory.java:40`; `TimelineSearch.java:60` |
| Elitismo | sim | 1 indivíduo | `DefaultGeneticAlgorithmFactory.java:38`; `TimelineSearch.java:130` |
| Estagnação / hipermutação | 25 / 0,20 | — | `DefaultGeneticAlgorithmFactory.java:41-42` |
| Seleção | torneio de 3 | torneio de 3 | `TournamentSelection.java:24` |

Toda resposta aceita declarou `metadata.generations = 60`. Nas recusas, as 60 gerações rodaram antes
da recusa: a recusa vem depois da busca nos dois motores.

**Sementes**, declaradas antes de olhar os dados: `ga` e `ga-timeline` com 1 a 10. O guloso roda uma
vez, com a semente do arquivo (20261012), porque não sorteia nada.

## 5. Tabela por instância (passo 3)

**[observado]** Mediana [mínimo–máximo] sobre as 10 sementes. O guloso é uma execução só.

- `n_tópicos`: tópicos distintos com pelo menos uma sessão.
- `util`: minutos agendados ÷ capacidade.
- `mastery`: `syllabusMastery` do avaliador a posteriori, isto é, a cobertura ponderada pela
  importância. É o objetivo do genético, não uma comparação, e só existe para planos aceitos.

| Instância | Motor | Aceitos | n_tópicos | n_sessões | Minutos | util | mastery (aceitos) | Parede (ms) |
|---|---|---|---|---|---|---|---|---|
| apertado | guloso | 1/1 | 2 | 2 | 75 | 0,099 | 0,0114 | 0,6 |
| apertado | ga | 0/10 | 0 [0–0] | 0 | 0 | 0,000 | — | 8,7 [7,9–10,5] |
| apertado | ga-timeline | 0/10 | 0 [0–0] | 0 | 0 | 0,000 | — | 35,9 [30,5–44,6] |
| médio | guloso | 1/1 | 15 | 15 | 820 | 0,506 | 0,0628 | 0,8 |
| médio | ga | 0/10 | 0 [0–0] | 0 | 0 | 0,000 | — | 8,8 [7,7–11,3] |
| médio | ga-timeline | 0/10 | 0 [0–0] | 0 | 0 | 0,000 | — | 64,4 [54,1–95,4] |
| folgado | guloso | 1/1 | 33 | 33 | 1870 | 0,577 | 0,1349 | 0,8 |
| folgado | ga | 7/10 | 1 [0–3] | 2 [0–6] | 38 [0–248] | 0,012 [0–0,077] | 0,0148 [0,0074–0,0166] (n=7) | 8,2 [7,9–10,2] |
| folgado | ga-timeline | 0/10 | 0 [0–0] | 0 | 0 | 0,000 | — | 123,5 [119,2–149,8] |

- **[observado]** Nenhuma violação de `PlanOutputInvariants`: os 63 estados são 10 `accepted` e 53
  `plan-would-be-empty`.
- **[observado]** O tempo de parede foi medido pelo relógio do harness em volta de `selector.plan`, na
  mesma JVM, e inclui aquecimento do JIT. Ele só serve como ordem de grandeza.

**Diferenças pareadas** (`d_n = n(guloso) − n(motor)`, `d_u = util(guloso) − util(motor)`, mediana das
10 sementes):

| Instância | `ga`: d_n | `ga`: d_u | `ga`: util | `ga-timeline`: d_n | `ga-timeline`: d_u |
|---|---|---|---|---|---|
| apertado | 2 | 0,099 | 0,000 | 2 | 0,099 |
| médio | 15 | 0,506 | 0,000 | 15 | 0,506 |
| folgado | 32 | 0,565 | 0,012 | 33 | 0,577 |

**Leitura pelas regras** (as regras citam o `ga`; o `ga-timeline` vai como complemento e dá o mesmo
rótulo):

- **H1 sustentada: NÃO.** Ela pede `d_n ≤ 1` e `d_u ≤ 0,05` nas três instâncias, e nenhuma satisfaz.
- **H2 ou H3 em geral: SIM**, no médio (15, 0,506, util 0,000) e no folgado (32, 0,565, util 0,012).
  O apertado não satisfaz: 2 < 3 e 0,099 < 0,15.

## 6. Comparação dentro do motor (3b)

**[observado]** Três soluções sob a função de **um** motor, nunca a de outro:

- `p_e`: a solução que o motor encontrou;
- `p_g`: o plano do guloso, codificado na representação desse motor;
- `p_0`: o plano vazio.

`F` é `FitnessBreakdown.selectionScore()`, o número pelo qual a seleção ordena.

**Como `p_e` foi lido.** O motor não devolve o cromossomo macro do `ga`, e o `ga-timeline` descarta o
melhor indivíduo quando ele é vazio. Por isso `SearchReplay` refaz a busca em código de teste, com as
mesmas classes públicas de produção, na mesma ordem e sob a mesma semente. A conferência é a seguinte:

- nos 7 `ga` aceitos, `search-final-best` publicado é **bit a bit** o do replay;
- nos 30 `ga-timeline`, o replay termina vazio exatamente quando o motor recusa.

Nas 23 recusas do `ga` não há número publicado para conferir (ver §11).

**Codificação de `p_g`.**

- `ga`: sessões por tópico do plano do guloso, com 0 para os tópicos não agendados, sem piso e sem
  reparo.
- `ga-timeline`: o calendário do guloso reconstruído por `PlanScoring.rebuild`.

Tudo isso coube em teste, sem acesso a internos não públicos.

| Instância | Motor | `p_e` (tópicos/sessões) | F(p_e) | F(p_g) | F(p_0) |
|---|---|---|---|---|---|
| apertado | ga | 35/47 (igual nas 10 sementes) | 0,3928 | 0,0000 (bruto −0,270) | NaN |
| médio | ga | 35/47 (igual nas 10 sementes) | 0,3928 | 0,0000 (bruto −0,078) | NaN |
| folgado | ga | 35/52 | 0,4151–0,4160 | 0,2824 | NaN |
| apertado | ga-timeline | 0/0 | NaN | −0,7701 | NaN |
| médio | ga-timeline | 0/0 | NaN | −0,5876 | NaN |
| folgado | ga-timeline | 0/0 | NaN | −0,2271 | NaN |

Contagens, em 10 sementes, comparando como a regra escreve (IEEE: qualquer comparação com `NaN` é
falsa):

| Instância | Motor | F(p_g) > F(p_e) | F(p_g) ≤ F(p_e) | A posteriori: p_g acima de p_e | F(p_0) ≥ F(p_e) | Rótulo pela regra |
|---|---|---|---|---|---|---|
| apertado | ga | 0 | 10 | 0 | 0 | inconclusivo |
| médio | ga | 0 | 10 | 0 | 0 | inconclusivo |
| folgado | ga | 0 | 10 | 0 (agregado) | 0 | inconclusivo |
| apertado | ga-timeline | 0 | 0 | 0 | 0 | inconclusivo |
| médio | ga-timeline | 0 | 0 | 0 | 0 | inconclusivo |
| folgado | ga-timeline | 0 | 0 | 0 | 0 | inconclusivo |

"A posteriori: p_g acima de p_e" usa o agregado publicado pelo avaliador a posteriori, que vale 0,0000
para todo plano aceito de todo motor nas três instâncias. Nas recusas, `p_e` entregue não existe e o
escore é `NaN`.

**Objeções**, registradas sem trocar o rótulo:

1. **`ga`: a regra supõe que `p_e` é o plano pequeno, e aqui ele não é.** O cromossomo que a busca
   entrega tem os 35 tópicos, e a função do `ga` o põe acima do guloso em 30/30. A busca não falhou e o
   objetivo não prefere o pequeno: o plano fica pequeno **depois** da busca, na colocação (§10, H-B).
   Isso é H2b, que nenhum dos três rótulos do 3b cobre.
2. **`ga`: o agregado a posteriori empata em 0 e não ordena.** Com o escore bruto, o guloso (−0,227)
   fica acima do `ga` nos 7 aceitos do folgado (−0,700 a −0,768). Mesmo assim o rótulo "objetivo prefere
   o pequeno" não se aplicaria, pela objeção 1.
3. **`ga-timeline`: pela ordem que o motor usa, o rótulo seria "o objetivo prefere o vazio", em
   30/30.** `Population.getFittest` compara com `Comparator.comparingDouble`, isto é, `Double.compare`,
   para o qual `NaN` é maior que qualquer número. Com essa ordem, `F(p_0) ≥ F(p_e)` vale em 30/30 e
   `F(p_g) > F(p_e)` em 0/30. O termo que explica a diferença é **`dailyLoadBudget`, que vale `NaN` no
   plano vazio**. Nenhum termo "prefere" o vazio numericamente: o vazio vence porque o seu `F` não é um
   número. Decomposição de `p_0` e `p_e`, idêntica:
   - `syllabusMastery` 0;
   - `retention` 0;
   - `dailyLoadBudget` **NaN**;
   - `MinimumDaysConstraint` −0,500;
   - `MandatoryReviewConstraint` −0,500;
   - `SoftPrerequisiteOrderConstraint` 0.

   O `ga` não é afetado da mesma forma: o cromossomo macro tem piso de 1 sessão por item e nunca é
   vazio.

**Observação lateral [observado]:** no caminho tático, `MandatoryReviewConstraint` vale 1,0 (−0,500) em
todo plano destas instâncias, inclusive no do guloso, com histórico vazio. Isso não explica a recusa,
mas é um termo constante nestas entradas.

## 7. Curva de escala (passo 4, perfil médio)

Para cada k, ficam os k primeiros tópicos por `position`, sem as arestas para tópicos removidos. A meta
é por disciplina e fica, porque há uma disciplina só.

**[suposição de projeto]** A disponibilidade foi reescalada tomando **janelas inteiras em ordem
cronológica** até o prefixo de capacidade mais próxima de `rho × demanda(k)`. Encurtar as janelas daria
um rho exato, mas mudaria o comprimento delas, que é uma das variáveis suspeitas. O rho efetivo ficou
entre 0,730 e 0,768.

**[observado]**

| k | rho efetivo | Capacidade | Guloso n / util | `ga` n mediana [mín–máx] | `ga` util | `ga` aceitos | d_n | d_u |
|---|---|---|---|---|---|---|---|---|
| 10 | 0,736 | 390 | 3 / 0,423 | 5 [5–5] | 0,615 | 10/10 | −2 | −0,192 |
| 15 | 0,768 | 630 | 3 / 0,262 | 7 [7–7] | 0,476 | 10/10 | −4 | −0,214 |
| 20 | 0,730 | 840 | 5 / 0,363 | 11 [11–11] | 0,798 | 10/10 | −6 | −0,435 |
| 25 | 0,763 | 1080 | 9 / 0,444 | 13 [13–13] | 0,713 | 10/10 | −4 | −0,269 |
| 30 | 0,750 | 1230 | 11 / 0,472 | 12 [12–12] | 0,585 | 10/10 | −1 | −0,114 |
| 35 | 0,747 | 1620 | 15 / 0,506 | 0 [0–0] | 0,000 | 0/10 | 15 | 0,506 |

**Leitura pela regra:** **H3 (escala) sustentada, com k = 35.** A mediana de d_n fica abaixo de 3 de
k = 10 a 30 e chega a 15 em k = 35.

**Objeção**, sem trocar o rótulo: k = 35 é exatamente o ponto em que entram as posições 34 e 35, os
dois tópicos EXTENDED de 150 min, maiores que qualquer janela do médio (90 min). Até k = 30 o `ga`
agenda **mais** tópicos que o guloso em todos os pontos. A curva confunde tamanho do problema com
presença de um tópico que não cabe em janela nenhuma. O experimento que separa os dois está em §10, H-B.
Também **[observado]**: em cada k o `ga` deu o mesmo resultado nas 10 sementes.

## 8. `plan-would-be-empty`

**a) Determinismo [observado].** O `ga-timeline` recusa em **10/10** sementes nas três instâncias, com o
código `plan-would-be-empty` e esta mensagem exata:

> No session fits: the availability windows inside the horizon cannot hold even the first topic of the
> study order. An empty plan is refused rather than returned, because the platform rejects one anyway.

O `offending` é sempre `04a8100d-…` (posição 1, 25 min). `TimelinePlanEngine.java:183` nomeia
`request.topics().get(0)`, o primeiro tópico **do pedido**, e não o da ordem estudada, então esse campo
não informa nada sobre a causa.

**b) Os outros motores [observado].** O guloso aceita as três instâncias. O `ga` recusa com o mesmo
código e a mesma mensagem em 10/10 no apertado, 10/10 no médio e 3/10 no folgado (sementes 1, 3 e 7).
Nas 23 recusas do `ga`, o `offending` é `4298f2ec-…`, **posição 34, EXTENDED, 150 min**: o primeiro
tópico da ordem de estudo do `ga` (`GeneticPlanEngine.java:176` nomeia `order.get(0)`).

**c) Onde e por quê [observado no código].** `sinapse/TimelinePlanEngine.java:176-184`:

```java
Searched searched = search(request, context, graph, topicIdsByItem);
TacticalStudyPlan fittest = searched.fittest();
if (fittest.getSchedule().isEmpty()) {
    throw new PlanRejectedException("plan-would-be-empty", ...,
            List.of(request.topics().get(0).id().toString()));
}
```

A condição é que o melhor indivíduo da última geração tenha zero blocos. Todo indivíduo já foi
decodificado e reparado por `TimelineRepairer.place` (`timeline/TimelineRepairer.java:274-292`), que
reempacota o calendário e **retorna no primeiro bloco que não cabe** (`:282-285`). O teste acontece
antes da resposta, antes do reparo lexicográfico (que nem roda: o padrão deste motor é `WEIGHTED`) e
antes de `PlanOutputInvariants`. Não é uma filtragem.

No `ga` a mesma exceção vem depois da colocação (`sinapse/GeneticPlanEngine.java:169-177`); no guloso,
em `baseline/GreedyBaselineScheduler.java:155-160`.

**d) Reprodutores [observado].**

- **Critério**, fixado antes de reduzir: o erro tem de ocorrer em pelo menos tantas sementes quanto na
  partida. A partida foi o apertado, 10/10, então o critério é 10/10.
- **Método:** redução por bisseção (`ddmin`), primeiro tópicos com as suas arestas, depois janelas,
  depois a meta. O resultado é 1-mínimo, não mínimo global.

| Arquivo | Conteúdo | `ga-timeline` | Guloso |
|---|---|---|---|
| `src/test/resources/instances/reproducers/ga-timeline-empty-minimal.json` | 1 tópico (posição 35, 150 min), 1 janela de 70 min, sem meta | 10/10 | recusa |
| `.../reproducers/ga-timeline-empty-minimal-feasible.json` | 2 tópicos (posição 31, 50 min; posição 34, 150 min), sem aresta, 1 janela de 70 min, sem meta | 10/10 | **aceita** |

A primeira é a redução pedida, e ela **degenera**: chega a um pedido em que nada cabe, e ali a recusa é
legítima. A segunda acrescenta ao critério "o guloso ainda aceita", ou seja, que existe plano não vazio.
**É ela que isola o defeito.** Existe um plano de 1 sessão de 50 min, e o `ga-timeline` devolve o vazio
em 10/10.

**e) A trajetória [observado], com o replay parado em cada geração** (com a mesma semente, parar na
geração g dá a população g da execução completa):

- **Geração 0 do reprodutor viável:** 20 dos 40 indivíduos vazios, os 20 com `F = NaN`. O melhor `F`
  finito entre os não vazios é −0,6057. O `getFittest` escolhe um vazio.
- **Geração 0 das três instâncias completas:** 2 a 6 dos 40 vazios, todos com `F = NaN`, e o
  `getFittest` escolhe um vazio em 30/30.
- **Depois da geração 0:** nos dois reprodutores, o melhor indivíduo tem **0 sessões em todas as
  gerações de 0 a 60, nas 10 sementes**, 610 de 610 linhas. É **vazio desde a primeira geração**; não
  degenera ao longo dela. O elitismo (`TimelineSearch.java:130`) carrega o vazio adiante.
- **O plano que o motor teria devolvido antes do teste** tem 0 sessões, com a decomposição de §6,
  objeção 3: `dailyLoadBudget = NaN`, e o bruto e `F` são `NaN`.

## 9. U por instância

Pela fórmula tudo-ou-nada: ordena `estimatedMinutes` em ordem crescente e acumula até estourar a
capacidade. U ignora precedência, fragmentação e colocação: é folga, não alvo.

| Instância | Capacidade | U | n do guloso | U ≥ n do guloso? |
|---|---|---|---|---|
| apertado | 760 | 17 | 2 | sim |
| médio | 1620 | 30 | 15 | sim |
| folgado | 3240 | 35 | 33 | sim |

A regra de parada (U < n do guloso) não disparou.

**Complemento [observado], fora da fórmula pedida:** quantos tópicos têm a primeira sessão maior que
**qualquer** janela e por isso nunca podem ser colocados:

| Instância | Maior janela | Tópicos que nunca cabem | Posições |
|---|---|---|---|
| apertado | 70 min | 10 | todos os de 90 e de 150 min (3, 4, 15, 16, 18, 24, 32, 33, 34, 35) |
| médio | 90 min | 2 | 34 e 35 |
| folgado | 120 min | 2 | 34 e 35 |

Com essa restrição, o U do folgado cai para 33, exatamente o n do guloso.

## 10. Hipóteses de causa

Nenhuma delas foi corrigida. Os três primeiros itens tocam fitness, seleção ou colocação, e o que fazer
com eles é a **decisão D2**.

**H-A: o vazio vence a seleção por `NaN` (`ga-timeline`).**

- **[observado]** Os componentes:
  - `sinapse/DailyLoadBudgetObjective.java:103-107` devolve `NaN` quando `totalDays <= 0`;
  - `:87` mantém o `NaN`, porque `Math.clamp(NaN, …)` é `NaN`;
  - `ga/fitness/FitnessEvaluator.java:133,141` propaga o `NaN` ao `F`;
  - `ga/Population.java:63-65` usa `Comparator.comparingDouble`, que põe `NaN` no topo;
  - `sinapse/timeline/TimelineSearch.java:130` mantém o melhor por elitismo.
- **[hipótese]** Que isso **basta** para causar a recusa, porque a soma dos componentes não foi testada
  isoladamente. `TournamentSelection.java:70` compara com `>`, e `NaN` nunca vence ali, mas também não é
  substituído quando sorteado primeiro.
- **Experimento:** no reprodutor viável, refazer a busca com um avaliador de teste que dê ao plano vazio
  um `F` finito (por exemplo, o bruto sem o termo `NaN`). A hipótese se confirma se o melhor indivíduo
  passar a ter a sessão de 50 min nas 10 sementes.

**H-B: colocação de bloco inteiro por janela, parada no primeiro que falha, e uma ordem que põe o
tópico grande na frente (`ga`, e também o guloso).**

- **[observado no código]**
  - `plan/AvailabilityAllocator.java:112-127`: um bloco nunca se divide entre janelas, e, quando não
    cabe na janela corrente, `current++` (`:124`) avança. Um bloco maior que toda janela **consome todas
    as janelas restantes**.
  - `sinapse/SessionPlacement.java:115-117`: para no primeiro tópico que não cabe.
  - `sinapse/SinapseStudyOrder.java:76-77`: ordena por mais sessões primeiro.
  - `sinapse/SinapseMinimumDays.java:100`: o piso de sessões cresce com `estimatedMinutes`.
  - `sinapse/SessionBudget.java:54`: no apertado e no médio o orçamento é a soma dos pisos (47 contra
    12 e 26 "cabíveis"), então o cromossomo **é** o vetor de pisos.
- **[observado]**
  - O tópico 34 tem 150 min, é raiz do grafo HARD e tem o maior piso: 3 no apertado e no médio,
    2 no folgado.
  - O `offending` das 23 recusas do `ga` é ele.
  - Uma simulação em Python da ordem e do alocador, fora do repositório, prevê 0 tópicos para o `ga` no
    apertado e no médio, e 2, 15 e 33 para o guloso, exatamente o medido. **É uma reconstrução, não uma
    medição do motor.**
- **[hipótese]** É a causa do `ga` com 0 a 3 tópicos e também do teto do guloso. O guloso ordena por
  posição e para na posição 3 (90 min) no apertado, na 16 no médio e na 34 no folgado. Os 33 do guloso
  na auditoria batem com o folgado, mas a auditoria não afirma qual entrada usou.
- **Experimentos:**
  1. Repetir o passo 3 sem os tópicos cuja primeira sessão excede a maior janela (34 e 35 no médio e no
     folgado). Esperado: o `ga` sobe para perto de U em todas as sementes.
  2. Repetir a curva do passo 4 trocando k = 35 por "k = 35 menos 34 e 35". Esperado: d_n < 3, o que
     derruba o rótulo H3.

**H-C: no apertado e no médio a busca do `ga` é um ponto só.**

- **[observado]** `p_e` é idêntico nas 10 sementes (35/47, F = 0,3928). O orçamento de sessões iguala a
  soma dos pisos, então a geração 0 são 40 cópias do mesmo plano, a situação G20 de `CLAUDE.md` §5b.
- **[hipótese]** Isso não causa a recusa, mas significa que, nesses perfis, a semente e o orçamento de
  busca não mudam nada.
- **Experimento:** ler `search-initial-distinct-fitness` numa entrada em que o `ga` aceite.

**O que os dados não sustentam.** H3, no sentido de busca, orçamento ou inicialização, como causa
principal:

- no 3b do `ga`, a busca acha um cromossomo que a função põe acima do guloso em 30/30;
- no `ga-timeline`, o vazio está na geração 0, e mais gerações não o tirariam dali, por H-A.

Subir gerações ou população não é sugerido por nenhum número aqui.

## 11. Limitações

- As instâncias são sintéticas, do catálogo de exemplo: uma disciplina, uma meta e nenhum histórico.
  Nada que dependa de histórico foi exercitado.
- O cenário exato da auditoria não é reconstruível, segundo o README das instâncias. A semelhança com o
  folgado (33) é indício, não identidade.
- O tempo de parede vem de uma JVM só, com aquecimento do JIT e num contêiner compartilhado. Só serve
  como ordem de grandeza, e varia entre execuções; o resto do CSV é determinístico.
- **Replay:** conferido bit a bit nos 7 `ga` aceitos, e em vazio ⇔ recusa nos 30 `ga-timeline`. Nas 23
  recusas do `ga` não há valor publicado para conferir, e lá a identidade é inferida: as mesmas chamadas
  e a mesma semente.
- **3b com `NaN`:** a regra foi aplicada como escrita (IEEE). O efeito da ordem `Double.compare` está na
  objeção, e não no rótulo.
- **Curva de escala:** o rho efetivo variou de 0,730 a 0,768, porque as janelas foram mantidas inteiras;
  o horizonte não mudou.
- **Reprodutores:** são mínimos locais (1-mínimos) da redução `ddmin`, não mínimos globais.
- **U:** a fórmula pedida ignora o comprimento das janelas, que aqui é o fator dominante. O complemento
  de §9 é uma leitura adicional e não substitui U.

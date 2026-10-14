# Reparo de escala com o catálogo real (EOA-12)

Este documento relata o EOA-12: os experimentos da Fase 0, a correção da Fase 1 e as decisões que a
evidência levanta (Fase 2). Ele não substitui o diagnóstico datado do EOA-10,
[`DIAGNOSTICO_ESCALA_REAL.md`](./DIAGNOSTICO_ESCALA_REAL.md), que fica como está.

Decisões que regem o trabalho, tomadas pelo dono do projeto em 2026-10-08:
[ADR-0010](./adr/0010-criterio-de-reparo-do-ag.md) (D1, o que encerra o reparo) e
[ADR-0011](./adr/0011-escopo-do-reparo-do-ag.md) (D2, o que o reparo pode tocar).

Rótulos: **[OBSERVED]** saiu de uma execução, com o número no CSV ou na saída citada;
**[READ]** lido no código, com `arquivo:linha`; **[HYPOTHESIS]** explicação compatível com o
observado e não testada; **[PROPOSAL]** opção para o dono decidir, sem escolha.

Nenhuma linha deste documento afirma que um motor é superior a outro. A comparação com o guloso é a
estatística fixada pela D1: por instância, a mediana sobre as 10 sementes das diferenças pareadas por
semente (`ga-timeline` menos guloso), sem teste de significância.

## 1. Resultado

1. **[OBSERVED]** O `ga-timeline` passa a responder 30 de 30 execuções do catálogo real (3 instâncias
   × sementes 1 a 10) com plano não vazio que passa por `PlanOutputInvariants`. Antes: 0 de 30. O
   reprodutor viável passa de 0/10 para 10/10, com a sessão de 50 min que cabe.
2. **[OBSERVED]** As respostas do guloso e do `ga` são idênticas antes e depois, nas 100 execuções
   comparadas (hash canônico, §5.3).
3. **[OBSERVED]** O `ga` continua recusando 23 de 30. O mecanismo não é aptidão não finita: é a
   colocação, que para no primeiro tópico da ordem de estudo, e esse tópico nunca cabe (§3.3).
4. **[OBSERVED]** No folgado o `ga-timeline` produz **um único plano** nas 10 sementes: ali o n efetivo
   é 1 (§5.2).

## 2. Ambiente

| Item | Valor |
|---|---|
| Base | `origin/main` em `09e0e76` (PR #36 do EOA-13 e PR #32 do EOA-10 mergeados) |
| Linha de base | `./mvnw verify` verde em `09e0e76`: 514 testes, 5 ignorados; INSTRUCTION 11384/12095 = 0,9412, BRANCH 809/922 = 0,8774 |
| Depois da correção | `./mvnw verify` verde: 571 testes, 5 ignorados; INSTRUCTION 11419/12128 = 0,9415, BRANCH 816/928 = 0,8793 |
| JDK | OpenJDK 21.0.12.1 (o EOA-10 usou 21.0.11; os números do E0 reproduzem os dele exatamente) |
| Orçamento | 60 gerações × população 40 (`application-baseline-core.properties`), sem mudança |

Para reproduzir o antes e o depois, no commit desejado:

```bash
./mvnw test -Dtest=RealCatalogRepairTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
```

O CSV sai em `target/diagnostic/reparo.csv`. As cópias publicadas estão em
[`reparo-escala-real/`](./reparo-escala-real/): `antes.csv` (rodado em `09e0e76` com o mesmo arquivo
de teste) e `depois.csv`. Tudo é determinístico exceto `wall_ms`.

## 3. Fase 0 — experimentos

Nenhuma mudança em `src/main` foi feita nesta fase. O código dos experimentos E1 a E3 foi teste
descartável, fora do commit.

### 3.1 E0 — linha de base

**[OBSERVED]** Harness do EOA-10 (`EngineProbe`, pelo seletor de produção), em `09e0e76`, sementes 1 a
10, os três motores. Bate com o diagnóstico em todos os pontos que ele registra:

| Instância | Guloso | `ga` | `ga-timeline` |
|---|---|---|---|
| apertado | 10/10 aceitos; 2 tópicos, util 0,099 | 0/10 | 0/10 |
| médio | 10/10 aceitos; 15 tópicos, util 0,506 | 0/10 | 0/10 |
| folgado | 10/10 aceitos; 33 tópicos, util 0,577 | 7/10 (recusa nas sementes 1, 3 e 7) | 0/10 |
| reprodutor viável | 10/10 aceitos; 1 sessão de 50 min | 0/10 | 0/10 |
| reprodutor degenerado | 0/10 (nada cabe) | 0/10 | 0/10 |

Todas as recusas são `plan-would-be-empty`. `ga-timeline` recusa 30/30 e o `ga` 23/30, como no
diagnóstico. O hash de cada resposta foi gravado fora do repositório. Esse primeiro hash não ordenava
as chaves de `fitness` e por isso não é comparável entre JVMs (§5.3). Nas 47 respostas aceitas, as
sessões e todas as métricas do E0 coincidem com `reparo-escala-real/antes.csv`, que é a mesma
medição refeita com o hash canônico.

**O que confere com o código atual [READ@09e0e76]:** `TimelineRepairer.place` em
`TimelineRepairer.java:274-292`, com retorno no primeiro bloco que não cabe em `:283-284`;
`DailyLoadBudgetObjective.java:103-107`; `Population.java:63-65`; elitismo em `TimelineSearch.java:130`;
a recusa do `ga` em `GeneticPlanEngine.java:169-177`; `AvailabilityAllocator.java:112-127`;
`SessionPlacement.java:115-117`. Nenhuma divergência com o contexto registrado.

### 3.2 E1 — suficiência de H-A

**Variante descartável, inteira em `src/test`.** Um decorador de `FitnessEvaluator` devolvia
`Double.isFinite(f) ? f : Double.NEGATIVE_INFINITY` em `evaluate` e delegava `explain`. Um
`Mockito.spy` da composição de produção fazia `evaluator()` devolver o decorador, e um
`TimelinePlanEngine` foi montado com essa composição e os demais beans de produção. Nada de `src/main`
mudou. **Pior valor usado: `-Infinity`.** Como `Individual.calculateFitness` é o único chamador de
`evaluate` em produção, o decorador equivale a corrigir a aptidão em cache logo depois do cálculo
(§4.1).

**[OBSERVED]** Por semente, 1 a 10:

| Instância | Recusou? | Sessões | Tópicos | `PlanOutputInvariants` |
|---|---|---|---|---|
| reprodutor viável | não, 10/10 | 1 em todas | 1 em todas (o de 50 min) | passou 10/10 |
| apertado | não, 10/10 | 13, 13, 13, 13, 13, 13, 13, 13, 13, 12 | iguais às sessões | passou 10/10 |
| médio | não, 10/10 | 22, 18, 20, 20, 21, 19, 21, 21, 18, 21 | 20, 15, 18, 18, 18, 17, 18, 20, 15, 19 | passou 10/10 |
| folgado | não, 10/10 | 45 em todas | 33 em todas | passou 10/10 |
| reprodutor degenerado | sim, 10/10 | 0 | 0 | — (nada cabe; o indivíduo mais apto da última geração tem 0 blocos) |

No reprodutor viável o `F` do plano devolvido é −0,6057, o mesmo "melhor F finito entre os não vazios"
da geração 0 que o diagnóstico registrou (§8e).

**Veredito do portão: PASSOU.** O reprodutor viável e as três instâncias deram plano não vazio e
válido em 10/10 sementes cada.

### 3.3 E2 — as 23 recusas do `ga`, sem mudar código

**[OBSERVED]** Para cada recusa, o replay da busca (`SearchReplay.ga`, mesmas classes de produção,
mesma semente) deu o cromossomo vencedor. A ordem e a colocação foram refeitas com `SinapseStudyOrder`,
`PrerequisiteOrderRepairer` (o padrão `LEXICOGRAPHIC` deste motor) e `SessionPlacement.place`.

| Instância | Sementes | Exceção | `offending` | Onde a colocação parou | Nunca cabe? | Janelas (maior) | `F` do indivíduo mais apto | Cromossomo |
|---|---|---|---|---|---|---|---|---|
| apertado | 1–10 | `plan-would-be-empty` | `4298f2ec-…` = 1º da ordem | 1º tópico: posição 34, 150 min, EXTENDED | sim | 12 (70 min) | 0,3928, finito | 35 tópicos, 47 sessões |
| médio | 1–10 | `plan-would-be-empty` | `4298f2ec-…` = 1º da ordem | 1º tópico: posição 34, 150 min, EXTENDED | sim | 25 (90 min) | 0,3928, finito | 35 tópicos, 47 sessões |
| folgado | 1, 3, 7 | `plan-would-be-empty` | `4298f2ec-…` = 1º da ordem | 1º tópico: posição 34, 150 min, EXTENDED | sim | 29 (120 min) | 0,4160, 0,4158, 0,4159, finitos | 35 tópicos, 52 sessões |

"Nunca cabe" foi testado com o próprio alocador: `AvailabilityAllocator.over(pedido).place(150)` vazio
numa agenda ainda sem nada colocado.

Comparação com o guloso nas mesmas instâncias **[OBSERVED]**: com o mesmo alocador, o guloso coloca 2,
15 e 33 tópicos e para no primeiro tópico que não coube na **sua** ordem. No apertado é a posição 3
(90 min), que também nunca cabe. No médio é a posição 16 (90 min), que caberia numa janela vazia: ali
ele para por falta de janela livre, não por tamanho. No folgado é a posição 34 (150 min), que nunca
cabe. A diferença está na ordem: o guloso começa por tópicos que cabem, e o `ga` começa pelo tópico de
150 min em todas as 23 recusas.

**Conclusão: colocação**, com prefixo vazio porque o primeiro tópico da ordem nunca cabe. **[OBSERVED]**
O `F` do indivíduo mais apto é finito nas 23 recusas, então não é o mecanismo do `ga-timeline`. O
reprodutor viável também é recusado pelo `ga` em 10/10 (E0). Que o mecanismo ali seja o mesmo é
**[HYPOTHESIS]**: o E2 não foi rodado sobre ele.

### 3.4 E3 — tamanho do problema que o alocador impõe

**[OBSERVED]** "Nunca cabe" é testado com o alocador, como no E2. A geração 0 é a do `ga-timeline`
antes da correção, refeita por `SearchReplay.timeline(pedido, 0)`.

| Instância | Maior janela | Tópicos com minutos > maior janela | Posições | Vazios na geração 0, sementes 1–10 (de 40) |
|---|---|---|---|---|
| apertado | 70 min | 10 | 3, 4, 15, 16, 18, 24, 32, 33, 34, 35 | 3, 2, 2, 6, 4, 2, 2, 3, 2, 4 (5 % a 15 %) |
| médio | 90 min | 2 | 34, 35 | 3, 2, 2, 6, 4, 2, 2, 3, 2, 4 |
| folgado | 120 min | 2 | 34, 35 | 3, 2, 2, 6, 4, 2, 2, 3, 2, 4 |
| reprodutor viável | 70 min | 1 | 34 | 20, 18, 18, 19, 21, 15, 14, 18, 19, 21 |

Os números de tópicos que nunca cabem (10, 2 e 2) conferem com o diagnóstico (§9). A contagem de vazios
na geração 0 é **idêntica nas três instâncias** para cada semente. Por quê: **[HYPOTHESIS]** as
permutações sorteadas são as mesmas, e um indivíduo só fica vazio quando a sua ordem começa por um dos
dois tópicos de 150 min. Os demais tópicos que não cabem no apertado não seriam raízes do grafo `HARD`
e não poderiam abrir a ordem. Não foi verificado.

## 4. Fase 1 — a correção

### 4.1 Ponto da correção

**[READ]** No caminho do `ga-timeline`, a aptidão é **calculada** por
`Individual.calculateFitness` (`ga/Individual.java:52`), que chama `context.fitnessEvaluator()`, e é
**atribuída** por `Population.calculateFitness` (`ga/Population.java:155-158`). O avaliador vai para o
contexto em `TimelinePlanEngine.java:172`. As duas classes de `ga/` estão na lista protegida. Quem
**entrega** a aptidão ao laço é `TimelineSearch`, logo depois de cada cálculo: `TimelineSearch.java:109`
(geração 0) e `:136` (cada geração), antes de qualquer leitor (`getFittest` em `:117` e `:130`, o
torneio em `:142-143`, `SearchVitality` em `:118`). Esses números de linha são de `09e0e76`.

`TimelineSearch` é o laço próprio da v2 e só o `TimelinePlanEngine` o usa (e o `SearchReplay` de
teste). **Ele não é uma das seis classes protegidas**, e o seu Javadoc já diz que não toca nenhuma
delas.

### 4.2 O teste vermelho primeiro

`src/test/java/.../sinapse/LinhaDoTempoPlanoNaoVazioTest.java`, commitado antes da correção:

- o reprodutor viável, sementes 1 a 10: resposta não vazia, só com o tópico de 50 min, e válida;
- o portão da D1: as três instâncias × sementes 1 a 10, resposta não vazia e válida.

Ele sobe o contexto e chama o seletor injetado, com o orçamento de produção. **[OBSERVED]** Antes da
correção: 40 testes, 40 falhas, todas `AssertionError: ga-timeline recusou com plan-would-be-empty
(semente N): No session fits: …`. O reprodutor degenerado não é usado, porque nele a recusa está certa.

O teste de unidade da regra é `sinapse/timeline/NonFiniteFitnessTest`. `NaN`, `+Infinity` e
`-Infinity` viram o pior valor. Doze valores finitos (incluindo `-0.0`, `±Double.MIN_VALUE` e
`±Double.MAX_VALUE`) saem idênticos por `doubleToRawLongBits`. Numa população, só o não finito é
regravado.

### 4.3 A correção

`sinapse/timeline/NonFiniteFitness`: `orWorst(f)` devolve `f` quando finito e
`Double.NEGATIVE_INFINITY` caso contrário. `demote(população)` regrava, no cache, só a aptidão não
finita. `TimelineSearch` chama `demote` logo depois de cada `calculateFitness`. São 9 linhas em
`TimelineSearch` e uma classe nova. Nenhum termo de fitness, nenhum operador e nenhuma das seis
classes mudou.

**Por que `-Infinity`, e o que foi verificado nos consumidores [READ]:**

- `0,0` não serve. A seleção ordena planos táticos pelo bruto quando ele é negativo
  (`FitnessEvaluator.selectionScoreOf`, `FitnessBreakdown.selectionScore()`). Os planos não vazios
  destas instâncias pontuam abaixo de zero (−0,6057 no reprodutor viável), então o vazio continuaria
  acima deles.
- Não existe no código um piso finito da aptidão de seleção. O sentinela de "pior" que existe é
  `Double.NEGATIVE_INFINITY`, com que `GeneticAlgorithm` inicia `bestFitnessSoFar`, e foi esse o usado.
- `Population.getFittest` (`Comparator.comparingDouble`, isto é, `Double.compare`) põe `-Infinity`
  abaixo de todo finito.
- `TournamentSelection` compara com `>`, e um contendor finito sempre troca um `-Infinity`. Com `NaN`,
  um contendor sorteado primeiro nunca era trocado.
- `SearchVitality.of` põe as aptidões num `HashSet<Double>`. `-Infinity` conta como um valor, como
  `NaN` contava. `best()` publica `search-initial-best` e `search-final-best`; nas 40 respostas
  aceitas do `ga-timeline` os dois são finitos (`depois.csv`).
- Soma, média e log: `Population.getAverageFitness` não tem chamador em `src/main`. Os dois pontos que
  leem aptidão para log ou estagnação (`DefaultPopulationGenerator`, `GeneticAlgorithm.trackStagnation`)
  são do caminho do `ga` e não veem este valor. A resposta publicada é montada por `explain`, que não
  lê a aptidão em cache.

**[OBSERVED]** A correção dá as mesmas sessões que o decorador da E1 nas 40 execuções do `ga-timeline`
(hash de `sessions` igual 40/40).

## 5. Verificação, com o mesmo harness, sementes e avaliador do E0

`RealCatalogRepairTest` rodou em `09e0e76` (antes) e no commit da correção (depois), sobre as três
instâncias e os dois reprodutores, os três motores, sementes 1 a 10.

### 5.1 Portão (D1)

**[OBSERVED]** `ga-timeline`, 3 instâncias × 10 sementes: **30/30 não vazios**, e todos passam por
`PlanOutputInvariants`, tanto dentro do motor quanto na reconferência do harness. Antes: 0/30.
`LinhaDoTempoPlanoNaoVazioTest` cobra isso no `verify`. No reprodutor viável, 10/10 com a sessão de 50
min (antes 0/10). No degenerado, onde nada cabe, a recusa continua em 10/10 nos três motores.

### 5.2 Hipótese (D1): medida e relatada, não é portão

Diferenças pareadas por semente, `ga-timeline` menos guloso. Por instância: mediana [mínimo–máximo]
sobre as 10 sementes. Métricas do avaliador a posteriori do EOA-10 (`EngineProbe` + `PlanScoring`).
A última coluna conta os planos distintos do `ga-timeline` nas 10 sementes, pelo hash de `sessions`.

| Instância | d tópicos | d sessões | d minutos | d util | d `syllabusMastery` | d agregado (objetivo do AG) | d bruto | Planos distintos do `ga-timeline` |
|---|---|---|---|---|---|---|---|---|
| apertado | 11 [10–11] | 11 [10–11] | 500 [450–525] | 0,658 [0,592–0,691] | 0,0506 [0,0466–0,0541] | 0 [0–0] | 0,148 [0,138–0,162] | 10 |
| médio | 3 [0–5] | 5,5 [3–7] | 175 [100–250] | 0,108 [0,062–0,154] | 0,0235 [0,0074–0,0329] | 0 [0–0] | 0,101 [0,087–0,132] | 9 |
| folgado | 0 [0–0] | 12 [12–12] | 316 [316–316] | 0,0975 [0,0975–0,0975] | 0,0118 [0,0118–0,0118] | 0 [0–0] | 0,188 [0,188–0,188] | **1** |

- O guloso produz o mesmo plano nas 10 sementes de cada instância, como esperado: ele não sorteia.
- O agregado limitado a `[0,1]` vale 0 para todo plano dos dois motores nas três instâncias e não
  ordena nada. O bruto é o objetivo do genético relatado por completude, não uma comparação entre
  motores (`CLAUDE.md` §5b).
- **Achado: no folgado o n efetivo é 1.** As 10 sementes dão o mesmo plano do `ga-timeline`, então a
  "mediana sobre 10 sementes" ali é uma observação só. No médio, as sementes 2 e 9 coincidem.
- **Achado:** nas 40 respostas aceitas do `ga-timeline`, `search-final-best` é igual a
  `search-initial-best`. As 60 gerações não melhoraram o indivíduo mais apto da geração 0 em nenhuma
  execução.

### 5.3 Regressão

**[OBSERVED]**

- **Guloso e `ga`: idênticos antes e depois em todas as 100 execuções comparadas** (3 instâncias e
  os 2 reprodutores × 2 motores × 10 sementes). As 47 aceitas têm o mesmo SHA-256, listado por linha
  em `antes.csv` e `depois.csv`, coluna `response_sha256`. As 53 recusas têm o mesmo código e a mesma
  mensagem.
- **O que o hash cobre.** A resposta inteira, serializada pelo `ObjectMapper` do contexto com as chaves
  de mapa em ordem, sem `fitness.build`. `fitness.build` é o commit e muda entre o antes e o depois por
  definição. A ordem das chaves é imposta porque `PlanResponse` copia `fitness` com `Map.copyOf`, cuja
  ordem de iteração **muda a cada JVM**. Na primeira tentativa, sem ordenar, os hashes do guloso
  diferiram entre duas execuções do **mesmo** commit pré-correção (E0 e antes), com o mesmo conteúdo.
  Isso é anterior a este trabalho e está registrado como K13 em `docs/ai/STATE.md`. Com a ordenação,
  duas JVMs do mesmo commit dão 0 diferenças nas 120 linhas comparadas.
- Os testes existentes passam sem edição: 514 de 514 que já existiam, mais os 57 novos.
- `GaResultadoInalteradoTest`: 4 de 4, sem edição.

### 5.4 O que o diff toca

`git diff --stat origin/main`: nenhuma das seis classes protegidas, nenhum termo de fitness, nada em
`src/test/resources/contract/` nem em `src/test/resources/instances/` (os reprodutores não foram
tocados). Em `src/main`: `sinapse/timeline/TimelineSearch.java` (+9) e
`sinapse/timeline/NonFiniteFitness.java` (novo). A lista completa está no corpo do PR.

### 5.5 Tempo

Ordem de grandeza, mediana do relógio do harness nas três instâncias, depois da correção: guloso ~1 ms,
`ga` ~10 ms, `ga-timeline` ~100 ms (de 43 a 199 ms). A máquina varia, e nada além da ordem de grandeza
é afirmado.

## 6. O que continua desconhecido

- **[HYPOTHESIS]** Que `search-initial-best` nunca saia `-Infinity` numa resposta aceita. Isso exigiria
  uma geração 0 toda não finita, isto é, toda vazia, e uma geração posterior não vazia. Não foi
  observado, e nem foi provado impossível.
- Por que a contagem de vazios na geração 0 é idêntica nas três instâncias (§3.4): **[HYPOTHESIS]**.
- O mecanismo da recusa do `ga` no reprodutor viável: **[HYPOTHESIS]**, o mesmo do E2.
- O que a plataforma faz com os planos do `ga-timeline`: não verificado, porque nada aqui roda a
  plataforma.
- O `offending` de uma recusa do `ga-timeline` continua sendo o primeiro tópico do **pedido**
  (`TimelinePlanEngine.java:183`) e não informa a causa. A recusa agora só ocorre onde nada cabe.
- Se a busca de 60 × 40 faz alguma diferença no `ga-timeline`: em 40 de 40 o indivíduo mais apto final é
  o da geração 0 (§5.2). Isso não foi investigado.

## 7. Fase 2 — decisões que a evidência levanta

Nada aqui foi implementado. Cada item é **[PROPOSAL]**, com opções e custos e sem escolha. Decidir é
do dono.

### a) Parar no primeiro bloco que não cabe

`AvailabilityAllocator.place` nunca divide um bloco entre janelas e, quando ele não cabe, avança até
consumir as janelas restantes. `SessionPlacement` e `TimelineRepairer.place` param no primeiro tópico
ou bloco que não cabe, e o plano parcial é um prefixo da ordem de estudo, como o README documenta. Pela
E3, 10, 2 e 2 tópicos nunca cabem no apertado, no médio e no folgado.

- **Manter o prefixo.** Custo: nenhum código. O `ga` continua com 0 tópicos sempre que a sua ordem
  começa por um tópico que nunca cabe (23/30), e o guloso continua parando em 2, 15 e 33 tópicos.
- **Pular o tópico que não cabe e seguir.** Custo: os dependentes `HARD` do tópico pulado também teriam
  de sair, ou o plano violaria `PlanOutputInvariants`. O "plano parcial é prefixo da ordem", hoje
  documentado, deixa de valer. Toda medição publicada que usa esse alocador muda.
- **Declarar de antemão os tópicos que nunca cabem** (primeira sessão maior que toda janela) e
  informá-los à plataforma. Isso se liga à D3 do produto, sobre o que o aluno vê. Custo: uma chave nova
  em `fitness`, que é mapa aberto, ou um campo novo no contrato, que exige subir `PlanRequest.VERSION`
  nos dois repositórios. Também não resolve a parada por fragmentação de janela, como no médio do
  guloso (§3.3).

**Toda opção que mude o alocador muda o guloso, que é o motor padrão em produção**
(`plan.engine.default=greedy-baseline`) e o grupo de controle das medições.

### b) O `ga`

A E2 conclui **colocação**: a ordem do `ga` põe primeiro o tópico de 150 min, e ele nunca cabe. Que
ele seja raiz `HARD` e tenha o maior piso de sessões está no diagnóstico (§10, H-B) e não foi refeito
aqui.

- **Não mexer**, e declarar o `ga` inutilizável no catálogo real. Custo: a v1 fica sem resultado no
  catálogo real.
- **Resolver pelo alocador** (item a). Custo: o do item a, inclusive mudar o guloso.
- **Mudar a ordem de estudo do `ga`** (`SinapseStudyOrder`, mais sessões primeiro). Custo: toca o
  caminho do `ga`, que a D2 não autoriza, e move o grupo de controle das medições publicadas (v1).
- **Retirar da entrada do `ga` os tópicos que nunca cabem.** Custo: o `ga` passa a resolver outro
  problema, e a comparação entre motores deixa de ser sobre a mesma instância.

### c) O n efetivo

No folgado o `ga-timeline` dá 1 plano distinto em 10 sementes; no apertado, 10, e no médio, 9. Para o
EOA-9b, uma mediana sobre sementes no folgado é uma observação só.

- **Aceitar e declarar n = 1 no folgado.** Custo: nenhum, mas a célula não sustenta comparação.
- **Outra fonte de variação**: mais instâncias, ou perturbações declaradas da instância (janelas,
  minutos). Custo: instâncias novas são cópias da plataforma (EOA-H2) ou geradas aqui com proveniência
  própria, e cada perturbação é uma escolha de projeto a declarar.

### d) O orçamento de busca (60 × 40)

O `ga-timeline` responde em ~100 ms, e em 40 de 40 execuções aceitas o indivíduo mais apto final é o
da geração 0 (§5.2).

- **Não medir.** Custo: o EOA-9b compara uma busca que, nestas instâncias, não sai da população
  inicial.
- **Medir o orçamento** à parte (gerações e população, e a taxa de `SearchVitality.improved()`). Custo:
  tempo de medição. O orçamento é do Core pela ADR-0009, e mudá-lo exige decisão nova, porque a D2 não
  o autoriza.

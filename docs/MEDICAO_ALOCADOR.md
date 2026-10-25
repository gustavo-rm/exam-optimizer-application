# Medição de duas opções do alocador (EOA-12b)

Este documento relata a medição das opções P e D de
[`REPARO_ESCALA_REAL.md`](./REPARO_ESCALA_REAL.md) §7 (a). **Nada aqui é adotado.** O código de
produção (`src/main`) não muda. A medição vive em `src/test`, e os dados em
[`alocador-opcoes/`](./alocador-opcoes/).

Rótulos: **[OBSERVED]** saiu de uma execução, com o número no CSV ou na saída citada;
**[READ]** lido no código, com `arquivo:linha` em `16be5b9`; **[HYPOTHESIS]** explicação compatível
com o observado e não testada; **[PROPOSAL]** opção para o dono decidir, sem escolha;
**[UNVERIFIED]** não está escrito em lugar que eu tenha lido.

Nenhuma linha deste documento afirma que uma opção ou um motor é superior a outro. Não há teste de
significância.

## 1. A decisão que rege o trabalho (D2b)

Tomada pelo dono do projeto em 2026-10-22 e registrada em
[ADR-0012](./adr/0012-medir-opcoes-do-alocador.md):

- **Autorizado:** MEDIR duas opções de `REPARO_ESCALA_REAL.md` §7 (a), sem adotar nenhuma.
  - **P — "pular e seguir":** quando o bloco de um tópico não cabe, o tópico é pulado, os seus
    dependentes `HARD` (transitivos) também, e a colocação continua com os tópicos seguintes da mesma
    ordem.
  - **D — "declarar de antemão":** classificar, para cada tópico que fica de fora do plano, o porquê.
    É análise e não muda a colocação.
- **Não autorizado:** adotar P ou D; qualquer edição em `src/main` (inclusive "só um gancho", mudança
  de visibilidade ou interface extraída); mudar o motor padrão (`plan.engine.default=greedy-baseline`)
  ou o que o guloso devolve; as seis classes protegidas de `CLAUDE.md` §4; termos de fitness;
  orçamento de busca; operadores; a ordem de estudo do `ga`; retirar tópicos da entrada do `ga`
  (opções b3 e b4 do §7); chave nova em `fitness` ou campo novo no contrato. Tudo isso fica como
  PROPOSAL para o dono, depois dos números.

## 2. Regras de P, fixadas antes de qualquer execução de P

Escritas e commitadas em `07cecbb`, antes do código de P (`e84c6f0`, vermelho; `f2d49bb`, verde) e da
primeira execução da matriz. Não foram ajustadas depois.

| # | Regra |
|---|---|
| P1 | **Passo único, na ordem do próprio motor, sem reordenar.** Guloso: a de `StudyOrder`. `ga`: a de `SinapseStudyOrder`, reparada por `PrerequisiteOrderRepairer` sob `LEXICOGRAPHIC` (o padrão do motor). `ga-timeline`: a ordem topológica que `TimelineRepairer` calcula em cada reparo. |
| P2 | **A unidade de tentativa é a do próprio motor**, como hoje. Guloso: o tópico inteiro, estudo mais a revisão quando `RevisionPolicy` a concede, tudo ou nada (`GreedyBaselineScheduler.java:216-234`). `ga`: a primeira sessão é obrigatória; as revisões são discricionárias, e a primeira que não cabe encerra as revisões do tópico, que fica com o que já tem (`SessionPlacement.java:149-155`). `ga-timeline`: bloco a bloco; o primeiro bloco do item é obrigatório, e um bloco seguinte que não cabe encerra os blocos do item, que fica com os já colocados, como a base fica no item em que trunca (`TimelineRepairer.java:280-285`). |
| P3 | **Uma falha de colocação não altera o estado do alocador.** Cursor e janela voltam ao que eram antes da tentativa. No guloso, o bloco de estudo já colocado de um tópico cuja revisão não coube também é devolvido. Implementação: o alocador de produção, pela API pública; depois de uma falha, um `rewound()` refaz em ordem as durações aceitas (§3.2, E1). |
| P4 | **Tópico pulado implica pular, sem tentativa, todos os seus dependentes `HARD` transitivos**, no grafo da condição de proveniência do pedido (`HardPrerequisiteGraph`). Pular é falhar a unidade obrigatória de P2. |
| P5 | **As sessões de revisão de um tópico pulado também saem.** Nenhuma é tentada. |
| P6 | **Pré-requisito `SOFT` mantém o comportamento atual**, que é [READ]: o guloso não repara nem é restringido por `SOFT` (`GreedyBaselineScheduler.java:144-150`); o `ga` reordena por `PrerequisiteOrderRepairer` antes da colocação, sob `LEXICOGRAPHIC` (`GeneticPlanEngine.java:163-167`); o `ga-timeline` precifica a inversão no fitness, sob `WEIGHTED` (`TimelinePlanEngine.java:161-163`). Pular um tópico **não** pula os seus dependentes `SOFT`. |
| P7 | **Nada é revisitado.** Um tópico pulado não é tentado de novo, nem quando sobra janela depois. |
| P8 | **Todo o resto igual à base**: busca, orçamento, sementes, termos de fitness, operadores, a recusa `plan-would-be-empty` quando o plano sai vazio, e `PlanOutputInvariants`, que não é relaxado. |
| P9 | **`topics-unscheduled-ids` passa a nomear os tópicos do pedido sem sessão**, na ordem do motor. Hoje `PrerequisiteReport` toma os que vêm depois do prefixo (`skip(scheduled)`, `PrerequisiteReport.java:44`), o que sob P nomearia os tópicos errados no guloso e no `ga`. O `ga-timeline` já monta a lista pelo calendário (`TimelinePlanEngine.java:315-321`), e nele nada muda. `soft-prerequisite-inversions` fica calculado como na base, sobre a ordem do motor. Quantas respostas P teriam a lista errada com o código de produção sem ajuste é medido (§7.4). |

## 3. Fase 0 — linha de base e viabilidade

Nada de `src/main` foi tocado nesta fase.

### 3.1 E0 — linha de base

**[OBSERVED]** `RealCatalogRepairTest` com `-Ddiagnostic=true` em `16be5b9` (`origin/main`, PRs #37 e
#38 mergeados), JDK 21.0.12.1, três motores, as três instâncias e os dois reprodutores, sementes 1 a
10: as **150 linhas** coincidem com [`reparo-escala-real/depois.csv`](./reparo-escala-real/depois.csv)
em todas as colunas exceto `wall_ms`, **inclusive `response_sha256` e `sessions_sha256`**.

| Instância | Guloso | `ga` | `ga-timeline` |
|---|---|---|---|
| apertado | 10/10 aceitos, 2 tópicos | 0/10 | 10/10 aceitos (13 tópicos em 9, 12 em 1) |
| médio | 10/10 aceitos, 15 tópicos | 0/10 | 10/10 aceitos (15 a 20 tópicos) |
| folgado | 10/10 aceitos, 33 tópicos | 7/10 (recusa nas sementes 1, 3 e 7) | 10/10 aceitos, 33 tópicos |
| reprodutor viável | 10/10 aceitos, 1 tópico | 0/10 | 10/10 aceitos, 1 tópico |

Guloso 2, 15 e 33 tópicos; `ga-timeline` 30/30 não vazios; `ga` recusa 23/30. **Veredito: a base não
se moveu desde o EOA-12.** O hash canônico de cada uma das 150 respostas foi gravado fora do
repositório e é comparado de novo no fim (§8).

Conferência com o código atual **[READ@16be5b9]**: `AvailabilityAllocator.place` em `:112-127`, com o
avanço de janela em `:124`; `SessionPlacement.place` com a parada em `:115-117`;
`TimelineRepairer.place` em `:274-292`, com o retorno no primeiro bloco que não cabe em `:283-284`;
`GreedyBaselineScheduler.allocate` com a parada em `:201-203`. Duas precisões que o contexto da tarefa
não traz:

- No `ga`, uma **revisão** que não cabe não encerra o tópico (`SessionPlacement.java:149-155`), mas
  consome as janelas restantes como qualquer falha de `place`. O tópico seguinte então também falha, e
  a colocação para ali. Na base, a parada pode portanto vir de uma revisão.
- No guloso, quando a revisão não cabe, o tópico é "devolvido" (`GreedyBaselineScheduler.java:229-231`),
  mas o cursor já passou pelo bloco de estudo e a falha já consumiu as janelas. A devolução não libera
  tempo; ela só tira o tópico do plano.

### 3.2 E1 — viabilidade, por motor

| Motor | (i) Onde fica "parar no primeiro que não cabe" [READ] | (ii) P inteiro em `src/test`? | (iii) A variante muda a busca? | (iv) Motor inteiro ou replay? | Classe |
|---|---|---|---|---|---|
| guloso | `GreedyBaselineScheduler.allocate`, `break` em `:201-203`; a falha consome janelas em `AvailabilityAllocator.java:124` | Sim. Cópia de `schedule` no pacote `baseline` de teste (`StudyOrder`, `RevisionPolicy` e `BaselineFitness.of` são de pacote e ficam visíveis), com `allocate` trocado pela colocação P sobre o alocador de produção | Não há busca. A ordem (`StudyOrder.of`, `:150`) é calculada antes da colocação e não depende dela | Motor inteiro | **COMPLETO** |
| `ga` | `SessionPlacement.place`, `break` em `:115-117` | Sim. Cópia de `GeneticPlanEngine.run` no pacote `sinapse` de teste, com os beans de produção, e cópia de `SessionPlacement.place` com a colocação P | Não. A busca avalia o cromossomo macro, e `SessionPlacement.place` só é chamado em `GeneticPlanEngine.java:170`, depois de `evolve` (`:151`); é o único chamador em `src/main` | Motor inteiro: a busca é a de produção, sem mudança, e só a colocação final difere | **COMPLETO** |
| `ga-timeline` | `TimelineRepairer.place`, `return` em `:283-284` | Sim, com Mockito. `TimelineSearch` recebe o `TimelineRepairer` concreto e `final` (`TimelineSearch.java:73,85`), sem interface. Um dublê do Mockito (inline, padrão do Mockito 5.17) delega `repair` a uma cópia de teste de `TimelineRepairer` com a colocação P. `TimelineSearch` roda como está | **Sim.** O reparo roda em cada semente da população inicial (`TimelinePlanEngine.java:241-245`) e em cada descendente (`TimelineSearch.java:158`), e a aptidão lê o calendário reparado. P muda a busca, não só a colocação final | Motor inteiro | **COMPLETO** |

**Portão: PASSOU.** O guloso é COMPLETO. Nenhum motor ficou BLOQUEADO, então não há diff de `src/main`
a propor nesta fase.

**Como se sabe que as cópias são fiéis [método; resultado no §7.1].** Cada cópia tem a variante `BASE`,
que reproduz a colocação de produção. Para toda célula da matriz, a resposta da cópia em `BASE` tem de
ter o **mesmo hash canônico** da resposta de produção, pelo seletor. Uma divergência aborta a medição.
Assim, a única diferença entre a base e P é a colocação.

## 4. Estatística e métricas, fixadas antes de ver resultado

- **Matriz:** as três instâncias de `src/test/resources/instances/` (não editadas) e o reprodutor
  viável de `instances/reproducers/`; os três motores; sementes 1 a 10; base e P.
- **Avaliador:** o mesmo avaliador a posteriori do EOA-10 (`PlanScoring` com a composição de
  produção, como em `EngineProbe`), nas métricas que ele define: `util`, `posthoc_syllabus_mastery`,
  `posthoc_ga_objective` (o objetivo do AG relatado por completude, não comparação entre motores,
  `CLAUDE.md` §5b) e `posthoc_raw_score`.
- **Por instância, motor e variante:** aceitou ou recusou (contagem); tópicos, sessões e minutos
  agendados; as métricas do avaliador; **n efetivo** = resultados distintos nas 10 sementes, em que
  cada plano aceito distinto pelo hash de `sessions` conta 1 e as recusas com o mesmo código contam 1
  juntas; `PlanOutputInvariants` passou (contagem).
- **P contra a base:** por instância e motor, pareado por semente, a **mediana das diferenças**
  (P menos base) em tópicos agendados e em cada métrica do avaliador, com mínimo e máximo; e a
  **fração de sementes em que P agenda estritamente mais tópicos**. Uma recusa conta 0 tópicos, 0
  sessões e 0 minutos (como em `EngineProbe`). As métricas do avaliador só existem para planos
  aceitos: a diferença delas usa só os pares com os dois lados aceitos, e o número de pares acompanha.
- **n efetivo do par:** pares (base, P) distintos nas 10 sementes. Onde for 1, a célula é **uma
  observação**, e é dito assim.
- Sem teste de significância, sem a palavra "melhor", nenhuma afirmação de superioridade.

## 5. Fase 1 — D: por que cada tópico ficou de fora

Definições, fixadas antes de rodar. Para cada tópico ausente de um plano, uma categoria, nesta ordem
de precedência:

- **A — nunca cabe:** os minutos da sua primeira sessão (`estimatedMinutes`, nos três motores)
  excedem a maior janela. Teste com o próprio alocador: `AvailabilityAllocator.over(pedido).place(n)`
  vazio numa agenda vazia, como no E2 e no E3 do EOA-12.
- **B — dependente `HARD` transitivo de um tópico de A**, no grafo da condição de proveniência do
  pedido.
- **C — não é A nem B**: caberia numa agenda vazia, e ficou de fora porque a colocação parou antes
  ou faltou janela livre. Subdividido em **C-raiz** (nenhum pré-requisito `HARD` transitivo seu ficou
  de fora) e **C-dep** (algum ficou).
- **D — outra:** ausente, não é A nem B, e a própria resposta **não o declara** em
  `fitness.topics-unscheduled-ids`. Por construção A, B e C cobrem todo ausente, porque quem não é A
  cabe numa agenda vazia; D existe para pegar a resposta que contradiga a sua própria declaração.

Numa recusa, os 35 tópicos estão ausentes. As recusas são relatadas à parte das respostas aceitas.

Resultados: §6.

## 6. Resultados da Fase 1 (D), nas respostas da base

Medido por `AllocatorOptionsMeasurementTest` (`-Ddiagnostic=true`) em `c1894ad`, sobre as mesmas
respostas de base do E0 (os hashes conferem, §8). Dados: [`alocador-opcoes/`](./alocador-opcoes/).
Para reproduzir (o CSV sai em `target/diagnostic/alocador-*.csv`; tudo é determinístico exceto
`wall_ms`):

```bash
./mvnw test -Dtest=AllocatorOptionsMeasurementTest -Ddiagnostic=true -Dsurefire.failIfNoSpecifiedTests=false
```

| Arquivo | Conteúdo |
|---|---|
| `matriz.csv` | uma linha por instância × motor × variante × semente (240): métricas, invariantes, contagem A/B/C/D, hashes |
| `celulas.csv` | §7.2 |
| `pares.csv` | §7.3 |
| `ausentes.csv` | §6.2 e a mesma distribuição sob P |
| `estatico.csv` | cada tópico de cada instância, com a categoria estática (A, B ou nenhuma) |
| `janelas.csv` | §6.1 |

### 6.1 Análise estática

**[OBSERVED]** `janelas.csv` e `estatico.csv`. A maior janela é a maior duração que o alocador coloca
numa agenda vazia (busca binária sobre `place`).

| Instância | Maior janela | A (nunca cabe) | Posições de A | B (dependentes `HARD` de A) |
|---|---|---|---|---|
| apertado | 70 min | **10** | 3, 4, 15, 16, 18, 24, 32, 33, 34, 35 | 1 (posição 19, dependente da 18) |
| médio | 90 min | **2** | 34, 35 | 0 |
| folgado | 120 min | **2** | 34, 35 | 0 |
| reprodutor viável | 70 min | 1 | 34 | 0 |

10, 2 e 2 conferem com o E3 do EOA-12, nas mesmas posições. Uma observação nova: dos 10 tópicos de A no
apertado, **só o da posição 34 não tem pré-requisito `HARD`**; os outros nove têm um ou dois
(`estatico.csv`, coluna `hard_prerequisites`). Isso confirma a parte de grafo da [HYPOTHESIS] do
`REPARO_ESCALA_REAL.md` §3.4 ("os demais não seriam raízes"); a parte das permutações continua sem
verificação.

### 6.2 Distribuição dos ausentes, por instância e motor

**[OBSERVED]** `ausentes.csv`, variante `base`. Somas sobre as 10 sementes; numa recusa os 35 tópicos
estão ausentes. Classificação do E1 de todos os motores: COMPLETO (aqui não importa, porque não há
variante).

| Instância | Motor | Respostas (n efetivo) | Ausentes | A | B | C-raiz | C-dep | D | A agendado | (A+B)/ausentes | C/ausentes |
|---|---|---|---|---|---|---|---|---|---|---|---|
| apertado | guloso | aceitas: 10 (1) | 330 | 100 | 10 | 170 | 50 | 0 | 0 | 0,333 | 0,667 |
| apertado | `ga` | recusas: 10 (1) | 350 | 100 | 10 | 140 | 100 | 0 | 0 | 0,314 | 0,686 |
| apertado | `ga-timeline` | aceitas: 10 (10) | 221 | 100 | 10 | 65 | 46 | 0 | 0 | 0,498 | 0,502 |
| médio | guloso | aceitas: 10 (1) | 200 | 20 | 0 | 120 | 60 | 0 | 0 | 0,100 | 0,900 |
| médio | `ga` | recusas: 10 (1) | 350 | 20 | 0 | 140 | 190 | 0 | 0 | 0,057 | 0,943 |
| médio | `ga-timeline` | aceitas: 10 (9) | 172 | 20 | 0 | 90 | 62 | 0 | 0 | 0,116 | 0,884 |
| folgado | guloso | aceitas: 10 (1) | 20 | 20 | 0 | 0 | 0 | 0 | 0 | 1,000 | 0,000 |
| folgado | `ga` | aceitas: 7 | 232 | 14 | 0 | 106 | 112 | 0 | 0 | 0,060 | 0,940 |
| folgado | `ga` | recusas: 3 | 105 | 6 | 0 | 42 | 57 | 0 | 0 | 0,057 | 0,943 |
| folgado | `ga-timeline` | aceitas: 10 (1) | 20 | 20 | 0 | 0 | 0 | 0 | 0 | 1,000 | 0,000 |
| reprodutor viável | guloso | aceitas: 10 (1) | 10 | 10 | 0 | 0 | 0 | 0 | 0 | 1,000 | 0,000 |
| reprodutor viável | `ga` | recusas: 10 (1) | 20 | 10 | 0 | 10 | 0 | 0 | 0 | 0,500 | 0,500 |
| reprodutor viável | `ga-timeline` | aceitas: 10 (1) | 10 | 10 | 0 | 0 | 0 | 0 | 0 | 1,000 | 0,000 |

O n efetivo do `ga` no folgado são 5 resultados distintos: 4 planos nas 7 aceitas e a recusa.

- **Nenhum tópico de A aparece agendado**, em nenhum motor, semente ou variante (coluna "A agendado",
  0 em todas as 25 linhas de `ausentes.csv`). O predicado A não foi contradito.
- **D é 0 em toda linha**: toda resposta aceita declara em `topics-unscheduled-ids` exatamente os
  tópicos sem sessão (`matriz.csv`, coluna `unscheduled_ids_match_absence`, `yes` nas 87 aceitas da
  base).
- Onde o n efetivo é 1 (guloso nas quatro instâncias, `ga` recusado, `ga-timeline` no folgado e no
  viável), as 10 sementes são **uma observação** repetida, e a soma é 10 vezes a mesma resposta.

**Conclusão em números [OBSERVED].** Do que o aluno não recebe, a fração "não cabe na sua
disponibilidade" (A+B) e a fração "o motor parou" (C) são:

| Instância | guloso (A+B / C) | `ga` (A+B / C) | `ga-timeline` (A+B / C) |
|---|---|---|---|
| apertado | 0,333 / 0,667 | 0,314 / 0,686 (recusas) | 0,498 / 0,502 |
| médio | 0,100 / 0,900 | 0,057 / 0,943 (recusas) | 0,116 / 0,884 |
| folgado | 1,000 / 0,000 | 0,060 / 0,940 (aceitas); 0,057 / 0,943 (recusas) | 1,000 / 0,000 |

No folgado, o guloso e o `ga-timeline` já deixam de fora só os dois tópicos que nunca cabem. No médio,
nove décimos do que fica de fora caberiam numa agenda vazia.

## 7. Resultados da Fase 2 (P)

### 7.1 As cópias são fiéis, e nenhuma resposta P viola as invariantes

**[OBSERVED]**, `AllocatorOptionsMeasurementTest` em `c1894ad`:

- **120 de 120** células: a cópia em `BASE` deu o mesmo hash canônico da resposta de produção, ou a
  mesma recusa com a mesma mensagem. A lista de divergências do teste saiu vazia.
- **120 de 120** respostas P aceitas, e **120 de 120** passam por `PlanOutputInvariants`, dentro da
  cópia e na reconferência do harness (`invariants_recheck = pass`). Nenhuma violação; a regra de
  parada não foi acionada.
- Duas execuções em JVMs separadas deram as **240 linhas** de `matriz.csv` iguais em todas as colunas
  exceto `wall_ms`, inclusive para P.
- Os testes pequenos de P (§2, casos a–d) estão no `verify`: `SkipAheadAllocatorTest`,
  `VariantGreedySchedulerTest`, `VariantSessionPlacementTest`, `VariantTimelineRepairerTest`, 34
  testes. Vermelho primeiro (`e84c6f0`: 34 rodados, 18 erros, todos `UnsupportedOperationException`;
  os 16 que passavam eram as conferências da cópia em `BASE` contra a produção e a de que a falha de
  produção consome janelas), verde em `f2d49bb` (34 de 34).

### 7.2 Por instância, motor e variante

**[OBSERVED]** `celulas.csv`. Medianas sobre as 10 sementes; as métricas do avaliador só sobre as
aceitas. Classe do E1 em todas as linhas: **COMPLETO**.

| Instância | Motor | Classe | Variante | Aceitas | Tópicos med [mín–máx] | Sessões med | Minutos med | util med | `syllabusMastery` med | agregado med | bruto med | n efetivo | Invariantes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| apertado | guloso | COMPLETO | base | 10/10 | 2 [2–2] | 2 | 75 | 0,099 | 0,0114 | 0,000 | −0,770 | 1 | 10/10 |
| apertado | guloso | COMPLETO | P | 10/10 | 12 [12–12] | 12 | 550 | 0,724 | 0,0546 | 0,000 | −0,632 | 1 | 10/10 |
| apertado | `ga` | COMPLETO | base | 0/10 | 0 [0–0] | 0 | 0 | — | — | — | — | 1 | — |
| apertado | `ga` | COMPLETO | P | 10/10 | 13 [13–13] | 13 | 600 | 0,789 | 0,0586 | 0,000 | −0,627 | 1 | 10/10 |
| apertado | `ga-timeline` | COMPLETO | base | 10/10 | 13 [12–13] | 13 | 575 | 0,757 | 0,0620 | 0,000 | −0,622 | 10 | 10/10 |
| apertado | `ga-timeline` | COMPLETO | P | 10/10 | 13 [12–13] | 13 | 575 | 0,757 | 0,0620 | 0,000 | −0,589 | 5 | 10/10 |
| médio | guloso | COMPLETO | base | 10/10 | 15 [15–15] | 15 | 820 | 0,506 | 0,0628 | 0,000 | −0,588 | 1 | 10/10 |
| médio | guloso | COMPLETO | P | 10/10 | 16 [16–16] | 16 | 870 | 0,537 | 0,0667 | 0,000 | −0,573 | 1 | 10/10 |
| médio | `ga` | COMPLETO | base | 0/10 | 0 [0–0] | 0 | 0 | — | — | — | — | 1 | — |
| médio | `ga` | COMPLETO | P | 10/10 | 15 [15–15] | 18 | 955 | 0,590 | 0,0701 | 0,000 | −0,509 | 1 | 10/10 |
| médio | `ga-timeline` | COMPLETO | base | 10/10 | 18 [15–20] | 20,5 | 995 | 0,614 | 0,0863 | 0,000 | −0,487 | 9 | 10/10 |
| médio | `ga-timeline` | COMPLETO | P | 10/10 | 23 [21–24] | 26 | 1287,5 | 0,795 | 0,1088 | 0,000 | −0,223 | 10 | 10/10 |
| folgado | guloso | COMPLETO | base | 10/10 | 33 [33–33] | 33 | 1870 | 0,577 | 0,1349 | 0,000 | −0,227 | 1 | 10/10 |
| folgado | guloso | COMPLETO | P | 10/10 | 33 [33–33] | 33 | 1870 | 0,577 | 0,1349 | 0,000 | −0,227 | 1 | 10/10 |
| folgado | `ga` | COMPLETO | base | 7/10 | 1 [0–3] | 2 | 38 | 0,023 | 0,0148 | 0,000 | −0,735 | 5 | 7/7 |
| folgado | `ga` | COMPLETO | P | 10/10 | 31,5 [30–33] | 46 | 2348 | 0,725 | 0,1485 | 0,000 | −0,065 | 10 | 10/10 |
| folgado | `ga-timeline` | COMPLETO | base | 10/10 | 33 [33–33] | 45 | 2186 | 0,675 | 0,1467 | 0,000 | −0,039 | 1 | 10/10 |
| folgado | `ga-timeline` | COMPLETO | P | 10/10 | 33 [33–33] | 46 | 2231 | 0,689 | 0,1728 | 0,292 | 0,292 | 10 | 10/10 |
| reprodutor viável | guloso | COMPLETO | base | 10/10 | 1 [1–1] | 1 | 50 | 0,714 | 0,0696 | 0,000 | −0,606 | 1 | 10/10 |
| reprodutor viável | guloso | COMPLETO | P | 10/10 | 1 [1–1] | 1 | 50 | 0,714 | 0,0696 | 0,000 | −0,606 | 1 | 10/10 |
| reprodutor viável | `ga` | COMPLETO | base | 0/10 | 0 [0–0] | 0 | 0 | — | — | — | — | 1 | — |
| reprodutor viável | `ga` | COMPLETO | P | 10/10 | 1 [1–1] | 1 | 50 | 0,714 | 0,0696 | 0,000 | −0,606 | 1 | 10/10 |
| reprodutor viável | `ga-timeline` | COMPLETO | base | 10/10 | 1 [1–1] | 1 | 50 | 0,714 | 0,0696 | 0,000 | −0,606 | 1 | 10/10 |
| reprodutor viável | `ga-timeline` | COMPLETO | P | 10/10 | 1 [1–1] | 1 | 50 | 0,714 | 0,0696 | 0,000 | −0,606 | 1 | 10/10 |

O agregado é o objetivo do genético limitado a `[0,1]`, relatado por completude e não como comparação
entre motores (`CLAUDE.md` §5b).

### 7.3 P contra a base, pareado por semente

**[OBSERVED]** `pares.csv`. d = P menos base, por semente; mediana [mínimo–máximo] sobre as 10
sementes. Tópicos, sessões e minutos usam as 10 (recusa = 0). As métricas do avaliador usam só os pares
com os dois lados aceitos, e a coluna diz quantos.

| Instância | Motor | Classe | n efetivo base / P / pares | d tópicos | P agenda estritamente mais tópicos | d sessões | d minutos | Pares aceitos | d util | d `syllabusMastery` | d agregado | d bruto |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| apertado | guloso | COMPLETO | 1 / 1 / 1 | 10 [10–10] | 10/10 | 10 | 475 | 10/10 | 0,625 [0,625–0,625] | 0,0432 [0,0432–0,0432] | 0 | 0,138 [0,138–0,138] |
| apertado | `ga` | COMPLETO | 1 / 1 / 1 | 13 [13–13] | 10/10 | 13 | 600 | 0/10 | — | — | — | — |
| apertado | `ga-timeline` | COMPLETO | 10 / 5 / 10 | 0 [−1–1] | 1/10 | 0 | 0 | 10/10 | 0,000 [−0,066–0,066] | 0,0000 [−0,0040–0,0040] | 0 | 0,033 [0,019–0,044] |
| médio | guloso | COMPLETO | 1 / 1 / 1 | 1 [1–1] | 10/10 | 1 | 50 | 10/10 | 0,031 [0,031–0,031] | 0,0040 [0,0040–0,0040] | 0 | 0,015 [0,015–0,015] |
| médio | `ga` | COMPLETO | 1 / 1 / 1 | 15 [15–15] | 10/10 | 18 | 955 | 0/10 | — | — | — | — |
| médio | `ga-timeline` | COMPLETO | 9 / 10 / 10 | 5 [2–9] | 10/10 | 6 | 305 | 10/10 | 0,188 [0,062–0,247] | 0,0242 [0,0080–0,0427] | 0 | 0,258 [0,223–0,301] |
| folgado | guloso | COMPLETO | 1 / 1 / 1 | 0 [0–0] | 0/10 | 0 | 0 | 10/10 | 0 [0–0] | 0 [0–0] | 0 | 0 [0–0] |
| folgado | `ga` | COMPLETO | 5 / 10 / 10 | 30 [28–32] | 10/10 | 43 | 2301,5 | 7/10 | 0,681 [0,630–0,742] | 0,1368 [0,1272–0,1494] | 0 | 0,692 [0,646–0,757] |
| folgado | `ga-timeline` | COMPLETO | 1 / 10 / 10 | 0 [0–0] | 0/10 | 1 | 45 | 10/10 | 0,014 [0,014–0,014] | 0,0261 [0,0196–0,0306] | 0,292 | 0,331 [0,326–0,338] |
| reprodutor viável | guloso | COMPLETO | 1 / 1 / 1 | 0 [0–0] | 0/10 | 0 | 0 | 10/10 | 0 [0–0] | 0 [0–0] | 0 | 0 [0–0] |
| reprodutor viável | `ga` | COMPLETO | 1 / 1 / 1 | 1 [1–1] | 10/10 | 1 | 50 | 0/10 | — | — | — | — |
| reprodutor viável | `ga-timeline` | COMPLETO | 1 / 1 / 1 | 0 [0–0] | 0/10 | 0 | 0 | 10/10 | 0 [0–0] | 0 [0–0] | 0 | 0 [0–0] |

**Células que são UMA observação** (n efetivo dos pares = 1): o guloso nas quatro instâncias, o `ga`
no apertado, no médio e no viável, e o `ga-timeline` no viável. Ali a mediana é o valor de um único par.

- **Guloso.** P muda o plano no apertado (2 → 12 tópicos) e no médio (15 → 16). No folgado e no viável
  a resposta P é **idêntica** à da base: os ausentes ali são só tópicos de A, e eles estão no fim da
  ordem do guloso.
- **As 23 recusas do `ga`:** com P, **0 recusas em 30** nas três instâncias (e 10/10 aceitas no
  viável), todas válidas. No apertado e no médio as 10 sementes dão o mesmo plano.
- **`ga-timeline` no folgado:** a base dá **1** plano distinto em 10 sementes; P dá **10**. No apertado
  o efeito vai no outro sentido: 10 planos distintos na base, **5** com P.

A distribuição dos ausentes sob P está em `ausentes.csv` (variante `P`). Exemplo: no apertado o guloso
passa de 330 ausentes (A+B 0,333) para 230 (A+B 0,478); no folgado o `ga` passa de 337 (aceitas e
recusas) para 35.

### 7.4 O que acontece com "plano parcial = prefixo da ordem"

**[OBSERVED]** Sob P a propriedade deixa de valer. `matriz.csv`, coluna `prefix_reading_matches`:
compara os ausentes de fato com o que `PrerequisiteReport` nomeia hoje (`skip(scheduled)` sobre a ordem
do motor).

| Motor | Respostas P em que a leitura de prefixo erraria `topics-unscheduled-ids` |
|---|---|
| guloso | 20 de 40 (apertado 10, médio 10; folgado e viável 0, onde P = base) |
| `ga` | 40 de 40 |
| `ga-timeline` | 0 de 40: ele monta a ordem relatada pelo calendário (`TimelinePlanEngine.java:315-321`), então a leitura coincide; se o plano é prefixo da ordem **interna** do reparo não aparece na resposta |

Ou seja, adotar P sem mexer em `PrerequisiteReport` faria **60 de 80** respostas do guloso e do `ga`
nomearem os tópicos errados. Nas respostas P medidas aqui a lista está certa porque a cópia aplica a
regra P9.

Trechos que afirmam a propriedade **[READ@16be5b9]**, que ficariam errados com P adotado:

- `README.md:196-197`: "placement walks that order forward and truncates at the first topic that does
  not fit — so the scheduled set is always a *prefix* of a topological order".
- `README.md:248-249`: "`topics-unscheduled-ids` lists the topics the calendar could not hold. They are
  exactly the tail of the study order, because the scheduled set is a prefix of it."
- `README.md:743-744`: "Not enough availability produces a **declared partial plan** — a prefix of the
  study order".
- `docs/ai/INTEGRATION.md:118-119`: "not enough availability yields a declared partial plan, a prefix of
  the study order, with `partial` and `topics-unscheduled-ids` in `fitness`".
- E no código, em Javadoc e comentário: `GreedyBaselineScheduler` (classe e `allocate`),
  `SessionPlacement` (classe), `TimelineRepairer` (classe, item 3, e `place`), `PrerequisiteReport.java`
  (comentário em `repaired`) e `PlanOutputInvariants` (classe e `checkPrerequisites`).

Nenhum deles foi editado: P não foi adotado, e todos descrevem corretamente o código de hoje.

### 7.5 Tempo

Ordem de grandeza, mediana de `wall_ms` nas três instâncias, base / P: guloso ~1 / ~1 ms, `ga` ~7 /
~7 ms, `ga-timeline` ~70 / ~170 ms. O P medido roda sobre a cópia de teste, com o replay do alocador e
o dublê do Mockito, então essa diferença não é o custo de P em produção. Nada além da ordem de grandeza
é afirmado.

## 8. A base não se moveu

**[OBSERVED]** As 120 respostas de base de `matriz.csv` (4 instâncias × 3 motores × 10 sementes) têm o
**mesmo** `response_sha256` e `sessions_sha256` do E0, gravado fora do repositório antes de qualquer
código. E `RealCatalogRepairTest` rodado de novo em `c1894ad` deu as 150 linhas iguais às do E0 em
tudo exceto `wall_ms` (§3.1).

**[OBSERVED]** `verify` verde em `c1894ad` mais este documento: 613 testes, 0 falhas, 7 ignorados (os
dois novos de medição entre eles, desligados sem `-Ddiagnostic=true`); `GaResultadoInalteradoTest` 4 de
4, sem edição; JaCoCo INSTRUCTION 11446/12128 = 0,9438 e BRANCH 816/928 = 0,8793, acima dos pisos
0,9325 e 0,7861, que não mudaram. Rodado com o Maven 3.9.11 instalado (`mvn verify`), a mesma versão
que o wrapper baixa, porque neste ambiente o wrapper não conseguiu baixar a distribuição.

O diff deste trabalho não toca `src/main`, `src/test/resources/contract/` nem os arquivos de
`src/test/resources/instances/`; nenhum teste existente foi editado.

## 9. Fase 3 — o que o consumidor assume

Lido em `sinapse-platform` no `origin/main`, **other-repo@2e97a86**, por clone raso fora deste
repositório, só leitura, nada executado, apagado depois. Arquivos: `docs/ai/INTEGRATION.md`,
`docs/ai/STATE.md` e `docs/ai/HANDOFF.md`.

O que está escrito lá **[READ@other-repo@2e97a86]**:

| Assunto | O que a plataforma assume | Onde |
|---|---|---|
| Plano parcial | A14: falta de disponibilidade dá plano parcial declarado (`fitness.partial`), não erro; a plataforma guarda um plano parcial não vazio como `ACTIVE` **sem ler `partial`** | INTEGRATION.md:56 |
| `fitness` | A15: mapa aberto, cujas chaves a plataforma não compila (`FitnessTermNamesAreNotCompiledTest`); "Not enforced: anything about `fitness`, … `partial`" | INTEGRATION.md:57, 74 |
| `fitness` até o aluno | K17: `fitness` passa inteiro nas rotas `study-plans` e de resumo do aluno; a decisão K17-D (filtrar ou não) está aberta com o dono | STATE.md:41, 66 |
| Ordem `HARD` | A6: a plataforma **não** confere a ordem `HARD` nem o horizonte; confia no Core | INTEGRATION.md:48, 74 |
| Motor padrão | A10: sem `engine`, o Core roda `greedy-baseline` | INTEGRATION.md:52 |
| Reprodutibilidade | A3: mesmo corpo, semente e motor ⇒ mesmo plano | INTEGRATION.md:45 |
| `ga` | A13 e K13: o `ga` recusa 23/30; SP-H3 continua `acknowledged` e o uso do `ga` "ainda espera essa decisão" | INTEGRATION.md:55; STATE.md:63; HANDOFF.md SP-H3 |
| D3 | listada como decisão do Core ("what the student sees when topics do not fit"), sem posição da plataforma | STATE.md:42 |

**O que não está escrito [UNVERIFIED]:** nenhum dos três arquivos fala em prefixo da ordem, nem em
`topics-unscheduled-ids` (um `grep` por `unscheduled` e por `prefix` em `docs/` e `src/main/` do clone
também não achou nada ligado a isso). Que a plataforma, a interface do aluno ou algum relatório dependa
da ordem ou do conteúdo dessa lista não está escrito; nada aqui roda a plataforma.

**O que mudaria do lado de lá:**

- **P adotado.** A forma da resposta não muda (nenhum campo, nenhuma chave). Muda o conteúdo: o motor
  padrão (A10) passa a devolver outro plano no apertado e no médio; o `ga` deixa de recusar (A13, K13 e
  SP-H3 teriam de ser revistos); a ordem `HARD` continua garantida pelo Core, que é do que A6 depende.
  Planos guardados antes e depois da mudança deixam de ser comparáveis para o mesmo pedido e semente, e
  A3 só vale dentro de um mesmo build. Com P adotado sem o ajuste de `PrerequisiteReport` (§7.4), a lista
  `topics-unscheduled-ids` que chega ao aluno pelo K17 estaria errada em 60 de 80 respostas medidas.
- **D como análise (o que foi feito aqui).** Nada muda do lado de lá.
- **D exposto ao aluno.** Por uma chave nova em `fitness`, a plataforma a guardaria e a passaria às
  rotas do aluno sem filtro (A15 e K17), sem subir a versão do contrato; o que o aluno veria é a D3, que
  está aberta. Por um campo novo no contrato, `PlanRequest.VERSION` sobe nos dois repositórios
  (`CLAUDE.md` §2). Como a plataforma mostraria isso: UNVERIFIED.

## 10. O que continua desconhecido

- **Por que o agregado do `ga-timeline` no folgado sai positivo com P** (0,292 em todas as sementes,
  contra 0 na base), com os mesmos 33 tópicos e uma sessão a mais. Não foi investigado; não se sabe
  qual termo muda nem se a busca sai da geração 0 (`search-final-best` não foi tabulado).
- Por que, no apertado, o `ga-timeline` com P produz menos planos distintos (5) que a base (10).
- A ordem interna do reparo do `ga-timeline` não aparece na resposta, então não se mede ali se o plano
  P deixa de ser prefixo dela (§7.4).
- Se as regras de P fixadas no §2 são as que se adotariam: elas foram escolhidas para medir com a
  unidade de tentativa de cada motor, e outra regra (por exemplo, revisitar um tópico pulado, ou no
  guloso manter o estudo quando a revisão não cabe) daria outros números. Nenhuma outra foi medida.
- Muitas células são uma observação só (§7.3); quatro instâncias não dizem como P se comporta em
  outras disponibilidades.
- O custo de P em produção: o tempo do §7.5 é da cópia de teste.
- Tudo do lado da plataforma além do que está escrito (§9).

## 11. Decisão — PROPOSAL

**[PROPOSAL]** Nada aqui é escolhido; a decisão é do dono. A D3 continua aberta e ligada a esta.

| Opção | O que muda | Custo | Dado que ainda faltaria |
|---|---|---|---|
| **Manter** | nada | Nenhum código. O `ga` continua recusando 23/30; o guloso continua em 2, 15 e 33 tópicos; no médio ~0,9 do que fica de fora caberia numa agenda vazia (§6.2) | — |
| **Adotar D** | classificar o porquê de cada ausente | Como análise, nenhum código de produção (é o `AbsenceClassifier` de teste). Para o aluno ver: uma chave nova em `fitness` chega às rotas do aluno pelo K17, sem filtro; um campo novo no contrato exige subir `PlanRequest.VERSION` nos dois repositórios. Não muda nenhum plano nem resolve as recusas do `ga` | o que o aluno deve ver (D3); se a plataforma filtra `fitness` (K17-D); texto e categorias a expor |
| **Adotar P** | pular e seguir nos três motores | Edição em `src/main`, que a D2b não autoriza hoje. Menor diff [PROPOSAL, não aplicado]: (1) falha de `AvailabilityAllocator.place` sem efeito no cursor, ou uma tentativa transacional; (2) pular com os dependentes `HARD` em `GreedyBaselineScheduler.allocate`, `SessionPlacement.place` e `TimelineRepairer.place`; (3) `PrerequisiteReport` nomear os ausentes por ausência (senão 60/80 erram, §7.4); (4) README e INTEGRATION.md (§7.4). **Muda o guloso, que é o motor padrão e o grupo de controle**: 2 → 12 tópicos no apertado, 15 → 16 no médio. Tira a comparabilidade com os números do EOA-10 e do EOA-12 (toda medição publicada que usa o alocador). Muda a busca do `ga-timeline` (n efetivo no folgado 1 → 10; no apertado 10 → 5) | regras de P alternativas (§10); mais instâncias, porque muitas células são n = 1; o efeito na plataforma (§9); o custo em produção |
| **Adotar P+D** | as duas | A soma dos dois custos. Sob P, a fração A+B entre os ausentes sobe (apertado, guloso: 0,333 → 0,478), porque P agenda parte do que era C | os dados de P e de D acima |

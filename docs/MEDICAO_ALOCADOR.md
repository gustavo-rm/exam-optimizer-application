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

Escritas e commitadas antes do código de P. Não foram ajustadas depois.

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
| P9 | **`topics-unscheduled-ids` passa a nomear os tópicos do pedido sem sessão**, na ordem do motor. Hoje `PrerequisiteReport` toma os que vêm depois do prefixo (`skip(scheduled)`, `PrerequisiteReport.java:44`), o que sob P nomearia os tópicos errados no guloso e no `ga`. O `ga-timeline` já monta a lista pelo calendário (`TimelinePlanEngine.java:315-321`), e nele nada muda. `soft-prerequisite-inversions` fica calculado como na base, sobre a ordem do motor. Quantas respostas P teriam a lista errada com o código de produção sem ajuste é medido (§6). |

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

**Como se sabe que as cópias são fiéis [método, a conferir no §6].** Cada cópia tem a variante `BASE`,
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

## 6. Resultados

Pendente: preenchido depois da execução.

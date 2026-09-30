# Medição 10 — v2, o cromossomo de linha do tempo: as três condições medidas

**Documentos anteriores:** [`README.md`](./README.md) (índice) ·
[`09-medicao-baseline-vs-v1.md`](./09-medicao-baseline-vs-v1.md) (baseline × v1, e o instrumento)
**Este documento:** a terceira condição — `ga-timeline` — medida pelas **mesmas** instâncias,
sementes, avaliador a posteriori e métricas da etapa 09.
**Código:** `sinapse/TimelinePlanEngine`, `sinapse/timeline/**`, `ga/tactical/**`
**Dados brutos:** [`benchmarks/results/measurement.csv`](../../benchmarks/results/measurement.csv)
— **1 080 linhas** (24 instâncias × 3 motores × 3 proveniências × 5 sementes)
**Alterou produção?** Sim: duas correções de fitness (G14, G15), um motor novo, e uma correção latente
em `SinapseFitness` que a v2 foi a primeira a exercitar.

---

## 1. Veredito

> ### A hipótese da v2 não se confirmou na métrica que ela existia para melhorar
>
> A v2 produz **674** inversões `SOFT` contra **420** da v1 — ela é **pior em 156 de 360** células
> pareadas e melhor em apenas 33 — e custa **~18×** o tempo da v1, depois da otimização de §6. Ela bate
> o guloso (674 contra 980),
> mas o guloso não era o alvo: a v1 era.
>
> **Isto é um resultado, não uma falha do experimento**, e a causa está medida em §5: a v2 faz
> exatamente o que a função objetivo pede. Ela não perde por não conseguir expressar ordem — perde
> porque a ordem vale `0,10` na fitness e a cobertura vale mais, então a busca troca ordem por
> cobertura, e troca com lucro pelo critério que recebeu.

| Pergunta | Resposta medida |
|---|---|
| A v2 remove mais inversões que a v1? | **Não. 674 contra 420.** Pior em 156/360 células, melhor em 33, empate em 171 |
| A v2 bate o baseline guloso? | **Sim. 674 contra 980**, melhor em 218/360 |
| A que custo? | **102 491 µs** de mediana, contra 5 693 da v1 (**18×**) e 167 do guloso. Mesmas 2 440 avaliações — o custo é o **reparo**, não a busca. G17 a deixou 1,37× mais rápida; o resto é estrutural (§6) |
| Então a v2 é pior? | **Não em tudo.** Ela agenda **mais tópicos** (15,23 contra 13,52), usa **mais calendário** (0,844 contra 0,771) e **nivela melhor os dias** (1,56 contra 1,69) |
| A busca da v2 funcionou? | **Sim, e é o achado que mais importa.** Agregado positivo em **328 de 360** contra 97 da v1; bruto médio **+0,303** contra −0,132. As correções G14 e G15 deram gradiente, e a v2 o usou |
| Alguma inversão `HARD`? | **Nenhuma**, em 1 080 execuções e 9 condições |
| Alguma execução não reprodutível? | **Nenhuma**, em 1 080 pares |

---

## 2. Método

Idêntico ao da etapa 09 — mesmas 24 instâncias, mesmas 5 sementes, mesma comparação pareada por
`(instância, proveniência, semente)`, mesmo avaliador a posteriori, mesmas quatro famílias de
métrica. **A v2 entrou como uma linha em `Condition.ENGINES`**, que era a afirmação que o harness foi
construído para honrar: nenhuma métrica, nenhuma invariante e nenhuma coluna do CSV mudou para
acomodá-la.

### 2.1 As três condições, e o que cada uma decide

| condição | alocação | ordem | preferências `SOFT` |
|---|---|---|---|
| `greedy-baseline` | heurística | pressão de meta, depois posição curricular | nem repara nem busca — **mede** |
| `ga` (v1) | **buscada** | derivada da alocação, depois **reparada** | reparo pós-processado |
| `ga-timeline` (v2) | determinística | **buscada** | **precificadas dentro da fitness**, sem reparo |

A v2 evolui `TacticalStudyPlan` — o mapa de `TimeSlot` para `TacticalStudyBlock` que já existia em
produção, já era produzido por `SessionPlacement` e já era pontuado pelo `FitnessEvaluator`. **Nada
em `domain/tactical/**` foi reimplementado.**

### 2.2 O que a v2 delibera não buscar

**A contagem de sessões por item é determinística** (`TimelineChromosomes`): o piso de
`SinapseMinimumDays` mais o excedente do orçamento por importância normalizada, com maior resto. O
orçamento vem de `SessionBudget`, extraído de `GeneticPlanEngine` exatamente para que os dois motores
genéticos não tivessem duas fórmulas.

**Isso é limitação declarada e corta nas duas direções.** A v1 busca a alocação e a v2 não, então
numa instância onde o ganho vem de alocar melhor a v2 não tem como competir. Em troca, uma diferença
medida entre as duas é atribuível à **ordem**, que passa a ser a única coisa que as separa.

### 2.3 Os operadores, e o que faltava

O levantamento pedido encontrou dois operadores em `ga/tactical/strategy/`, e o estado deles não era o
descrito no enunciado da tarefa:

| operador | estado encontrado | o que foi feito |
|---|---|---|
| `DayBoundaryCrossover` | **tinha** teste (`DayBoundaryCrossoverTest`), desligado por não ter consumidor | ligado sem alteração |
| `MethodologyMutation` | **sem teste nenhum**, e com defeito | corrigido e testado — ver abaixo |
| `SpacedRepetitionRepairer` | `@Component` **sem `@Profile`**, instanciado em todo contexto, com teste | ligado; empate de desempate corrigido |
| — | **faltava o operador de ordem** | `BlockSwapMutation`, novo |
| — | **faltava o reparo de linha do tempo** | `TimelineRepairer`, novo |

**`MethodologyMutation` tinha um defeito, não só falta de teste.** Ele iterava
`new HashMap<>(plan.getSchedule())` sorteando um número **por entrada**, então qual slot recebia qual
sorteio era decidido por `TimeSlot.hashCode()`. Nada era irreprodutível hoje — a ordem de um
`HashMap` é estável para hashes fixos, ao contrário de `Map.copyOf`, cuja semente muda por JVM —, mas
todo plano produzido passava a depender da dispersão do registro, e nenhum teste apontaria para a
causa se ela mudasse. `TacticalSlots` centraliza a ordenação por calendário; `SpacedRepetitionRepairer`
tinha a mesma fragilidade no desempate e foi corrigido junto.

**O operador que faltava era o de ordem, e sem ele não há v2.** `MethodologyMutation` troca
intensidade e deixa o bloco onde está; `DayBoundaryCrossover` recombina ordens existentes mas não gera
ordem nova. Sem `BlockSwapMutation`, a população nunca visitaria uma permutação que a geração inicial
não tivesse sorteado.

---

## 3. As duas regras de parada, e o que a produção pegou por nós

| Regra | Resultado |
|---|---|
| Qualquer inversão `HARD` | **Não disparou.** 1 080 execuções, 0 inversões rígidas |
| Falha de reprodutibilidade | **Não disparou.** 1 080 pares de execuções idênticos |

**`PlanOutputInvariants` pegou um defeito real da v2 antes de qualquer medição.**
`DayBoundaryCrossover` copia os dias até o corte de um pai e os seguintes do outro, então um item que
aparece nos dois lados sai **duplicado** e um que não aparece em nenhum sai **perdido**. Isso produziu
um plano em que o tópico `…0d` estava agendado e o `…08` de que ele depende não estava no plano.

Duas consequências, e as duas foram corrigidas em `TimelineRepairer`:

1. **a alocação deixava de ser fixa** — com o multiconjunto à deriva, a v2 passaria a buscar alocação
   *e* ordem, e a comparação com a v1 mediria duas mudanças;
2. **um dependente sobrevivia sem o pré-requisito** — violação de invariante, não questão de
   qualidade.

O reparo agora reconstrói a contagem **canônica** por item e conserva do cromossomo apenas o que é
gene: a ordem dos itens e a metodologia dos blocos. Com todos os itens sempre presentes, a ordenação
topológica é total e o caso "pré-requisito ausente" deixa de ser alcançável em vez de ser tratado.

**E a v2 expôs um defeito latente em `SinapseFitness`.** Ela escrevia
`soft-prerequisite-inversions-before-repair` com valor **nulo** quando nenhum reparo rodara, e
`PlanResponse` copia o mapa com `Map.copyOf`, que **recusa valor nulo com `NullPointerException`**.
Ficou latente enquanto o único caminho por ali reparava; a v2, que precifica em vez de reparar, foi o
primeiro motor a exercitá-lo. A chave agora é **omitida**, que é o que o próprio Javadoc daquela
classe já dizia ser a declaração correta.

---

## 4. O resultado principal: inversões `SOFT`

Sobre as 360 células pareadas.

| motor | total | média por célula | células em zero |
|---|--:|--:|--:|
| `greedy-baseline` | 980 | 2,722 | 15 / 360 |
| `ga` (v1) | **420** | **1,167** | 121 / 360 |
| `ga-timeline` (v2) | 674 | 1,872 | **124 / 360** |

| comparação pareada | v2 melhor | empate | v2 pior | delta médio |
|---|--:|--:|--:|--:|
| v2 contra **v1** | 33 | 171 | **156** | **−0,706** |
| v2 contra **guloso** | **218** | 87 | 55 | +0,850 |

**A v2 fica entre os dois.** Ela chega a zero inversões em ligeiramente mais células que a v1
(124 contra 121) — ou seja, quando consegue resolver a ordem, resolve —, mas quando não consegue,
deixa mais inversões que o reparo da v1 deixaria.

### Onde a v2 perde mais

| eixo | nível | delta médio (v2 − v1) | v2 ganha | v2 perde |
|---|---|--:|--:|--:|
| `topics` | 10 | −0,189 | 14 | 41 |
| | 25 | **−1,222** | 19 | 115 |
| `density` | 0,4 | −0,289 | 14 | 44 |
| | 1,2 | **−1,122** | 19 | 112 |
| `provenance` | `curated` | −0,275 | 12 | 32 |
| | `curated-textbook` | −0,858 | 9 | 61 |
| | `all` | **−0,983** | 12 | 63 |

A desvantagem da v2 **cresce com o número de preferências em jogo** — mais tópicos, grafo mais denso,
proveniência mais larga. Isso é coerente com §5: quanto mais inversões existem para trocar, mais a
busca troca.

---

## 5. Por que a v2 perde: a troca, medida

Nas **156 células em que a v2 tem mais inversões que a v1**:

| | v1 | v2 |
|---|--:|--:|
| Tópicos agendados | 15,81 | **18,01** |
| Utilização do calendário | 0,7700 | **0,8612** |
| Fitness bruta | −0,1862 | **+0,2355** |

**A v2 não está perdendo a busca. Ela está ganhando a busca que lhe foi dada.** Em toda célula em que
aceita mais inversões, ela entrega mais dois tópicos, nove pontos percentuais mais de calendário
usado, e uma fitness bruta muito superior.

A causa é a estrutura de pesos, e ela é explícita no código: `SoftPrerequisiteOrderConstraint` pesa
**0,10**, enquanto os três objetivos somam **1,00** e `MandatoryReviewConstraint` pesa **0,50**. Uma
inversão custa 0,10 × (severidade) à busca; um tópico a mais de cobertura vale mais que isso. A v2
troca ordem por cobertura porque a função objetivo diz que esse é um bom negócio.

**A v1 não faz essa troca porque não a avalia.** O reparo de preferências da v1 é **lexicográfico**:
ele remove inversões dentro do que as arestas rígidas permitem, sem consultar a fitness e sem
perguntar o que a remoção custa em cobertura. É pós-processamento, não otimização.

> ### A conclusão correta, portanto, não é "a linha do tempo não funciona"
>
> É que **a comparação mede reparo-contra-preço, não representação-contra-representação**. Um reparo
> lexicográfico sempre vencerá um preço de 0,10 na métrica que o reparo ataca diretamente. Para a
> pergunta original — "a ordem como gene planeja melhor?" — este experimento responde: *não sob esta
> ponderação*, e não responde nada sobre outras ponderações.
>
> O teste que decidiria a questão é uma varredura de `FitnessWeights.SOFT_PREREQUISITE_ORDER`, com a
> v2 medida em cada ponto. **Não foi feita aqui, e não deve ser feita sem decisão de produto**: subir
> aquele peso muda o que o sistema promete ao aluno, e este repositório registra que inventar um
> coeficiente e apresentá-lo como aritmética é o erro que a ponderação lexicográfica do baseline
> existe para evitar.

---

## 6. Custo

> **Nota de correção, 2026-09-30 (G17).** Esta seção afirmava que o custo da v2 estava em
> "recomputar o calendário inteiro por descendente". **Isso estava errado**, e o erro era de
> diagnóstico por leitura em vez de medição: perfilado com JFR,
> `AvailabilityAllocator.over(request)` custa **1,4 µs de 53 µs por reparo — 2,6%**. O custo real
> estava em outro lugar, descrito em §6.2. Os números da tabela abaixo são pós-otimização.

| motor | mediana | p90 | gerações | avaliações |
|---|--:|--:|--:|--:|
| `greedy-baseline` | **167 µs** | 320 | 0 | 0 |
| `ga` (v1) | **5 693 µs** | 7 400 | 60 | 2 440 |
| `ga-timeline` (v2) | **102 491 µs** | 199 000 | 60 | 2 440 |

**As avaliações são iguais entre v1 e v2 — 2 440 — e o tempo difere quase vinte vezes.** Logo o custo
da v2 não está na busca: está no que ela faz **por descendente** enquanto a v1 o faz uma vez no fim.

### 6.1 Por que as medianas do harness não servem para medir uma otimização

A máquina de medição varia mais que o efeito que se quer ver. Entre duas execuções do harness sem
nenhuma mudança de código, a mediana do guloso mudou de 138 para 167 µs e a da v1 de 3 897 para
5 693 µs — <b>e o guloso não toca nenhuma linha que foi alterada</b>. Uma otimização de 30% fica
dentro desse ruído.

Então o efeito foi medido por **A/B controlado na mesma máquina**, alternando entre o `HEAD` commitado
e a versão otimizada, com a medição "antes" repetida ao fim para limitar a deriva:

| | execuções (ms por busca completa da v2) | mediana |
|---|---|--:|
| antes | 268,4 · 265,0 · 276,8 | 268,4 |
| **depois** | **195,3 · 199,2 · 199,7** | **199,2** |
| antes, de novo | 273,7 · 278,0 · 279,1 | 278,0 |

**1,37× mais rápido.** A deriva entre os dois blocos "antes" é de ~4%, uma ordem de grandeza abaixo do
efeito. A razão v2/v1 no harness passou de 27× para **18×**, mas essa leitura é confundida pela deriva
— a mediana da própria v1 subiu 1,46× no mesmo intervalo —, então o número a citar é o A/B: **1,37×**.

### 6.2 Onde o tempo estava de verdade

Perfil JFR sobre a busca da v2 (25 tópicos, grafo denso, horizonte esparso), atribuindo cada amostra
ao quadro mais alto do próprio código:

| quadro | amostras | |
|---|--:|---|
| `TacticalStudyPlan.extractDaysPerItem` | **40 / 166** | **24%** |
| `PlanningItemIndex.of` + `projectInts` + `positionOf` | 17 / 166 | 10% |
| `DayBoundaryCrossover.copyDays` | 9 / 166 | 5% |
| `BlockSwapMutation.mutate` | 7 / 166 | 4% |
| `TimelineRepairer.place` | 6 / 166 | 4% |
| `TimelineRepairer.topologicalOrder` | 4 / 166 | 2% |

A alocação confirma: `HashMap$Node` e `HashMap$Node[]` lideram com folga. A causa é que
`extractDaysPerItem` alocava **um `HashSet<Integer>` por item** — e um `HashSet` é um `HashMap` por
dentro, com um nó por dia — mais um `Integer` por dia.

**A construção de um `TacticalStudyPlan` respondia por cerca de um terço do tempo da v2.** Ela não
custava nada enquanto um plano tático era construído uma vez por requisição, que é o caso de
`SessionPlacement`; o motor de linha do tempo constrói vários por descendente.

### 6.3 O que foi feito, e o que não foi

| mudança | efeito |
|---|---|
| `extractDaysPerItem`: `BitSet` no lugar de `HashSet<Integer>` por item | o maior ganho isolado, ~8,5% |
| `TimelineRepairer`: posições, dependentes e in-degrees pré-computados no construtor | tira do laço um `TreeMap<UUID>` sob `BY_TEXT`, cujo comparador aloca uma `String` de 36 caracteres por comparação |
| `AvailabilityAllocator.rewound()`: reusa as janelas já preparadas | pequeno (2,6%), mas grátis |
| `TacticalSlots.ordered`: ordena só se ainda não estiver ordenado | o reparador já emite em ordem de calendário |
| `BlockSwapMutation` / `MethodologyMutation`: devolvem o plano de entrada quando nada mudou | com taxa 0,05 é o caso comum; **nenhum sorteio é poupado** |

**A estrutura das passadas da ordenação topológica foi preservada ao pé da letra**, e isso não é
conservadorismo: ela decide a saída. Um Kahn de manual, que sempre toma o pronto de menor índice de
preferência, dá ordem **diferente** — com preferência `[B, A, C]` e `A` pré-requisito de `B`, a
varredura dá `[A, C, B]` e o Kahn dá `[A, B, C]`, porque a varredura já passou de `B` nesta passada e
o Kahn o toma na hora. Só o **teste por candidato** ficou mais barato.

**Não foi feito: compartilhar o `PlanningItemIndex` entre os indivíduos da população.** Ele é função
pura do conjunto de itens, que é o mesmo para toda a população, e eliminaria os 10% da segunda linha
do perfil. Mas a ordem canônica dos genes de um plano tático sai hoje da ordem de iteração de um
`HashMap` (fragilidade **G6**), então passar um índice compartilhado **mudaria** essa ordem — e com
ela a ordem de somas de ponto flutuante rio abaixo. Exigiria passar o índice por todos os pontos de
construção de `TacticalStudyPlan`, vários deles em classes com teste, por ~10% contra uma linha de
base de 18×. Fica registrado como caminho conhecido, não tomado.

### 6.4 O que sobra é estrutural

Depois de 1,37×, a v2 segue ~18× mais lenta que a v1 com o **mesmo** número de avaliações. O que
resta não é desperdício: é o reparo rodando por descendente, e o reparo é o que garante validade. Um
reparo **incremental** — que aproveitasse o calendário do pai em vez de reconstruí-lo — é a única
mudança que mudaria a ordem de grandeza, e é uma mudança grande, com risco real de correção, sobre um
mecanismo cuja utilidade **G16 ainda não estabeleceu**. A ordem correta continua sendo G16 antes de
G17.

## 7. O que a v2 ganha

| métrica de resultado | guloso | v1 | v2 |
|---|--:|--:|--:|
| Tópicos agendados | 13,64 | 13,52 | **15,23** |
| Utilização | 0,6935 | 0,7713 | **0,8443** |
| `peak_over_mean` (1,0 = nivelado) | 1,6727 | 1,6945 | **1,5616** |
| `first_quarter_share` | 0,2594 | **0,2186** | 0,3460 |

A v2 entrega **1,7 tópico a mais** que a v1 em média, usa **7 pontos percentuais** mais do calendário
declarado, e produz dias mais **nivelados**. Em troca concentra mais esforço no primeiro quarto do
horizonte (0,346 contra 0,219), que é a direção pior se front-loading for indesejado — e nada neste
repositório decidiu que é.

**Um leitor que só olhasse inversões concluiria que a v2 é um retrocesso. Um que só olhasse cobertura
concluiria que é um avanço.** As duas leituras são verdadeiras sobre métricas diferentes, e é por isso
que o grupo (b) tem quatro famílias de número em vez de um índice composto.

---

## 8. A fitness: G14 e G15 funcionaram

| motor | agregado > 0 | bruto médio | agregado máximo |
|---|--:|--:|--:|
| `greedy-baseline` | 15 / 360 | −0,2160 | 0,0150 |
| `ga` (v1) | 97 / 360 | −0,1322 | 0,2705 |
| `ga-timeline` (v2) | **328 / 360** | **+0,3032** | **0,5947** |

Antes das duas correções, o agregado estava preso em zero em **94,6%** das execuções, e a v2 teria
buscado num platô: toda a população marcaria zero e o torneio escolheria ao acaso. O relatório
reportaria "sem diferença" por um motivo que não tem nada a ver com a representação sob teste — o
pior desfecho possível, porque parece um resultado.

Com a severidade graduada (**G14**) e a seleção ordenando pelo bruto onde o limite apagava a ordem
(**G15**), a v2 tem agregado positivo em 328 de 360 execuções e bruto médio **+0,303**. **A busca da
v2 funciona.** É justamente por isso que §5 é uma conclusão sobre pesos e não sobre gradiente.

---

## 9. Variância entre sementes, e a revisão do limiar proposto

| motor | células idênticas nas 5 sementes | amplitude de inversões (média / máx.) | total da matriz por semente | amplitude |
|---|--:|--:|---|--:|
| `greedy-baseline` | **72 / 72** | 0,000 / 0 | 196, 196, 196, 196, 196 | **0 (0,00%)** |
| `ga` (v1) | 42 / 72 | 0,083 / 1 | 86, 82, 84, 84, 84 | 4 (**4,76%**) |
| `ga-timeline` (v2) | **14 / 72** | **0,903 / 4** | 125, 135, 140, 138, 136 | 15 (**11,13%**) |

**A v2 é mais de duas vezes mais sensível à semente que a v1** — 11,13% contra 4,76% do total da
matriz — e só 14 das 72 células repetem entre as cinco sementes. Isso é esperado: o espaço de
permutações é muito maior que o de alocações, e a v2 o percorre com o mesmo orçamento de busca.

### O que muda na proposta da etapa 09

A proposta de §10.2 de [`09`](./09-medicao-baseline-vs-v1.md) tinha três partes. Duas sobrevivem
inalteradas e uma **não sobrevive**:

* **Travas binárias (zero inversões `HARD`, reprodutibilidade):** valem para a v2 igualmente. Já estão
  no harness e em `MeasurementHarnessTest`. **Sem mudança.**
* **Teto sobre o total, derivado da amplitude observada:** o método continua válido, os números não.
  Para a v2, máximo observado 140 + 2 × amplitude 15 = **170**. Note que a margem relativa dobra por
  causa da variância maior — o que é a proposta se comportando como deveria.
* **Dominância pareada — ~~travar `inversões(motor) ≤ inversões(guloso)`~~:** **retirada.** Ela valia
  360/360 quando os motores eram dois. Com a v2 no quadro, ela vale contra o guloso (305/360, com 55
  exceções) e **não vale** entre v1 e v2. Uma trava sem limiar só é melhor que um número calibrado
  enquanto a propriedade é realmente universal; esta não era, e travá-la teria congelado a v2 fora do
  repositório por uma afirmação que a medição refuta.

**Nenhum limiar foi configurado**, pelo mesmo motivo da etapa 09: 5 sementes bastam para dizer que a
variância existe e é maior na v2, não para calibrar uma margem. Com a v2 no quadro isso fica mais
urgente, não menos.

---

## 10. Limitações

1. **A v2 não busca a alocação** (§2.2). Metade do que a v1 decide, a v2 recebe pronto. Uma v3 que
   busque as duas coisas é possível e não foi medida.
2. **O reparo não intercala.** `TimelineRepairer` reordena **por item**: quando A precede B, todos os
   blocos de A precedem todos os de B. É o que torna a garantia rígida verificável na leitura forte
   que `Invariants.hardInversions` cobra. O preço é que a v2 **não explora intercalação**, e
   intercalar é útil para retenção — explorá-la exigiria enfraquecer a leitura forte para uma leitura
   por sessão, que é outra decisão.
3. **Sem hipermutação.** O laço macro a usa contra estagnação; a v2 não, porque acrescentá-la sem
   medir se a v2 estagna seria importar um mecanismo por simetria.
4. **5 sementes**, e a v2 precisa de mais que a v1 (§9).
5. **Instâncias sintéticas**, como na etapa 09. A **forma** dos resultados é robusta; os valores não
   transferem para um currículo real.
6. **O custo da v2 foi otimizado em 1,37× e não mais que isso** (§6). A razão de ~18× mede a
   implementação atual do reparo e não um limite da abordagem: um reparo incremental mudaria a ordem
   de grandeza, e não foi tentado porque G16 ainda não estabeleceu que o mecanismo vale a pena.

---

## 11. A v2 deve ficar?

A pergunta que o experimento existia para responder, respondida com os números acima.

**Como está, ela não substitui a v1**: perde na métrica que motivou sua construção, por ~18× o custo.
**E não é código morto**: ela é a única das três condições cuja busca enxerga ordem, ela ganha em
cobertura e nivelamento, e ela é a única que pode responder à pergunta de §5 se o peso das
preferências vier a ser revisto.

A recomendação é **mantê-la registrada e não torná-la padrão** — `plan.engine.default` segue em
`greedy-baseline`, e nada nesta etapa o mudou. As três condições agora rodam contra o mesmo build,
pela mesma seleção de motor, e qualquer revisão de peso pode ser medida nas três de uma vez.

---

## 12. Pendências

| # | Gap | Status | Onde |
|---|---|---|---|
| **G14** | Severidade binária de `MandatoryReviewConstraint` saturava a fitness | ✅ **RESOLVIDO** — severidade graduada; saturação de 94,6% para 84,4% | [`09`](./09-medicao-baseline-vs-v1.md) §9 |
| **G15** | `clamp(raw,0,1)` apagava a ordenação entre planos táticos inviáveis | ✅ **RESOLVIDO** — a seleção ordena pelo bruto em plano tático; a publicação segue limitada. Agregado positivo da v2: 328/360 | §8 |
| **G16** | **A ponderação decide o resultado de §5, e ninguém a mediu.** `SOFT_PREREQUISITE_ORDER = 0,10` faz a v2 trocar ordem por cobertura com lucro. A comparação v1 × v2 mede reparo lexicográfico contra preço, não representação contra representação | ⬜ **ABERTO — decisão de produto.** Uma varredura do peso com a v2 medida em cada ponto responderia; subir o peso muda o que o sistema promete ao aluno | §5 · `ga/fitness/FitnessWeights` |
| **G17** | **A v2 custava 27× a v1 com as mesmas 2 440 avaliações.** O diagnóstico original — "recomputa o calendário por descendente" — **estava errado**: medido com JFR, o recomputo do calendário é **2,6%**, e ~⅓ do tempo estava em `TacticalStudyPlan.extractDaysPerItem`, que alocava um `HashSet<Integer>` por item | 🟡 **PARCIAL** — **1,37× mais rápido** (A/B controlado, deriva ≤4%), saída **bit a bit idêntica** nas 1 080 linhas. O resto é estrutural: o reparo por descendente é o que garante validade. Um reparo incremental mudaria a ordem de grandeza e é mudança grande sobre mecanismo que **G16 ainda não justificou** | §6 · `domain/tactical/TacticalStudyPlan` · `sinapse/timeline/TimelineRepairer` |

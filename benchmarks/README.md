# Benchmarks — instrumento de medição

Módulo de medição isolado. **Nada aqui é servido em produção.**

Ele responde a uma pergunta e só a ela: *dois motores que atendem o mesmo `POST /plans` produzem
planos diferentes, e em quê?* Hoje os motores são o **guloso** (`greedy-baseline`, EOA-2) e o
**genético com etapa tática de pré-requisitos** (`ga`, EOA-7, chamado **v1** nos relatórios). O
cromossomo de linha do tempo (**v2**) entra como uma terceira condição sem reescrever nada — ver
"Acrescentar um motor", abaixo.

Relatório da primeira medição:
[`docs/revisao-ag/09-medicao-baseline-vs-v1.md`](../docs/revisao-ag/09-medicao-baseline-vs-v1.md).
Dados brutos: [`results/measurement.csv`](./results/measurement.csv).

---

## Isolamento e política de build

`benchmarks/java` é anexado como **test source root** pelo `build-helper-maven-plugin`
(ver `pom.xml`). Três consequências, todas deliberadas:

1. **O harness compila no CI.** `./mvnw verify` o compila junto com a suíte, então uma mudança em
   produção que quebre o instrumento reprova o build. Isto não é detalhe de conveniência: a execução
   anterior deste módulo ficou **desligada do build** na EOA-4b, quando o sistema que ela media foi
   removido, e apodreceu em silêncio — dez arquivos importando tipos que não existiam mais, e nada
   dizia nada, porque nada os compilava.
2. **O harness não entra no artefato.** O `spring-boot-maven-plugin` empacota apenas `src/main`; um
   test source root compila para `target/test-classes`, que o repackage não inclui.
3. **Os pisos de cobertura não são afetados.** O *bundle* do JaCoCo é `target/classes`; código em
   `target/test-classes` não entra nele nem como instrução coberta nem como descoberta. Verificado,
   não suposto: o pacote `benchmark` **não aparece em nenhuma linha** de
   `target/site/jacoco/jacoco.csv` — o bundle analisa 118 classes, todas de `src/main`.
   O único efeito que este módulo tem sobre a cobertura vem de `MeasurementHarnessTest`, que —
   sendo teste — exercita código de produção e só pode **subir** a medição, nunca baixá-la.
   Medição atual: `INSTRUCTION` 9 208/9 823 = 0,9374 (piso 0,9325), `BRANCH` 692/796 = 0,8693
   (piso 0,7861). Os pisos do `pom.xml` não foram tocados.

**O Checkstyle também cobre este diretório.** Ele roda em `validate` e o `build-helper` acrescenta a
raiz em `generate-test-sources`, que é *depois* — então `testSourceDirectories` está listado à mão no
`pom.xml`. Sem isso, o harness compilaria no CI e estaria isento das regras de estilo por acidente de
ordenação de fase.

**Cobertura deste módulo: nenhuma exigida, e a razão importa.** O piso do JaCoCo mede o sistema, não
o instrumento. O que garante o instrumento é `MeasurementHarnessTest`, que roda na suíte normal: ele
verifica que a matriz roda, que as invariantes valem, que o CSV alinha, que a biblioteca é
determinística — e, por sabotagem, que a checagem de inversão rígida **reprova** quando lhe entregam
um plano inválido. Uma checagem que nunca reprova não verifica nada.

---

## Como rodar

### A verificação rápida (parte da suíte)

```bash
./mvnw -o test -Dtest=MeasurementHarnessTest
```

Cinco testes, ~8 s incluindo a subida do contexto Spring. Roda a matriz completa em duas instâncias
das pontas opostas do fatorial.

### A medição completa (manual)

```bash
./mvnw -q -o test-compile
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
java --enable-preview -cp "target/classes:target/test-classes:$(cat target/cp.txt)" \
     com.ia.project.dynamicstudyplanner.benchmark.harness.MeasurementMain
```

24 instâncias × 6 condições × 5 sementes = **720 linhas**, cada uma com **duas** chamadas ao motor
(a segunda é o auto-teste de reprodutibilidade). ~4 s. Escreve `benchmarks/results/measurement.csv`.

O perfil `baseline-core` é obrigatório e o `MeasurementMain` o ativa sozinho: sem ele a aplicação não
registra motor nenhum, nem seletor, nem endpoint.

---

## A matriz

| Eixo | Níveis | O que varia |
|---|---|---|
| `topics` | 10, 25 | tamanho do currículo |
| `density` | 0,4 / 1,2 | arestas de pré-requisito por tópico |
| `tightness` | 0,7 / 1,0 / 1,5 | minutos disponíveis ÷ minutos de primeira passada |
| `spread` | compacto / esparso | os mesmos minutos em dias seguidos, ou dia sim dia não |
| `engine` | `greedy-baseline`, `ga` | a condição sob teste |
| `provenance` | `curated` ⊂ `curated-textbook` ⊂ `all` | a ablação de arestas |
| `seed` | 5 valores distintos | a unidade de replicação |

`importance` é mantida fixa em `goal-priority` — não é um eixo desta medição — e mesmo assim é
escrita em toda requisição e registrada em toda linha, para que a linha diga o que a produziu em vez
de depender do que as propriedades do deployment diziam naquele dia.

**A unidade de replicação é a semente, e a comparação é pareada por instância.** Uma execução semeada
não tem ruído de medição: rode duas vezes e ela devolve o mesmo plano até o último bit — o que o
harness *verifica* em vez de supor, em toda linha. Repetir a mesma semente, portanto, não mede nada.
O que varia é *qual* semente.

### Acrescentar um motor

Uma linha em `Condition.ENGINES`. O harness nunca nomeia uma classe de motor, nunca constrói uma e
nunca ramifica por qual é: ele escreve o id em `algorithmParams.engine` e entrega a requisição ao
`PlanEngineSelector`, como a plataforma faria. O único lugar que distingue motores é a contagem de
avaliações, e ela distingue por **propriedade** (um motor que relata zero gerações não avaliou
população nenhuma), não por identidade.

---

## As quatro famílias de métrica

Elas não são intercambiáveis, e a ordem das colunas é o argumento.

| Grupo | O que é | Papel |
|---|---|---|
| **(a) invariantes** | zero inversões `HARD`, sessões dentro das janelas, sem sobreposição, `sequenceIndex` contíguo, reprodutibilidade | **validade, não qualidade.** Uma violação anula a execução; o harness aborta em vez de gravar a linha |
| **(b) resultado** | inversões `SOFT` residuais, inversões removidas, tópicos agendados, utilização, teto de carga, distribuição de dificuldade | **é aqui que as conclusões se apoiam.** Propriedades do cronograma, calculáveis por quem nunca viu a função objetivo |
| **(c) custo** | gerações, avaliações, tempo de parede | o que a busca cobrou |
| **(d) objetivo** | *F* e o `FitnessBreakdown` completo | **o objetivo do AG, relatado para completude** — nunca a comparação |

> **Por que (d) não compara nada.** O motor genético *maximiza* `F`; o guloso não otimiza função
> objetivo nenhuma e não relata nenhuma. Pôr as duas numa coluna chamada "fitness" seria comparar uma
> grandeza que um motor foi construído para maximizar contra uma que o outro nunca viu — enviesado
> por construção, na direção de quem a inventou. Por isso **todo plano é repontuado a posteriori pelo
> mesmo avaliador**, a composição de produção, injetada e não reconstruída; e mesmo assim o número
> sai rotulado `ga_objective_*`.
>
> A repontuação se verifica contra o único motor que publica o próprio escore: se o valor
> recalculado não bater **bit a bit** com o que o motor genético publicou em `fitness.aggregate`, o
> harness aborta — seria sinal de que o plano reconstruído não é o plano que o motor pontuou.

---

## Esquema do CSV

`results/measurement.csv`, UTF-8, separador vírgula, ponto decimal, seis casas. **Uma linha por
execução** — nada aqui é média de nada; toda agregação acontece no relatório, onde o método pode ser
declarado ao lado do número.

### Identidade

| Coluna | Tipo | Significado |
|---|---|---|
| `instance_id` | texto | `t{tópicos}-d{densidade}-a{folga}-{compact\|sparse}` |
| `topics` | inteiro | tópicos no currículo |
| `density` | real | arestas por tópico, como geradas |
| `tightness` | real | minutos disponíveis ÷ minutos demandados |
| `horizon_days` | inteiro | dias de calendário do horizonte |
| `demanded_minutes` | inteiro | soma de `estimatedMinutes` (só primeiras passadas) |
| `available_minutes` | inteiro | minutos utilizáveis dentro do horizonte |
| `engine` | texto | o motor, **como o seletor o carimbou** |
| `provenance` | texto | `curated` / `curated-textbook` / `all` |
| `importance` | texto | estratégia de importância da execução |
| `seed` | inteiro | a semente |
| `prerequisite_edges_hard` | inteiro | arestas `HARD` aplicáveis nesta condição |
| `prerequisite_edges_soft` | inteiro | arestas `SOFT` aplicáveis nesta condição |

### Grupo (a) — invariantes

| Coluna | Tipo | Significado |
|---|---|---|
| `invariants_ok` | booleano | sempre `true` — a linha não existe se for falso |
| `reproducible` | booleano | sempre `true` — idem |

Não é tautologia: o harness recusa construir a linha de uma execução que reprovou qualquer das duas
checagens, então as colunas registram que a checagem **rodou e passou** para aquela linha. Um `false`
ali significaria que o aborto foi contornado.

### Grupo (b) — resultado

| Coluna | Tipo | Significado |
|---|---|---|
| `soft_inversions` | inteiro | preferências ainda invertidas no plano produzido |
| `inversions_removed` | inteiro ou **vazio** | removidas pelo reparo. **Vazio, não zero**, quando nenhum reparo rodou — o guloso não repara, e "removeu nenhuma" é um fato diferente de "nenhum reparo foi tentado" |
| `topics_scheduled` | inteiro | tópicos que receberam ao menos a primeira sessão |
| `topics_unscheduled` | inteiro | tópicos que o calendário não coube |
| `scheduled_minutes` | inteiro | minutos efetivamente alocados |
| `utilisation` | real | `scheduled_minutes ÷ available_minutes` |
| `load_ceiling_bound` | booleano | se o teto diário de carga foi excedido |
| `load_excess_ratio` | real | por que fração foi excedido; 0 quando não foi |
| `first_quarter_share` | real | fração do esforço (dificuldade × minutos) no primeiro quarto dos dias ativos |
| `peak_over_mean` | real | dia mais pesado ÷ dia médio; 1,0 é perfeitamente nivelado |

### Grupo (c) — custo

| Coluna | Tipo | Significado |
|---|---|---|
| `generations` | inteiro | gerações relatadas pelo motor; 0 para motor não evolutivo |
| `population_size` | inteiro | indivíduos por geração; 0 para motor não evolutivo |
| `evaluations` | inteiro | `population_size × (1 + generations)` — derivado, ver abaixo |
| `elapsed_micros` | inteiro | tempo de parede medido **pelo harness** |

`metadata.elapsedMillis` é zero por construção nos dois motores, de propósito: uma duração medida na
resposta faria duas execuções da mesma requisição semeada diferirem. Então o tempo é medido de fora,
em **microssegundos** — um relógio de milissegundos não resolve o guloso, que responde em bem menos
de um, e reportaria a mediana dele como zero.

**`elapsed_micros` é a única coluna que muda entre duas execuções do `MeasurementMain`.** Verificado:
rodando em processos separados, as 720 linhas saem idênticas nas outras 50 colunas. Ao comparar dois
CSVs, ignore esta coluna — qualquer outra diferença é uma mudança de comportamento.

As avaliações são derivadas e não contadas porque contá-las exigiria um contador em `Population`, no
caminho de produção, existindo só para o harness. A contagem não está em dúvida:
`Population.calculateFitness` é chamada em exatamente dois lugares —
`DefaultPopulationGenerator.generate` uma vez, e `GeneticAlgorithm.evolvePopulation` uma vez por
geração — enquanto a seleção e os operadores de cruzamento leem o valor já em cache no `Individual`.

### Grupo (d) — objetivo do AG, relatado para completude

| Coluna | Tipo | Significado |
|---|---|---|
| `ga_objective_aggregate` | real | *F* final: `clamp(raw,0,1) × fator de penalidade` |
| `ga_objective_raw` | real | soma das contribuições ponderadas, antes do limite |
| `ga_objective_bounded` | real | `raw` limitado a `[0,1]` |
| `ga_objective_penalty_factor` | real | produto dos fatores multiplicativos; 1,0 quando nenhum incide |
| `ga_term_{nome}_value` | real | valor normalizado do termo em `[0,1]` |
| `ga_term_{nome}_weight` | real | peso declarado do termo |
| `ga_term_{nome}_weighted` | real | o que o termo somou ao `raw`; já negativo para uma restrição |

**As colunas por termo saem da composição, não de uma lista escrita à mão.** `MeasurementRow.header`
recebe os nomes e `cells` os lê de volta do `FitnessBreakdown` pelo mesmo nome, então um termo
acrescentado à composição acrescenta coluna nos dois lugares ou em nenhum. Um termo que a composição
não declara sai **vazio**, nunca zero: um termo que não rodou não é um termo que pontuou nada.

Na composição atual (`sinapse`) os termos são, nesta ordem: `syllabusMastery`, `retention`,
`dailyLoadBudget`, `MinimumDaysConstraint`, `MandatoryReviewConstraint`,
`SoftPrerequisiteOrderConstraint` — 51 colunas no total.

---

## Estrutura

| Pacote | Conteúdo |
|---|---|
| `instance` | `InstanceLibrary` (o fatorial, determinístico) e `BenchmarkInstance` (uma instância com suas coordenadas) |
| `metric` | `Invariants` (a), `OutcomeMetrics` (b), `CostMetrics` (c), `PlanScoring` (d) e `MeasurementRow` (o esquema) |
| `harness` | `Condition` (a matriz), `MeasurementHarness` (a execução), `MeasurementCsv`, `MeasurementMain` |
| `strategy` | Estratégias de alocação de nível macro, herdadas da revisão do AG — **fora da matriz de motores**, ver abaixo |

### Por que `strategy/**` não está na matriz

As seis estratégias (`aleatorio`, `uniforme`, `guloso-prioridade`, `melhor-de-n-aleatorios`,
`otimo-exato` e o auxiliar `Allocations`) distribuem um orçamento de sessões entre tópicos e param
aí: elas produzem um `StudyPlan` macro, não um plano colocado no calendário. Um motor produz um
`PlanResponse` inteiro. Comparar um com o outro compararia dois orçamentos diferentes —
`BenchmarkInstance.totalStudyDays()` é o orçamento **do harness**, e explicitamente não uma cópia do
que o `GeneticPlanEngine` deriva internamente.

Elas continuam compilando e continuam corretas; são material para uma pergunta de alocação, não para
esta comparação de motores.

---

## Arquivo

`archive/2026-09-01-concurso-path/` guarda os CSVs da revisão do AG anterior, junto com um README que
declara o commit que os produziu, o commit que removeu o sistema medido, e por que **nenhum número
deles atravessa** para cá: mudou a unidade de planejamento, mudou a função objetivo e mudaram as
instâncias. Eles são mantidos por serem a única proveniência de cinco relatórios de
`docs/revisao-ag/` — não por serem comparáveis com o que está em `results/`.

**Nenhum número do harness antigo aparece no relatório novo.**

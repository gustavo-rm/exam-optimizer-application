# Verificação Pós-Rodada: Sequenciamento, Números e Guardrails

**Objeto:** verificar em código — não por leitura dos relatórios anteriores — se existe lógica de
pré-requisitos em algum ponto do sistema, e revalidar do zero os números da rodada anterior.
**Método:** varredura de todo o repositório, leitura das camadas fora do escopo "fitness", testes de
caracterização executados, e reconstrução dos commits `07c99da` (etapa 03) e `0083188` (etapa 05) em
*worktrees* isolados para reproduzir os números originais com o código original.
**Nada foi corrigido nesta etapa.** O único código adicionado é um teste de caracterização, que
documenta comportamento atual e não o altera.

> ## ⚠ Nota de atualização — 2026-08-31 (etapa 07)
>
> Os dois achados principais deste relatório (§5.2 e §3.2) foram **corrigidos na etapa 07**, e as
> pendências G1–G11 da §7 têm agora status consolidado em
> [`README.md`](./README.md). Duas atualizações de número afetam a leitura deste documento:
>
> 1. **A média aritmética simples usada aqui era um substituto ad hoc**, escolhido durante a
>    verificação para demonstrar que o empilhamento invertia o sinal. O método canônico definido na
>    etapa 07 é a **transformada z de Fisher** ponderada por `n−3`
>    (`benchmarks/…/metric/CorrelationAggregate.java`). Os valores mudam de
>    −0,910 / −0,358 / −0,015 para **−0,909 / −0,460 / −0,106**; a conclusão — o empilhamento
>    inverte o sinal, a trajetória real converge para zero sem cruzá-lo — é a mesma.
> 2. **A baseline da etapa 03 foi remedida** em 10 execuções e passa a ser reportada como
>    média ± desvio-padrão. Ver [`07-correcao-metrica-e-ausubel.md`](./07-correcao-metrica-e-ausubel.md).

---

## 0. Resumo executivo

| Pergunta | Resposta verificada |
|---|---|
| Existe lógica de pré-requisitos em algum lugar? | **Não.** Zero ocorrências em 153 arquivos Java, em nenhuma camada. Confirmado por teste executado |
| A afirmação "Ausubel não implementado" estava certa? | **Sim** — classificação (i) quanto à existência |
| Então não há achado? | **Há.** A *premissa* usada para justificá-la é falsa sobre o repositório, e o custo da opção (i) foi estimado ignorando andaime já existente — classificação (ii) quanto ao raciocínio |
| Os números da etapa 05 batem? | **Sim, exatamente**, quando reconstruídos no commit da etapa 05. As divergências contra hoje são todas explicadas pela temperagem da etapa 06 |
| Algum número não bate? | **Sim, dois.** A estatística agregada é metodologicamente inválida e inverte a conclusão; e os números da etapa 03 não são reprodutíveis |
| Guardrails intactos? | **Sim**, ambos, agora com teste |

**O achado principal não é sobre Ausubel.** É que a frase da etapa 05 *"o sinal agregado inverteu"* —
a evidência de manchete de que a correção da fitness funcionou — repousa sobre uma estatística que
empilha valores de fitness que o próprio código documenta como **não comparáveis entre instâncias**.
Recalculada corretamente, a mesma medição da etapa 05 dá **−0,460** (Fisher z; −0,358 pela média
simples usada ad hoc aqui), não **+0,161**. A conclusão de fundo sobrevive; a evidência apresentada,
não (§5.2).

---

## 1. Levantamento: o que foi procurado e onde

Varredura sobre **todo** o repositório, não apenas os arquivos mapeados na etapa 00.

**Termos buscados** (case-insensitive, incluindo português): `prerequisite`, `pre-requisit`,
`pré-requisit`, `prereq`, `dependenc`, `topolog`, `sequenc`, `subsun`, `ausubel`, `precede`,
`predecessor`, `successor`, `ordering`, `ordem`, `requires`, `blocked`, `unlock`, `gate`, `graph`,
`adjacen`, `edge`, `node`, `parent`, `child`, `tier`, `stage`, `phase`, `curricul`, `scaffold`,
`anchor`, `foundation`, `básico`, `avançado`, `trilha`, `roteiro`, `encadea`, `antes`, `depois`,
`before`, `after`.

**Camadas inspecionadas** — todas lidas, não apenas grepadas:

| Camada | Arquivos | Resultado |
|---|---|---|
| Modelo de domínio | `Subject`, `ThematicAxis`, `Exam`, `StudyPlan` | Nenhum campo relacional |
| Contrato de API | `SubjectDto`, `ExamDto`, `ThematicAxisDto`, mappers | Nenhum campo relacional |
| Objetivos da fitness | `ScoreGainObjective`, `RetentionObjective`, `CognitiveLoadObjective` | Nenhuma noção de ordem |
| Restrições de viabilidade | `ConstraintValidator` e suas **2** implementações | Nenhuma noção de ordem |
| Penalidades | `FitnessPenalty` e suas **2** implementações | Nenhuma noção de ordem |
| Operadores de crossover | `HybridCrossover`, `WeightedAverageCrossover`, `RepairingCrossover` | Reparam **soma de dias**, nada mais |
| Operadores de mutação | `AbstractMutationStrategy`, `CreepMutation`, `TransferMutation` | Idem |
| Validação de cromossomo | `StudyPlan.meetsMinimumConstraints`, `StudyPlanFactory` | Só piso de dias e orçamento |
| **Operadores de reparo** | `ChromosomeRepairer`, `SpacedRepetitionRepairer` | Repetição espaçada, **não** precedência |
| Agendador (pós-AG) | `StudyScheduleGenerator`, 5 × `AllocationStrategy` | Ordenam por carga/recência/aleatório |
| Serviços de cálculo | `Importance`, `Baseline`, `CognitiveLoad`, `HybridRetentionEngine`, `Fatigue`, `Dropout` | Nenhuma noção de ordem |
| Camada tática (morta) | `ga/tactical/**`, `service/scheduler/tactical/**`, `domain/tactical/**` | Metodologia e revisão, **não** precedência |

**Resultado: nenhuma ocorrência de lógica de pré-requisitos, em nenhuma camada.** Todos os acertos
de `parent`/`child`/`before`/`after` são vocabulário de algoritmo genético (pai/filho, gerações) ou
prosa de Javadoc. Verificação independente no histórico: `git log --all -S"prerequisite"` e
`-S"Ausubel"` sobre os 77 commits não retornam **nenhum** commit de código — só os documentos desta
revisão e o README original.

### 1.1 Por que o resultado é estrutural, não acidental

Duas barreiras impedem que a informação sequer chegue às camadas que ordenam:

1. **`Subject` é um record de três campos** — `name`, `questionCount`, `cognitiveLoad`
   (`domain/exam/Subject.java:11-15`). Não há onde guardar uma dependência.
2. **`AllocationContext`** — o objeto que as estratégias de ordenação recebem — carrega
   `availableHoursToday`, `hoursToSchedulePerSubject` e `lastStudiedDateMap`
   (`service/scheduler/strategy/AllocationContext.java:12-16`). Mesmo que `Subject` tivesse
   pré-requisitos, **nenhuma das cinco estratégias os veria**: não há canal.

---

## 2. Cada trecho relevante encontrado

Nenhum implementa precedência. Registrados porque são o que existe de mais próximo e porque a
classificação da §3 depende deles.

### 2.1 Lógica de **ordem** que existe e está ativa

| Componente | O que faz exatamente | Ponto do pipeline | Ativo? |
|---|---|---|---|
| `InterleavedCriticalStrategy` | Pega as **3** disciplinas com mais horas pendentes e as alterna em *round-robin* no dia | **Depois** da fitness | **Sim** — caminho principal |
| `CognitiveLoadBalancingStrategy.reorderForBalance` | Ordena os blocos do dia por `cognitiveLoad` e intercala mais difícil / mais fácil (dois ponteiros) | **Depois** da fitness | **Sim** — caminho principal |
| `ReviewFocusedStrategy` | Reserva a 1ª hora do dia para a disciplina **estudada há mais tempo** | **Depois** da fitness | **Sim** — caminho principal |
| `CriticalFirstStrategy` | Sempre a disciplina com mais horas pendentes | — | **Não** — existe, não cabeado |
| `RandomizedInterleavedStrategy` | Embaralha as disciplinas do dia | — | **Não** — existe, não cabeado |

Os três ativos são compostos em `DynamicStudyPlannerService.java:64-69` como
`ReviewFocusedStrategy(CognitiveLoadBalancingStrategy(InterleavedCriticalStrategy))`.

**Nenhum lê qualquer relação entre disciplinas.** Ordenam por *quantidade pendente*, *carga
cognitiva* e *recência*. São ordenação **dentro de um dia**, sobre disciplinas já escolhidas — não
sequenciamento curricular.

### 2.2 Encoding indexado no tempo — existe, está morto, e é o achado da §3

| Componente | O que é | Ativo? |
|---|---|---|
| `TacticalStudyPlan` | Cromossomo `Map<TimeSlot, TacticalStudyBlock>`; **estende `StudyPlan`** | Instanciável; **não produzido por nenhum AG** |
| `TimeSlot` | `(startTime, endTime)` — `LocalDateTime` reais | — |
| `SpacedRepetitionRepairer` | Reparo: injeta revisão obrigatória sobrescrevendo o bloco de menor ROI | `@Component`, **nunca injetado** |
| `DayBoundaryCrossover` | Crossover que corta em fronteira de meia-noite | **Nunca instanciado** |
| `MethodologyMutation` | Mutação da metodologia do bloco | **Nunca instanciado** |
| `HybridHeuristicScheduler` | Empacota o plano macro em janelas de disponibilidade | `@Service`, **nunca injetado** |

Verificação de cabeamento (grep por usos fora dos próprios pacotes): **zero** referências a
`TacticalScheduler`, `HybridHeuristicScheduler`, `ChromosomeRepairer`, `SpacedRepetitionRepairer`,
`TacticalCrossoverStrategy`, `DayBoundaryCrossover`, `TacticalMutationStrategy`,
`MethodologyMutation` e `AvailabilityWindow`. `TacticalStudyPlan` só aparece fora do pacote como
**guarda de tipo** (`instanceof`) em `MandatoryReviewConstraint`, `DropoutRiskPenalty` e
`FatigueAndSustainabilityPenalty` — as guardas inertes que a etapa 01 já havia provado nunca abrirem.

> **Crédito onde é devido:** a etapa 00 mapeou esta camada corretamente e a chamou de
> "ANDAIME NÃO CABEADO" (`00-diagnostico.md` §1.9, e a nota em §1.2 sobre o segundo encoding).
> O problema não é que alguém não a viu. É o que as etapas seguintes fizeram com essa informação.

---

## 3. Reconciliação com a afirmação anterior

A afirmação sob exame, em `05-fitness-function.md` §4.1 e `06-decisao-ausubel.md` §1:

> "A aprendizagem significativa de Ausubel é uma restrição de **ordem** […] **O cromossomo macro não
> tem ordem**, então isso é inexprimível por construção, não por omissão."

A classificação exige separar duas asserções, porque elas não têm o mesmo veredito.

### 3.1 Asserção A — "Ausubel não está implementado" → **(i) estava certa**

Verificada por varredura completa (§1), por leitura das 12 camadas, pelo histórico de 77 commits e —
o que importa mais — por execução. Ver §4.

### 3.2 Asserção B — "é inexprimível por construção" → **(ii) estava incompleta**

A frase é verdadeira sobre `Map<Subject,Integer>`. **É falsa sobre o repositório**, que contém um
segundo cromossomo com posição temporal completa (§2.2) — e que o `FitnessEvaluator` **já aceita
hoje**, sem alteração de assinatura, porque `TacticalStudyPlan extends StudyPlan`.

A consequência não é retórica. Ela recai sobre `06-decisao-ausubel.md` §2.4, que descartou a "versão
mínima" pedida no enunciado daquela etapa com este argumento:

> "**Essa checagem é vacuosa sobre o encoding atual.** `Map<Subject, Integer>` não tem ordem: não
> existe 'antes' para verificar. Qualquer validador topológico escrito hoje passaria para **todo**
> indivíduo, por construção."

A primeira metade é correta. A generalização **"qualquer validador topológico escrito hoje"** não é:
sobre `TacticalStudyPlan`, um validador topológico teria a informação de que precisa, e a pergunta
"A vem antes de B?" **tem resposta** — demonstrado por teste na §4.2.

E recai também sobre `06-decisao-ausubel.md` §2.1, cuja tabela de esforço lista como item 4
*"Trocar `Map<Subject,Integer>` por estrutura com ordem"* e como item 5 *"o maior item"* —
sem mencionar em momento algum que uma estrutura com ordem, seus operadores de crossover e mutação, e
um operador de reparo **já existem no repositório**. A estimativa de §2.3 ("da mesma ordem de
grandeza ou maior" que as etapas 00–05 somadas) foi feita sobre um ponto de partida do zero que não é
o ponto de partida real.

### 3.3 O que isso muda — e o que não muda

**Não muda a decisão.** O bloqueio decisivo registrado em `06-decisao-ausubel.md` §2.1 item 2 —
*alguém precisa autorar o DAG de pré-requisitos de cada edital* — é de conteúdo editorial, não de
engenharia, e permanece intacto. A API também não coleta o dado (`SubjectDto`, três campos). Um
encoding pronto sem DAG não implementa Ausubel.

**Muda a qualidade do argumento.** A recomendação foi sustentada por dois pilares: "é impossível com
este encoding" e "não há dados". O primeiro pilar está errado como enunciado — o correto é *"é
impossível com o encoding que produção executa; existe outro, morto, que poderia"*. Numa auditoria
técnica, um revisor que abrir `domain/tactical/TacticalStudyPlan.java` vai encontrar
`Map<TimeSlot, TacticalStudyBlock>` três minutos depois de ler que ordem é "inexprimível por
construção". **Esse é o risco concreto que este documento existe para eliminar.**

---

## 4. Auditoria de fidelidade e teste executado

O item 4 do enunciado condiciona-se a *encontrar lógica de pré-requisitos ativa*. **Não há.** As
quatro perguntas de fidelidade ficam, portanto, sem objeto — não há regra para checar contra Ausubel,
não há critério de "pré-requisito coberto adequadamente" (nem binário, nem por limiar), e não há
rótulo teórico aplicado a uma regra que não é isso. Nesse ponto o produto está **correto por
ausência**: nada afirma sequenciamento no código, e o README já foi corrigido na etapa 06.

O enunciado pede que, na falta de teste, um seja escrito e o **resultado real** reportado. Escrito em
`src/test/java/…/ga/PrerequisiteSequencingDiagnosticTest.java`, **6 testes, todos passando**.

### 4.1 Prova de ausência

```java
TacticalStudyPlan respeita = tacticalPlan(FOUNDATION, ADVANCED);  // pré-requisito no dia 1
TacticalStudyPlan viola    = tacticalPlan(ADVANCED, FOUNDATION);  // dependente no dia 1
```

Os dois planos alocam exatamente o mesmo esforço e diferem **apenas na ordem**, que este encoding
expressa. Resultado medido pelo `FitnessEvaluator` de produção:

```
respeita precedencia : 0.2642479576
viola precedencia    : 0.2642479576
diferenca            : 0.0000000000
```

**Diferença exatamente zero.** Nenhum objetivo, restrição ou penalidade lê precedência. Se algum dia
alguém adicionar sequenciamento, este teste falha — que é o ponto dele.

No cromossomo macro a demonstração é ainda mais direta: `new StudyPlan(Map.of(A,10, B,6))` e
`new StudyPlan(Map.of(B,6, A,10))` produzem mapas **iguais** por `equals`. Não há violação a detectar
porque não há precedência a expressar.

### 4.2 Prova de que a vacuidade é do encoding, não do repositório

```java
Subject primeira = plan.getSchedule().entrySet().stream()
        .min(comparing(e -> e.getKey().startTime()))
        .map(e -> e.getValue().subject()).orElseThrow();
// devolve FOUNDATION — passou
```

A ordem **é** recuperável de um `TacticalStudyPlan`. E o `FitnessEvaluator` de produção avalia esse
plano sem qualquer alteração, devolvendo um valor em [0,1]. Um validador de precedência escrito
contra essa assinatura não seria vacuo. É a evidência executável da §3.2.

---

## 5. Revalidação dos números, do zero

Reconstrução limpa: `rm -rf target benchmarks/results`, `mvnw clean test-compile`, suíte completa
(**55 testes, 0 falhas**), e os quatro *mains* de benchmark reexecutados. Para separar "mudou porque
o código mudou" de "estava errado", os commits `07c99da` (etapa 03) e `0083188` (etapa 05) foram
reconstruídos em *worktrees* isolados e reexecutados **com o código deles**.

### 5.1 Confronto item a item

| Afirmação a verificar | Etapa 05 reconstruída | Hoje | Veredito |
|---|---|---|---|
| `I3` zerado (−0,029) | **−0,029** ✓ | **+0,257** | Reproduz na 05. Diverge hoje **pela temperagem da etapa 06**, e bate com o valor documentado em `06-regime-alta-carga.md` |
| `I7` zerado (+0,000) | **+0,000** ✓ | **+0,655** | Idem |
| `I4` reduzido à metade (−0,525) | **−0,525** ✓ | **−0,093** | Idem |
| `I8` em −0,880 | **−0,880** ✓ | **−0,880** | **Bate nos dois estados** |
| Uniforme vence `I3`: 96% × 76,6% | **96,0% × 76,6%** ✓ | **96,0% × 76,6%** | **Bate exatamente** |
| Uniforme vence `I4`: 100% × 81,4% | **100,0% × 81,4%** ✓ | **100,0% × 81,4%** | **Bate exatamente** |
| Pesos ±20% → máx. 10,6%, mediana ~4% | — | **máx. 10,6%, mediana 4,55%** | **Bate exatamente** |
| Agregado −0,654 → **+0,161** | **−0,654 → +0,161** ✓ | **+0,196** | Reproduz — **mas a estatística é inválida (§5.2)** |

**Todos os números da etapa 05 reproduzem exatamente no commit da etapa 05.** As divergências contra
hoje são inteiramente atribuíveis à temperagem dos pesos de retenção da etapa 06, e cada valor novo
confere com o que aquela etapa documentou. Nenhum número foi inventado ou copiado errado.

### 5.2 **Achado: a estatística agregada é inválida e inverte a conclusão**

O agregado empilha as 48 observações (8 instâncias × 6 estratégias) num único Spearman. Isso
**ranqueia valores de fitness entre instâncias diferentes** — e o próprio código documenta que isso
não é permitido:

> `benchmarks/…/metric/PlanMetrics.java`: *"Comparable across planners on the same instance;
> **not** comparable across instances, because the fitness has no normalised unit (auditoria §3.1)."*

A fitness de `I4` (≈0,90) fica acima da de `I8` (≈0,55) porque são problemas diferentes, não porque
um plano seja melhor. O ranqueamento conjunto mede sobretudo *qual instância é fácil*.

Recalculando com a estatística correta — **média das correlações por instância**, onde a comparação é
legítima — sobre os mesmos dados, dos mesmos commits:

| Estado | Agregado empilhado (citado nos relatórios) | Média simples (ad hoc, aqui) | **Fisher z (canônico, etapa 07)** |
|---|---|---|---|
| Etapa 03 — fitness original | −0,654 | −0,910 | **−0,909** [IC95% −0,971; −0,734] |
| Etapa 05 — após correções | **+0,161** | −0,358 | **−0,460** [IC95% −0,793; +0,085] |
| Etapa 06 — após temperagem | +0,196 | −0,015 | **−0,106** [IC95% −0,597; +0,443] |

*(Coluna Fisher z acrescentada em 2026-08-31. A média simples foi o substituto ad hoc desta
verificação; o método sancionado é o da coluna à direita.)*

As duas colunas contam histórias opostas para a etapa 05. A frase de manchete de
`05-fitness-function.md` §6.4 — *"**O sinal agregado inverteu**"* — é verdadeira apenas da coluna
inválida. Pela coluna correta, a etapa 05 **reduziu** a anti-correlação de −0,909 para −0,460, mas
não a inverteu nem chegou perto de zero.

**A conclusão de fundo sobrevive, e sai reforçada:** a progressão −0,909 → −0,460 → **−0,106** é um
resultado melhor e mais honesto que o alegado, porque atribui à etapa 06 o mérito que a estatística
empilhada escondia. Mas a frase, como escrita, não se sustenta em auditoria — e o número `+0,161`,
que a sustentava, **foi silenciosamente removido** de `05-fitness-function.md` na reescrita do §6.4
na etapa 06, sem que a frase que dele dependia fosse revista. O valor `−0,654` continuava vivo em
`03-validacao.md` §5, com o mesmo defeito de construção.

### 5.3 **Achado: os números da etapa 03 não são reprodutíveis**

Seis execuções do harness no commit `07c99da`, sem alterar nada:

| Execução | `I3` | `I4` | `I7` | `I8` | Agregado |
|---|---|---|---|---|---|
| 1 | −0,941 | −0,941 | **−0,878** | −0,880 | — |
| 2 | −0,941 | −0,941 | −0,833 | −0,880 | — |
| 3 | −0,941 | −0,941 | −0,833 | **−0,893** | — |
| 4 | **−0,899** | −0,941 | −0,833 | −0,880 | −0,654 |
| 5 | −0,941 | −0,941 | −0,833 | −0,880 | **−0,648** |
| 6 | **−0,899** | −0,941 | −0,833 | −0,880 | −0,654 |

Os valores documentados em `03-validacao.md` §5 são os **modais**, não os únicos: `I3` varia entre
−0,941 e −0,899, `I7` entre −0,833 e −0,878, `I8` entre −0,880 e −0,893. A causa é conhecida e já
está registrada — naquele commit a mutação sorteava de `Math.random()` e `ThreadLocalRandom`, e o
determinismo só foi corrigido na etapa 04 (`04-robustez.md` §1: de 2/8 para 8/8).

Não é erro de transcrição, e a etapa 03 não tinha como fazer melhor. Mas a tabela é apresentada com
três casas decimais e sem faixa, o que sugere uma precisão que a medição não tinha. **O estado atual
é reprodutível**: `RobustnessMain` confirma plano idêntico em **8 de 8** instâncias, e a
reconstrução limpa de hoje reproduziu todos os valores dígito a dígito.

### 5.4 Divergência menor de método

`RegimeCorrelationMain` usa **5** repetições e `BenchmarkMain` usa **7**, então a mesma instância
aparece com `I3` a 76,8% num relatório e 76,6% no outro. É ruído de amostragem, não discordância —
mas os dois números circulam nos documentos sem que a diferença de método seja dita.

---

## 6. Guardrails

| Guardrail | Estado verificado |
|---|---|
| `assert` de soma dos pesos = 1 | **Ativo e funcionando.** `FitnessEvaluator.java:77` chama `assertWeightsSumToOne` no construtor, tolerância `1e-9`. Pesos hoje: `0,50 + 0,30 + 0,20 = 1,00` |
| O `assert` realmente dispara? | **Sim — agora provado.** Novo teste constrói um pipeline com dois objetivos (soma 0,80) e verifica `IllegalStateException` com a mensagem "devem somar 1.0". Antes desta etapa nenhum teste exercitava o caminho de falha |
| "Aderência a prazo" não virou termo de fitness | **Confirmado.** `grep -i "deadline\|prazo\|examDate\|daysUntil\|aderenc"` sobre todo o pacote `ga/` retorna **zero** ocorrências. A aderência vive só em `StudyScheduleGenerator`, que emite `ScheduleStatus` (`SUCCESS_IDEAL_PLAN` / `SUCCESS_WITH_SURPLUS_TIME` / `WARNING_TIME_DEFICIT`) **depois** do AG |
| README corrigido | **Confirmado.** Linhas 46-51 declaram explicitamente que o sequenciamento de Ausubel não é implementado e que a atribuição anterior estava errada |

---

## 7. Lista priorizada de gaps

Ordenada por severidade. **Nada aqui foi corrigido nesta etapa.** Itens já confirmados corretos e
testados na §6 não aparecem.

> **Status consolidado (2026-08-31):** esta lista foi movida para
> [`README.md`](./README.md), que é agora a fonte única de status de G1–G11. As linhas abaixo ficam
> como o enunciado original de cada gap; **o status atual está no índice**, não aqui.

### Severidade 1 — evidência que não sustenta a conclusão que apoia

| # | Gap | Evidência | Por que é o mais grave |
|---|---|---|---|
| **G1** | **A correlação agregada empilha fitness não comparável entre instâncias.** `05` §6.4 (versão da etapa 05) e `03` §5 apoiam a frase "o sinal agregado inverteu" numa estatística que, recalculada corretamente, dá **−0,460** e não **+0,161** | §5.2; `PlanMetrics.java` documenta a incomparabilidade | É a evidência de manchete de que a correção da fitness funcionou. Um auditor que refaça a conta chega ao sinal oposto |
| **G2** | **A premissa "ordem é inexprimível por construção" é falsa sobre o repositório**, e `06-decisao-ausubel.md` §2.4 generaliza dela para "qualquer validador topológico escrito hoje seria vacuo" | §3.2, §4.2; `domain/tactical/TacticalStudyPlan.java` | Rótulo teórico sobre premissa incorreta — a mesma classe de defeito que a revisão apontou no multiplicador de lacuna |
| **G3** | **A estimativa de esforço da opção (i) ignora andaime existente** — encoding temporal, crossover, mutação e reparo já escritos | §3.2; `06-decisao-ausubel.md` §2.1 itens 4-5 e §2.3 | Uma decisão de produto foi tomada sobre um custo estimado a partir de um ponto de partida que não é o real |

### Severidade 2 — comportamento não coberto por teste

| # | Gap | Evidência |
|---|---|---|
| **G4** | **Nenhum teste trava o valor da correlação fitness × retenção.** É a métrica central da revisão e só existe como *main* executado à mão; uma regressão que a inverta passa no `mvnw test` | §5.1; não há asserção sobre `RegimeCorrelationMain` |
| **G5** | **A camada tática morta não tem teste nem plano.** ~400 linhas com `@Component`/`@Service` que o Spring instancia a cada boot e ninguém consome, e três guardas `instanceof` de fitness que dependem dela | §2.2; `00-diagnostico.md` §1.9 |
| **G6** | **`TacticalStudyPlan.getDaysPerSubject()` conta dias distintos, não dias planejados.** Como estende `StudyPlan`, todo objetivo da fitness o aceita e mede outra coisa sem avisar — hoje inofensivo porque nada produz esses planos, e uma armadilha no dia em que algo produzir | `TacticalStudyPlan.java:26-42` |

### Severidade 3 — documentação desatualizada ou imprecisa

| # | Gap | Evidência |
|---|---|---|
| **G7** | **Os números da etapa 03 são apresentados com 3 decimais sem faixa**, medidos quando o AG ainda não era determinístico; `I3`, `I7` e `I8` variam entre execuções | §5.3 |
| **G8** | **O `+0,161` foi removido de `05` §6.4 na reescrita da etapa 06, mas a frase que dependia dele não foi.** O `−0,654` segue em `03` §5 com o mesmo defeito | §5.2 |
| **G9** | **`01-auditoria-fitness.md` §3.3 afirma "um mapa não tem ordem, inexpressável por construção" sem qualificar** que fala do cromossomo macro. É a origem da cadeia que produziu G2 | §3.2 |
| **G10** | **5 e 7 repetições circulam sem que a diferença seja declarada** | §5.4 |

### Severidade 4 — cosmético

| # | Gap | Evidência |
|---|---|---|
| **G11** | `CriticalFirstStrategy` e `RandomizedInterleavedStrategy` existem, estão corretas e nunca são usadas. Sem anotação Spring, então nem bean viram | §2.1 |

---

## 8. O que **não** é gap

Registrado para que uma próxima rodada não reabra o que esta fechou com evidência:

- **"Ausubel não implementado" está correto.** Verificado por varredura, leitura, histórico e teste
  executado com diferença de fitness exatamente zero (§4.1).
- **A decisão de não implementar Ausubel continua de pé.** O bloqueio editorial (DAG por edital) e a
  ausência do dado na API são independentes do encoding e não foram afetados (§3.3).
- **Os números da etapa 05 estavam certos quando escritos.** Reproduzidos dígito a dígito no commit
  original (§5.1).
- **`I3` 96% × 76,6% e `I4` 100% × 81,4%, e a estabilidade de ±20% → 10,6%**, batem exatamente, nos
  dois estados do código (§5.1).
- **Os dois guardrails estão ativos**, e o de soma dos pesos agora tem teste do caminho de falha
  (§6).

## 9. Como reproduzir

```bash
./mvnw -o clean test                      # 55 testes, 0 falhas
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:target/test-classes:$(cat cp.txt)"
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.RegimeCorrelationMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.WeightSensitivityMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.RobustnessMain

# Reconstrução de um estado anterior (§5.1, §5.3):
git worktree add /tmp/e05 0083188 && cd /tmp/e05 && ./mvnw -o test-compile -DskipTests
```

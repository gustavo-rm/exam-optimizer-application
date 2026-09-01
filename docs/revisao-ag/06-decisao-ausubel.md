# Decisão: Ausubel / Sequenciamento por Pré-requisitos

**Etapa:** decisão de arquitetura. Precede qualquer código.
**Questão:** implementar sequenciamento por pré-requisitos nesta versão do produto, ou não implementar
e corrigir a documentação que hoje afirma incorretamente que ele existe?
**Anteriores:** [`01`](./01-auditoria-fitness.md) §3.3, §4 · [`02`](./02-formulacao.md) §5 ·
[`05`](./05-fitness-function.md) §4.1

---

## 1. O problema

A aprendizagem significativa de Ausubel sustenta que material novo precisa se ancorar em
subsunçores já presentes na estrutura cognitiva; sem eles, o resultado é aprendizagem mecânica.
Operacionalmente, isso implica uma restrição de **ordem**: o pré-requisito precede o dependente.

O motor não implementa isso. O cromossomo macro é `Map<Subject, Integer>` — uma contagem de dias por
disciplina, **sem posição no calendário e sem ordem**. Não há como um mapa expressar "A antes de B".

> **Precisão adicionada em 2026-08-31 (etapa 07).** Essa frase vale para o cromossomo **macro**, que
> é o que produção executa — e não para o repositório inteiro. Existe um segundo cromossomo,
> `TacticalStudyPlan : Map<TimeSlot, TacticalStudyBlock>`, com posição temporal completa e já aceito
> pelo `FitnessEvaluator`. Ele é andaime morto (não é produzido por nenhum AG), mas sua existência
> muda o que §2.3 e §2.4 podiam afirmar; ambas foram reescritas.

Além disso, e este ponto é novo desta etapa: **a API não coleta a informação de pré-requisitos.**
`SubjectDto` tem `name`, `questionCount` e `cognitiveLoad`. `Subject` tem os mesmos três campos.
Não existe relação de dependência em lugar nenhum do modelo de entrada.

O `README.md` afirma que Ausubel é tratado pelo multiplicador de lacuna de conhecimento. Não é: essa
é uma regra monotônica de triagem ("mais lacuna, mais prioridade"), sem noção de ancoragem nem de
ordem. A correção dessa afirmação é obrigatória e independe da decisão abaixo (§5).

---

## 2. Opção (i) — Redesenhar o encoding para expressar precedência

### 2.1 O que seria preciso construir

| # | Camada | Trabalho | Observação |
|---|---|---|---|
| 1 | **Contrato de API** | `SubjectDto` ganha `prerequisites: List<String>`; validação de referências existentes e ausência de ciclos | Mudança de contrato: versão nova do endpoint ou quebra |
| 2 | **Conteúdo** | Alguém precisa **autorar o DAG de pré-requisitos de cada edital** e mantê-lo quando o edital muda | **Não é engenharia.** É trabalho editorial por concurso, recorrente |
| 3 | **Domínio** | `PrerequisiteGraph` com ordenação topológica e detecção de ciclo | Pequeno |
| 4 | **Encoding** | Trocar `Map<Subject,Integer>` por estrutura com ordem | Ver §2.2 — **mas `TacticalStudyPlan` já é uma (§2.3, corrigido)** |
| 5 | **Operadores** | `WeightedAverageCrossover`, `RepairingCrossover`, `CreepMutation`, `TransferMutation` e `StudyPlanFactory` precisam preservar **duas** invariantes: orçamento e validade topológica | ~~O maior item~~ — **existem operadores táticos a adaptar (§2.3)** |
| 6 | **Reparo** | Ordenação topológica + reempacotamento das datas de início quando um operador quebra a precedência | ~~Novo componente~~ — **`ChromosomeRepairer` já define o contrato (§2.3)** |
| 10 | **Motor de AG** | Nenhum loop evolutivo consome `TacticalCrossoverStrategy`/`TacticalMutationStrategy` | **Acrescentado em 2026-08-31: é hoje o maior item de engenharia** |
| 7 | **Fitness** | Termo de violação de precedência | Pequeno, uma vez que 4–6 existam |
| 8 | **Agendador** | `StudyScheduleGenerator` consome `Map<Subject,Integer>`; passaria a ter de respeitar a ordem | Médio |
| 9 | **Benchmarks** | As 6 estratégias do harness precisam produzir o novo cromossomo; as 8 instâncias precisam de DAGs | Médio |

### 2.2 Dois encodings possíveis

**(a) Sequência explícita de alocações** — cromossomo é uma lista ordenada de `(disciplina, dia)`.
Expressivo, mas o espaço de busca explode (permutações) e todo operador clássico de AG precisa ser
substituído por operadores de permutação com reparo. É o caminho caro.

**(b) Vetor de datas de início** — `Map<Subject, (diaInicio, dias)>`. A ordem emerge das datas, e a
precedência vira a restrição linear `inicio_B ≥ inicio_A + dias_A` para cada aresta `A → B`.
Dobra a dimensão do cromossomo em vez de trocar sua natureza, e os operadores atuais podem ser
adaptados em vez de reescritos. **Se a opção (i) for escolhida um dia, é por aqui que se começa.**

### 2.3 Estimativa de esforço — **corrigida em 2026-08-31**

> **Correção (etapa 07).** A versão original desta seção estimava a opção (i) como *"da mesma ordem
> de grandeza ou maior"* que as etapas 00–05 somadas, e a tabela §2.1 listava "trocar
> `Map<Subject,Integer>` por estrutura com ordem" como se fosse partir do zero. **Estava errado.**
> A verificação da etapa 06 encontrou, no próprio repositório, um encoding com ordem já escrito —
> junto com crossover, mutação e um operador de reparo sobre ele. A estimativa abaixo foi refeita.

O repositório **já contém** o andaime que a §2.1 tratava como trabalho a fazer:

| Peça | Onde está | Estado |
|---|---|---|
| Cromossomo com posição temporal | `domain/tactical/TacticalStudyPlan` — `Map<TimeSlot, TacticalStudyBlock>`, `TimeSlot` com `LocalDateTime` reais | Escrito; **estende `StudyPlan`** |
| Aceitação pela fitness | `FitnessEvaluator.evaluate(StudyPlan, …)` | **Já aceita**, sem mudança de assinatura — provado por teste |
| Crossover | `ga/tactical/strategy/crossover/DayBoundaryCrossover` | Escrito; nunca instanciado |
| Mutação | `ga/tactical/strategy/mutation/MethodologyMutation` | Escrito; nunca instanciado |
| Operador de reparo | `ga/tactical/repair/SpacedRepetitionRepairer` | `@Component`; nunca injetado |
| Empacotador em janelas | `service/scheduler/tactical/HybridHeuristicScheduler` | `@Service`; nunca injetado |

Nada disso está cabeado — é andaime morto, e a etapa 00 §1.9 já o registrava como tal. Mas andaime
morto **não é a mesma coisa que folha em branco**, e a estimativa original tratava os dois como
equivalentes.

**Estimativa revisada**, supondo que o dado de pré-requisitos exista (ver §2.5):

| Frente | Antes estimado | Revisado | Por quê |
|---|---|---|---|
| Encoding com ordem | Grande | **Já existe** | `TacticalStudyPlan` |
| Operadores (crossover/mutação) | "O maior item" | **Adaptar, não escrever** | Existem para o encoding temporal; precisam preservar a invariante topológica além da de orçamento |
| Reparo | Componente novo | **Adaptar** | `ChromosomeRepairer` já define o contrato; falta um reparo topológico ao lado do de revisão |
| Motor de AG tático | Não mencionado | **Escrever** | `GeneticAlgorithm` só conhece `CrossoverStrategy`/`MutationStrategy` sobre `Individual`; nada consome as interfaces táticas |
| Grafo + ordenação topológica | Pequeno | Pequeno | Inalterado |
| Termo de precedência na fitness | Pequeno | Pequeno | Inalterado; **não seria vacuo** sobre este encoding (§2.4) |
| Contrato de API + validação de ciclos | Médio | Médio | Inalterado |
| Agendador e benchmarks | Médio | Médio | Inalterado |

O item que a estimativa original **não** listava e que é hoje o maior de engenharia: **não existe
motor de AG que evolua o cromossomo tático.** As peças existem, o loop que as usa não.

Comparando de novo com o custo desta revisão (~1.400 linhas entre produção e benchmark, 7
documentos): a opção (i) continua sendo **trabalho substancial, mas menor do que se estimou** —
mais próximo de "metade a uma vez" do que de "uma vez ou mais". A diferença não muda a decisão; muda
o que é honesto dizer sobre ela.

### 2.4 A "versão mínima" pedida — **corrigida em 2026-08-31**

> **Correção (etapa 07).** A versão original desta seção afirmava que *"qualquer validador topológico
> escrito hoje passaria para todo indivíduo, por construção"*. **A generalização é falsa.** É
> verdadeira do cromossomo macro e falsa do repositório.

O enunciado da etapa 06 oferecia um meio-termo: implementar uma **checagem de viabilidade topológica
na fitness**, penalizando indivíduos que agendem um tópico antes de seus pré-requisitos.

**Sobre `Map<Subject, Integer>` — o cromossomo que produção executa — essa checagem é de fato
vacuosa.** Não existe "antes" para verificar. Demonstrado por teste: `new StudyPlan(Map.of(A,10,B,6))`
e `new StudyPlan(Map.of(B,6,A,10))` são o **mesmo valor** por `equals`. Não há violação a detectar
porque não há precedência a expressar.

**Sobre `TacticalStudyPlan`, não é.** Os `TimeSlot` carregam data e hora reais, a ordem é recuperável,
e o `FitnessEvaluator` de produção já avalia esses planos sem qualquer alteração. O teste
`PrerequisiteSequencingDiagnosticTest` verifica as duas metades: recupera corretamente qual
disciplina vem primeiro, e mede que dois planos que diferem **apenas na ordem** recebem fitness
idêntica — `0,2642479576` nos dois, diferença exatamente zero. Ou seja: **a informação está lá e
nada a lê.** Um validador de precedência escrito contra essa assinatura teria o que verificar.

A razão para não escrevê-lo agora, portanto, **não é que seria vacuo**. É outra, e mais simples:

1. **Não há dado.** `SubjectDto` e `Subject` têm três campos; nenhum é uma relação de dependência.
   Um validador de precedência sem grafo de precedência não tem o que validar — seria inerte por
   falta de entrada, e não por falta de expressividade do encoding.
2. **Nada produz um `TacticalStudyPlan`.** O encoding onde a checagem funcionaria não é gerado por
   nenhum caminho de execução, então o validador nunca rodaria em produção.

O ponto de fundo da versão original continua valendo, e vale repetir porque é o que importa: a etapa
`01` §0 encontrou **quatro de cinco** componentes da fitness incapazes de alterar qualquer decisão.
Acrescentar um quinto — um termo de Ausubel que nunca pode falhar, agora por falta de dado em vez de
falta de ordem — daria ao produto licença para afirmar que trata pré-requisitos, com um componente
que a própria auditoria classificaria como inerte na etapa seguinte. **A conclusão se mantém; o
argumento que a sustentava foi trocado por um verdadeiro.**

### 2.5 O bloqueio real

Com §2.3 e §2.4 corrigidas, o que efetivamente impede a opção (i) hoje fica isolado, e **não é
técnico**:

1. **Não existe DAG de pré-requisitos autorado por edital.** Alguém precisa decidir, para cada
   concurso, que "Controle de Constitucionalidade" pressupõe "Teoria da Constituição" — e manter
   isso quando o edital muda. É **trabalho editorial e curatorial recorrente**, não engenharia.
   Nenhuma linha de código produz esse dado.
2. **A API não coleta o dado.** `SubjectDto` teria de ganhar `prerequisites`, com validação de
   referências e de ausência de ciclos — mudança de contrato, mas pequena perto do item 1.

**Enquanto o item 1 não existir, o encoding pronto não implementa Ausubel.** É por isso que a
recomendação da §4 não muda.

---

## 3. Opção (ii) — Não implementar nesta versão e corrigir a documentação

Manter o encoding atual, declarar explicitamente que sequenciamento por pré-requisitos não é uma
capacidade do produto, e corrigir toda afirmação em contrário no repositório.

**Custo:** algumas horas de documentação.
**Risco:** nenhum risco técnico. O risco é comercial — o produto passa a anunciar dois fundamentos
teóricos em vez de três.

---

## 4. Recomendação: **opção (ii)**

Quatro razões, em ordem de peso.

**1. A versão mínima seria inerte por falta de dado, e a versão completa não cabe nesta rodada.**
*(Razão reescrita em 2026-08-31: a formulação original dizia que a versão mínima seria vacuosa por
limitação de encoding — ver §2.4 corrigida.)* Um validador de precedência **teria** o que verificar
sobre `TacticalStudyPlan`, mas não há grafo de pré-requisitos para alimentá-lo e nada em produção
gera esse cromossomo, então ele seria o quinto componente inerte da fitness. A versão completa
continua sendo trabalho substancial (§2.3 revisada), ainda que menor do que se estimou.

**2. Falta o dado, não só o código.** A API não coleta pré-requisitos e ninguém autorou DAGs de
edital. Mesmo com o encoding pronto, o produto não teria o que executar. Este é um bloqueio de
conteúdo e operação, e é o motivo pelo qual a decisão não pertence só à engenharia.

**3. O encoding é uma decisão já registrada como separada.** A etapa `02` §5 concluiu que a troca de
encoding deve ser tratada como decisão explícita e isolada, porque ela também destrava as limitações
L2, L3 e L6 de `05` §8 — retenção fiel, carga por episódio e aderência a prazo. Fazê-la agora,
enfiada dentro de "implementar Ausubel", subverteria essa decisão e resolveria mal quatro problemas
de uma vez.

**4. Afirmar menos e provar é melhor do que afirmar mais e não sustentar.** O produto vai a uma
reunião com o Colégio Donaduzzi e a um pitch do Centelha. Dois fundamentos implementados, medidos e
documentados — com as aproximações nomeadas como aproximações — sustentam escrutínio. Três
fundamentos em que o terceiro não resiste à primeira pergunta técnica ("como vocês representam a
ordem?") custam a credibilidade dos outros dois junto.

### 4.1 O que o produto pode afirmar sobre Ausubel a partir de agora

**Nada.** Ausubel sai do material técnico. O que existe — o multiplicador de lacuna — é descrito pelo
que faz: *a lacuna de conhecimento declarada pelo aluno personaliza a importância de cada disciplina;
quanto maior a lacuna declarada, maior a prioridade.* É uma regra de triagem monotônica, defensável
por si, sem atribuição teórica.

### 4.2 Gatilhos para reabrir a decisão

Registrados para que a reabertura seja por dado, não por intuição:

1. **O dado passar a existir** — quando houver DAG de pré-requisitos autorado para ao menos um edital
   real, o bloqueio de conteúdo cai e a conta muda.
2. **A decisão de encoding ser tomada por outro motivo** — se L2/L3/L6 justificarem o encoding
   temporal, Ausubel passa a custar apenas os itens 7 e 10 da tabela de §2.1, e aí vale a pena.
   O andaime tático (§2.3) é o ponto de partida natural dessa conversa.
3. **Evidência de que a ordem importa no macro** — hoje não há medição de que planos que respeitam
   precedência produzam melhor desempenho do que planos que a ignoram. Sem isso, a implementação
   seria fé, não engenharia.

---

## 5. Ação aplicada nesta etapa

Conforme a opção (ii) exige, a correção documental foi feita agora, não deixada como pendência:

| Local | Antes | Depois |
|---|---|---|
| `README.md` §Theoretical Foundation | "Meaningful Learning Theory (David Ausubel): (…) subsumers (…) knowledgeGaps" | Ausubel removido. A regra é descrita pelo que faz, sem atribuição teórica |
| `README.md` — Ebbinghaus e Sweller | Atribuídos a `ReviewFocusedStrategy` / `CognitiveLoadBalancingStrategy` (heurísticas pós-AG) | Corrigidos: agora são termos da fitness (`O₃`, `O₄`), com as aproximações nomeadas |
| `README.md` — frase de abertura | "direct computational translation of three established educational theories" | Reescrita: dois fundamentos, com o grau de fidelidade declarado |
| `05-fitness-function.md` §4.1 | já registrava a ausência | Passa a apontar para esta decisão |

Os documentos `00` a `04` **não** foram alterados: eles descrevem a atribuição incorreta como achado
de auditoria, o que continua correto e é o registro histórico de como o erro foi encontrado.

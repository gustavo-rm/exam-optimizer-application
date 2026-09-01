# Etapa 01b — Correção das lacunas de teste

**Escopo:** ataque à lista priorizada de [`01-diagnostico-testes.md`](./01-diagnostico-testes.md),
começando pelos itens de maior risco. Segurança, estrutura, escrita, performance e escalonamento
continuam para as etapas seguintes.

| | |
|---|---|
| **Data** | 2026-09-01 |
| **Base** | `f187891` (etapa 01), sobre `d1eafcb` (etapa 00) |
| **Branch** | `release/v4.0` |

### Vocabulário usado neste documento

- **Cobertura de instruções / de ramos** — fração das instruções de *bytecode* executadas pela
  suíte, e fração dos desvios condicionais percorridos **nas duas direções**. Ramos é sempre a
  métrica mais exigente.
- **Teste frágil ou instável (*flaky*)** — passa ou falha para o mesmo código, sem que nada tenha
  mudado, por depender de tempo, ordem, aleatoriedade ou estado externo.
- ***Gate*** — verificação que **bloqueia** a integração de uma mudança quando falha; o oposto de
  informativa, que apenas relata.
- **Teste de retrato (*snapshot*)** — compara a saída atual com um arquivo commitado. Não julga se a
  mudança é boa; torna a mudança **visível** no *diff*.
- **Catraca (*ratchet*)** — piso que só se move numa direção. Aqui: a cobertura pode subir sozinha,
  mas só pode cair se alguém baixar o piso de propósito, no mesmo commit e à vista do revisor.
- ***Dublê de teste*** — objeto que substitui uma dependência real para que o teste controle o que
  ela devolve.

---

## Resumo do que mudou

| | Antes (etapa 01) | Depois | |
|---|---:|---:|---|
| Testes | 73 | **117** | +44 |
| Cobertura de instruções | 67,1% | **79,2%** | +12,1 pontos |
| Cobertura de linhas | 68,4% | **80,2%** | +11,8 pontos |
| Cobertura de ramos | 52,9% | **56,8%** | +3,9 pontos |
| Fluxo de sucesso coberto | **não** | **sim** | |
| Tratadores de erro executados | 1 de 9 | **9 de 9** | |
| CI | inexistente | **workflow com gate** | falta marcar como obrigatório |
| Contrato HTTP versionado | não | **sim** | retrato OpenAPI commitado |

**Dois defeitos de produção foram encontrados durante o trabalho** — o endpoint de documentação
devolvia 500 e o projeto não gerava jar executável. Ambos estão descritos na seção "Defeitos
encontrados", com a justificativa das três alterações que precisei fazer em código de produção.

---

## 1. Testes frágeis: o que foi corrigido e o que foi isolado

O diagnóstico não observou **nenhum** teste instável em 10 execuções. O que ele registrou foram três
**mecanismos** capazes de produzir instabilidade (T6, T7, T8). Cada um recebeu tratamento diferente,
conforme a causa raiz fosse trivial de eliminar ou não.

### 1.1 T7 — estado estático global do `RandomProvider`: isolado por construção

**Causa raiz:** `RandomProvider` guarda a fonte de aleatoriedade do algoritmo genético num campo
`static` mutável. Testes gravam nele para fixar sementes, e o `ProductionGeneticAlgorithm` dos
benchmarks também. Uma semente esquecida vaza para o teste seguinte, que passa a rodar com
aleatoriedade fixa sem saber — e o resultado passa a depender da ordem de execução.

**O que existia:** contenção por disciplina. `GaEdgeCasesTest` tinha um `@AfterEach` próprio
restaurando `SecureRandom`, com um comentário explicando o motivo. Funciona enquanto todo teste
futuro lembrar de fazer o mesmo.

**O que foi feito:** a disciplina virou garantia estrutural. Uma extensão do JUnit 5,
`support/RandomProviderIsolation`, guarda a fonte instalada antes de cada teste e a restaura depois.
Ela é registrada **automaticamente** para toda a suíte, via
`src/test/resources/junit-platform.properties` (`junit.jupiter.extensions.autodetection.enabled=true`)
e um arquivo de serviço em `META-INF/services` — inclusive para os testes que vivem em
`benchmarks/java`, sem que nenhuma classe precise se anotar.

**O que não foi resolvido:** isso isola testes executados **em sequência**. Não torna o
`RandomProvider` seguro para execução paralela. Ver pendência **P1**.

### 1.2 T8 — dependência de data: corrigida

Oito testes, em quatro arquivos, usavam `LocalDate.now()` como data da prova. São testes que podem
mudar de comportamento com a passagem do tempo sem que nenhuma linha mude. Os benchmarks já haviam
resolvido isso (`InstanceLibrary` usa âncora fixa, com comentário explícito de *"Never
`LocalDate.now()`"*); a suíte de `src/test` não tinha seguido.

Todos passaram a usar âncoras fixas:

| Arquivo | Antes | Depois |
|---|---|---|
| `CognitiveLoadCalculatorTest` | `LocalDate.now()` | `LocalDate.of(2026, 9, 1)` |
| `ImportanceCalculatorTest` | `LocalDate.now()` | `LocalDate.of(2026, 9, 1)` |
| `BaselineCalculatorTest` | `LocalDate.now()` | `LocalDate.of(2026, 9, 1)` |
| `ExamTest` (5 ocorrências) | `LocalDate.now().plusDays(30)` | `LocalDate.of(2026, 10, 1)` |

**Uma exceção deliberada.** Os testes novos de API (`GenerateStudyPlanHappyPathTest`,
`TimeDeficitScenarioTest`) **continuam** usando `LocalDate.now().plusDays(N)`. O motivo é diferente:
`DynamicStudyPlannerService` chama `LocalDate.now()` **dentro do código de produção** para marcar o
início do cronograma. Uma âncora fixa no passado produziria cronograma vazio e o teste deixaria de
exercitar o caminho que pretende cobrir. Enquanto a produção depender do relógio, o teste do fluxo
completo depende também. Ver pendência **P3**.

### 1.3 T8 — asserção de tempo de parede: marcada como conhecida-instável, não apagada

`GaEdgeCasesTest.scalesToManySubjects` compara tempo de parede contra um limite fixo de 10 segundos.
Nunca falhou em 17 execuções (10 no diagnóstico, 7 nesta etapa) e a folga medida é de cerca de 66×.
Mesmo assim, é uma asserção sensível à máquina: numa máquina de CI compartilhada, uma pausa longa do
coletor de lixo pode estourá-la sem que nada no código tenha mudado.

**Não foi corrigida**, porque a correção honesta — trocar tempo por contagem de operações —
exigiria instrumentar o motor do algoritmo genético, ou seja, mudar código de produção por
conveniência de teste. Foi feito o seguinte, conforme o pedido de "não apagar silenciosamente":

- o teste continua **ativo**;
- recebeu a etiqueta `@Tag("lento-e-sensivel-a-maquina")`, o que permite excluí-lo de uma execução
  com `-Dgroups='!lento-e-sensivel-a-maquina'` caso passe a falhar de forma intermitente, sem
  precisar editar código;
- o Javadoc registra a causa suspeita, a folga medida, o número de execuções limpas e o motivo de
  não ter sido corrigido agora.

Ver pendência **P2**.

### 1.4 T6 — a repetição da suíte não é evidência para a parte determinística

Este item não é corrigível: é uma propriedade do desenho dos benchmarks (`BASE_SEED + r` fixa as
sementes, de propósito, para que os números publicados sejam reproduzíveis). O que se pode fazer é
não citar o resultado de forma enganosa, e é o que a seção 6 deste documento faz.

---

## 2. Testes para os caminhos críticos sem cobertura

### 2.1 T1 — o fluxo de sucesso (o item de maior risco da lista)

`api/GenerateStudyPlanHappyPathTest` — **5 testes**. É o primeiro teste do repositório a receber uma
resposta `200`.

Exercita a cadeia inteira montada pelo Spring, e não uma reprodução dela feita à mão: mapeamento de
DTO para domínio, orquestração das duas fases, execução no pool `optimizerTaskExecutor` via
`@Async`, redespacho assíncrono do Servlet e serialização do `PlannerResponseDto`.

O detalhe que faz o teste valer alguma coisa: o controller devolve `CompletableFuture`, então o
MockMvc precisa de **dois passos** — a requisição inicial, que apenas inicia o processamento, e o
`asyncDispatch`, que entrega o resultado. Sem o segundo, o teste inspecionaria um corpo vazio e
passaria sem exercitar nada.

O que é afirmado, além de "respondeu 200":

- toda disciplina do edital aparece no plano entregue;
- a soma dos dias por disciplina é **exatamente** o `totalStudyDays` pedido;
- a fitness reportada está em `[0,1]`, e o número de gerações é o que foi pedido;
- o cronograma não vem vazio, as chaves são datas ISO-8601, nenhum bloco cita disciplina fora do
  edital e nenhum bloco tem zero horas.

**Resultado medido:**

| Método | Antes | Depois |
|---|---:|---:|
| `OptimizerController.generateFullStudyPlan` | 0 / 35 | **35 / 35** |
| `DynamicStudyPlannerService.generateFullStudyPlan` | 0 / 41 | **41 / 41** |
| `StudyScheduleGenerator.generate` | parcial | **41 / 41** |

### 2.2 T1b — o cenário de déficit de tempo

`api/TimeDeficitScenarioTest` — **3 testes**. O aluno que **não** tem tempo suficiente até a prova.

É o caso realista: quem procura um otimizador de estudos normalmente está com o prazo apertado. O
código trata isso num ramo separado que reduz o plano proporcionalmente e devolve
`WARNING_TIME_DEFICIT`. Esse ramo não era percorrido por nada. A diferença para o aluno é grande — no
caminho de folga ele recebe o plano ideal; aqui recebe um plano encolhido, e o campo `status` é a
única coisa que o avisa.

O teste também trava a separação entre as fases: a redução acontece na fase tática, e o plano macro
continua somando o orçamento inteiro. Uma mudança acidental que embaralhasse isso passaria por
qualquer asserção mais frouxa.

### 2.3 T2 — os oito mapeadores

`api/mapper/MapperRoundTripTest` — **15 testes**. O pacote tinha **0% de cobertura de ramos**.

Onde o mapeador declara as duas direções, o teste é de **ida e volta**: converter para DTO e de volta
precisa devolver um objeto igual ao original. Isso pega a classe inteira de erro em que dois campos
do mesmo tipo são trocados de lugar — `questionCount` e `cognitiveLoad`, por exemplo —, que uma
asserção campo a campo escrita a partir do próprio mapeador não pega, porque repetiria o mesmo
engano.

**Resultado: `api.mapper` foi de 12,7% / 0,0% para 96,8% de instruções e 94,4% de ramos.**

Dois desses testes documentam **defeitos latentes** encontrados no caminho, e estão marcados como
`COMPORTAMENTO ATUAL` para que ninguém os leia como aprovação do comportamento — ver pendência
**P5**.

### 2.4 T3 e T4 — o contrato de erro

Três arquivos, porque os códigos exigem montagens diferentes:

| Arquivo | Testes | Cobre |
|---|---:|---|
| `api/ApiErrorContractTest` | 7 | `400` com corpo RFC 7807 completo; `429` de ponta a ponta; isolamento do balde por IP; **o ramo `X-Forwarded-For`**, que nenhum teste percorria |
| `api/ApiFailureContractTest` | 5 | `408` e `500`, com o caso de uso substituído por dublê; e que o `500` **não vaza** mensagem interna, nome de classe nem pilha |
| `api/exception/GlobalExceptionHandlerTest` | 7 | `422`, `401`, `403` e `ConstraintViolationException` — os que **não são alcançáveis pela porta HTTP** |

**Resultado: os 9 tratadores do `GlobalExceptionHandler` estão em 100% de instruções.** Antes, 8 dos
9 nunca executavam.

Sobre o recorte do `408`: provocá-lo de verdade custaria 30 segundos de suíte por teste, e reduzir
esse tempo exigiria tornar o prazo configurável — mudança em produção. O dublê exercita o que
importa aqui (o transporte da falha pelo redespacho assíncrono e a tradução em RFC 7807), mas **não**
verifica que o `orTimeout` dispara no prazo certo. Ver pendência **P4**.

Sobre os três inalcançáveis: testá-los diretamente é o recorte honesto — garante que o formato está
certo caso o caminho passe a existir, sem fingir que existe hoje. O motivo de cada um estar fora de
alcance está no Javadoc da classe, e o fato de existirem tratadores para situações impossíveis é a
pendência **P6**.

---

## 3. Testes "não lança exceção" reescritos

O diagnóstico encontrou três. Dois foram reescritos; o terceiro foi deliberadamente mantido.

### 3.1 `tinyGaConfigurationStillRuns` → `tinyGaConfigurationProducesAValidResult`

**Antes:** `assertThatCode(() -> optimizer().optimize(...)).doesNotThrowAnyException();` — rodava o
AG na configuração mínima e não afirmava nada. Um algoritmo que devolvesse plano vazio, estourasse o
orçamento ou reportasse fitness fora de `[0,1]` passaria.

**Depois:** afirma que as três disciplinas aparecem, que nenhuma tem dias negativos, que o orçamento
é respeitado **exatamente**, que a fitness está em `[0,1]` e que o número de gerações reportado é o
pedido. A configuração mínima (1 geração, população 2) é onde erros de contorno — população que nunca
evolui, elite não inicializada — apareceriam primeiro, e agora eles são detectáveis.

### 3.2 `productionPipelineSatisfiesTheAssertion` → `...SatisfiesTheWeightSumAssertion`

**Antes:** `assertThat(productionPipeline()).isNotNull();` — em Java isso não pode falhar sem que uma
exceção tenha sido lançada antes. O teste funcionava por efeito colateral e a asserção não dizia o
que verificava.

**Depois:** declara as duas coisas separadamente — que a composição passa pelo assert de soma dos
pesos (`assertThatCode(...).doesNotThrowAnyException()`), e **qual** é a composição que passa
(pesos `0,50 / 0,30 / 0,20`, nesta ordem). Acrescentar ou remover um objetivo sem rebalançar falha
aqui, com mensagem legível.

### 3.3 `contextLoads()` — mantido e fortalecido

O diagnóstico observou que o teste vazio **não** era inútil: como `FitnessEvaluator` é um
`@Component` cujo construtor valida a soma dos pesos, subir o contexto já falharia se a composição de
beans ficasse desbalanceada. O problema é que a proteção era **acidental** — invisível para quem
lesse o teste, e perdida no dia em que alguém apagasse "o teste vazio".

Agora `DynamicStudyPlannerApplicationTests` tem 3 testes que tornam isso explícito: nomeia os beans
do caminho de produção, e — fechando a lacuna descrita em `01-diagnostico-testes.md` §2.4 — verifica
que **os objetivos fiados pelo Spring** somam 1,0, em vez de repetir a afirmação por comentário
sobre uma composição remontada à mão.

### 3.4 `HybridHeuristicSchedulerTest` — deliberadamente **não** mexido

O diagnóstico mediu que suas três asserções são frouxas (36 minutos produzidos contra um limite de
102). A tarefa pede para reescrever *"quando o caminho for crítico o suficiente para justificar o
esforço"* — e este não é. `HybridHeuristicScheduler` **não tem nenhum consumidor em produção**
(pendência G5 de `docs/revisao-ag/`, ABERTA). Apertar asserções sobre código que nenhum usuário
alcança consumiria esforço sem reduzir risco, e ainda aumentaria o custo de remover a classe se essa
for a decisão da etapa de estrutura. Fica registrado como pendência **P7**.

---

## 4. O gate de CI

**Antes: não existia.** A consulta à API do GitHub confirmou 0 workflows e 0 execuções em toda a
história do repositório. Os testes só rodavam quando alguém digitava `./mvnw test`.

### 4.1 O que foi criado

**`.github/workflows/ci.yml`** — dispara em `push` e `pull_request` para `main` e `release/**`.
Roda `./mvnw -B verify` em JDK 21 Temurin, com cache do Maven, uma execução por branch
(`concurrency` com `cancel-in-progress`) e limite de 20 minutos. Publica os relatórios do Surefire e
do JaCoCo como artefatos — mesmo quando a etapa falha, porque é o relatório que explica a falha — e
escreve a tabela de cobertura no resumo do job.

**JaCoCo no `pom.xml`** — a etapa 01 registrou que nenhuma ferramenta de cobertura estava
configurada (T18): o número só existia quando alguém o media à mão. Agora `verify` roda o goal
`check` com piso de **0,77 de instruções e 0,55 de ramos**.

Os pisos são **catraca, não meta**: estão logo abaixo do medido neste commit (79,2% e 56,8%), de
modo que a cobertura pode subir livremente e não pode cair sem que alguém baixe o piso de propósito,
no mesmo commit e à vista do revisor.

### 4.2 O gate foi verificado, não apenas escrito

Duas sabotagens temporárias, ambas revertidas:

| Experimento | Resultado |
|---|---|
| Quebrar uma asserção de `StudyPlanTest` | `Tests run: 117, Failures: 1` → **BUILD FAILURE** |
| Subir o piso de instruções para 0,95 | `Rule violated ... is 0.79, but expected minimum is 0.95` → **BUILD FAILURE** |

Falha de teste e queda de cobertura reprovam o mesmo job.

### 4.3 O que falta para o gate bloquear de fato

**Este arquivo sozinho não bloqueia merge.** Ele faz a verificação existir e reportar. Para virar
bloqueio, o job precisa ser marcado como *required status check* na proteção da branch — configuração
de repositório, que não pode ser feita por commit. Tentei via API e recebi `403`: a credencial desta
sessão não tem permissão administrativa.

**Passo manual, uma vez, por alguém com acesso de administrador:**

1. Fazer o merge deste trabalho, para que o workflow rode ao menos uma vez e o GitHub passe a
   conhecer o nome do check.
2. `Settings → Branches → Branch protection rules`, na regra de `main` (e criar uma para
   `release/**`, que hoje não tem).
3. Marcar **Require status checks to pass before merging** e selecionar **`Testes e cobertura`**.
4. Marcar **Require branches to be up to date before merging**, para que o check rode contra o
   resultado do merge e não contra uma base antiga.

Enquanto o passo 3 não for feito, o CI é **informativo**: mostra vermelho, mas não impede o merge.
Registrado como pendência **P8** — é a única parte do item 4 da tarefa que não pude concluir por
falta de permissão, não por decisão técnica.

---

## 5. Teste de contrato

### 5.1 Qual contrato — e a correção de premissa

O diagnóstico da etapa 01 §4.1 já havia concluído que **não há contrato entre serviços a proteger**:
o repositório tem um serviço só, sem nenhuma chamada de rede saindo dele (zero `RestTemplate`,
`WebClient`, `Feign`, `Kafka`, `Rabbit`, `JMS`, `DataSource`). Um teste de contrato *entre serviços*
seria trabalho sem objeto.

O contrato que existe e estava desprotegido é o **HTTP público**, consumido por clientes que este
repositório não controla. Estava desprotegido em dois sentidos: não havia especificação versionada —
a descrição da API só existia em tempo de execução, logo uma quebra de compatibilidade não aparecia
em nenhum *diff* — e nenhum teste inspecionava o corpo de uma resposta de sucesso.

### 5.2 O que foi criado

**`src/test/resources/contract/openapi-snapshot.json`** — retrato versionado do documento OpenAPI,
542 linhas, com chaves ordenadas para que a comparação não dependa da ordem em que o springdoc monta
o documento.

**`contract/OpenApiContractTest`** — 4 testes:

- `GET /v3/api-docs` responde `200` (asserção separada de propósito: se a documentação quebrar de
  novo, a mensagem aponta a causa em vez de exibir um *diff* gigante contra um corpo de erro);
- o contrato publicado é idêntico ao retrato — e, na falha, o teste grava o contrato atual em
  `target/openapi-atual.json` e instrui o autor a copiá-lo por cima **no mesmo commit**, para que o
  revisor veja no *diff* qual campo mudou;
- o endpoint e os 13 esquemas que o cliente precisa conhecer estão documentados, e o endpoint aceita
  apenas `POST`;
- **os limites de validação fazem parte do contrato**: `totalStudyDays ≤ 365`,
  `numGenerations ≤ 1000`, `populationSize ≤ 500`, `questionCount ≤ 500`, `cognitiveLoad ≤ 5`. Esses
  tetos existem para proteger CPU e memória; afrouxar um deles é ao mesmo tempo mudança de contrato
  e de superfície de ataque, e agora fica visível.

---

## 6. Verificação de estabilidade

**Sete execuções da suíte completa**: cinco na ordem padrão, conforme pedido, e mais duas com ordem
embaralhada (`-Dsurefire.runOrder=random`), para exercitar o isolamento novo do `RandomProvider` —
que ordem fixa não exercita.

Os relatórios XML do Surefire foram comparados **caso a caso**, não só pelo total.

| Bateria | Execuções | Casos | Resultado |
|---|---:|---:|---|
| Ordem padrão | 5 | 117 | 117 PASS em todas |
| Ordem aleatória | 2 | 117 | 117 PASS em ambas |

**Nenhum caso de teste apresentou resultado inconsistente entre as sete execuções.** A união dos
casos observados é 117 em todas — nenhum teste apareceu ou desapareceu entre execuções. Cada execução
completa levou entre 21 e 25 segundos.

### 6.1 O limite deste resultado — leia antes de citá-lo

Vale a mesma ressalva da etapa 01, e ela **não** foi resolvida: boa parte da suíte é determinística
por construção. `BenchmarkHarness` usa `BASE_SEED + r` com base constante, então
`GeneticAlgorithmVsBaselinesTest` produz resultado bit a bit idêntico em toda execução. Repeti-lo não
é amostragem.

A parte que exercita aleatoriedade real a cada execução são os sete testes de `GaEdgeCasesTest` que
rodam o AG sem fixar semente. Para esses, sete execuções limpas é evidência razoável — não prova.

**Sobre os itens que eram frágeis:**

| Item | Situação |
|---|---|
| **T7** (`RandomProvider`) | Isolado por construção. As duas execuções em ordem aleatória exercitam exatamente o cenário que a extensão protege, e passaram |
| **T8** (data) | Resolvido nos oito testes de unidade. Nos dois testes novos de API a dependência do relógio é deliberada e documentada (P3) |
| **T8** (tempo de parede) | **Não corrigido.** 17 execuções limpas no total, etiquetado e documentado (P2) |

---

## 7. Defeitos de produção encontrados

Escrever os testes revelou dois defeitos que nenhuma suíte pegava — que é, precisamente, o argumento
para tê-los escrito.

### 7.1 `GET /v3/api-docs` devolvia 500 — toda a documentação da API estava fora do ar

Descoberto ao escrever o teste de contrato. A causa:

```
NoSuchMethodError: 'void org.springframework.web.method.ControllerAdviceBean.<init>(java.lang.Object)'
```

**springdoc-openapi 2.5.0 é binariamente incompatível com o Spring Framework 6.2** que vem no Spring
Boot 3.5.5: foi compilado contra uma assinatura que o 6.2 não tem mais. O resultado é que a Swagger
UI e o `/v3/api-docs` — ambos anunciados no `README.md` como a documentação da API — respondiam
`500`. É a consequência concreta do R18 do inventário, que registrava o springdoc como a segunda
maior defasagem de dependência do projeto.

**Correção: `2.5.0` → `2.8.17`** (última da linha 2.x, compatível com Spring Boot 3.5). Verificado:
o endpoint volta a `200` e emite um documento OpenAPI 3.1.0 com os 13 esquemas.

### 7.2 `mvn package` falhava — não havia jar executável

Descoberto ao rodar `verify` para montar o gate:

```
Failed to execute goal spring-boot-maven-plugin:repackage: Unable to find main class
```

A causa estava no ponto de entrada da aplicação:

```java
static void main(String[] args) {          // sem `public`
```

O goal `repackage` procura um `public static void main` em `target/classes` para gravar o
`Main-Class` do jar. Sem o modificador, **o artefato de implantação descrito no `README.md`
(`./mvnw clean package` seguido de `java -jar target/DynamicStudyPlanner-2.0.1.jar`) nunca chegava a
existir.** Confirmei que a falha é anterior a este trabalho, guardando minhas mudanças no `pom.xml` e
rodando `mvn clean package` na versão original: falha igual.

O `main` sem `public` é sintaxe válida a partir do Java 25 (métodos `main` de instância), o que
provavelmente explica o `<source>24</source>` e o `--enable-preview` declarados no
`maven-compiler-plugin` — configuração que, como o inventário registrou em R3, é **inerte**, porque
`maven.compiler.release=21` herdado do parent prevalece.

**Correção: `static void main` → `public static void main`.** O jar passa a ser gerado (37 MB,
executável). E `DynamicStudyPlannerApplicationTests` ganhou um teste que trava isso por reflexão, de
modo que a regressão seja pega pelos testes — que dão retorno em segundos — e não pela etapa de
empacotamento do CI, que roda depois e custa muito mais tempo.

### 7.3 As três alterações em código de produção, e por que as fiz

A etapa 01 foi explicitamente de diagnóstico, sem tocar em código. Esta é de correção, mas o pedido é
sobre **testes**. Fiz três alterações fora de `src/test`, todas pequenas e reversíveis, e listo cada
uma para que a decisão seja sua e não minha por omissão:

| # | Alteração | Justificativa |
|---|---|---|
| 1 | `pom.xml`: springdoc `2.5.0` → `2.8.17` | Sem isso o teste de contrato pedido no item 5 da tarefa não tem objeto: o endpoint que ele deveria travar respondia `500` |
| 2 | `pom.xml`: JaCoCo com `report` e `check` | O gate de CI pedido no item 4 precisa de uma verificação de cobertura para reprovar; e resolve o T18 |
| 3 | `DynamicStudyPlannerApplication`: `main` público | Sem isso o `verify` que o gate roda **nunca** fica verde. Um gate que não pode passar não é gate |

Nenhuma toca em lógica de negócio: são uma versão de dependência, um plugin de build e um
modificador de visibilidade. Se preferir que qualquer uma delas seja tratada na etapa própria
(dependências na de segurança, por exemplo), reverter é um comando — mas a #3 precisa ficar, ou o
gate nasce vermelho.

---

## 8. Cobertura nova

### 8.1 Total

| Métrica | Antes | Depois | Variação |
|---|---:|---:|---:|
| Instruções | 67,1% (4.421/6.593) | **79,2%** (5.219/6.593) | **+12,1 pontos** |
| Linhas | 68,4% (954/1.394) | **80,2%** (1.118/1.394) | **+11,8 pontos** |
| Ramos | 52,9% (256/484) | **56,8%** (275/484) | **+3,9 pontos** |

Como o repositório tem um serviço só, "por serviço" é este número. O detalhamento útil é por módulo.

### 8.2 Por módulo — onde o esforço foi

| Pacote | Instruções antes | Depois | Ramos antes | Depois |
|---|---:|---:|---:|---:|
| `api.mapper` | 12,7% | **96,8%** | **0,0%** | **94,4%** |
| `api.exception` | 22,0% | **99,4%** | 50,0% | 50,0% |
| `api.dto` | 34,5% | **94,5%** | — | — |
| `api.controller` | 67,7% | **93,7%** | 62,5% | 62,5% |
| `config.logging` | 17,6% | **100,0%** | 0,0% | 50,0% |
| `service` | 83,0% | **89,6%** | 83,3% | **87,5%** |
| `domain` | 93,1% | **96,7%** | 72,7% | 72,7% |
| (raiz do pacote) | 37,5% | 37,5% | — | — |

Os módulos não listados não mudaram: o trabalho foi dirigido à borda que fala com o usuário, que era
onde estava o risco.

### 8.3 Por que os ramos subiram só 3,9 pontos

Porque quase todo o ramo descoberto que restou está em **código que nenhum caminho de produção
alcança** — exatamente o que o diagnóstico classificou como Nível 3 e recomendou não priorizar:

| Pacote | Ramos perdidos | Situação |
|---|---:|---|
| `service.calculation.fatigue` | 28 | Alcançável só por penalidade que retorna 1,0 no caminho macro |
| `ga.tactical.strategy.crossover` | 18 | Camada tática sem consumidor (G5) |
| `ga.tactical.repair` | 18 | Camada tática sem consumidor (G5) |
| `ga.fitness.constraint` | 15 | `MandatoryReviewConstraint`, inerte em produção |
| `service.calculation.engagement` | 14 | Como o de fadiga |
| `service.scheduler.strategy` | 16 | Inclui as duas estratégias sem consumidor (G11) |

Somados, são 109 dos 209 ramos ainda descobertos. **Cobri-los subiria o número agregado sem reduzir
risco nenhum** — e é decisão da etapa de estrutura o que fazer com esse código, não da de testes.

### 8.4 Método a método, no caminho crítico

| Método | Antes | Depois |
|---|---:|---:|
| `OptimizerController.generateFullStudyPlan` | **0 / 35** | 35 / 35 |
| `DynamicStudyPlannerService.generateFullStudyPlan` | **0 / 41** | 41 / 41 |
| `GlobalExceptionHandler` — 9 tratadores | 1 de 9 executava | **9 de 9 em 100%** |
| `StudyScheduleGenerator.generate` | parcial | 41 / 41 |
| `StudyScheduleGenerator.buildSchedule` | parcial | 69 / 69 |
| `StudyOptimizerService.optimize` | parcial | 59 / 59 |

---

## 9. Pendências explícitas

O que **não** foi resolvido, com o motivo. Nada aqui foi omitido por esquecimento.

| # | Pendência | Por que ficou | Etapa dona |
|---|---|---|---|
| **P1** | `RandomProvider` continua sendo singleton estático mutável. A extensão isola execução **sequencial**; paralelismo no Surefire ainda quebraria a suíte | A correção raiz é injetar a fonte de aleatoriedade nos operadores do AG — refatoração de produção, e o Javadoc da própria classe diz que o singleton existe justamente para evitá-la | estrutura |
| **P2** | `scalesToManySubjects` continua com asserção de tempo de parede | Trocar tempo por contagem de operações exige instrumentar o motor do AG. Etiquetado, documentado, 17 execuções limpas | performance |
| **P3** | Dois testes novos de API dependem de `LocalDate.now()` | Porque a **produção** depende: `DynamicStudyPlannerService` chama `LocalDate.now()` internamente. A correção é injetar um `Clock` | estrutura |
| **P4** | O `408` é testado por dublê; ninguém verifica que `orTimeout` dispara aos 30 s | Um teste real custaria 30 s de suíte; reduzir exigiria tornar o prazo configurável | performance |
| **P5** | `StudentProfileMapper` aceita lacuna que cita disciplina fora do edital: vira chave nula; duas delas colidem e viram **500** em vez de 400 | Dois testes **documentam** o comportamento atual, marcados `COMPORTAMENTO ATUAL`. Corrigir muda o contrato de erro da API | estrutura / escrita |
| **P6** | Existem tratadores para `422`, `401` e `403` que nenhuma requisição consegue provocar | Testados diretamente. Decidir entre remover ou tornar alcançável é decisão de estrutura e de segurança — o `401`/`403` dependem do `permitAll()` (R13/R14 do inventário) | segurança |
| **P7** | As asserções frouxas de `HybridHeuristicSchedulerTest` não foram apertadas | A classe não tem consumidor em produção (G5). Apertar asserções sobre código que ninguém alcança é esforço sem redução de risco, e encarece removê-la | estrutura |
| **P8** | **O gate ainda não bloqueia merge** | Marcar o check como obrigatório é configuração de repositório; a API respondeu `403` para esta credencial. Passo a passo na seção 4.3 | **ação humana, uma vez** |
| **P9** | O bloco de log `TRACE` de `StudyOptimizerService.runEvolution` continua descoberto (39/103), e com ele `Population.getWorst` e `getAverageFitness` | Nível 3 da lista priorizada. Vale registrar que é um risco real de outra natureza: uma linha de log que só executa com `TRACE` ligado pode quebrar no dia em que alguém ligar | testes (próxima rodada) |
| **P10** | Nenhum teste de concorrência sobre o `optimizerTaskExecutor` (pool 8, fila 50, rejeição rápida) | Fora do escopo desta etapa; o diagnóstico já o havia colocado na de performance | performance / escalonamento |

---

## Resumo em uma tela

- **117 testes** (eram 73), **7 execuções limpas**, comparadas caso a caso.
- **Cobertura: 79,2% de instruções e 80,2% de linhas** (eram 67,1% e 68,4%). Ramos subiu pouco, de
  propósito: o que resta é quase todo código que nenhum caminho de produção alcança.
- **O fluxo de sucesso passou de zero para 100%** nos dois métodos que o diagnóstico apontou como o
  risco número um. **Os 9 tratadores de erro estão em 100%**; antes, 8 nunca executavam.
- **O gate de CI existe e foi verificado nas duas direções** — reprova por teste quebrado e por queda
  de cobertura. **Falta um passo humano** para virar bloqueio de merge (P8).
- **O contrato HTTP tem retrato versionado**, e mudanças nele passam a aparecer no *diff*.
- **Dois defeitos de produção foram encontrados e corrigidos**: a documentação da API estava fora do
  ar (`500`) e o projeto não gerava jar executável. Ambos existiam antes deste trabalho e nenhuma
  suíte os pegava.
- **Dez pendências registradas com justificativa**, das quais uma (P8) precisa de ação humana e
  sete pertencem a etapas seguintes.

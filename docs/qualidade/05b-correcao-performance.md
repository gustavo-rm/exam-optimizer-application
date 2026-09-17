# 05b — Correção de performance

- **Data:** 2026-09-03
- **Branch:** `release/v4.0`
- **Base:** [`05-diagnostico-performance.md`](./05-diagnostico-performance.md) — 9 achados (F1–F9)
- **Commits:** `7731cfc` (F1) · `9db2505` (F5) · `ffa75ca` (F4) · `558265b` (F3) · `8bccfae` (F7) ·
  `e4d157e` (F8 + decisão do F2) · `9e9089a` (F9/S5)
- **Pendências abertas:** P17 (pool próprio para a fitness), P18 (representação do cromossomo),
  P19 (credencial real para o coletor de métricas)

---

## Resumo em uma tela

**Motor de planejamento** — mesma bancada e mesma metodologia do diagnóstico, com a linha de base
**remedida nesta sessão** a partir do commit `a9865e2` (medianas de 15 execuções; 9 no pior caso):

| Cenário | Antes | Depois | Ganho |
|---|---:|---:|---:|
| 15 disciplinas / 100 gerações / população 50 | 29 ms | **13 ms** | −55 % |
| 15 disciplinas / 500 gerações / população 50 | 117 ms | **42 ms** | −64 % |
| 24 disciplinas / 1000 gerações / população 500 | 2 349 ms | **1 163 ms** | −50 % |
| 40 disciplinas / 100 gerações / população 50 | 36 ms | **19 ms** | −47 % |

**Servidor sob concorrência** — carga HTTP contra o `.jar`, 32 amostras por nível, aquecimento de
12 requisições descartado:

| Requisições simultâneas | Antes (mediana / vazão) | Depois (mediana / vazão) | Vazão |
|---|---:|---:|---:|
| 1 | 49 ms / 20,5 req/s | **25 ms / 40,3 req/s** | 2,0× |
| 2 | 84 ms / 23,9 req/s | **31 ms / 64,3 req/s** | 2,7× |
| 4 | 196 ms / 20,4 req/s | **39 ms / 103,5 req/s** | 5,1× |
| 8 | 358 ms / 22,3 req/s | **57 ms / 139,3 req/s** | 6,2× |
| 16 | 533 ms / 30,0 req/s | **93 ms / 172,0 req/s** | 5,7× |

A mudança qualitativa importa mais que os números: **a vazão deixou de ser plana**. Antes, o
sistema entregava ~25 req/s de 1 a 16 clientes simultâneos e só aumentava a latência — a assinatura
de um servidor já saturado por uma requisição só. Agora ele escala com a concorrência.

| | Antes | Depois |
|---|---|---|
| Achados corrigidos | — | **7 de 9** (F1, F3, F4, F5, F7, F8, F9) |
| Achados recusados com evidência | — | **1** (F2), **1 parcial** (F6 → pendência P17) |
| Testes | 203 | **222** (+19) |
| Cobertura (BUNDLE) | 91,09 % instr. / 72,88 % ramos | **91,47 % / 73,85 %** |
| Resultado do algoritmo | — | **idêntico**, bit a bit, nas 9 assinaturas de referência |
| Suíte | verde | **verde** (`mvn clean verify`, rodada após cada mudança) |

---

## 0. A rede de segurança, montada antes de otimizar

A instrução da etapa era que otimização não pode mudar comportamento. Isso só significa alguma
coisa se houver como **verificar**, e não apenas afirmar. Duas redes foram montadas antes da
primeira linha de otimização.

### As 9 assinaturas de referência

Uma *assinatura* aqui é o plano completo produzido pelo algoritmo para uma semente fixa, escrito
como texto ordenado — `D0=105|D1=84|D2=61|…` — mais a fitness com 12 casas decimais. Nove delas
cobrem três sementes × três tamanhos de instância (5, 12 e 24 disciplinas).

Com a semente fixada, a evolução inteira é determinística: a mesma sequência de sorteios produz o
mesmo plano. Isso torna a verificação **muito** mais estrita do que "o resultado continua bom".
Qualquer mudança que altere a **ordem** ou a **quantidade** de sorteios aparece — inclusive
mudanças que não alteram conta nenhuma. Trocar `new HashMap<>()` por `new HashMap<>(capacidade)`,
por exemplo, muda a distribuição das chaves em baldes e, com ela, a ordem de iteração das
disciplinas — que é o que decide qual disciplina o reparo sorteia.

As assinaturas foram capturadas antes do primeiro commit e conferidas depois de **cada** um. Elas
são idênticas do começo ao fim da etapa.

### Os testes que ficaram no repositório

As assinaturas moram na bancada de medição, fora do repositório. O que ficou versionado:

| Arquivo | O que trava | Testes |
|---|---|---:|
| `ga/GaResultadoInalteradoTest` | mesma semente ⇒ mesmo plano e mesma fitness; contraprova de que sementes distintas divergem; orçamento respeitado exatamente | 4 |
| `domain/StudyPlanInvarianteTest` | o total memorizado nunca diverge da soma do mapa, sobre planos produzidos pelos operadores genéticos reais | 4 |
| `ga/PopulationFitnessParalelaTest` | os dois caminhos de avaliação (paralelo e sequencial) dão resultado idêntico, dos dois lados do limiar | 5 |
| `config/AsyncConfigDimensionamentoTest` | o pool sai de `availableProcessors()`, não de constante | 3 |
| `config/ActuatorAcessivelTest` | saúde pública, métricas autenticadas — e ambas alcançáveis | 3 |

**Um teste que nunca reprovou não provou nada.** Os cinco foram verificados no sentido contrário —
a mudança que cada um protege foi desfeita de propósito, e o teste teve de acusar:

| Sabotagem aplicada | O que aconteceu |
|---|---|
| Laço de descendentes paralelizado | `GaResultadoInalteradoTest` reprova **2 de 4** |
| Total memorizado vira a contagem de disciplinas | `StudyPlanInvarianteTest` reprova **1 de 4** |
| Caminho paralelo desvia a fitness em 1 parte em 10 milhões | `PopulationFitnessParalelaTest` reprova **2 de 5** |
| Padrão do pool volta a `núcleos/2` | `AsyncConfigDimensionamentoTest` reprova **1 de 3** |
| Mecanismo de autenticação removido (o defeito original do F9) | `ActuatorAcessivelTest` reprova **2 de 3** — 403 no lugar de 200 e de 401 |
| Actuator inteiro aberto (o defeito oposto) | `ActuatorAcessivelTest` reprova **1 de 3** — 200 onde tinha de ser 401 |

O último par importa: o teste do F9 pega tanto *"ninguém consegue ler"* quanto *"qualquer um
consegue ler"*. Sem a segunda verificação, a correção poderia ter trocado um defeito por um pior.

---

## 1. F1 — a fonte de aleatoriedade era criptográfica

**O maior ganho da etapa, e o de causa mais simples.**

`RandomProvider` entregava um `SecureRandom` — gerador criptográfico, que consulta entropia do
sistema operacional e passa por SHA-1 — para sortear **simulação**: qual pai vence o torneio, qual
disciplina recebe um dia a mais, se ocorre mutação. Nenhum dos dez usos gera identificador, token,
senha, *nonce* ou material de sessão. O perfil de CPU mostrava **~31 % das amostras** dentro de
`sun.security.provider`.

| Cenário | Antes | Depois | Ganho |
|---|---:|---:|---:|
| 15 disc / 100 ger / 50 pop | 29 ms | 23 ms | −21 % |
| 15 disc / 500 ger / 50 pop | 117 ms | 66 ms | −44 % |
| 24 disc / 1000 ger / 500 pop | 2 349 ms | 1 385 ms | **−41 %** |
| 40 disc / 100 ger / 50 pop | 36 ms | 26 ms | −28 % |

**A auditoria de segurança está escrita no próprio arquivo**, porque é ela que sustenta a troca: os
dez usos foram listados e verificados um a um. Se algum dia aparecer um uso que precise de garantia
criptográfica, ele deve instanciar `SecureRandom` no ponto de uso — **não** reverter o padrão daqui.

`ThreadLocalRandom` seria ainda mais rápido e foi rejeitado: não aceita semente, e sem semente as
assinaturas de referência e o teste de reprodutibilidade deixam de existir.

---

## 2. F5 — o total de dias recalculado a cada filho

`StudyPlan.getTotalDays()` somava o mapa inteiro a cada chamada, num objeto imutável cuja soma
nunca muda. Passou a ser calculado uma vez, no construtor.

**A parte interessante é a cópia defensiva que foi medida e rejeitada.** A versão segura por
construção copiaria o mapa recebido, tornando o total impossível de ficar obsoleto. Foi
implementada e medida no pior caso, mediana de 9 execuções:

| Variante | Pior caso | Contra a linha de base |
|---|---:|---:|
| sem memoização, sem cópia (linha de base) | 1 385 ms | — |
| **com** memoização **e** cópia defensiva | 1 688 ms | **pior em 22 %** |
| com memoização, sem cópia | 1 318 ms | melhor em 4,8 % |

Um `LinkedHashMap` novo por plano custa mais do que a memoização economiza, porque planos são
construídos uma vez por descendente — 500 500 vezes no pior caso. A cópia gastaria ~370 ms para
proteger contra um uso que nenhum chamador faz.

A escolha foi o **contrato explícito mais o teste de invariante**: o construtor documenta que recebe
a posse do mapa, e `StudyPlanInvarianteTest` verifica, sobre 800 planos produzidos pela cadeia real
de crossover e mutação, que o total memorizado bate com a soma.

**Estratégia de invalidação: não existe, e isso é uma afirmação, não um esquecimento.** Não há
método que altere a alocação — o campo é `final`, o mapa só sai por visão não modificável, e o total
é derivado no construtor. Não há evento capaz de tornar o valor obsoleto, logo não há o que
invalidar. O cálculo mora no construtor justamente para que quem acrescentar um método mutador não
consiga não vê-lo.

---

## 3. F4 — `requiredSessions` recalculada 12 milhões de vezes

`RetentionObjective` chamava `LearningModel.requiredSessions(disciplina, horizonte)` dentro do laço
por disciplina, a cada avaliação de fitness: 12 012 000 chamadas para **24 resultados distintos**,
com entradas constantes durante toda a execução. O resultado passou a ser pré-calculado uma vez, no
`EvolutionContext`.

| Cenário | Antes (após F5) | Depois | Ganho |
|---|---:|---:|---:|
| 15 disc / 500 ger / 50 pop | 71 ms | 65 ms | −8 % |
| 24 disc / 1000 ger / 500 pop | 1 329 ms | 1 259 ms | −5 % |

**O ganho real ficou abaixo da estimativa, e vale registrar por quê.** O diagnóstico projetava
208 ms a partir de micro-medição isolada (17,29 ns × 12 M). O ganho em situação foi ~70 ms. A
diferença é o compilador JIT: no laço quente ele já embutia a aritmética, então o custo isolado não
se repetia integralmente no contexto real. É a razão de medir em situação e não extrapolar.

**Estratégia de invalidação, como pedido:** o `EvolutionContext` é construído **por requisição** e é
imutável — um `record`. O mapa pré-calculado nasce e morre com a otimização, e as entradas de que
ele depende (as disciplinas do edital e o horizonte de planejamento) são fixas durante toda a
execução. Não há janela em que possa ficar desatualizado.

O que seria um defeito: um cache **estático** dentro de `LearningModel`, com chave vinda da
requisição. Ele cresceria sem limite ao longo da vida do processo e guardaria dados de um aluno para
servir a requisição de outro. A escolha do lugar é a estratégia de invalidação.

---

## 4. F3 — paralelismo onde ele custava caro

A avaliação de fitness usava `parallelStream()` **incondicionalmente**. Medindo os dois caminhos
com 200 gerações e 15 disciplinas, medianas de 15 execuções:

| População | Paralela | Sequencial | Vencedor |
|---:|---:|---:|---|
| 10 | 15 ms | **10 ms** | sequencial, por 50 % |
| 25 | 19 ms | **16 ms** | sequencial |
| 50 | 27 ms | 26 ms | empate |
| 100 | **44 ms** | 47 ms | paralela |
| 200 | **85 ms** | 95 ms | paralela |
| 500 | **193 ms** | 219 ms | paralela, por 12 % |

Abaixo de ~50 indivíduos, repartir o trabalho entre threads e recolher o resultado custa mais do que
economiza — avaliar um indivíduo é curto demais para pagar a coordenação. Como o contrato da API
aceita população de 10 a 500, **boa parte da faixa admitida caía no lado ruim**. O limiar ficou em
64, o primeiro valor redondo dentro da faixa em que a paralela vence com folga.

| Cenário | Antes (após F4) | Depois | Ganho |
|---|---:|---:|---:|
| 15 disc / 100 ger / 50 pop | 21 ms | 16 ms | −24 % |
| 15 disc / 500 ger / 50 pop | 65 ms | 55 ms | −15 % |

**O teste que garante que o resultado não muda** (`PopulationFitnessParalelaTest`) confere, dos dois
lados do limiar, que a fitness atribuída é idêntica **sem margem de tolerância** à do cálculo direto
de cada indivíduo. A igualdade exata é legítima porque avaliar um indivíduo é operação pura: depende
só dele e do contexto, não sorteia nada, e nenhuma thread lê o resultado de outra. Não há acumulador
compartilhado cuja ordem de soma pudesse variar.

**Este é também o achado que destravou a escala do servidor**, e é onde o F6 foi parcialmente
resolvido — ver a seção 6.

---

## 5. F8 — a troca entre imutabilidade e velocidade não existia

O diagnóstico classificou o F8 como custo estrutural sem correção óbvia: `StudyPlan` embrulha o mapa
de genes em `Collections.unmodifiableMap` para impedir alteração, e percorrer esse invólucro custa
mais. *"A imutabilidade é uma garantia real — o custo é o preço dela, e a decisão é de projeto."*

**A troca não existia.** O invólucro cria um objeto de entrada por gene só para bloquear
`setValue`, mas seu `forEach` **delega direto** ao mapa de baixo, sem criar entrada nenhuma. Medido
em percursos de 24 genes, mediana de 3 baterias de 3 milhões de voltas:

| Forma de percorrer | Custo por percurso | Contra o `HashMap` cru |
|---|---:|---:|
| `entrySet()` no `HashMap` cru | 199 ns | — |
| `entrySet()` no mapa envolto | 272 ns | +37 % |
| **`forEach()` no mapa envolto** | **61 ns** | **−69 %** |

O `forEach` é mais rápido que percorrer o próprio `HashMap` sem proteção alguma. Os três objetivos
de fitness passaram a usá-lo. Medido no algoritmo completo, **4 baterias pareadas** alternando as
duas versões para não confundir diferença com deriva da máquina:

| Cenário | Antes (mediana) | Depois | Ganho |
|---|---:|---:|---:|
| 15 disc / 100 ger / 50 pop | 15,5 ms | 14 ms | −10 % |
| 15 disc / 500 ger / 50 pop | 49,5 ms | 43,5 ms | −12 % |
| 40 disc / 100 ger / 50 pop | 22 ms | 19,5 ms | −13 % |
| 24 disc / 1000 ger / 500 pop | 1 218 ms | 1 215 ms | sem diferença |

No pior caso não há ganho: lá a fitness já roda em 4 threads e o cruzamento domina o tempo. O ganho
aparece nos cenários de população pequena — que são exatamente os do payload de carga.

---

## 6. F7 e F6 — o servidor não escalava

### F7: o pool dimensionado por um número inventado

```properties
# antes, em application.properties:54
# Available core count
optimizer.thread-pool-size=8
```

O comentário dizia "contagem de núcleos disponíveis". O contêiner tem **4**. Na inicialização a
aplicação registrava, literalmente, *"Configuring optimizerTaskExecutor with 8 cores"*. O número não
vinha da máquina; só parecia vir.

Medido a 16 requisições simultâneas, **medianas de 3 baterias alternadas** entre as configurações:

| Tamanho do pool | Vazão |
|---|---:|
| 2 — o padrão do código (`núcleos/2`) | 142 req/s |
| **4 — `núcleos`** | **170 req/s** |
| 6 | 155 req/s |
| 8 — o valor fixo em arquivo | 138 req/s |

O pool de 4 venceu o de 8 nas três baterias (170/148, 170/126, 145/138). O padrão do próprio código
também estava baixo: `núcleos/2` vinha de quando a fitness era sempre paralela e cada requisição já
ocupava vários núcleos por dentro. Com o limiar do F3, uma requisição de população típica virou
monothread, e **o pool voltou a ser o que de fato limita a concorrência** — uma thread por núcleo.

A propriedade passou a `0` (a máquina decide) e continua aceitando um número explícito, agora
documentado como porta de escape para limitar CPU de propósito. A mensagem de log deixou de chamar
threads de "cores".

### F6: resolvido em parte, e a parte que sobra está registrada

O F6 media que `parallelStream()` no caminho de requisição troca vazão do servidor por latência de
um pedido: o `ForkJoinPool` comum é global, então o ganho de uma requisição sai das outras. O limiar
do F3 elimina essa disputa nas populações pequenas — que são a maioria da faixa admitida — mas não
nas grandes.

Fechar isso de vez exigiria um pool próprio para a fitness, dimensionado junto com o executor de
requisições, de modo que os dois níveis de paralelismo não somem mais threads do que a máquina tem.
Isso é decisão de arquitetura de concorrência, não ajuste local: **pendência P17**, anotada em
`Population.calculateFitness`.

---

## 7. F9 — a métrica existia e não podia ser lida

A configuração de segurança terminava em `anyRequest().authenticated()` **sem declarar mecanismo de
autenticação nenhum**. Como a classe define um `SecurityFilterChain` próprio, o Spring Boot não
acrescenta os padrões dele — então não havia forma de apresentar credencial. O resultado não era
"protegido": era **inalcançável**. `403` permanente, para qualquer um, com ou sem senha.

Dois defeitos registrados em etapas diferentes vinham daí:

- **S5** (etapa 02) — `management.endpoint.health.probes.enabled=true` declara sondas de *liveness*
  e *readiness* para um orquestrador, mas elas respondiam `403`. Uma sonda de Kubernetes concluiria
  que a aplicação está morta e reiniciaria em laço um processo saudável.
- **F9** (etapa 05) — a métrica `dynamicstudyplanner.optimization.duration` é publicada e
  `prometheus` está na lista de exposição, mas `/actuator/prometheus` respondia `403`. Foi por isso
  que ~15 % do tempo de requisição ficou **sem atribuição** no diagnóstico.

A correção não afrouxa nada, e foi verificada na aplicação real:

| Caminho | Antes | Depois |
|---|---:|---:|
| `/actuator/health` sem credencial | 403 | **200** |
| `/actuator/prometheus` sem credencial | 403 | **401** (desafio) |
| `/actuator/prometheus` com credencial | 403 | **200** |

### O buraco que o diagnóstico deixou, agora fechado

Com a métrica legível, os ~15 % não atribuídos foram medidos. 40 requisições sequenciais do payload
de 15 disciplinas, contador lido antes e depois:

| | Valor |
|---|---:|
| Latência HTTP externa | 27,5 ms (média) · 26,7 ms (mediana) |
| Tempo interno do AG (métrica) | 14,2 ms (média) |
| **O AG explica** | **51 % da requisição** |
| Fora do AG | 13,4 ms — HTTP, JSON, geração do cronograma e o pipeline do Spring |

**Pendência P19:** a credencial ainda é o usuário `user` com senha gerada a cada inicialização
(achado R14 do inventário). Serve para desenvolvimento, não para produção. Definir uma credencial
real para o coletor é decisão de implantação, e hoje não há implantação nenhuma (R2).

---

## 8. F2 — o maior achado da lista, e por que ele não foi corrigido

O F2 era o item nº 1 por tempo: **817 ms de 1 592 ms no pior caso, 51 % do tempo de parede**,
inteiramente monothread. Ele **não foi corrigido**, e esta seção existe porque uma recusa sem
evidência não vale nada.

### O suspeito óbvio é gratuito

`ChildGeneRepair.repairToTargetSum` ajusta a soma de dias movendo **um dia por volta**, sorteando
uma disciplina a cada uma. Parece o laço quente. Foi instrumentado com contadores:

| | Medido |
|---|---:|
| Chamadas de reparo (pior caso) | 474 412 |
| Voltas do laço, no total | 12 075 |
| **Voltas por chamada** | **0,025** |
| Maior desvio já visto | 21 dias |

A média ponderada de dois planos válidos erra a soma só pelo arredondamento de cada gene. O laço
praticamente não roda.

### Eliminar as buscas redundantes: implementado, medido, revertido

`createWeightedAverageGenes` iterava `keySet()` e depois rebuscava o valor do pai com
`getOrDefault` — uma busca de mapa por gene, por filho, que a própria iteração já tinha em mãos.
`RepairingCrossover` fazia o mesmo, mais uma `ArrayList` intermediária por chamada.

A correção foi escrita, verificada **idêntica** nas 9 assinaturas, e medida em 4 baterias pareadas:

| Bateria | Antes | Depois |
|---|---:|---:|
| 1 | 1 437 ms | 1 295 ms |
| 2 | 1 225 ms | 1 216 ms |
| 3 | 1 240 ms | **1 255 ms** |
| 4 | 1 217 ms | **1 297 ms** |
| **Mediana** | **1 232 ms** | **1 275 ms** |

A primeira bateria sugeria 10 % de ganho. As três seguintes mostraram que aquilo era **deriva da
máquina**, não a mudança: o JIT e o cache da CPU já absorviam a busca redundante. **A mudança foi
revertida** — não se acrescenta churn a um arquivo crítico para reprodutibilidade em troca de nada.

### Paralelizar quebra a reprodutibilidade — verificado, não suposto

Seleção, cruzamento e mutação consomem **uma** fonte de aleatoriedade compartilhada, e o número de
sorteios por descendente varia. Não há como repartir a sequência entre threads sem saber de antemão
quantos números cada uma vai consumir.

O laço foi paralelizado de forma experimental para medir a consequência:

| Verificação | Resultado |
|---|---|
| 9 assinaturas de referência | **as 9 mudaram** |
| `GaResultadoInalteradoTest` | **2 dos 4 testes reprovam** |
| O que reprova, exatamente | duas execuções com a **mesma semente** deixam de coincidir |

Não é "um plano diferente, igualmente válido". É **a mesma requisição devolvendo planos diferentes a
cada chamada** — a reprodutibilidade se perde por completo.

E o ganho nem seria consistente:

| Cenário | Sequencial | Paralelo |
|---|---:|---:|
| 24 disc / 1000 ger / 500 pop | 1 244 ms | **1 088 ms** (−13 %) |
| 15 disc / 500 ger / 50 pop | **50 ms** | 83 ms (**+66 %**) |

O mesmo padrão do F3: só a instância maior ganha, e a média perde feio.

### Onde o tempo está de verdade

Descartados o reparo, as buscas redundantes e a alocação (a pausa de GC é ~1 % do tempo, com 9,7 GB
reciclados sem esforço pelo coletor jovem), sobra a **representação do cromossomo**.
`Map<Subject, Integer>` paga hash, *boxing* e alocação de nó **por gene** — ~72 ns por gene sobre
474 mil recombinações. Trocar por um vetor indexado eliminaria os três custos de uma vez.

Isso muda o tipo de domínio usado no sistema inteiro (fitness, cronograma, mapeamento da API) e
altera a ordem de iteração — logo, o resultado. **É mudança estrutural com decisão de produto, não
ajuste de otimização: pendência P18**, escrita em `GeneticAlgorithm.evolvePopulation`, onde a
próxima pessoa vai tentar mexer.

---

## 9. Item 5 do escopo — consultas N+1 e índices

**Não se aplica, e a evidência é a mesma do diagnóstico, reconferida:**

| Verificação | Resultado |
|---|---:|
| Dependências de persistência no `pom.xml` (JPA, JDBC, Hibernate, jOOQ, MyBatis, drivers, Flyway, Liquibase, Mongo, Redis) | **0** |
| Anotações `@Entity`, `@Repository`, `@Table`, `@Query`, `@Transactional`, `DataSource`, `EntityManager`, `JdbcTemplate` em 123 arquivos de `src/main` | **0** |
| Chaves `spring.datasource`, `spring.jpa`, `spring.data` na configuração | **0** |

Não há banco de dados, portanto não há consulta N+1, índice ausente nem plano de consulta a validar.
O sistema é um motor de cálculo sem estado: recebe o pedido, otimiza, devolve.

**O análogo computacional do N+1 existe e foi corrigido.** Um N+1 é o mesmo trabalho refeito uma vez
por item quando poderia ser feito uma vez só. É exatamente o que os achados F4 e F5 eram — 12 milhões
de recálculos de 24 valores constantes, e uma soma completa refeita a cada filho gerado. As seções 2
e 3 têm as medições.

---

## 10. Metodologia — e uma correção nela

Toda medição desta etapa usa a mesma bancada: JVM aquecida antes de medir, medianas de 15 execuções
(9 no pior caso), e a fonte de aleatoriedade **não semeada**, para refletir produção.

**Uma correção de metodologia vale registro.** A tabela de latência HTTP do diagnóstico foi tirada
com o gravador JFR ligado, que acrescenta sobrecarga. Reaproveitar aqueles números como "antes"
seria comparar metodologias diferentes. Por isso a linha de base HTTP e a do motor foram
**remedidas nesta sessão**, a partir do commit `a9865e2`, sem JFR — e a base do motor reproduziu o
diagnóstico de perto (29 / 117 / 2 349 / 36 ms contra 30 / 118 / 2 399 / 36 ms), o que dá confiança
nas duas.

Um erro de classpath foi encontrado e corrigido no caminho: a primeira medição da linha de base
carregava as classes atuais em vez das do commit antigo, porque o arquivo de classpath continha a
entrada relativa `target/classes`, resolvida contra o diretório de trabalho. O sintoma que denunciou
foi o próprio harness reportando `rng=Random` onde o código antigo tem `SecureRandom`. A medição foi
refeita com o classpath saneado.

---

## 11. Cobertura

| | Antes | Depois |
|---|---:|---:|
| Instruções | 91,09 % | **91,47 %** |
| Ramos | 72,88 % | **73,85 %** |
| Testes | 203 | **222** |

O piso do `jacoco:check` acompanha o valor medido, como nas etapas anteriores — e uma armadilha
foi encontrada nesse caminho. **O JaCoCo trunca a razão antes de comparar, não arredonda.** A
medição real é 0,91469; ele a lê como 0,9146. Um piso de 0,9147, tirado de um `printf` que
arredondava, reprovava um build que não havia regredido em nada, e o sintoma parecia instabilidade
do gate. O aviso está escrito no `pom.xml`, ao lado do limite.

**O piso de ramos desceu 0,0001 e isso não é regressão.** A conversão dos três objetivos de fitness
para `forEach` removeu três laços `for`, e com eles três ramos que **estavam cobertos**. Tirar ramos
cobertos de um conjunto coberto em 74 % abaixa a razão: 390/527 = 0,7401 virou 387/524 = 0,7385. Não
há teste a menos — a cobertura de instruções subiu no mesmo commit. A explicação está escrita no
`pom.xml`, ao lado do próprio limite, porque é lá que a próxima pessoa vai estranhar o número.

---

## 12. O que ficou aberto

| # | Pendência | Onde está anotada | Por que não foi feita agora |
|---|---|---|---|
| **P17** | Pool próprio para a avaliação de fitness, dimensionado junto com o executor de requisições | `ga/Population.java` | Arquitetura de concorrência: os dois níveis de paralelismo precisam ser dimensionados em conjunto, não isoladamente |
| **P18** | Representação do cromossomo como vetor indexado em vez de `Map<Subject, Integer>` | `ga/GeneticAlgorithm.java` | Muda o tipo de domínio no sistema inteiro e altera o resultado; é decisão de produto |
| **P19** | Credencial real para o coletor de métricas | `config/SecurityConfig.java` | Decisão de implantação, e não há implantação hoje (R2) |

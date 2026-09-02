# Etapa 03b — Correção de estrutura e arquitetura

**Escopo:** ataque aos achados de [`03-diagnostico-estrutura.md`](./03-diagnostico-estrutura.md).
Escrita/manutenibilidade, performance e escalonamento continuam para as etapas seguintes.

| | |
|---|---|
| **Data** | 2026-09-02 |
| **Base** | `6729945` (etapa 03) |
| **Branch** | `release/v4.0` |
| **Suíte** | 145 → **148 testes**, todos passando |
| **Cobertura** | instruções 79,4% → **81,4%**; ramos 58,6% → **60,0%**; linhas 80,3% → **82,2%** |
| **Validações** | `mvn clean verify` rodado **cinco vezes**, uma após cada mudança estrutural |

### Vocabulário usado neste documento

- **ADR** (*Architecture Decision Record*) — documento curto que registra uma decisão arquitetural:
  contexto, decisão, alternativas descartadas e consequências aceitas. Serve para responder, meses
  depois, "por que isto está assim?".
- **Inversão de dependência** — quando dois módulos precisam se conhecer, a **interface pertence a
  quem consome**, não a quem implementa.
- **Ciclo de dependência** — A depende de B e B depende de A. Impede compilar, testar ou extrair um
  sem o outro, e não produz erro de compilação.
- **Grafo acíclico** — grafo de dependências sem nenhum ciclo. É a propriedade que permite extrair
  um módulo.

---

## Resumo do que mudou

| Achado da etapa 03 | Situação |
|---|---|
| **E4** — ciclo `ga` ↔ `service` | **Eliminado.** Grafo de módulos agora é acíclico |
| **E5** — `RetentionAlgorithm` no módulo de quem implementa | **Corrigido.** Movida para `domain.retention` |
| **E2** — composição de produção replicada | **Cadeia de alocação unificada**; composição de fitness mantida duplicada **de propósito**, com guarda nova |
| **E6** — código inalcançável | **172 instruções removidas**; 952 mantidas, com decisão registrada e gatilho de reavaliação |
| Fronteiras sem verificação | **`ModuleBoundaryTest`**, com 3 regras, verificado por sabotagem |

**Três ADRs criados** em `docs/adr/`, que o inventário da etapa 00 havia registrado como inexistentes.

**Nada foi decidido em silêncio.** As duas decisões que envolviam escolha — o que fazer com a camada
tática e o que fazer com a duplicação da fitness — estão nos ADRs 0002 e 0003, com as alternativas
descartadas e o porquê.

---

## 1. Limite entre módulos: contrato explícito onde havia import direto

### O que era

Quatro imports diretos de `ga` para `service`, sem contrato intermediário — dois deles para **classe
concreta**, sem abstração nenhuma no caminho:

```java
// ga/fitness/penalty/FatigueAndSustainabilityPenalty.java  (antes)
import com.ia.project.dynamicstudyplanner.service.calculation.fatigue.FatigueAndEnergyModel;
private final FatigueAndEnergyModel fatigueModel;
```

### O que foi feito

Dois contratos novos em `domain`, seguindo o precedente do `RetentionAlgorithm` que já existia:

| Contrato criado | Pacote | Implementação |
|---|---|---|
| `FatigueAlgorithm` | `domain.fatigue` | `service.calculation.fatigue.FatigueAndEnergyModel` |
| `DropoutRiskAlgorithm` | `domain.engagement` | `service.calculation.engagement.DropoutRiskPredictor` |

```java
// ga/fitness/penalty/FatigueAndSustainabilityPenalty.java  (depois)
import com.ia.project.dynamicstudyplanner.domain.fatigue.FatigueAlgorithm;
private final FatigueAlgorithm fatigueModel;
```

As implementações continuam em `service` e passaram a declarar `implements`. Nenhuma lógica mudou.

**Convenção adotada junto:** contratos de cálculo de domínio usam o sufixo `...Algorithm`. É resposta
parcial ao achado E9 — estabelece o sufixo para a categoria *contrato*, sem renomear as implementações
existentes, o que seria mudança de escopo maior.

**Verificação:** `grep` por `import ...service` dentro de `ga/` retorna **zero ocorrências**.

---

## 2. Inversão de dependência: a interface mudou de lado

`RetentionAlgorithm` já era interface — o defeito era onde morava:

```
antes                                        depois
service/calculation/retention/               domain/retention/
├── RetentionAlgorithm.java   ← contrato     └── RetentionAlgorithm.java   ← contrato
└── HybridRetentionEngine.java ← impl        service/calculation/retention/
                                             └── HybridRetentionEngine.java ← impl
```

Toda a assinatura já usava tipos de domínio (`Subject`, `SubjectRetentionState`, `LocalDate`), então o
contrato pertencia ali. `git mv` preservou o histórico do arquivo.

**Sobre a pureza do domínio.** A etapa 03 registrou como força que `domain` tinha 0 de 22 classes
importando framework. Os três contratos acrescentados **não importam framework**, então a propriedade
se mantém — agora com 0 de 24 classes, e travada por teste (seção 6).

---

## 3. Ciclo eliminado

**Antes:**

```
api ──▶ domain, usecase
ga  ──▶ domain, service, util          ciclo: ga ↔ service
service ──▶ domain, ga, usecase, util
usecase ──▶ domain
```

**Depois:**

```
api ──▶ domain, usecase
ga  ──▶ domain, util                   ciclos: NENHUM
service ──▶ domain, ga, usecase, util
usecase ──▶ domain
```

O grafo virou acíclico. `ga` deixou de compilar contra `service` — pode ser extraído como biblioteca
sem arrastar o módulo de serviços junto, que é a evolução natural de um componente com 39 classes e
suíte própria.

Os seis ciclos entre pacote-pai e pacote-filho continuam existindo e **continuam não sendo defeito**,
como a etapa 03 já havia argumentado: são o padrão normal de interface no pacote de cima e
implementações em subpacotes. O `ModuleBoundaryTest` verifica ciclos **entre módulos de topo**, e não
entre pacotes, justamente para não gerar ruído que ensine a equipe a ignorar o teste.

---

## 4. Código morto removido — e o que foi mantido, com justificativa

### Confirmação antes de remover

O pedido era explícito: confirmar contra a cobertura antes de remover, para não apagar algo que só
está sem teste. Cada candidato passou por três verificações — busca textual em `src/main`,
`src/test` e `benchmarks/`; presença de anotação Spring; e cobertura da etapa 01.

### Removido: 172 instruções

| Classe | Instruções | Confirmação |
|---|---:|---|
| `service.scheduler.strategy.RandomizedInterleavedStrategy` | 82 | 0 referências, sem anotação, 0% de cobertura |
| `service.scheduler.strategy.CriticalFirstStrategy` | 66 | idem |
| `domain.exam.ExamType` | 15 | 0 referências em lugar nenhum do repositório |
| `api.dto.OptimizationResponse` | 9 | Usada só pelo método `@Deprecated` removido junto |

O método `OptimizationResultMapper.toResponse` saiu com o DTO. O import que o sustentava trazia o
comentário `// This can be removed now`, escrito pelo próprio autor.

Com isso, **a pendência G11 de `docs/revisao-ag/` fica fechada.**

### Mantido: 952 instruções, com decisão registrada

A camada tática — `ga.tactical.*`, `service.scheduler.tactical.*`, as duas penalidades e a restrição
que só agem sobre `TacticalStudyPlan`:

| Classe | Cobertura | Instruções |
|---|---:|---:|
| `FatigueAndEnergyModel` | 1,3% | 235 |
| `HybridHeuristicScheduler` | **100,0%** | 189 |
| `DayBoundaryCrossover` | 0,0% | 123 |
| `SpacedRepetitionRepairer` | 4,9% | 122 |
| `DropoutRiskPredictor` | 3,4% | 88 |
| `MandatoryReviewConstraint` | 22,1% | 77 |
| `MethodologyMutation` | 0,0% | 61 |
| `DropoutRiskPenalty` | 29,8% | 57 |

**Por que não removi.** Existe decisão registrada em contrário, em `docs/revisao-ag/README.md`, G5:
*"Agora com destino explícito: é o ponto de partida da opção (i) se ela for reaberta"*. E a existência
desse andaime foi **insumo de uma estimativa de esforço** em `06-decisao-ausubel.md` §2.3.

Sobrescrever silenciosamente uma decisão de uma revisão anterior, numa etapa de estrutura, seria
errado — e o pedido desta etapa exige exatamente o contrário para duplicação ("não decida
silenciosamente"). O mesmo princípio se aplica aqui.

**Minha recomendação, registrada no ADR-0002 e não escondida:** o argumento a favor de remover é
forte — *"podemos precisar depois"* é a razão mais fraca para manter código morto, porque o histórico
do git já resolve isso. O que faz a decisão não ser minha é a decisão registrada e o fato de que a
remoção apagaria também `HybridHeuristicSchedulerTest`, que tem 100% de cobertura da classe.

**O ADR-0002 fixa um gatilho**, para que "manter por enquanto" não vire "manter para sempre" por
inércia: se a opção (i) for formalmente descartada, ou se passar um ano sem retomada, o ADR deve ser
substituído por outro que autorize a remoção.

---

## 5. Duplicação: decidida, não silenciada

Os dois casos receberam tratamento diferente **porque a causa é diferente**. Ambos estão no ADR-0003.

### 5.1 Cadeia de alocação — **unificada**

Três cópias idênticas. Criada `service.scheduler.strategy.AllocationChains`:

```java
public static AllocationStrategy production(int maxDailyCognitiveLoad) {
    return new ReviewFocusedStrategy(
            new CognitiveLoadBalancingStrategy(
                    new InterleavedCriticalStrategy(),
                    maxDailyCognitiveLoad));
}
```

Produção, benchmark e teste passaram a chamá-la:

| Arquivo | Antes | Depois |
|---|---|---|
| `service/DynamicStudyPlannerService.java` | 6 linhas de aninhamento | `AllocationChains.production(...)` |
| `benchmarks/.../MetricsCalculator.java` | réplica + comentário admitindo | `AllocationChains.production(maxDailyLoad)` |
| `benchmarks/.../MetricsCalculator.java` (variante) | réplica sem podador | `AllocationChains.productionWithoutLoadPruning()` |
| `src/test/.../GaEdgeCasesTest.java` | réplica | `AllocationChains.production(maxLoad)` |

**Verificação:** as duas únicas ocorrências de `new ReviewFocusedStrategy(` no repositório estão
dentro da própria fábrica.

### 5.2 Composição de fitness — **duplicação intencional, agora vigiada**

Seis cópias fora da produção. **Não unificada**, e a razão é concreta: os benchmarks precisam
construir variantes com pesos perturbados — é literalmente o que `WeightSensitivityMain` mede
(estabilidade do plano sob ±20% nos pesos). Unificar removeria a capacidade de variar, que é a função
daquele programa.

No lugar, uma guarda em `DynamicStudyPlannerApplicationTests` que fixa **quais tipos** compõem a
fitness que o Spring monta, não só a soma dos pesos.

**A distinção importa e foi o motivo de mexer nela.** A verificação anterior — soma igual a 1,0 —
**não pega** o caso realista: um `FitnessObjective` novo com peso 0,0 mantém a soma em 1,0 e passa
despercebido, e as seis cópias continuam rodando sem ele. Fixando os tipos, qualquer alteração da
composição falha, e a mensagem lista os seis arquivos a atualizar no mesmo commit.

---

## 6. Fronteiras travadas por teste

`arquitetura/ModuleBoundaryTest` — 3 testes que leem os `import` do código-fonte:

| Regra | O que reprova |
|---|---|
| Sem ciclo entre módulos de topo | Um import que recrie `ga` ↔ `service` ou qualquer outro ciclo |
| `domain` não depende de nenhum módulo do projeto | Uma classe de domínio importando `ga`, `service` ou `api` |
| `domain` não importa framework | Spring, Jakarta, Swagger ou Jackson em `domain` |

Não usa biblioteca de análise arquitetural: lê os arquivos e aplica as regras em ~140 linhas. É
pequeno o bastante para ser lido inteiro por quem ele reprovar — o que importa mais aqui do que
sofisticação, porque a mensagem de falha precisa explicar a regra a quem nunca leu o diagnóstico.

**Verificado por sabotagem, não presumido.** Acrescentei um `import org.springframework...` a
`domain/StudyPlan.java`, rodei, obtive `Tests run: 3, Failures: 1` com a mensagem
*"Uma classe de dominio passou a importar framework"*, e reverti.

---

## 7. Validação incremental

O pedido era explícito: rodar a suíte após cada mudança estrutural, sem acumular. Foi o que foi feito.

| # | Mudança | Resultado |
|---|---|---|
| 1 | `RetentionAlgorithm` movida para `domain.retention` | `BUILD SUCCESS` |
| 2 | `FatigueAlgorithm` e `DropoutRiskAlgorithm` criados e ligados | `BUILD SUCCESS` — ciclo verificado como eliminado |
| 3 | Quatro classes mortas removidas | `BUILD SUCCESS` |
| 4 | `AllocationChains` criada; produção, benchmark e teste unificados | `BUILD SUCCESS` |
| 5 | Guarda de composição de fitness fortalecida | `BUILD SUCCESS` |
| final | Suíte completa + gate de cobertura | **148 testes, `All coverage checks have been met`** |

Nenhuma etapa exigiu voltar atrás. O gate de cobertura da etapa 01b passou em todas — e a cobertura
**subiu** em cada remoção, porque o código removido tinha 0%.

---

## 8. ADRs criados

`docs/adr/` não existia; o inventário da etapa 00 §4.4 registrou a ausência. O diretório começa aqui,
com um `README.md` explicando o formato e a regra de que **um ADR aceito não é editado** — se a
decisão mudar, escreve-se outro que o substitui.

| ADR | Título | Decisão em uma linha |
|---|---|---|
| [0001](../adr/0001-abstracoes-de-calculo-no-dominio.md) | Contratos de cálculo de domínio vivem em `domain` | A interface pertence a quem consome; a implementação fica onde está |
| [0002](../adr/0002-remocao-de-codigo-inalcancavel.md) | Remover o inalcançável sem destino; manter a camada tática | Com gatilho explícito de reavaliação |
| [0003](../adr/0003-composicao-de-producao-unica.md) | Unificar a cadeia; manter a fitness duplicada, com guarda | Duplicação declarada vale mais que abstração forçada |

Cada um traz a seção **Alternativas consideradas** — a parte que mais se perde com o tempo e a que
mais serve a quem for revisitar.

---

## 9. O que ficou de fora, e por quê

Achados da etapa 03 que **não** foram tratados nesta etapa:

| # | Achado | Por que ficou |
|---|---|---|
| **E1** | `EvolutionContext.of()` com 10 parâmetros posicionais | **Corrigido logo em seguida, na etapa 03c** — ver seção 10. Ficou fora *desta* etapa de propósito: misturá-lo com mudanças de fronteira tornaria impossível saber qual delas quebrou algo |
| **E3** | `DomainException` usada 1 vez contra 13 `IllegalArgumentException` | Corrigir muda o **contrato de erro da API**: exceções que hoje viram `400` passariam a `422`. É mudança visível ao cliente e pertence a uma etapa que trate contrato, não estrutura interna |
| **E7** | `GlobalExceptionHandler` com 447 linhas e 16 tratadores | Separar em três `@RestControllerAdvice` por natureza é refatoração de API, e faz par com E3 |
| **E8** | `StudyOptimizerService` mistura 4 responsabilidades | Extrair a montagem do contexto depende de E1 estar resolvido primeiro — é o mesmo código |
| **E9–E11** | Sufixos, organização de pastas, idioma misturado | Convenções de escrita. Pertencem à etapa de escrita/manutenibilidade, que vem a seguir |
| **E12–E13** | `new` em vez de injeção; nomes qualificados | Pontuais, no mesmo arquivo que E8 tocará |
| **E14** | Operadores genéticos anotados com `@Component` | Removê-las exige decidir como o `GeneticAlgorithmFactory` passa a compor os operadores. Mudança de desenho, não de arrumação |

**Parcialmente atendido:** E9 — a convenção `...Algorithm` foi estabelecida para contratos de cálculo
(ADR-0001), mas as implementações continuam com seis sufixos.

---

## Resumo em uma tela

- **O ciclo `ga` ↔ `service` acabou.** O grafo de módulos é acíclico, e `ga` pode ser extraído como
  biblioteca sem arrastar `service`.
- **Três contratos vivem em `domain` agora**, um movido e dois criados. O domínio continua puro — 0
  de 24 classes importando framework — e isso passou a ser verificado por teste.
- **172 instruções de código morto removidas**, cada uma confirmada por três sinais antes de sair.
  G11 fechada.
- **952 instruções mantidas com decisão registrada e gatilho de reavaliação**, porque havia decisão
  anterior em contrário. A recomendação de remover está escrita no ADR-0002, não escondida.
- **A cadeia de alocação tem uma definição só.** A composição de fitness continua duplicada de
  propósito, agora com guarda que pega o objetivo novo de peso zero — que a verificação anterior não
  pegava.
- **`ModuleBoundaryTest` trava as três regras de fronteira**, verificado por sabotagem revertida.
- **148 testes, cobertura 81,4% / 60,0% / 82,2%**, com `verify` rodado cinco vezes — uma após cada
  mudança estrutural, como pedido.
- **`docs/adr/` existe**, com três ADRs e a regra de não editar decisão aceita.
- **E1 foi corrigido na etapa 03c**, imediatamente depois e em commit separado — ver seção 10.


---

## 10. Adendo — etapa 03c: E1 corrigido

Feito logo após esta etapa, **em commit separado e de propósito**: manter a refatoração do motor
apartada das mudanças de fronteira é o que permite saber qual delas quebrou algo, se algo quebrar.

### O que mudou

`EvolutionContext.of()` — dez parâmetros posicionais, nove locais de chamada — foi substituído por um
**construtor passo a passo** e removido. Os nove chamadores foram migrados: produção, quatro classes
de teste e três programas de benchmark.

```java
// antes, em TransferMutationTest
EvolutionContext.of(Map.of(), Map.of(), null, null, null, null, null, 180, 4, 20);

// depois
EvolutionContext.builder()
        .importanceScores(Map.of())
        .minimumDaysPerSubject(Map.of())
        .planningHorizonDays(180)
        .hoursPerStudyDay(4)
        .maxDailyCognitiveLoad(20)
        .build();
```

Cinco valores são obrigatórios e `build()` nomeia **todos** os ausentes de uma vez; cinco são
opcionais e valem `null` quando omitidos — exatamente o que os chamadores do caminho macro passavam
antes, então **não há mudança de comportamento**.

### Como se sabe que o comportamento não mudou

Não por inspeção: a suíte tem dois testes que travam números do algoritmo genético com sementes
fixas. `GeneticAlgorithmVsBaselinesTest` compara o AG contra seis baselines em oito instâncias
sintéticas, e `CorrelationAggregateTest.RealDataRegression` fixa a trajetória de correlação
−0,909 → −0,460 → −0,106. **Ambos passam sem alteração.**

### Cobertura do próprio construtor

`EvolutionContextBuilderTest` — 9 testes em três grupos: obrigatórios exigidos (inclusive o caso de
vários ausentes reportados juntos), opcionais omissíveis, e campos derivados calculados na
construção. Mais um que verifica que o **construtor canônico de doze argumentos** — público por
exigência da linguagem, e pior que a fábrica removida — não é usado em lugar nenhum fora do próprio
construtor passo a passo.

Esse último teste falhou na primeira execução acusando **a si mesmo**, porque cita o trecho procurado
na própria mensagem de falha. A exclusão está explícita no código, com o motivo.

### Números

| | Etapa 03b | **Etapa 03c** |
|---|---:|---:|
| Testes | 148 | **157** |
| Instruções | 81,4% | **81,8%** |
| Ramos | 60,0% | **60,9%** |
| Linhas | 82,2% | **82,7%** |

**ADR-0004** registra a decisão, com três alternativas descartadas — manter `of()` como atalho,
agrupar parâmetros em objetos intermediários e usar Lombok `@Builder` — e as consequências aceitas,
inclusive a troca de erro de compilação por erro de execução.

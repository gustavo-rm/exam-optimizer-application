# Robustez do AG: Reprodutibilidade, Variância, Hiperparâmetros e Casos de Borda

**Documentos anteriores:** [`00-diagnostico.md`](./00-diagnostico.md) ·
[`01-auditoria-fitness.md`](./01-auditoria-fitness.md) · [`02-formulacao.md`](./02-formulacao.md) ·
[`03-validacao.md`](./03-validacao.md)
**Este documento:** robustez do algoritmo em si, não da formulação da fitness.
**Código:** `benchmarks/java/…/robustness/` (estudos) · `src/test/java/…/ga/GaEdgeCasesTest.java` (testes)
**Dados brutos:** `benchmarks/results/robustez.csv`
**Correções aplicadas nesta etapa:** sim — apenas as de baixo risco. Pendências estruturais em §7.

---

## 1. Veredito

| Pergunta | Antes | Depois das correções |
|---|---|---|
| Mesma seed reproduz o mesmo plano? | **2 de 8 instâncias** | **8 de 8** |
| Variância de *fitness* entre 12 seeds | CV 0,000%–0,869% | CV 0,000%–0,717% |
| Variância do *plano* entre 12 seeds | até **12 planos distintos de 12**, 22 dias movidos | inalterada — é estocasticidade legítima |
| O AG é sensível a hiperparâmetros? | Depende da instância: **irrelevante** nas fáceis, **decisivo** na difícil | — |
| Casos de borda geram erro claro? | Não: erro interno do JDK, cronograma vazio silencioso, horas negativas aceitas | Corrigidos os 3 de baixo risco |

**O achado principal desta etapa é que a variância de fitness escondia a variância do plano.**
Em `03-validacao.md` eu reportei que o AG varia pouco entre execuções — medindo *fitness*. Medindo o
**plano**, a conclusão se inverte: em 4 das 8 instâncias, **12 seeds produzem 12 planos diferentes**,
com até 23 dias de estudo realocados (14,4% do orçamento). Dois alunos com entradas idênticas
recebem cronogramas materialmente diferentes que pontuam quase igual. A fitness é um platô, e o AG
para em pontos arbitrários dele.

**O achado secundário é que a trava de seed do sistema nunca funcionou.** `RandomProvider` documenta
que existe para *"lock the seed during tests"*, e `TESTING_STRATEGY_ARCHITECTURE.md` §2 promete
execução determinística com seed fixa. Nenhuma das duas coisas era verdade, por duas causas
independentes descritas em §3.

---

## 2. Método

- **Instâncias:** as mesmas 8 de `03-validacao.md`, sem alteração.
- **Seeds:** 12 (`101, 202, …, 1212`), acima das 10 pedidas.
- **Comparação de planos:** assinatura canônica ordenada por nome de disciplina
  (`PlanSignature`), imune à ordem de iteração do mapa. Distância medida em **dias movidos** —
  metade da distância L1, já que cada dia retirado de uma disciplina é somado a outra.
- **Varredura de hiperparâmetros:** grade pequena em torno do valor de produção, um eixo por vez.
  Qualidade expressa como **% do ótimo exato** (o guloso marginal de `03`), o que a torna comparável
  entre instâncias — a fitness bruta não é (`01` §3.1).
- **Correções:** aplicadas e re-medidas. Todos os números de "depois" nesta página são pós-correção.

---

## 3. Determinismo: duas causas, ambas corrigidas

### 3.1 Estado inicial

Rodando o AG duas vezes com a **mesma seed**, sobre a mesma instância:

| Instância | plano idêntico? | dias movidos entre as duas execuções |
|---|---|---|
| `I1-pequeno-folgado` | sim | 0 |
| `I2-medio-tipico` | **não** | 1 |
| `I3-grande-apertado` | **não** | 4 |
| `I4-pesos-extremos` | **não** | 9 |
| `I5-pesos-balanceados` | **não** | 2 |
| `I6-horizonte-curto` | sim | 0 |
| `I7-horizonte-longo` | **não** | 4 |
| `I8-escala` | **não** | 1 |

**Reprodutível em 2 de 8.** As duas que passavam são as instâncias em que o ótimo é único e
trivialmente alcançável — não reprodutibilidade, apenas convergência forçada.

### 3.2 Causa 1 — geradores não semeáveis na mutação

Já registrada como R18 em `00-diagnostico.md`: `AbstractMutationStrategy` decidia mutar com
`Math.random()` e sorteava disciplinas com `ThreadLocalRandom`; `CreepMutation` sorteava o passo com
`ThreadLocalRandom`. Nenhum dos dois aceita seed.

Corrigir apenas isso levou o resultado de **2/8 para 5/8** — melhora real, mas insuficiente, o que
revelou a segunda causa.

### 3.3 Causa 2 — a trava de seed nunca alcançava os operadores

Esta é nova, e é a mais séria das duas. Todos os operadores usavam o padrão:

```java
public class TournamentSelection implements SelectionStrategy {
    private final Random random = RandomProvider.getInstance();   // capturado na CONSTRUÇÃO
```

Os operadores são beans do Spring, construídos uma única vez na subida da aplicação. `RandomProvider`
só é semeado depois. Logo **o campo guarda para sempre o `SecureRandom` original, e
`RandomProvider.setInstance(new Random(seed))` não tem efeito algum sobre eles.**

Oito classes carregavam esse padrão, cinco delas no caminho ativo: `TournamentSelection`,
`HybridCrossover`, `RepairingCrossover`, `WeightedAverageCrossover` e `StudyPlanFactory`.

Isto significa que o mecanismo de reprodutibilidade descrito em `RandomProvider` (*"providing a
centralized point to lock the seed during tests"*) e a estratégia de testes determinísticos de
`TESTING_STRATEGY_ARCHITECTURE.md` §2 **nunca funcionaram**. Não havia teste que verificasse,
porque não havia teste de determinismo.

### 3.4 Estado final

Com as duas causas corrigidas — RNG consultado no ponto de uso, não capturado na construção:

| Instância | plano idêntico? | dias movidos |
|---|---|---|
| todas as 8 | **sim** | **0** |

**Reprodutível em 8 de 8.** Verificado também por teste automatizado
(`GaEdgeCasesTest.Determinism.sameSeedProducesIdenticalPlan`), acompanhado do teste complementar
`differentSeedsProduceDifferentPlans`, que garante que a correção não colapsou a busca numa
constante.

---

## 4. Variância entre seeds

12 seeds, pós-correção. `planos distintos` conta assinaturas únicas entre as 12 execuções;
`dias movidos` é a média e o máximo sobre todos os 66 pares de seeds.

| Instância | fitness média | desvio | CV% | amplitude% | planos distintos | dias movidos (méd.) | dias movidos (máx.) | % do orçamento (máx.) |
|---|---|---|---|---|---|---|---|---|
| `I1-pequeno-folgado` | 1462,59 | 0,000 | 0,000% | 0,000% | **1/12** | 0,0 | 0,0 | 0,0% |
| `I2-medio-tipico` | 1390,04 | 0,054 | 0,004% | 0,013% | 6/12 | 1,0 | 3,0 | 2,1% |
| `I3-grande-apertado` | 2915,17 | 0,860 | 0,029% | 0,097% | **12/12** | 5,4 | 9,0 | 4,5% |
| `I4-pesos-extremos` | 16960,77 | 121,561 | **0,717%** | **2,487%** | **12/12** | 13,2 | **23,0** | **14,4%** |
| `I5-pesos-balanceados` | 1854,01 | 0,214 | 0,012% | 0,033% | **12/12** | 2,3 | 4,0 | 2,5% |
| `I6-horizonte-curto` | 1085,75 | 0,001 | 0,000% | 0,000% | 2/12 | 0,2 | 1,0 | 1,0% |
| `I7-horizonte-longo` | 2418,13 | 0,123 | 0,005% | 0,017% | **12/12** | 3,1 | 6,0 | 2,3% |
| `I8-escala` | 3911,45 | 0,019 | 0,000% | 0,001% | 4/12 | 0,7 | 2,0 | 0,7% |

### 4.1 Leitura

**A variância de fitness é baixa; a do plano não é.** Em `I3`, `I4`, `I5` e `I7`, nenhuma das 12
seeds produz o mesmo plano que outra. Em `I5` a fitness varia 0,033% enquanto os planos diferem em
até 4 dias — **a fitness é essencialmente plana sobre um conjunto grande de alocações distintas**, e
o AG para em um ponto arbitrário desse platô conforme a seed.

Isso é consistente com a análise de `01` §3.4: a fitness é separável e côncava, e perto do ótimo o
gradiente é quase nulo em muitas direções. Muitas alocações diferentes pontuam praticamente igual.

**Por que isso importa para o produto, e não só para a engenharia:**

1. **Consistência percebida.** Dois alunos com o mesmo edital e o mesmo perfil recebem planos
   visivelmente diferentes. Um replanejamento semanal — que o README anuncia como
   *"re-planning from scratch"* — devolve um plano diferente mesmo sem mudança de entrada.
2. **Suporte e depuração.** Sem reprodutibilidade era impossível reproduzir a queixa de um aluno.
   Isso agora está resolvido *se* a seed for registrada (ver pendência **P3**).
3. **A variância é maior justamente onde o AG já é fraco.** `I4` — a instância em que o AG perde do
   guloso por 6,2% (`03` §4.2) — é também a de maior variância: 14,4% do orçamento entre seeds.
   Longe de convergir, o AG é ao mesmo tempo pior e mais instável.

**Ressalva honesta:** variância entre seeds diferentes **não é um defeito** de um algoritmo
estocástico; é o comportamento esperado. O que se documenta aqui é a *magnitude* — 14,4% do
orçamento é grande o bastante para o aluno notar — e o fato de que ela não aparece na fitness, que é
a única métrica que o sistema registra hoje.

---

## 5. Sensibilidade a hiperparâmetros

Grade pequena, um eixo por vez, demais eixos no valor de produção
(população 50, gerações 100, mutação 0,05). Qualidade = fitness / fitness do ótimo exato.
Média de 3 seeds.

### `I2-medio-tipico` — ótimo exato 1390,1

| Eixo | Valor | Qualidade | Tempo (ms) |
|---|---|---|---|
| população | 10 → 200 | 99,939% → 100,000% | 10 → 29 |
| gerações | 10 → 500 | 99,797% → 100,000% | 1 → 48 |
| taxa de mutação | 0,01 → 0,50 | 99,996% → 100,000% | 9 → 10 |

### `I4-pesos-extremos` — ótimo exato 18022,8

| Eixo | Valor | Qualidade | Tempo (ms) |
|---|---|---|---|
| **população** | 10 | 82,816% | 4 |
| | 25 | 90,123% | 6 |
| | **50 (produção)** | **93,936%** | 9 |
| | 100 | 95,858% | 14 |
| | 200 | 96,582% | 26 |
| **gerações** | 10 | 81,082% | 1 |
| | 50 | 88,571% | 5 |
| | **100 (produção)** | **93,936%** | 9 |
| | 250 | 98,915% | 23 |
| | 500 | **99,983%** | 46 |
| **taxa de mutação** | 0,01 | 87,371% | 9 |
| | **0,05 (produção)** | **93,936%** | 9 |
| | 0,15 | 97,378% | 9 |
| | 0,30 | 98,921% | 10 |
| | 0,50 | **99,689%** | **10** |

### `I8-escala` — ótimo exato 3911,5

| Eixo | Valor | Qualidade | Tempo (ms) |
|---|---|---|---|
| população | 10 → 200 | 98,586% → 99,982% | 7 → 66 |
| gerações | 10 → 500 | 98,791% → 99,994% | 2 → 93 |
| taxa de mutação | 0,01 → 0,50 | 99,399% → 99,995% | 18 → 19 |

### 5.1 Leitura

**Em instâncias de peso moderado (`I2`, `I8`) o AG é insensível a tudo.** Mesmo com população 10 e
10 gerações fica acima de 98,5% do ótimo. Os valores de produção estão numa região totalmente plana:
gastar 4× mais tempo compra menos de 0,2%. Isto reforça a conclusão de `03` §6.2 — nessas instâncias
o motor evolutivo está sendo pago para não fazer diferença.

**Em `I4` o AG é fortemente sensível, e o eixo mais barato é a taxa de mutação.** Elevar 0,05 → 0,50
leva a qualidade de **93,9% para 99,7%** com **custo de tempo nulo** (9 ms → 10 ms). Para o mesmo
ganho via gerações seria preciso ir de 100 para 500, quintuplicando o tempo. Via população, 200
ainda não chega lá (96,6%).

Isso confirma mecanicamente o diagnóstico de `03` §4.2: o problema de `I4` é **alcance de operador**.
O ótimo concentra 80,6% do orçamento numa disciplina; `CreepMutation` move ±3 dias por vez; com
mutação a 5% a população quase não se desloca. Mais mutação é literalmente mais deslocamento.

**Não alterei a taxa de mutação.** É uma mudança de qualidade, não de robustez, e alteraria o plano
de todo usuário. Fica como pendência **P4**, com os dados acima.

---

## 6. Efeito das correções sobre o benchmark da etapa 03

As correções trocam a fonte de aleatoriedade, então os números de `03-validacao.md` se deslocam.
Nenhuma conclusão muda.

| Métrica | `03` (antes) | Agora |
|---|---|---|
| AG vs guloso, `I4-pesos-extremos` | −5,42% | **−6,18%** |
| AG vs guloso, demais instâncias | +0,01% a +0,44% | +0,01% a +0,44% |
| Gap até o ótimo exato (7 instâncias) | ≤ 0,06% | ≤ 0,09% |
| Pior ruído entre repetições, instâncias cobertas pelo teste | 0,111% | **0,286%** |
| Limiar do teste de regressão | 2% (18× o ruído) | 2% (**7×** o ruído) — segue válido |

O teste `GeneticAlgorithmVsBaselinesTest` continua verde, incluindo
`knownDeficiencyListIsStillAccurate`: `I4` segue sendo a única instância deficiente.
O Javadoc do teste foi atualizado com os números atuais.

---

## 7. Casos de borda

### 7.1 Comportamento medido e o que foi feito

| # | Caso | Comportamento antes | Ação |
|---|---|---|---|
| 1 | Orçamento < piso de dias mínimos | `IllegalArgumentException` com mensagem clara, de `StudyPlanFactory` → HTTP 400 | **Mantido**, testado. Ver P6 sobre 400 vs 422 |
| 2 | Exame sem disciplinas | `IllegalArgumentException: bound must be positive` — mensagem crua do JDK, vinda de `Random.nextInt(0)` | **Corrigido (C2)** |
| 3 | Orçamento negativo | Aceito silenciosamente | **Corrigido (C2)** |
| 4 | Disponibilidade semanal negativa | Aceita; propaga fator de redução negativo → cronograma vazio rotulado apenas como déficit de tempo | **Corrigido (C3)** |
| 5 | Disponibilidade semanal zero | Plano gerado, cronograma com **0 dias**, status `WARNING_TIME_DEFICIT` | **Não corrigido** — pendência P1 |
| 6 | Data da prova no passado | Cronograma com 0 dias, `availableHours = 0`, mesmo status genérico | **Não corrigido** — pendência P2 |
| 7 | Uma única disciplina | Funciona; mutação devolve o indivíduo intacto | **Mantido**, testado + guarda C4 |
| 8 | Muitas disciplinas | Escala bem em tempo, mas o **piso de dias mínimos** inviabiliza o problema | **Não corrigido** — pendência P5 |

### 7.2 Escalabilidade

Disciplinas de peso igual, população 50 × 100 gerações:

| Disciplinas | Piso de dias mínimos | Orçamento necessário | Tempo |
|---|---|---|---|
| 10 | 150 | 188 | 48 ms |
| 50 | 750 | 938 | 55 ms |
| 100 | 1 500 | 1 875 | 134 ms |
| 250 | 3 750 | 4 688 | 167 ms |
| 500 | 7 500 | 9 375 | 247 ms |
| 1 000 | 15 000 | 18 750 | 508 ms |

**O tempo não é o problema** — 1 000 disciplinas em meio segundo. **O piso é.**
`BaselineCalculator` normaliza a dificuldade percebida pelo máximo, então disciplinas de peso igual
recebem todas `MAX_MINIMUM_DAYS = 15`, e o piso vale `15 × n`. Como `GaConfigDto` limita
`totalStudyDays` a 365, **qualquer exame com 25 ou mais disciplinas de peso igual é infactível por
construção**, para qualquer orçamento que a API aceite. Fixado em teste
(`minimumDaysFloorGrowsWithSubjectCount`).

Com pesos variados o piso cai bastante — `I3` (25 disciplinas) e `I8` (40) funcionam com orçamentos
de 200 e 280 dias. O caso ruim é o de pesos homogêneos, que não é exótico: um edital com dez
disciplinas de peso semelhante já consome 150 dias só de piso.

---

## 8. Correções aplicadas (baixo risco)

Todas em `src/main`, todas cobertas por teste.

### C1 — RNG semeável e consultado no ponto de uso

- `AbstractMutationStrategy`: `Math.random()` → `RandomProvider.getInstance().nextDouble()`;
  `ThreadLocalRandom` → `RandomProvider`.
- `CreepMutation`: `ThreadLocalRandom` → `RandomProvider`.
- **`TournamentSelection`, `HybridCrossover`, `RepairingCrossover`, `WeightedAverageCrossover`,
  `StudyPlanFactory`, `DayBoundaryCrossover`, `MethodologyMutation`,
  `RandomizedInterleavedStrategy`**: removido o campo `private final Random random =
  RandomProvider.getInstance()` e substituído por consulta ao provider no ponto de uso.

  Essa é a correção que fez a diferença: enquanto a instância era capturada na construção, nenhum
  seed posterior alcançava os operadores.

**Risco:** baixo. Mesma distribuição estatística; muda apenas de onde vêm os bits. A mutação roda
numa única thread dentro do laço de evolução, então o gerador compartilhado não introduz contenção.
**Efeito medido:** determinismo de 2/8 → 8/8; deslocamento de ≤ 0,8 pp nos resultados do benchmark.

### C2 — Erros claros em `StudyPlanFactory`

Guardas explícitas para exame sem disciplinas (antes: `bound must be positive`) e para orçamento
negativo (antes: aceito).

**Risco:** nulo. Mesmo tipo de exceção, logo mesmo status HTTP; apenas a mensagem melhora.

### C3 — `StudentProfile` rejeita disponibilidade negativa

Horas negativas não têm significado e produziam nonsense silencioso. `StudentProfileDto` já validava
`@Min(0)` na fronteira da API; a guarda fecha o mesmo buraco para os demais chamadores.

**Risco:** baixo. Nenhum chamador legítimo passa horas negativas.

### C4 — `randomSubjectExcluding` falha em vez de travar

Com menos de duas disciplinas, o laço de rejeição girava para sempre. Hoje é inalcançável porque
`mutate` já retorna antes, mas a armadilha ficava armada para o próximo chamador.

**Risco:** nulo. Caminho hoje inalcançável.

---

## 9. Pendências para decisão humana

Não implementadas: mudam contrato de API, saída de todos os usuários, ou exigem refatoração
estrutural.

| # | Pendência | Por que não foi feita | Proposta |
|---|---|---|---|
| **P1** | Disponibilidade semanal zero devolve cronograma vazio com aviso genérico | Transformar em erro muda o contrato (200 → 4xx) | Ou um `ScheduleStatus` específico (`NO_TIME_AVAILABLE`), ou `DomainException` → 422. A segunda é mais clara; a primeira não quebra clientes |
| **P2** | Data da prova no passado produz o mesmo cronograma vazio | Idem P1 | O DTO já valida `@Future`; falta a mesma guarda no domínio, para os demais chamadores |
| **P3** | A seed usada não é registrada nem devolvida | `OptimizationResult` é parte do contrato da API | Incluir a seed no resultado e no log. Sem isso, o determinismo recém-conquistado não serve para reproduzir a queixa de um aluno |
| **P4** | Taxa de mutação 0,05 mal calibrada para instâncias concentradas | Muda o plano de todo usuário; é qualidade, não robustez | Elevar para 0,15–0,30. Dados em §5: em `I4` leva 93,9% → 97,4–98,9% a custo de tempo nulo. Deveria ser validado com o harness de `03` antes de subir |
| **P5** | O piso `15 × n` inviabiliza exames com muitas disciplinas de peso homogêneo | Mexer em `BaselineCalculator` altera todos os planos | O piso deveria escalar com o orçamento disponível, não ser absoluto por disciplina. Decisão de produto sobre quanto do orçamento a restrição pode consumir |
| **P6** | Inviabilidade sai como 400 (`IllegalArgumentException`), não 422 | Mudança de status HTTP | Semanticamente é violação de regra de negócio: `DomainException` → 422 é mais correto. Baixo impacto, mas é contrato |
| **P7** | `LocalDate.now()` dentro da lógica | Injetar `Clock` altera construtores e wiring do Spring | `StudyOptimizerService.prepareContext` e `DynamicStudyPlannerService` leem o relógio. O cronograma gerado depende do dia da execução, então a saída de negócio **continua não reprodutível** apesar de C1 |

> **P7 merece destaque.** As correções desta etapa tornam o *plano macro* reprodutível, mas o
> *cronograma* — o que o aluno de fato recebe — ainda depende de `LocalDate.now()`. Reprodutibilidade
> ponta a ponta exige P7. O harness contorna isso usando uma data-âncora fixa, opção que o código de
> produção não oferece.

---

## 10. Testes adicionados

`src/test/java/…/ga/GaEdgeCasesTest.java` — 14 testes:

| Grupo | Testes |
|---|---|
| Entradas inviáveis | orçamento abaixo do piso; exame sem disciplinas; orçamento negativo; disponibilidade negativa |
| Entradas degeneradas | disciplina única; mutação com disciplina única; disponibilidade zero (documenta P1); prova no passado (documenta P2) |
| Determinismo | mesma seed → plano idêntico; seeds diferentes → planos diferentes |
| Escala e invariantes | 200 disciplinas dentro do orçamento de tempo; crescimento do piso (documenta P5); viabilidade do plano em 5 seeds; configuração mínima do AG não explode |

Os testes de P1 e P2 fixam o comportamento **atual**, insatisfatório, apontando para a pendência.
Isso mantém relatório e código sincronizados: se alguém corrigir P1, o teste falha e obriga a
atualizar esta página.

Suíte completa: **47 testes, 0 falhas** (33 antes desta etapa + 14).

---

## 11. Ameaças à validade

1. **As mesmas 8 instâncias sintéticas de `03`.** Herdam as ressalvas de `03` §9 — em especial, a
   dispersão de peso de `I4`, embora aceita pela API, pode não ocorrer em editais reais.
2. **12 seeds é pouco para caracterizar uma distribuição.** Basta para mostrar que a dispersão de
   planos é grande; não basta para afirmar sua forma.
3. **A varredura é unidimensional.** Um eixo por vez, com os demais em produção. Interações entre
   população e mutação não foram exploradas.
4. **Tempos são indicativos.** Uma máquina, sem JMH, sem aquecimento de JIT. Servem para ordem de
   grandeza e comparação relativa, não como medição de desempenho.
5. **O determinismo foi verificado dentro de uma mesma JVM.** Não testei entre versões de JDK, onde
   ordem de iteração de `HashMap` poderia divergir. Os operadores continuam iterando sobre
   `HashMap`; hoje isso é estável porque a ordem depende só das chaves e da capacidade, mas não é
   uma garantia da linguagem. Ordenar explicitamente teria custo em laço quente e não foi feito —
   nenhuma evidência de que seja necessário hoje.

---

## 12. Conclusão

O AG **é robusto no que se mede por fitness e frágil no que se mede por plano**. As correções desta
etapa resolvem o problema estrutural mais sério — a impossibilidade de reproduzir uma execução — e
revelam que a promessa de determinismo existia na documentação havia meses sem nunca ter funcionado,
por falta de um teste que a verificasse.

O que **não** foi resolvido, e que a medição deixa explícito:

- **A dispersão entre seeds é grande onde importa.** Até 14,4% do orçamento realocado entre
  execuções, invisível na fitness. É consequência de a fitness ser um platô perto do ótimo
  (`01` §3.4) — sintoma da formulação, não do algoritmo.
- **A reprodutibilidade ainda não é ponta a ponta** enquanto o cronograma depender do relógio (P7).
- **A taxa de mutação está mal calibrada** para instâncias concentradas, com correção barata e
  medida disponível (P4).

Nada disso muda a ordem de prioridade estabelecida em `02` e reforçada em `03`: a função objetivo
continua sendo o gargalo. Mas a Fase 0 daquele plano — reprodutibilidade, sem a qual nenhuma análise
de sensibilidade distingue efeito de ruído — **está concluída**, e o caminho para as fases seguintes
está aberto.

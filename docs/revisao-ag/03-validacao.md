# Validação Empírica: AG de Produção contra Baselines Simples

**Documentos anteriores:** [`00-diagnostico.md`](./00-diagnostico.md) ·
[`01-auditoria-fitness.md`](./01-auditoria-fitness.md) · [`02-formulacao.md`](./02-formulacao.md)
**Este documento:** primeira comparação empírica do motor contra alternativas mais simples.
**Código:** `benchmarks/java/…` (módulo isolado, escopo de teste — nunca vai para o artefato de produção)
**Dados brutos:** `benchmarks/results/resultados.csv`
**Data:** 2026-08-30 · **Lógica do AG de produção: não alterada.**

> ## ⚠ Nota de correção — 2026-08-31 (etapa 07)
>
> **Duas correções afetam a §5 deste documento. Elas não invalidam a conclusão, mas invalidam dois
> números.** Detalhamento completo em
> [`07-correcao-metrica-e-ausubel.md`](./07-correcao-metrica-e-ausubel.md).
>
> **1. A linha "Agregado (48 observações) = −0,654" era estatisticamente inválida.** Ela empilhava
> as 48 observações (8 instâncias × 6 planejadores) num único Spearman, ranqueando valores de
> fitness entre instâncias que `benchmarks/…/metric/PlanMetrics.java` documenta como **não
> comparáveis entre si**. O coeficiente resultante mede sobretudo qual instância é fácil. O método
> canônico passa a ser a transformada z de Fisher sobre as correlações **por instância**
> (`benchmarks/…/metric/CorrelationAggregate.java`), e por ele o valor correto desta etapa é
> **−0,909 [IC95% −0,971; −0,734], n = 4 instâncias**. A linha foi substituída abaixo.
>
> **2. Os valores por instância são médias de uma medição não reprodutível.** Este documento foi
> medido antes da correção de determinismo da etapa 04, quando a mutação sorteava de
> `Math.random()`. Dez reexecuções do commit `07c99da` mostram `I3` = −0,937 ± 0,013 e
> `I7` = −0,847 ± 0,022 — as três casas decimais originais sugeriam uma precisão que a medição não
> tinha. A tabela abaixo passa a trazer média ± desvio-padrão sobre 10 execuções, e **esta é a
> baseline "antes" oficial** para qualquer comparação futura.
>
> A conclusão central da §5 — *a fitness estava fortemente anti-correlacionada com a retenção* —
> **sai reforçada**: pelo método correto o agregado é −0,909, mais negativo que o −0,654 publicado.

O `00-diagnostico.md` §5.2 registrou explicitamente *"comparação empírica contra alternativa mais
simples: nenhum encontrado"*. Este documento fecha essa lacuna.

> **Nota posterior (etapa 04).** As tabelas abaixo descrevem o estado **antes** das correções de
> reprodutibilidade de [`04-robustez.md`](./04-robustez.md). Depois delas os números se deslocam
> ligeiramente — a fonte de aleatoriedade mudou —, sem alterar nenhuma conclusão: o déficit de
> `I4-pesos-extremos` vai de −5,42% para −6,18%, e o pior ruído entre repetições nas instâncias
> cobertas pelo teste vai de 0,111% para 0,286%. O limiar de 2% do teste segue válido, com 7× de
> margem. Os valores pós-correção estão em `04-robustez.md` §6.

---

## 1. Veredito

| Pergunta | Resposta medida |
|---|---|
| O AG supera o guloso por prioridade? | **Sim, por 0,01% a 0,44%** em 7 de 8 instâncias — e **perde por 5,4%** na oitava |
| O AG supera a divisão uniforme e o aleatório? | **Sim, sempre** — de +0,14% a +27% |
| O AG alcança o ótimo da própria fitness? | **Sim, em 7 de 8** (gap ≤ 0,06%); na oitava fica **5,7% abaixo** |
| Quanto custa? | **17–371 ms** contra **&lt; 1 ms** do guloso exato — 20× a 400× mais lento |
| O AG melhora sobre sua própria população inicial? | **+0,08% a +4,1%** (mediana ~1,7%). Toda a maquinaria evolutiva agrega isso |
| Ganhar fitness melhora o resultado de negócio? | **Não. É o contrário.** Spearman entre fitness e retenção: **−0,83 a −0,94** nas 4 instâncias onde a retenção não satura |

**A conclusão que domina todas as outras não é sobre o AG, é sobre a função objetivo.** Nas
instâncias em que a métrica de negócio consegue discriminar, **maximizar a fitness atual piora
sistematicamente a retenção prevista na data da prova**. O AG está bem construído o suficiente para
chegar quase sempre ao ótimo do que lhe pediram — e o que lhe pediram está anti-correlacionado com
o que o produto vende. Otimizar melhor essa fitness produziria planos piores, não melhores.

Isso reordena a prioridade estabelecida em `02-formulacao.md`: corrigir a fitness (Fase 1) não é
apenas pré-requisito da formulação multiobjetivo — é o que impede o motor de piorar o produto
quanto melhor ele funciona.

---

## 2. O que foi construído

### 2.1 Isolamento em relação à produção

```
benchmarks/java/com/ia/project/dynamicstudyplanner/benchmark/
├── instance/    BenchmarkInstance, InstanceLibrary          (8 instâncias determinísticas)
├── strategy/    PlanningStrategy, Allocations,
│                RandomBaseline, GreedyPriorityBaseline, UniformSplitBaseline,   (os 3 pedidos)
│                BestOfRandomBaseline, MarginalGainOptimum,                      (2 controles)
│                ProductionGeneticAlgorithm                                       (sistema sob teste)
├── metric/      PlanMetrics, MetricsCalculator
├── harness/     BenchmarkHarness, StrategyOutcome, BenchmarkMain
└── GeneticAlgorithmVsBaselinesTest.java                     (teste de regressão)
```

O diretório é anexado como **test source root** via `build-helper-maven-plugin`
(`pom.xml`). Consequências:

- O código de benchmark é compilado em escopo de teste, então **nada disto entra no artefato
  servido em produção** — a separação é estrutural, não uma convenção. Verificado:

  ```
  classes de benchmark em target/classes      (entra no jar):  0
  classes de benchmark em target/test-classes (escopo teste): 18
  ```

- Ao mesmo tempo, `./mvnw test` compila e executa o benchmark, então o teste de regressão roda no
  mesmo comando que o resto da suíte.
- A única alteração em `pom.xml` é a adição desse plugin. **Nenhum arquivo sob `src/main` foi
  modificado** (verificável por `git status --short src/main`, vazio).

### 2.2 Como rodar

```bash
# suíte completa, incluindo o teste de regressão do benchmark
./mvnw test

# relatório completo (8 instâncias, 7 repetições) → CSV + tabelas Markdown no stdout
./mvnw -q test-compile
java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
     com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain
```

### 2.3 O que garante que a comparação é justa

- **Mesmo avaliador.** Todos os planejadores produzem um `StudyPlan` pontuado pelo mesmo
  `FitnessEvaluator` de produção, montado com exatamente os beans que o Spring injeta
  (`BenchmarkHarness.productionFitnessEvaluator`).
- **Mesmo contexto.** `BenchmarkHarness.buildContext` reproduz `StudyOptimizerService.prepareContext`
  campo a campo, incluindo o `RetentionProfile` vazio e o `EngagementProfile.baseline()` que aquele
  método injeta — os mesmos *placeholders* que mantêm dois componentes da fitness neutros
  (`01` §2.3.4, §2.4.4). Um benchmark que fornecesse perfis mais ricos não estaria medindo produção.
- **Sistema sob teste é o real.** `ProductionGeneticAlgorithm` chama
  `StudyOptimizerService.optimize()`, não uma reimplementação. Hiperparâmetros, operadores e
  configuração vêm de `DefaultGeneticAlgorithmFactory` como está em produção.
- **Viabilidade auditada.** O harness rejeita, com exceção, qualquer plano que fure o orçamento ou o
  piso de dias mínimos — um planejador que trapaceasse inflaria a fitness e invalidaria a tabela.
- **Métricas de negócio com classes de produção.** `StudyScheduleGenerator`,
  `CognitiveLoadCalculator` e `HybridRetentionEngine`, sem subclasses nem modificações.

---

## 3. Instâncias

Oito instâncias sintéticas determinísticas (seed fixa por instância; a data-âncora é constante,
não `LocalDate.now()`, para que as métricas derivadas do cronograma sejam reprodutíveis).

| ID | Disciplinas | Horizonte (dias) | h/semana | Orçamento (dias) | Pop × Ger | O que testa |
|---|---|---|---|---|---|---|
| `I1-pequeno-folgado` | 6 | 120 | 21 | 80 | 50 × 100 | caso fácil, tempo sobrando |
| `I2-medio-tipico` | 12 | 180 | 25 | 140 | 50 × 100 | perfil típico |
| `I3-grande-apertado` | 25 | 150 | 14 | 200 | 50 × 100 | déficit de tempo severo |
| `I4-pesos-extremos` | 10 | 200 | 30 | 160 | 50 × 100 | dispersão de peso de 3 ordens de grandeza |
| `I5-pesos-balanceados` | 10 | 200 | 30 | 160 | 50 × 100 | controle de I4: pesos ~2:1 |
| `I6-horizonte-curto` | 8 | 40 | 31 | 105 | 50 × 100 | reta final |
| `I7-horizonte-longo` | 15 | 400 | 10 | 260 | 50 × 100 | maratona, esquecimento pesa |
| `I8-escala` | 40 | 300 | 35 | 280 | 200 × 400 | custo na configuração pesada |

I4 e I5 são pares controlados: mesma escala, mesma disponibilidade, mesmo orçamento, **dispersão de
peso oposta**. É esse par que isola o efeito previsto pela auditoria (`01` §2.1.4).

> **Achado colateral, no próprio processo de montar as instâncias.** Em `I6`, o piso de dias mínimos
> do `BaselineCalculator` exigiu **84 dias de estudo para um horizonte de 40 dias** — com 8
> disciplinas e `MAX_MINIMUM_DAYS = 15`, a restrição sozinha pede mais do que o calendário comporta.
> Foi preciso elevar o orçamento de 50 para 105 dias apenas para que `StudyPlanFactory` não lançasse
> `IllegalArgumentException`. Isso confirma na prática o risco R2 do `01` §2.2.2: em concursos de
> reta final, a restrição de mínimos pode tornar o problema infactível sozinha.

---

## 4. Resultados

### 4.1 Fitness e tempo

Média de 7 repetições para os métodos estocásticos; os determinísticos rodam uma vez.
Maior fitness é melhor. **A fitness não é comparável entre instâncias** (não tem unidade
normalizada — `01` §3.1); apenas entre colunas da mesma linha.

| Instância | AG produção | Guloso prioridade | Ótimo exato | Melhor de N aleat. | Uniforme | Aleatório | t(AG) ms | t(ótimo) ms |
|---|---|---|---|---|---|---|---|---|
| `I1-pequeno-folgado` | 1462,6 | 1462,3 | **1462,6** | 1458,5 | 1453,8 | 1446,3 | 45 | 1 |
| `I2-medio-tipico` | 1390,0 | 1388,0 | **1390,1** | 1380,7 | 1373,1 | 1367,4 | 49 | 0 |
| `I3-grande-apertado` | 2915,3 | 2907,0 | **2917,1** | 2873,5 | 2857,6 | 2850,6 | 27 | 0 |
| `I4-pesos-extremos` | 16997,1 | 17971,2 | **18022,8** | 14509,4 | 13523,1 | 13382,7 | 42 | 0 |
| `I5-pesos-balanceados` | 1853,9 | 1851,3 | **1854,3** | 1781,0 | 1751,7 | 1751,8 | 18 | 0 |
| `I6-horizonte-curto` | 1085,7 | 1085,7 | **1085,7** | 1084,9 | 1084,2 | 1083,1 | 17 | 0 |
| `I7-horizonte-longo` | 2418,1 | 2415,6 | **2418,3** | 2368,6 | 2348,6 | 2340,5 | 20 | 0 |
| `I8-escala` | 3911,5 | 3894,2 | **3911,5** | 3839,3 | 3814,3 | 3797,5 | 371 | 0 |

### 4.2 O AG relativo a cada baseline

Positivo = AG melhor.

| Instância | vs guloso prioridade | vs melhor de N aleat. | vs uniforme | vs aleatório | gap até o ótimo exato |
|---|---|---|---|---|---|
| `I1-pequeno-folgado` | +0,02% | +0,28% | +0,61% | +1,13% | 0,00% |
| `I2-medio-tipico` | +0,15% | +0,68% | +1,23% | +1,66% | 0,00% |
| `I3-grande-apertado` | +0,29% | +1,45% | +2,02% | +2,27% | 0,06% |
| `I4-pesos-extremos` | **−5,42%** | +17,14% | +25,69% | +27,01% | **5,69%** |
| `I5-pesos-balanceados` | +0,14% | +4,09% | +5,83% | +5,83% | 0,02% |
| `I6-horizonte-curto` | +0,01% | +0,08% | +0,14% | +0,24% | 0,00% |
| `I7-horizonte-longo` | +0,10% | +2,09% | +2,96% | +3,32% | 0,01% |
| `I8-escala` | +0,44% | +1,88% | +2,55% | +3,00% | 0,00% |

Três leituras:

**(a) O ótimo analítico previsto pela auditoria se confirma.** Em 7 de 8 instâncias o AG chega a
0,00–0,06% do guloso marginal — que a auditoria derivou como **exatamente ótimo** para a fitness
atual (`01` Apêndice A). O guloso o alcança em **menos de 1 ms**, contra 17–371 ms do AG. A previsão
teórica de `01` §3.4 ("o AG está aproximando estocasticamente um problema com solução em forma
fechada") está empiricamente verificada.

**(b) A degeneração winner-take-all se confirma, e é onde o AG falha.** I4 é a única instância em
que o AG perde, e perde feio. O mecanismo é mensurável na coluna `share top-1`:

| I4, ordenado por fitness | fitness | share da disciplina top-1 |
|---|---|---|
| Ótimo exato | 18022,8 | **80,6%** |
| Guloso prioridade | 17971,2 | 75,6% |
| **AG produção** | 16997,1 | **48,7%** |
| Melhor de N aleatórios | 14509,4 | 23,4% |
| Uniforme | 13523,1 | 17,5% |

O ótimo exige concentrar 80,6% do orçamento numa única disciplina. O AG chega a 48,7% e para.
A explicação é o passo dos operadores: `CreepMutation` move ±3 dias por vez
(`00` §4.3) a partir de uma população inicial difusa, e 100 gerações não bastam para atravessar
essa distância. **O AG não falha por acaso em I4 — ele falha por alcance de operador**, e I5
(mesma instância com pesos balanceados, ótimo pouco concentrado) confirma por contraste: ali o AG
fecha o gap para 0,02%.

**(c) O que a evolução em si agrega é pequeno.** A coluna "melhor de N aleatórios" é literalmente a
população inicial do AG: sortear `populationSize` planos e ficar com o melhor. A diferença entre
ela e o AG é o valor de **todas** as gerações de seleção, crossover, mutação, elitismo e
hipermutação. Em 5 das 8 instâncias esse valor é **inferior a 2,1%**; em I1 e I6, inferior a 0,3%.

### 4.3 Métricas de negócio

Três métricas interpretáveis, calculadas com classes de produção sobre o cronograma que o aluno
receberia:

- **% na janela de retenção** — fração das disciplinas cuja probabilidade de recall prevista na data
  da prova é ≥ 0,85, o `MANDATORY_REVIEW_THRESHOLD` do `HybridRetentionEngine`. É a operacionalização
  de "tópicos dentro da janela ideal de retenção" com o critério que o próprio sistema usa.
- **Dias com sobrecarga cognitiva** — dias cuja soma `horas × cognitiveLoad` excede o orçamento do
  `CognitiveLoadCalculator`. Medido num cronograma **sem** o podador `CognitiveLoadBalancingStrategy`,
  porque o podador zera a métrica para todo mundo por construção e esconderia a diferença.
- **Horas entregues / planejadas** — quanto do plano macro sobrevive ao agendamento tático.

| Instância | Estratégia | % na janela de retenção | Dias c/ sobrecarga | Horas entregues/planejadas | Share top-1 |
|---|---|---|---|---|---|
| `I3-grande-apertado` | AG produção | 72,0% | 98,9 | 0,74 | 11,2% |
| | Guloso prioridade | 72,0% | 93,0 | 0,74 | 10,5% |
| | Ótimo exato | 72,0% | 102,0 | 0,73 | 11,5% |
| | Uniforme | **80,0%** | 91,0 | 0,73 | 8,5% |
| | Aleatório | **80,6%** | 88,0 | 0,73 | 8,7% |
| `I4-pesos-extremos` | AG produção | 87,1% | 98,4 | 0,97 | 48,7% |
| | Guloso prioridade | 70,0% | 41,0 | 0,99 | 75,6% |
| | Ótimo exato | **50,0%** | 28,0 | 0,99 | 80,6% |
| | Uniforme | **100,0%** | 124,0 | 0,92 | 17,5% |
| | Aleatório | **100,0%** | 120,7 | 0,91 | 16,7% |
| `I7-horizonte-longo` | AG produção | 92,4% | 22,9 | 1,00 | 15,3% |
| | Guloso prioridade | 93,3% | 29,0 | 1,00 | 14,6% |
| | Ótimo exato | 93,3% | 26,0 | 1,00 | 15,8% |
| | Uniforme | **100,0%** | 34,0 | 1,00 | 9,6% |
| | Aleatório | **100,0%** | 33,6 | 1,00 | 10,0% |

Em I1, I2, I5 e I6 a retenção satura em 100% para todos os métodos — não discrimina.

---

## 5. O achado central: a fitness está anti-correlacionada com o negócio

Correlação de Spearman entre fitness e % na janela de retenção, sobre as 6 estratégias de cada
instância:

*(Tabela corrigida em 2026-08-31 — ver nota no topo. Média ± desvio-padrão sobre **10 execuções**
do commit `07c99da`, porque naquele commit o AG ainda não era reprodutível. Os valores originalmente
publicados — `I3` −0,941, `I7` −0,833 — estão dentro da faixa observada, mas eram pontos de uma única
execução.)*

| Instância | ρ (fitness × retenção) | Desvio-padrão | Faixa observada | Amplitude da retenção |
|---|---|---|---|---|
| `I3-grande-apertado` | **−0,937** | 0,013 | −0,941 a −0,899 | 8,6 pp |
| `I4-pesos-extremos` | **−0,941** | 0,000 | −0,941 | 50,0 pp |
| `I7-horizonte-longo` | **−0,847** | 0,022 | −0,878 a −0,833 | 7,6 pp |
| `I8-escala` | **−0,880** | 0,000 | −0,880 | 2,5 pp |
| I1, I2, I5, I6 | indefinida | — | — | 0,0 pp (saturada em 100%) |
| **Agregado (Fisher z, n = 4 instâncias)** | **−0,909** | — | IC95% −0,971 a −0,734 | — |

~~| **Agregado (48 observações)** | **−0,654** | — |~~ — **linha retirada: método inválido**, ver
nota de correção no topo.

**Nas quatro instâncias em que a métrica de negócio consegue discriminar, a correlação é fortemente
negativa. Em I4 a inversão é quase perfeita e monotônica:** o método com a maior fitness
(ótimo exato, 18022,8) entrega a **pior** retenção (50%), e os métodos com a menor fitness
(uniforme e aleatório, ~13500) entregam **100%**.

O mecanismo é direto e está nas colunas: a fitness `Σ ln(1+d_s)·I_s` premia concentração
(`01` §2.1.4); concentração significa que poucas disciplinas são tocadas com frequência e as demais
ficam sem revisão por longos períodos; disciplinas sem revisão decaem exponencialmente
(`R = e^{−t/S}`). A divisão uniforme toca todas as disciplinas regularmente, então nada decai.

**Consequência prática, e é desconfortável:** hoje, quanto melhor o otimizador for em maximizar a
fitness, pior será o plano segundo a métrica de retenção que o próprio sistema define. Um AG mais
potente, mais gerações, ou operadores com maior alcance — qualquer melhoria de busca — moveria o
produto na direção errada nessas instâncias. **Melhorar a busca antes de corrigir a função objetivo
seria ativamente prejudicial.**

Duas ressalvas de honestidade sobre este achado:
- A métrica de retenção assume grade 4 (recall bem-sucedido) em toda sessão, e usa o `SubjectRetentionState`
  do próprio sistema. É uma simulação, não uma medição com alunos reais.
- Ela herda a inconsistência interna documentada em `01` Apêndice C — o limiar 0,85 dispara a 16% do
  intervalo agendado pelo SM-2. Um limiar coerente mudaria os valores absolutos. **Não mudaria o
  sinal da correlação**, que é o que sustenta a conclusão, porque a inversão decorre da concentração
  e não da calibração do limiar.

### 5.1 Sweller também não se sustenta em nenhum método

A contagem de dias com sobrecarga cognitiva é alta para **todos** os planejadores: em I2, ~147 dias
de sobrecarga; em I3, ~95; em I8, ~220. Nenhum método respeita o orçamento diário de carga, porque
nenhum o otimiza — a conformidade só aparece depois, quando `CognitiveLoadBalancingStrategy`
**poda blocos até caber**. A coluna "horas entregues/planejadas" mostra o preço dessa poda:
0,74 em I3, 0,85 em I2, 0,87 em I8, e **0,31 em I6**. Em I6, quase 70% do plano otimizado é
descartado silenciosamente antes de chegar ao aluno.

Isso confirma empiricamente o que `01` §4 concluiu por leitura de código: Sweller não está no
otimizador, está numa poda posterior.

---

## 6. Onde o AG compensa e onde não compensa

### 6.1 Onde o AG realmente supera os baselines

- **Contra divisão uniforme e alocação aleatória: sempre, e às vezes muito.** De +0,14% (I5) a +27%
  (I4). A vantagem cresce com a dispersão dos pesos do edital: +5,8% em I5 (pesos ~2:1) contra +27%
  em I4 (pesos 1000:1). Ou seja: **o AG captura de fato o sinal do edital**, e esse é seu ganho
  legítimo e mensurável sobre planejamento ingênuo.
- **Em instâncias grandes com pesos moderados** (I8: 40 disciplinas), fica +0,44% acima do guloso
  por prioridade e alcança o ótimo exato. É o cenário em que o AG se sai melhor em termos relativos
  contra o guloso.

### 6.2 Onde o AG não compensa a complexidade adicional

- **Contra o guloso por prioridade, a margem é de 0,01% a 0,44%.** Sete instâncias, ganho mediano
  ~0,15%. Nenhum aluno distingue dois planos que diferem 0,15% numa métrica sem unidade. Esse ganho
  não paga a complexidade de um motor evolutivo, sua não-reprodutibilidade, seu custo de CPU e sua
  superfície de manutenção.
- **Contra o ótimo exato, o AG nunca ganha e custa 20× a 400× mais.** Para a fitness atual, um
  guloso marginal de ~15 linhas devolve o ótimo em menos de 1 ms. O AG gasta 17–371 ms para chegar
  ao mesmo lugar (7 de 8 instâncias) ou a um lugar 5,7% pior (I4).
- **Em instâncias fáceis, o AG é indistinguível de qualquer coisa.** I1 e I6: todos os métodos ficam
  a menos de 1,2% entre si, e o AG melhora sua própria população inicial em menos de 0,3%. Nessas
  instâncias o motor evolutivo está sendo pago para não fazer diferença.
- **Onde a dispersão de pesos é extrema, o AG é o pior dos métodos informados**, perdendo para um
  cálculo proporcional de planilha por 5,4%.

### 6.3 Como isso muda a decisão de arquitetura

`02-formulacao.md` recomendou Fase 0 (reprodutibilidade) → Fase 1 (normalizar e construir
objetivos) → Fase 2 (multiobjetivo leve, condicionado a medição). **Estes resultados reforçam a
ordem e aumentam a urgência da Fase 1**, com um argumento novo que a etapa 2 não tinha:

> Não se trata só de que a fitness é pouco fiel às teorias declaradas. É que ela está
> **anti-correlacionada** com a métrica de negócio nas instâncias que discriminam. Enquanto isso
> valer, investir em melhorar a busca — mais gerações, operadores melhores, um MOEA — piora o
> produto. A função objetivo é o gargalo, não o otimizador.

E acrescenta um dado que a etapa 2 pedia mas não tinha: **o guloso marginal exato é um baseline de
produção viável hoje**, com resultado igual ou melhor que o AG em 8 de 8 instâncias e custo
desprezível. Isso torna a Opção B (`02` §3) ainda mais barata do que estimado: as 2–3 escalarizações
poderiam ser resolvidas exatamente, sem AG nenhum, enquanto a fitness permanecer separável e côncava.

---

## 7. O teste automatizado

`benchmarks/java/…/GeneticAlgorithmVsBaselinesTest.java`, executado por `./mvnw test`.

### 7.1 O limiar X = 2%, e por que esse valor

A tarefa pede um teste que falhe se o AG ficar mais de X% abaixo do baseline guloso. **X = 2%**,
calibrado com os dados desta medição e não escolhido a priori:

| Critério | Medição | Implicação |
|---|---|---|
| **Piso de ruído** | O AG não é semeável (`00` §7.4, R18). Em 7 repetições, a pior amplitude run-to-run nas instâncias cobertas foi **0,111%** | 2% fica ~18× acima do ruído → o teste não é *flaky* |
| **Poder de detecção** | O único déficit real medido é **5,42%** (I4) | 2% está bem abaixo → um defeito dessa magnitude seria detectado em qualquer outra instância |
| **Por que não mais apertado** | Abaixo de ~0,5% a asserção passaria a rastrear ruído de RNG nas instâncias difíceis | falharia por motivo que nenhuma mudança de código corrige |

Ruído medido por instância (7 repetições):

| Instância | média | desvio | CV% | amplitude% |
|---|---|---|---|---|
| `I1-pequeno-folgado` | 1462,6 | 0,00 | 0,000% | 0,000% |
| `I2-medio-tipico` | 1390,0 | 0,04 | 0,003% | 0,010% |
| `I3-grande-apertado` | 2915,3 | 0,67 | 0,023% | 0,063% |
| `I4-pesos-extremos` | 16997,1 | 174,85 | 1,029% | 2,499% |
| `I5-pesos-balanceados` | 1853,9 | 0,74 | 0,040% | 0,111% |
| `I6-horizonte-curto` | 1085,7 | 0,00 | 0,000% | 0,000% |
| `I7-horizonte-longo` | 2418,1 | 0,16 | 0,007% | 0,019% |
| `I8-escala` | 3911,5 | 0,02 | 0,000% | 0,001% |

Note que I4 é simultaneamente a instância mais ruidosa (CV 1,03%) e a única em que o AG perde — as
duas coisas têm a mesma causa: é a única instância em que a busca está genuinamente longe de
convergir.

### 7.2 Como o teste lida com o defeito já existente

I4 falha o contrato de 2% hoje, e esta etapa é explicitamente de medição — o AG **não** pode ser
alterado para o teste passar. As três saídas ruins seriam: afrouxar o limiar até acomodar o defeito,
desabilitar a asserção, ou deixar o build permanentemente vermelho.

A saída adotada é fixar a instância deficiente numa lista e asseverá-la **exatamente**:

```java
private static final Set<String> KNOWN_DEFICIENT_INSTANCES = Set.of("I4-pesos-extremos");
```

Assim a suíte falha nas duas direções:
- **uma instância nova cair abaixo do limiar** → regressão não percebida;
- **I4 voltar a passar** → melhoria não registrada, e a lista deve ser atualizada para travar o ganho.

O defeito fica documentado no código, visível, e sob guarda automática — em vez de escondido atrás
de um limiar frouxo.

### 7.3 Os cinco testes

| Teste | O que garante |
|---|---|
| `gaStaysWithinThresholdOfGreedyBaseline` | AG ≥ guloso × 0,98 nas instâncias fora da lista de deficiências |
| `knownDeficiencyListIsStillAccurate` | A lista de deficientes é exatamente `{I4}` — pega regressão e melhoria |
| `gaBeatsRandomAllocationEverywhere` | AG > alocação aleatória em toda instância (sanidade mínima) |
| `gaNeverExceedsTheExactOptimum` | AG ≤ ótimo analítico. **É um teste da auditoria**: se falhar, algum componente tido como inerte está ativo e `01` precisa ser revisto |
| `everyPlannerProducesFeasiblePlans` | Todo planejador respeita orçamento e piso de mínimos |

Verificação de que o teste realmente detecta o problema: esvaziando `KNOWN_DEFICIENT_INSTANCES`, a
suíte falha com a mensagem
*"Instancia I4-pesos-extremos: AG (16884.11) ficou 6.05% abaixo do guloso por prioridade (17971.24);
o limite tolerado e 2%."* — confirmando que a asserção tem poder e diagnóstico útil.

Estado atual: **33 testes, 0 falhas** (28 pré-existentes + 5 novos).

---

## 8. Achado incidental: `./mvnw package` está quebrado (pré-existente)

Ao tentar comprovar o isolamento inspecionando o jar, descobri que **o build de empacotamento falha
— e falhava antes desta etapa**:

```
Failed to execute goal org.springframework.boot:spring-boot-maven-plugin:3.5.5:repackage:
Unable to find main class
```

Verificado com `git stash` do meu diff de `pom.xml`: **o erro é idêntico com o pom original**, logo
não foi introduzido aqui.

**Causa raiz.** `DynamicStudyPlannerApplication` declara o ponto de entrada como *package-private*:

```java
static void main(String[] args) { ... }     // sem 'public'
```

Confirmado em bytecode: `javap -p` mostra `static void main(java.lang.String[])`. Essa forma só é
válida como ponto de entrada a partir dos *instance main methods* (preview do Java 25), que o pom
tenta habilitar com `<source>24</source><target>24</target>` e `--enable-preview`. Mas o parent do
Spring Boot deriva `maven.compiler.release=21` de `<java.version>21</java.version>`, e o
`maven-compiler-plugin` 3.14 dá precedência a `release` — a compilação sai como
`[debug parameters release 21]`. Sob regras do Java 21 o método compila (é um método comum) mas
**não é um ponto de entrada válido**, e o `repackage` não acha a main class.

**Isto corrige e agrava o R48 do `00-diagnostico.md`**, onde registrei a divergência
`java.version=21` × `source/target=24` como "latente, mas não bloqueia o build". Aquela avaliação
se baseou em `compile` e `test`, que de fato passam. O comando que produz o artefato entregável não
passa. A afirmação correta é: **o projeto compila e testa, mas não empacota.**

Não corrigi nada disso: está fora do escopo desta etapa, e envolveria alterar o ponto de entrada da
aplicação. Fica registrado como item de maior severidade do que o originalmente classificado.

---

## 9. Ameaças à validade

Registradas para que o leitor calibre a confiança, não como ressalvas defensivas.

1. **Instâncias sintéticas.** Não há dados de alunos ou editais reais. As faixas respeitam as
   validações da API (`SubjectDto`, `ThematicAxisDto`, `ExamDto`), mas a dispersão de 1000:1 de I4,
   embora **aceita** pela API, pode não ocorrer em editais reais. Se não ocorrer, o pior caso do AG
   é menos relevante — e sua vantagem sobre os baselines também encolhe, porque é justamente a
   dispersão que a produz.
2. **Reprodutibilidade parcial.** Semear `RandomProvider` fixa seleção, crossover e população
   inicial, mas não a mutação (`Math.random()` + `ThreadLocalRandom`). O resíduo está quantificado
   em §7.1 e é ≤ 0,111% fora de I4. Corrigir isso exige mexer em produção, o que esta etapa proíbe.
3. **A métrica de retenção é uma simulação.** Grade 4 em toda sessão, `SubjectRetentionState` do
   sistema, e a inconsistência de limiar de `01` Apêndice C. Afeta valores absolutos; não afeta o
   sinal das correlações (§5).
4. **Sem repetição sobre múltiplas famílias de instâncias.** Oito instâncias, uma seed cada. Uma
   validação mais forte varreria dezenas de instâncias por família com seeds diferentes. O que está
   aqui é suficiente para as conclusões qualitativas — que são grandes e consistentes — e
   insuficiente para afirmações finas do tipo "o AG ganha 0,15% em média".
5. **A fitness não é comparável entre instâncias.** Todas as comparações são intra-instância. Não há
   média agregada de fitness neste relatório porque ela não teria significado (`01` §3.1).
6. **Um único ambiente.** JDK 21, uma máquina. Os tempos são indicativos de ordem de grandeza,
   não medições de desempenho controladas (sem JMH, sem aquecimento de JIT).

---

## 10. Conclusão

O AG de produção **funciona**: alcança o ótimo analítico da fitness que lhe deram em 7 de 8
instâncias, e supera com folga o planejamento ingênuo (uniforme, aleatório). Não é um motor
quebrado.

Mas a comparação empírica responde à pergunta que a motivou — *"um AG só é uma escolha defensável se
supera alternativas mais simples nos mesmos dados"* — de forma desfavorável em três frentes
independentes:

1. **Contra o guloso por prioridade**, a margem é de 0,01% a 0,44% em 7 instâncias, e **negativa
   (−5,4%)** na oitava. Não há aqui um ganho que justifique um motor evolutivo.
2. **Contra o guloso marginal exato**, o AG empata em 7 de 8 e perde em 1, gastando 20× a 400× mais
   tempo. Para a fitness atual, o AG é estritamente dominado por 15 linhas de código guloso.
3. **A métrica de negócio inverte o ranking.** Nas quatro instâncias que discriminam, ρ entre
   fitness e retenção fica entre −0,83 e −0,94.

O terceiro ponto subordina os outros dois. Enquanto a função objetivo estiver anti-correlacionada
com a retenção, **a pergunta "AG ou guloso?" é secundária** — os dois otimizam a coisa errada, e o
melhor deles otimiza a coisa errada com mais eficiência. A decisão sobre a arquitetura de busca deve
vir **depois** da correção da fitness (Fase 1 de `02-formulacao.md`), não antes; e essa correção
deve ser reavaliada com este mesmo harness, que agora existe e roda em 4 segundos.

Uma nota final sobre o que este trabalho não decide: nada aqui diz que o AG deve ser removido.
Diz que ele **ainda não tem justificativa empírica** — e que a justificativa, se vier, virá de uma
fitness que o guloso não consiga resolver exatamente (`01` §3.4, `02` §5). Construir essa fitness é
o que tornaria o AG defensável; medir de novo com este harness é o que provaria.

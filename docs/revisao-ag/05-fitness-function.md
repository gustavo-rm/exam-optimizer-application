# A Função de Fitness do SINAPSE — Documentação de Referência

**Status:** documentação permanente. Descreve a fitness **como ela é hoje**, não um plano.
**Escopo:** motor de planejamento macro (produto). A formulação multiobjetivo completa pertence à
linha de pesquisa e não está aqui, por decisão registrada em [`02-formulacao.md`](./02-formulacao.md).
**Código:** `src/main/java/…/ga/fitness/` · pesos em `FitnessWeights` · agregação em `FitnessEvaluator`
**Histórico da revisão:** [`00`](./00-diagnostico.md) · [`01`](./01-auditoria-fitness.md) ·
[`02`](./02-formulacao.md) · [`03`](./03-validacao.md) · [`04`](./04-robustez.md)

> **Se você vai alterar um peso ou uma fórmula, leia a §7 antes.** Esta página e o código estão
> acoplados de propósito: cada classe da fitness aponta para cá no Javadoc, e `FitnessEvaluator`
> falha na subida se os pesos deixarem de somar 1.

---

## 1. A fórmula

```
fitness(plano) = clamp( Σ_o  w_o · O_o(plano)  −  Σ_c  λ_c · severidade_c(plano) , 0, 1 )
                 × Π_p  penalidade_p(plano)
```

Com os componentes de hoje:

```
fitness = clamp( 0,50 · O₁  +  0,30 · O₃  +  0,20 · O₄  −  0,50 · severidade_piso , 0, 1 ) × 1,0
```

Três propriedades garantidas por construção:

| Propriedade | Como é garantida | Por que importa |
|---|---|---|
| **fitness ∈ [0,1]** | todo objetivo devolve [0,1] e os pesos somam 1 | o valor é comparável entre editais, alunos e releases — sem isso nenhum benchmark tem significado |
| **Pesos somam 1** | asserção em `FitnessEvaluator` na construção | um peso editado sem rebalancear reescalaria toda fitness em silêncio |
| **Violação subtraída, não multiplicada** | `− λ·severidade`, graduada | o custo de violar não depende da qualidade do plano, e há gradiente de volta à viabilidade |

---

## 2. Tabela de referência dos termos

| Termo | Conceito | Fórmula | Peso | Justificativa do peso | Fundamento |
|---|---|---|---|---|---|
| **O₁** `ScoreGainObjective` | Domínio do edital ponderado pelo valor de cada disciplina na prova | `Σ_s Î_s · (1 − e^(−d_s/τ_s))` | **0,50** | Maior fatia: a prova é o objetivo do aluno. Um plano que pontua bem em tudo o mais e negligencia o edital não é um plano de estudo | Curvas de prática com assíntota (Newell & Rosenbloom, 1981); pesos de pontuação do próprio edital |
| **O₃** `RetentionObjective` | Retenção na data da prova / repetição espaçada | `Σ_s Î_s · min(1, d_s / (H/τ_s))` | **0,30** | Segunda maior: conhecimento não retido até a prova vale zero na prova, então é pré-condição para O₁ contar. Fica abaixo de O₁ porque a falha inversa custa mais — um plano estreito e bem retido perde mais pontos que um amplo com algum decaimento | Curva de esquecimento de Ebbinghaus (1885), `R = e^(−t/S)`; limiar derivado de um intervalo de estabilidade |
| **O₄** `CognitiveLoadObjective` | Sustentabilidade da carga mental diária | `1 − clamp((cargaDiária − orçamento)/orçamento, 0, 1)` | **0,20** | Menor das três. É a menos fiel à teoria de origem (só limita carga *esperada*, não por episódio) e é guarda de viabilidade, não meta — uma fatia maior deixaria trocar cobertura do edital por conforto | Teoria da carga cognitiva de Sweller (1988); orçamento de `CognitiveLoadCalculator` |
| **C₁** `MinimumDaysConstraint` | Piso de cobertura por disciplina | `− 0,50 · (Σ_s max(0, m_s − d_s) / Σ_s m_s)` | **λ = 0,50** | Metade da massa total dos objetivos: uma violação plena custa mais do que qualquer objetivo isolado pode pagar de volta, então um plano inviável nunca supera um viável; uma violação marginal continua recuperável | Regra de produto (cobertura mínima do edital), sem base teórica de aprendizagem |
| **P₁, P₂** `DropoutRiskPenalty`, `FatigueAndSustainabilityPenalty` | Risco de evasão; burnout | multiplicativas, `= 1,0` no caminho macro | — | Reservadas à camada tática, onde há cronograma real para avaliar. Neutras aqui por projeto, não por acidente | Engajamento/churn (não é um dos três fundamentos) |

**Notação:** `Î_s` = importância normalizada no simplex (`Σ Î_s = 1`); `d_s` = dias alocados à
disciplina *s*; `τ_s` = constante de tempo da disciplina; `H` = horizonte em dias até a prova;
`m_s` = piso de dias mínimos.

---

## 3. Cada termo em detalhe

### 3.1 Normalização da importância — a base de tudo

Antes de qualquer termo, a importância bruta de cada disciplina é projetada no simplex:

```
Î_s = I_s / Σ_k I_k        onde  I_s = questionCount_s × pesoDoEixo_s × lacunaDeConhecimento_s
```

**Por que.** A importância bruta carrega as unidades do próprio edital, e os limites de validação da
API permitem que duas disciplinas da mesma soma difiram por um fator de **250 000** (`01` §2.1.4).
Sob soma simples isso tornava a alocação *winner-take-all*: o ganho marginal da disciplina dominante
permanecia acima de todas as outras por todo o orçamento, e só o piso de dias mínimos mantinha o
resto do edital vivo — **a diversificação estava sendo feita pela restrição, não pelo objetivo.**

Normalizar remove do payload a capacidade de definir a escala, torna a fitness comparável entre
editais, e é pré-requisito dos outros termos: O₃ e O₄ nascem em [0,1] e seriam aritmeticamente
invisíveis somados à escala anterior.

*Implementação:* `EvolutionContext.normalize`, calculada uma vez por requisição.

### 3.2 O₁ — Domínio do edital

```
O₁ = Σ_s Î_s · maestria(s, d_s)          maestria(s,d) = 1 − e^(−d/τ_s)
τ_s = 10 · cargaCognitiva_s / 3          (dias para ~63% de domínio)
```

**O que modela.** Ganho esperado de pontuação. Cada disciplina contribui na proporção do que vale na
prova, multiplicada por quanto dela o plano consegue efetivamente ensinar.

**Por que uma curva com teto.** A forma anterior, `ln(1 + d)`, capturava retornos decrescentes mas é
ilimitada superiormente — afirma que o conhecimento cresce sem limite com os dias, sem teto de
domínio. Não é o que a literatura de curvas de prática descreve, e tinha consequência concreta: sem
saturação, o ganho marginal de uma disciplina de alta importância nunca caía o suficiente para outra
competir, e o ótimo concentrava o orçamento em pouquíssimas disciplinas (`01` §2.1.2). A aproximação
exponencial de uma assíntota corrige as duas coisas: satura, então dias extras numa disciplina já
dominada param de pagar, e o valor marginal migra.

**Por que τ escala com a carga cognitiva.** Material mais denso leva mais tempo. Ancorado em 10 dias
para dificuldade média — a mesma média que `CognitiveLoadCalculator` assume — o que dá τ de 3,3 dias
para a disciplina mais fácil e 16,7 para a mais difícil. **Este foi o primeiro ponto em que
`Subject.cognitiveLoad` passou a influenciar o otimizador**: até então nenhum componente ativo da
fitness o lia (`01` §2.5.1).

### 3.3 O₃ — Retenção na data da prova (Ebbinghaus)

```
sessõesNecessárias(s) = H / τ_s
O₃ = Σ_s Î_s · min(1, d_s / sessõesNecessárias(s))
```

**Derivação.** A curva de Ebbinghaus, `R = e^(−t/S)`, atinge `e⁻¹` após exatamente um intervalo de
estabilidade. Sessões espaçadas além de `τ` deixam a disciplina cair abaixo do ponto em que o
`HybridRetentionEngine` declara a revisão devida. Num horizonte de `H` dias, isso são `H/τ` sessões.
O termo é a fração do edital, ponderada por importância, que recebe sessões suficientes.

**Por que o teto é essencial.** O `min(1, ·)` é o que torna o termo côncavo, e a concavidade é o que
faz ele premiar espalhamento: uma vez que a disciplina tem sessões suficientes, dias extras não
rendem mais nada ali e o valor marginal migra para a que ainda está descoberta. **É o contrapeso
direto à concentração** — o mecanismo pelo qual a fitness antiga ficava anti-correlacionada com a
retenção (`03` §5).

**Limite honesto — leia antes de citar este termo.** É uma **aproximação de campo médio, não
repetição espaçada**. O cromossomo macro é `Map<Subject, Integer>`: uma contagem de dias, sem posição
no calendário e sem ordem (`01` §3.3). O termo só sabe **quantas** sessões a disciplina recebe,
assumindo que o agendador as distribui de forma aproximadamente uniforme pelo horizonte — que é o
que o `StudyScheduleGenerator` faz. Ele **não** expressa *quando* a revisão ocorre, **não** modela
intervalos expansivos e **não deve ser descrito como implementação de SM-2 ou de repetição espaçada
propriamente dita**. Fazer isso fielmente exige encoding indexado no tempo, decisão separada e ainda
em aberto (`02` §5).

### 3.4 O₄ — Carga cognitiva diária (Sweller)

```
cargaDiáriaEsperada = horasPorDia × Σ_s (d_s/D) · cargaCognitiva_s
O₄ = 1 − clamp((cargaDiáriaEsperada − orçamento) / orçamento, 0, 1)
```

**O que modela.** A teoria de Sweller sustenta que a memória de trabalho tem limite de capacidade
rígido, e que instrução que o excede não produz aprendizagem por mais tempo que se gaste.
`Subject.cognitiveLoad` (1–5) é o proxy de carga intrínseca que a API já coleta;
`CognitiveLoadCalculator` converte disponibilidade e estado psicológico num orçamento diário
sustentável. O termo compara os dois: um plano pesado em material de alta carga implica dias médios
mais difíceis, e é penalizado na proporção do excesso.

**Por que o estado do aluno entra aqui, e não como penalidade separada.** O orçamento já incorpora
estresse, fadiga e motivação. Roteá-lo pelo orçamento faz o sinal **discriminar entre planos**: um
aluno estressado recebe orçamento menor, então planos pesados perdem mais fitness que leves. A
`FatigueAndSustainabilityPenalty` multiplicava todo indivíduo pela mesma constante, que o Apêndice B
de `01` provou incapaz de alterar o ranqueamento de dois planos quaisquer — era aritmética sem efeito
sobre a busca.

**Limite honesto.** O construto de Sweller é sobre carga dentro de um episódio de aprendizagem, e
este termo só fala da carga diária **esperada** pela mistura de disciplinas. Um plano pode
satisfazê-lo e ainda produzir dias sobrecarregados individuais. Um teto por dia real exige encoding
temporal; até lá o `CognitiveLoadBalancingStrategy` da camada tática é a única guarda por dia — e ela
faz isso **descartando blocos**, não planejando em torno deles.

### 3.5 C₁ — Piso de dias mínimos

```
severidade = Σ_s max(0, m_s − d_s) / Σ_s m_s        contribuição = − 0,50 × severidade
```

Antes, qualquer violação multiplicava a fitness inteira por 0,5. Isso era **binário** (furar o piso
de uma disciplina em um dia custava o mesmo que furar todas em dez, sem gradiente de volta à
viabilidade) e **proporcional à qualidade do plano** (um plano bom pagava mais pelo mesmo erro que um
ruim) — invertido para o que se pretende ser uma restrição dura (`01` §2.2.3).

**Nota:** esta restrição **não dispara na prática**. Todo operador que produz um plano preserva o
piso por construção, logo por indução toda a população é viável em toda geração (prova por
enumeração em `01` §1.2). Ela é mantida como guarda: se um operador futuro quebrar a invariante,
isso vira perda visível de fitness em vez de um plano entregue ao aluno com disciplinas abaixo do
piso.

---

## 4. O que **não** está na fitness — e por quê

Esta seção existe para que ninguém precise inferir ausências a partir do silêncio.

### 4.1 Ausubel / pré-requisitos: **não implementado**

A aprendizagem significativa de Ausubel é uma restrição de **ordem** — material novo precisa de
subsunçores já presentes, o que implica que o pré-requisito precede o dependente. **O cromossomo
macro não tem ordem**, então isso é inexprimível por construção, não por omissão (`01` §3.3).

O `README.md` do projeto atribui Ausubel ao multiplicador de lacuna de conhecimento. Essa leitura
merece escrutínio e **não deve ser repetida em material técnico**: a regra implementada é "quanto
maior a lacuna, maior a prioridade", monotônica e sem noção de ancoragem. Ausubel implica o oposto
em um caso importante — material cujos pré-requisitos faltam deveria ser **adiado** até que o
pré-requisito seja coberto, não promovido. A heurística de triagem é defensável por si; chamá-la de
Ausubel não é.

**O que o produto pode afirmar hoje:** a lacuna de conhecimento declarada pelo aluno personaliza a
importância de cada disciplina. Nada além disso.

### 4.2 Aderência a prazo: **não é um termo da fitness**

A etapa `02` §1 previa um objetivo de aderência a prazo. Ao implementar, descobriu-se que **não é
uma propriedade do indivíduo**: as horas requeridas são `Σ d_s × horasPorDia`, e como `Σ d_s = D` é
invariante em toda a população, o termo seria idêntico para todos os indivíduos — exatamente o defeito
que `01` Apêndice B apontou na penalidade de fadiga.

Aderência a prazo é, portanto, tratada como **verificação de viabilidade**, não como objetivo:
`StudyScheduleGenerator` compara horas disponíveis com requeridas e devolve `ScheduleStatus`.
Acrescentar um termo constante à fitness para "representar prazo" seria decorativo — o mesmo erro que
esta revisão passou quatro etapas documentando.

### 4.3 Fronteira de Pareto / NSGA-II: **fora de escopo por decisão**

Registrado em `02` §4. Não por dificuldade algorítmica, mas porque não há consumidor de produto para
um conjunto de soluções (a API é single-plan ponta a ponta), porque falta infraestrutura para validar
um MOEA, e porque a formulação multiobjetivo completa pertence à linha de doutorado — duplicá-la no
produto criaria duas versões divergentes do mesmo artefato.

---

## 5. Os pesos: valores, justificativa e estabilidade

### 5.1 Como foram escolhidos

**São julgamentos de produto, não ajustes empíricos.** Nenhum dataset foi usado para calibrá-los.
Afirmar o contrário seria falso e insustentável numa auditoria. A ordenação segue um raciocínio
explícito:

1. **O edital domina (0,50)** porque é a métrica pela qual o aluno será avaliado.
2. **A retenção vem em seguida (0,30)** porque conhecimento não retido até a prova vale zero na
   prova — é pré-condição para O₁ contar, não um refinamento dele. Fica abaixo de O₁ porque a falha
   inversa custa mais: um plano estreito e perfeitamente retido perde mais pontos que um amplo com
   algum decaimento.
3. **A carga cognitiva fecha (0,20)** porque é a menos fiel à teoria de origem sob o encoding atual e
   porque é guarda de viabilidade, não meta. Uma fatia maior deixaria o otimizador trocar cobertura
   do edital por conforto.

### 5.2 O que torna a escolha defensável: a medição de estabilidade

Não é uma alegação de otimalidade — é a resposta a *"e se os pesos estivessem 20% errados?"*.
Cada peso foi perturbado em ±20%, os demais renormalizados, e mediu-se **o quanto o plano se move**
(não a fitness: dois planos podem pontuar quase igual alocando dias bem diferentes).

| Instância | edital ∓20% | retenção ∓20% | carga ∓20% | pior caso |
|---|---|---|---|---|
| `I1-pequeno-folgado` | 0,0% / 0,0% | 0,0% / 0,0% | 0,0% / 0,0% | **0,0%** |
| `I2-medio-tipico` | 2,9% / 2,9% | 0,0% / 0,0% | 2,9% / 1,4% | **2,9%** |
| `I3-grande-apertado` | 4,5% / 4,5% | 0,0% / 0,0% | 3,0% / 3,5% | **4,5%** |
| `I4-pesos-extremos` | 5,0% / 9,4% | 0,0% / 0,0% | 6,9% / 4,4% | **9,4%** |
| `I5-pesos-balanceados` | 0,6% / 10,6% | 0,0% / 0,0% | 6,3% / 1,3% | **10,6%** |
| `I6-horizonte-curto` | 3,8% / 4,8% | 0,0% / 0,0% | 3,8% / 1,9% | **4,8%** |
| `I7-horizonte-longo` | 6,2% / 4,6% | 2,3% / 5,0% | 0,0% / 0,0% | **6,2%** |
| `I8-escala` | 2,1% / 2,9% | 1,1% / 0,7% | 1,1% / 1,4% | **2,9%** |

**Afirmação sustentável:** *uma perturbação de ±20% em qualquer peso desloca no máximo 10,6% do
orçamento de dias, com mediana em torno de 4%.* Os pesos importam, mas não são knife-edge — o plano
não depende de eles estarem exatamente certos.

**Detalhe que merece registro:** o peso de retenção não move nada na maioria das instâncias. Não é
sinal de que o termo é inerte — ele muda o plano —, e sim de que na maioria dos casos o orçamento não
alcança a cobertura de todas as disciplinas, e o termo opera no seu regime **linear**, onde apenas
reordena por `Î_s / sessõesNecessárias_s`. Só em horizontes longos (`I7`) e em escala (`I8`) o teto
começa a atar e a perturbação passa a importar.

*Reprodução:* `benchmarks/…/robustness/WeightSensitivityMain` → `benchmarks/results/sensibilidade-pesos.csv`

---

## 6. Validação empírica

Mesmo harness e mesmas 8 instâncias sintéticas da etapa `03`, medidos antes e depois das correções.

### 6.1 Contra os baselines (positivo = AG melhor)

| Instância | vs guloso prioridade **antes** | vs guloso prioridade **depois** | vs uniforme | vs aleatório |
|---|---|---|---|---|
| `I1-pequeno-folgado` | +0,02% | **+0,13%** | −0,25% | −0,00% |
| `I2-medio-tipico` | +0,15% | **+4,17%** | +6,15% | +7,18% |
| `I3-grande-apertado` | +0,29% | **+2,70%** | +2,84% | +2,90% |
| `I4-pesos-extremos` | **−6,18%** | **+4,29%** | +10,95% | +12,67% |
| `I5-pesos-balanceados` | +0,14% | **+5,49%** | +2,09% | +3,02% |
| `I6-horizonte-curto` | +0,01% | **+2,65%** | +1,74% | +1,82% |
| `I7-horizonte-longo` | +0,10% | **+0,41%** | +0,87% | +1,16% |
| `I8-escala` | +0,44% | **+0,67%** | +3,62% | +4,39% |

**A vantagem melhorou em todas as 8 instâncias**, e a única em que o AG perdia (por 6,18%) passou a
ganhar por 4,29%. `I1` empata com sorteio porque é uma instância saturada — poucas disciplinas,
orçamento sobrando, todo plano viável está essencialmente no ótimo.

### 6.2 O AG passou a se justificar

`03` §4.2 media que o AG apenas empatava com o guloso marginal exato, gastando 20× a 400× mais tempo
— o problema tinha solução em forma fechada. Com O₃ e O₄ no agregado, a fitness deixou de ser
separável e côncava, e **o AG agora supera esse guloso em até 10,09%**. É a evidência empírica de que
a busca passou a ter o que encontrar. Há teste de regressão para isso
(`gaExceedsTheSingleObjectiveOptimumOnSomeInstance`): se falhar, a fitness voltou a uma forma que um
guloso `O(D log n)` resolve exatamente e o argumento para rodar um AG em produção cai junto.

### 6.3 Métricas de negócio

| Instância | Métrica | AG | Guloso | Uniforme |
|---|---|---|---|---|
| `I4-pesos-extremos` | dias com sobrecarga cognitiva | **23,0** | 41,0 | 124,0 |
| | concentração na disciplina top-1 | **25,2%** | 75,6% | 17,5% |
| | % na janela de retenção | 81,4% | 70,0% | 100,0% |
| `I8-escala` | dias com sobrecarga | **156,7** | 218,0 | 223,0 |
| | horas entregues / planejadas | **0,94** | 0,87 | 0,88 |
| `I3-grande-apertado` | dias com sobrecarga | **73,4** | 93,0 | 91,0 |

O AG passou a produzir planos com **menos sobrecarga cognitiva e maior taxa de entrega de horas do
que todos os baselines** — ganho de produto direto do termo de Sweller. A concentração em `I4` caiu
de 75,6% para 25,2%.

### 6.4 A anti-correlação: muito reduzida, não eliminada

O achado central de `03` §5 era que maximizar a fitness **piorava** a retenção. Correlação de
Spearman entre fitness e % na janela de retenção, sobre as 6 estratégias de cada instância:

| Instância | antes | depois |
|---|---|---|
| `I3-grande-apertado` | −0,941 | **−0,029** |
| `I4-pesos-extremos` | −0,941 | **−0,525** |
| `I7-horizonte-longo` | −0,833 | **+0,000** |
| `I8-escala` | −0,880 | **−0,880** |
| **Agregado (48 obs.)** | **−0,654** | **+0,161** |

**O sinal agregado inverteu** e a anti-correlação foi eliminada em `I3` e `I7`, e reduzida à metade
em `I4`. **Mas `I8` permanece inalterada**, e isso precisa ser dito: com 40 disciplinas, 280 dias de
orçamento e horizonte de 300, as `H/τ ≈ 30` sessões por disciplina somam ~1200 contra 280
disponíveis. O termo de retenção nunca sai do regime linear e portanto não exerce força de
espalhamento nenhuma. **Em editais muito grandes com orçamento apertado, a fitness ainda favorece
concentração.** É limitação conhecida e registrada, não corrigida.

Segunda ressalva honesta: a divisão uniforme continua vencendo o AG na métrica de janela de retenção
em `I3` (96,0% contra 76,6%) e `I4` (100% contra 81,4%). Isso é a **troca deliberada que os pesos
codificam** — 0,50 para edital contra 0,30 para retenção —, não um defeito: o AG sacrifica alguma
amplitude de retenção para priorizar o que vale mais pontos na prova. Quem discordar dessa troca deve
mexer nos pesos, e a §5.2 diz quanto o plano se move quando se faz isso.

---

## 7. Como alterar a fitness com segurança

1. **Leia esta página inteira primeiro.** Cada classe da fitness aponta para cá no Javadoc.
2. **Todo objetivo novo deve devolver [0,1].** Se não devolver, ele vai dominar ou sumir na soma —
   `01` §2.1.4 quantificou o que acontece quando isso não vale.
3. **Rebalanceie os pesos para somar 1.** `FitnessEvaluator` falha na subida caso contrário. A
   asserção já pegou uma divergência real durante a própria implementação desta etapa.
4. **Registre o novo componente em `BenchmarkHarness.productionFitnessEvaluator()`.** As listas são
   explícitas de propósito: esquecê-las invalidaria a comparação em silêncio.
5. **Rode o harness e a análise de sensibilidade.** Atualize as tabelas da §5.2 e §6.
6. **Atualize esta página.** Um peso alterado sem justificativa registrada deixa o produto incapaz de
   explicar as próprias decisões de planejamento — que é exatamente o estado que esta revisão existiu
   para corrigir.

Testes que protegem o que está documentado aqui:

| Teste | O que trava |
|---|---|
| `IndividualTest` | A fórmula agregada, com valores calculados à mão a partir desta página |
| `GeneticAlgorithmVsBaselinesTest` | O AG não fica 2% abaixo do guloso; supera o ótimo de O₁ em alguma instância |
| `GaEdgeCasesTest` | Determinismo, casos de borda, viabilidade dos planos |
| Asserção em `FitnessEvaluator` | Pesos somando 1 |

---

## 8. Limitações conhecidas

Registradas para que não sejam descobertas por um avaliador antes de nós.

| # | Limitação | Consequência | O que resolveria |
|---|---|---|---|
| L1 | **Ausubel não está implementado** | O produto não pode afirmar sequenciamento por pré-requisitos | Encoding indexado no tempo com grafo de dependências |
| L2 | **O₃ é aproximação de campo médio** | Não é repetição espaçada; não sabe *quando* a revisão ocorre | Encoding indexado no tempo |
| L3 | **O₄ limita carga esperada, não por episódio** | Um plano conforme ainda pode ter dias sobrecarregados | Encoding indexado no tempo |
| L4 | **Anti-correlação persiste em editais grandes com orçamento apertado** (`I8`) | Em regime linear de O₃, a fitness ainda favorece concentração | Termo de retenção com saturação relativa ao orçamento, ou orçamento maior |
| L5 | **Pesos não calibrados empiricamente** | Defensáveis por estabilidade medida, não por otimalidade | Dados de desempenho real de alunos |
| L6 | **Aderência a prazo não é otimizada** | O AG não sabe se o plano cabe no calendário; só o agendador descobre, depois | Encoding indexado no tempo |
| L7 | **Taxa de mutação 0,05 mal calibrada** | `04` §5 mediu que 0,15–0,30 recupera qualidade a custo de tempo nulo | Pendência P4 de `04`, aguardando decisão |

**L1, L2, L3 e L6 têm a mesma raiz:** o cromossomo `Map<Subject, Integer>` não tem coordenada
temporal nem ordem. É a decisão de arquitetura em aberto mais importante do motor, e está registrada
em `02` §5 como separada e explícita.

---

## 9. Fundamentos citados

| Fundamento | Fonte | Onde aparece | Fidelidade |
|---|---|---|---|
| Curva de esquecimento | Ebbinghaus, H. (1885). *Über das Gedächtnis* | O₃; `HybridRetentionEngine` (`R = e^(−t/S)`) | **Fórmula fiel; integração aproximada** (L2) |
| Repetição espaçada / SM-2 | Wozniak & Gorzelanczyk (1994), algoritmo SuperMemo-2 | `HybridRetentionEngine.processReview`; EF inicial 2,5 e piso 1,3 são canônicos do SM-2 | Fiel na camada tática; **não** no macro |
| Teoria da carga cognitiva | Sweller, J. (1988). *Cognitive load during problem solving* | O₄; `CognitiveLoadCalculator` | **Aproximada** — carga esperada, não por episódio (L3) |
| Aprendizagem significativa | Ausubel, D. (1968). *Educational Psychology: A Cognitive View* | — | **Não implementado** (L1) |
| Curvas de prática com assíntota | Newell & Rosenbloom (1981) | O₁, forma `1 − e^(−d/τ)` | Forma funcional; τ é escolha nossa, não calibrada |
| Limite da escalarização por soma ponderada | Resultado padrão de otimização multiobjetivo: soma ponderada só alcança o casco convexo da fronteira de Pareto | Agregação; decisão em `02` | Limitação assumida e documentada |

**Distinção importante para auditoria:** as constantes herdadas do SM-2 (EF inicial 2,5, piso 1,3,
intervalos 1 e 6 dias) são canônicas do algoritmo publicado. As demais — τ de 10 dias na dificuldade
média, os pesos 0,50/0,30/0,20, o λ de 0,50 — são **escolhas nossas**, e estão marcadas como tal em
todo este documento. Nenhuma delas é apresentada como resultado da literatura.

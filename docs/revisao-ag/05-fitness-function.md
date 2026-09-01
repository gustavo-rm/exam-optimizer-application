# A Função de Fitness do SINAPSE — Documentação de Referência

**Status:** documentação permanente. Descreve a fitness **como ela é hoje**, não um plano.
**Escopo:** motor de planejamento macro (produto). A formulação multiobjetivo completa pertence à
linha de pesquisa e não está aqui, por decisão registrada em [`02-formulacao.md`](./02-formulacao.md).
**Código:** `src/main/java/…/ga/fitness/` · pesos em `FitnessWeights` · agregação em `FitnessEvaluator`
**Histórico da revisão:** [`00`](./00-diagnostico.md) · [`01`](./01-auditoria-fitness.md) ·
[`02`](./02-formulacao.md) · [`03`](./03-validacao.md) · [`04`](./04-robustez.md) ·
[`06-ausubel`](./06-decisao-ausubel.md) · [`06-alta-carga`](./06-regime-alta-carga.md) ·
[`06-troca-pesos`](./06-limite-troca-pesos.md)

> **Se você vai alterar um peso ou uma fórmula, leia a §7 antes.** Esta página e o código estão
> acoplados de propósito: cada classe da fitness aponta para cá no Javadoc, e `FitnessEvaluator`
> falha na subida se os pesos deixarem de somar 1.

> ## ⚠ Nota de correção — 2026-08-31 (etapa 07)
>
> **A versão da etapa 05 deste documento afirmava, na §6.4, que "o sinal agregado inverteu", de
> −0,654 para +0,161. Essa afirmação era falsa**, e a evidência que a sustentava era uma estatística
> inválida: o agregado empilhava as 48 observações (8 instâncias × 6 planejadores) num único
> Spearman, ranqueando valores de fitness que `PlanMetrics.java` documenta como **não comparáveis
> entre instâncias**.
>
> O número `+0,161` já havia sido removido da §6.4 na reescrita da etapa 06, mas **sem que a
> afirmação que dele dependia fosse revista** — corrigido agora. Pelo método canônico
> (transformada z de Fisher sobre as correlações por instância,
> `benchmarks/…/metric/CorrelationAggregate.java`), a trajetória real é:
>
> | Estado | Agregado empilhado (publicado, **inválido**) | Fisher z por instância (**canônico**) |
> |---|---|---|
> | Etapa 03 | −0,654 | **−0,909** [IC95% −0,971; −0,734] |
> | Etapa 05 | **+0,161** | **−0,460** [IC95% −0,793; +0,085] |
> | Etapa 06 | +0,196 | **−0,106** [IC95% −0,597; +0,443] |
>
> **A etapa 05 reduziu a anti-correlação, não a inverteu.** A §6.4 abaixo já traz a tabela por
> instância, que sempre esteve correta, agora com a linha de agregado canônico. Racional completo em
> [`07-correcao-metrica-e-ausubel.md`](./07-correcao-metrica-e-ausubel.md).

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

`w_o` são os pesos **dos objetivos** e somam 1. Não confundir com os pesos **por disciplina** que O₁
e O₃ usam internamente: desde a etapa 06 os dois não são mais o mesmo vetor (§3.3).

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
| **O₃** `RetentionObjective` | Retenção na data da prova / repetição espaçada | `Σ_s w₃_s · min(1, d_s / (H/τ_s))` | **0,30** | Segunda maior: conhecimento não retido até a prova vale zero na prova, então é pré-condição para O₁ contar. Fica abaixo de O₁ porque a falha inversa custa mais — um plano estreito e bem retido perde mais pontos que um amplo com algum decaimento | Curva de esquecimento de Ebbinghaus (1885), `R = e^(−t/S)`; limiar derivado de um intervalo de estabilidade |
| **O₄** `CognitiveLoadObjective` | Sustentabilidade da carga mental diária | `1 − clamp((cargaDiária − orçamento)/orçamento, 0, 1)` | **0,20** | Menor das três. É a menos fiel à teoria de origem (só limita carga *esperada*, não por episódio) e é guarda de viabilidade, não meta — uma fatia maior deixaria trocar cobertura do edital por conforto | Teoria da carga cognitiva de Sweller (1988); orçamento de `CognitiveLoadCalculator` |
| **C₁** `MinimumDaysConstraint` | Piso de cobertura por disciplina | `− 0,50 · (Σ_s max(0, m_s − d_s) / Σ_s m_s)` | **λ = 0,50** | Metade da massa total dos objetivos: uma violação plena custa mais do que qualquer objetivo isolado pode pagar de volta, então um plano inviável nunca supera um viável; uma violação marginal continua recuperável | Regra de produto (cobertura mínima do edital), sem base teórica de aprendizagem |
| **P₁, P₂** `DropoutRiskPenalty`, `FatigueAndSustainabilityPenalty` | Risco de evasão; burnout | multiplicativas, `= 1,0` no caminho macro | — | Reservadas à camada tática, onde há cronograma real para avaliar. Neutras aqui por projeto, não por acidente | Engajamento/churn (não é um dos três fundamentos) |

**Notação:** `Î_s` = importância normalizada no simplex (`Σ Î_s = 1`); `w₃_s` = importância
**temperada** que só O₃ usa, `Î_s^0,5 / Σ_k Î_k^0,5` (também soma 1 — §3.3); `d_s` = dias alocados à
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
w₃_s = Î_s^0,5 / Σ_k Î_k^0,5
O₃   = Σ_s w₃_s · min(1, d_s / sessõesNecessárias(s))
```

**Derivação.** A curva de Ebbinghaus, `R = e^(−t/S)`, atinge `e⁻¹` após exatamente um intervalo de
estabilidade. Sessões espaçadas além de `τ` deixam a disciplina cair abaixo do ponto em que o
`HybridRetentionEngine` declara a revisão devida. Num horizonte de `H` dias, isso são `H/τ` sessões.
O termo é a fração do edital, ponderada por importância, que recebe sessões suficientes.

**Por que o peso não é o mesmo de O₁ (temperagem).** O₁ pergunta *"quantos pontos este plano rende
na prova?"*, e é correto ponderá-lo pelo valor de prova. O₃ pergunta outra coisa: *"quanto do que foi
estudado sobrevive até a prova?"* O custo de esquecer **não** é proporcional ao valor de prova —
esquecer uma disciplina de peso baixo desperdiça os dias já gastos nela, e esses dias custaram ao
aluno o mesmo que os dias gastos numa disciplina de peso alto. A ponderação natural da retenção é
mais achatada que a da pontuação. O expoente 0,5 é o ponto temperado entre uniforme (0) e
proporcional ao valor de prova (1): uma disciplina que vale 100× mais na prova pesa 10× mais aqui.

Enquanto os dois objetivos dividiram o **mesmo** vetor de pesos, eles concordavam em matar de fome as
mesmas disciplinas e nada na fitness fazia contrapeso. A varredura controlada de
[`06-regime-alta-carga.md`](./06-regime-alta-carga.md) mediu a consequência: acima de cerca de
**30:1** de dispersão efetiva de importância a correlação entre fitness e retenção prevista virava
negativa e se aprofundava com a dispersão. Com a temperagem essa fronteira passou para **~200:1**.
O expoente 0,5 **não foi calibrado** — é um ponto médio defensável, e nada mostra que 0,4 ou 0,6
seriam piores. *Implementação:* `EvolutionContext.temper`.

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

O `README.md` **atribuía** Ausubel ao multiplicador de lacuna de conhecimento. A atribuição estava
errada e **foi removida** — a regra implementada é "quanto maior a lacuna, maior a prioridade",
monotônica e sem noção de ancoragem, enquanto Ausubel implica o oposto num caso importante: material
cujos pré-requisitos faltam deveria ser **adiado** até que o pré-requisito seja coberto, não
promovido. A heurística de triagem é defensável por si; chamá-la de Ausubel não era.

A decisão de não implementar Ausubel nesta versão, as duas opções consideradas e os gatilhos para
reabri-la estão em [`06-decisao-ausubel.md`](./06-decisao-ausubel.md). Registro adicional levantado
lá: **a API não coleta pré-requisitos** — `SubjectDto` e `Subject` não têm o campo —, então o
bloqueio não é apenas de encoding, é também de dado e de conteúdo editorial por edital.

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
| `I7-horizonte-longo` | 4,6% / 3,1% | 2,3% / 3,5% | 0,0% / 0,0% | **4,6%** |
| `I8-escala` | 1,8% / 2,1% | 1,8% / 1,1% | 1,4% / 2,1% | **2,1%** |

**Afirmação sustentável:** *uma perturbação de ±20% em qualquer peso desloca no máximo 10,6% do
orçamento de dias, com mediana em torno de 4%.* Os pesos importam, mas não são knife-edge — o plano
não depende de eles estarem exatamente certos.

**Detalhe que merece registro:** o peso de retenção não move nada na maioria das instâncias. Não é
sinal de que o termo é inerte — ele muda o plano —, e sim de que na maioria dos casos o orçamento não
alcança a cobertura de todas as disciplinas, e o termo opera no seu regime **linear**, onde apenas
reordena por `w₃_s / sessõesNecessárias_s`. Só em horizontes longos (`I7`) e em escala (`I8`) o teto
começa a atar e a perturbação passa a importar.

*(Tabela remedida na etapa 06, depois da temperagem. `I7` e `I8` mudaram; as demais instâncias e o
pior caso global de 10,6% permanecem idênticos.)*

*Reprodução:* `benchmarks/…/robustness/WeightSensitivityMain` → `benchmarks/results/sensibilidade-pesos.csv`

---

## 6. Validação empírica

Mesmo harness e mesmas 8 instâncias sintéticas da etapa `03`. Todos os números desta seção foram
remedidos de ponta a ponta no fechamento da etapa 06, com a suíte completa (49 testes, 0 falhas).

### 6.1 Contra os baselines (positivo = AG melhor)

Três estados: **`03`** = fitness original; **`05`** = depois das correções de fidelidade;
**`06`** = depois da temperagem dos pesos de retenção. As colunas `vs uniforme` e `vs aleatório` são
do estado atual.

| Instância | vs guloso `03` | vs guloso `05` | vs guloso **`06`** | vs uniforme | vs aleatório | gap até o ótimo exato |
|---|---|---|---|---|---|---|
| `I1-pequeno-folgado` | +0,02% | +0,13% | **+1,51%** | +0,42% | +0,53% | −0,00% |
| `I2-medio-tipico` | +0,15% | +4,17% | **+3,66%** | +4,28% | +5,23% | −8,30% |
| `I3-grande-apertado` | +0,29% | +2,70% | **+2,71%** | +2,35% | +2,46% | −4,07% |
| `I4-pesos-extremos` | **−6,18%** | +4,29% | **+6,36%** | +7,45% | +8,59% | −8,03% |
| `I5-pesos-balanceados` | +0,14% | +5,49% | **+4,71%** | −0,17% | +1,00% | −6,97% |
| `I6-horizonte-curto` | +0,01% | +2,65% | **+2,73%** | +1,74% | +1,85% | −5,60% |
| `I7-horizonte-longo` | +0,10% | +0,41% | **+2,16%** | +1,77% | +2,05% | +0,19% |
| `I8-escala` | +0,44% | +0,67% | **+1,28%** | +3,56% | +4,36% | −1,68% |

**A vantagem sobre o guloso por prioridade se manteve ou melhorou em todas as 8 instâncias**, tanto
de `03` para `05` quanto de `05` para `06`. As duas quedas de `05` para `06` (`I2` −0,51 pp, `I5`
−0,78 pp) são deslocamentos dentro da margem: o AG continua ganhando com folga nas duas, e a
temperagem não custou vantagem competitiva em lugar nenhum. `I1` empata com sorteio porque é uma
instância saturada — poucas disciplinas, orçamento sobrando, todo plano viável está essencialmente no
ótimo. Em `I5` a divisão uniforme leva por 0,17%, o que também é ruído de instância saturada.

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
| `I8-escala` | dias com sobrecarga | **164,0** | 218,0 | 223,0 |
| | horas entregues / planejadas | **0,94** | 0,87 | 0,88 |
| `I3-grande-apertado` | dias com sobrecarga | **73,4** | 93,0 | 91,0 |

O AG passou a produzir planos com **menos sobrecarga cognitiva e maior taxa de entrega de horas do
que todos os baselines** — ganho de produto direto do termo de Sweller. A concentração em `I4` caiu
de 75,6% para 25,2%.

### 6.4 A anti-correlação: causa identificada e fronteira deslocada

O achado central de `03` §5 era que maximizar a fitness **piorava** a retenção. Correlação de
Spearman entre fitness e % na janela de retenção, sobre as 6 estratégias de cada instância:

| Instância | `03` (média de 10 exec.) | `05` | **`06`** |
|---|---|---|---|
| `I3-grande-apertado` | −0,937 ± 0,013 | −0,029 | **+0,257** |
| `I4-pesos-extremos` | −0,941 ± 0,000 | −0,525 | **−0,093** |
| `I7-horizonte-longo` | −0,847 ± 0,022 | +0,000 | **+0,655** |
| `I8-escala` | −0,880 ± 0,000 | −0,880 | **−0,880** |
| I1, I2, I5, I6 | indefinida | indefinida | indefinida |
| **Agregado (Fisher z, n = 4)** | **−0,909** | **−0,460** | **−0,106** |

O agregado vem de `CorrelationAggregate`, o **único** método sancionado — a §7 exige rodá-lo depois
de qualquer mudança na fitness. As quatro instâncias saturadas ficam de fora em vez de entrarem como
zero: elas não foram medidas como "sem correlação", foram medidas como "indefinida". Com n = 4, os
intervalos de confiança são largos (o de `06` cruza o zero), então **a direção da trajetória é
sólida e o valor pontual de cada etapa não é preciso** — dizer "converge para zero" é defensável,
dizer "é exatamente −0,106" não.

**A causa foi identificada na etapa 06 e não era a que `05` supunha.** A limitação registrada aqui
atribuía a anti-correlação ao aperto do orçamento. Três varreduras controladas
([`06-regime-alta-carga.md`](./06-regime-alta-carga.md)) refutaram essa hipótese com dados — a métrica satura
em 100% para todo planejador em razões de demanda de 0,5 a 5,0 — e também a hipótese do número de
disciplinas. O driver é a **dispersão efetiva da importância**, e o mecanismo é o da §3.3: enquanto
O₁ e O₃ dividiam o mesmo vetor de pesos, concordavam em abandonar as mesmas disciplinas.

Fronteira medida em 30 pontos ordenados por dispersão efetiva:

| | Antes da temperagem | Depois |
|---|---|---|
| Dispersão a partir da qual a correlação fica negativa | **~30:1** (entre 22:1 e 32:1) | **~200:1** |

Abaixo de 200:1 só `I8` permanece negativa; acima, a correlação segue negativa mas atenuada em cerca
de um terço.

**`I8` continua sendo a exceção não explicada.** Com 32:1 de dispersão — bem abaixo da nova fronteira
— mantém ρ = −0,880 inalterado. Duas observações sem as quais o número engana: (1) a **amplitude** da
retenção em `I8` é de 2,5 pontos percentuais (97,5% a 100%), então a correlação é forte em *ranking* e
desprezível em *magnitude* — nada a ver com os −0,941 sobre 50 pp que era o estado de `I4` em `03`;
(2) `I8` combina 40 disciplinas com um agendador tático que estuda só as três mais críticas por dia,
o que é hipótese plausível e **não medida**. Registrado como L4 revisada; não perseguido.

### 6.5 A divisão uniforme ainda vence a janela de retenção em `I3` e `I4`

Permanece verdade: 96,0% contra 76,6% em `I3`, 100% contra 81,4% em `I4`. A etapa 06 mediu essa troca
em vez de justificá-la, e o resultado desmonta a explicação que estava escrita aqui — de que seria
"a troca deliberada que os pesos codificam". Ela não é.

Decompondo a vantagem de fitness do AG sobre a divisão uniforme em `I3` (+0,0153): O₁ **−0,0052**,
O₃ **−0,0041**, O₄ **+0,0246**. **O AG perde nos dois termos de conteúdo e vence inteiramente pelo
termo de carga cognitiva** — e o mesmo padrão vale em `I4`. A troca real, portanto, não é
"pontuação × memória", é **"memória × sustentabilidade da agenda"**: em `I4` a divisão uniforme paga
a retenção com 124 dos 160 dias em sobrecarga cognitiva, contra 23 do AG.

Duas consequências práticas, ambas medidas em
[`06-limite-troca-pesos.md`](./06-limite-troca-pesos.md):

1. **Mexer nos pesos não resolveria.** Em `I3`, levar `w₃` de 0,30 a 0,70 compra 0,6 pp de janela ao
   custo de 3,64% de O₁, e piora `I7` nas duas dimensões. A métrica de janela é uma contagem com
   limiar e reage à **cauda** da alocação, não à média ponderada que O₃ mede.
2. **A alavanca que move a métrica é o piso de dias mínimos**, que vive em `BaselineCalculator` e não
   na fitness: um piso de 4 dias leva `I3` a 100% por 0,52% de O₁, sem regredir nenhuma instância.

Nada disso foi implementado. **É pendência de negócio aberta** — qual promessa de cobertura o produto
faz ao aluno —, registrada com as duas opções e seus preços no documento acima, aguardando decisão
humana. Os pesos seguem em 0,50/0,30/0,20 e o piso em 1.

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
6. **Se mexer nos pesos por disciplina** — `EvolutionContext.normalize` ou `.temper`, incluindo o
   expoente `RETENTION_TEMPERING` — **rode também `RegimeCorrelationMain`.** A temperagem existe por
   causa de um efeito que só aparece em dispersão alta e é invisível nas 8 instâncias do harness
   principal (§6.4).
7. **Atualize esta página.** Um peso alterado sem justificativa registrada deixa o produto incapaz de
   explicar as próprias decisões de planejamento — que é exatamente o estado que esta revisão existiu
   para corrigir.

Testes que protegem o que está documentado aqui:

| Teste | O que trava |
|---|---|
| `IndividualTest` | A fórmula agregada, com valores calculados à mão a partir desta página |
| `GeneticAlgorithmVsBaselinesTest` | O AG não fica 2% abaixo do guloso; supera o ótimo de O₁ em alguma instância |
| `GaEdgeCasesTest` | Determinismo, casos de borda, viabilidade dos planos |
| Asserção em `FitnessEvaluator` | Pesos somando 1 |

Medições que **não** têm teste e precisam ser rodadas à mão depois de qualquer mudança na fitness:
`BenchmarkMain` (§6.1, §6.3), `RegimeCorrelationMain` (§6.4), `WeightSensitivityMain` (§5.2),
`WeightTradeoffMain` (§6.5), `RobustnessMain` (`04`).

---

## 8. Limitações conhecidas

Registradas para que não sejam descobertas por um avaliador antes de nós.

| # | Limitação | Consequência | O que resolveria |
|---|---|---|---|
| L1 | **Ausubel não está implementado** | O produto não pode afirmar sequenciamento por pré-requisitos | Encoding indexado no tempo com grafo de dependências |
| L2 | **O₃ é aproximação de campo médio** | Não é repetição espaçada; não sabe *quando* a revisão ocorre | Encoding indexado no tempo |
| L3 | **O₄ limita carga esperada, não por episódio** | Um plano conforme ainda pode ter dias sobrecarregados | Encoding indexado no tempo |
| L4 | **Anti-correlação acima de ~200:1 de dispersão efetiva de importância**, mais a exceção não explicada de `I8` (32:1, ρ = −0,880 sobre amplitude de 2,5 pp) | Em editais com pesos muito dispersos a fitness ainda favorece concentração | Hipótese não medida para `I8`: `INTERLEAVING_FOCUS_SIZE` no agendador tático. Enunciado revisado na etapa 06 — a hipótese original (aperto de orçamento) **foi testada e não se confirmou**. Rastreada como **G12** em [`README.md`](./README.md) desde a etapa 08 |
| L5 | **Pesos não calibrados empiricamente** | Defensáveis por estabilidade medida, não por otimalidade | Dados de desempenho real de alunos |
| L6 | **Aderência a prazo não é otimizada** | O AG não sabe se o plano cabe no calendário; só o agendador descobre, depois | Encoding indexado no tempo |
| L7 | **Taxa de mutação 0,05 mal calibrada** | `04` §5 mediu que 0,15–0,30 recupera qualidade a custo de tempo nulo | Pendência P4 de `04`, aguardando decisão |
| L8 | **A divisão uniforme entrega mais retenção que o AG em `I3` e `I4`** | 5 de 25 e 2 de 10 disciplinas fora da janela de retenção; o AG compra isso com muito menos sobrecarga cognitiva | Decisão de produto pendente (`06-limite-troca-pesos.md`). A alavanca é o piso de `BaselineCalculator`, não os pesos. Rastreada como **G13** em [`README.md`](./README.md) desde a etapa 08 |
| L11 | **O agregado repousa em 4 de 8 instâncias, e elas discordam entre si** (I² = 58%) | A média é descrição fraca de qualquer instância; o IC de efeitos aleatórios cruza o zero | Amostra maior ou edital real. Decisão de não expandir agora, com gatilhos, em [`08-saturacao-e-amostragem.md`](./08-saturacao-e-amostragem.md) §5 |
| L9 | **O expoente 0,5 da temperagem não foi calibrado** | Ponto médio defensável entre uniforme e proporcional; 0,4 ou 0,6 não foram medidos | Varredura do expoente contra a métrica de janela de retenção |
| L10 | **A busca degrada em pesos extremos de retenção** | Com `w₃ ≥ 0,45` em `I3` o AG encontra planos piores sob o próprio objetivo do que os que encontra com os pesos de produção | Não investigado (`06-limite-troca-pesos.md` §4) |

**L4 e L8 são o mesmo fenômeno visto por duas réguas:** L4 mede a correlação entre fitness e retenção
sobre todas as estratégias, L8 mede a diferença absoluta entre dois planejadores específicos. A causa
comum é que O₃ é uma média ponderada e a métrica de negócio é uma contagem com limiar.

**L1, L2, L3 e L6 têm a mesma raiz:** o cromossomo `Map<Subject, Integer>` não tem coordenada
temporal nem ordem. É a decisão de arquitetura em aberto mais importante do motor, e está registrada
em `02` §5 como separada e explícita.

---

## 9. Fundamentos citados

| Fundamento | Fonte | Onde aparece | Fidelidade |
|---|---|---|---|
| Curva de esquecimento | Ebbinghaus, H. (1885). *Über das Gedächtnis* | O₃; `HybridRetentionEngine` (`R = e^(−t/S)`) | **Fórmula fiel; integração aproximada** (L2) |
| Ponderação temperada de O₃ | — | `EvolutionContext.temper`, expoente 0,5 | **Escolha nossa**, sem base na literatura. Motivada por medição (`06-regime-alta-carga.md`), não calibrada (L9) |
| Repetição espaçada / SM-2 | Wozniak & Gorzelanczyk (1994), algoritmo SuperMemo-2 | `HybridRetentionEngine.processReview`; EF inicial 2,5 e piso 1,3 são canônicos do SM-2 | Fiel na camada tática; **não** no macro |
| Teoria da carga cognitiva | Sweller, J. (1988). *Cognitive load during problem solving* | O₄; `CognitiveLoadCalculator` | **Aproximada** — carga esperada, não por episódio (L3) |
| Aprendizagem significativa | Ausubel, D. (1968). *Educational Psychology: A Cognitive View* | — | **Não implementado** (L1) |
| Curvas de prática com assíntota | Newell & Rosenbloom (1981) | O₁, forma `1 − e^(−d/τ)` | Forma funcional; τ é escolha nossa, não calibrada |
| Limite da escalarização por soma ponderada | Resultado padrão de otimização multiobjetivo: soma ponderada só alcança o casco convexo da fronteira de Pareto | Agregação; decisão em `02` | Limitação assumida e documentada |

**Distinção importante para auditoria:** as constantes herdadas do SM-2 (EF inicial 2,5, piso 1,3,
intervalos 1 e 6 dias) são canônicas do algoritmo publicado. As demais — τ de 10 dias na dificuldade
média, os pesos 0,50/0,30/0,20, o λ de 0,50, o expoente 0,5 da temperagem — são **escolhas nossas**, e estão marcadas como tal em
todo este documento. Nenhuma delas é apresentada como resultado da literatura.

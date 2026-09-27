# Medição 09 — Guloso (baseline) × AG com pré-requisitos táticos (v1), no domínio de tópicos

**Documentos anteriores:** [`README.md`](./README.md) (índice) ·
[`03-validacao.md`](./03-validacao.md) · [`04-robustez.md`](./04-robustez.md) ·
[`08-saturacao-e-amostragem.md`](./08-saturacao-e-amostragem.md)
**Este documento:** a **primeira** medição do sistema que existe hoje — `POST /plans`, dois motores,
domínio de tópicos. É também a linha de base contra a qual a v2 (cromossomo de linha do tempo) será
medida quando existir.
**Código:** `benchmarks/java/…/benchmark/` · esquema e política de build em
[`benchmarks/README.md`](../../benchmarks/README.md)
**Dados brutos:** [`benchmarks/results/measurement.csv`](../../benchmarks/results/measurement.csv)
— 720 linhas, uma por execução
**Alterou produção?** Não. Nenhuma linha de `src/main` mudou nesta etapa.

> ### ⚠ Nenhum número dos relatórios 03 a 08 atravessa para cá
>
> Aqueles relatórios mediram o caminho de concurso: a unidade de planejamento era a **disciplina**, a
> função objetivo tinha termos que foram removidos, e as instâncias eram editais sintéticos. Os três
> mudaram. Os CSVs antigos continuam no repositório, em
> `benchmarks/archive/2026-09-01-concurso-path/`, com um README que declara o commit que os produziu
> e o commit que removeu o sistema que eles mediam. **Eles não são comparáveis com nada desta
> página**, e o limiar de regressão de 2% daquela linha de trabalho não é herdável.

---

## 1. Veredito

| Pergunta | Resposta medida |
|---|---|
| A v1 remove inversões que o guloso deixa? | **Sim. 980 → 420** no total pareado (−57,1%). Estritamente menos em **261 de 360** células pareadas, empate em 99, **pior em nenhuma** |
| Quanto disso é o reparo e quanto é a ordenação? | Reparo: **−442**. Ordenação: **−118**. O reparo responde por 78,9% da redução |
| A que custo? | **23,3× mais tempo** (mediana pareada), 60 gerações, **2 440 avaliações** por execução. Em números absolutos: mediana de 198 µs contra 6 272 µs |
| Onde a diferença aparece? | Cresce com a densidade do grafo (+1,01 → +2,11 por célula), com o tamanho (+1,17 → +1,94) e com a largura da condição de proveniência (+0,88 → +2,04) |
| Onde ela desaparece? | Onde há poucas arestas `SOFT` aplicáveis. Das 99 células empatadas, **59 estão em `curated`**, a condição mais estreita (2,5 arestas em média) |
| Alguma inversão `HARD`? | **Nenhuma**, em 720 execuções e 6 condições. A regra de parada (a) não disparou |
| Alguma execução não reprodutível? | **Nenhuma**, em 720 pares de execuções. A regra de parada (b) não disparou |
| A v1 é melhor em tudo? | **Não.** Ela usa mais o calendário (+7,8 pontos percentuais de utilização) e agenda **menos tópicos** (−0,12 em média, pior em 146 células de 360). Ver §7 |

**A afirmação que esta medição sustenta é estreita e é a que a EOA-7 fez:** a etapa tática de
pré-requisitos reduz as inversões de preferência que o guloso deixa, de forma consistente, sem nunca
piorá-las, e sem violar nenhuma restrição rígida. Ela **não** sustenta que a v1 produz planos
melhores em geral — §7 mostra um eixo em que ela é pior, e §8 mostra que a função objetivo agregada
está saturada demais para arbitrar.

---

## 2. Método

### 2.1 As instâncias

Fatorial explícito e completamente cruzado, gerado deterministicamente a partir de uma semente fixa
no repositório (`InstanceLibrary.GENERATOR_SEED = 20260925`):

| Eixo | Níveis | O que ele varia |
|---|---|---|
| `topics` | 10, 25 | tamanho do currículo |
| `density` | 0,4 / 1,2 | arestas de pré-requisito por tópico |
| `tightness` | 0,7 / 1,0 / 1,5 | minutos disponíveis ÷ minutos de primeira passada |
| `spread` | compacto / esparso | os mesmos minutos em dias seguidos, ou dia sim dia não |

2 × 2 × 3 × 2 = **24 instâncias**.

**Por que a variedade de aperto importa mais que o tamanho.** Um currículo maior deixa todo motor
mais lento sem mudar o que qualquer um deles decide. Um calendário que não cabe o currículo obriga
os dois a escolher o que deixar de fora — e é aí que uma etapa de ordenação pode diferir de outra.
Por isso `tightness` tem três níveis e `topics` tem dois. `tightness = 0,7` não cabe as primeiras
passadas; `1,0` cabe exatamente; `1,5` sobra para revisões.

**`spread` não é tamanho disfarçado.** Ele mantém os minutos disponíveis fixos e dobra o calendário
sobre o qual eles se espalham, o que reduz pela metade as horas por dia de horizonte — a única
entrada de que o `SinapseLoadBudget` deriva o teto diário de carga.

Três propriedades que o gerador garante, e a razão de cada uma:

1. **Três disciplinas, em três prioridades distintas (5, 3, 1).** A estratégia de importância padrão
   é `goal-priority`, que dá a **todo tópico de uma disciplina o mesmo peso**. Uma biblioteca com uma
   disciplina só achataria `syllabusMastery` — metade da fitness — numa constante, e o motor genético
   ficaria sem sinal no termo que o domina. Medir a busca em instâncias onde o sinal principal dela é
   constante subestimaria a busca, e subestimaria invisivelmente.
2. **Todo grafo `HARD` é acíclico sob qualquer subconjunto de proveniência.** As arestas rígidas só
   apontam de um índice de chegada menor para um maior. Uma instância recusada por ciclo mediria o
   caminho de recusa, não os escalonadores.
3. **Toda janela cabe o tópico mais longo** (janelas de 240 min, tópicos de 30 a 180 min). Um tópico
   maior que toda janela não cabe em lugar nenhum e a instância colapsa numa recusa de plano vazio,
   independentemente do motor.

### 2.2 As condições e as sementes

**Condição = motor × proveniência.** 2 motores (`greedy-baseline`, `ga`) × 3 condições de ablação
(`curated` ⊂ `curated-textbook` ⊂ `all`) = **6 condições**. `importance` é mantida fixa em
`goal-priority` — não é um eixo desta medição — e ainda assim escrita em toda requisição e registrada
em toda linha.

**Sementes: 5 valores distintos** (`20260925` a `20260929`). Total: 24 × 6 × 5 = **720 linhas**.

> **Por que sementes distintas, e por que a comparação é pareada.** Com semente fixa **não existe
> ruído de medição**: a mesma requisição devolve o mesmo plano até o último bit — o que o harness
> verifica em vez de supor, em toda linha. Repetir a mesma semente, portanto, não mede nada, e
> tirar média sobre repetições dela reportaria um desvio de zero como se fosse um desvio. O que
> varia é *qual* semente. Por isso a unidade de replicação são sementes distintas por instância, e
> toda comparação entre motores nesta página é **pareada por (instância, proveniência, semente)**:
> 360 células, cada uma com os dois motores sobre exatamente a mesma entrada.

**Toda execução passa pelo `PlanEngineSelector`**, o mesmo ponto de entrada que a plataforma chama —
não pelos motores diretamente, embora ambos sejam beans que o harness poderia injetar. É o seletor
que carimba `fitness.engine`, e um motor não consegue se rotular errado através dele: a atribuição
de cada linha é do caminho de produção, não da contabilidade do harness.

### 2.3 As quatro famílias de métrica, e por que a fitness não é uma delas

| Grupo | Conteúdo | Papel |
|---|---|---|
| **(a) invariantes** | zero inversões `HARD`; sessões dentro das janelas; sem sobreposição; `sequenceIndex` contíguo; reprodutibilidade | **validade, não qualidade** |
| **(b) resultado** | inversões `SOFT` residuais, removidas pelo reparo, tópicos agendados e não agendados, utilização, teto de carga, distribuição de dificuldade | **é aqui que as conclusões se apoiam** |
| **(c) custo** | gerações, avaliações, tempo de parede | o que a busca cobrou |
| **(d) objetivo** | *F* e o `FitnessBreakdown` completo | **objetivo do AG, relatado para completude** |

> **Fitness agregada não é métrica de comparação entre motores.** O motor genético *maximiza* `F`; o
> guloso não otimiza função objetivo nenhuma e não relata nenhuma. Comparar por `F` seria comparar
> uma grandeza que um motor foi construído para maximizar contra uma que o outro nunca viu —
> enviesado por construção, na direção de quem a inventou.
>
> Por isso **todo plano produzido, de qualquer motor, é repontuado a posteriori pelo mesmo
> avaliador**: a resposta que veio pelo fio é reconstruída no cromossomo tático que ela descreve e
> entregue à composição de produção, **injetada do contexto Spring e não reconstruída**. Os dois
> motores são então julgados em termos idênticos. E mesmo assim o número sai rotulado
> `ga_objective_*`, porque continua sendo a coisa que um motor otimiza e o outro ignora.

**A repontuação é verificada, não assumida.** Ela se apoia numa afirmação — que a ida e volta por
`PlanResponse` não perde nada que a fitness leia — e essa afirmação é checável exatamente uma vez: no
motor genético, que já publicou o escore que calculou sobre o próprio plano em memória. O harness
compara os dois **bit a bit** e aborta se diferirem. Nas 360 linhas do motor genético, o valor
recalculado bateu com `fitness.aggregate` em **360 de 360**. A reconstrução é fiel.

### 2.4 Máquina

Intel Xeon @ 2,80 GHz, 4 vCPU, 15 GiB, OpenJDK 21.0.10, Linux. Os tempos de §6 são de parede, nesta
máquina, e são comparáveis **entre os motores da mesma execução** e em nenhum outro lugar.

---

## 3. As duas regras de parada: nenhuma disparou

A tarefa fixou duas condições que interrompem a medição em vez de registrá-la:

| Regra | O que ela detecta | Resultado |
|---|---|---|
| **(a)** Qualquer inversão `HARD`, em qualquer condição | Defeito da v1 — significaria que o critério de aceitação da EOA-7 não foi de fato satisfeito | **Não disparou.** 720 execuções, 0 inversões rígidas |
| **(b)** Falha de reprodutibilidade | Os números não são os números | **Não disparou.** 720 pares de execuções, 720 assinaturas idênticas |

**A reprodutibilidade vale também entre JVMs, e isso é mais forte do que parece.** Rodando o
`MeasurementMain` duas vezes em processos separados, as 720 linhas saem idênticas em **todas as 50
colunas** exceto `elapsed_micros`, que é tempo de parede. Isso importa porque a ordem de iteração de
um mapa de `Map.copyOf` é embaralhada por uma semente aleatória **por execução da JVM**, e essa ordem
entra numa soma de ponto flutuante dentro de `EvolutionContext.normalize` — foi exatamente o defeito
que `SinapseAdapterIsolationTest` existe para impedir, e que já apareceu uma vez como falha
intermitente de 1 em 3. Uma diferença entre duas JVMs seria o sintoma dele. Não houve nenhuma.

As duas checagens são feitas pelo harness **de forma independente** de `PlanOutputInvariants`, que já
recusaria a maior parte delas dentro do motor. É exatamente por isso: um instrumento que confia no
sistema sob teste para se policiar não mede nada quando o sistema erra duas vezes do mesmo jeito. As
checagens do harness leem apenas a requisição e a resposta, e não compartilham código com o
escalonador.

**A checagem de inversão rígida é verificada por sabotagem.** `MeasurementHarnessTest` pega uma
resposta válida, adianta a última sessão para o instante da primeira — criando uma inversão por
construção — e exige que a checagem reprove. Uma checagem que nunca reprova não verifica nada, e as
720 linhas teriam sido escritas por um aparelho cego.

---

## 4. Resultado principal: inversões `SOFT`

Contagem sobre as 360 células pareadas.

| | guloso | v1 | diferença |
|---|--:|--:|--:|
| Total de inversões residuais | **980** | **420** | **−560 (−57,1%)** |
| Média por célula | 2,722 | 1,167 | −1,556 |
| Células em que o motor chega a **zero** inversões | 15 / 360 | **121 / 360** | ×8,1 |
| Células em que a v1 é estritamente melhor | — | **261 / 360 (72,5%)** | — |
| Células empatadas | — | 99 / 360 | — |
| Células em que a v1 é **pior** | — | **0** | — |

**A v1 nunca produz mais inversões que o guloso, em nenhuma das 360 células.** Isso é mais forte do
que a média sugere e é a forma em que a afirmação deve ser citada: não "a v1 melhora em média", e sim
**"a v1 domina o guloso nesta métrica, sem contraexemplo em 360 comparações pareadas"**.

### 4.1 Por instância

**Método de agregação declarado:** cada linha soma as 15 execuções daquela instância — 3 condições de
proveniência × 5 sementes — para cada motor. As colunas de tempo são **medianas** dessas 15 (médias
seriam puxadas pelo aquecimento do JIT). As colunas de diferença são **médias das diferenças
pareadas** célula a célula, nunca a diferença das médias.

| instancia | T | D | A | espalh. | inv. guloso | inv. v1 | delta | reparo | us guloso | us v1 | dif. util. | dif. topicos |
|---|--:|--:|--:|---|--:|--:|--:|--:|--:|--:|--:|--:|
| `t10-d0.4-a0.7-compact` | 10 | 0.4 | 0.7 | compacto | 25 | 0 | **-25** | 15 | 638 | 4424 | +0.100 | +0.00 |
| `t10-d0.4-a0.7-sparse` | 10 | 0.4 | 0.7 | esparso | 25 | 0 | **-25** | 15 | 288 | 3842 | +0.146 | +2.00 |
| `t10-d0.4-a1.0-compact` | 10 | 0.4 | 1.0 | compacto | 25 | 0 | **-25** | 15 | 304 | 4596 | -0.041 | -1.00 |
| `t10-d0.4-a1.0-sparse` | 10 | 0.4 | 1.0 | esparso | 15 | 0 | **-15** | 15 | 283 | 3393 | -0.093 | -1.33 |
| `t10-d0.4-a1.5-compact` | 10 | 0.4 | 1.5 | compacto | 25 | 15 | **-10** | 0 | 269 | 3217 | +0.161 | -1.00 |
| `t10-d0.4-a1.5-sparse` | 10 | 0.4 | 1.5 | esparso | 25 | 0 | **-25** | 15 | 243 | 3175 | +0.258 | +0.00 |
| `t10-d1.2-a0.7-compact` | 10 | 1.2 | 0.7 | compacto | 30 | 10 | **-20** | 20 | 200 | 3221 | +0.102 | +1.67 |
| `t10-d1.2-a0.7-sparse` | 10 | 1.2 | 0.7 | esparso | 35 | 25 | **-10** | 5 | 141 | 2959 | +0.120 | +2.33 |
| `t10-d1.2-a1.0-compact` | 10 | 1.2 | 1.0 | compacto | 30 | 10 | **-20** | 20 | 152 | 3026 | +0.012 | +0.00 |
| `t10-d1.2-a1.0-sparse` | 10 | 1.2 | 1.0 | esparso | 35 | 10 | **-25** | 20 | 150 | 2959 | +0.084 | +0.00 |
| `t10-d1.2-a1.5-compact` | 10 | 1.2 | 1.5 | compacto | 40 | 30 | **-10** | 0 | 189 | 3084 | +0.150 | +0.00 |
| `t10-d1.2-a1.5-sparse` | 10 | 1.2 | 1.5 | esparso | 15 | 15 | **+0** | 0 | 125 | 2976 | +0.110 | -1.67 |
| `t25-d0.4-a0.7-compact` | 25 | 0.4 | 0.7 | compacto | 30 | 20 | **-10** | 5 | 205 | 6447 | +0.106 | +2.33 |
| `t25-d0.4-a0.7-sparse` | 25 | 0.4 | 0.7 | esparso | 15 | 10 | **-5** | 5 | 294 | 6190 | +0.009 | +0.00 |
| `t25-d0.4-a1.0-compact` | 25 | 0.4 | 1.0 | compacto | 15 | 15 | **+0** | 10 | 191 | 6488 | +0.095 | +1.80 |
| `t25-d0.4-a1.0-sparse` | 25 | 0.4 | 1.0 | esparso | 15 | 15 | **+0** | 10 | 170 | 6190 | -0.036 | -1.00 |
| `t25-d0.4-a1.5-compact` | 25 | 0.4 | 1.5 | compacto | 10 | 5 | **-5** | 18 | 191 | 6257 | +0.095 | -2.33 |
| `t25-d0.4-a1.5-sparse` | 25 | 0.4 | 1.5 | esparso | 50 | 14 | **-36** | 25 | 212 | 6585 | +0.127 | -2.13 |
| `t25-d1.2-a0.7-compact` | 25 | 1.2 | 0.7 | compacto | 100 | 30 | **-70** | 45 | 228 | 6701 | +0.026 | +0.00 |
| `t25-d1.2-a0.7-sparse` | 25 | 1.2 | 0.7 | esparso | 95 | 25 | **-70** | 50 | 226 | 6161 | +0.069 | -1.33 |
| `t25-d1.2-a1.0-compact` | 25 | 1.2 | 1.0 | compacto | 100 | 70 | **-30** | 5 | 191 | 6368 | +0.087 | +3.20 |
| `t25-d1.2-a1.0-sparse` | 25 | 1.2 | 1.0 | esparso | 70 | 35 | **-35** | 40 | 199 | 6054 | -0.071 | -1.33 |
| `t25-d1.2-a1.5-compact` | 25 | 1.2 | 1.5 | compacto | 80 | 24 | **-56** | 57 | 198 | 6219 | +0.139 | -1.33 |
| `t25-d1.2-a1.5-sparse` | 25 | 1.2 | 1.5 | esparso | 75 | 42 | **-33** | 32 | 201 | 6608 | +0.111 | -1.80 |
| **total** | | | | | **980** | **420** | **-560** | **442** | | | | |

`T` = tópicos, `D` = densidade, `A` = aperto (`tightness`). `reparo` = inversões que o passo de
reparo removeu; ele não existe no guloso.

**Duas leituras que a tabela deixa explícitas.** A maior diferença absoluta está nas duas instâncias
grandes, densas e apertadas (`t25-d1.2-a0.7-compact` e `-sparse`, −70 cada). As **três** instâncias
sem diferença nenhuma são `t10-d1.2-a1.5-sparse`, `t25-d0.4-a1.0-compact` e `t25-d0.4-a1.0-sparse`:
nas duas últimas o grafo é esparso (densidade 0,4) e o guloso já produz poucas inversões; na primeira
o grafo é denso mas o calendário é folgado (aperto 1,5) e o reparo não removeu nada — a coluna
`reparo` é 0 ali, e os dois motores terminam com a mesma contagem total de 15.

### 4.2 Por condição de proveniência

| proveniencia | arestas SOFT (media) | inv. guloso | inv. v1 | delta | celulas com melhora | empates |
|---|--:|--:|--:|--:|--:|--:|
| `curated` | 2.50 | 180 | 74 | **-106** | 61/120 | 59 |
| `curated-textbook` | 4.92 | 345 | 136 | **-209** | 100/120 | 20 |
| `all` | 6.83 | 455 | 210 | **-245** | 100/120 | 20 |

A ablação se comporta como o desenho previa: as três condições são cumulativas
(`curated` ⊂ `curated-textbook` ⊂ `all`), o número de preferências aplicáveis cresce com elas, e o
ganho da v1 cresce junto. **A condição mais estreita é a que menos distingue os motores** — com 2,50
arestas `SOFT` em média, metade das células empata.

Vale registrar o que isso diz sobre a ablação em si: se o produto acabar rodando apenas com arestas
curadas, metade do benefício medido da etapa tática não se materializa, porque metade das células
não tem preferência suficiente para inverter.

---

## 5. Decomposição: quanto é ordenação e quanto é reparo

Os dois motores diferem em **duas** coisas, não em uma. Eles ordenam os tópicos por critérios
diferentes — o guloso por prioridade de meta, depois data, depois posição curricular; o genético pela
alocação que a busca escolheu — e só o genético roda o passo de reparo. Separar os dois efeitos é
possível porque o motor genético publica a contagem **antes** do reparo:

| Estado | Total de inversões | Efeito |
|---|--:|--:|
| Ordem do guloso | 980 | — |
| Ordem da v1, **antes** do reparo | 862 | ordenação sozinha: **−118** |
| Ordem da v1, **depois** do reparo | 420 | reparo sozinho: **−442** |

**O reparo responde por 78,9% da redução; a diferença de ordenação, por 21,1%.** Isso importa para a
v2: a maior parte do ganho da v1 vem de um passo que **não** está na busca — é um pós-processamento
determinístico sobre a ordem que o cromossomo induziu. A v2 propõe mover a ordem para dentro do
cromossomo, e o que ela tem de superar é o reparo, não a ordenação macro.

O reparo removeu ao menos uma inversão em **253 de 360** execuções do motor genético, com média de
1,228 por execução.

**O guloso não repara, e isso é decisão registrada, não omissão.** Ele relata as inversões que
produziu e **omite a chave `-before-repair`** — a coluna `inversions_removed` sai vazia, não zero,
porque "removeu nenhuma" e "nenhum reparo foi tentado" são fatos diferentes. Ensinar o reparo ao
baseline faria os dois motores diferirem por uma coisa a menos e a comparação medir duas mudanças ao
mesmo tempo (`CLAUDE.md` §1b; `GreedyBaselineSchedulerTest.softEdgesDoNotConstrainTheOrder` prende
isso).

---

## 6. Custo

| Motor | mediana | p10 | p90 | máximo | gerações | avaliações |
|---|--:|--:|--:|--:|--:|--:|
| `greedy-baseline` | **198 µs** | 143 | 375 | 18 869 | 0 | 0 |
| `ga` (v1) | **6 272 µs** | 3 111 | 7 452 | 37 127 | 60 | **2 440** |

**Razão pareada (v1 ÷ guloso): 23,3× na mediana.**

Por tamanho de currículo:

| | 10 tópicos | 25 tópicos |
|---|--:|--:|
| `greedy-baseline` | 218 µs | 190 µs |
| `ga` (v1) | 3 283 µs | 6 574 µs |

O guloso é indiferente ao tamanho nesta faixa; o genético dobra. Os máximos das duas colunas (18,9 ms
e 37,1 ms) são as primeiras execuções da JVM, com o aquecimento do JIT dentro — é por isso que esta
tabela lê **medianas** e não médias. **Estas são as únicas colunas do CSV que mudam de uma execução
do `MeasurementMain` para outra** (§3).

**As avaliações são exatas e derivadas, não contadas.** `Population.calculateFitness` é chamada em
exatamente dois lugares: uma vez por `DefaultPopulationGenerator.generate` e uma vez por geração em
`GeneticAlgorithm.evolvePopulation`; a seleção e os operadores de cruzamento leem o valor já em cache
no `Individual`. Logo `avaliações = população × (1 + gerações) = 40 × 61 = 2 440`, mais o único
`explain` sobre o plano vencedor. Contá-las exigiria um contador no caminho de produção existindo só
para o harness.

**O tempo é medido pelo harness, de fora.** `metadata.elapsedMillis` é zero por construção nos dois
motores, de propósito: uma duração medida dentro da resposta faria duas execuções da mesma requisição
semeada diferirem, e a reprodutibilidade é requisito do experimento.

---

## 7. Onde a diferença aparece, e onde ela desaparece

Diferença pareada em inversões (guloso − v1), média por célula. Positivo = v1 melhor.

| Eixo | Nível | n | diferença média | células com melhora |
|---|---|--:|--:|--:|
| `topics` | 10 | 180 | +1,167 | 140 |
| | 25 | 180 | **+1,944** | 121 |
| `density` | 0,4 | 180 | +1,006 | 120 |
| | 1,2 | 180 | **+2,106** | 141 |
| `tightness` | 0,7 | 120 | **+1,958** | 95 |
| | 1,0 | 120 | +1,250 | 85 |
| | 1,5 | 120 | +1,458 | 81 |
| `spread` | compacto | 180 | +1,561 | 130 |
| | esparso | 180 | +1,550 | 131 |
| `provenance` | `curated` | 120 | +0,883 | 61 |
| | `curated-textbook` | 120 | +1,742 | 100 |
| | `all` | 120 | **+2,042** | 100 |

**Onde aparece:** quanto mais restrito o problema de ordem, maior a diferença. Ela cresce
monotonicamente com a densidade do grafo, com o tamanho do currículo e com a largura da condição de
proveniência — os três eixos que aumentam o número de preferências que podem ser violadas.

**Onde desaparece:** onde há pouca preferência aplicável. Das 99 células empatadas, **59 estão em
`curated`** (média de 2,50 arestas `SOFT` aplicáveis), contra 20 em cada uma das outras duas
condições. Em 15 dessas 99 os dois motores já estavam em zero inversões — não há o que melhorar.

**`spread` não move nada.** +1,561 contra +1,550 é uma diferença sem significado. A geometria do
calendário não afeta esta métrica, o que é coerente: as inversões são uma propriedade da **ordem**, e
espalhar os mesmos minutos por mais dias não reordena nada.

---

## 8. O que a v1 **não** melhora

Esta seção existe porque a seção 4 sozinha seria propaganda.

| Métrica de resultado | guloso | v1 | diferença pareada | v1 melhor / pior / empate |
|---|--:|--:|--:|---|
| `utilisation` | 0,6935 | **0,7713** | **+0,0777** | 300 / 60 / 0 |
| `topics_scheduled` | **13,639** | 13,517 | **−0,122** | 90 / **146** / 124 |
| `first_quarter_share` | 0,2594 | **0,2186** | −0,0408 | 119 / 241 / 0 |
| `peak_over_mean` | **1,6727** | 1,6945 | +0,0218 | 187 / 173 / 0 |
| `load_excess_ratio` | 0,0018 | **0,0002** | −0,0016 | 6 / 15 / 339 |

**A v1 usa mais o calendário e entrega menos tópicos.** Não é contradição: ela gasta os minutos a
mais em **revisões**. Média de 117 minutos por tópico agendado contra 104 do guloso. Em 91 das 146
células em que a v1 agenda menos tópicos, ela alocou **mais** minutos que o guloso.

Isso é uma troca real e é exatamente a troca que a função objetivo pede — `retention` pesa 0,30 e só
sobe com sessões repetidas — mas **ninguém decidiu que essa é a troca certa para o produto**. Um
aluno que recebe 13 tópicos com revisão e outro que recebe 14 sem revisão não receberam a mesma coisa,
e qual dos dois está melhor servido é decisão de produto e não consequência de um peso.

**`first_quarter_share`**: a v1 concentra *menos* esforço no primeiro quarto do calendário
(0,219 contra 0,259), ou seja, distribui mais. **`peak_over_mean`** é praticamente empate (187 contra
173 células) — nenhum dos dois nivela os dias de forma consistente.

**O teto diário de carga quase nunca morde**: verdadeiro em 15 execuções do guloso e 6 da v1, de 360
cada. Isso é coerente com o que `DailyLoadBudgetBindingRateTest` já observa, e nesta biblioteca não
muda a conclusão de nenhuma seção.

---

## 9. Achado: a fitness agregada está saturada e não arbitra nada

Não era o objeto desta medição e apareceu nela.

| | valor |
|---|--:|
| Linhas com `ga_objective_aggregate = 0` | **681 de 720 (94,6%)** |
| `MandatoryReviewConstraint` com severidade 1,0 | **720 de 720 (100%)** |
| Linhas com `aggregate > 0` | 39, **todas** em `tightness = 1,5` |

`MandatoryReviewConstraint` é **binária**: um único tópico sem revisão obrigatória agendada e a
severidade vai a 1,0. Com peso 0,50, ela subtrai meio ponto de **toda** soma. Os três objetivos somam
no máximo 1,0; `MinimumDaysConstraint` subtrai mais um tanto; o resultado é uma soma bruta negativa
que o `clamp` leva a zero.

**Consequências, na ordem em que importam:**

1. **`F` não distingue planos em 94,6% desta biblioteca.** Não é ruído — é um piso. Dois planos
   materialmente diferentes recebem o mesmo zero.
2. **Isso não invalida nada nesta página**, porque nenhuma conclusão aqui se apoia em `F`. É
   precisamente o motivo metodológico pelo qual `F` foi posta no grupo (d) e rotulada como objetivo
   do AG em vez de métrica de comparação. Se a comparação entre motores tivesse sido feita por
   fitness agregada, ela teria dado empate em 94,6% dos casos e a medição não teria resultado nenhum.
3. **Não é defeito desta biblioteca de instâncias.** É o comportamento de uma restrição binária num
   domínio onde o calendário raramente cabe uma revisão de todo tópico. A biblioteca só o tornou
   visível.
4. **Nada foi alterado em produção por causa disto.** Corrigir a saturação — graduar a restrição em
   vez de mantê-la binária, ou repesá-la — é mudança na função objetivo, que muda o que o AG procura,
   e não se faz como efeito colateral de uma medição. Fica registrado aqui como **pendência G14**.

---

## 10. Variância entre sementes, e a proposta de limiar para o CI

> **Nenhum limiar de regressão foi configurado nesta etapa.** A instrução foi medir a variância
> primeiro e **propor** um limiar com os números por trás. O que segue é proposta, não configuração.

### 10.1 A variância medida

Por célula (instância × proveniência), sobre as 5 sementes:

| Motor | células idênticas nas 5 sementes | desvio de `soft_inversions` (média / máx.) | amplitude (média / máx.) | desvio de `utilisation` (máx.) |
|---|--:|--:|--:|--:|
| `greedy-baseline` | **72 / 72** | 0,0000 / 0,0000 | 0,000 / 0 | 0,000000 |
| `ga` (v1) | 42 / 72 | 0,0358 / 0,4899 | 0,083 / **1** | 0,037771 |

**O guloso tem variância exatamente zero**, o que era esperado da leitura do código —
`GreedyBaselineScheduler` ecoa `randomSeed` na metadata e nunca o lê — e agora é **medido** em vez de
argumentado. As 5 sementes foram rodadas nele de propósito por isso.

Ao nível da matriz inteira (soma de inversões sobre as 24 instâncias e 3 proveniências, por semente):

| Motor | por semente | mínimo | máximo | amplitude | % da média |
|---|---|--:|--:|--:|--:|
| `greedy-baseline` | 196, 196, 196, 196, 196 | 196 | 196 | **0** | 0,00% |
| `ga` (v1) | 86, 82, 84, 84, 84 | 82 | 86 | **4** | **4,76%** |

Tópicos agendados, mesmo agregado: guloso 982 nas cinco sementes; v1 varia de 965 a 985 (amplitude
20). Utilização média: guloso idêntica em seis casas; v1 varia 0,00719.

### 10.2 A proposta, em três partes

**Parte 1 — duas travas binárias, sem limiar nenhum.** Zero inversões `HARD` e reprodutibilidade não
são números com faixa: são propriedades que valem ou não valem. Elas já estão dentro do harness e
dentro de `MeasurementHarnessTest`, que roda na suíte. **Nada a propor além de mantê-las.**

**Parte 2 — dominância pareada, também sem limiar.** A afirmação que a v1 faz é
`inversões(v1) ≤ inversões(guloso)` na mesma célula. Ela valeu em **360 de 360** comparações, sem um
contraexemplo. Uma trava sem limiar é estritamente melhor que um número calibrado: não há o que
ajustar, não há o que discutir quando reprova, e ela reprova exatamente quando a afirmação do produto
deixa de ser verdadeira. **Proposta: travar a dominância, não uma margem.**

**Parte 3 — um teto sobre o total, e só aqui entra um número.** A dominância não pega uma degradação
em que a v1 piora e o guloso piora junto. Para isso vale um teto sobre o total da matriz. A
derivação:

| Passo | Valor |
|---|--:|
| Máximo observado sobre as 5 sementes | 86 |
| Amplitude observada entre sementes | 4 (4,76% da média) |
| Margem proposta: 2 × a amplitude observada | 8 |
| **Teto proposto** | **94, arredondado para 95** |

Duas vezes a amplitude e não uma porque 5 sementes são poucas: o máximo sobre 5 amostras subestima o
máximo verdadeiro, e um teto colado nele reprovaria por sorte de sorteio. Duas vezes a amplitude é a
margem mais estreita que não tem esse defeito e ainda pega uma regressão de 10% — que é uma ordem de
grandeza acima de qualquer coisa que a estocasticidade produziu aqui.

**Parte 4 — o custo trava em avaliações, nunca em tempo de parede.** `elapsed_micros` é uma
propriedade da máquina, e um teto sobre ele transformaria um agente de CI mais lento numa reprovação
de qualidade. `evaluations` é exatamente 2 440, determinístico, e muda se e somente se o orçamento de
busca mudar. **Proposta: travar `evaluations`, publicar `elapsed_micros`.**

### 10.3 O que falta antes de configurar qualquer uma das partes

1. **Mais sementes.** 5 bastam para dizer que a variância existe e é pequena; não bastam para
   calibrar uma margem com confiança. 20 sementes custariam ~16 s de CPU nesta máquina.
2. **Decidir se o CI roda a matriz inteira ou um recorte.** A matriz inteira leva ~4 s, o que é
   barato — mas 720 linhas por build é muito dado para um gate.
3. **G14 (§9) antes da parte 3, se a parte 3 vier a citar `F`.** Enquanto a fitness agregada estiver
   saturada, um limiar sobre ela não mediria nada. O teto proposto aqui é sobre inversões, que não
   têm esse problema — mas isso é escolha e precisa ficar dita.

---

## 11. Limitações

1. **Instâncias sintéticas.** Os grafos de pré-requisito são gerados, não autorados por especialista.
   A densidade e a distribuição de proveniência são escolhas do gerador. A **forma** dos resultados
   (dominância, crescimento com a densidade) é robusta a isso; os **valores** não são transferíveis
   para um currículo real.
2. **Uma máquina, uma JVM, uma execução.** Os tempos de §6 não sobrevivem a uma troca de máquina. As
   contagens sobrevivem, porque são determinísticas.
3. **`importance` fixa em `goal-priority`.** A estratégia `prerequisite-centrality` não foi medida.
   Ela é a que mais plausivelmente interage com a ordenação — é literalmente derivada do grafo — e
   fica como o eixo mais óbvio a acrescentar.
4. **5 sementes.** Ver §10.3.
5. **A fitness agregada não arbitra** (§9). Um leitor que queira "qual motor produz o plano melhor"
   não vai encontrar essa resposta aqui, e não vai encontrar porque a pergunta não tem resposta
   medível enquanto `F` estiver no piso.
6. **`tightness` é medida sobre primeiras passadas apenas.** O denominador é a soma dos
   `estimatedMinutes`; as revisões (metade da duração, arredondada para cima) não entram nele. Então
   `tightness = 1,0` não significa "cabe exatamente o plano", significa "cabe exatamente uma passada
   por tópico".

---

## 12. O que isto habilita

A precondição metodológica da EOA-8 (v2, cromossomo de linha do tempo) era: *v1 implementada **e
medida***. A v1 está medida, e o que a v2 precisa superar está quantificado:

| Alvo | Valor a bater |
|---|--:|
| Inversões `SOFT` residuais, total pareado | **420** |
| Células com zero inversões | **121 / 360** |
| Tópicos agendados, média | 13,517 |
| Utilização média | 0,7713 |
| Tempo, mediana | 6 272 µs |
| Avaliações | 2 440 |

E a observação que mais deveria orientar o desenho da v2 está em §5: **78,9% do ganho da v1 vem do
reparo, não da busca.** Uma v2 que interiorize a ordem no cromossomo compete com um
pós-processamento determinístico que já resolve quase quatro quintos do problema, e que custa
praticamente nada. Se ela não superar isso, a resposta correta é que a v1 já era suficiente — e essa
é uma conclusão legítima que esta linha de trabalho precisa poder alcançar.

A v2 entra na medição como **uma linha em `Condition.ENGINES`**. Nenhuma métrica, nenhuma
invariante, nenhuma coluna do CSV muda.

---

## 13. Pendência acrescentada nesta etapa

| # | Gap | Status | Onde |
|---|---|---|---|
| **G14** | **`MandatoryReviewConstraint` é binária e satura: severidade 1,0 em 720 de 720 execuções, levando `ga_objective_aggregate` a zero em 94,6% delas.** A fitness agregada não distingue planos nesta biblioteca. Não afeta as conclusões desta página, que não se apoiam nela; afeta qualquer uso futuro de `F` como critério, incluindo um limiar de CI sobre ela | ⬜ **ABERTO** — caracterizado, não corrigido. Graduar a restrição ou repesá-la é mudança na função objetivo e não se faz como efeito colateral de uma medição | `ga/fitness/constraint/MandatoryReviewConstraint` · §9 desta página |

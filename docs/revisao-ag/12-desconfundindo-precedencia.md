# Medição 12 — Desconfundindo representação de política de precedência

**Documentos anteriores:** [`README.md`](./README.md) ·
[`10-medicao-v2-linha-do-tempo.md`](./10-medicao-v2-linha-do-tempo.md) ·
[`11-varredura-peso-ordem.md`](./11-varredura-peso-ordem.md)
**Este documento:** o fatorial 2×2 representação × política, e a previsão falsificável que ele testa.
**Código:** `plan/PrecedencePolicy`, `plan/PrecedencePolicies`, `sinapse/RequestConditions`,
`sinapse/SearchVitality`, `benchmarks/.../harness/PrecedenceCellsMain`

---

> ## ⚠ Esta seção foi escrita e versionada ANTES de a medição rodar
>
> O commit que a introduz não contém dado nenhum das quatro células. Isso é verificável no histórico, e
> é o ponto: as seções §1 a §4 abaixo são **pré-registro**. Escolher a estatística depois de ver os
> números é o jardim dos caminhos que se bifurcam, e nos dados de ontem ele estava escancarado —
> **quatro estatísticas, duas respostas**:
>
> | estatística | quem ganha |
> |---|---|
> | total de inversões | v1 (420 contra 674) |
> | células zeradas | v2 (147 contra 121, em λ = 0,40) |
> | zeradas pareadas | v2, 54 a 15 |
> | pior célula | v1 (7 contra 9) |
>
> Com esse grau de liberdade, qualquer conclusão é alcançável escolhendo a régua depois. Então a régua
> é escolhida antes.

---

## 1. A previsão que está sendo testada

A v2 publicada combina **duas** mudanças em relação à v1: a representação (linha do tempo em vez de
alocação macro) e a política de precedência (preço ponderado em vez de reparo lexicográfico). A
comparação entre elas, portanto, não diz qual das duas explica o quê.

O relatório 11 mostrou que **política não se reduz a peso**: nenhum λ admissível alcança as 420
inversões da v1, e isso era esperado — reparo lexicográfico **fixa** a métrica, peso apenas a
**precifica**. Incentivo não iguala garantia na dimensão garantida.

O que sobrou sem explicação é o outro lado: a v2 aloca **~1,5 tópico mais** que a v1 em todo λ
(15,0–15,3 contra 13,52) e zera a ordem por completo em mais células. Duas leituras disputam isso:

* **A representação explica o ganho.** A linha do tempo coloca melhor, e bastaria dar-lhe o reparo
  lexicográfico para ter as duas coisas.
* **A política explica o ganho.** Não reparar libera capacidade: a vantagem de cobertura é consequência
  de *não corrigir a ordem*, e a representação não contribui.

A célula que decide é **v2′ = linha do tempo + reparo lexicográfico**, usando o reparador que já
existe aplicado à saída da linha do tempo.

### Os dois desfechos possíveis, declarados agora

| se… | então… |
|---|---|
| a v2′ **igualar ou bater** a v1 em inversões **e preservar** a vantagem de cobertura | a representação é o que explica o ganho. A resposta é linha do tempo com reparo lexicográfico, e a afirmação sobre representação se sustenta |
| a v2′ **perder cobertura até o nível da v1** | a vantagem era consequência da **política**, e a representação não contribui. A afirmação cai |

---

## 2. O desfecho primário, e por que este

> **Primário: a comparação pareada por célula, v2′ contra v1, em inversões `SOFT` — vitórias, empates
> e derrotas sobre as 360 células.**

Três razões, em ordem de peso.

**A unidade de entrega é um aluno, não uma coorte.** O serviço responde um plano por requisição. Somar
inversões sobre 360 células responde "quantas inversões existem no total", que é a pergunta de quem
administra um agregado; a pergunta do produto é "o plano deste aluno está bem ordenado?". A célula é a
unidade de análise porque é a unidade de entrega.

**O pareamento remove os dois confundidores que sobraram.** Instância e semente explicam muito da
variação — a v2 tem 11% de amplitude entre sementes (relatório 11 §7). Comparar v2′ e v1 na mesma
instância, mesma proveniência e mesma semente elimina os dois de uma vez, o que uma média sobre
totais não faz.

**Vitória/empate/derrota não exige uma função de dano que ninguém mediu.** O total pressupõe dano
**aditivo**: nove inversões são nove vezes uma. A pior célula pressupõe dano **categórico**: um plano
mal ordenado é ruim independentemente de quão mal. As duas são hipóteses sobre a experiência do aluno,
e **nenhuma das duas foi medida neste projeto**. Uma estatística ordinal não escolhe entre elas, e é
a escolha honesta quando a função de dano é desconhecida.

### O critério de cobertura, também declarado agora

"Preservar a vantagem de cobertura" precisa de número, ou é interpretável depois. A v1 aloca **13,52**
tópicos e a v2 **15,23**, uma vantagem de **1,71**. Declaro:

| zona | critério | leitura |
|---|---|---|
| **preservada** | v2′ ≥ **14,38** tópicos | ao menos **metade** da vantagem sobrevive |
| ambígua | entre 13,70 e 14,38 | a vantagem encolheu materialmente; nenhuma das duas leituras se sustenta sozinha |
| **perdida** | v2′ ≤ **13,70** | caiu ao nível da v1 (13,52 mais 10% da vantagem como folga) |

### As outras três estatísticas são secundárias, e serão reportadas todas

Total de inversões, células zeradas, zeradas pareadas e pior célula entram no relatório **inclusive
quando contrariarem a primária**. Uma secundária que contraria a primária é informação sobre a forma
da distribuição, não um resultado a ser escolhido no lugar dela.

---

## 3. A faixa admissível de λ

**Nada acima de `CONSTRAINT_VIOLATION` (0,50) entra em conclusão.** Acima daquele ponto uma
*preferência* violada custa mais que um *requisito* violado, e o vetor de pesos deixa de ser coerente
com a distinção que o contrato faz entre `HARD` e `SOFT`. Um resultado medido ali descreve um sistema
que ninguém proporia.

Consequência imediata, aplicada retroativamente ao relatório 11: **a evidência de bimodalidade passa a
ser citada em λ = 0,40, não em 0,80.** Em 0,40 a v2 zera 147 células contra 121 da v1 e a conclusão
sobrevive; as 160 células de 0,80 vêm de uma configuração incoerente e não devem sustentar afirmação.

As quatro células deste relatório rodam em **λ = 0,10**, o valor de produção, que é a configuração que
de fato atenderia um aluno. O braço lexicográfico depende de λ apenas através da busca — o reparo fixa
a métrica depois —, e o braço ponderado tem a sua sensibilidade a λ caracterizada no relatório 11.

---

## 4. A degeneração do espaço de busca da v1 — achada antes de medir, e o que se faz com ela

A invariante de **vitalidade da busca**, construída nesta etapa, disparou na primeira execução. Não na
v2: **na v1**.

Em **12 das 24 instâncias**, `SessionBudget` é exatamente a soma dos pisos por item, a folga é zero,
`StudyPlanFactory` não tem nada para distribuir, e **os 40 indivíduos da geração zero são o mesmo
plano**. O espaço de busca da v1 é um ponto único.

| coordenada | instâncias degeneradas |
|---|---|
| aperto 0,7 | **8 de 8** |
| aperto 1,0, esparso | **4 de 4** |
| aperto 1,0, compacto | 0 de 4 (folga 1) |
| aperto 1,5 | 0 de 8 (folga 5 a 13) |

O mecanismo: `SinapseMinimumDays` dá a cada tópico `ceil(estimatedMinutes / 240) = 1` sessão de piso,
logo a soma dos pisos é o número de tópicos; e em aperto ≤ 1,0 o calendário não comporta mais de uma
sessão por tópico. Orçamento e piso coincidem.

**Isso não é defeito do algoritmo genético nem da biblioteca — é uma propriedade da interação entre
os dois**, e tem três consequências que precisam ficar ditas:

1. **Em metade do fatorial a v1 não é uma busca.** É um alocador determinístico seguido de ordenação,
   reparo e colocação. As 2 440 avaliações reavaliam 40 cópias de um plano.
2. **Nessas 12 instâncias, "a v1 ganha" não é evidência sobre busca nem sobre representação.** É
   evidência de que um alocador forçado mais um reparo lexicográfico produz menos inversões que uma
   busca ponderada — afirmação legítima sobre motores, e muda nada sobre as duas hipóteses de §1.
3. **Explica a variância baixa da v1** registrada no relatório 09 (42 de 72 células idênticas entre
   cinco sementes): onde o espaço é um ponto, a semente não tem como importar.

### O que se faz com isso, decidido antes de ver os resultados

**O desfecho primário continua sobre as 24 instâncias.** Mover a população depois de descobrir uma
propriedade dos dados é precisamente o caminho que se bifurca que §2 existe para fechar.

**E uma secundária pré-declarada restringe às 12 instâncias não degeneradas** — aperto 1,0 compacto e
aperto 1,5 —, onde os dois braços de fato buscam. Só ali a comparação é sobre busca. Se primária e
secundária discordarem, a discordância é o achado, e será relatada como tal.

### A invariante foi dividida, e a razão importa

Ela nasceu esperando que empate total na geração zero fosse defeito. A medição mostrou que é
propriedade da instância. Então:

* **aborta**: a melhor aptidão final pior que a inicial, que significa elitismo quebrado — defeito de
  motor, conserta-se em código;
* **registra**: população inicial totalmente empatada, na coluna `search_initial_distinct` — fato sobre
  a comparação, que o relatório tem de dizer em vez de o harness esconder.

Recusar teria recusado 12 instâncias legítimas da biblioteca. Uma invariante que refuta a realidade
em vez de descrevê-la é uma invariante errada.

---

*As seções de resultado começam abaixo desta linha, e foram escritas depois de rodar.*

## 5. O desfecho primário

**Dados brutos:** [`benchmarks/results/precedence-cells.csv`](../../benchmarks/results/precedence-cells.csv)
— **1 440 linhas** (24 instâncias × 4 células × 3 proveniências × 5 sementes), 136 s, λ = 0,10,
ambiente em [`precedence-cells-environment.txt`](../../benchmarks/results/precedence-cells-environment.txt).
**Alterou produção?** Só a abertura da política como parâmetro de requisição, com o comportamento
publicado de cada motor como padrão. **`plan.engine.default` continua `greedy-baseline`.**

> **A v2′ bate a v1 em inversões e preserva a cobertura.** É o **primeiro** dos dois desfechos
> declarados em §1: *a representação é o que explica o ganho*.

| desfecho primário — pareado por célula, 360 células | |
|---|--:|
| a v2′ ordena **melhor** que a v1 | **92** |
| empate | 220 |
| a v2′ ordena **pior** que a v1 | **48** |
| teste de sinais, bilateral | **p = 0,00025** |

| critério de cobertura (§2) | medido |
|---|--:|
| zona **preservada** declarada | ≥ 14,38 tópicos |
| **v2′** | **14,82** |
| v1 | 13,52 |
| fração da vantagem de 1,71 que sobrevive ao reparo | **76%** |

A cobertura cai na zona **preservada**, com folga, e o pareamento concorda: a v2′ agenda mais tópicos
em **254** células, empata em 88 e agenda menos em **18** — e nessas 18 o déficit é de **1 tópico em 17
delas e 2 numa**.

**Os dois lados ao mesmo tempo, que era exatamente o que §1 declarou como o desfecho decisivo.** A
regra de parada do enunciado se aplica a este resultado, e ela foi obedecida: o relatório para aqui, na
descrição, e **não segue para as consequências** — nem para mudar padrão de motor, nem para reabrir o
que a tese afirma. Isso é decisão de quem lê.

---

## 6. As quatro células

Inversões `SOFT` somadas sobre as 360 células de cada braço (média por célula entre parênteses):

| | `LEXICOGRAPHIC` (reparo) | `WEIGHTED` (preço, λ = 0,10) | efeito da política |
|---|--:|--:|--:|
| **macro** (`ga`) | **420** (1,167) = *v1* | **862** (2,394) = *v1″* | **+1,228** |
| **linha do tempo** (`ga-timeline`) | **362** (1,006) = *v2′* | **674** (1,872) = *v2* | **+0,867** |
| **efeito da representação** | **−0,161** | **−0,522** | |

Tópicos agendados, as mesmas quatro células:

| | `LEXICOGRAPHIC` | `WEIGHTED` | efeito da política |
|---|--:|--:|--:|
| **macro** | 13,5167 | 13,5083 | **−0,008** |
| **linha do tempo** | 14,8222 | 15,2278 | +0,406 |
| **efeito da representação** | **+1,306** | **+1,719** | |

| efeito | inversões/célula | tópicos | utilização |
|---|--:|--:|--:|
| principal, **representação** (linha do tempo − macro) | **−0,342** | **+1,513** | **+0,064** |
| principal, **política** (preço − reparo) | +1,047 | +0,199 | +0,009 |
| interação | −0,361 | +0,414 | +0,020 |

**Três leituras, e a segunda é a que fecha a pergunta.**

**A política domina a dimensão de ordem, como o relatório 11 previa.** Trocar reparo por preço custa
+1,05 inversão por célula — maior que o efeito da representação por um fator de três. Reparo **fixa** a
métrica; preço apenas a precifica, e os números dizem o tamanho da diferença.

**A hipótese "não reparar libera capacidade" está refutada, e a célula que a refuta é a v1″.** Na
representação macro, deixar de reparar muda a cobertura em **−0,008 tópico** — zero dentro de qualquer
leitura. Se a vantagem de cobertura da v2 fosse consequência de não corrigir a ordem, ela apareceria
aqui também, e não aparece. A cobertura é **da representação**: +1,31 tópico na coluna do reparo, +1,72
na coluna do preço, e o efeito principal é **+1,51**.

**A interação existe e é pequena.** O ganho de cobertura por não reparar é zero na macro e +0,41 na
linha do tempo — ou seja, a linha do tempo *tem* o que perder ao ser reparada, porque tem um calendário
que o reparo reembaralha, enquanto a macro não tem calendário no cromossomo. Isso é o preço do reparo
sobre a representação nova, e ele consome 24% da vantagem, não ela toda.

---

## 7. As quatro secundárias, todas — inclusive a que contraria

| estatística | v1 | v2′ | quem ganha |
|---|--:|--:|---|
| **total** de inversões | 420 | **362** | **v2′** |
| **células zeradas** | 121 | **169** | **v2′** |
| **zeradas pareadas** | 10 | **58** | **v2′** (p = 2,4 × 10⁻⁹) |
| **pior célula** | 7 | **6** | **v2′** |
| *células com ≥ 4 inversões* | ***15*** | *21* | ***v1*** |

**As quatro secundárias pré-declaradas concordam com a primária.** Essa concordância é informativa
porque ontem elas discordavam: a tabela de aviso no topo deste documento registra quatro estatísticas e
duas respostas, e hoje as quatro apontam para o mesmo lado. Não houve escolha de régua a fazer.

**A quinta estatística contraria, e entra pelo mesmo motivo.** A contagem de células com quatro ou mais
inversões — que o relatório 11 §6 usou para caracterizar a v2 como bimodal — ainda favorece a v1, 15
contra 21. Ela é reportada porque foi usada antes e porque descreve a cauda, não porque muda a
conclusão: a **pior** célula da v2′ é melhor (6 contra 7), então a cauda da v2′ é mais **densa** e mais
**curta** que a da v2 (que ia a 9), e a bimodalidade que o relatório 11 descreveu é atenuada pelo
reparo, não eliminada.

A forma completa das diferenças pareadas:

| v2′ − v1 | −4 | −3 | −2 | −1 | 0 | +1 | +2 |
|---|--:|--:|--:|--:|--:|--:|--:|
| células | 6 | 4 | 7 | 75 | 220 | 29 | 19 |

**A assimetria é o achado por trás da primária.** Quando a v2′ perde, perde **1 ou 2** inversões —
nunca mais. Quando ganha, ganha até **4**. Somadas, as perdas valem 67 inversões e os ganhos 125.

Distribuição por célula, as quatro células:

| inversões na célula | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| **v1** | 121 | 138 | 60 | 26 | 1 | 9 | — | 5 | — | — |
| **v1″** | 7 | 156 | 74 | 58 | 6 | 28 | 2 | 26 | 1 | 2 |
| **v2′** | **169** | 107 | 32 | 31 | 10 | 8 | 3 | — | — | — |
| **v2** | 124 | 63 | 69 | 46 | 12 | 14 | 17 | 8 | 4 | 3 |

---

## 8. A secundária pré-declarada: as 12 instâncias não degeneradas

§4 declarou, antes de rodar, que uma secundária restringiria às 12 instâncias em que os dois braços de
fato buscam — aperto 1,0 compacto e aperto 1,5 — porque nas outras 12 a população inicial da v1 é um
ponto único. **Primária e secundária concordam.**

| | células | v2′ melhor | empate | v2′ pior | p | tópicos v2′ | tópicos v1 |
|---|--:|--:|--:|--:|--:|--:|--:|
| **24 instâncias** (primária) | 360 | **92** | 220 | 48 | **0,00025** | 14,82 | 13,52 |
| **12 não degeneradas** (secundária) | 180 | **45** | 118 | 17 | **0,00050** | 16,84 | 15,42 |
| 12 degeneradas (completude) | 180 | 47 | 102 | 31 | 0,089 | 12,81 | 11,61 |

**Onde os dois braços buscam, a conclusão é mais limpa, não menos.** Na metade não degenerada a v2′
ganha 45 a 17; na metade degenerada, 47 a 31, e ali o teste de sinais **não** atinge significância
(p = 0,089). A leitura honesta: o resultado é sustentado pela metade do fatorial em que a comparação é
sobre busca, e na outra metade — em que a v1 é um alocador determinístico e não uma busca — a vantagem
da v2′ é menor e não distinguível de ruído de pareamento.

Isso **reforça** a conclusão de §5 em vez de a enfraquecer, porque o braço degenerado é o que mais
favorece a v1: lá a v1 tem inversões máximas de 3 e nenhuma célula com ≥ 4.

| | zeradas pareadas (v2′ só / v1 só) | pior célula v2′ / v1 | ≥4 v2′ / v1 |
|---|---|---|---|
| 12 não degeneradas | 28 / 4 | 6 / 7 | 11 / 15 |
| 12 degeneradas | 30 / 6 | 5 / 3 | 10 / 0 |

Na metade degenerada a cauda da v1 é **vazia** (zero células com ≥ 4) e a da v2′ tem 10. É aí, e só aí,
que vive a quinta estatística de §7.

---

## 9. O custo

| braço | mediana | p90 | avaliações | × v1 |
|---|--:|--:|--:|--:|
| v1 | 4 695 µs | 6 258 | 2 440 | 1,0 |
| v1″ | 4 048 µs | 5 361 | 2 440 | 0,9 |
| **v2′** | **91 126 µs** | 174 138 | 2 440 | **19,4** |
| v2 | 75 217 µs | 173 716 | 2 440 | 16,0 |

**A v2′ é a mais cara das quatro, e por uma razão estrutural.** Ela paga o custo da linha do tempo
(G17 reduziu-o de 1,37×, e o que sobra é a reconstrução de `TacticalStudyPlan` por descendente) **mais**
um reparo e uma recolocação de blocos ao final. Os 19,4× contra a v1 são o número a pôr na mesa de
qualquer decisão sobre padrão de motor; o número não é pequeno e o relatório não o suaviza.

Mediana e p90 continuam sendo medidas desta máquina — o `-environment.txt` ao lado do CSV registra
qual. O relatório 10 §6 mostrou a mediana do guloso andar 138 → 167 µs entre execuções sem mudança de
código, então comparar estes números com os de outra execução é inválido; comparar **entre braços da
mesma execução**, que é o que a tabela faz, é válido.

---

## 10. O objetivo agregado, com a ressalva de sempre

| braço | `raw` | `aggregate` |
|---|--:|--:|
| v1 | −0,1322 | +0,0366 |
| v1″ | −0,1520 | +0,0347 |
| v2′ | −0,0350 | +0,0914 |
| v2 | **+0,3032** | **+0,3116** |

**Isto não é uma comparação entre motores**, pela razão que o `benchmarks/README.md` declara: o motor
genético maximiza `F` e o guloso não otimiza objetivo nenhum. Entre os quatro braços daqui a
comparação é menos inválida — todos são buscas sobre a mesma composição injetada — mas continua
enviesada pela mesma assimetria, numa direção que vale dizer:

**a v2 ganha no objetivo porque o objetivo é o critério que ela otimizou.** A v2′ roda a mesma busca e
depois **reordena o plano por fora do critério**, então o reparo necessariamente a afasta do ótimo que
a busca encontrou: −0,34 de `raw`. Que ela ainda fique acima da v1 (−0,035 contra −0,132) é
informação; que fique abaixo da v2 é tautologia, não desvantagem.

Esta é a mesma distinção de §5 do relatório 10, aplicada a um caso novo: o desfecho que importa é
propriedade do cronograma, e `ga_objective_*` é publicado por completude.

---

## 11. Onde a vantagem está

Diferença v2′ − v1, por coordenada da biblioteca:

| coordenada | inversões v2′ | inversões v1 | Δ tópicos |
|---|--:|--:|--:|
| 10 tópicos | 113 | 115 | **+0,73** |
| 25 tópicos | **249** | **305** | **+1,88** |
| densidade 0,4 | **57** | **94** | +1,52 |
| densidade 1,2 | 305 | 326 | +1,09 |
| aperto 0,7 | *132* | *120* | +0,93 |
| aperto 1,0 | **85** | **155** | +1,86 |
| aperto 1,5 | 145 | 145 | +1,13 |
| compacto | **152** | **229** | +1,32 |
| esparso | *210* | *191* | +1,29 |

**A vantagem de ordem é concentrada, não uniforme.** Ela vem de 25 tópicos (−56 inversões), de
calendário médio (−70 em aperto 1,0) e de disponibilidade compacta (−77). Em **aperto 0,7** e em
**esparso** a v1 continua à frente — e as duas coordenadas se sobrepõem fortemente com a degeneração
de §4, o que é consistente com §8.

**A vantagem de cobertura é uniforme.** Positiva em todas as nove linhas, de +0,73 a +1,88. Nenhuma
coordenada da biblioteca a inverte.

---

## 12. As duas células publicadas reproduziram exatamente

Regra de parada do enunciado: *se a v1 mudar qualquer número após o refactor, pare — contaminou a
condição de controle.* Ela foi verificada e não suposta.

`ga/lexicographic` é a v1 do relatório 09 e `ga-timeline/weighted` é a v2 do relatório 10: nomear a
política explicitamente resolve para o padrão que cada motor já tinha. As **720** linhas
correspondentes foram comparadas com `measurement.csv` coluna por coluna.

| verificação | resultado |
|---|---|
| células comparadas | 720 (2 motores × 24 instâncias × 3 proveniências × 5 sementes) |
| colunas comparadas | **50** (todas, exceto `elapsed_micros` e as quatro colunas novas) |
| diferenças | **nenhuma** |
| inversões v1 | 420 publicadas, **420** agora |
| inversões v2 | 674 publicadas, **674** agora |
| tópicos v1 / v2 | 13,5167 / 15,2278, idênticos |

O eixo de política não tocou nenhuma das duas condições de controle.

### A vitalidade da busca, nas quatro células

| braço | aborta? | população inicial toda empatada | melhor final > melhor inicial |
|---|---|--:|--:|
| v1 | não | **180 / 360** | 117 / 360 |
| v1″ | não | **180 / 360** | 117 / 360 |
| v2′ | não | 0 / 360 | **360 / 360** |
| v2 | não | 0 / 360 | **360 / 360** |

**Nenhum aborto em 1 440 execuções**, que é o critério de aceitação na forma em que a invariante o pode
cumprir. A coluna do meio é a degeneração de §4, agora medida: metade das células da v1 começa de um
ponto único, e nelas a busca não tem como melhorar — daí 117 de 360, que são as células não degeneradas
em que ela melhora. Os dois braços da linha do tempo partem de populações distintas e melhoram em
**todas** as 360.

**Isto é um desvio declarado do critério de aceitação do enunciado**, que pedia "vitalidade da busca
verde em todas as quatro células". Lido como "nenhuma população inicial empatada", o critério recusaria
12 instâncias legítimas da biblioteca — ver §4, onde a invariante foi dividida e a razão registrada. Foi
lido como "nenhum aborto", e a degeneração passou a ser **reportada** em vez de recusada.

---

## 13. O que fica para quem lê, e o que não foi feito

**O que está medido.** A representação explica a cobertura (+1,51 tópico de efeito principal, e a
célula v1″ refuta a explicação alternativa). A política explica a ordem (+1,05 inversão de efeito
principal). As duas são separáveis, e a combinação que vence nas duas dimensões é **linha do tempo com
reparo lexicográfico**, por 19,4× o custo da v1.

**O que não foi feito, e por quê.** A regra de parada do enunciado diz que este resultado *reordena o
resto do roteiro e muda o que a tese afirma*, e manda relatar antes de qualquer análise adicional.
Então, deliberadamente, **não** foram feitos aqui:

* nenhuma mudança de `plan.engine.default`, que continua `greedy-baseline`;
* nenhuma varredura de λ sobre a v2′ (o braço lexicográfico depende de λ só através da busca);
* nenhuma releitura das conclusões dos relatórios 09, 10 e 11 à luz desta — os números deles estão
  intactos e reproduzidos, e o que a v2′ muda na *interpretação* é decisão de quem lê;
* nenhuma proposta de v3.

---

## 14. Limites

**Três sementes a mais não mudariam a classe do resultado, mas cinco é pouco para a cauda.** A primária
é ordinal e tem p = 0,00025 com 140 células não empatadas; as contagens de cauda (10 células com ≥ 4
contra 15) são números pequenos e devem ser lidas como tal.

**As células de uma instância não são independentes.** O teste de sinais sobre 360 células trata
proveniência e semente como réplicas independentes dentro da instância, e elas não são — o pareamento
remove o efeito de instância do *contraste*, não a correlação entre células. A secundária por instância
de §8 e a tabela por coordenada de §11 existem para que a conclusão não dependa só do p sobre células.

**Nenhuma função de dano foi medida.** §2 declarou isso como razão para escolher uma estatística
ordinal, e continua valendo: que a v2′ perca por 1 ou 2 e ganhe por até 4 só é uma boa troca se o dano
for aproximadamente aditivo nessa faixa, e **ninguém mediu isso neste projeto**.

**O custo não foi otimizado nesta etapa.** Os 19,4× são o estado atual, não um limite: G17 já mostrou
1,37× de folga achável com perfilamento, e a recolocação de blocos do reparo não foi perfilada.

---

## 15. Pendências

| # | pendência | estado | onde |
|---|---|---|---|
| **G19** | **Qual perfil o produto prefere** — a v2′ atenua a bimodalidade (pior célula 6 contra 9 da v2) mas mantém mais cauda que a v1 | ⬜ **ABERTO — decisão de produto** | §7 |
| **G20** | **A degeneração do espaço de busca da v1 em 12 de 24 instâncias.** `SessionBudget == totalFloor` em todo aperto ≤ 1,0 esparso. Não é defeito, mas significa que metade da biblioteca não mede busca | ⬜ **ABERTO** — a decisão é da biblioteca: ou se aceita e se declara, ou se acrescentam instâncias com folga | §4, §8 |
| **G21** | **O custo da v2′ (19,4× a v1) não foi perfilado.** O reparo e a recolocação de blocos são novos no caminho quente e nunca passaram por JFR | ⬜ **ABERTO** | §9, §14 |
| **G22** | **A função de dano de uma inversão `SOFT` não foi medida**, e três estatísticas deste relatório dependem de qual se assume | ⬜ **ABERTO — precisa de dado de aprendizagem** | §2, §14 |

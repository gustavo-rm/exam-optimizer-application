# Medição 11 — Varredura de `SOFT_PREREQUISITE_ORDER`: o peso não é o que separa a v1 da v2

**Documentos anteriores:** [`README.md`](./README.md) (índice) ·
[`09-medicao-baseline-vs-v1.md`](./09-medicao-baseline-vs-v1.md) ·
[`10-medicao-v2-linha-do-tempo.md`](./10-medicao-v2-linha-do-tempo.md)
**Este documento:** a pendência **G16**. Seis pontos de λ, a v2 medida em cada um, pelas mesmas
instâncias, sementes, invariantes e métricas de resultado.
**Código:** `benchmarks/.../harness/WeightSweepMain` ·
`ga/fitness/constraint/SoftPrerequisiteOrderConstraint`
**Dados brutos:** [`benchmarks/results/soft-weight-sweep.csv`](../../benchmarks/results/soft-weight-sweep.csv)
— **2 880 linhas**, 466 s
**Alterou produção?** Só a abertura do peso como chave de configuração, com o valor canônico por
padrão. **Nenhum número de produção mudou.**

---

## 1. Veredito

> **A varredura responde G16 com um "não" útil.** O peso **funciona** — subi-lo reduz as inversões da
> v2 de forma monotônica, de 856 a 509 — e **não é o que separa a v2 da v1**. Mesmo em λ = 0,80, oito
> vezes o valor de produção e já acima de `CONSTRAINT_VIOLATION` (0,50), a v2 fica em 509 contra as
> **420** da v1.
>
> A diferença entre reparo lexicográfico e preço ponderado **não se fecha por preço**. Era exatamente
> a hipótese que a etapa 10 não conseguia testar, e ela foi testada e refutada.

| Pergunta | Resposta medida |
|---|---|
| O peso guia a busca da v2? | **Sim, monotonicamente.** 856 → 708 → 674 → 624 → 556 → **509** inversões, de λ = 0,00 a 0,80 |
| Subir o peso fecha a distância até a v1? | **Não.** 556 contra 420 em λ = 0,40, o maior ponto **admissível** (§2.3); e 509 contra 420 mesmo em λ = 0,80, fora da faixa |
| Quanto custa subir o peso? | **Muito pouco.** Tópicos agendados 15,27 → 15,02; utilização 0,8490 → 0,8344. A troca é bem mais suave do que o retrato em λ = 0,10 sugeria |
| A v2 mantém a vantagem de cobertura? | **Em toda a varredura.** Em λ = 0,40 agenda 15,14 tópicos contra 13,52 da v1 (0,839 de calendário contra 0,771), e mesmo em 0,80 ainda 15,02 |
| λ = 0,10 é um bom valor? | **No agregado, sim** — o objetivo canônico é plano entre 0,00 e 0,20 e tem máximo em 0,05–0,10. **Por célula, não há maioria**: 53% das células preferem λ ≤ 0,05 (§5) |
| O ótimo por célula acompanha algum fator da biblioteca? | **Não** (§5.1). O que parecia correlação com o tamanho é artefato da convenção de empate, e 80% da variação do ótimo está *dentro* da instância |
| As conclusões dependem dessa constante? | **Não**, e isso é resultado e não ressalva (§5.2): os três desfechos dominantes valem em toda a faixa admissível |
| O plano da v1 depende de λ? | **Não**, e isto foi verificado e não suposto (§3) |
| Alguma inversão `HARD` ou falha de reprodutibilidade? | **Nenhuma**, em 2 880 execuções sobre seis composições diferentes |

**A recomendação é não subir o peso.** Ele está dentro do ótimo achatado do próprio critério, subi-lo
não compra a comparação, e o que sobra da diferença entre v1 e v2 é estrutural.

---

## 2. Método

Seis pontos: **0,00 · 0,05 · 0,10 · 0,20 · 0,40 · 0,80**. `0,00` é o controle — a ordem deixa de ter
preço e a v2 só otimiza cobertura e retenção. `0,10` é produção. Acima dele os pontos **dobram**,
porque o que se procura é a ordem de grandeza em que o comportamento vira, não um ótimo fino. `0,80`
está acima de `CONSTRAINT_VIOLATION` (0,50), ou seja, além do ponto em que uma preferência passaria a
custar mais que um requisito — que é onde o argumento de ordenação diz que não se deve ir, e portanto
onde interessa ver o que acontece.

O peso virou chave de configuração (`plan.fitness.sinapse.soft-prerequisite-order`), com
`FitnessWeights.SOFT_PREREQUISITE_ORDER` como padrão. **Nenhuma renormalização segue disto**:
restrições são subtraídas e não fazem parte da combinação convexa, e `FitnessEvaluator` afirma que os
pesos dos *objetivos* somam 1 — este não é um deles.

### 2.1 O que é comparável entre pontos, e o que não é

As métricas de **resultado** são propriedades do cronograma e não dependem de λ: inversões, tópicos
agendados, utilização. São diretamente comparáveis, e é nelas que a conclusão se apoia.

A coluna de **objetivo** não é: cada ponto a calcula com o seu próprio λ, então
`ga_objective_aggregate` em λ = 0,80 e em λ = 0,05 são valores de funções diferentes. Ela pode ser
trazida a um λ comum por **aritmética exata sobre as colunas publicadas**, porque o CSV carrega a
severidade e o peso do termo separadamente:

```
raw_canônico = raw + λ_varrido × severidade − 0,10 × severidade
```

É a coluna `raw@0,10` das tabelas abaixo. **Nenhuma composição foi reconstruída** — a regra de que o
avaliador a posteriori é o injetado, nunca um recriado, vale em cada um dos seis contextos.

**Esta aritmética só é válida porque a composição é LINEAR em λ**, e quem repetir o método precisa da
condição, não só da fórmula. `FitnessEvaluator` subtrai `Σ peso × severidade`: o termo entra uma única
vez, multiplicado pelo seu peso, sem produto com outro termo e sem passar por função não linear. Logo
desfazer `λ_varrido × severidade` e aplicar `0,10 × severidade` **recupera exatamente** o valor que
aquele plano teria recebido sob o λ canônico — é a mesma função avaliada no mesmo plano, não uma
aproximação.

Com um termo **não linear** em λ — um limiar, uma saturação, um produto com outro termo, ou uma
severidade que dependesse do peso — a re-aritmética seria **inválida**, e a única forma de comparar
pontos seria reavaliar cada plano sob a composição canônica. Três consequências práticas:

* `raw@0,10` é exato aqui, e **não é transferível** para um relatório futuro sem reverificar a
  linearidade da composição daquele momento;
* a renormalização não é um problema à parte: pesos de restrição são **subtraídos** e não entram na
  combinação convexa, então mexer neste λ não renormaliza nada — ao contrário de um peso de objetivo,
  em que a soma dos pesos é 1 e mexer num muda todos;
* a invariância da v1 (§3) **não** depende da linearidade: lá o termo vale `λ × 0`, que é zero por
  qualquer função que valha zero em zero.

### 2.2 Por que só a v2 foi remedida em cada ponto

O termo de preferências devolve `0,0` num plano macro por construção, então durante a busca da v1 ele
vale `λ × 0` qualquer que seja λ. O guloso não avalia fitness nenhuma. Remedir os três em cada ponto
gastaria seis vezes o tempo para reescrever as mesmas linhas.

### 2.3 A faixa admissível: nada acima de `CONSTRAINT_VIOLATION` entra em conclusão

**`CONSTRAINT_VIOLATION` vale 0,50, e λ = 0,80 está acima dele.** Ali uma *preferência* de ordem violada
custa mais que um *requisito* violado, e o vetor de pesos deixa de ser coerente com a distinção que o
próprio contrato faz entre `HARD` e `SOFT`. Um resultado medido naquele ponto descreve um sistema que
ninguém proporia.

λ = 0,80 continua medido e publicado — ele é o que mostra que **nem fora da faixa** o preço fecha a
distância até a v1 —, mas **não sustenta afirmação sobre comportamento desejável**:

| uso de λ = 0,80 | admissível? |
|---|---|
| "nem oituplicando o peso a v2 alcança a v1" (argumento *a fortiori*) | **sim** — a conclusão é "não alcança", e um ponto incoerente só a reforça |
| "a v2 zera a ordem em 160 células contra 121" (evidência de perfil) | **não** — descreve um sistema incoerente |

> **Correção (2026-10-01).** A evidência de bimodalidade de §6 era citada em λ = 0,80. Passou a ser
> citada em **λ = 0,40**, o maior ponto admissível da varredura, onde ela é **147 células zeradas contra
> 121** e **a conclusão sobrevive**. Os números de 0,80 continuam na tabela de §6 marcados como fora da
> faixa.

---

## 3. A premissa foi verificada, não suposta

O argumento de §2.2 é uma **leitura de código**, e leitura de código já errou nesta linha de trabalho —
a nota de correção em §6 da etapa 10 é sobre exatamente isso. Então a v1 foi medida nos **dois
extremos** da varredura e as colunas comparadas linha a linha:

| | resultado |
|---|---|
| Linhas da v1 em λ = 0,00 e em λ = 0,80 | 360 e 360 |
| Colunas de **resultado** que diferem | **nenhuma** — `soft_inversions`, `topics_scheduled`, `topics_unscheduled`, `scheduled_minutes`, `utilisation`, `load_ceiling_bound`, `load_excess_ratio`, `first_quarter_share`, `peak_over_mean`, `generations`, `evaluations` |
| Colunas que **diferem**, como esperado | `ga_objective_raw` e `ga_term_SoftPrerequisiteOrderConstraint_weight` — λ entra no escore relatado, não no plano |

**O plano da v1 é λ-invariante.** A premissa que economizou dois terços da varredura está medida.

---

## 4. A curva

360 células por ponto (24 instâncias × 3 proveniências × 5 sementes).

| λ | inversões | células em zero | tópicos | utilização | raw | raw@0,10 |
|--:|--:|--:|--:|--:|--:|--:|
| 0,00 | 856 | 61 / 360 | 15,27 | 0,8490 | +0,3175 | +0,2919 |
| 0,05 | 708 | 115 / 360 | 15,26 | 0,8478 | +0,3100 | **+0,3034** |
| **0,10** | **674** | 124 / 360 | 15,23 | 0,8443 | +0,3032 | +0,3032 |
| 0,20 | 624 | 142 / 360 | 15,21 | 0,8430 | +0,2934 | +0,3022 |
| 0,40 | 556 | 147 / 360 | 15,14 | 0,8387 | +0,2785 | +0,2964 |
| 0,80 | **509** | **160 / 360** | 15,02 | 0,8344 | +0,2571 | +0,2863 |
| *v1* | *420* | *121 / 360* | *13,52* | *0,7713* | — | — |

**Três leituras.**

**O mecanismo funciona.** A monotonicidade é limpa em seis pontos: o peso é de fato o botão que
controla quanto a busca se importa com ordem. λ = 0,00 dá 856 inversões, e é o que confirma que a v2
sem preço de ordem é pior que a v1 sem margem de dúvida.

**O ganho por dobra não decai — e por isso nada é extrapolado.** O primeiro passo é grande (148
inversões de 0,00 a 0,05, onde a ordem passa a ter preço pela primeira vez). Depois dele, os ganhos por
dobra **sobem e caem**:

| passo | 0,00→0,05 | 0,05→0,10 | 0,10→0,20 | 0,20→0,40 | 0,40→0,80 |
|---|--:|--:|--:|--:|--:|
| inversões a menos | 148 | **34** | **50** | **68** | **47** |

> **Correção (2026-10-01).** Uma versão anterior desta seção extrapolava desta curva que seria "preciso
> mais de uma década adicional de peso para chegar às 420 da v1". A extrapolação **foi removida**: ela
> pressupunha decaimento, e 34 · 50 · 68 · 47 não decai. A afirmação firme já bastava e não precisava
> dela: **nenhum λ admissível alcança 420** (§2.3) — em λ = 0,40, o maior ponto admissível, a v2 está em
> 556.

**O custo é baixo, e isso reabre a leitura da etapa 10.** A §5 daquele relatório descrevia a v2
trocando ordem por cobertura, medido em λ = 0,10. A varredura mostra que essa troca é **rasa**: oito
vezes o peso custa 0,25 tópico e 1,5 ponto percentual de utilização. A v2 não estava presa numa troca
dura; ela estava respondendo a um preço baixo.

---

## 5. λ = 0,10 está dentro do ótimo — no agregado, e não por célula

O objetivo canônico (`raw@0,10`, §2.1) é **achatado**: 0,2919 a 0,3034 ao longo de toda a varredura,
com máximo em 0,05 e essencialmente empatado com 0,10 e 0,20. Isso **fecha uma lacuna registrada no
próprio código**: o Javadoc de `FitnessWeights.SOFT_PREREQUISITE_ORDER` dizia que 0,10 vem de "um
argumento de ordenação, não de uma medição", e terminava dizendo que o peso é o que se deve subir "a
partir de dados". Os dados dizem para **não subir**.

**Mas o agregado esconde desacordo, e o desacordo é o achado.** Por célula, qual λ maximiza o objetivo
canônico:

| λ | células em que é o melhor | |
|--:|--:|--:|
| 0,00 | 82 | 23% |
| **0,05** | **108** | **30%** |
| 0,10 | 60 | 17% |
| 0,20 | 52 | 14% |
| 0,40 | 35 | 10% |
| 0,80 | 23 | 6% |

**Nenhum λ é o melhor para a maioria das células.** O modal é 0,05, com 30%, e **53% das células
preferem λ ≤ 0,05**. Um peso global é portanto um compromisso entre instâncias que querem coisas
diferentes — a mesma heterogeneidade que o `I²` de 58% registrava na linha de trabalho anterior, agora
sobre outro eixo. Publicar "0,10 é o ótimo" seria descrever a média como se fosse a distribuição.

**Um detalhe da tabela que muda a leitura da próxima seção.** Em **104 das 360 células há empate exato**
no objetivo canônico entre dois ou mais λ — em 30 delas, entre todos os seis —, porque o plano não muda
e o termo de ordem vale o mesmo. A tabela acima atribui cada empate ao **menor** λ empatado, que é a
convenção conservadora para a pergunta "precisa subir o peso?". Isso empurra massa para 0,00 e 0,05: só
**252** células têm ótimo único, e entre elas a distribuição é 30 · 67 · 51 · 48 · 33 · 23. O modal
continua 0,05 e a conclusão "não subir" continua, mas a convenção tem de estar dita antes de qualquer
coisa ser correlacionada com ela.

---

### 5.1 O ótimo por célula **não** acompanha nenhum fator da biblioteca (G18)

A biblioteca é um fatorial declarado — tamanho, densidade do grafo, aperto do calendário, dispersão da
disponibilidade. Se o λ ótimo por célula acompanhasse um desses fatores, seria achado publicável e G18
mudaria de natureza: a heterogeneidade de §5 teria **estrutura**, e λ poderia ser função da instância.
Se não acompanhasse, um λ global está certo e a heterogeneidade é variação entre instâncias.

**Medido: não acompanha.** O que parecia sinal é, em boa parte, artefato da convenção de empate.

| fator | ρ de Spearman, 360 células (empate → menor λ) | ρ, só as 252 de ótimo único |
|---|--:|--:|
| tamanho (tópicos) | +0,349 | **+0,080** |
| densidade do grafo | +0,082 | −0,040 |
| aperto do calendário | +0,217 | **+0,146** |
| dispersão (compacto→esparso) | −0,158 | −0,149 |

**A correlação com o tamanho desaparece quando os empates saem**, e a razão é mecânica: a taxa de
empate é **55% com 10 tópicos contra 5% com 25**. Instâncias pequenas empatam muito, o empate vai para
λ = 0,00 por convenção, e isso fabrica uma correlação "menor instância prefere menor λ" que é a
convenção se medindo a si mesma. Nas 252 células de ótimo único os dois tamanhos pedem praticamente o
mesmo λ: **0,187 contra 0,202**.

| λ médio por nível, só ótimos únicos | | |
|---|---|---|
| tópicos | 10: **0,187** | 25: **0,202** |
| densidade | 0,4: 0,231 | 1,2: 0,171 |
| aperto | 0,7: 0,152 · 1,0: 0,180 · 1,5: **0,245** | |
| dispersão | compacto: 0,231 | esparso: 0,161 |

**Sobra um gradiente fraco e interpretável no aperto**, e só ele: calendário folgado prefere λ um pouco
maior (0,152 → 0,180 → 0,245), o que faz sentido — com folga, ordenar custa menos cobertura. A
magnitude não sustenta política: ρ = +0,15.

**E o ótimo por célula é em sua maior parte *ruído*, não propriedade da instância.** Decompondo a
variação do λ ótimo entre as 22 instâncias com ao menos duas células de ótimo único:

| | |
|---|--:|
| fração da variação **entre** instâncias | **20,3%** |
| ICC(1) | **+0,135** |
| F = MSB/MSW | 2,79 |

**80% da variação está *dentro* da instância** — entre proveniências e sementes da mesma instância. Uma
quantidade que varia mais entre sementes do que entre instâncias não é propriedade da instância, e não
pode ser prevista a partir dos fatores dela por construção.

**Quanto custaria ignorar isso: quase nada.** Escolhendo por instância o λ que maximiza o objetivo
médio daquela instância — um **oráculo**, ajustado e avaliado nos mesmos dados, portanto um limite
superior otimista:

| | |
|---|--:|
| ganho médio no objetivo canônico | **+0,0027** |
| inversões totais, λ global 0,10 | 674 |
| inversões totais, λ por instância (oráculo) | **647** |
| *v1, para referência* | *420* |
| mudança em tópicos agendados | +0,06 |

**Conclusão: um λ global está certo, e G18 não muda de natureza.** A heterogeneidade de §5 é real e
continua registrada, mas é variação entre células — em boa parte entre sementes — e não estrutura que
os fatores da biblioteca expliquem. Nada aqui transforma λ em função da instância, e nada aqui foi
implementado: **esta subseção mediu e só**.

### 5.2 Isto é uma análise de sensibilidade, e o resultado é **a favor**

O registro de constantes não calibradas descrevia `SOFT_PREREQUISITE_ORDER = 0,10` como vinda de "um
argumento de ordenação, não de uma medição". Depois desta varredura a nota correta é outra, e é melhor:

> **varrido de 0,00 a 0,80; desfechos insensíveis dentro da faixa admissível; não calibrado contra dado
> de aprendizagem.**

**A diferença entre as duas notas é a diferença entre uma constante arbitrária e uma constante com
análise de sensibilidade feita** — e vale dizer explicitamente qual é o resultado dela, porque omitir
soaria como ressalva quando é o contrário:

**as conclusões dominantes deste relatório não dependem daquela constante.** Em toda a faixa admissível
— e mesmo fora dela — a v2 não alcança a v1 em inversões, mantém a vantagem de cobertura de ~1,5 tópico
e permanece com cauda mais longa. O objetivo canônico varia 0,2919 a 0,3034 em oito vezes o valor do
peso: 3,9% de amplitude. Nenhuma das três conclusões vira ao mexer λ.

O que **continua** não calibrado é o que nenhuma varredura resolve: **0,10 não foi ajustado contra dado
de aprendizagem de aluno nenhum**. Varrer o peso mostra o que o *critério* prefere; mostrar o que o
*aluno* prefere exige medir aprendizagem, que este projeto não faz. A nota diz as duas coisas porque as
duas são verdade.

---

## 6. A v2 falha diferente da v1, e os totais escondem isso

**Em λ = 0,40**, o maior ponto admissível da varredura (§2.3), a distribuição de inversões por célula:

| inversões na célula | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| **v2** (λ = **0,40**) | **147** | 75 | 48 | 44 | 11 | 13 | 14 | 5 | 2 | 1 |
| **v1** | 121 | 138 | 60 | 26 | 1 | 9 | — | 5 | — | — |
| *v2 (λ = 0,80 — fora da faixa)* | *160* | *75* | *52* | *31* | *9* | *15* | *6* | *7* | *4* | *1* |

**A v2 resolve a ordem por completo mais vezes que a v1** — 147 células em zero contra 121 — e ainda
assim tem total maior (556 contra 420). As duas coisas são compatíveis porque a cauda dela é mais longa:
pior célula 9 contra 7, e **46 células com 4 ou mais inversões contra 15 da v1**.

Pareado, em λ = 0,40: a v2 chega a zero em **42 células onde a v1 não chega**; a v1 em **16 onde a v2
não chega**.

| λ | v2 melhor | empate | v2 pior | v2 = 0 e v1 > 0 | v1 = 0 e v2 > 0 |
|--:|--:|--:|--:|--:|--:|
| 0,00 | 24 | 87 | 249 | 16 | 76 |
| 0,05 | 34 | 165 | 161 | 24 | 30 |
| 0,10 | 33 | 171 | 156 | 24 | 21 |
| 0,20 | 50 | 173 | 137 | 40 | 19 |
| **0,40** | **64** | 170 | 126 | **42** | 16 |
| *0,80 (fora da faixa)* | *74* | *185* | *101* | *54* | *15* |

> **Correção (2026-10-01).** Esta seção citava λ = 0,80 — 160 células zeradas contra 121, e 54 a 15 no
> pareado. Aqueles números vêm de uma configuração **incoerente** (λ acima de `CONSTRAINT_VIOLATION`,
> §2.3) e não devem sustentar afirmação sobre perfil. Em λ = 0,40 a evidência é mais fraca e **a
> conclusão sobrevive**: 147 contra 121 zeradas, 42 contra 16 no pareado, cauda ainda mais longa. As
> linhas de 0,80 ficam nas tabelas, marcadas, porque a monotonicidade é parte do resultado.

**A caracterização correta não é "a v2 é pior".** É que **a v1 é uniformemente mediana e a v2 é
bimodal**: ou resolve, ou erra feio. Qual das duas se prefere não é pergunta técnica — um plano com
zero inversões em 41% dos casos e nove inversões em alguns é um produto diferente de um plano com uma
ou duas inversões quase sempre.

O déficit que sobra continua concentrado onde a etapa 10 já o localizava: 25 tópicos (−0,57), grafo
denso (−0,52), calendário apertado (−0,60). Com 10 tópicos, grafo esparso ou calendário folgado, a v2
empata ou passa à frente.

---

## 7. A variância entre sementes vem do termo de ordem

| λ | totais por semente | amplitude |
|--:|---|--:|
| 0,00 | 173 · 171 · 171 · 173 · 168 | 5 (**2,9%**) |
| 0,10 | 125 · 135 · 140 · 138 · 136 | 15 (**11,1%**) |
| **0,40** | 111 · 106 · 118 · 109 · 112 | 12 (**10,8%**) |
| *0,80 (fora da faixa)* | *101 · 105 · 105 · 94 · 104* | *11 (10,8%)* |

Sem preço de ordem a v2 é quase determinística entre sementes — a busca converge para planos
parecidos, guiados por cobertura e retenção. **O termo de ordem é o que introduz a variância**, e ela
aparece já em 0,05 e não cresce muito depois.

Isso tem consequência para qualquer limiar de CI sobre a v2: a variância a calibrar depende do λ
configurado, e os 11% da etapa 10 valem para λ = 0,10 e não em geral.

---

## 8. Limitações

1. **Seis pontos, dobrando.** Suficiente para a ordem de grandeza e para a monotonicidade; não para
   localizar um ótimo fino entre 0,05 e 0,20, onde o agregado é achatado.
2. **λ só foi varrido para a v2.** A v1 é λ-invariante (§3) e o guloso não lê fitness, mas um motor
   futuro que avaliasse ordem na busca precisaria da varredura própria.
3. **Os outros pesos ficaram fixos.** `CONSTRAINT_VIOLATION` (0,50) e os três objetivos não se moveram.
   A varredura mede o preço da ordem *contra esta* estrutura, e um ótimo conjunto poderia estar em
   outro lugar do espaço de pesos — que é um estudo maior e outra decisão.
4. **5 sementes por ponto**, como nas etapas 09 e 10.
5. **Instâncias sintéticas.** A forma dos resultados é robusta; os valores não transferem para um
   currículo real.

---

## 9. Pendências

| # | Gap | Status | Onde |
|---|---|---|---|
| **G16** | **A ponderação decidia o resultado da comparação v1 × v2 e ninguém a medira.** | ✅ **RESOLVIDO** — o peso guia a v2 monotonicamente (856 → 509) e **não fecha a distância até a v1** (420): 556 em λ = 0,40, o maior ponto admissível, e 509 nem em 0,80, fora da faixa. λ = 0,10 está dentro do ótimo achatado do objetivo canônico, então a recomendação é **não mexer**. A diferença entre reparo lexicográfico e preço ponderado não se fecha por preço — e o 2×2 de [`12`](./12-desconfundindo-precedencia.md) mediu o tamanho de cada efeito | §1, §2.3, §4, §5 |
| **G17** | Custo da v2 | 🟡 **PARCIAL** — 1,37× mais rápido; o resto é estrutural | [`10`](./10-medicao-v2-linha-do-tempo.md) §6 |
| **G18** | **Nenhum λ global serve à maioria das instâncias**: 53% das células preferem λ ≤ 0,05 e o modal (0,05) leva só 30% | 🟡 **FECHADO COMO VARIAÇÃO, NÃO COMO ESTRUTURA** (§5.1) — o ótimo por célula **não acompanha** tamanho, densidade, aperto nem dispersão; a correlação aparente com o tamanho é artefato da convenção de empate, e **80% da variação está dentro da instância**. Um λ por instância, escolhido por oráculo, levaria 674 → 647 inversões (v1: 420). **Um λ global está certo**; a heterogeneidade fica registrada | §5, §5.1 |
| **G19** | **A v1 é uniformemente mediana e a v2 é bimodal** — em λ = **0,40** a v2 zera a ordem em **147** células contra 121, e tem 46 células com ≥4 inversões contra 15. Qual perfil o produto prefere **não é pergunta técnica** | ⬜ **ABERTO — decisão de produto**, e reaberta em outros termos por [`12`](./12-desconfundindo-precedencia.md) §7: com reparo lexicográfico a v2′ zera 169 e tem pior célula 6 | §6 |

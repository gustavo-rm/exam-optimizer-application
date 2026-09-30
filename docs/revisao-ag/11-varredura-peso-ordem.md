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
| Subir o peso fecha a distância até a v1? | **Não.** 509 contra 420 em λ = 0,80, já além do limite que o argumento de ordenação estabelece |
| Quanto custa subir o peso? | **Muito pouco.** Tópicos agendados 15,27 → 15,02; utilização 0,8490 → 0,8344. A troca é bem mais suave do que o retrato em λ = 0,10 sugeria |
| A v2 mantém a vantagem de cobertura? | **Em toda a varredura.** Em λ = 0,80 ela ainda agenda 15,02 tópicos contra 13,52 da v1, e usa 0,834 do calendário contra 0,771 |
| λ = 0,10 é um bom valor? | **No agregado, sim** — o objetivo canônico é plano entre 0,00 e 0,20 e tem máximo em 0,05–0,10. **Por célula, não há maioria**: 53% das células preferem λ ≤ 0,05 (§5) |
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

### 2.2 Por que só a v2 foi remedida em cada ponto

O termo de preferências devolve `0,0` num plano macro por construção, então durante a busca da v1 ele
vale `λ × 0` qualquer que seja λ. O guloso não avalia fitness nenhuma. Remedir os três em cada ponto
gastaria seis vezes o tempo para reescrever as mesmas linhas.

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

**O ganho por dobra decai.** De 0,00 a 0,05 caem 148 inversões; de 0,40 a 0,80, 47. Extrapolando, seria
preciso mais de uma década adicional de peso para chegar às 420 da v1 — e a essa altura o termo estaria
custando várias vezes o que um requisito custa, o que deixa de ser um ajuste de peso e passa a ser uma
mudança do que o sistema promete.

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

---

## 6. A v2 falha diferente da v1, e os totais escondem isso

Em λ = 0,80, a distribuição de inversões por célula:

| inversões na célula | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| **v2** (λ = 0,80) | **160** | 75 | 52 | 31 | 9 | 15 | 6 | 7 | 4 | 1 |
| **v1** | 121 | 138 | 60 | 26 | 1 | 9 | — | 5 | — | — |

**A v2 resolve a ordem por completo mais vezes que a v1** — 160 células em zero contra 121 — e ainda
assim tem total maior. As duas coisas são compatíveis porque a cauda dela é mais longa: pior célula 9
contra 7, e **42 células com 4 ou mais inversões contra 15 da v1**.

Pareado, em λ = 0,80: a v2 chega a zero em **54 células onde a v1 não chega**; a v1 em **15 onde a v2
não chega**.

| λ | v2 melhor | empate | v2 pior | v2 = 0 e v1 > 0 | v1 = 0 e v2 > 0 |
|--:|--:|--:|--:|--:|--:|
| 0,00 | 24 | 87 | 249 | 16 | 76 |
| 0,10 | 33 | 171 | 156 | 24 | 21 |
| 0,80 | **74** | 185 | 101 | **54** | 15 |

**A caracterização correta não é "a v2 é pior".** É que **a v1 é uniformemente mediana e a v2 é
bimodal**: ou resolve, ou erra feio. Qual das duas se prefere não é pergunta técnica — um plano com
zero inversões em 44% dos casos e nove inversões em alguns é um produto diferente de um plano com uma
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
| 0,80 | 101 · 105 · 105 · 94 · 104 | 11 (**10,8%**) |

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
| **G16** | **A ponderação decidia o resultado da comparação v1 × v2 e ninguém a medira.** | ✅ **RESOLVIDO** — o peso guia a v2 monotonicamente (856 → 509) e **não fecha a distância até a v1** (420) nem em λ = 0,80, acima do limite do argumento de ordenação. λ = 0,10 está dentro do ótimo achatado do objetivo canônico, então a recomendação é **não mexer**. A diferença entre reparo lexicográfico e preço ponderado não se fecha por preço | §1, §4, §5 |
| **G17** | Custo da v2 | 🟡 **PARCIAL** — 1,37× mais rápido; o resto é estrutural | [`10`](./10-medicao-v2-linha-do-tempo.md) §6 |
| **G18** | **Nenhum λ global serve à maioria das instâncias**: 53% das células preferem λ ≤ 0,05 e o modal (0,05) leva só 30%. Um peso único é um compromisso entre instâncias que querem coisas diferentes — a mesma heterogeneidade que o `I²` de 58% registrava noutro eixo | ⬜ **ABERTO** — um peso por instância seria um parâmetro derivado de dado que a plataforma não envia, e escolhê-lo por busca faria a fitness ajustar a si mesma. Fica registrado, não corrigido | §5 |
| **G19** | **A v1 é uniformemente mediana e a v2 é bimodal** — em λ = 0,80 a v2 zera a ordem em 160 células contra 121, e tem 42 células com ≥4 inversões contra 15. Qual perfil o produto prefere **não é pergunta técnica** | ⬜ **ABERTO — decisão de produto** | §6 |

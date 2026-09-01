# Saturação, Rastreamento e a Decisão de Não Expandir a Amostra

**Data:** 2026-08-31 · **Etapa:** 08 · **Índice e status:** [`README.md`](./README.md)
**Objeto:** fechar duas lacunas de rastreamento, explicar por que metade das instâncias não produz
correlação, e decidir se o conjunto de instâncias deve crescer.
**Código novo:** `benchmarks/…/robustness/SaturationDiagnosticMain` ·
`benchmarks/…/metric/SpearmanTest` · heterogeneidade em `CorrelationAggregate`
**Nada da fitness ou do termo de retenção foi alterado.**

---

## 0. Resumo

| Item | Resultado |
|---|---|
| L4 estava rastreado? | **Não.** Não aparecia no `README` nem na lista G1–G11. Ficou órfão; agora é o **G12** |
| Troca I3/I4 estava rastreada? | **Parcialmente** — existia como pendência de negócio, sem os números e fora de G1–G11 → agora **G13** |
| Saturação e L4 são o mesmo problema? | **Não. São opostos.** As 4 instâncias indefinidas saturam no **teto** (100% para todo planejador); L4 é sobre regime disperso |
| Expandir a amostra? | **Não agora** — o eixo proposto já foi varrido e é nulo, e o gargalo não é `n`, é heterogeneidade (I² = 58%) |
| Achado adicional | **Defeito na detecção de empates do `Spearman`** trocava `I8` de −0,880 para −0,928 conforme o caminho de código. Corrigido |

---

## 1. Auditoria de rastreamento

### 1.1 (a) L4 — **não estava rastreado**

Busca por `L4`, `I8`, `I3`, `I4` em [`README.md`](./README.md): **zero ocorrências.** A limitação
existia apenas dentro de [`05`](./05-fitness-function.md) §8 (tabela de limitações) e
[`06b`](./06-regime-alta-carga.md) §10. Quem entrasse pelo índice — que a etapa 07 declarou "fonte
única de status" — não a encontraria. Registrada agora como **G12**, status pendente.

### 1.2 Correção necessária ao enunciado de L4

O enunciado desta etapa descreve L4 como *"o termo de retenção ficar em regime linear sem força de
espalhamento sob orçamento muito apertado"*. **Essa explicação foi testada na etapa 06 e não se
confirmou**, portanto não deve ser propagada.

[`06b`](./06-regime-alta-carga.md) §3 varreu a razão demanda/orçamento de 0,50 a 5,00 mantendo todo
o resto fixo: **as oito instâncias saturam em 100% para todo planejador**, amplitude 0,0 pp. O
orçamento apertado não reproduz o fenômeno. O driver medido é a **dispersão efetiva da importância**,
e o mecanismo é que O₁ e O₃ dividiam o mesmo vetor de pesos e concordavam em abandonar as mesmas
disciplinas — corrigido pela temperagem, que moveu a fronteira de ~30:1 para ~200:1.

O resíduo de `I8` (ρ = −0,880, inalterado pela temperagem, com dispersão de apenas 32:1) é uma
**anomalia não explicada**, com uma hipótese não medida: `InterleavedCriticalStrategy` estuda só as
três disciplinas mais críticas por dia, e `I8` tem 40 disciplinas. É isso que G12 rastreia.

### 1.3 (b) Troca I3/I4 — **rastreada, mas fraca**

Existia em `README` → "Pendências de decisão humana" → *Troca de pesos*, apontando para
[`06c`](./06-limite-troca-pesos.md) §6. Estava correto como status, mas **sem os identificadores nem
os números**, então não era localizável por busca e não aparecia em G1–G11. Registrada agora como
**G13** e a linha da pendência foi enriquecida com os valores.

Números confirmados nesta etapa: `I3` 96,0% (uniforme) × 76,6% (AG); `I4` 100,0% × 81,4%.

---

## 2. Achado novo: empates do Spearman dependiam do caminho de código

Encontrado ao checar a consistência do diagnóstico da §3 contra os números canônicos.

`Spearman.ranks` detectava empates com `Double.equals` **exato**. As séries correlacionadas são
médias sobre repetições: somar `0,975` sete vezes num laço e dividir por 7 dá
`0,9749999999999999` — um ulp abaixo do `0,975` exato de um planejador determinístico. O empate
passava despercebido, três planejadores empatados recebiam postos distintos, e `I8` lia **−0,928**
em vez de **−0,880**.

O que torna isso grave é a **dependência de caminho**: relatórios que passavam pelo CSV liam
`"0.9750"` de volta como `0,975` exato e viam o empate; relatórios que calculavam em memória, não.
Dois caminhos, mesmos dados, coeficiente publicado diferente. O agregado canônico ia de **−0,106**
para **−0,171**.

**Correção:** tolerância de `1e-9` na detecção de empate. A métrica de negócio é uma contagem sobre
um total — a menor diferença real em `I8` é `1/40 = 0,025`, sete ordens de grandeza acima da
tolerância. Cinco testes em `SpearmanTest` travam o comportamento, incluindo a comparação explícita
entre o caminho em memória e o caminho com round-trip textual.

**O valor publicado (−0,880) era o correto**; a correção o valida em vez de mudá-lo. Todos os
números canônicos da etapa 07 permanecem: −0,909 → −0,460 → **−0,106**.

---

## 3. Causa raiz da saturação

`SaturationDiagnosticMain`, 7 repetições, mesmas séries do harness canônico.

| Instância | Disc. | Orçam. | Horiz. | Razão | Dispersão | Dias mín | Cobertura mín | Janela mín..máx | ρ | Diagnóstico |
|---|---|---|---|---|---|---|---|---|---|---|
| `I1-pequeno-folgado` | 6 | 80 | 120 | 1,48 | 3:1 | 8 | 0,22 | 100,0% .. 100,0% | n/d | **SATURADA NO TETO** |
| `I2-medio-tipico` | 12 | 140 | 180 | 1,56 | 7:1 | 3 | 0,13 | 100,0% .. 100,0% | n/d | **SATURADA NO TETO** |
| `I3-grande-apertado` | 25 | 200 | 150 | 2,66 | 42:1 | 1 | 0,04 | 72,0% .. 96,0% | +0,257 | DISCRIMINA |
| `I4-pesos-extremos` | 10 | 160 | 200 | 1,81 | 1636:1 | 1 | 0,02 | 70,0% .. 100,0% | −0,093 | DISCRIMINA |
| `I5-pesos-balanceados` | 10 | 160 | 200 | 2,03 | 21:1 | 2 | 0,07 | 100,0% .. 100,0% | n/d | **SATURADA NO TETO** |
| `I6-horizonte-curto` | 8 | 105 | 40 | 0,46 | 3:1 | 7 | 0,75 | 100,0% .. 100,0% | n/d | **SATURADA NO TETO** |
| `I7-horizonte-longo` | 15 | 260 | 400 | 4,13 | 17:1 | 3 | 0,05 | 93,3% .. 100,0% | +0,655 | DISCRIMINA |
| `I8-escala` | 40 | 280 | 300 | 6,02 | 32:1 | 1 | 0,02 | 97,5% .. 100,0% | −0,880 | DISCRIMINA |

### 3.1 As quatro são o mesmo caso, e é o caso benigno

**Todas as quatro saturam no TETO** — 100% para todo planejador, amplitude 0,0 pp. Nenhuma satura no
piso. A distinção importa: saturar no teto significa que **nem o pior planejador perde disciplina
alguma**, portanto a fitness *não tem como causar dano* ali. Excluí-las do agregado não esconde um
regime quebrado; exclui um regime em que a pergunta não se aplica.

Se alguma saturasse no piso (0% para todos), a exclusão estaria escondendo exatamente o cenário em
que o produto falha. O diagnóstico classifica os dois casos separadamente para que essa diferença
nunca mais fique implícita numa contagem de "excluídas: indefinida".

### 3.2 Saturação e L4 são fenômenos **opostos**

| | Saturação (4 instâncias) | L4 / `I8` |
|---|---|---|
| Métrica de negócio | Constante em **100%** | Varia (97,5% – 100%) |
| O que a fitness pode fazer | **Nada** — não há o que piorar | Ordena ao contrário |
| Regime | Folgado o bastante para todos | Disperso o bastante para separar |
| Efeito no agregado | Excluída | Domina, é a mais negativa |

Não são o mesmo problema disfarçado de um só. São as duas pontas do mesmo eixo: abaixo do limiar de
discriminação nada aparece; acima dele, aparece o desalinhamento.

### 3.3 O eixo que separa não é o orçamento

A varredura R isola a razão demanda/orçamento com tudo o mais fixo:

| Instância | Razão | Dias mín | Cobertura mín | Janela | ρ |
|---|---|---|---|---|---|
| `R0_50` | 0,50 | 24 | 0,27 | 100,0% .. 100,0% | n/d |
| `R2_00` | 2,00 | 4 | 0,09 | 100,0% .. 100,0% | n/d |
| `R5_00` | 5,00 | 3 | **0,03** | 100,0% .. 100,0% | n/d |

Com cobertura mínima de **0,03** — 3% das sessões que O₃ pediria — todo planejador ainda mantém
todas as 20 disciplinas dentro da janela. **A métrica de janela de retenção é essencialmente
insensível ao aperto do orçamento** nesta faixa.

O que separa é a **dispersão da importância**: `I5` (21:1) satura, `I8` (32:1), `I3` (42:1) e `I4`
(1636:1) discriminam. O limiar de discriminação fica entre **21:1 e 32:1**, coerente com a fronteira
de 22:1–32:1 medida em [`06b`](./06-regime-alta-carga.md) §6 por outro caminho.

`I7` é a exceção parcial: discrimina com dispersão de apenas 17:1, mas tem horizonte de **400 dias**
— o dobro de qualquer outra. Horizonte longo eleva `H/τ`, o que empurra a cauda para baixo pelo mesmo
mecanismo. Então há **dois** caminhos para discriminar (dispersão alta, ou horizonte longo), e
nenhum deles é o orçamento.

---

## 4. Heterogeneidade: o gargalo não é `n`

`CorrelationAggregate` passa a reportar Q de Cochran, I² e um intervalo de efeitos aleatórios.

| Estado | Agregado | IC fixo | Q (gl = 3) | I² | IC de efeitos aleatórios |
|---|---|---|---|---|---|
| Etapa 03 | −0,909 | −0,971 a −0,734 | 0,52 | **0%** | −0,971 a −0,734 |
| Etapa 05 | −0,460 | −0,793 a +0,085 | 3,53 | **15%** | −0,811 a +0,134 |
| **Etapa 06 / hoje** | **−0,106** | −0,597 a +0,443 | **7,20** | **58%** | **−0,765 a +0,662** |

**A heterogeneidade cresceu ao longo da revisão, e isso é consequência direta do que foi corrigido.**
Na etapa 03 as quatro instâncias concordavam — todas fortemente negativas, I² = 0%. A temperagem da
etapa 06 moveu `I3` (−0,029 → +0,257) e `I7` (0,000 → +0,655) bastante, e `I8` nada. Hoje `I7` está
em +0,655 e `I8` em −0,880: **elas não estimam a mesma quantidade.**

Consequência para a leitura: com I² = 58%, a média é uma descrição fraca de qualquer instância
individual, e o intervalo honesto é o de efeitos aleatórios — **−0,765 a +0,662**, mais largo ainda
que o de efeito fixo. O `format()` do agregado agora carrega esses números sempre, para que o
coeficiente não circule sozinho.

---

## 5. Decisão sobre expandir o conjunto: **(ii) não expandir agora**

### 5.1 Por que não

**1. O eixo proposto já foi varrido, e é nulo.** O enunciado sugere gerar 6–8 instâncias variando
sistematicamente sessões-necessárias/sessões-disponíveis, do folgado ao apertado. **Essa varredura
existe desde a etapa 06** (`HighLoadInstanceLibrary.all()`, 8 instâncias, razão 0,50 a 5,00, tudo o
mais fixo) e todas as oito são indefinidas (§3.3). Repeti-la produziria 6–8 novas instâncias
saturadas e deixaria `n` exatamente onde está. Isso não é conjectura: está medido, com cobertura
mínima descendo a 0,03 sem tirar ninguém da janela.

**2. Os eixos que funcionam introduzem viés de seleção.** O que produz discriminação é dispersão
alta ou horizonte longo. Gerar instâncias por esses critérios é escolher instâncias **por um preditor
conhecido do desfecho** — e como são exatamente as instâncias em que a fitness falha, o agregado
ficaria sistematicamente mais negativo. Trocaríamos um intervalo largo por uma estimativa enviesada,
que é pior: um número estreito e errado é mais perigoso que um número largo e honesto. É o risco que
o próprio enunciado levanta ("desenhar instâncias novas tem risco de viés se forem construídas para
'dar certo'") — vale igualmente para construí-las de modo a "dar errado".

**3. O gargalo não é `n`, é I².** Expandir estreita o intervalo *da média*. Mas com I² = 58% a média
não é a quantidade sob questão: as instâncias discordam de verdade. Medir com mais precisão uma média
de coisas que não são a mesma coisa não responde melhor a pergunta "maximizar a fitness prejudica a
retenção?" — a resposta honesta hoje é *"depende do edital, e sabemos de qual propriedade depende"*,
que é o que a tabela por instância e a fronteira de dispersão já dizem.

**4. As instâncias são sintéticas.** Todas as oito, mais as 22 das varreduras, foram desenhadas por
nós. Aumentar a amostra sintética aumenta a precisão sobre uma população que nós inventamos. O ganho
de validade viria de **um edital real**, não de mais instâncias do mesmo gerador.

### 5.2 O que se perde ao não expandir

Registrado para não parecer que a decisão é gratuita:

- O IC de efeitos aleatórios continua cruzando o zero (−0,765 a +0,662). **Não é possível afirmar
  que a etapa 06 neutralizou a anti-correlação** — só que a reduziu, com direção consistente em três
  estados medidos.
- Um avaliador externo que pedir "qual a correlação do seu sistema" recebe um número com quatro
  instâncias por trás. É pouco, e o documento diz que é pouco.
- A anomalia de `I8` continua sem explicação, e ela é a instância que mais puxa o agregado.

### 5.3 O que reabre a decisão

Gatilhos objetivos, não intuição:

1. **Uso externo do número.** Se o agregado for para o pitch do Centelha, para o Colégio Donaduzzi ou
   para uma auditoria como evidência quantitativa, a amostra precisa crescer **antes** — e a
   expansão deve então ser pré-registrada: eixos e faixas definidos por escrito antes de medir, e
   **todas** as instâncias geradas entram no agregado, inclusive as que saturarem.
2. **Disponibilidade de edital real.** Um único edital real com pesos e prazos verdadeiros vale mais
   que dez sintéticos, porque responde à pergunta que os sintéticos não respondem: qual dispersão os
   editais brasileiros de fato apresentam. Se ficarem consistentemente abaixo de 21:1, L4 é teórica.
3. **Mudança na fitness ou no termo de retenção.** Qualquer alteração exige remedir os três estados,
   e é o momento natural para reconsiderar a amostra.
4. **Redução da heterogeneidade.** Se uma correção futura fizer `I8` convergir com as demais e I²
   cair abaixo de 50%, a média volta a ser uma descrição razoável e expandir passa a valer a pena.

### 5.4 Em vez de expandir, o que foi feito

- A limitação de `n = 4` deixou de ser implícita: o `format()` do agregado emite `n`, quantas foram
  excluídas, Q, I² e o intervalo de efeitos aleatórios. Não é possível citar o coeficiente sem eles.
- A causa da exclusão está medida e classificada (§3), não presumida.
- `SaturationDiagnosticMain` deixa a verificação repetível para qualquer instância futura.

---

## 6. Ameaças à validade

1. **Oito instâncias sintéticas**, mais 22 de varredura, todas do mesmo gerador. Nenhum edital real.
2. **A métrica de retenção é uma simulação** com grade 4 fixa, herdada de [`03`](./03-validacao.md)
   §9. Não foi validada contra retenção real.
3. **O limiar de discriminação (21:1–32:1) vem de 8 pontos**, dos quais `I7` é exceção parcial. É uma
   faixa, não uma constante.
4. **A tolerância de empate de `1e-9` é apropriada a esta métrica** (contagem sobre total). Uma
   métrica de negócio futura com diferenças genuínas menores que isso exigiria revisá-la.
5. **I² com 4 instâncias é impreciso.** Q = 7,20 com 3 graus de liberdade dá p ≈ 0,07 — sugestivo,
   não conclusivo. O argumento da §5.1(3) seria mais forte com mais instâncias, o que é uma tensão
   honesta com a decisão de não expandir: usamos uma estatística imprecisa para justificar não
   melhorar sua precisão. O que sustenta a decisão são os motivos 1, 2 e 4, que não dependem de I².

---

## 7. Como reproduzir

```bash
./mvnw -o clean test          # 73 testes
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:target/test-classes:$(cat cp.txt)"

java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.SaturationDiagnosticMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.RegimeCorrelationMain
```

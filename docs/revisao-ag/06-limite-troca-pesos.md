# Limite da Troca de Pesos: Até Onde a Vitória da Divisão Uniforme é Aceitável

> **Status: PENDÊNCIA ABERTA — decisão de negócio, aguardando confirmação humana.**
> Este documento mede a troca e a apresenta. **Não escolhe um lado.** Nenhum peso da fitness foi
> alterado nesta etapa, e nenhum ajuste motivado por este documento deve ser feito antes de uma
> decisão explícita registrada aqui.

**Código:** `benchmarks/…/robustness/WeightTradeoffMain`
**Dados:** `benchmarks/results/limite-troca-pesos.csv`
**Método:** média de 7 sementes por ponto medido, com as mesmas instâncias e o mesmo harness da
etapa 03. Todas as grandezas são avaliadas com os objetivos **de produção**, mesmo quando o plano foi
gerado por um pipeline com pesos diferentes — caso contrário cada linha seria medida com a própria
régua e as colunas não se comparariam.

---

## 1. O fato

Em duas das oito instâncias, o baseline mais ingênuo do harness — dividir os dias igualmente entre as
disciplinas — entrega **mais retenção** que o AG, ainda que perca na fitness:

| Instância | % de disciplinas na janela de retenção — AG | — divisão uniforme | Diferença |
|---|---|---|---|
| `I3-grande-apertado` (25 disciplinas, 200 dias) | 76,6% | **96,0%** | **19,4 pp** |
| `I4-pesos-extremos` (10 disciplinas, 160 dias) | 81,4% | **100,0%** | **18,6 pp** |

Em números absolutos: em `I3` o plano do AG deixa **5 de 25 disciplinas** fora da janela de retenção
no dia da prova, contra **1 de 25** da divisão uniforme. Em `I4`, **2 de 10** contra **0 de 10**.

Nas outras seis instâncias não há troca a decidir: ambos saturam em 100% (`I1`, `I2`, `I5`, `I6`,
`I7`) ou empatam (`I8`, 97,5% para ambos).

---

## 2. Por que a divisão uniforme ganha

A fitness é `0,50·O₁ + 0,30·O₃ + 0,20·O₄`. Decompondo os dois planos termo a termo:

**`I3-grande-apertado`**

| Planejador | O₁ edital | O₃ retenção | O₄ carga | Fitness | % janela | Dias de sobrecarga |
|---|---|---|---|---|---|---|
| AG de produção | 0,6524 | 0,5112 | **0,9231** | **0,6642** | 76,6% | **73** |
| Divisão uniforme | **0,6628** | **0,5249** | 0,8000 | 0,6489 | **96,0%** | 91 |

**`I4-pesos-extremos`**

| Planejador | O₁ edital | O₃ retenção | O₄ carga | Fitness | % janela | Dias de sobrecarga |
|---|---|---|---|---|---|---|
| AG de produção | **0,9491** | 0,7642 | **1,0000** | **0,9038** | 81,4% | **23** |
| Divisão uniforme | 0,9427 | **0,8223** | 0,6156 | 0,8412 | **100,0%** | 124 |

O resultado é contraintuitivo e vale enunciá-lo com clareza:

> **O AG não vence por cobrir melhor o edital. Ele vence pelo termo de carga cognitiva.**

Abrindo a diferença de fitness em `I3` (+0,0153 a favor do AG):

| Termo | Contribuição para a vantagem do AG |
|---|---|
| O₁ edital | **−0,0052** (o AG *perde*) |
| O₃ retenção | **−0,0041** (o AG *perde*) |
| O₄ carga cognitiva | **+0,0246** |

Em `I4` (+0,0627 a favor do AG), o mesmo padrão: O₁ +0,0032, O₃ **−0,0174**, O₄ **+0,0769**.

Nas duas instâncias o AG **perde no próprio termo de retenção** e compensa com folga na carga
cognitiva. Ou seja, não se trata de "o AG prioriza pontuação e sacrifica memória" — ele prioriza
**sustentabilidade da agenda** e sacrifica memória.

## 3. O que cada lado ganha e o que perde

Essa leitura muda o que está sendo trocado. A divisão uniforme **não é de graça**:

| | AG de produção | Divisão uniforme |
|---|---|---|
| Disciplinas retidas até a prova (`I4`) | 8 de 10 | **10 de 10** |
| Dias com carga acima do orçamento cognitivo (`I4`) | **23** de 160 | **124** de 160 |
| Disciplinas retidas até a prova (`I3`) | 20 de 25 | **24 de 25** |
| Dias com carga acima do orçamento cognitivo (`I3`) | **73** de 200 | 91 de 200 |
| Concentração na maior disciplina (`I4`) | 25,2% | **17,5%** |

Em `I4` o preço da retenção uniforme é um cronograma que estoura o orçamento cognitivo do aluno em
**124 dos 160 dias** — contra 23 do AG. É o tipo de plano que o aluno abandona na terceira semana.
A métrica de janela de retenção não enxerga isso, porque mede a memória de um plano assumindo que
ele foi cumprido.

**Portanto a troca real não é "pontuação × memória". É "memória × sustentabilidade da agenda".**

---

## 4. A alavanca de pesos: preço medido

A hipótese natural — deslocar peso de O₁ para O₃ — foi medida ponto a ponto, com `w₄` fixo em 0,20
para que apenas um grau de liberdade se movesse.

**`I3-grande-apertado`**

| w₁ edital | w₃ retenção | O₁ obtido | Δ O₁ | % janela | Δ janela | O₃ obtido |
|---|---|---|---|---|---|---|
| **0,50** | **0,30** | 0,6524 | — | 76,6% | — | 0,5112 |
| 0,45 | 0,35 | 0,6510 | −0,21% | 77,1% | +0,6 pp | 0,5098 |
| 0,40 | 0,40 | 0,6463 | −0,92% | 77,7% | **+1,1 pp** | 0,5080 |
| 0,35 | 0,45 | 0,6411 | −1,73% | 76,6% | +0,0 pp | 0,5015 |
| 0,30 | 0,50 | 0,6407 | −1,78% | 76,6% | +0,0 pp | 0,5019 |
| 0,20 | 0,60 | 0,6326 | −3,03% | 76,6% | +0,0 pp | 0,4962 |
| 0,10 | 0,70 | 0,6286 | −3,64% | 77,1% | +0,6 pp | 0,4928 |

**`I4-pesos-extremos`**

| w₁ edital | w₃ retenção | O₁ obtido | Δ O₁ | % janela | Δ janela |
|---|---|---|---|---|---|
| **0,50** | **0,30** | 0,9491 | — | 81,4% | — |
| 0,45 | 0,35 | 0,9507 | +0,17% | 82,9% | +1,4 pp |
| 0,40 | 0,40 | 0,9494 | +0,03% | 82,9% | +1,4 pp |
| 0,35 | 0,45 | 0,9500 | +0,09% | 82,9% | +1,4 pp |
| 0,30 | 0,50 | 0,9498 | +0,07% | 85,7% | +4,3 pp |
| 0,20 | 0,60 | 0,9485 | −0,07% | 87,1% | **+5,7 pp** |
| 0,10 | 0,70 | 0,9475 | −0,18% | 82,9% | +1,4 pp |

**A alavanca de pesos praticamente não funciona.** Em `I3`, mais que dobrar o peso da retenção e
cortar o do edital a um quinto compra **0,6 pp** de janela ao custo de 3,64% de O₁ — sobre uma
lacuna de 19,4 pp. Em `I4` funciona melhor, mas fecha no máximo **5,7 pp** dos 18,6 pp de lacuna.

Há ainda um detalhe que merece registro, porque é um achado sobre o AG e não sobre a formulação: em
`I3`, **aumentar `w₃` faz O₃ cair** (0,5112 → 0,4928). O plano que o AG encontra otimizando
`0,10·O₁ + 0,70·O₃ + 0,20·O₄` vale 0,6013 sob esse próprio objetivo, enquanto o plano que ele já
encontra com os pesos de produção vale 0,6077 sob o mesmo objetivo. Isto é: **na região de pesos
extremos a busca piora**, e o AG deixa cerca de 1% na mesa. A paisagem fica mais plana quando O₃
domina — O₃ satura por construção — e a pressão seletiva enfraquece. Não é motivo para mudar nada
agora, mas é a razão pela qual "só aumentar o peso da retenção" não produz o efeito esperado.

**Efeito colateral do deslocamento em todas as instâncias** (0,50/0,30 → 0,40/0,40):

| Instância | Δ O₁ | Δ janela |
|---|---|---|
| `I1-pequeno-folgado` | +0,00% | +0,0 pp |
| `I2-medio-tipico` | −0,73% | +0,0 pp |
| `I3-grande-apertado` | −0,92% | +1,1 pp |
| `I4-pesos-extremos` | +0,03% | +1,4 pp |
| `I5-pesos-balanceados` | −0,05% | +0,0 pp |
| `I6-horizonte-curto` | −0,59% | +0,0 pp |
| `I7-horizonte-longo` | **−2,16%** | **−5,7 pp** |
| `I8-escala` | −0,08% | +0,0 pp |

`I7` é **estritamente pior** nas duas dimensões: perde 2,16% de O₁ *e* 5,7 pp de janela. Um
deslocamento de peso motivado por `I3`/`I4` cobraria esse preço de uma instância que já estava em
100%.

---

## 5. A alavanca que de fato move a métrica

A decomposição da §2 aponta onde está a diferença real. `I3` tem **22 de 25 disciplinas com
cobertura O₃ < 1 nos dois planos** — O₃ não consegue distinguir os dois. O que os distingue é a
**cauda** da alocação:

| Planejador (`I3`) | Dias mínimos | Mediana | Máximo | Fora da janela |
|---|---|---|---|---|
| AG de produção | **1** | 8 | 15 | 5 de 25 |
| Divisão uniforme | **3** | 7 | 17 | 1 de 25 |

| Planejador (`I4`) | Dias mínimos | Mediana | Máximo | Fora da janela |
|---|---|---|---|---|
| AG de produção | **1** | 17 | 42 | 2 de 10 |
| Divisão uniforme | **14** | 15 | 28 | 0 de 10 |

A métrica de janela é uma **contagem com limiar**: uma disciplina entra ou não entra. Uma disciplina
com 1 dia decai abaixo de `e⁻¹` até a prova independentemente de quanto peso a fitness dê à retenção,
porque O₃ é uma média ponderada e a média não vê a cauda. O parâmetro que age sobre a cauda é o
**piso de dias mínimos**, que vive em `BaselineCalculator.MIN_REQUIRED_DAYS` (hoje **1**) — fora da
função de fitness.

Medindo o piso com os pesos de produção intactos:

**`I3-grande-apertado`**

| Piso | O₁ obtido | Δ O₁ | % janela | Δ janela | Dias de sobrecarga |
|---|---|---|---|---|---|
| **1** (produção) | 0,6524 | — | 76,6% | — | 73 |
| 2 | 0,6509 | −0,22% | 77,1% | +0,6 pp | 72 |
| 3 | 0,6496 | −0,42% | 78,3% | +1,7 pp | 72 |
| **4** | 0,6489 | **−0,52%** | **100,0%** | **+23,4 pp** | 77 |
| 5 | 0,6459 | −1,00% | 100,0% | +23,4 pp | 78 |

**`I4-pesos-extremos`**

| Piso | O₁ obtido | Δ O₁ | % janela | Δ janela | Dias de sobrecarga |
|---|---|---|---|---|---|
| **1** (produção) | 0,9491 | — | 81,4% | — | 23 |
| **2** | 0,9489 | **−0,03%** | **100,0%** | **+18,6 pp** | 24 |
| 3 | 0,9475 | −0,17% | 100,0% | +18,6 pp | 30 |
| 4 | 0,9450 | −0,43% | 100,0% | +18,6 pp | 35 |
| 5 | 0,9427 | −0,68% | 100,0% | +18,6 pp | 32 |

**Piso 4 em toda a biblioteca**, com os pesos de produção:

| Instância | Disciplinas | Orçamento | % janela base | Δ O₁ com piso 4 | Δ janela com piso 4 |
|---|---|---|---|---|---|
| `I1-pequeno-folgado` | 6 | 80 | 100,0% | +0,00% | +0,0 pp |
| `I2-medio-tipico` | 12 | 140 | 100,0% | −0,33% | +0,0 pp |
| `I3-grande-apertado` | 25 | 200 | 76,6% | −0,52% | **+23,4 pp** |
| `I4-pesos-extremos` | 10 | 160 | 81,4% | −0,43% | **+18,6 pp** |
| `I5-pesos-balanceados` | 10 | 160 | 100,0% | **+0,35%** | +0,0 pp |
| `I6-horizonte-curto` | 8 | 105 | 100,0% | +0,00% | +0,0 pp |
| `I7-horizonte-longo` | 15 | 260 | 100,0% | **+0,02%** | +0,0 pp |
| `I8-escala` | 40 | 280 | 97,5% | −0,36% | **+2,5 pp** |

Nenhuma instância regride em retenção; o pior custo em O₁ é **−0,52%**; duas instâncias *melhoram*
em O₁; e a lacuna residual de 2,5 pp de `I8` também fecha. (Se isso neutraliza a anomalia de correlação
de `I8` registrada em [`06-regime-alta-carga.md`](./06-regime-alta-carga.md) §10 **não foi medido**:
aqui só o plano do AG foi reavaliado com o piso, não as seis estratégias que compõem o Spearman.)

**Comparando as duas alavancas em `I3`, na mesma moeda:**

| Alavanca | Custo em O₁ | Ganho na janela | Taxa de câmbio |
|---|---|---|---|
| Peso (0,10/0,70) | −3,64% | +0,6 pp | 0,16 pp por 1% de O₁ |
| Piso (4 dias) | −0,52% | +23,4 pp | **45 pp por 1% de O₁** |

Cerca de **270 vezes** mais eficiente.

---

## 6. A decisão pendente

O enunciado original da pendência era: *"até que ponto a vitória da divisão uniforme em `I3`/`I4` na
métrica de janela de retenção é aceitável do ponto de vista de produto?"* A medição mostra que a
pergunta, como formulada, embute uma premissa que os dados não sustentam — a de que aceitar a
retenção custaria cobertura do edital. **Ao preço medido, custa menos de 1%.**

Há então duas decisões separadas, e nenhuma delas é minha.

### D1 — Qual é o piso de retenção que o produto promete ao aluno?

Não é uma pergunta técnica. É a promessa que o SINAPSE faz:

- **(a) "Cobrimos o edital inteiro."** Toda disciplina do edital chega à prova acima do limiar de
  retenção. Implica um piso de cobertura e implica **recusar** um edital cujo orçamento de dias não
  comporte o piso, com mensagem explícita ao aluno, em vez de entregar um plano silenciosamente
  incompleto.
- **(b) "Otimizamos sua nota esperada."** Disciplinas de baixo valor podem ser sacrificadas quando o
  orçamento aperta, porque estudar 1 dia e esquecer é pior do que não estudar. Nesse caso 76,6% em
  `I3` não é um defeito — é o comportamento pretendido, e o documento a corrigir é o material de
  venda, não o código.

As duas posições são defensáveis. **(a)** é mais fácil de explicar ao Colégio Donaduzzi e mais
alinhada ao discurso de "plano de estudos completo"; **(b)** é mais honesta com um aluno que tem 200
dias e 25 disciplinas e vai, de fato, ter que abrir mão de algo. Os dados não decidem entre elas.

### D2 — Se D1 = (a): adotar o piso de cobertura?

Só faz sentido depois de D1. Números para essa conversa:

- **Custo medido:** ≤ 0,52% de O₁ em 8 instâncias sintéticas; ganho de até +23,4 pp de retenção.
- **Custo não medido — carga:** o piso aumenta os dias de sobrecarga em `I4` (23 → 35 com piso 4;
  23 → 24 com piso 2). O piso mínimo que resolve cada instância difere (2 em `I4`, 4 em `I3`), e
  fixar 4 para todos cobra de `I4` uma sobrecarga que o piso 2 não cobraria.
- **Viabilidade:** o piso exige `piso × nº de disciplinas ≤ orçamento de dias`. Com piso 4, um edital
  de 40 disciplinas precisa de ao menos 160 dias. `I8` (40 disciplinas, 280 dias) passa; um edital
  igual com 120 dias **não passa**, e o produto teria que decidir o que fazer nesse caso — reduzir o
  piso, recusar o plano, ou avisar o aluno de que o edital não cabe no prazo. **Essa regra de
  fallback não existe hoje e teria que ser especificada.**
- **Onde mexeria:** `BaselineCalculator`, não a fitness. Fora do escopo desta revisão do AG e com
  consumidores próprios (o piso alimenta `MinimumDaysConstraint` e os baselines do harness).

### O que fica explicitamente decidido nesta etapa

**Nada.** Os pesos permanecem `0,50 / 0,30 / 0,20`, o piso permanece 1, e o `assert` de soma dos
pesos em `FitnessEvaluator` continua ativo. O que a medição adiciona é que **um ajuste de pesos não
resolveria o problema de qualquer forma** — se a decisão for o lado da retenção, a alavanca é o
piso, e ela precisa de D1 respondida antes.

---

## 7. Ameaças à validade

1. **Oito instâncias sintéticas.** Nenhum edital real. O custo de 0,52% é o pior caso *entre as que
   foram testadas*.
2. **A métrica de janela é uma simulação** — replay do cronograma pelo `HybridRetentionEngine` com
   nota de recall fixa em 4, herdada de `03` §9. Ela não foi validada contra retenção real de aluno
   nenhum. Toda esta discussão trata uma proxy como se fosse o desfecho.
3. **A métrica é uma contagem com limiar em `e⁻¹`.** Em `I4`, uma disciplina vale 10 pp; em `I3`,
   4 pp. Diferenças pequenas nas tabelas são uma disciplina cruzando o limiar. Por isso todo ponto é
   média de 7 sementes — mas a granulosidade permanece.
4. **A carga cognitiva é medida sem o podador.** Os dias de sobrecarga vêm do cronograma sem
   `CognitiveLoadBalancingStrategy`, para expor a carga que o plano macro implica (`03` §9). O aluno
   receberia o plano podado — com menos sobrecarga e menos horas entregues.
5. **O achado de degradação da busca em `w₃` alto** (§4) é uma observação sobre médias de 7 sementes
   em uma instância. Não foi investigado.
6. **O piso foi medido por injeção**, via subclasse de `BaselineCalculator` no código de benchmark.
   O comportamento de produção com um piso maior — interação com `MinimumDaysConstraint`,
   `StudyPlanFactory` e a compressão do `StudyScheduleGenerator` — **não** foi testado.

## 8. Como reproduzir

```bash
./mvnw -o test-compile -DskipTests
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp "target/classes:target/test-classes:$(cat cp.txt)" \
     com.ia.project.dynamicstudyplanner.benchmark.robustness.WeightTradeoffMain
```

# Correção da Métrica Agregada e da Narrativa de Ausubel

**Data:** 2026-08-31 · **Etapa:** 07 · **Índice e status geral:** [`README.md`](./README.md)
**Objeto:** corrigir os dois defeitos que a etapa 06 encontrou e apenas registrou — uma estatística
inválida sustentando a conclusão de manchete, e uma justificativa técnica falsa sustentando uma
decisão de produto.
**Código novo:** `benchmarks/…/metric/CorrelationAggregate` · `…/metric/CorrelationAggregateTest` ·
`…/robustness/BaselineReplayMain`
**Ausubel continua não implementado.** Esta etapa corrige o que se diz sobre ele, não o que se faz.

---

## 1. O que estava errado

Dois defeitos, ambos da mesma família: **a conclusão estava certa e a evidência publicada não a
sustentava.**

| | Defeito | Consequência |
|---|---|---|
| **(a)** | O agregado de correlação empilhava 48 observações de 8 instâncias com escalas de fitness diferentes | A etapa 05 publicou `+0,161` e a frase "o sinal agregado inverteu". O valor correto é **−0,460**: a anti-correlação foi reduzida, nunca invertida |
| **(b)** | `06-decisao-ausubel.md` §2.3/§2.4 afirmavam que ordem é inexprimível e que qualquer validador topológico seria vácuo | Falso sobre o repositório: `TacticalStudyPlan` tem ordem e o `FitnessEvaluator` já o aceita. A decisão de não implementar continua certa — por outros motivos |

---

## 2. A metodologia estatística, fixada

### 2.1 Por que o empilhamento é inválido

O agregado antigo concatenava todos os pares (fitness, % na janela) e calculava **um** Spearman.
Isso ranqueia valores de fitness entre instâncias — e o próprio código já dizia que isso é proibido:

> `benchmarks/…/metric/PlanMetrics.java`: *"Comparable across planners on the same instance;
> **not** comparable across instances, because the fitness has no normalised unit."*

A fitness de `I4` (≈0,90) fica acima da de `I8` (≈0,55) porque são problemas diferentes, não porque
um plano seja melhor. O coeficiente conjunto mede sobretudo **qual instância é fácil**, e a variação
entre instâncias abafa a variação entre planejadores, que é a única que responde à pergunta.

O teste `CorrelationAggregateTest.StackingReversesTheConclusion` constrói o caso mínimo: duas
instâncias em que a fitness é **perfeitamente anti-correlacionada** com a retenção (ρ = −1,000 nas
duas), mas que vivem em faixas diferentes. Empilhadas, dão **ρ > +0,5**. É a forma exata do defeito,
reproduzida em 12 números.

### 2.2 As três opções e a escolha

| Opção | O que é | Por que não / por que sim |
|---|---|---|
| **(i)** Só correlações por instância, sem agregado | Máxima honestidade | Rejeitada como *única* saída: sem um resumo, comparar três estados do motor vira leitura de tabela. **Mantida como apresentação primária** — o agregado nunca substitui a tabela |
| **(ii)** Média aritmética das correlações | O que a etapa 06 usou ad hoc | Rejeitada: `r` não vive numa escala aditiva. A distância de 0,90 a 0,95 carrega muito mais informação que de 0,10 a 0,15, e a média simples subestima correlações fortes. Não produz intervalo de confiança defensável |
| **(iii)** **Transformada z de Fisher, ponderada por `n−3`** | `z = artanh(r)`, média ponderada, `tanh` de volta | **Escolhida.** `z` é aproximadamente normal com variância `1/(n−3)`; é o procedimento padrão para combinar coeficientes, e dá intervalo de confiança |

**Uma ressalva honesta sobre a ponderação.** Neste desenho ela não faz nada: toda instância correlaciona
os mesmos 6 planejadores, então todo peso é `6−3 = 3` e a média ponderada é igual à média simples
**dos valores z**. O ganho de (iii) sobre (ii) é a **estabilização de variância**, não o peso. A
ponderação foi implementada mesmo assim porque as varreduras de `HighLoadInstanceLibrary` podem um
dia comparar um número diferente de planejadores, e um agregado que assumisse `n` igual em silêncio
estaria errado a partir daí. Dizer que o peso "corrige a comparação" seria exagerar o que o método
compra.

### 2.3 Decisões de implementação que mudam números

- **Instâncias com correlação indefinida são excluídas e contadas**, não somadas como zero. Quatro
  das oito (`I1`, `I2`, `I5`, `I6`) saturam a métrica em 100% para todo planejador: não foram
  medidas como "sem correlação", foram medidas como "não há o que correlacionar". Tratá-las como
  zero fabricaria concordância que ninguém observou e puxaria todos os agregados para perto de zero.
- **`n ≤ 3` é excluído** — sem graus de liberdade, a transformada não tem variância definida.
- **`|r| = 1` é limitado** a `1 − 10⁻¹²` antes do `artanh`, que de outro modo iria a infinito e
  contaminaria a média. Atingível com 6 planejadores.
- **O intervalo de confiança usa `1,06/(n−3)`**, a inflação de variância de Spearman sobre Pearson
  (Fieller, Hartley & Pearson, 1957). Cancela na média, mas ignorá-la reportaria um intervalo mais
  estreito do que o dado sustenta.
- **O resultado carrega sempre o número de instâncias.** `Result.format()` não sabe imprimir o
  coeficiente sozinho — é deliberado, para que ele não seja citado sem o `n`.

### 2.4 Onde vive, e por que ali

Em `benchmarks/…/metric/`, ao lado de `Spearman` e `PlanMetrics` — não em `src/main`. A alternativa
seria empurrar uma estatística que só o harness usa para dentro do artefato servido em produção. O
requisito real do enunciado é que exista **um único método, versionado e testado, e não um script
avulso**: `CorrelationAggregate` é chamado por `RegimeCorrelationMain` e `BaselineReplayMain`, tem 10
testes, e é o único caminho pelo qual um agregado pode ser produzido.

---

## 3. Testes que travam a metodologia

`CorrelationAggregateTest`, **10 testes, todos passando**.

| Teste | O que prova |
|---|---|
| `withinEachInstanceTheCorrelationIsPerfectlyNegative` | O caso sintético é ρ = −1,000 dentro de cada instância |
| `poolingAcrossInstancesReportsAStrongPositiveCorrelation` | O método antigo devolve **ρ > +0,5** sobre esses mesmos dados |
| `fisherZAggregationPreservesTheWithinInstanceConclusion` | O método novo devolve **−1,000** |
| `theTwoMethodsDisagreeOnTheSign` | Os dois discordam **no sinal** — a asserção pedida pelo enunciado |
| `undefinedInstancesAreExcludedRatherThanCountedAsZero` | 4 indefinidas + 2 a −0,9 → agregado −0,9, `instancesUsed = 2` |
| `aPerfectCorrelationDoesNotBecomeInfinite` | ρ = 1,0 não vira `Infinity` |
| `fisherZWeighsStrongCorrelationsMoreThanAPlainMean` | Fisher < média simples para `{−0,95, +0,05}` |
| `instancesWithoutDegreesOfFreedomAreExcluded` | `n = 3` é descartado |
| `withNoDefinedInstanceTheResultIsUndefined` | Devolve `n/d`, não `0` |
| `publishedTrajectoryIsReproducedByTheCanonicalMethod` | **Fixa −0,909 / −0,460 / −0,106.** Se a trajetória mudar, o teste falha e este documento tem de mudar junto |

---

## 4. A tabela final — substitui todas as anteriores

Baseline da etapa 03 remedida com `BaselineReplayMain` sobre **10 execuções** do commit `07c99da`;
etapas 05 e 06 sobre 3 execuções cada (determinísticas, desvio 0,000 em todas).

### 4.1 Por instância

| Instância | **Etapa 03** (10 exec.) | **Etapa 05** | **Etapa 06 / hoje** |
|---|---|---|---|
| `I3-grande-apertado` | −0,937 ± 0,013 | −0,029 | **+0,257** |
| `I4-pesos-extremos` | −0,941 ± 0,000 | −0,525 | **−0,093** |
| `I7-horizonte-longo` | −0,847 ± 0,022 | +0,000 | **+0,655** |
| `I8-escala` | −0,880 ± 0,000 | −0,880 | **−0,880** |
| `I1`, `I2`, `I5`, `I6` | indefinida | indefinida | indefinida |

### 4.2 Agregado canônico

| Estado | Fisher z (n = 4 instâncias) | IC 95% |
|---|---|---|
| **Etapa 03** — fitness original | **−0,909** | −0,971 a −0,734 |
| **Etapa 05** — após correções de fidelidade | **−0,460** | −0,793 a +0,085 |
| **Etapa 06** — após temperagem dos pesos de retenção | **−0,106** | −0,597 a +0,443 |

**Leitura correta:** a anti-correlação caiu de −0,909 para −0,106 — quase uma ordem de grandeza — e
**converge para zero sem cruzá-lo**. Não houve inversão de sinal em etapa nenhuma.

**Leitura que os intervalos não autorizam:** afirmar que a etapa 06 "neutralizou" a anti-correlação.
Com n = 4 o intervalo vai de −0,597 a +0,443. A *direção* da trajetória é sólida — três estados,
melhora monotônica, cada um medido em código reconstruído do próprio commit. O *valor pontual* de
cada etapa não é preciso, e `I8` continua em −0,880 nos três estados.

### 4.3 Comparação com o que foi publicado antes

| Estado | Empilhado (**inválido**) | Média simples (ad hoc, etapa 06) | **Fisher z (canônico)** |
|---|---|---|---|
| Etapa 03 | −0,654 | −0,910 | **−0,909** |
| Etapa 05 | **+0,161** | −0,358 | **−0,460** |
| Etapa 06 | +0,196 | −0,015 | **−0,106** |

A coluna do meio, usada na verificação da etapa 06 para demonstrar o problema, chega perto do valor
correto na etapa 03 e se afasta nas outras duas — porque a média simples subestima correlações
fortes e mistura sinais opostos linearmente. **Só a coluna da direita deve ser citada.**

---

## 5. Ausubel: a narrativa corrigida

### 5.1 O que era falso

`06-decisao-ausubel.md` §2.4 afirmava:

> *"Qualquer validador topológico escrito hoje passaria para todo indivíduo, por construção."*

**É verdade do cromossomo macro e falso do repositório.** `domain/tactical/TacticalStudyPlan` mapeia
`TimeSlot → TacticalStudyBlock` com `LocalDateTime` reais, estende `StudyPlan`, e o
`FitnessEvaluator` de produção já o avalia **sem mudança de assinatura**. Provado em
`PrerequisiteSequencingDiagnosticTest`:

- a ordem **é** recuperável (`min` por `startTime()` devolve a disciplina correta);
- dois planos que diferem **apenas na ordem** recebem fitness idêntica — `0,2642479576` nos dois,
  diferença exatamente zero.

Ou seja: **a informação está lá e nada a lê.** Um validador de precedência teria o que verificar.

§2.3 tinha o mesmo defeito na outra direção: estimava a opção (i) como "da mesma ordem de grandeza ou
maior" que as etapas 00–05 somadas, listando "trocar `Map<Subject,Integer>` por estrutura com ordem"
como trabalho a fazer — quando encoding com ordem, crossover, mutação e operador de reparo **já
estavam escritos** no repositório, e a etapa 00 §1.9 já os havia mapeado como andaime morto.

### 5.2 O que passa a valer

**A decisão não muda: Ausubel não é implementado agora.** Mudam os motivos.

| | Motivo publicado antes | Motivo correto |
|---|---|---|
| 1 | "O encoding não expressa ordem" | ❌ Falso do repositório. Expressa, no cromossomo tático |
| 2 | "A versão mínima seria vacuosa" | ❌ Seria inerte, mas **por falta de dado**, não de expressividade |
| 3 | "Não existe DAG de pré-requisitos autorado por edital" | ✅ **Verdadeiro e decisivo.** É trabalho editorial/curatorial recorrente, não engenharia |
| 4 | "A API não coleta o dado" | ✅ **Verdadeiro.** `SubjectDto` e `Subject` têm três campos, nenhum relacional |

O bloqueio real é o item 3, e nenhuma linha de código o resolve: alguém precisa decidir, para cada
concurso, que "Controle de Constitucionalidade" pressupõe "Teoria da Constituição", e manter isso
quando o edital muda.

Há ainda um segundo motivo, técnico e menor: **nada produz um `TacticalStudyPlan`.** O encoding onde
a checagem funcionaria não é gerado por nenhum caminho de execução, então um validador escrito hoje
nunca rodaria em produção.

### 5.3 Estimativa revisada

| Frente | Antes estimado | Revisado |
|---|---|---|
| Encoding com ordem | Grande | **Já existe** (`TacticalStudyPlan`) |
| Aceitação pela fitness | Não considerado | **Já existe** — sem mudança de assinatura |
| Operadores | "O maior item" | **Adaptar**, não escrever |
| Reparo | Componente novo | **Adaptar** — `ChromosomeRepairer` define o contrato |
| **Motor de AG tático** | **Não listado** | **Escrever — é hoje o maior item** |
| Grafo + termo de fitness + API + agendador + benchmarks | Pequeno a médio | Inalterado |

A opção (i) continua sendo trabalho substancial, mas **menor do que se estimou** — mais perto de
"metade a uma vez" a revisão inteira do que de "uma vez ou mais". A diferença não muda a decisão;
muda o que é honesto dizer sobre ela numa auditoria.

---

## 6. Nota de método: 5 contra 7 repetições (G10)

`RegimeCorrelationMain` usa **5** repetições e `BenchmarkMain` usa **7**, então a mesma instância
aparece como `I3` = 76,8% num relatório e 76,6% no outro. É ruído de amostragem, não discordância.
A diferença passa a estar declarada no Javadoc de `RegimeCorrelationMain`, e qualquer documento que
cite os dois precisa dizer qual é qual. Os agregados desta etapa vêm todos do harness de 7
repetições, via `BaselineReplayMain`.

---

## 7. Status de G1–G11

Consolidado em [`README.md`](./README.md), que é a fonte única. Resumo:

| Severidade | Gaps | Resolvidos nesta etapa |
|---|---|---|
| 1 — evidência que não sustentava a conclusão | G1, G2, G3 | **3 de 3** ✅ |
| 2 — comportamento sem teste | G4, G5, G6 | 1 de 3 (G4) |
| 3 — documentação | G7, G8, G9, G10 | **4 de 4** ✅ |
| 4 — cosmético | G11 | 0 de 1 |

**8 de 11 resolvidos.** Os três abertos — G5 (camada tática morta), G6
(`TacticalStudyPlan.getDaysPerSubject` conta dias distintos) e G11 (duas estratégias não cabeadas) —
são todos sobre código que não está no caminho de execução de produção, e nenhum sustenta uma
afirmação publicada. G5 ganhou destino explícito nesta etapa: é o ponto de partida da opção (i) se
ela for reaberta.

---

## 8. O que esta etapa não fez

- **Não implementou Ausubel.** O item 4 do enunciado era correção de documentação.
- **Não mexeu na fitness.** Pesos seguem `0,50 / 0,30 / 0,20`, temperagem em `0,5`, piso de dias
  mínimos em `1`. O `assert` de soma dos pesos segue ativo, com teste do caminho de falha.
- **Não reabriu a pendência de negócio** de [`06-limite-troca-pesos.md`](./06-limite-troca-pesos.md).
- **Não corrigiu a variância da etapa 03 na origem** — seria preciso alterar o código sob medição,
  que deixaria de ser a baseline. Reportar a distribuição é o que se pode fazer honestamente.

---

## 9. Ameaças à validade que sobrevivem a esta etapa

1. **n = 4 instâncias.** Todos os agregados repousam em quatro coeficientes. Os intervalos são
   largos e dois deles cruzam o zero. A trajetória é confiável na direção, não no valor.
2. **A métrica de retenção continua sendo uma simulação** — replay do cronograma com grade 4 fixa,
   herdada de `03` §9. Nenhum aluno real foi medido. Toda esta discussão trata uma proxy como
   desfecho.
3. **Quatro das oito instâncias nunca discriminam.** Saturam em 100% para todo planejador, então
   metade do harness não contribui para nenhuma conclusão sobre correlação. Instâncias que
   discriminassem mais seriam mais informativas que mais repetições.
4. **A baseline da etapa 03 é reprodutível apenas em distribuição.** Dez execuções bastam para o
   sinal observado, que é grande (−0,909); não bastariam para uma afirmação fina sobre uma instância
   isolada.
5. **`I8` continua em −0,880 nos três estados**, sem explicação. Registrado como L4 revisada em
   [`05`](./05-fitness-function.md) §8; a hipótese do agendador tático segue **não medida**.

---

## 10. Como reproduzir

```bash
./mvnw -o clean test          # suite completa
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:target/test-classes:$(cat cp.txt)"

# Agregado canônico do estado atual:
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.RegimeCorrelationMain

# Baseline remedida de um estado anterior:
git worktree add /tmp/e03 07c99da && cd /tmp/e03 && ./mvnw -o test-compile -DskipTests
for i in $(seq -w 1 10); do
  java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain > /dev/null
  cp benchmarks/results/resultados.csv /tmp/snap03/run-$i.csv
done
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.BaselineReplayMain \
     /tmp/snap03 "Etapa 03"
```

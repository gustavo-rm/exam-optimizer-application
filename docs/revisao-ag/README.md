# Revisão do Algoritmo Genético — Índice

Revisão de engenharia do motor de planejamento macro do SINAPSE, conduzida em sete etapas entre
2026-08-30 e 2026-08-31. Esta página é a **porta de entrada** e a **fonte única de status** das
pendências G1–G11.

---

## Documentos, em ordem de leitura

| # | Documento | O que responde | Alterou código? |
|---|---|---|---|
| 00 | [`00-diagnostico.md`](./00-diagnostico.md) | O que o AG é hoje, mapeado sem juízo de valor. 51 riscos R1–R51 | Não |
| 01 | [`01-auditoria-fitness.md`](./01-auditoria-fitness.md) | Auditoria crítica de cada termo. Prova que **4 de 5** componentes eram inertes | Não |
| 02 | [`02-formulacao.md`](./02-formulacao.md) | Soma ponderada × multiobjetivo leve × Pareto. Recomenda a opção A | Não |
| 03 | [`03-validacao.md`](./03-validacao.md) | O AG supera baselines simples? Harness, 6 estratégias, 8 instâncias | Só benchmarks |
| 04 | [`04-robustez.md`](./04-robustez.md) | Determinismo, variância entre sementes, hiperparâmetros, casos de borda | Sim — correções de baixo risco |
| 05 | [`05-fitness-function.md`](./05-fitness-function.md) | **Documentação de referência permanente da fitness.** Comece por aqui se quiser entender o motor hoje | Sim — correções de fidelidade |
| 06a | [`06-decisao-ausubel.md`](./06-decisao-ausubel.md) | Implementar sequenciamento por pré-requisitos? Decisão: **não agora** | Só documentação |
| 06b | [`06-regime-alta-carga.md`](./06-regime-alta-carga.md) | Onde a fitness deixa de ajudar a retenção. Fronteira caracterizada | Sim — temperagem dos pesos |
| 06c | [`06-limite-troca-pesos.md`](./06-limite-troca-pesos.md) | Troca pontuação × retenção. **Pendência de negócio aberta** | Não |
| 06d | [`06-verificacao-pos-rodada.md`](./06-verificacao-pos-rodada.md) | Verificação independente em código. Origem de G1–G11 | Só um teste |
| 07 | [`07-correcao-metrica-e-ausubel.md`](./07-correcao-metrica-e-ausubel.md) | **Estado atual.** Métrica corrigida, baseline remedida, narrativa de Ausubel | Sim — método de agregação |

---

## Números canônicos

Qualquer relatório futuro cita **estes** valores. Correlação de Spearman entre fitness e % de
disciplinas na janela de retenção, agregada entre instâncias por **transformada z de Fisher**
(`benchmarks/…/metric/CorrelationAggregate.java`) — nunca por empilhamento de observações.

| Estado | Agregado (Fisher z, n = 4 instâncias) | IC 95% |
|---|---|---|
| Etapa 03 — fitness original (média de 10 execuções) | **−0,909** | −0,971 a −0,734 |
| Etapa 05 — após correções de fidelidade | **−0,460** | −0,793 a +0,085 |
| Etapa 06 — após temperagem dos pesos de retenção | **−0,106** | −0,597 a +0,443 |

**A anti-correlação foi reduzida em quase uma ordem de grandeza, mas não invertida.** Com n = 4 os
intervalos são largos: a *direção* da trajetória é sólida, o valor pontual de cada etapa não é
preciso. Detalhamento e tabela por instância em [`07`](./07-correcao-metrica-e-ausubel.md) §4.

> **Números que não devem mais ser citados:** o agregado empilhado das etapas 03/05/06
> (−0,654 → +0,161 → +0,196) é estatisticamente inválido e inverte o sinal da conclusão. Ver
> [`07`](./07-correcao-metrica-e-ausubel.md) §2.

---

## Status das pendências G1–G11

Levantadas em [`06-verificacao-pos-rodada.md`](./06-verificacao-pos-rodada.md) §7. Ordenadas por
severidade original. **Atualizado em 2026-08-31 (etapa 07).**

### Severidade 1 — evidência que não sustentava a conclusão que apoiava

| # | Gap | Status | Onde |
|---|---|---|---|
| **G1** | Correlação agregada empilhava fitness não comparável entre instâncias | ✅ **RESOLVIDO** — método canônico (Fisher z) implementado em `CorrelationAggregate`, com 10 testes; toda a documentação republicada com nota de correção datada | [`07`](./07-correcao-metrica-e-ausubel.md) §2–§3 |
| **G2** | Premissa "ordem é inexprimível por construção" falsa sobre o repositório | ✅ **RESOLVIDO** — §1, §2.3, §2.4 de `06-decisao-ausubel.md` reescritas; a decisão se mantém pelos motivos corretos | [`07`](./07-correcao-metrica-e-ausubel.md) §5 |
| **G3** | Estimativa de esforço da opção (i) ignorava andaime existente | ✅ **RESOLVIDO** — §2.3 refeita com inventário do que já existe e o item que faltava (motor de AG tático) | [`06a`](./06-decisao-ausubel.md) §2.3 |

### Severidade 2 — comportamento não coberto por teste

| # | Gap | Status | Onde |
|---|---|---|---|
| **G4** | Nenhum teste travava o valor da correlação fitness × retenção | ✅ **RESOLVIDO** — `CorrelationAggregateTest.RealDataRegression` fixa a trajetória −0,909 → −0,460 → −0,106 e falha se ela mudar | `benchmarks/…/metric/CorrelationAggregateTest` |
| **G5** | Camada tática morta sem teste nem plano | ⬜ **ABERTO** — ~400 linhas com `@Component`/`@Service` que o Spring instancia e ninguém consome. Agora com destino explícito: é o ponto de partida da opção (i) se ela for reaberta | [`06a`](./06-decisao-ausubel.md) §2.3 |
| **G6** | `TacticalStudyPlan.getDaysPerSubject()` conta dias distintos, não planejados | ⬜ **ABERTO** — inofensivo hoje (nada produz esses planos), armadilha no dia em que algo produzir | `domain/tactical/TacticalStudyPlan:26-42` |

### Severidade 3 — documentação desatualizada ou imprecisa

| # | Gap | Status | Onde |
|---|---|---|---|
| **G7** | Números da etapa 03 com 3 decimais sem faixa, medidos sem determinismo | ✅ **RESOLVIDO** — remedidos em 10 execuções, publicados como média ± desvio-padrão; `BaselineReplayMain` torna a medição repetível | [`03`](./03-validacao.md) §5 · [`07`](./07-correcao-metrica-e-ausubel.md) §4 |
| **G8** | `+0,161` removido de `05` §6.4 sem revisar a frase que dele dependia; `−0,654` vivo em `03` §5 | ✅ **RESOLVIDO** — notas de correção datadas em `03` e `05`, ambas as tabelas substituídas | [`03`](./03-validacao.md) · [`05`](./05-fitness-function.md) |
| **G9** | `01` §3.3 afirma "um mapa não tem ordem" sem qualificar que fala do cromossomo macro | ✅ **RESOLVIDO** — precisão acrescentada em `01` §3.3 e em `06a` §1 | [`01`](./01-auditoria-fitness.md) §3.3 |
| **G10** | 5 e 7 repetições circulam sem que a diferença de método seja declarada | ✅ **RESOLVIDO** — declarado em [`07`](./07-correcao-metrica-e-ausubel.md) §6 e no cabeçalho de `RegimeCorrelationMain` | [`07`](./07-correcao-metrica-e-ausubel.md) §6 |

### Severidade 4 — cosmético

| # | Gap | Status | Onde |
|---|---|---|---|
| **G11** | `CriticalFirstStrategy` e `RandomizedInterleavedStrategy` existem e nunca são usadas | ⬜ **ABERTO** — corretas, sem anotação Spring, sem consumidor. Custo de manter é próximo de zero | `service/scheduler/strategy/` |

**Resumo: 8 de 11 resolvidos.** Os três abertos (G5, G6, G11) são todos sobre a camada tática morta
ou código não cabeado — nenhum afeta o caminho de execução de produção, e nenhum sustenta uma
afirmação publicada.

---

## Pendências de decisão humana

Separadas dos gaps porque **não são defeitos** — são escolhas que engenharia não deve fazer sozinha.

| Pendência | Pergunta | Documento |
|---|---|---|
| **Troca de pesos** | Até onde a vitória da divisão uniforme na métrica de retenção é aceitável? Qual promessa de cobertura o produto faz ao aluno? | [`06c`](./06-limite-troca-pesos.md) §6 |
| **Ausubel** | Reabrir quando existir DAG de pré-requisitos autorado por edital | [`06a`](./06-decisao-ausubel.md) §4.2 |
| **P1–P7 da etapa 04** | Correções estruturais listadas como pendência, não implementadas | [`04`](./04-robustez.md) |

---

## Como rodar as medições

```bash
./mvnw -o clean test                       # suíte completa
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:target/test-classes:$(cat cp.txt)"

java -cp "$CP" com.ia...benchmark.harness.BenchmarkMain              # baselines (05 §6.1, §6.3)
java -cp "$CP" com.ia...benchmark.robustness.RegimeCorrelationMain   # correlação + agregado canônico
java -cp "$CP" com.ia...benchmark.robustness.WeightSensitivityMain   # estabilidade dos pesos (05 §5.2)
java -cp "$CP" com.ia...benchmark.robustness.WeightTradeoffMain      # troca de pesos (06c)
java -cp "$CP" com.ia...benchmark.robustness.RobustnessMain          # determinismo e hiperparâmetros
java -cp "$CP" com.ia...benchmark.robustness.BaselineReplayMain <dir> # agrega N execuções salvas
```

Prefixo completo: `com.ia.project.dynamicstudyplanner`.

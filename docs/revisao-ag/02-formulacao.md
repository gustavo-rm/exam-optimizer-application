# Formulação Multiobjetivo: Decisão de Nível de Rigor para o Produto

**Documentos anteriores:** [`00-diagnostico.md`](./00-diagnostico.md) (mapa do estado atual) ·
[`01-auditoria-fitness.md`](./01-auditoria-fitness.md) (auditoria crítica)
**Este documento:** etapa de decisão. Avalia três níveis de rigor multiobjetivo e recomenda um.
**Commit:** `6ed3c3c` — nenhum código alterado.
**Data:** 2026-08-30

---

## 0. Correção de premissa antes de decidir

O enunciado desta etapa parte de que *"a fitness atual provavelmente combina múltiplos
objetivos (aderência a prazo, carga cognitiva, repetição espaçada, prioridade por peso de
edital) em um único escalar por soma ponderada"*.

**Essa descrição corresponde à intenção declarada da arquitetura, não ao comportamento em
execução.** O `FitnessEvaluator` de fato implementa o *mecanismo* de soma ponderada e seu
Javadoc descreve exatamente um MOOP por soma ponderada (`FitnessEvaluator.java:16-20`). Mas a
etapa 1 estabeleceu, com prova:

| Objetivo suposto pelo enunciado | Situação verificada |
|---|---|
| Prioridade por peso de edital | **Existe** — `ScoreGainObjective`, peso `1.0`. É o único |
| Repetição espaçada | Termo existe (`MandatoryReviewConstraint`) mas é **inerte por guarda de tipo** (`01` §1.1) |
| Carga cognitiva | **Não existe termo algum.** `Subject.cognitiveLoad` não é lido por nenhum componente ativo (`01` §2.5.1) |
| Aderência a prazo | **Não existe termo algum.** Verificado nesta etapa: `examDate` nunca chega ao AG — só é usado em `StudyScheduleGenerator` (`StudyScheduleGenerator.java:52,55,120`), depois da otimização |

Ou seja: **há um objetivo, um peso, e nenhum trade-off.** Isso não é preciosismo — é o que
determina a viabilidade das três opções:

- **Opção B é inexecutável hoje.** Rodar o AG com 2-3 configurações de peso produziria
  **planos idênticos**. Com um único objetivo, mudar seu peso é um reescalonamento global que
  não altera o `argmax` (mesmo argumento do Apêndice B de `01`). Verificado numericamente:

  ```
  peso 0.8  -> plano [27, 2, 1]
  peso 1.0  -> plano [27, 2, 1]
  peso 1.2  -> plano [27, 2, 1]
  ```

- **Opção C é inexecutável hoje.** Uma fronteira de Pareto sobre um único objetivo é um ponto.

- **Opção A é apenas parcialmente executável hoje.** "Normalizar todos os termos" e
  "documentar a justificativa de cada peso" não têm sobre o que operar além de um termo; e a
  análise de sensibilidade ±20% retornaria, corretamente, *"o plano nunca muda"* — um
  resultado tecnicamente válido e comercialmente inútil.

**Consequência para esta decisão.** As três opções não são alternativas entre si sobre uma
fitness multiobjetivo existente. Todas pressupõem objetivos que precisam ser **construídos
primeiro**. A pergunta real é: *qual é o destino, e qual o menor caminho defensável até lá?*

Este documento responde às três opções como pedido — mas as trata como **destinos**, com o
pré-requisito comum explicitado em §1. Isso não reduz o escopo pedido: avalia as três com
prós/contras técnicos e de produto, e fecha com recomendação. Apenas ordena o que precisa
existir antes de qualquer uma delas ser possível.

---

## 1. Pré-requisito comum às três opções: construir os objetivos

Nenhuma das opções é avaliável sem isto. O conjunto mínimo de objetivos para que exista
trade-off real:

| Obj. | Nome | O que mede | Custo | Depende do encoding? |
|---|---|---|---|---|
| **O₁** | Aderência ao edital | `Σ Î_s · M_s(d_s)`, com `Î_s` normalizada no simplex e `M_s` curva com teto (`01` §2.1.6 a+b) | Baixo — refatora o termo existente | Não |
| **O₂** | Aderência a prazo / viabilidade | Fração do plano que cabe no calendário real: `min(1, horasDisponíveis / horasRequeridas)`, com `horasDisponíveis` derivado de `examDate` + `weeklyAvailability` | **Muito baixo** — os dados já existem, só nunca chegaram ao AG | Não |
| **O₃** | Retenção na data da prova (Ebbinghaus) | `(1/n) Σ_s R_s(examDate)` | Médio (aproximado) / Alto (fiel) | **Sim** — versão fiel exige encoding temporal (`01` §3.3) |
| **O₄** | Carga cognitiva (Sweller) | Aproximação por concentração ponderada: dispersão de `Σ d_s · cognitiveLoad_s` | Baixo (aproximado) / Alto (orçamento diário real) | **Sim** para a versão fiel |

**O₂ merece destaque como o item de melhor relação custo/benefício de todo o backlog.** Hoje o
AG otimiza a alocação de `totalStudyDays` — um número que o *cliente* envia — sem nenhum
conhecimento de `examDate` nem da disponibilidade semanal do aluno. Quem descobre que o plano
não cabe no calendário é o `StudyScheduleGenerator`, **depois** da otimização, que então
comprime tudo por `reductionFactor = availableHours / requiredHours`
(`StudyScheduleGenerator.java:96`) e devolve `WARNING_TIME_DEFICIT`. O otimizador é cego para a
restrição mais concreta que o aluno tem.

> **Verificação honesta de um efeito que eu esperava ser um defeito, e não é.** Testei se essa
> compressão proporcional degrada a qualidade da alocação frente a reotimizar para o orçamento
> real. Em 200 editais sintéticos (12 matérias, déficit de 30% e 50%), a perda foi de **0,02%
> em média e 0,37% no pior caso** — desprezível. A compressão proporcional é numericamente
> quase ótima. **O problema de O₂ não é numérico, é de produto:** o aluno recebe um plano
> silenciosamente encolhido e um enum de aviso, em vez de o otimizador ter buscado, desde o
> início, o melhor plano *que cabe no tempo dele*.

**Pré-requisito do pré-requisito: reprodutibilidade.** A análise de sensibilidade da Opção A e a
comparação de cenários da Opção B **exigem execução determinística**. Hoje ela é impossível: a
decisão "este indivíduo sofre mutação?" usa `Math.random()` e a escolha de matérias usa
`ThreadLocalRandom` — nenhum dos dois é semeável nem passa pelo `RandomProvider`
(`00` §7.4, R18). É a menor tarefa de toda a lista e destrava todas as outras.

---

## 2. Opção A — Soma ponderada corrigida

Normalizar todos os termos para a mesma escala, documentar a justificativa de cada peso,
adicionar análise de sensibilidade (±20%).

### Prós técnicos

- **Preserva a arquitetura.** `FitnessEvaluator` já é uma soma ponderada com listas injetadas;
  acrescentar objetivos é acrescentar beans. Zero refatoração estrutural.
- **A normalização é pré-requisito das outras duas opções, não alternativa a elas.** Sem ela,
  um termo de retenção (natural em `[0,1]`) seria ~5 ordens de grandeza menor que O₁ na escala
  atual (`01` §2.1.4) — aritmeticamente invisível. O `crowding distance` do NSGA-II também
  exige normalização. **Fazer A não é escolher A: é pagar uma dívida que B e C também têm.**
- **A análise de sensibilidade é barata e produz defensabilidade científica real.** É o
  entregável que converte "escolhemos os pesos" em "medimos que os pesos estão em região
  estável". Ilustração do formato do resultado, com dois objetivos e ±20% em `w₁`:

  ```
  base    (w = 0.70 / 0.30):  [23, 4, 3]
  w₁ −20% (w = 0.56 / 0.44):  [20, 6, 4]   distância L1 = 6 dias (10% do orçamento)
  w₁ +20% (w = 0.84 / 0.16):  [26, 3, 1]   distância L1 = 6 dias (10% do orçamento)
  ```

  Uma afirmação como *"uma perturbação de ±20% nos pesos desloca menos de 10% do orçamento de
  dias"* é verificável, reprodutível e citável. É exatamente o tipo de evidência que hoje não
  existe em nenhum dos 7 `.md` da raiz.

### Contras técnicos

- **Escalarização por soma ponderada só alcança o casco convexo da fronteira de Pareto.**
  Limitação clássica e inescapável: soluções em regiões não convexas da fronteira são
  inalcançáveis para *qualquer* vetor de pesos. Se os objetivos deste domínio produzirem
  regiões não convexas, A nunca as encontrará — e, pior, não haverá sinal de que elas existem.
- **Os pesos continuam sendo uma decisão de produto vestida de parâmetro técnico.** Documentar
  a justificativa melhora muito a defensabilidade, mas não elimina o fato de que alguém
  escolheu `0.7/0.3` em vez de `0.6/0.4`.
- **Um escalar por indivíduo esconde o trade-off do próprio time.** Sem ver os pontos
  alternativos, é difícil saber se os objetivos são conflitantes ou redundantes.

### Prós de produto

- **Sem mudança de contrato de API.** `PlannerResponseDto` é single-plan
  (`message`, `OptimizationResultDto`, `ScheduleResultDto`) e continua servindo.
- **Risco mínimo.** Nada no comportamento visível muda além da qualidade do plano.
- **Entrega material comercial defensável.** A análise de sensibilidade vira seção de README /
  whitepaper e sustenta a afirmação de fundamentação científica — que hoje não se sustenta
  (`01` §4).

### Contras de produto

- **Não expõe ao usuário que existe mais de um plano ótimo.** O trade-off continua invisível
  para quem usa. Nenhum valor novo percebido pelo aluno.
- **Não diferencia o produto.** A melhoria é de qualidade interna, não de funcionalidade.

### Esforço

Baixo a médio. Ordem de grandeza: reprodutibilidade (pequeno) + normalização de O₁ (pequeno) +
O₂ (pequeno, dados já existem) + O₄ aproximado (pequeno) + harness de sensibilidade
(pequeno-médio) + documentação dos pesos (pequeno). Nada exige mudar encoding, API ou UI.

---

## 3. Opção B — Multiobjetivo leve (2-3 escalarizações + filtro não-dominado)

Rodar o solver com 2-3 vetores de peso representando pontos distintos do trade-off e apresentar
os resultados não-dominados entre si.

### Prós técnicos

- **Reaproveita 100% de A.** B é literalmente "rodar A `k` vezes e filtrar". O filtro de
  não-dominância entre `k ≤ 3` candidatos são ~20 linhas triviais.
- **Custo computacional potencialmente *menor* que o AG atual.** Achado específico deste
  código: se O₁…O₄ permanecerem separáveis e côncavos em `d_s`, cada escalarização é resolvível
  **exatamente** pelo guloso `O(D log n)` (`01` §3.4, Apêndice A). Três escalarizações exatas
  custam microssegundos, contra as até 500 000 avaliações estocásticas de uma única execução do
  AG hoje. **B pode ser 3 planos exatos por uma fração do custo de 1 plano aproximado.**
- **Revela ao time se os objetivos são de fato conflitantes** — informação que A não produz e
  que é pré-requisito para decidir sobre C.

### Contras técnicos

- **Herda a limitação do casco convexo.** Escalarização repetida não alcança regiões não
  convexas; `k` execuções de soma ponderada não são uma fronteira de Pareto.
- **Rigor terminológico obrigatório.** Com `k = 3`, o resultado são **três cenários**, não uma
  fronteira. Chamá-los de "fronteira de Pareto" em material de produto seria exatamente o tipo
  de imprecisão que estas revisões existem para eliminar — e seria facilmente contestável por
  qualquer avaliador técnico.
- **Risco real de cenários quase idênticos.** Se os objetivos forem correlacionados, os 3
  planos colapsam em variações triviais. O₁ (peso de edital) e O₂ (aderência a prazo) têm
  motivo estrutural para correlacionar. **Isso é mensurável antes de construir a feature** — ver
  o critério de decisão em §5.
- **Se o encoding migrar para indexado no tempo, B fica caro.** A separabilidade se quebra, o
  guloso deixa de valer, e B passa a custar `k ×` uma execução completa do AG.

### Prós de produto

- **É a única das três opções com valor visível para o aluno.** "Plano com foco em cobertura do
  edital" vs. "plano com foco em retenção" vs. "plano equilibrado" é compreensível, explicável e
  vendável. Reconhece honestamente que não existe um único plano ótimo — o que é
  cientificamente correto *e* comercialmente melhor que fingir que existe.
- **Cria superfície de diferenciação** que concorrentes com heurística fixa não têm.

### Contras de produto

- **Exige mudança no contrato da API.** `PlannerResponseDto` precisa carregar uma lista de
  planos rotulados. É uma quebra de contrato ou uma versão nova do endpoint.
- **Exige UI de escolha.** Apresentar 3 planos sem uma forma clara de compará-los transfere
  carga de decisão ao aluno — que é exatamente o público com menos tempo e menos apetite para
  isso. Nomear os cenários bem é trabalho de produto, não de engenharia.
- **Triplica o custo do agendamento tático.** Cada plano macro precisa passar pelo
  `StudyScheduleGenerator` para virar cronograma apresentável.

### Esforço

A + incremento pequeno na engenharia, **incremento não-trivial em produto/UI**. O gargalo de B
não é o algoritmo; é o design da escolha.

---

## 4. Opção C — Pareto completo (non-dominated sorting / NSGA-II)

### Prós técnicos

- Alcança regiões não convexas da fronteira, que A e B não alcançam.
- Não exige escolher pesos *a priori*.
- É o estado da arte e o que se espera de uma formulação multiobjetivo rigorosa.

### Contras técnicos

- **O custo não está no algoritmo.** *Fast non-dominated sort* + *crowding distance* somam algo
  como 200-300 linhas — não é o obstáculo, e seria desonesto argumentar que é. O custo está na
  **infraestrutura de validação que não existe**:

  | Requisito para validar um MOEA | Estado atual (`00` §5.2, §7.4) |
  |---|---|
  | Execução determinística (seed fixável) | **Impossível** — `Math.random()` + `ThreadLocalRandom` |
  | Testes de convergência do AG | **Nenhum** |
  | CI | **Nenhum** (não existe `.github/`) |
  | Métrica de qualidade de fronteira (hypervolume, IGD) | **Nenhuma** |
  | Benchmark contra baseline | **Nenhum** |
  | Testes de operadores de crossover | **Nenhum** |

  Introduzir um MOEA aqui é acrescentar sofisticação **não verificável** sobre uma base **não
  verificada**. O risco não é o NSGA-II estar errado; é ninguém conseguir demonstrar que está
  certo.

- **C também depende de A.** `crowding distance` compara distâncias entre objetivos e exige
  normalização. Sem A, a diversidade da fronteira fica dominada pelo objetivo de maior escala.
- **A fronteira reintroduz a ponderação que C queria evitar.** Com 2-4 objetivos, a fronteira
  tem dezenas de pontos. O produto precisa entregar **um** plano (ou poucos). Escolher entre
  eles é ponderar — só que agora de forma implícita e não documentada, o que é *pior* que a
  ponderação explícita da Opção A.

### Contras de produto

- **Não existe consumidor.** Nenhuma superfície do produto consome um conjunto de soluções. O
  contrato é single-plan de ponta a ponta.
- **Argumento organizacional, o mais forte contra C nesta rodada:** a formulação multiobjetivo
  completa **já é propriedade da linha de doutorado do projeto**. Construir uma segunda
  implementação de NSGA-II no produto cria duas versões divergentes do mesmo artefato. No
  momento de publicar ou defender, aparece a pergunta *"qual é o resultado — o do produto ou o
  da tese?"*, e qualquer divergência entre as duas vira passivo de credibilidade em vez de
  ativo. **Manter a separação limpa entre motor de produto e formulação de pesquisa é um ativo
  metodológico**, não uma limitação de escopo.

### Quando C passaria a se justificar

Gatilhos concretos, não "quando amadurecer":

1. A Opção B mostrar, com medição, que os objetivos são **genuinamente conflitantes** (cenários
   materialmente diferentes) **e** que há indício de região não convexa na fronteira.
2. O produto passar a vender explicitamente exploração de trade-offs (ex.: um controle
   interativo de prioridade).
3. A infraestrutura de validação da tabela acima existir — o que é bom investimento
   independentemente de C.

### Esforço

Alto, e mal distribuído: pequeno no algoritmo, grande na validação, na API, na UI e na
coordenação com a linha de pesquisa.

---

## 5. Recomendação

### **Adotar a Opção A nesta rodada, com a Opção B como fast-follow condicionado a medição. Reservar a Opção C à linha de pesquisa.**

A justificativa em uma frase: **A é a única opção cujo trabalho é pré-requisito das outras
duas**, e o estado atual — um objetivo, quatro componentes inertes, zero reprodutibilidade,
zero testes de AG — não sustenta nada mais sofisticado do que isso ainda.

### Faseamento proposto

**Fase 0 — Reprodutibilidade.** Tornar a execução do AG determinística sob seed fixa. É a menor
tarefa da lista, é pré-requisito de toda medição em A, B e C, e hoje bloqueia até a validação
básica. Sem isto, a "análise de sensibilidade" da Opção A não distingue efeito de peso de ruído
de RNG.

**Fase 1 — Opção A.** Construir O₁ (normalizado, com teto), O₂ (aderência a prazo — melhor
custo/benefício do backlog) e O₄ aproximado; documentar a justificativa de cada peso; entregar
a análise de sensibilidade ±20%. Resolver conscientemente os quatro componentes inertes
(`01` §0) para que o pipeline descreva o que faz.

**Fase 2 — Opção B, condicionada a um teste barato.** Antes de construir a feature, rodar as
2-3 escalarizações e **medir a distância entre os planos resultantes**:

- Se a distância L1 mediana entre cenários for **materialmente grande** (referência inicial
  sugerida: > 15% do orçamento de dias), os objetivos são de fato conflitantes → B tem valor de
  produto real e deve ser construída.
- Se for **pequena**, os objetivos são redundantes → B entregaria três planos quase idênticos
  com três rótulos diferentes, o que é pior que não entregar. Nesse caso a resposta certa é
  revisar os objetivos, não construir a feature.

Este critério transforma "faremos B depois" em uma decisão baseada em dado que custa uma tarde
para produzir, uma vez que a Fase 1 exista.

**Opção C — reservada à pesquisa**, com os gatilhos de reavaliação de §4 registrados.

### Por que não B agora

B é a opção com maior valor de produto, e a tentação de ir direto a ela é legítima. Mas hoje ela
é **literalmente inexecutável** (§0): produziria três planos idênticos. E mesmo depois da Fase 1,
seu gargalo real é design de produto — nomear e apresentar os cenários — não engenharia. Fazer a
Fase 1 primeiro custa pouco, é pré-requisito de B de qualquer forma, e produz exatamente a
medição que diz se B vale a pena.

### Por que não C agora

Não por dificuldade algorítmica — NSGA-II é acessível. Por três razões que se somam: não há
consumidor de produto para um conjunto de soluções; não há infraestrutura para validar que o
resultado está correto; e a linha de doutorado já é dona dessa formulação, de modo que duplicá-la
gera risco de credibilidade em vez de rigor. C é a resposta certa para a pergunta errada nesta
rodada.

### Dependência que condiciona toda a decisão

A escolha entre A/B/C é **ortogonal, mas subordinada, à decisão de encoding** (`01` §3.3, §4):

- **Mantendo `Map<Subject, Integer>`:** O₁, O₂ e O₄-aproximado são viáveis; O₃ (Ebbinghaus)
  entra apenas como aproximação grosseira e **não deve ser rotulado como repetição espaçada**;
  Ausubel permanece inexpressável. A recomendação acima vale integralmente, com a ressalva de
  que os rótulos teóricos precisam ser honestos sobre o grau de aproximação.
- **Migrando para encoding indexado no tempo:** os quatro objetivos ficam expressáveis, a
  separabilidade se quebra e o AG passa a ser genuinamente necessário (`01` §3.4) — mas a
  Opção B fica `k×` mais cara, porque cada cenário exige uma execução completa do AG em vez do
  guloso exato.

Recomendo tratar o encoding como decisão separada e explícita, na próxima etapa. A Fase 0 e a
Fase 1 são valiosas e não desperdiçadas em qualquer dos dois caminhos.

---

## 6. Quadro comparativo

| Critério | **A — Soma ponderada corrigida** | **B — Multiobjetivo leve** | **C — Pareto completo** |
|---|---|---|---|
| Executável hoje | Parcialmente (só após criar objetivos) | **Não** — geraria planos idênticos | **Não** — fronteira de 1 ponto |
| Esforço de engenharia | Baixo-médio | A + pequeno | Alto (validação, não algoritmo) |
| Esforço de produto/UI | Nenhum | **Não-trivial** — design da escolha | Alto |
| Muda contrato de API | Não | **Sim** | Sim |
| Ganho de defensabilidade científica | **Alto** — normalização + sensibilidade medida | Alto — expõe o trade-off | Alto no papel, **não verificável** na base atual |
| Valor para o usuário final | Indireto (qualidade do plano) | **Direto** — escolhe o perfil do plano | Nenhum sem UI dedicada |
| Alcança região não convexa da fronteira | Não | Não | **Sim** |
| Pré-requisito das outras | **Sim — B e C dependem de A** | Não | Não |
| Risco de duplicar a linha de pesquisa | Nenhum | Baixo | **Alto** |
| **Veredito** | **Adotar agora** | **Fast-follow, condicionado a medição** | **Reservar à pesquisa** |

---

## Apêndice — Verificações numéricas desta etapa

Todas reproduzíveis; parâmetros nas próprias linhas.

**1. Sensibilidade a pesos com um único objetivo é identicamente nula.**
`I = (100, 10, 1)`, `D = 30`, `m = (1,1,1)`, guloso exato:

```
peso 0.8 → [27, 2, 1]     peso 1.0 → [27, 2, 1]     peso 1.2 → [27, 2, 1]
```

Confirma §0: a Opção B não produz cenários distintos no estado atual.

**2. Sensibilidade com dois objetivos normalizados é mensurável.**
`O₁` = importância normalizada no simplex; `O₂` = cobertura uniforme; base `w = 0.70/0.30`:

```
base     [23, 4, 3]
w₁ −20%  [20, 6, 4]   L1 = 6 dias (10% do orçamento)
w₁ +20%  [26, 3, 1]   L1 = 6 dias (10% do orçamento)
```

Ilustra o formato do entregável da Opção A.

**3. A compressão proporcional do `StudyScheduleGenerator` é numericamente quase ótima.**
200 editais sintéticos, 12 matérias, `D = 180`, déficits de 30% e 50%; comparação entre
reotimizar para o orçamento real e escalar proporcionalmente o plano original:

| Cenário de importâncias | perda média | mediana | p95 | máx |
|---|---|---|---|---|
| Balanceadas (pós-normalização, razão ~3:1) | 0,024% | 0,016% | 0,082% | 0,163% |
| Dispersas (estado atual, razão até 1000:1) | 0,052% | 0,027% | 0,171% | 0,366% |

**Resultado negativo, registrado como tal:** eu esperava que a compressão proporcional fosse um
defeito numérico e não é. O argumento para O₂ (aderência a prazo) é de produto — o otimizador
deveria buscar o melhor plano *que cabe no tempo do aluno* desde o início, em vez de otimizar
para um orçamento fictício e comprimir depois — e não de perda de qualidade da alocação.

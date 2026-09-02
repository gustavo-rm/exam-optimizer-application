# ADR-0003 — Unificar a cadeia de alocação; manter a composição de fitness duplicada, com guarda

- **Estado:** Aceito
- **Data:** 2026-09-02
- **Etapa:** 03b — correção de estrutura
- **Achado atendido:** E2 (composição de produção replicada)

## Contexto

A etapa 03 encontrou a fiação de produção remontada à mão fora da produção, em dois pontos
diferentes, com naturezas diferentes.

**A cadeia de estratégias de alocação diária**, montada de forma idêntica em três lugares:
`DynamicStudyPlannerService` (produção), `MetricsCalculator` (benchmark) e `GaEdgeCasesTest` (teste).
O benchmark até documentava a réplica: *"exactly the chain DynamicStudyPlannerService composes in
production"*.

**A composição do `FitnessEvaluator`**, montada em seis lugares fora da produção: três programas de
benchmark e três classes de teste. Em produção, quem monta é o Spring, por injeção de coleção.

O risco é o mesmo nos dois casos e não é estético: mudar a produção deixaria as cópias medindo e
testando algo que o aluno não recebe — **em silêncio, sem nenhuma falha**.

## Decisão

**Os dois casos recebem tratamento diferente, porque a duplicação tem causa diferente.**

### A cadeia de alocação: unificada

Criada `service.scheduler.strategy.AllocationChains`, com dois métodos nomeados —
`production(int maxDailyCognitiveLoad)` e `productionWithoutLoadPruning()`. Produção, benchmark e
teste passaram a chamá-la. **Nenhum outro lugar monta a cadeia:** verificado por busca — as duas
únicas ocorrências de `new ReviewFocusedStrategy(` estão dentro da própria fábrica.

A variante sem podador existia no benchmark por um motivo legítimo, agora documentado no Javadoc do
método: o podador descarta blocos até caber no orçamento, o que faria todo planejador reportar zero
dias de sobrecarga e esconderia a diferença entre eles.

### A composição de fitness: duplicação **intencional**, com guarda

Não unificada, e a razão é concreta: os benchmarks precisam construir variantes com pesos
perturbados. É literalmente o que `WeightSensitivityMain` mede — a estabilidade do plano sob
perturbação de ±20% nos pesos. Unificar removeria a capacidade de variar, que é a função daquele
programa.

O que se acrescenta no lugar é uma **guarda**, em
`DynamicStudyPlannerApplicationTests.aComposicaoDeFitnessFiadaPeloSpringEhACanonica`: o teste fixa
**quais tipos** compõem a fitness que o Spring monta, e não só a soma dos pesos.

A distinção importa. A verificação anterior — soma dos pesos igual a 1,0 — **não pega** o caso que
preocupa: um `FitnessObjective` novo com peso 0,0 mantém a soma em 1,0 e passa despercebido. Fixando
os tipos, acrescentar, remover ou trocar um objetivo falha, e a mensagem de falha lista os seis
arquivos a atualizar no mesmo commit.

## Alternativas consideradas

**Unificar também a fitness, com uma fábrica que aceite pesos.** Descartada: `WeightSensitivityMain`
e `WeightTradeoffMain` variam pesos de formas diferentes, e uma fábrica que aceitasse todas as
variações teria mais parâmetros do que a construção direta — trocaria duplicação por uma abstração
mais difícil de ler que o código duplicado.

**Fazer os benchmarks subirem o contexto do Spring.** Descartada: transformaria programas de medição
de segundos em testes de integração, e `benchmarks/README.md` documenta o isolamento do módulo de
medição como propriedade deliberada.

**Deixar a fitness só com a verificação de soma dos pesos.** Descartada pelo motivo acima: não pega
o objetivo novo de peso zero, que é o modo de falha realista.

## Consequências

**A favor:**

- A cadeia de alocação tem uma definição só. Trocar a ordem dos decoradores em produção muda
  automaticamente o que os benchmarks medem e o que os testes verificam.
- A composição de fitness continua variável onde precisa ser, e passou a ser vigiada onde não deve
  variar.
- A duplicação que resta é **declarada**, com o motivo escrito no Javadoc do teste que a vigia — não
  é mais dívida silenciosa.

**Contra, e aceito:**

- `AllocationChains` é uma classe de fábrica com métodos estáticos, o que dificulta substituí-la por
  dublê. É aceitável porque ela não tem estado nem dependência: é composição pura, e o que se quer
  testar são as estratégias que ela compõe, já cobertas individualmente.
- A guarda da fitness fixa nomes de classe como texto. Renomear um objetivo quebra o teste — o que é
  o comportamento desejado, mas exige atualizar o teste junto, no mesmo commit.

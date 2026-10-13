# ADR-0008 — `metadata.elapsedMillis` continua 0; quem precisa de tempo mede por fora

- **Estado:** Aceito
- **Data:** 2026-10-08
- **Decidido por:** dono do projeto (decisão DT, opção DT-2)
- **Etapa:** EOA-13, Parte A
- **Pedido atendido:** SP-H2 da plataforma (`docs/ai/HANDOFF.md`)

## Contexto

`PlanResponse.metadata.elapsedMillis` é a constante `0` nos três motores (`greedy-baseline`, `ga`,
`ga-timeline`). Cada classe diz por quê no próprio Javadoc: medir o tempo faria duas execuções do
mesmo pedido semeado diferirem, e a reprodutibilidade byte a byte é requisito do experimento
(`GreedyBaselineScheduler`, `GeneticPlanEngine`, `TimelinePlanEngine`).

A plataforma grava o campo e precisa de uma duração. O corpo do contrato v1.0 não pode mudar: os JSON
de referência são byte-idênticos nos dois repositórios.

## Decisão

DT-2: o Core mantém `elapsedMillis = 0`. A plataforma mede a chamada, em coluna nova.

## Alternativas consideradas

DT-1 e DT-3, descartadas pelo dono. O conteúdo delas não está registrado neste repositório.

## Consequências

- `elapsedMillis` fica no contrato como campo reservado: vale sempre `0` e não mede nada.
- Quem precisa de uma duração mede fora do Core. A plataforma mede a chamada.

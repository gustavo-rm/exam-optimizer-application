# ADR-0011 — O que o reparo do AG pode tocar, em estágios

- **Estado:** Aceito
- **Data:** 2026-10-08
- **Decidido por:** dono do projeto (decisão D2)
- **Etapa:** EOA-12

## Contexto

O diagnóstico do EOA-10 (`docs/DIAGNOSTICO_ESCALA_REAL.md` §10) levantou hipóteses de causa que tocam
fitness, seleção ou colocação, e deixou para esta decisão o que o reparo pode alterar.

## Decisão

- **Autorizado, e só depois do portão da Fase 0 do EOA-12:** tratar fitness não finito (`NaN`,
  `+Infinity`, `-Infinity`) como o pior valor possível, num ponto do caminho do `ga-timeline` fora das
  seis classes protegidas de `CLAUDE.md` §4 (`CreepMutation`, `TransferMutation`,
  `WeightedAverageCrossover`, `TournamentSelection`, `Population` e o laço de evolução de
  `GeneticAlgorithm`).
- **Não autorizado:**
  - as seis classes acima;
  - qualquer termo de fitness ou normalização, inclusive `DailyLoadBudgetObjective`;
  - o orçamento de busca (hoje 60 gerações × população 40), a inicialização e os operadores;
  - o alocador e a colocação (`AvailabilityAllocator`, `SessionPlacement`, `TimelineRepairer.place`,
    `GreedyBaselineScheduler`);
  - qualquer coisa no caminho do `ga`.

## Alternativas consideradas

Dar `F` finito ao plano vazio dentro de um termo de fitness, descartada pelo dono: muda a função
objetivo que a tese descreve, e isso é decisão científica, não correção de bug.

## Consequências

- O que fica fora do autorizado exige nova decisão do dono antes de qualquer mudança.

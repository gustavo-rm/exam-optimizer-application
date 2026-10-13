# ADR-0010 — O que encerra o reparo do AG: invariantes como portão, qualidade só medida

- **Estado:** Aceito
- **Data:** 2026-10-08
- **Decidido por:** dono do projeto (decisão D1)
- **Etapa:** EOA-12

## Contexto

O diagnóstico do EOA-10 (`docs/DIAGNOSTICO_ESCALA_REAL.md`) observou que, no catálogo real, o
`ga-timeline` recusa 30 de 30 execuções e o `ga` recusa 23 de 30, ambos com `plan-would-be-empty`.
Faltava decidir o que prova que o reparo funcionou.

## Decisão

- **Portão (vai para o `verify`):** para o `ga-timeline`, nas 3 instâncias de
  `src/test/resources/instances/` e nas 10 sementes (1 a 10), 30 de 30 respostas são não vazias e
  passam por `PlanOutputInvariants`.
- **Hipótese (medida e relatada, nunca portão):** qualidade comparativa com o guloso. Estatística
  fixada antes de ver o resultado: por instância, a mediana das diferenças pareadas por semente
  (`ga-timeline` menos guloso) sobre as 10 sementes, com todos os planos pontuados pelo mesmo
  avaliador a posteriori do EOA-10, nas métricas que ele já define. Sem teste de significância e sem a
  palavra "melhor": 10 sementes que o guloso nem usa não são uma amostra para isso.

## Alternativas consideradas

Qualidade comparativa como portão, descartada pelo dono. O motivo além do que está na Decisão não está
registrado neste repositório.

## Consequências

- Um teste no `verify` cobra as 30 respostas do `ga-timeline`.
- A comparação com o guloso é relatada em documento, por instância, e não falha o build.

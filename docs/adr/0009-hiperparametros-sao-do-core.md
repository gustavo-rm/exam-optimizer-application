# ADR-0009 — Os hiperparâmetros do AG são do Core

- **Estado:** Aceito
- **Data:** 2026-10-08
- **Decidido por:** dono do projeto (decisão D4, Variante 1)
- **Etapa:** EOA-13, Parte B

## Contexto

`PlanRequest.algorithmParams` é um `Map<String, Object>` aberto no contrato v1.0. A plataforma envia
nele `generations`, `population-size` e `mutation-rate`. O Core aplica só quatro chaves (`engine`,
`importance`, `precedence`, `provenance`). As outras não têm efeito, e isso foi confirmado por
execução no item 0 do EOA-13 (`HiperparametrosDoCoreHttpTest`). O orçamento da busca vem de
`plan.engine.ga.*`, e as taxas são constantes do código.

Faltava decidir quem é dono desses valores: o Core ou quem chama.

## Decisão

Variante 1: o Core é dono dos hiperparâmetros do AG.

## Alternativas consideradas

Variante 2, descartada pelo dono. O conteúdo dela não está registrado neste repositório.

## Consequências

- `algorithmParams` continua aceito, como o contrato prevê. `generations`, `population-size` e
  `mutation-rate` continuam sem efeito no plano.
- O que a EOA-13 fez para aplicar esta decisão (log das chaves ignoradas, parâmetros efetivos,
  `fitness.build`) está descrito no README, seção "Choosing the engine per request", e não faz parte
  da decisão.

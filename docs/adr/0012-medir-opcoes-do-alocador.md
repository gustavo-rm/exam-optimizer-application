# ADR-0012 — Medir duas opções do alocador, sem adotar nenhuma

- **Estado:** Aceito
- **Data:** 2026-10-22
- **Decidido por:** dono do projeto (decisão D2b)
- **Etapa:** EOA-12b

## Contexto

O EOA-12 (`docs/REPARO_ESCALA_REAL.md` §7 (a)) deixou em aberto o que fazer quando o bloco de um
tópico não cabe: hoje a colocação para no primeiro tópico que não cabe, e o plano parcial é um prefixo
da ordem de estudo. A seção listou opções, sem escolha.

## Decisão

- **Autorizado:** MEDIR duas opções de `docs/REPARO_ESCALA_REAL.md` §7 (a), sem adotar nenhuma.
  - **P — "pular e seguir":** quando o bloco de um tópico não cabe, o tópico é pulado, os seus
    dependentes `HARD` (transitivos) também, e a colocação continua com os tópicos seguintes da mesma
    ordem.
  - **D — "declarar de antemão":** classificar, para cada tópico que fica de fora do plano, o porquê.
    É análise e não muda a colocação.
- **Não autorizado:**
  - adotar P ou D;
  - qualquer edição em `src/main`, inclusive "só um gancho", mudança de visibilidade ou interface
    extraída;
  - mudar o motor padrão (`plan.engine.default=greedy-baseline`) ou o que o guloso devolve;
  - as seis classes protegidas de `CLAUDE.md` §4;
  - termos de fitness, orçamento de busca, operadores;
  - a ordem de estudo do `ga`;
  - retirar tópicos da entrada do `ga` (opções b3 e b4 de `docs/REPARO_ESCALA_REAL.md` §7);
  - chave nova em `fitness` ou campo novo no contrato.

  Tudo isso fica como proposta para o dono, depois dos números.

## Alternativas consideradas

Nenhuma registrada neste repositório. As opções de §7 (a) continuam abertas.

## Consequências

- A medição vive em `src/test` (e, se usado, `benchmarks/java`) e em `docs/`.
- Adotar qualquer opção exige decisão nova do dono.

# ADR-0001 — Contratos de cálculo de domínio vivem em `domain`

- **Estado:** Aceito
- **Data:** 2026-09-02
- **Etapa:** 03b — correção de estrutura
- **Achados atendidos:** E4 (ciclo `ga` ↔ `service`), E5 (interface no módulo errado)

## Contexto

O diagnóstico da etapa 03 encontrou um ciclo de dependência entre dois módulos de topo:

```
ga.fitness.constraint.MandatoryReviewConstraint      ──▶ service.calculation.retention.RetentionAlgorithm
ga.fitness.penalty.DropoutRiskPenalty                ──▶ service.calculation.engagement.DropoutRiskPredictor
ga.fitness.penalty.FatigueAndSustainabilityPenalty   ──▶ service.calculation.fatigue.FatigueAndEnergyModel
ga.tactical.repair.SpacedRepetitionRepairer          ──▶ service.calculation.retention.RetentionAlgorithm
service.StudyOptimizerService                        ──▶ ga.*
```

Módulos em ciclo não podem ser compilados, testados nem extraídos separadamente. E o ciclo não
produz erro de compilação nem falha de teste: só aparece quando alguém tenta extrair um módulo.

As quatro arestas tinham naturezas diferentes. `RetentionAlgorithm` **já era uma interface** — o
problema era só onde ela morava: no pacote de quem a **implementa**, e não no de quem a **consome**.
As outras duas eram dependências de **classe concreta**, sem abstração nenhuma no caminho.

## Decisão

**Todo contrato de cálculo cuja assinatura use apenas tipos de domínio pertence a `domain`. A
implementação permanece em `service`.**

Aplicado a três contratos:

| Contrato | De | Para |
|---|---|---|
| `RetentionAlgorithm` (já existia) | `service.calculation.retention` | `domain.retention` |
| `FatigueAlgorithm` (criado) | — | `domain.fatigue` |
| `DropoutRiskAlgorithm` (criado) | — | `domain.engagement` |

As implementações — `HybridRetentionEngine`, `FatigueAndEnergyModel`, `DropoutRiskPredictor` —
continuam em `service` e passaram a declarar `implements`.

**Convenção de nome adotada junto:** contratos de cálculo de domínio usam o sufixo `...Algorithm`,
seguindo o precedente do `RetentionAlgorithm`, que já existia. É uma resposta parcial ao achado E9
(seis sufixos para o mesmo papel): estabelece o sufixo para **a categoria contrato**, sem renomear as
implementações existentes.

## Alternativas consideradas

**Mover as implementações para `ga`.** Descartada: `HybridRetentionEngine` implementa SM-2 adaptado e
não tem nada de algoritmo genético. Apenas inverteria o ciclo de lado.

**Criar um módulo `shared` ou `common` para as interfaces.** Descartada: um pacote definido pela
ausência de tema atrai tudo o que não tem lugar óbvio, e em pouco tempo vira o novo centro do ciclo.
`domain` já é, por definição, o que todos os módulos podem conhecer.

**Deixar o ciclo e documentá-lo.** Descartada: era a alternativa mais barata hoje — três das quatro
arestas apontam para código que nenhuma requisição alcança — mas o custo de desfazê-la só cresce, e
o ciclo é invisível sem verificação automática.

**Inverter só o `RetentionAlgorithm`.** Descartada: fecharia duas arestas e deixaria duas. Meio ciclo
é ciclo.

## Consequências

**A favor:**

- O grafo de módulos virou acíclico: `api → domain, usecase`; `ga → domain, util`;
  `service → domain, ga, usecase, util`; `usecase → domain`.
- `ga` deixou de compilar contra `service`. **Zero imports** de `ga` para `service`.
- As penalidades passaram a depender de interface em vez de classe concreta, o que as torna
  substituíveis por dublê em teste sem o contêiner do Spring.
- `domain` continua puro: os três contratos não importam framework.

**Contra, e aceito:**

- Duas interfaces novas para uma implementação cada. É indireção com um implementador só — custo
  real, pago para desfazer o ciclo. Se algum dia a implementação for única e definitiva, a decisão
  pode ser revisitada; até lá o benefício é a fronteira.
- `domain` ganhou um pacote (`domain.fatigue`) e cresceu de 22 para 24 classes.

**Travado por teste:** `arquitetura/ModuleBoundaryTest` reprova se um ciclo entre módulos voltar, se
`domain` passar a depender de outro módulo, ou se uma classe de domínio importar framework. Os três
foram verificados por sabotagem temporária.

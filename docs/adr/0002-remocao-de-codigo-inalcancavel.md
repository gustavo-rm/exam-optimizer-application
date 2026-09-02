# ADR-0002 — Remover código inalcançável sem destino registrado; manter a camada tática

- **Estado:** Aceito
- **Data:** 2026-09-02
- **Etapa:** 03b — correção de estrutura
- **Achado atendido:** E6 (~630 instruções inalcançáveis)

## Contexto

A etapa 03 cruzou três sinais — referência textual, anotação Spring e cobertura de testes — para
separar **código morto** de **código apenas sem teste**. A distinção importa: escrever teste para
código que deveria sair é desperdício, e remover código que só falta testar é regressão.

O cruzamento produziu duas categorias:

**Categoria A — nada instancia, nada chama, 0% de cobertura:** `DayBoundaryCrossover` (123
instruções), `RandomizedInterleavedStrategy` (82), `CriticalFirstStrategy` (66),
`MethodologyMutation` (61), `ExamType` (15), `OptimizationResponse` (9).

**Categoria B — o Spring instancia, mas nenhuma requisição alcança:** `HybridHeuristicScheduler`
(100% de cobertura e zero consumidores), `SpacedRepetitionRepairer`, `FatigueAndEnergyModel`,
`DropoutRiskPenalty`, `MandatoryReviewConstraint`.

Parte desse código tem **decisão registrada anterior**, em `docs/revisao-ag/README.md`:

- **G5** — a camada tática: *"Agora com destino explícito: é o ponto de partida da opção (i) se ela
  for reaberta"*. É uma escolha deliberada, tomada por uma revisão de engenharia anterior, e a
  existência do andaime foi **insumo de uma estimativa de esforço** em `06-decisao-ausubel.md` §2.3.
- **G11** — `CriticalFirstStrategy` e `RandomizedInterleavedStrategy`: marcadas ABERTO, com a nota de
  que *"custo de manter é próximo de zero"*. Isso é uma observação, não uma decisão de manter.

## Decisão

**Remover o código inalcançável que não tem destino registrado. Manter a camada tática, porque tem.**

**Removido nesta etapa** — quatro classes, todas com zero referências confirmadas em `src/main`,
`src/test` e `benchmarks/`:

| Classe | Instruções | Justificativa |
|---|---:|---|
| `domain.exam.ExamType` | 15 | Enum sem nenhuma referência em lugar nenhum |
| `api.dto.OptimizationResponse` | 9 | Usada só pelo método `@Deprecated` `OptimizationResultMapper.toResponse`, removido junto |
| `service.scheduler.strategy.CriticalFirstStrategy` | 66 | G11, sem destino registrado |
| `service.scheduler.strategy.RandomizedInterleavedStrategy` | 82 | G11, sem destino registrado |

O método `OptimizationResultMapper.toResponse` foi removido com o DTO. O import que o sustentava
carregava o comentário `// This can be removed now`, do próprio autor.

**Mantido, com justificativa:** a camada tática — `ga.tactical.*`, `service.scheduler.tactical.*`,
`domain.tactical.*` e as penalidades e restrição que só agem sobre `TacticalStudyPlan`.

## Alternativas consideradas

**Remover tudo, inclusive a camada tática.** Descartada, e não por timidez. O argumento a favor é
forte: "podemos precisar depois" é a razão mais fraca para manter código morto, porque o histórico do
git já resolve isso. O argumento contra prevaleceu por dois motivos concretos: **(a)** existe decisão
registrada em contrário, tomada por uma revisão anterior com contexto que este diagnóstico não tem, e
sobrescrevê-la silenciosamente numa etapa de estrutura seria errado; **(b)** a remoção também
apagaria `HybridHeuristicSchedulerTest`, que tem 100% de cobertura da classe — trabalho de teste
real, perdido junto.

**Manter tudo e apenas documentar.** Descartada para as quatro removidas: nenhuma tem destino, e todo
leitor futuro paga o custo de entender código que não roda.

**Marcar a camada tática com `@Deprecated`.** Descartada: `@Deprecated` sinaliza "vai sair", e a
decisão registrada em G5 é a oposta — "é o ponto de partida se a opção (i) for reaberta". A anotação
diria ao leitor o contrário do que está decidido.

## Consequências

**A favor:**

- 172 instruções a menos de código que nada alcança.
- G11 fica **fechada**: as duas estratégias sem consumidor deixaram de existir.
- `service.scheduler.strategy` passou de 6 para 4 classes, todas na cadeia de produção.

**Contra, e aceito:**

- ~460 instruções da camada tática continuam no repositório, instanciadas pelo Spring na subida da
  aplicação, custando tempo de inicialização e espaço no grafo de beans. Todo leitor continua pagando
  o custo de entendê-las.
- A decisão de G5 **não é permanente e precisa de gatilho.** Fica registrado: se a opção (i) da
  `06-decisao-ausubel.md` for formalmente descartada, ou se passar um ano sem que a camada tática
  seja retomada, este ADR deve ser substituído por outro que autorize a remoção. Sem gatilho, "manter
  por enquanto" vira "manter para sempre" por inércia.

**Nota para quem decidir depois:** remover a camada tática é uma operação de um comando
(`git rm -r` nos três pacotes, mais as duas penalidades e a restrição), e o `ModuleBoundaryTest`
continuaria passando. O que trava a decisão não é dificuldade técnica — é a pergunta de produto sobre
retomar o ITS.

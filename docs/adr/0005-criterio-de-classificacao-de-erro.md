# ADR-0005 — O que separa `IllegalArgumentException` (400) de `DomainException` (422)

- **Estado:** Aceito
- **Data:** 2026-09-03
- **Etapa:** 03d — correção dos achados E3 e E7
- **Achados atendidos:** E3 (`StudyPlanFactory` sinaliza regra de negócio como erro de argumento),
  E7 (`GlobalExceptionHandler` com 447 linhas e 16 tratadores)

## Contexto

O sistema tem duas famílias de exceção que chegam à borda HTTP e viram códigos de erro diferentes:

| Exceção | Status | Significado pretendido |
|---|---|---|
| `IllegalArgumentException` | **400** `Bad Request` | A requisição não foi compreendida |
| `DomainException` | **422** `Unprocessable Content` | Foi compreendida, mas não pode ser atendida |

A distinção vem da RFC 9110 e é a que o cliente percebe na prática: um **400** se corrige mudando o
*formato* do envio; um **422** só se corrige mudando o *pedido*.

Não havia critério escrito para decidir em qual família cada verificação entrava, e o resultado foi
uma classificação por hábito. `StudyPlanFactory` lançava `IllegalArgumentException` em **13 pontos**,
entre eles este:

```java
if (totalMinimumDays > totalDays) {
    throw new IllegalArgumentException("Total minimum study days required (" + totalMinimumDays +
            ") exceeds total available days (" + totalDays + ").");
}
```

Isso não é argumento malformado. O JSON estava perfeito, os tipos estavam certos, cada campo passou
por `@Min` e `@NotNull`. O que aconteceu é que **as disciplinas do edital exigem, somadas, mais dias
do que existem até a prova** — uma conclusão do domínio sobre dados válidos. O cliente recebia 400 e
a mensagem "a requisição está malformada", quando a resposta correta era "entendi seu pedido, e ele
é inviável; adie a prova, remova disciplinas ou libere mais tempo".

O efeito colateral era medível: **o tratador de 422 existia e era inalcançável**. A única origem de
`DomainException` em produção era `Exam.calculateBaseImportance`, num ramo que a validação `@Min(1)`
de `SubjectDto.questionCount` tornava impossível de atingir. Havia código de tradução para um status
que nenhuma requisição conseguia produzir, e o teste que o cobria dizia isso em Javadoc —
`ErrorAdviceContractTest` (então `GlobalExceptionHandlerTest`) listava o 422 entre os casos "fora do
alcance da porta HTTP". O `README.md` §*Error Handling* enumera `400`, `408` e `429`, e nunca chegou
a mencionar o 422: o código prometia mais do que a documentação, e entregava menos que os dois.

**Esta não é uma constatação nova.** A revisão de robustez de maio de 2026 já a havia registrado como
pendência aberta, em `docs/revisao-ag/04-robustez.md`:

> **P6** — Inviabilidade sai como 400 (`IllegalArgumentException`), não 422. […] Semanticamente é
> violação de regra de negócio: `DomainException` → 422 é mais correto. Baixo impacto, mas é contrato.

O diagnóstico de estrutura da etapa 03 chegou ao mesmo ponto por outro caminho (achado E3), sem
conhecer o registro anterior. Duas análises independentes convergindo é o que justifica fechar a
pendência agora em vez de mantê-la aberta por mais um ciclo. O que faltava não era o diagnóstico:
era o **critério** que dissesse quais das treze verificações mudam e quais não — e é isso que este
ADR fixa.

## Decisão

**Adotar o critério abaixo, e reclassificar `StudyPlanFactory` segundo ele.**

> Pergunte: *quem errou?*
>
> - Se **quem chamou** montou a chamada de forma que nenhum cliente legítimo montaria — um `null`
>   onde o contrato exige valor, um número negativo onde só faz sentido positivo, uma coleção que o
>   próprio método acabou de exigir não vazia —, é **defeito de programação**:
>   `IllegalArgumentException` → **400**. O destinatário real da mensagem é um desenvolvedor.
> - Se **ninguém errou** — a chamada é válida, os tipos estão certos, e é o *domínio* que conclui
>   que o pedido não pode ser atendido —, é **regra de negócio**: `DomainException` → **422**. O
>   destinatário da mensagem é o usuário, e ela precisa dizer o que ele pode mudar.

O teste prático: *o cliente consegue corrigir mudando só o formato do envio?* Se sim, 400. Se ele
precisa mudar **o que está pedindo**, 422.

Sob esse critério, **2 das 13 verificações** de `StudyPlanFactory` mudaram de família:

| Verificação | Antes | Depois | Por quê |
|---|---|---|---|
| Edital sem nenhuma disciplina | `IllegalArgumentException` | `DomainException` | Um edital vazio é um pedido compreensível e inviável, não um argumento malformado |
| Dias mínimos exigidos > dias disponíveis | `IllegalArgumentException` | `DomainException` | Conclusão do domínio sobre dados válidos; só se corrige mudando o pedido |
| Dias disponíveis negativos | `IllegalArgumentException` | *(inalterada)* | Nenhum cliente legítimo envia dias negativos — é defeito de quem chama |

As outras dez continuam como estão: são `null` em campos obrigatórios e invariantes internas, todas
defeito de programação.

## Consequências

**A favor:**

- A pendência **P6** de `docs/revisao-ag/04-robustez.md`, aberta desde maio de 2026, está fechada.
- O **422 passou a ser alcançável** pela API, e tem prova de ponta a ponta em
  `security/BusinessRuleStatusTest`: um edital com 25 disciplinas cujo piso de dias mínimos supera
  365 devolve 422. O mesmo teste guarda a contraprova — erro de sintaxe continua devolvendo 400 —,
  porque uma reclassificação larga demais seria tão errada quanto a estreita.
- A mensagem que o cliente recebe passa a ser acionável no caso que mais importa: a mensagem do 422
  carrega os dois números (dias exigidos contra dias disponíveis), então ele sabe **quanto** falta.
- O critério dá endereço ao tratamento de erro. O achado **E7** dividiu
  `GlobalExceptionHandler` em três `@RestControllerAdvice` — `RequestErrorAdvice`,
  `BusinessRuleErrorAdvice` e `InfrastructureErrorAdvice` — e essa divisão só é sustentável se
  existir uma regra para dizer em qual deles um erro novo entra. É esta.

**Contra, e aceito:**

- **É uma mudança de contrato público.** Um cliente que trate `400` como "erro do usuário" e `422`
  como "erro de sistema" passa a se comportar diferente diante de um edital inviável. Aceita-se
  porque o comportamento anterior estava errado segundo a RFC que o próprio `README.md` invoca, e
  porque a API ainda não tem consumidor externo em produção. Se tivesse, isto exigiria versionamento.
- **O critério tem zona cinzenta.** "Nenhum cliente legítimo faria isso" é um julgamento, não um
  teste mecânico. Casos duvidosos devem cair em **422**: informar demais é preferível a acusar o
  cliente de um erro que ele não cometeu.
- **A validação da borda continua sendo a primeira linha.** Este critério vale para o que escapa dela.
  Onde uma regra puder ser expressa como `@Min`/`@NotNull` no DTO, ela deve continuar lá — a
  mensagem chega mais cedo e mais barata, e nem chega ao domínio.

## Alternativas consideradas

**Criar uma exceção por status HTTP no domínio** (`UnprocessableException`, `BadRequestException`).
Descartada: acopla o domínio ao protocolo de transporte. Hoje o domínio não sabe que existe HTTP, e
essa ignorância é o que permite testá-lo sem contexto Spring e reutilizá-lo por outra borda. A
tradução para status acontece uma vez só, em `BusinessRuleErrorAdvice`.

**Deixar tudo como estava e só corrigir o `README.md`**, admitindo que o 422 não existe. Descartada
porque o problema não era a documentação: era o cliente receber "sua requisição está malformada"
diante de uma requisição perfeitamente formada. Documentar o defeito não o remove — e a revisão de
maio já o havia documentado, em P6, sem que isso mudasse o que o cliente recebe.

**Reclassificar as 13 verificações de uma vez para `DomainException`.** Descartada: apagaria a
distinção em vez de aplicá-la. Um `null` em campo obrigatório é defeito de programação e merece 400
— devolver 422 para isso diria ao cliente que ele precisa mudar o pedido, quando na verdade há um
erro no código de quem chamou.

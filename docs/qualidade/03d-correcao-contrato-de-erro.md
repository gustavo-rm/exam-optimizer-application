# 03d — Correção dos achados E3 e E7: o contrato de erro

- **Data:** 2026-09-03
- **Branch:** `release/v4.0`
- **Achados atendidos:** **E3** (nível 1) e **E7** (nível 3) de
  [`03-diagnostico-estrutura.md`](./03-diagnostico-estrutura.md)
- **Pendência de terceiros fechada:** **P6** de `docs/revisao-ag/04-robustez.md`
- **ADR produzido:** [ADR-0005](../adr/0005-criterio-de-classificacao-de-erro.md)

---

## Resumo em uma tela

| | Antes | Depois |
|---|---|---|
| Classes de tratamento de erro | 1 (`GlobalExceptionHandler`) | 4 (3 *advices* + 1 montador de corpo) |
| Maior classe do pacote | 447 linhas / 263 de código / 16 tratadores | 261 linhas / 145 de código / 10 tratadores |
| Montagem do corpo RFC 7807 | repetida 16× | 1 lugar (`ProblemDetails`) |
| Verificações de `StudyPlanFactory` que são regra de negócio | 0 de 13 (todas `IllegalArgumentException`) | 2 de 13 (`DomainException`) |
| `422` alcançável por requisição HTTP | **não** | **sim**, com prova de ponta a ponta |
| Critério escrito para classificar 400 × 422 | nenhum | ADR-0005 |
| Testes | 157 | **164** (+7) |
| Cobertura de `api/exception` | 84,3 % instr. / 43,8 % ramos | **96,9 % / 50,0 %** |
| Cobertura total (BUNDLE) | 81,79 % instr. / 60,86 % ramos | **82,64 % / 61,05 %** |
| Suíte | verde | **verde** (`mvn clean verify`, 164/164) |

**Ambos os achados foram fechados por completo.** Nada de E3 ou E7 ficou pendente.

---

## 1. Achado E3 — o `422` era decorativo

### O que o diagnóstico dizia

> **E3** — `DomainException` usada 1 vez contra 13 `IllegalArgumentException`. Torna o `422`
> inalcançável — metade do contrato de erro é decorativa.

### O defeito, em concreto

`StudyPlanFactory` recusava um pedido **inviável** com a exceção que a borda HTTP traduz para
`400 Bad Request`:

```java
if (totalMinimumDays > totalDays) {
    throw new IllegalArgumentException("Total minimum study days required (" + totalMinimumDays +
            ") exceeds total available days (" + totalDays + ").");
}
```

Um **`400`** diz ao cliente *"não entendi sua requisição"*. Mas a requisição estava perfeita: o JSON
era válido, os tipos estavam certos, cada campo passou por `@Min` e `@NotNull`. O que aconteceu foi
outra coisa — **as disciplinas do edital exigem, somadas, mais dias do que existem até a data da
prova**. Isso é uma conclusão do domínio sobre dados válidos, e o código HTTP para isso é **`422`**
(`Unprocessable Content`), que a RFC 9110 define exatamente como *"entendi, mas não posso cumprir"*.

A diferença não é acadêmica. Um `400` se corrige mudando **como** você envia; um `422` só se corrige
mudando **o que** você está pedindo — adiar a prova, remover disciplinas, liberar mais tempo. O
cliente estava recebendo a instrução errada.

### Isto já estava registrado — por outra revisão, há meses

A revisão de robustez de maio de 2026 tinha chegado à mesma conclusão, e a deixou aberta:

> **P6** — Inviabilidade sai como 400 (`IllegalArgumentException`), não 422. […] Semanticamente é
> violação de regra de negócio: `DomainException` → 422 é mais correto. Baixo impacto, mas é
> contrato.
> — `docs/revisao-ag/04-robustez.md`

O diagnóstico da etapa 03 chegou ao mesmo ponto por outro caminho, sem conhecer esse registro. **Duas
análises independentes convergindo** é o que justifica fechar agora em vez de arrastar por mais um
ciclo. O que faltava nunca foi o diagnóstico — era o **critério** que dissesse *quais* das treze
verificações mudam e quais não.

### O critério adotado (ADR-0005)

> Pergunte: *quem errou?*
>
> - **Quem chamou** montou algo que nenhum cliente legítimo montaria (um `null` onde o contrato
>   exige valor, um número negativo onde só faz sentido positivo)? É defeito de programação:
>   `IllegalArgumentException` → **400**.
> - **Ninguém errou** — a chamada é válida e é o *domínio* que conclui que o pedido não pode ser
>   atendido? É regra de negócio: `DomainException` → **422**.

Teste prático: *o cliente corrige mudando só o formato do envio?* Sim → 400. Precisa mudar o pedido
→ 422.

### O que mudou, verificação por verificação

Das **13** verificações de `StudyPlanFactory`, **2 mudaram de família**:

| Verificação | Antes | Depois | Por quê |
|---|---|---|---|
| Edital sem nenhuma disciplina | `IllegalArgumentException` | **`DomainException`** | Um edital vazio é um pedido compreensível e inviável |
| Dias mínimos exigidos > dias disponíveis | `IllegalArgumentException` | **`DomainException`** | Conclusão do domínio sobre dados válidos |
| Dias disponíveis negativos | `IllegalArgumentException` | *inalterada* | Nenhum cliente legítimo envia dias negativos |
| *(outras 10: `null` em campo obrigatório, invariantes internas)* | `IllegalArgumentException` | *inalteradas* | Defeito de programação |

**Onze das treze não mudaram.** Isso é deliberado: reclassificar tudo apagaria a distinção em vez de
aplicá-la. Devolver `422` para um `null` em campo obrigatório diria ao cliente que ele precisa mudar
o pedido, quando na verdade há um defeito no código de quem chamou.

O código guarda o porquê no ponto da decisão:

```java
if (totalMinimumDays > totalDays) {
    // O caso central do achado E3. ... E o que a RFC 9110 chama de 422 ...
    throw new DomainException("Total minimum study days required (" + totalMinimumDays +
            ") exceeds total available days (" + totalDays + ").");
}
if (totalDays < 0) {
    // Continua IllegalArgumentException de proposito: ... defeito de quem chama.
    throw new IllegalArgumentException("Total available days cannot be negative (received " + totalDays + ").");
}
```

### A prova de que o `422` agora existe de verdade

`security/BusinessRuleStatusTest` monta um edital com **25 disciplinas** cujo piso de dias mínimos
supera os 365 dias disponíveis, envia pela porta HTTP real e verifica o status:

- **Prova:** o pedido inviável devolve **422**, com a mensagem carregando os dois números — dias
  exigidos contra dias disponíveis —, de modo que o cliente sabe *quanto* falta.
- **Contraprova, no mesmo arquivo:** um corpo com erro de sintaxe continua devolvendo **400**.

A contraprova é a parte que importa. Uma reclassificação larga demais — tudo virando 422 — passaria
no primeiro teste e estaria tão errada quanto o estado anterior. O par de testes fixa os dois lados
da fronteira, não um só.

### O que quebrou, e por quê isso era esperado

Dois testes de `GaEdgeCasesTest` falharam imediatamente após a mudança:
`budgetBelowMinimumDaysFloorFails` e `examWithNoSubjectsFailsClearly`. Eles afirmavam
`IllegalArgumentException` — isto é, **documentavam a classificação antiga**. Foram atualizados para
`DomainException`, com comentário apontando para o ADR-0005 e para este relatório.

Falha esperada, não regressão: era exatamente o sinal de que a mudança alcançou o comportamento que
se pretendia mudar. Se eles tivessem continuado verdes, a reclassificação não teria efeito nenhum.

---

## 2. Achado E7 — uma classe, três assuntos

### O que o diagnóstico dizia

> **E7** — `GlobalExceptionHandler` com **447 linhas e 16 tratadores** — a maior classe do
> repositório; era 214 linhas e 9 tratadores antes da etapa 02b.

E, no corpo do diagnóstico:

> A decisão continua correta […], mas o custo estrutural é real: uma classe dobrou de tamanho e
> concentra decisões de três naturezas. Um recorte plausível é separar por natureza em três
> `@RestControllerAdvice` ordenados.

Vale registrar a honestidade da origem: **os sete tratadores extras foram acrescentados por mim, na
etapa 02b**, para corrigir o achado de segurança S8. A correção estava certa e o custo estrutural
também era real — foi por isso que ele entrou no diagnóstico como achado, e não como nota de rodapé.

### O recorte: por natureza da causa, não por código HTTP

| Classe | O que trata | Status típicos | `@Order` |
|---|---|---|---|
| `RequestErrorAdvice` | A requisição em si não é aceitável: JSON malformado, tipo errado, rota inexistente, verbo não suportado, validação reprovada | `400`, `404`, `405`, `415` | `HIGHEST_PRECEDENCE` |
| `BusinessRuleErrorAdvice` | A requisição está correta, mas o domínio não pode atendê-la | `422` | `HIGHEST_PRECEDENCE + 1` |
| `InfrastructureErrorAdvice` | Segurança, limite de uso, prazo e a rede de segurança do `500` | `401`, `403`, `408`, `429`, `500` | `LOWEST_PRECEDENCE` |

O corte é pela **causa**, não pelo número. Repare que `RequestErrorAdvice` e `BusinessRuleErrorAdvice`
devolvem ambos códigos `4xx` e ainda assim estão separados: o que os distingue é quem precisa agir e
o que essa pessoa precisa fazer. Cortar por faixa de status (`4xx` numa classe, `5xx` noutra) juntaria
de novo exatamente o que precisava ser separado.

### `ProblemDetails`: o formato do erro ganhou dono

Cada um dos 16 tratadores repetia as mesmas cinco linhas:

```java
ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "...");
problemDetail.setTitle("Bad Request");
problemDetail.setType(URI.create("https://api.dynamicstudyplanner.com/errors/validation-failed"));
problemDetail.setInstance(URI.create(request.getRequestURI()));
problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
```

O volume era o sintoma; o problema era o formato **não ter dono**. Nada garantia que um tratador novo
carimbasse o mesmo conjunto de campos — esquecer o `timestamp` ou o `instance` compilava e passava
despercebido. Agora há um lugar só (`ProblemDetails.of` e `ProblemDetails.withInvalidParams`), e
acrescentar um campo ao corpo de erro é **uma** edição em vez de dezesseis.

### A ordem entre *advices* é comportamento, e agora está travada por teste

Este é o ponto sutil da divisão, e o que mais merecia um teste.

`InfrastructureErrorAdvice` declara `@ExceptionHandler(Exception.class)` — um ***catch-all***, que
casa com **qualquer** exceção. Dentro de uma mesma classe isso seria inofensivo: o Spring escolhe o
tratador de tipo mais específico. **Entre classes diferentes o critério é outro** — os *advices* são
percorridos na ordem de `@Order`, e vence o primeiro que tenha algum tratador aplicável, específico
ou não.

Se a infraestrutura passar à frente, **todo erro de cliente vira `500`** — que é precisamente o
defeito S8 que a etapa 02b corrigiu. E a regressão seria silenciosa: apagar uma anotação `@Order`
compila, sobe a aplicação, e só aparece como status errado em produção.

`api/exception/AdviceOrderTest` trava isso com três verificações: a ordem relativa (pelo próprio
`AnnotationAwareOrderComparator` do Spring, não pelos números — que são arbitrários), a presença das
anotações, e o fato de que **só** a infraestrutura declara um catch-all.

#### Verificação em dois sentidos

Um teste que nunca falhou não provou nada. A ordem foi **invertida de propósito**
(`InfrastructureErrorAdvice` → `HIGHEST_PRECEDENCE`, `RequestErrorAdvice` → `LOWEST_PRECEDENCE`) e a
suíte executada:

```
[ERROR] Tests run: 3, Failures: 1 — AdviceOrderTest
  - element at index 0: expected "RequestErrorAdvice" but was "InfrastructureErrorAdvice"
[ERROR] Tests run: 6, Failures: 4 — ApiErrorContractTest
```

O `AdviceOrderTest` pegou a **causa** e disse o nome dela; o `ApiErrorContractTest` pegou o
**sintoma** — quatro erros de cliente devolvendo `500`, com pilha completa em nível `ERROR` no log,
exatamente o quadro do S8. A ordem correta foi restaurada e a suíte voltou a ficar verde.

> **Nota metodológica.** A primeira tentativa de sabotagem foi um **falso negativo**: usei
> `@Order(Ordered.HIGHEST_PRECEDENCE - 1)`, e como `HIGHEST_PRECEDENCE` é `Integer.MIN_VALUE`, a
> subtração **transborda** para `Integer.MAX_VALUE` — ou seja, para `LOWEST_PRECEDENCE`. A sabotagem
> não sabotou nada, e tudo passou. Fica registrado porque é o tipo de engano que faria alguém
> concluir "o teste não funciona" quando o problema estava na sabotagem.

---

## 3. O que a divisão revelou: duas lacunas que estavam escondidas

Efeito colateral útil de quebrar a classe de 447 linhas: com os tratadores em unidades menores, o
relatório de cobertura passou a ser legível por classe, e **dois tratadores apareceram com 0 %**:

| Tratador | Cobertura antes de 03d | Por que ninguém percebia |
|---|---|---|
| `handleMissingParameter` | 0 % | Diluído numa classe de 447 linhas cuja média agregada era 83,8 % — número confortável o bastante para ninguém abrir |
| `handleTypeMismatch` | 0 % | Idem |

Os dois zeros foram **medidos no estado anterior**, não inferidos: o relatório JaCoCo gerado sobre o
commit `0deb0c7`, antes de qualquer mudança desta etapa, marca ambos os métodos de
`GlobalExceptionHandler` com 0 % de instruções cobertas.

Ambos tratam parâmetro de **rota ou consulta**, e o único endpoint da API recebe apenas corpo — ou
seja, são inalcançáveis por requisição HTTP, mesma categoria dos casos que
`ErrorAdviceContractTest` já cobria por teste direto. Dois testes foram acrescentados lá, um deles
verificando também que o **valor recusado não volta na resposta** (regra do achado S2).

Resultado: `api/exception` foi de **84,3 % para 96,9 %** de instruções cobertas, e de **43,8 % para
50,0 %** de ramos.

---

## 4. Arquivos tocados

### Produção

| Arquivo | O quê |
|---|---|
| `api/exception/GlobalExceptionHandler.java` | **removido** (447 linhas) |
| `api/exception/RequestErrorAdvice.java` | **novo** — 10 tratadores de erro de cliente |
| `api/exception/BusinessRuleErrorAdvice.java` | **novo** — o `422` |
| `api/exception/InfrastructureErrorAdvice.java` | **novo** — segurança, prazo, catch-all |
| `api/exception/ProblemDetails.java` | **novo** — montagem única do corpo RFC 7807 |
| `ga/factory/StudyPlanFactory.java` | 2 de 13 verificações reclassificadas para `DomainException` |
| `api/controller/RateLimitingFilter.java` | comentário apontava para a classe removida |

### Testes

| Arquivo | O quê |
|---|---|
| `api/exception/ErrorAdviceContractTest.java` | era `GlobalExceptionHandlerTest`; passou a instanciar os três *advices*; +2 testes |
| `api/exception/AdviceOrderTest.java` | **novo** — trava a ordem dos *advices* |
| `security/BusinessRuleStatusTest.java` | **novo** — prova o `422` de ponta a ponta, com contraprova do `400` |
| `ga/GaEdgeCasesTest.java` | 2 testes atualizados para `DomainException` |
| `security/LogPrivacyTest.java` | coletor de log apontado ao **pacote**, não a uma classe |
| `api/ApiErrorContractTest.java`, `api/ApiFailureContractTest.java`, `security/ClientErrorStatusTest.java` | referências de Javadoc atualizadas |

Sobre `LogPrivacyTest`: a mudança de alvo é mais do que conveniência. Um coletor no logger do
**pacote** `api.exception` recebe, por propagação do Logback, os eventos dos três *advices* — e
também os de qualquer tratador que venha a ser criado depois. Apontar para uma classe deixaria o
tratador novo fora da rede de proteção de dado pessoal.

### Documentação

| Arquivo | O quê |
|---|---|
| `docs/adr/0005-criterio-de-classificacao-de-erro.md` | **novo** |
| `docs/adr/README.md` | índice |
| `README.md` | seção *Error Handling* reescrita: os três *advices*, a distinção 400 × 422, link para o ADR |
| `docs/qualidade/03d-correcao-contrato-de-erro.md` | este arquivo |

---

## 5. Validação

`mvn clean verify` ao final, com o *gate* de cobertura ativo:

```
[INFO] Tests run: 164, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.13:check (check-coverage-floor) ---
[INFO] BUILD SUCCESS
```

- **164 testes, 0 falhas** (eram **157** no commit `0deb0c7`, medidos antes de começar a etapa).
- **Piso de cobertura respeitado**: 82,64 % instruções (piso 77 %) e 61,05 % ramos (piso 55 %) —
  ambos subiram em relação ao início da etapa (81,79 % e 60,86 %).
- **Contrato OpenAPI intacto**: `contract/OpenApiContractTest` compara a especificação publicada
  contra um *snapshot*. Ele passa, o que confirma que a reorganização interna **não alterou o
  contrato publicado** — a divisão foi estrutural, não de interface. As mudanças de *comportamento*
  são as duas do E3, deliberadas e provadas por `BusinessRuleStatusTest`.

A suíte também foi executada a cada passo, não só no fim: após a reclassificação do E3, após a
criação de cada *advice*, e após a remoção do `GlobalExceptionHandler`.

---

## 6. O que ficou de fora, e por quê

| Item | Situação |
|---|---|
| **Mudança de contrato público** | O `422` é observável por qualquer cliente. Aceito e registrado no ADR-0005: o comportamento anterior contrariava a RFC que o `README.md` invoca, e a API ainda não tem consumidor externo em produção. Se tivesse, isto exigiria versionamento — **é a decisão que mais merece revisão humana nesta etapa**. |
| **`docs/revisao-ag/01-auditoria-fitness.md:308`** | Cita `GlobalExceptionHandler.java:91-104`, arquivo que não existe mais. É um relatório de auditoria datado, um registro do que era verdade naquele momento; **não foi editado**, pela mesma razão que um ADR aceito não é editado. Fica anotado aqui para quem seguir a referência. |
| **Os dois tratadores inalcançáveis** (`handleMissingParameter`, `handleTypeMismatch`) | Agora **testados**, mas continuam sem caminho HTTP. Permanecem sob a pendência **P6** de `01b-correcao-testes.md` — remover ou tornar alcançável é decisão de produto sobre a API futura, não de teste. |
| **Achados E8 a E13** | Fora do escopo desta etapa, que atendeu E3 e E7. Continuam abertos em `03-diagnostico-estrutura.md`. |

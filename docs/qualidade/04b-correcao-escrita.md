# 04b — Correção de legibilidade e manutenibilidade

- **Data:** 2026-09-03
- **Branch:** `release/v4.0`
- **Base:** [`04-diagnostico-escrita.md`](./04-diagnostico-escrita.md) — 19 achados (L1–L19)
- **ADR produzido:** [ADR-0007](../adr/0007-idioma-do-codigo-e-da-documentacao.md)
- **Pendências abertas:** P11, P12, P13 (domínio) e P14, P15, P16 (dependências)

---

## Resumo em uma tela

| | Antes | Depois |
|---|---|---|
| Ferramenta de estilo | **nenhuma** | Checkstyle na fase `validate` + `.editorconfig` |
| Violações de estilo (config do projeto) | 213 | **0** |
| Linhas acima de 120 caracteres | 96 | **0** |
| Métodos com complexidade ≥ 10 | 1 | **0** |
| Métodos com complexidade ≥ 8 | 6 | 4 |
| Duplicação (CPD, `src/main` + benchmarks) | 5 blocos / 103 linhas | **4 blocos / 69 linhas** |
| Duplicação de lógica de negócio | 2 casos | **0** |
| Comentários que descrevem comportamento inexistente | 4 | **0** |
| Status HTTP documentados no OpenAPI | 5 de 9 reais | **9 de 9** |
| Erros verificáveis no README | 3 | **0** |
| Aviso automático de dependência desatualizada | nenhum | Dependabot |
| Spring Boot | 3.5.5 (22 releases atrás) | **3.5.16** (11 atrás, só majors) |
| Testes | 173 | **203** (+30) |
| Cobertura (BUNDLE) | 84,04 % instr. / 61,80 % ramos | **91,09 % / 72,88 %** |
| Piso de cobertura | 0,77 / 0,55 (folga de 14 pontos) | **0,9109 / 0,7288** — colado na medição |
| Suíte | verde | **verde** (`mvn clean verify`) |

**Nenhum comportamento de produção mudou**, com uma exceção deliberada: o OpenAPI passou a
documentar quatro códigos de status que a API já devolvia. A refatoração foi validada contra testes
de caracterização escritos **antes** dela.

---

## 1. Formatação: um verificador, não um formatador

### A decisão, e por que ela é o oposto do óbvio

O diagnóstico encontrou **nenhuma ferramenta configurada** (achado L1) e 2.651 violações pelo
`google_checks`. A reação natural seria instalar o Spotless com `google-java-format` e rodar.

**Não foi o que se fez, e a razão está nos números do próprio diagnóstico:** das 2.651 violações,
**1.915 — 72 % — eram de indentação**, porque o `google_checks` exige 2 espaços e o projeto usa 4. A
medição separada mostrou que o projeto é **100 % consistente em 4 espaços, com zero tabulações**.

Ou seja: aquelas 1.915 violações não mediam desordem do projeto. Mediam a distância entre a escolha
do projeto e a de um guia de estilo que ninguém adotou. Rodar um formatador que reescreve tudo
custaria:

- um *diff* de aproximadamente **3.700 linhas**, praticamente todo o código de produção;
- o `git blame` de quase todo o repositório, que é a ferramenta que responde "por que esta linha
  está assim?" — a mesma pergunta que os ADRs desta varredura existem para responder;
- **zero ganho de leitura**, porque a formatação já era uniforme.

O que faltava não era disciplina de formatação. Era **algo que travasse a disciplina existente**.

### O que foi configurado

`config/checkstyle/checkstyle.xml` — a convenção **medida do próprio código**, não importada:

| Regra | Valor | De onde veio |
|---|---|---|
| Indentação | 4 espaços, sem tabulação | 100 % dos arquivos já faziam |
| Comprimento de linha | 120 | percentil 99 medido: 121 |
| Imports | sem asterisco, sem mortos | 9 e 5 ocorrências, todas isoladas |
| Chaves | obrigatórias em `if` | 7 exceções, todas isoladas |
| Complexidade ciclomática | máximo 10 | estado após a §2 desta etapa: pior em 8 |
| Comprimento de método | máximo 80 linhas | estado atual: maior tem 20 executáveis |

Os limiares de complexidade são **catraca, não aspiração**: refletem o estado já alcançado, de modo
que quem passar deles precise justificar em vez de descobrir meses depois.

Roda na fase **`validate`**, antes de compilar: uma violação de estilo falha em segundos, não no fim
de um build de minutos. Também foi acrescentado `.editorconfig`, para o editor acertar antes de o
build reclamar, e a publicação do relatório de estilo no CI.

### O gate foi verificado nos dois sentidos

Um teste que nunca falhou não provou nada. Com um `import java.util.*` injetado de propósito em
`Population.java`:

```
[ERROR] .../ga/Population.java:3:17: Using the '.*' form of import should be avoided
[INFO] BUILD FAILURE
```

Removida a sabotagem, `BUILD SUCCESS`.

### O que foi corrigido para o gate passar

| Item | Antes | Depois |
|---|---:|---:|
| Linhas acima de 120 caracteres | 96 | 0 |
| Imports com asterisco | 9 | 0 |
| Imports mortos | 5 | 0 |
| Arquivos sem quebra de linha final | 14 | 0 |
| `if` sem chaves | 7 | 0 |
| Nomes totalmente qualificados no corpo do código | 14 | 0 |
| Métodos de teste com sublinhado | 3 | 0 |

As quebras de linha foram feitas por script, com o compilador e a suíte verificando a cada passo. As
últimas 19 — que nenhum padrão automático pegava — foram feitas à mão. Os **14 nomes totalmente
qualificados** fecham o padrão amplo que o diagnóstico havia registrado como maior do que o achado
E13 descrevia (§6 do diagnóstico).

---

## 2. Complexidade: refatorado com rede, não com coragem

A instrução era explícita: *"não refatore sem rede de segurança"*. Aqui ela não era formalidade — os
três alvos estavam praticamente **sem cobertura nenhuma**:

| Classe | Complexidade | Cobertura antes |
|---|---|---:|
| `DayBoundaryCrossover#crossover` | **CC 10** — a maior do repositório | **0,0 %** |
| `FatigueAndEnergyModel#calculateBurnoutRisk` | CC 9, 30 linhas, 26 números mágicos | **1,3 %** |
| `SpacedRepetitionRepairer#repair` | cognitiva 21, 4 níveis de aninhamento | **4,9 %** |

Refatorar código de complexidade 10 sem teste é reescrever no escuro: nada avisaria se um limiar
mudasse de `>` para `>=`.

### O que é um teste de caracterização

Um **teste de caracterização** (*characterization test*) descreve o que o código **faz hoje**, sem
julgar se está certo. Não valida a regra de negócio — existe para que qualquer mudança de
comportamento durante a refatoração apareça como falha.

Os 26 testes foram escritos e **executados contra o código não modificado**, em commit próprio
(`ed8135f`), antes de qualquer alteração de produção. Depois de cada refatoração, os mesmos 26
passaram sem uma única asserção alterada.

### Escrever o teste primeiro pagou: três descobertas

Este é o resultado mais importante da etapa, e nenhuma das três apareceria por leitura.

**Descoberta 1 — a metodologia de estudo não afeta o risco de esgotamento.**

As cinco metodologias produzem valores idênticos para o mesmo número de blocos. A causa, medida:

```
energia esperada, em qualquer hora e cronotipo:  máximo 1,500
carga do bloco MAIS LEVE possível (leitura passiva, 15 min):  2,048
```

O modelo escolhe entre multiplicador 1,5 e 0,8 comparando os dois — e a comparação é **sempre
verdadeira**. O ramo de 0,8 é inalcançável, e a curva bifásica por cronotipo, calculada logo acima
com picos próprios para cada perfil, é **computada e descartada**. Vinte e cinco linhas de código
sugerem que o cronotipo importa; ele não importa. → **P12**

**Descoberta 2 — a função é descontínua.**

A rampa gradual para em **0,5** e o degrau agudo devolve **0,1**. Nenhuma entrada produz valor entre
os dois. O Javadoc prometia queda "exponencial" (achado C1), o que não descreve nenhum dos três
regimes.

**Descoberta 3 — o regime de esgotamento crônico é inalcançável pela API.**

Exige `fatigueLevel >= 8`; `StudentStateDto` valida o campo com `@DecimalMax("5.0")`. Varredura
sobre fadiga 1–5 — a única faixa que a API aceita — não encontrou **nenhuma** combinação de dias,
blocos, duração e metodologia que devolva `0.2`. É o mesmo padrão do achado E3 (o `422` decorativo),
noutro lugar. → **P11**

Um quarto caso, em `DayBoundaryCrossover`: **pai 1 vazio produz filho vazio**, descartando o segundo
pai por inteiro, porque o intervalo de dias é calculado só a partir do primeiro. → **P13**

**As quatro foram preservadas exatamente.** Corrigi-las é decisão de domínio — qual carga *deveria*
caracterizar esgotamento crônico? o cronotipo *deveria* influenciar a fadiga? — e não de
legibilidade. Estão travadas por teste, para que a mudança, quando vier, seja deliberada e visível.
Registradas em `docs/revisao-ag/04-robustez.md` §9.

### O que a refatoração fez

**`FatigueAndEnergyModel`** — 26 números mágicos viraram 22 constantes nomeadas; extraídos
`fatigueOf`, `gradualPenalty`, `chronologically` e `peakContribution`. O Javadoc mentiroso foi
substituído pela descrição dos três regimes reais e do salto entre eles. CC 9 → 8, método principal
de 30 → 20 linhas.

**`DayBoundaryCrossover`** — extraídos `drawCutoffDay` e `copyDays`; os dois laços de cópia com
condições espelhadas viraram uma função parametrizada por `IntPredicate`. CC 10 → abaixo de 8.

**`SpacedRepetitionRepairer`** — extraídos `needsReview`, `alreadyHasReview`, `scheduleReview` e
`reviewValueOf`; o aninhamento caiu de quatro níveis para um, com um `continue` no topo do laço.

### Resultado

| | Antes | Depois |
|---|---:|---:|
| Métodos com CC ≥ 10 | 1 | **0** |
| Métodos com CC ≥ 8 | 6 | 4 |
| Métodos com ≥ 30 linhas executáveis | 1 | **0** |
| `DayBoundaryCrossover` | 0,0 % | **100 %** |
| `SpacedRepetitionRepairer` | 4,9 % | **100 %** |
| `FatigueAndEnergyModel` | 1,3 % | **99,1 %** |

---

## 3. Duplicação: as duas relevantes, eliminadas

### L2 — 21 linhas idênticas, byte a byte

`repairChildGenes` existia duas vezes, em `RepairingCrossover` e `WeightedAverageCrossover`, com o
Javadoc igual palavra por palavra. O risco nunca foi o volume: **qual dos dois operadores roda
depende de um sorteio** em `HybridCrossover`, então corrigir um defeito numa cópia e não na outra
produziria planos inválidos de forma **intermitente**.

Extraído para `ChildGeneRepair`, de visibilidade de pacote — é detalhe interno da família de
cruzamento, não API. `ChildGeneRepairTest` cobra a invariante **pelos dois operadores e pelo
híbrido**: soma de dias igual ao orçamento, nenhum piso violado, término garantido quando nenhuma
disciplina pode ceder dias. Foi escrito e executado contra o código duplicado, antes da extração.

### L3 — a cópia que o CPD não via

`BenchmarkHarness.buildContext` era uma cópia manual de `EvolutionContextAssembler.assemble`: as
mesmas dez chamadas na mesma ordem, com nomes de variável diferentes — o que basta para o CPD não
detectar.

**A disciplina que mantinha as duas em dia já havia falhado**: o comentário apontava para
`StudyOptimizerService.prepareContext`, método removido na etapa 03e.

A única diferença real era a data de início: produção lê o relógio, o benchmark precisa de âncora
fixa para reproduzir métricas em qualquer dia. Resolvido com uma sobrecarga
`assemble(exam, profile, planStartDate)` — o harness agora **chama** a produção em vez de
transcrevê-la. É também um passo na direção da pendência **P7**.

A verificação forte: `GeneticAlgorithmVsBaselinesTest` tem limiar de regressão sobre os números
publicados. Se o contexto tivesse mudado, ele acusaria. Continua verde.

### O que sobrou, e por que fica

CPD sobre `src/main` + benchmarks: **5 blocos / 103 linhas → 4 blocos / 69 linhas**. O bloco de 34
linhas desapareceu. Os quatro restantes:

| Blocos | O que são | Por que ficam |
|---|---|---|
| 19 linhas | Cabeçalho e assinatura de `RepairingCrossover` × `WeightedAverageCrossover` | Semelhança estrutural entre duas implementações da mesma interface. Unificar exigiria herança que acopla sem separar |
| 26 + 14 linhas | Impressão de tabela em `WeightSensitivityMain` × `WeightTradeoffMain` | Dois programas de benchmark independentes. Nenhuma lógica de negócio |
| 10 linhas | Duas instâncias em `HighLoadInstanceLibrary` | Dados de teste construídos do mesmo jeito |

**Nenhum deles é lógica de negócio duplicada.**

---

## 4. Nomenclatura: a fronteira de idioma, escrita

Três dos quatro itens do diagnóstico já vieram limpos na medição (zero variáveis de uma letra fora
de laço, nomes de classe sem abreviação inventada). Restavam dois.

### A abreviação obscura

`totalGKQuestions` → `totalGeneralKnowledgeQuestions` (`domain/exam/Exam.java`). Era a única do
repositório, num método que escreve `generalKnowledge` por extenso duas linhas abaixo.

### O idioma — e a responsabilidade por ele

O achado **E11** mediu 6 de 118 classes com Javadoc em português. A etapa 04 mediu **31 de 122**. E
a escalada é consequência direta desta varredura: cada relatório foi escrito em português a pedido,
e o português foi junto para o Javadoc sem que ninguém decidisse que deveria.

**A instrução era explícita — "não o repositório inteiro de uma vez" — e ela é a decisão certa
aqui.** Traduzir os 31 arquivos (ou os outros 88) seria o maior *diff* possível, sem ganho de
leitura, apagando o `git blame` — exatamente o que a §1 recusou para formatação.

O que foi feito: **[ADR-0007](../adr/0007-idioma-do-codigo-e-da-documentacao.md)** escreve a
convenção que o código já pratica.

> Inglês para o que a máquina lê ou o que sai pela API — identificadores, mensagens de exceção,
> chaves de log. Português para o que só uma pessoa lê — Javadoc, comentário, `@DisplayName`.

Vale para código novo e código tocado. A conversão acontece por atrito natural, não por mutirão. O
ADR registra também o custo aceito: o repositório fica bilíngue por tempo indeterminado, e a
decisão fecha a porta para quem não lê português.

O contador subiu de 31 para 35 durante esta etapa, pelas classes tocadas aqui — o que é a convenção
funcionando, não escapando.

---

## 5. Comentários que mentiam

| # | Onde | O que dizia | Situação |
|---|---|---|---|
| **C1** | `FatigueAndEnergyModel:29` | queda "exponencial" além do limiar | O código tem dois degraus e uma rampa linear. Reescrito na §2 com os três regimes |
| **C2** | `BenchmarkHarness:151,168` | "mirrors `StudyOptimizerService.prepareContext` field for field" | O método não existe desde a 03e. **Eliminado**: o harness agora chama a produção (§3) |
| **C3** | `InstanceLibrary:254` | `StudyPlanFactory` rejeita com `IllegalArgumentException` | Virou `DomainException` na 03d (ADR-0005). Corrigido |
| **C4** | `WeightTradeoffMain:388` e `ProductionGeneticAlgorithm:33,115` | "the service builds its own context" / "the service injects" | Quem monta é o `EvolutionContextAssembler` desde a 03e. Corrigido |

**Três dos quatro foram criados por mim**, nas etapas 03d e 03e, ao mover código sem seguir suas
referências para dentro de `benchmarks/` — diretório que é compilado no escopo de teste mas não tem
teste próprio, e onde por isso comentários apodrecem sem resistência.

As **13 referências `arquivo:linha` quebradas** em documentos continuam onde estão, deliberadamente:
são relatórios datados, e reescrevê-los quando o código muda destruiria o registro histórico — mesma
razão pela qual um ADR aceito não é editado. A exceção está na §6.

---

## 6. Débito: zero marcadores, 66 pendências em prosa

O diagnóstico encontrou **zero** `TODO`/`FIXME` — e mostrou que isso não era a boa notícia que
parece: o débito existia, em 66 itens espalhados por documentos, com apenas 14 citados de algum
comentário de código.

### Obsoleto, removido

`docs/revisao-ag/04-robustez.md` não é relatório datado: é **tabela de rastreamento viva**. Ela
ainda listava **P6** como pendência aberta e ainda afirmava, na linha 248, que inviabilidade sai
como `400`. As duas coisas deixaram de ser verdade na etapa 03d. Um leitor que consultasse a tabela
hoje trabalharia sobre informação falsa. P6 foi marcada como fechada, com link para o ADR-0005 e
para o teste que a prova; a linha 248 foi corrigida.

### Relevante, ancorado no código

A decisão sobre uma linha é tomada **olhando a linha**, não o documento. Duas pendências que se
referem a pontos exatos ganharam âncora onde importam:

- **P7** (`LocalDate.now()` dentro da lógica) → `EvolutionContextAssembler:73`, na chamada que lê o
  relógio, dizendo o que fechar exige.
- **P5** (o piso de dias que inviabiliza editais grandes) → `BaselineCalculator:33`, junto às duas
  constantes que o produzem, ligando-as ao `422` que `BusinessRuleStatusTest` exercita.

### Novo, documentado como decisão de produto

P11, P12 e P13 — as três descobertas da §2 — registradas na tabela de pendências com evidência
medida e proposta, e referenciadas do Javadoc das classes onde vivem.

**O que não foi feito:** ancorar as 66 pendências. Seria poluição, e a maioria não se refere a uma
linha específica. O critério aplicado: ancora-se o que tem endereço exato e cobraria caro se fosse
esquecido.

---

## 7. README e documentação de API

### O erro grave

O README documentava `server.forward-headers-strategy` com padrão **`framework`**. O valor real é
**`none`** — e `framework` é exatamente o que **reabre o achado de segurança S12**: o
`ForwardedHeaderFilter` reescreve `request.getRemoteAddr()` com o cabeçalho enviado pelo cliente
antes de qualquer filtro da aplicação, e variar o cabeçalho a cada requisição burla o limite de
taxa.

Não era imprecisão de documentação; era uma armadilha. Corrigido, e as outras **três propriedades da
mesma premissa de implantação** — ausentes da tabela — ganharam seção própria, com o aviso de que as
quatro devem ser ligadas juntas.

### Os outros dois erros e as seções obsoletas

- `cd DynamicStudyPlanner` → `cd exam-optimizer-application` (o repositório clona com outro nome).
- **Testes:** passou a distinguir `test` de `verify`, dizendo que os dois portões — estilo e piso de
  cobertura — só rodam em `verify`, e mencionando o CI.
- **Code Quality:** ainda recomendava "configurar Checkstyle no futuro". Reescrita com o que existe:
  Checkstyle, piso JaCoCo, teste de fronteira arquitetural, retrato do contrato OpenAPI, e a
  admissão franca de que análise estática **não** está configurada.

### O contrato de API

O OpenAPI declarava **5** códigos de status; a API devolve **9**. Os quatro ausentes — `404`, `405`,
`415` e `429` — são todos provados por teste existente (`ClientErrorStatusTest`,
`ApiErrorContractTest`). Os três primeiros surgiram na etapa 02b e a especificação não acompanhou.

Documentados. O `OpenApiContractTest` **reprovou a mudança**, que é exatamente sua função, e o
retrato foi atualizado deliberadamente:

```
POST /api/v1/optimizer/generate -> [200, 400, 404, 405, 408, 415, 422, 429, 500]
```

Esta é a **única mudança observável pelo cliente** nesta etapa, e ela só documenta o que já
acontecia.

---

## 8. Dependências: as baratas atualizadas, as caras explicadas

### Atualizadas — todas compatíveis por definição

| Dependência | De | Para | Natureza |
|---|---|---|---|
| `spring-boot-starter-parent` | 3.5.5 | **3.5.16** | 11 correções dentro da mesma linha menor |
| `jacoco-maven-plugin` | 0.8.13 | 0.8.15 | 2 correções |
| `build-helper-maven-plugin` | 3.6.0 | 3.6.1 | 1 correção |

O caso do Spring Boot é o que o diagnóstico chamou de "caminho barato não tomado": dos 22 releases
de atraso, **11 eram patches dentro da 3.5** — compatíveis por definição de versionamento
semântico, ao custo de trocar um número. A atualização do *parent* arrasta junto Jackson, Logback,
Micrometer, Lombok e Caffeine, que não têm versão própria.

**Verificação:** `mvn clean verify` completo, 203/203 testes verdes, incluindo o retrato do contrato
OpenAPI — que teria acusado qualquer mudança na especificação gerada pelo springdoc sob o Spring
novo.

### Não atualizadas — com o motivo, como pedido

| # | Dependência | Atraso | Por que não foi forçada |
|---|---|---|---|
| **P14** | `spring-boot-starter-parent` 3.5.16 → 4.1.1 | 11 releases, **linha maior** | Arrasta Spring Framework 7 e provavelmente Jakarta EE 11. Exige plano de teste próprio: rodar o *harness* de benchmark para confirmar que os números publicados não mudam, e revalidar os testes de segurança das etapas 02b e 03d, que dependem de comportamento de filtro e de tratamento de exceção |
| **P15** | `springdoc-openapi` 2.8.17 → 3.1.0 | 6 releases, **linha maior** | **Já quebrou esta API uma vez**: a 2.5.0 era binariamente incompatível com o Spring 6.2 e devolvia HTTP 500 em `/v3/api-docs`, descoberto só na etapa 01b. A linha 3.x existe porque o Boot 4 precisa dela, então esta é a dependência que **decide quando P14 é viável** — as duas migram juntas ou nenhuma |
| **P16** | `logstash-logback-encoder` 7.4 → 9.0 | 3 releases, **duas linhas maiores** | É o que dá formato ao log JSON que sai para o agregador. Mudança de formato afeta os testes de privacidade da etapa 02b (`LogPrivacyTest`), que inspecionam a mensagem formatada e a pilha — e afeta qualquer consulta já montada no agregador |

Nos três casos o critério foi o mesmo: **atraso não é justificativa para migrar sem plano de teste**,
e este repositório tem testes que dependem de comportamento de biblioteca em três frentes distintas
(contrato HTTP, formato de log, filtros de segurança).

### O que faltava mais do que qualquer atualização

`.github/dependabot.yml`. O diagnóstico registrou que **nenhum mecanismo avisava** — os números só
existiam porque alguém rodou a ferramenta à mão. Foi assim que 11 correções de patch passaram
despercebidas.

Configurado com: atualizações compatíveis agrupadas num PR semanal, para não gerar ruído de revisão;
e as três *majors* acima explicitamente ignoradas, com a justificativa escrita no próprio arquivo —
para que ignorar seja uma decisão visível, e não um esquecimento.

---

## 9. Validação

`mvn clean verify` ao final, com os dois portões ativos:

```
[INFO] --- checkstyle:3.6.0:check (checkstyle-validation) ---   0 violações
[INFO] Tests run: 203, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- jacoco:0.8.15:check (check-coverage-floor) ---
[INFO] BUILD SUCCESS
```

- **203 testes, 0 falhas** (eram 173 no início da etapa).
- **Cobertura subiu de 84,04 % para 91,09 %** de instruções e de 61,80 % para 72,88 % de ramos, quase
  toda a diferença vinda das três classes táticas que estavam perto de zero.
- **Nenhuma asserção de teste existente precisou mudar.** Os 26 testes de caracterização passaram
  antes e depois de cada refatoração, sem edição. É o que se espera de uma refatoração que preserva
  comportamento; se alguma asserção tivesse mudado, seria sinal de que o sistema passou a fazer
  outra coisa.
- **O retrato do contrato OpenAPI mudou uma vez, deliberadamente**, para documentar quatro status
  que já existiam.

A suíte foi executada a cada mudança estrutural, não só no fim, e a etapa foi dividida em cinco
commits que podem ser lidos — e revertidos — separadamente:

| Commit | Conteúdo |
|---|---|
| `ed8135f` | rede de segurança (26 testes), **antes de qualquer mudança de produção** |
| `45cd786` | refatoração dos três ofensores de complexidade |
| `80c5438` | eliminação das duas duplicações |
| `5b8609f` | ferramenta de estilo e as correções para o gate passar |
| `19d5912` | comentários, README, contrato de API, dependências |

### 9.1 O piso de cobertura foi colado na medição

O piso estava em **0,77 de instruções e 0,55 de ramos** desde a etapa 01b, enquanto a cobertura
subia para 0,9109 — uma folga de quatorze pontos. Elevá-lo foi recomendado ao fim da 03e e de novo
ao fim desta etapa; agora foi feito.

| | Antes | Depois |
|---|---|---|
| Instruções | 0,77 | **0,9109** (6290 de 6905) |
| Ramos | 0,55 | **0,7288** (379 de 520) |

**Sem folga, e isso exigiu verificar uma coisa antes.** Um piso colado na medição só é seguro se a
medição for determinística — caso contrário o *gate* falharia de forma intermitente, que é o pior
tipo de portão. Três execuções limpas consecutivas deram **os mesmos números absolutos**, não apenas
a mesma razão:

```
execucao 1: instrucoes 6290/6905   ramos 379/520
execucao 2: instrucoes 6290/6905   ramos 379/520
execucao 3: instrucoes 6290/6905   ramos 379/520
```

A aleatoriedade do algoritmo genético é sempre semeada nos testes, e `RandomProviderIsolation`
restaura a fonte original entre eles — é o que torna o resultado repetível.

**Verificação nos dois sentidos.** Com `FatigueAndEnergyModelTest` removido:

```
[WARNING] Rule violated for bundle: instructions covered ratio is 0.8764, but expected minimum is 0.9109
[WARNING] Rule violated for bundle: branches covered ratio is 0.6826, but expected minimum is 0.7288
[INFO] BUILD FAILURE
```

Restaurado, `BUILD SUCCESS` com 203 testes. **O piso antigo teria aprovado essa mesma sabotagem** —
0,8764 é confortavelmente acima de 0,77. É a demonstração concreta de por que a folga importava: uma
catraca que não trava não é catraca.

> **Nota de método.** As duas primeiras tentativas de sabotagem *não* reprovaram o build, e isso é
> informação. Remover um teste individual de `DayBoundaryCrossoverTest` e depois um de
> `SpacedRepetitionRepairerTest` deixou a cobertura **idêntica**: os demais testes da mesma classe
> percorrem as mesmas linhas. Os testes de caracterização são redundantes em cobertura porque fixam
> *comportamento*, não linhas — cada um pinça um caso distinto sobre código compartilhado. Só a
> remoção de uma classe de teste inteira derrubou o número. Registrado porque um piso de cobertura
> mede o que foi *executado*, nunca o que foi *verificado*, e essa distinção é fácil de esquecer.

**O que isso passa a exigir de quem mantém o repositório:** acrescentar código coberto não atualiza
o piso sozinho — é preciso ler `target/site/jacoco/jacoco.csv` e subir os dois valores. Acrescentar
código sem teste reprova o build, que é a função. O comentário no `pom.xml` explica as duas coisas,
junto com o histórico dos pisos e a razão de cada um.

---

## 10. O que ficou de fora, e por quê

| Item | Situação |
|---|---|
| **P11, P12, P13** — as três limitações medidas | **Preservadas de propósito**, travadas por teste. São decisões de domínio: qual carga deveria caracterizar esgotamento crônico, se o cronotipo deveria influenciar a fadiga, se o cruzamento deveria considerar os dois pais. Mudar qualquer uma altera o plano de todos os usuários |
| **P14, P15, P16** — as três *majors* | Documentadas com o motivo e ignoradas no Dependabot. Cada uma exige plano de teste próprio (§8) |
| **Achados E9 e E10** — seis sufixos para o mesmo papel, dois critérios de organização de pastas | Renomear classes e mover pacotes é exatamente o "repositório inteiro de uma vez" que a instrução excluiu. Continuam abertos em `03-diagnostico-estrutura.md` |
| **Traduzir os 88 Javadoc em inglês** | Recusado pelo ADR-0007: maior *diff* possível, sem ganho de leitura, apagando o `git blame` |
| **As 13 referências de linha quebradas em documentos** | Mantidas. São relatórios datados; a única que era rastreamento vivo (a tabela de P6) foi corrigida |
| **Análise estática (PMD, SonarQube)** | Não configurada. Foi rodada à mão para o diagnóstico; incorporá-la ao build é outra decisão, com outro custo de tempo de execução |

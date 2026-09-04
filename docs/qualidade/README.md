# Varredura de qualidade — status consolidado

Este documento resume **seis áreas** de trabalho sobre o SINAPSE, feitas em catorze etapas entre
2026-09-01 e 2026-09-04. Ele existe para quem precisa saber **em que estado o sistema está** sem ler
os quinze relatórios — e para separar, de forma inequívoca, **o que foi resolvido** do **que espera
decisão humana**.

Cada área teve duas etapas: um **diagnóstico**, que só mede e não altera código, e uma **correção**,
que age sobre o que o diagnóstico encontrou. A regra valeu do começo ao fim: nenhum achado entra num
relatório sem número medido, e nenhuma correção entra sem antes/depois pela mesma metodologia.

---

## Resumo em uma tela

| | Antes da varredura | Agora |
|---|---:|---:|
| Testes | **73** | **288** |
| Cobertura de instruções | 67,1 % | **92,44 %** |
| Cobertura de ramos | 52,9 % | **74,91 %** |
| Integração contínua | **nenhuma** | gate no GitHub Actions, verificado nos dois sentidos |
| Ferramenta de estilo | **nenhuma** | Checkstyle na fase `validate`, 0 violações |
| Varredura de dependências | **nenhuma** | Dependabot configurado (falta 1 clique — ver §7) |
| Contrato HTTP | sem retrato | retrato versionado; mudanças aparecem no *diff* |
| Pior caso do motor de planejamento | 2 349 ms | **1 163 ms** (−50 %) |
| Vazão sob concorrência (16 simultâneos) | 30 req/s, latência 533 ms | **174 req/s, latência 92 ms** |
| Comportamento sob sobrecarga | 55 % de respostas **500** | **0** respostas 500; 503 com `Retry-After` |
| Réplicas | limite de taxa quebrava na 2ª | estado compartilhado, verificado com 2 réplicas reais |
| Suíte | verde | **verde** (`mvn clean verify`) |

**Nenhum comportamento de produção foi alterado sem que a mudança fosse deliberada e registrada.**
As exceções estão listadas em §8.

---

## 1. Testes — de 73 para 288, e de "passa" para "protege"

**Documentos:** [`01-diagnostico-testes.md`](./01-diagnostico-testes.md) ·
[`01b-correcao-testes.md`](./01b-correcao-testes.md)

### O que foi encontrado

- **Nenhum teste do repositório recebia uma resposta 200.** Os dois testes de API existentes
  enviavam cargas inválidas e eram rejeitados pela validação antes de o controlador executar. Todo o
  caminho entre receber o pedido e devolver o plano tinha **zero instruções cobertas**.
- **Oito dos nove tratadores de erro nunca executavam** em teste.
- **Zero integração contínua.** Nada impedia um merge com a suíte quebrada.
- **Dois defeitos de produção**, que existiam antes deste trabalho e nenhuma suíte pegava: a
  documentação da API estava fora do ar (`500`) e o projeto **não gerava jar executável** — o
  artefato de implantação descrito no README não chegava a existir.

### O que foi corrigido

O fluxo de sucesso passou de 0 % a 100 % nos dois métodos que o diagnóstico apontou como risco
número um; os nove tratadores de erro foram a 100 %; e o repositório ganhou seu primeiro gate de CI,
**verificado nos dois sentidos** — a primeira execução da história passou em 59 s, e sabotagens
provaram que ele reprova por teste quebrado e por queda de cobertura.

### Uma lição que vale além desta área

Remover testes individuais de duas classes deixou a cobertura **idêntica** — os outros testes da
mesma classe cobriam as mesmas linhas. Só remover a classe inteira derrubou o número. Registrado
assim: *um piso de cobertura mede o que foi executado, nunca o que foi verificado.*

---

## 2. Segurança — o que estava aberto, e o que não é decisão de engenharia

**Documentos:** [`02-diagnostico-seguranca.md`](./02-diagnostico-seguranca.md) ·
[`02b-correcao-seguranca.md`](./02b-correcao-seguranca.md)

### O que foi encontrado e corrigido

- **O limite de taxa era burlável por cabeçalho.** Com `X-Forwarded-For` vindo do próprio cliente,
  bastava variar o cabeçalho a cada requisição para trocar de balde e anular o limite. A resolução
  do endereço virou `ClientIpResolver`, que só confia em proxy declarado.
- **Dado pessoal saía no log.** O endereço do cliente ia inteiro para o registro de abuso; passou a
  ser mascarado.
- **Enforcement de TLS** implementado de forma defensiva, desligado por padrão.

### O que **não** foi decidido aqui — e por quê

O contrato coleta `stressLevel`, `fatigueLevel` e `motivationLevel` — estado psicológico
autodeclarado — **junto com o nome do estudante**. A categoria mais próxima na LGPD é dado referente
à saúde, que é dado sensível; e o público de concursos e vestibulares frequentemente **inclui
adolescentes**, o que aciona o art. 14.

**Isso não é defeito a corrigir: é decisão de produto e jurídico.** Está em §7.

---

## 3. Estrutura — fronteiras, ciclos e responsabilidades

**Documentos:** [`03-diagnostico-estrutura.md`](./03-diagnostico-estrutura.md) ·
[`03b-correcao-estrutura.md`](./03b-correcao-estrutura.md) ·
[`03d-correcao-contrato-de-erro.md`](./03d-correcao-contrato-de-erro.md) ·
[`03e-correcao-responsabilidades.md`](./03e-correcao-responsabilidades.md)

> **Nota de leitura:** esta área numera seus achados de **E1 a E15**, e a área de escalonamento (§6)
> numera os dela de **E1 a E10**. São séries distintas. Ao citar um achado `E*`, diga sempre de qual
> documento.

### O que foi encontrado e corrigido

- **Ciclo de dependência entre módulos** (`ga ↔ service`), desfeito movendo os contratos de cálculo
  para `domain` (ADR-0001). Um teste de arquitetura passou a reprovar se ele voltar — e esse teste
  **pegou um ciclo novo** que a etapa de escalonamento introduziu por engano (§6).
- **Um `GlobalExceptionHandler` de 447 linhas e 16 responsabilidades**, dividido em três conselhos
  por natureza do erro: requisição, regra de negócio e infraestrutura.
- **O contrato de erro não distinguia 400 de 422.** Inviabilidade de edital — pedido bem-formado que
  o domínio não pode atender — saía como 400. Passou a 422, com ADR.
- **`StudyOptimizerService` acumulava quatro responsabilidades**, separadas em montagem de contexto,
  métricas e orquestração.

---

## 4. Escrita e manutenibilidade

**Documentos:** [`04-diagnostico-escrita.md`](./04-diagnostico-escrita.md) ·
[`04b-correcao-escrita.md`](./04b-correcao-escrita.md)

### O que foi encontrado e corrigido

| | Antes | Depois |
|---|---:|---:|
| Ferramenta de estilo | **nenhuma** | Checkstyle na fase `validate` |
| Violações de estilo | 213 | **0** |
| Linhas acima de 120 caracteres | 96 | **0** |
| Métodos com complexidade ≥ 10 | 1 | **0** |
| Duplicação de lógica de negócio | 2 casos | **0** |
| Comentários que descrevem comportamento inexistente | 4 | **0** |
| Status HTTP documentados no OpenAPI | 5 de 9 reais | **9 de 9** |
| Spring Boot | 3.5.5 (22 releases atrás) | **3.5.16** |

**Zero marcadores `TODO`/`FIXME` não era a boa notícia que parecia:** o débito existia em 66 itens
espalhados por documentos, com apenas 14 citados de algum comentário de código. Uma tabela de
rastreamento ainda listava como aberta uma pendência fechada duas etapas antes — quem a consultasse
trabalharia sobre informação falsa.

### Quatro descobertas de domínio, preservadas de propósito

A refatoração com rede de segurança revelou comportamentos que **não são bugs óbvios nem
funcionalidades claras**. Foram preservados exatamente e registrados como decisão de produto (§7):

- Um ramo de risco de abandono é inalcançável, e **25 linhas de curva por cronotipo são computadas e
  descartadas** — o código sugere que o cronotipo importa; ele não importa.
- Um valor de retorno de carga cognitiva é inalcançável em toda a faixa que a API aceita.
- Em `DayBoundaryCrossover`, **pai 1 vazio produz filho vazio**, descartando o segundo pai inteiro.

### Duas coisas deliberadamente **não** feitas

Reformatação em massa (diff de 3 700 linhas, destrói `git blame`, zero ganho de legibilidade em
código já uniforme) e tradução dos 88 Javadocs em inglês. As duas estão registradas com o motivo.

---

## 5. Performance — 50 % do pior caso, com o resultado inalterado

**Documentos:** [`05-diagnostico-performance.md`](./05-diagnostico-performance.md) ·
[`05b-correcao-performance.md`](./05b-correcao-performance.md)

### O motor de planejamento

Linha de base remedida na própria etapa, medianas de 15 execuções (9 no pior caso):

| Cenário | Antes | Depois | Ganho |
|---|---:|---:|---:|
| 15 disciplinas / 100 gerações / população 50 | 29 ms | **13 ms** | −55 % |
| 15 disciplinas / 500 gerações / população 50 | 117 ms | **42 ms** | −64 % |
| 24 disciplinas / 1000 gerações / população 500 | 2 349 ms | **1 163 ms** | −50 % |
| 40 disciplinas / 100 gerações / população 50 | 36 ms | **19 ms** | −47 % |

**O maior ganho teve a causa mais simples:** a fonte de aleatoriedade do algoritmo era um
`SecureRandom` — gerador criptográfico, com SHA-1 e entropia do sistema operacional — usado para
sortear **simulação**. Eram ~31 % de todo o tempo de CPU. Nenhum dos dez usos gera identificador,
token, senha ou material de sessão; a auditoria que sustenta a troca está escrita na própria classe.

### Três vezes em que a medição contradisse a expectativa

- **A cópia defensiva piorou 22 %.** Memorizar o total de dias exigia copiar o mapa para ser seguro
  por construção; a cópia custou mais do que a memoização economizava, porque planos são construídos
  500 500 vezes no pior caso. Ficou o contrato explícito mais um teste de invariante.
- **A troca entre imutabilidade e velocidade não existia.** O diagnóstico dizia que percorrer o
  invólucro imutável custava caro e que o preço era inevitável. `forEach` no mapa envolto delega
  direto ao mapa de baixo: 61 ns contra 272 ns — mais rápido que percorrer o `HashMap` cru.
- **O maior item da lista não foi corrigido, com evidência.** O cruzamento é 51 % do tempo de parede.
  O laço de reparo, suspeito óbvio, foi instrumentado e é gratuito (0,025 voltas por chamada).
  Eliminar buscas redundantes foi implementado, verificado idêntico e **revertido** — 4 baterias
  pareadas não acusaram ganho. Paralelizar quebraria a reprodutibilidade: com o laço paralelo, duas
  execuções com a **mesma semente** deixam de coincidir.

### A rede que dá sentido a "não mudou comportamento"

Nove **assinaturas de referência** — o plano completo para uma semente fixa, disciplina por
disciplina, mais a fitness com 12 casas — foram capturadas antes da primeira otimização e conferidas
depois de cada uma. São idênticas do começo ao fim.

---

## 6. Escalonamento — replicar sem quebrar

**Documentos:** [`06-diagnostico-escalonamento.md`](./06-diagnostico-escalonamento.md) ·
[`06b-correcao-escalonamento.md`](./06b-correcao-escalonamento.md)

### O achado que não precisava de crescimento para aparecer

O limite de taxa vivia na memória de cada réplica. Com N réplicas, o limite declarado virava N × o
valor configurado — **em silêncio**: nenhum log, métrica ou teste acusava. Demonstrado com dois
processos reais e limite de 5 por minuto:

| | Antes | Depois |
|---|---:|---:|
| Aceitos | **10** | **5** |

A correção central não é o Redis: é que **a escolha deixou de ser silenciosa**. O modo local anuncia
no log o que quebra, e publica uma métrica onde duas réplicas reportando "não replicável" *são* o
defeito acontecendo.

### Sobrecarga: de erro de servidor a recusa educada

Com 120 pedidos pesados simultâneos:

| Resposta | Antes | Depois |
|---|---:|---:|
| **500** Internal Server Error | 66 (55 %) | **0** |
| **408** Request Timeout | 33 (27,5 %) | **0** |
| **503** + `Retry-After` | 0 | 84, mediana 1,59 s |
| **200** OK | 21, mediana 19,08 s | **36**, mediana 15,67 s |
| Registros de nível ERROR | **99** | **0** |

As requisições **bem-sucedidas subiram 71 %**: recusar cedo libera a máquina para terminar trabalho
em vez de se debater com pedidos que vão estourar o prazo.

### Fluxo assíncrono, e o limite que passou a contar custo

O pedido pesado deixou de segurar a conexão por 1 527 ms; agora é aceito em **24 ms** com um
identificador, e o resultado é consultado depois — inclusive **em outra réplica**.

O limite de taxa passou a cobrar por **custo estimado**, calibrado sobre uma grade de 16 medições. O
efeito na justiça entre clientes:

| Pedido | Antes | Depois | Clientes obedientes para saturar |
|---|---:|---:|---:|
| Típico | 5/min | **650/min** | 2 088 → **16** |
| Pesado | 5/min | **7/min** | 17 → **12** |

A assimetria entre os extremos do contrato caiu de **123× para 1,3×**, sem afrouxar a proteção no
pior caso.

### Dois números que **não** melhoraram, e estão escritos assim

- **O ganho de 34 % da fonte de aleatoriedade por thread não se reproduz ponta a ponta.** Medido em
  pares alternando os builds pela porta HTTP, o efeito fica dentro do ruído.
- **Duas réplicas na mesma máquina de 4 núcleos entregam menos que uma** (90 contra 174 req/s). Não
  é falha da correção: é aritmética. Replicar adiciona capacidade em máquinas **diferentes**.

---

## 7. Pendências de decisão humana

**Nada aqui é defeito a corrigir.** São escolhas que engenharia não deve fazer sozinha, ou ações
fora do alcance desta credencial. Estão separadas do que foi resolvido justamente para que ninguém
as confunda com trabalho pendente de código.

> **Nota sobre a numeração:** as pendências foram numeradas **por etapa**, e três séries distintas
> reusam os mesmos números — `01b-correcao-testes.md` (P1–P10, estendida até P20),
> `02b-correcao-seguranca.md` (P1–P3, série própria) e `docs/revisao-ag/` (série anterior, da revisão
> do algoritmo). **Cite sempre o documento junto do número.** Consolidar as três numa série única
> quebraria as referências cruzadas já escritas em dezenas de lugares, inclusive em comentários de
> código.

### 7.1 Ação humana imediata — alto retorno, custo de minutos

| Ação | Onde | Por que está aqui |
|---|---|---|
| **Tornar o gate de CI um bloqueio de merge** | `Settings → Branches` | O workflow roda e passa, mas marcar o check como obrigatório é configuração de repositório: a API respondeu **403** para esta credencial. Passo a passo em `01b` §4.5 (P8) |
| **Ligar o Dependabot** | `Settings → Code security` | A consulta à API confirmou que está **desligado**. É a ação de maior alavancagem que resta em segurança (`02b` P3) |

### 7.2 Decisão de produto e jurídico

| Decisão | Contexto |
|---|---|
| **Base legal para dado psicológico nominal · PRIORIDADE ALTA** | Estado psicológico autodeclarado coletado junto com o nome, e público que inclui adolescentes (art. 14 da LGPD). Faltam também política de privacidade, aviso de tratamento e registro de operações. Recomendação: tratar como dado sensível até que alguém com competência jurídica diga o contrário (`02b` P1) |
| **A promessa que o SINAPSE faz ao aluno** | *"Cobrimos o edital inteiro"* implica adotar piso de cobertura e recusar editais que não caibam no prazo; *"otimizamos sua nota esperada"* implica que sacrificar disciplinas de baixo peso sob orçamento apertado é o comportamento pretendido. **Nenhum peso da fitness deve ser ajustado antes dessa resposta** (`docs/revisao-ag/06-limite-troca-pesos.md`) |
| **As quatro descobertas de domínio** | Ramo de abandono inalcançável e curva por cronotipo descartada; valor de carga cognitiva inalcançável; pai vazio descartando o outro pai. Preservadas exatamente; corrigi-las muda comportamento do produto (`04b` P11, P12, P13) |
| **Tratadores de erro sem caminho HTTP** | Existem tratadores testados que nenhuma requisição alcança. Remover ou tornar alcançáveis é decisão sobre a API futura (`01b` P6) |
| **O piso de dias por disciplina** | É o que faz um edital de 50 disciplinas ser recusado com 422. Ancorado em `BaselineCalculator:33` (`revisao-ag` P5) |

### 7.3 Decisão de infraestrutura e implantação

| Decisão | Contexto |
|---|---|
| **Terminação TLS de verdade** | O enforcement está implementado e desligado por padrão. Sem proxy real, o dado trafega em claro (`02b` P2) |
| **Credencial real para o coletor de métricas** | Hoje é o usuário `user` com senha gerada a cada inicialização. Não há implantação (`05b`/`06b` P19) |
| **Três dependências de linha maior** | Spring Boot 4.1.1, springdoc-openapi 3.1.0 e logstash-logback-encoder 9.0. Cada uma com o motivo de não ter sido forçada sem plano de teste (`04b` P14, P15, P16) |

### 7.4 Trabalho técnico registrado para uma próxima rodada

| Item | Por que não foi feito |
|---|---|
| **Pool próprio para a avaliação de fitness** (P17) | Os dois níveis de paralelismo precisam ser dimensionados em conjunto — arquitetura de concorrência, não ajuste local |
| **Cromossomo como vetor indexado** (P18) | É onde está o custo real do cruzamento. Muda o tipo de domínio no sistema inteiro e **altera o resultado** |
| **Injetar um `Clock`** (`revisao-ag` P7) | `LocalDate.now()` dentro da lógica de produção; ancorado em `EvolutionContextAssembler:73` |
| **Bloco de log `TRACE` descoberto** (`01b` P9) | Uma linha que só executa com `TRACE` ligado pode quebrar no dia em que alguém ligar |
| **Asserções de tempo de parede** (`01b` P2, P4) | Trocar tempo por contagem de operações exige instrumentar o motor |

### 7.5 Pendências que foram **fechadas** durante a varredura

Registradas aqui para que ninguém as reabra por engano:

| Pendência | Fechada em |
|---|---|
| `revisao-ag` **P6** — inviabilidade saía como 400, não 422 | etapa 03d, com ADR-0005 e teste |
| `01b` **P1** — `RandomProvider` inseguro para execução paralela | etapa 06b: a fonte passou a ser por thread, e o limite conhecido da extensão de isolamento deixou de existir |
| `06b` **P20** — limite de taxa por custo | etapa 06b, ao final |
| `01b` **P10** — nenhum teste de concorrência sobre o executor | etapa 06b: contrato de sobrecarga, dimensionamento do pool e testes de carga |

---

## 8. Mudanças de comportamento visíveis ao cliente

Poucas, e todas deliberadas:

| Mudança | Etapa |
|---|---|
| Inviabilidade de edital passou de **400** para **422** | 03d |
| Quatro códigos de status que a API já devolvia passaram a ser **documentados** no OpenAPI | 04b |
| Sobrecarga passou de **500** para **503** com `Retry-After` | 06b |
| **Novos endpoints** `POST /jobs` e `GET /jobs/{id}` (o síncrono continua idêntico) | 06b |
| Limite de taxa passou de **5 requisições/min** para **650 fichas/min** | 06b |
| `/actuator/health` passou a ser público; `/actuator/prometheus` exige credencial e agora **responde** | 06b |
| Respostas passaram a ser **comprimidas** | 06b |

---

## 9. Índice dos documentos

| # | Documento | Área |
|---|---|---|
| 00 | [`00-inventario.md`](./00-inventario.md) | ponto de partida |
| 01 · 01b | [diagnóstico](./01-diagnostico-testes.md) · [correção](./01b-correcao-testes.md) | testes |
| 02 · 02b | [diagnóstico](./02-diagnostico-seguranca.md) · [correção](./02b-correcao-seguranca.md) | segurança |
| 03 · 03b · 03d · 03e | [diagnóstico](./03-diagnostico-estrutura.md) · [correção](./03b-correcao-estrutura.md) · [contrato de erro](./03d-correcao-contrato-de-erro.md) · [responsabilidades](./03e-correcao-responsabilidades.md) | estrutura |
| 04 · 04b | [diagnóstico](./04-diagnostico-escrita.md) · [correção](./04b-correcao-escrita.md) | escrita e manutenibilidade |
| 05 · 05b | [diagnóstico](./05-diagnostico-performance.md) · [correção](./05b-correcao-performance.md) | performance |
| 06 · 06b | [diagnóstico](./06-diagnostico-escalonamento.md) · [correção](./06b-correcao-escalonamento.md) | escalonamento |

Trabalho anterior, já entregue na release 4.0: [`docs/revisao-ag/`](../revisao-ag/README.md) —
revisão da função de fitness do algoritmo genético.

---

## 10. Como verificar

```bash
mvn clean verify        # 288 testes, 0 falhas; o gate de cobertura reprova abaixo do piso medido
```

O gate de cobertura tem uma armadilha documentada no `pom.xml`, ao lado do limite: **o JaCoCo trunca
a razão antes de comparar, não arredonda.** Um piso tirado de um valor arredondado reprova um build
que não regrediu, e o sintoma parece instabilidade.

Os testes de estado compartilhado sobem um **Redis real, em processo** — não exigem Redis instalado
nem contêiner, e não são pulados em nenhum ambiente.

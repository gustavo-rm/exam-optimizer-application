# Release 5.0 — Varredura de qualidade em seis áreas

Este documento é a descrição do pull request de `release/v5.0` para `main`. Ele é escrito para quem
**não** acompanhou as catorze etapas da varredura.

---

## O que é este PR

O SINAPSE monta planos de estudo com um algoritmo genético. A release anterior (4.0) revisou a
**função de fitness** — a qualidade das decisões do algoritmo. Este PR revisa tudo em volta dela:
**testes, segurança, estrutura, legibilidade, performance e escalonamento**, nessa ordem, porque
cada área depende de a anterior estar resolvida.

São **39 commits e 197 arquivos**, dos quais 104 em `src/main`. A maior parte do volume é
documentação: quinze relatórios que registram o que foi medido, com os números que sustentam cada
decisão.

**Suíte: 288 testes, 0 falhas. Cobertura: 92,44 % de instruções, 74,91 % de ramos.**

A regra valeu do começo ao fim: **nenhum achado entrou num relatório sem número medido, e nenhuma
correção entrou sem antes/depois pela mesma metodologia.** Onde a medição contradisse a expectativa,
o relatório traz a contradição, não a expectativa.

---

## Por onde começar

| Se você quer… | Leia |
|---|---|
| O estado do sistema em uma tela | [`docs/qualidade/README.md`](./README.md) |
| **O que espera decisão sua** | [`README.md` §7](./README.md#7-pendências-de-decisão-humana) |
| As mudanças visíveis ao cliente | [`README.md` §8](./README.md#8-mudanças-de-comportamento-visíveis-ao-cliente) |

---

## 1. O ponto de partida

O inventário (etapa 00) encontrou um serviço Spring Boot de módulo único, um endpoint de negócio,
sem banco de dados e sem implantação. E encontrou lacunas que não apareciam em nenhum painel:

- **73 testes, e nenhum deles recebia uma resposta 200.** Os dois testes de API existentes enviavam
  cargas inválidas e eram rejeitados antes de o controlador executar. Todo o caminho entre receber o
  pedido e devolver o plano tinha **zero instruções cobertas**.
- **Zero integração contínua, zero lint, zero varredura de dependências.**
- **Dois defeitos de produção que ninguém via:** a documentação da API estava fora do ar (`500`) e o
  projeto **não gerava jar executável** — o artefato de implantação descrito no README não chegava a
  existir.

---

## 2. As seis áreas, em resumo

### Testes — de "passa" para "protege"

O fluxo de sucesso foi de 0 % a 100 % nos dois métodos de maior risco; os nove tratadores de erro
foram a 100 %; e o repositório ganhou seu primeiro gate de CI, **verificado nos dois sentidos** —
sabotagens provaram que ele reprova por teste quebrado e por queda de cobertura.

Uma descoberta que mudou como o resto da varredura foi feito: remover testes individuais de duas
classes deixou a cobertura **idêntica**, porque os outros testes da mesma classe cobriam as mesmas
linhas. *Um piso de cobertura mede o que foi executado, nunca o que foi verificado.*

### Segurança — uma burla real, fechada

O limite de taxa **era burlável**: com `X-Forwarded-For` vindo do próprio cliente, bastava variar o
cabeçalho a cada requisição para trocar de balde e anular o limite. A resolução do endereço passou a
confiar apenas em proxy declarado. O endereço do cliente deixou de sair inteiro no log.

O que **não** foi decidido aqui está em §4: a coleta de estado psicológico nominal é questão de
LGPD, não de engenharia.

### Estrutura — ciclos, responsabilidades e o contrato de erro

Um ciclo de dependência entre módulos foi desfeito e travado por um teste de arquitetura — que
**pegou um ciclo novo** introduzido por engano na última etapa deste mesmo PR. Um
`GlobalExceptionHandler` de 447 linhas e 16 responsabilidades virou três conselhos por natureza do
erro. E o contrato passou a distinguir **400 de 422**: um edital que não cabe no prazo é um pedido
bem-formado que o domínio não pode atender, e agora diz isso.

### Escrita e manutenibilidade

| | Antes | Depois |
|---|---:|---:|
| Ferramenta de estilo | nenhuma | Checkstyle na fase `validate` |
| Violações de estilo | 213 | **0** |
| Métodos com complexidade ≥ 10 | 1 | **0** |
| Status HTTP documentados no OpenAPI | 5 de 9 reais | **9 de 9** |
| Spring Boot | 3.5.5 (22 releases atrás) | **3.5.16** |

**Zero `TODO`/`FIXME` não era boa notícia:** o débito existia em 66 itens espalhados por documentos.
Uma tabela de rastreamento ainda listava como aberta uma pendência fechada duas etapas antes.

### Performance — metade do pior caso, com o resultado idêntico

| Cenário | Antes | Depois |
|---|---:|---:|
| 15 disciplinas / 100 gerações / população 50 | 29 ms | **13 ms** |
| 24 disciplinas / 1000 gerações / população 500 | 2 349 ms | **1 163 ms** |

O maior ganho teve a causa mais simples: a fonte de aleatoriedade era um `SecureRandom` — gerador
criptográfico — usado para sortear **simulação**, custando ~31 % de todo o tempo de CPU.

**Nove assinaturas de referência** — o plano completo para uma semente fixa, disciplina por
disciplina, mais a fitness com 12 casas — foram capturadas antes da primeira otimização e conferidas
depois de cada uma. **São idênticas do começo ao fim.** É isso que dá sentido a "não mudou
comportamento".

### Escalonamento — replicar sem quebrar

O achado mais grave não precisava de crescimento para aparecer: **o limite de taxa vivia na memória
de cada réplica**, e com N réplicas o limite declarado virava N × o valor configurado, em silêncio.
Demonstrado com dois processos reais: **10 requisições aceitas onde a política declara 5**. Agora
são 5.

Sob 120 pedidos pesados simultâneos, **as 66 respostas 500 (55 % da carga) viraram zero**, e a fila
cheia passa a responder `503` com `Retry-After`. As requisições bem-sucedidas **subiram 71 %**:
recusar cedo libera a máquina para terminar trabalho.

Um pedido pesado deixou de segurar a conexão por 1 527 ms — agora é aceito em **24 ms** com um
identificador, e o resultado pode ser buscado **em outra réplica**.

---

## 3. O que este PR **não** faz

- **Não altera os pesos da fitness** nem o piso de dias mínimos. Dependem de uma decisão de produto
  aberta desde a release 4.0 (§4).
- **Não substitui o endpoint síncrono.** `POST /generate` continua **byte a byte idêntico** no
  contrato OpenAPI; o fluxo assíncrono foi acrescentado ao lado.
- **Não exige Redis.** O padrão é uma instalação de uma réplica sem infraestrutura adicional — mas
  o modo local agora **anuncia no log** o que quebra se alguém replicar.
- **Não resolve as quatro descobertas de domínio** listadas em §4. Foram preservadas exatamente.

---

## 4. Pendências de decisão humana

**Nada aqui é defeito a corrigir.** São escolhas que engenharia não deve fazer sozinha, ou ações
fora do alcance da credencial usada.

### Dois cliques, alto retorno

| Ação | Onde |
|---|---|
| **Tornar o gate de CI um bloqueio de merge** | `Settings → Branches` — o workflow roda e passa, mas marcar o check como obrigatório é configuração de repositório, e a API respondeu **403** |
| **Ligar o Dependabot** | `Settings → Code security` — a consulta à API confirmou que está desligado |

### Produto e jurídico

- **Base legal para dado psicológico nominal · PRIORIDADE ALTA.** O contrato coleta estado
  psicológico autodeclarado **junto com o nome do estudante**, e o público de concursos e
  vestibulares frequentemente **inclui adolescentes** (art. 14 da LGPD). Recomendação: tratar como
  dado sensível até que alguém com competência jurídica diga o contrário.
- **A promessa que o SINAPSE faz ao aluno.** *"Cobrimos o edital inteiro"* implica adotar piso de
  cobertura e recusar editais que não caibam no prazo; *"otimizamos sua nota esperada"* implica que
  sacrificar disciplinas de baixo peso é o comportamento pretendido. **Nenhum peso deve ser ajustado
  antes dessa resposta.**
- **Quatro descobertas de domínio.** Um ramo de risco de abandono é inalcançável e 25 linhas de
  curva por cronotipo são computadas e descartadas — o código sugere que o cronotipo importa; ele
  não importa. Mais dois casos análogos, preservados exatamente.

### Infraestrutura

Terminação TLS de verdade; credencial real para o coletor de métricas; e três dependências de linha
maior (Spring Boot 4.1.1, springdoc 3.1.0, logstash-logback-encoder 9.0), cada uma com o motivo de
não ter sido forçada sem plano de teste.

**A lista completa, com contexto, está em [`README.md` §7](./README.md#7-pendências-de-decisão-humana).**

---

## 5. Mudanças visíveis ao cliente

| Mudança | Nota |
|---|---|
| Inviabilidade de edital: **400 → 422** | pedido bem-formado que o domínio não pode atender |
| Sobrecarga: **500 → 503** com `Retry-After` | 500 dizia ao balanceador que a réplica estava quebrada |
| **Novos endpoints** `POST /jobs` e `GET /jobs/{id}` | o síncrono continua idêntico |
| Limite de taxa: **5 requisições/min → 650 fichas/min** | passa a cobrar por custo; um pedido típico custa 1 ficha, o mais caro custa 129 |
| `/actuator/health` público; `/actuator/prometheus` autenticado e **funcionando** | antes os dois respondiam 403 permanente, sem forma de apresentar credencial |
| Respostas **comprimidas** | 36,3 KB → 2,4 KB |
| Quatro status que a API já devolvia passaram a ser **documentados** | o OpenAPI descrevia 5 de 9 |

---

## 6. Três erros cometidos e corrigidos durante o trabalho

Registrados porque cada um teria passado despercebido, e porque mostram o que a rede de testes pegou:

| Erro | Como apareceu |
|---|---|
| `@Async` num método chamado de outro método do **mesmo bean** — autoinvocação passa por fora do proxy, e o trabalho rodava na thread da requisição | **a medição**: o envio levava os mesmos 1,5 s do caminho síncrono |
| Um ciclo `service → api → service` introduzido ao criar o fluxo assíncrono | **`ModuleBoundaryTest`**, criado na etapa de estrutura, reprovou |
| Cobrar o **máximo** por corpo ilegível no limite por custo | **a suíte**: 14 testes reprovaram. Um corpo que não pode ser precificado também não pode ser executado — cobrar caro puniria quem tem defeito de integração |

---

## 7. Como verificar

```bash
mvn clean verify        # 288 testes, 0 falhas
```

O gate de cobertura reprova abaixo do piso medido. Há uma armadilha documentada no `pom.xml`, ao
lado do limite: **o JaCoCo trunca a razão antes de comparar, não arredonda** — um piso tirado de um
valor arredondado reprova um build que não regrediu.

Os testes de estado compartilhado sobem um **Redis real, em processo**: não exigem Redis instalado
nem contêiner, e não são pulados em nenhum ambiente.

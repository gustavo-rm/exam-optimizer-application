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

**Suíte: 311 testes, 0 falhas. Cobertura: 93,25 % de instruções, 78,61 % de ramos.**

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

*(idêntico nesta etapa; a pendência P18, resolvida depois, mudou o plano produzido — ver adiante)*

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

### Duas pendências técnicas retomadas no fechamento — e a única mudança de resultado do PR

As duas estavam registradas para "uma próxima rodada" e foram resolvidas antes de fechar.

**P17 — a paralelização da fitness foi removida, não redimensionada.** A pendência propunha dar à
avaliação de fitness um pool próprio. Antes de escolher o tamanho, foi medido quanto ela ainda
comprava: **nada**. No maior tamanho de população que o contrato aceita, cinco baterias pareadas
deram +1,4 % vencendo 3 de 5 — ruído; em populações menores a versão paralela chegou a ser 11 % mais
lenta. Uma correção anterior (a troca do percurso dos genes) tinha tornado a avaliação ~4,5× mais
barata por indivíduo, e com tão pouco trabalho o custo de repartir entre threads passou a consumir a
economia. Ponta a ponta: **+27 % de vazão com 4 concorrentes**, resultado idêntico.

**P18 — o cromossomo virou um vetor indexado.** O plano de estudos deixou de ser um mapa de
disciplina para dias e passou a ser um vetor de inteiros alinhado a uma ordem única, compartilhada
por toda a população. Some, a cada gene, um cálculo de hash, um desembrulho de objeto e uma alocação
de memória; os dados por disciplina que a avaliação consulta gene a gene passaram a ser vetores
calculados uma vez por otimização. Ponta a ponta, com o mesmo corpo de requisição nos dois lados e
medição alternada:

| Requisições simultâneas | Latência antes | Latência depois | Vazão antes | Vazão depois |
|---:|---:|---:|---:|---:|
| 1 | 91 / 98 ms | **35 / 35 ms** | 11,0 / 10,2 req/s | **28,8 / 28,4 req/s** |
| 4 | 105 / 104 ms | **51 / 43 ms** | 38,2 / 38,6 req/s | **77,8 / 92,3 req/s** |
| 16 | 265 / 261 ms | **112 / 108 ms** | 60,4 / 61,3 req/s | **142,6 / 148,7 req/s** |

Latência mediana **~63 % menor**, vazão **~2,4× maior**, zero erros.

> ### ⚠️ Esta é a única mudança de resultado do PR
>
> **O plano de estudos produzido mudou.** A ordem dos genes decide onde cai o ponto de corte do
> cruzamento; antes ela era a ordem de iteração de um `HashMap` — determinística na prática, mas
> **não especificada** pela biblioteca padrão do Java e livre para mudar numa atualização da
> plataforma. Hoje é a ordem em que o cliente declara as disciplinas no edital. O plano de um mesmo
> aluno com os mesmos dados pode sair diferente do que saía na 4.0.
>
> Em troca, o resultado deixou de depender de algo fora do nosso controle.
>
> Como a instrução era resolver a pendência e não preservar o resultado, a verificação mudou de
> *"idêntico"* para *"reprodutível e sem perda de qualidade"*. As duas foram medidas:
>
> - **Reprodutibilidade:** 150 de 150 execuções deram fitness idêntico entre duas baterias
>   completas; três execuções da assinatura de referência saíram iguais entre si.
> - **Qualidade:** comparando a nota final da mesma semente nas duas versões ao longo de **150
>   pares** (5 configurações × 30 sementes), **62 vitórias contra 52 derrotas** nos 114 pares não
>   empatados. Teste dos sinais: z = +0,94, longe dos 1,96 que indicariam diferença a 5 %. Toda a
>   dispersão cabe em **±0,13 %**.
>
> É o que se espera de uma renumeração de posições: outro caminho de busca, mesma qualidade de
> chegada.

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
| **O plano de estudos produzido mudou** | mesma qualidade medida, outra ordem de genes; ver o aviso em §2 |

---

## 6. Cinco erros cometidos e corrigidos durante o trabalho

Registrados porque cada um teria passado despercebido, e porque mostram o que a rede de testes pegou:

| Erro | Como apareceu |
|---|---|
| `@Async` num método chamado de outro método do **mesmo bean** — autoinvocação passa por fora do proxy, e o trabalho rodava na thread da requisição | **a medição**: o envio levava os mesmos 1,5 s do caminho síncrono |
| Um ciclo `service → api → service` introduzido ao criar o fluxo assíncrono | **`ModuleBoundaryTest`**, criado na etapa de estrutura, reprovou |
| Cobrar o **máximo** por corpo ilegível no limite por custo | **a suíte**: 14 testes reprovaram. Um corpo que não pode ser precificado também não pode ser executado — cobrar caro puniria quem tem defeito de integração |
| Uma bateria inteira de carga medindo **o servidor errado**: o roteiro de parada casava pelo nome do arquivo `.jar`, e as cópias comparadas tinham outro nome, então o servidor "antes" sobreviveu e atendeu as quatro rodadas | **os próprios números**: saíram suspeitosamente iguais. O roteiro passou a casar pela flag de lançamento, a só devolver quando a porta estiver livre, e a conferir antes de cada rodada quem está atendendo |
| Otimizar `MinimumDaysConstraint.isValid` alegando que ele roda uma vez por gene, por indivíduo, por geração | **o relatório de cobertura**: o método inteiro aparecia sem execução. Nada o chama — a avaliação usa só `violationSeverity`. A versão simples voltou |

---

## 7. Como verificar

```bash
mvn clean verify        # 311 testes, 0 falhas
```

O gate de cobertura reprova abaixo do piso medido. Há uma armadilha documentada no `pom.xml`, ao
lado do limite: **o JaCoCo trunca a razão antes de comparar, não arredonda** — um piso tirado de um
valor arredondado reprova um build que não regrediu.

Os testes de estado compartilhado sobem um **Redis real, em processo**: não exigem Redis instalado
nem contêiner, e não são pulados em nenhum ambiente.

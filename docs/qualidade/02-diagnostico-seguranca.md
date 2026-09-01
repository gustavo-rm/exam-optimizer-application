# Etapa 02 — Diagnóstico de segurança

**Escopo:** só segurança. Estrutura, escrita, performance e escalonamento continuam para as etapas
seguintes.

**Esta etapa não corrige nada.** Todo achado está registrado com arquivo e linha, para que a decisão
sobre o que corrigir e em que ordem seja tomada com o mapa na mão.

| | |
|---|---|
| **Data** | 2026-09-01 |
| **Commit** | `9e34e0f` |
| **Branch** | `release/v4.0` |
| **Base** | [`00-inventario.md`](./00-inventario.md), [`01b-correcao-testes.md`](./01b-correcao-testes.md) |

### Vocabulário usado neste documento

- **Dado pessoal** — qualquer informação que identifique ou torne identificável uma pessoa natural
  (LGPD, art. 5º I). **Dado pessoal sensível** é a categoria com proteção reforçada (art. 5º II), que
  inclui dado referente à saúde.
- **IDOR** (*Insecure Direct Object Reference*) — quando o sistema aceita um identificador enviado
  pelo cliente e devolve o recurso correspondente **sem checar** se aquele recurso pertence a quem
  pediu. Trocar `id=1` por `id=2` e ver o dado de outra pessoa.
- **SCA** (*Software Composition Analysis*) — análise que cruza a lista de bibliotecas usadas contra
  bases públicas de vulnerabilidades conhecidas.
- **CVE / GHSA** — identificadores públicos de uma vulnerabilidade específica.
- **CORS** (*Cross-Origin Resource Sharing*) — mecanismo pelo qual um servidor autoriza páginas de
  outros domínios a chamarem sua API pelo navegador. Configuração permissiva demais permite que
  qualquer site chame a API em nome do visitante.
- **HSTS** (*HTTP Strict Transport Security*) — cabeçalho que instrui o navegador a nunca mais
  acessar aquele domínio por HTTP puro.
- **Path traversal** — usar `../` numa entrada que vira caminho de arquivo para escapar do diretório
  pretendido.

---

## Nota de escopo, verificada mais uma vez

A tarefa menciona *Integration Gateway* e "material enviado" (upload de arquivos). Refiz a
verificação antes de escrever:

| Verificação | Resultado |
|---|---|
| `Integration Gateway` / `gateway` no repositório | **0 arquivos** |
| Upload de arquivo (`MultipartFile`, `@RequestPart`, `multipart`) | **0 ocorrências** |
| Escrita ou leitura de arquivo (`Files.`, `new File`, `Paths.get`, `FileInputStream`) | **0 ocorrências** |
| Chamada a LLM ou provedor de IA (`openai`, `anthropic`, `claude`, `gpt`, `gemini`, `bedrock`, `langchain`, …) | **0 ocorrências** |
| Persistência (`jdbc`, `jpa`, `@Entity`, `Repository`, `mongo`, `redis`, `s3`, `datasource`) | **0 ocorrências** |
| Artefatos implantáveis | **1** |
| Endpoints HTTP de negócio | **1** (`POST /api/v1/optimizer/generate`) |

**Não existe gateway, não existe upload de material e não existe chamada a LLM.** O sistema é uma
API sem estado que recebe um payload JSON, calcula em memória e devolve o resultado. Isso muda
radicalmente o perfil de risco — para melhor em quase tudo, e o relatório reflete isso em vez de
listar riscos genéricos que não se aplicam.

---

## 1. Dado pessoal manipulado pelo sistema

### 1.1 O que entra

Todo dado pessoal chega por um único caminho: o corpo do `POST /api/v1/optimizer/generate`.

| Campo | Arquivo | Natureza |
|---|---|---|
| `studentProfile.name` | `api/dto/StudentProfileDto.java:25` | **Identificação direta** — nome do estudante, até 100 caracteres |
| `studentProfile.knowledgeGaps` | `api/dto/StudentProfileDto.java:29` | **Desempenho acadêmico** — autoavaliação de 1,0 a 5,0 por disciplina. É um mapa de fraquezas declaradas |
| `studentProfile.weeklyAvailability` | `api/dto/StudentProfileDto.java:33` | **Rotina** — horas disponíveis por dia da semana. Revela padrão de vida (trabalha? estuda à noite? fins de semana livres?) |
| `state.stressLevel` | `api/dto/StudentStateDto.java:21` | **Estado psicológico** — nível de estresse de 1,0 a 5,0 |
| `state.fatigueLevel` | `api/dto/StudentStateDto.java:27` | **Estado físico/psicológico** — nível de fadiga |
| `state.motivationLevel` | `api/dto/StudentStateDto.java:33` | **Estado psicológico** — nível de motivação |
| `state.chronotype` | `api/dto/StudentStateDto.java:36` | **Ritmo biológico** — matutino, intermediário, vespertino |
| Endereço IP do cliente | `api/controller/RateLimitingFilter.java:86-92` | **Dado pessoal** — coletado implicitamente, não declarado no contrato |

**Sobre a classificação de estresse, fadiga e motivação.** Não vou afirmar categoricamente que são
"dado sensível" no sentido do art. 5º II da LGPD — essa é uma decisão jurídica, não de engenharia.
O que registro como fato técnico é: **são medidas autodeclaradas de estado psicológico e físico,
coletadas nominalmente** (junto com o nome), e a categoria mais próxima na lei é "dado referente à
saúde". Quem responder a essa pergunta deve olhar também o público: quem presta concurso e vestibular
inclui **adolescentes**, e a LGPD tem regime próprio para dado de criança e adolescente (art. 14).
**Recomendo tratar como sensível até que alguém com competência jurídica diga o contrário** — o custo
de errar para o lado cauteloso é baixo.

### 1.2 Onde é armazenado

**Em lugar nenhum.** Verificado: não há banco, não há cache persistente, não há gravação em disco. O
dado vive na memória do processo pela duração de uma requisição (limitada a 30 segundos pelo
`orTimeout` em `api/controller/OptimizerController.java:85`) e é descartado.

**Isto é a maior força de segurança do sistema** e merece ser dito explicitamente: não há base de
dados de estudantes para vazar, não há backup para esquecer criptografado, não há retenção a
justificar perante a LGPD. Qualquer proposta futura de "salvar o histórico do aluno" muda o perfil de
risco por completo e deveria passar por avaliação própria.

Uma exceção parcial: o `RateLimitingFilter` mantém um cache Caffeine **em memória** com até 10.000
endereços IP, com expiração por inatividade de 60 minutos
(`api/controller/RateLimitingFilter.java:52-55`). É retenção de dado pessoal — curta, volátil e por
instância, mas existe e não está documentada em lugar nenhum.

### 1.3 Onde é transmitido

| Destino | Situação |
|---|---|
| Cliente HTTP → API | Único fluxo de entrada. **Sem TLS obrigatório** — ver seção 6 |
| API → qualquer serviço externo | **Não existe.** Zero clientes HTTP, zero filas, zero LLM |
| API → agregador de logs | **Existe, e leva dado pessoal** — ver 1.4 |

O nome do estudante **não volta na resposta**: verifiquei os DTOs de saída
(`PlannerResponseDto`, `OptimizationResultDto`, `ScheduleResultDto`, `StudyPlanDto`,
`StudyBlockDto`) e nenhum carrega identificação. A resposta contém só o plano, por nome de
disciplina. É uma boa propriedade, provavelmente acidental, e vale preservá-la deliberadamente.

### 1.4 Onde é registrado em log — os achados

Esta é a área com problema real. O `logback-spring.xml` emite **JSON estruturado** com
`LogstashEncoder`, explicitamente preparado para ingestão em ELK ou Datadog (`README.md`, seção
"Logging and Monitoring"). Ou seja: **o que entra no log sai do processo e vai para um sistema de
terceiros, onde é indexado e retido**.

Inventariei os 18 pontos de log de `src/main`. Três levam dado pessoal.

---

**S1 — O endereço IP do cliente vai para o log a cada bloqueio por limite de requisições.**

`api/controller/RateLimitingFilter.java:70` constrói a exceção com o IP embutido na mensagem, e
`api/exception/GlobalExceptionHandler.java:167` registra essa mensagem em nível `WARN`.

Capturado em execução real:

```json
{"timestamp":"2026-09-01T22:35:37.736Z","message":"Rate limit exceeded on path
 /api/v1/optimizer/generate: Rate limit exceeded for IP: 10.0.0.99",
 "level":"WARN","traceId":"6a9753390e6554095cb56a72c2ee3caf", ...}
```

O IP é dado pessoal e vai para o agregador sem qualquer mascaramento, sem política de retenção
declarada e sem que o titular saiba que está sendo coletado.

---

**S2 — Valores enviados pelo estudante entram no log de ERRO quando a desserialização falha.**

Quando o JSON tem um valor de tipo incompatível, o Jackson lança `HttpMessageNotReadableException`.
Não há tratador específico para ela, então cai no `handleAllUncaughtException`
(`api/exception/GlobalExceptionHandler.java:204`), que registra **a pilha completa** em nível
`ERROR`. E a mensagem do Jackson **repete o valor recusado**.

Capturado em execução real (valor sublinhado por mim):

```
Cannot deserialize value of type `java.lang.Double` from String "ISTO_NAO_E_NUMERO":
not a valid `Double` value
 ... (through reference chain: OptimizationRequest["studentProfile"]
      ->StudentProfileDto["knowledgeGaps"]->LinkedHashMap["Portugues"])
```

**Uma atenuação importante, medida e não suposta:** a mesma linha traz
`at [Source: REDACTED ('StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION' disabled)]`. O Jackson **não**
despeja o payload inteiro — esse comportamento vem desligado por padrão nas versões recentes. O que
vaza é **o valor específico que falhou**, mais o caminho do campo. É menos grave do que "o corpo
inteiro no log", e mais grave do que "nada".

---

**S3 — O defeito de mapeamento P5 leva a autoavaliação do estudante para o log de ERRO.**

A pendência **P5** de `01b-correcao-testes.md` registrou que `StudentProfileMapper` deixa virar chave
nula qualquer lacuna que cite disciplina fora do edital, e que duas delas colidem em
`IllegalStateException`. O que a etapa 01b não perseguiu — porque era etapa de testes — é **o que
essa exceção carrega**. Medido com sonda direta contra as classes compiladas:

```
java.lang.IllegalStateException: Duplicate key null (attempted merging values 4.5 and 2.0)
```

Os números `4.5` e `2.0` são **as notas de autoavaliação do estudante**. A mensagem sobe pelo
`handleAllUncaughtException` e é registrada em `ERROR` com pilha completa, indo para o agregador.

Ou seja: um estudante que digite o nome de uma disciplina com grafia diferente da do edital provoca
a publicação das próprias notas de fraqueza no log — e recebe **500** como resposta, por um erro que
é dele.

---

**O que está correto e vale preservar.** O log de erro de validação **não** inclui valores enviados.
Verificado em execução:

```
"Validation error on path /api/v1/optimizer/generate:
 [InvalidParam[name=gaConfig.populationSize, reason=Population size cannot exceed 500 ...]]"
```

Só nome do campo e mensagem da restrição. É exatamente o comportamento desejado, e é o contraste que
mostra que S2 e S3 são desvios, não a norma da casa.

### 1.5 Consentimento e transparência

Não há, em nenhum lugar do repositório, política de privacidade, aviso de tratamento, base legal
declarada, mecanismo de consentimento ou documento de retenção. O `README.md` descreve a API como
*"open optimization engine"* de acesso público.

**Para dado de estudante com estado psicológico, isso é uma lacuna de conformidade, não só de
documentação.** Registro como achado; a decisão sobre base legal e sobre o que precisa existir é de
quem responde pelo produto, não desta etapa.

---

## 2. Gestão de segredos

**Nenhum segredo encontrado.** Procurei por `password`, `passwd`, `secret`, `api[_-]?key`, `token`,
`credential`, `private key`, blocos `BEGIN RSA`/`BEGIN OPENSSH`, prefixos `aws_` e cabeçalhos
`Authorization` em todos os `.java`, `.properties`, `.yml`, `.yaml`, `.xml` e `.json` versionados,
mais o `pom.xml` e o workflow de CI.

Todas as ocorrências são falsos positivos de duas famílias:

- **`refill-tokens` / `token`** — são fichas do balde do Bucket4j (algoritmo de limite de
  requisições), não credenciais. `application.properties:9`,
  `api/controller/RateLimitingFilter.java:36,42,47,81`.
- **Nomes de classe do Spring Security** — `BadCredentialsException`, `spring-boot-starter-security`.

Também verifiquei:

| Verificação | Resultado |
|---|---|
| Arquivos típicos de segredo versionados (`.env`, `*.pem`, `*.p12`, `*.jks`, `*.key`, `id_rsa`) | **nenhum** |
| Os mesmos, em qualquer ponto do histórico do git (`git log --all --diff-filter=AM`) | **nenhum jamais versionado** |
| O `.gitignore` cobre esses padrões? | **Não** — ver S4 |

**S4 — O `.gitignore` não protege nenhum padrão de segredo.** O arquivo cobre artefatos de build e
diretórios de IDE, e não menciona `.env`, `*.pem`, `*.key`, `*.p12` nem equivalentes. Hoje não há
segredo a proteger, porque o sistema não tem dependência externa autenticada. **No dia em que tiver
— e um agregador de logs já é uma —, a primeira linha de defesa não existe.** É achado preventivo, e
por isso está classificado como baixo na seção 8.

**Um segredo que existe em tempo de execução e não está no código:** com Spring Security no
classpath e nenhum `UserDetailsService` configurado, o Spring Boot gera um usuário `user` com senha
aleatória a cada inicialização e **a imprime no log**, junto com o próprio aviso do framework de que
a configuração é só para desenvolvimento. Observado no log da suíte. Não é um segredo versionado, mas
é uma credencial em log — relacionada a S5.

---

## 3. Autenticação, autorização e IDOR

### 3.1 O mapa completo da superfície

| Rota | Regra | Verificado em execução |
|---|---|---|
| `POST /api/v1/optimizer/generate` | `permitAll()` — `config/SecurityConfig.java:40` | `200` sem credencial |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | `permitAll()` — `config/SecurityConfig.java:38` | `200` sem credencial |
| `/actuator/health` | cai em `anyRequest().authenticated()` | **`403`, corpo vazio** |
| `/actuator/prometheus` | idem | **`403`, corpo vazio** |
| `/actuator`, `/actuator/env`, `/actuator/beans`, `/actuator/configprops`, `/actuator/heapdump`, `/actuator/loggers` | idem, e nem estão expostos | **`403`** |

### 3.2 IDOR: não é aplicável, e a razão importa

**Não há como um usuário acessar dado de outro trocando um ID, porque não existe ID, não existe
usuário e não existe dado armazenado.**

O sistema é sem estado por construção: cada requisição carrega **todo** o dado que precisa e recebe
de volta **apenas** o resultado daquele cálculo. Não há recurso identificável por chave, não há
consulta por identificador, não há sessão. Verifiquei que o único endpoint não aceita nenhum
`@PathVariable` nem `@RequestParam` — só corpo.

Essa é a diferença entre "IDOR não foi encontrado" e "IDOR não é expressável nesta arquitetura". É o
segundo caso. **A conclusão deixa de valer no instante em que o produto ganhar persistência e
histórico por aluno** — e essa é justamente a mudança que a documentação prospectiva da raiz
descreve. Quando isso for feito, autenticação e autorização precisam vir junto, não depois.

### 3.3 Achados

**S5 — `/actuator/health` está inacessível para sonda de infraestrutura.** O
`application.properties:14` declara `management.endpoint.health.probes.enabled=true`, o que só faz
sentido se um orquestrador for consultar `/actuator/health/liveness` e `/readiness`. Mas a regra de
segurança exige autenticação, e a única credencial é a senha gerada aleatoriamente a cada boot.
**Uma sonda de Kubernetes ou de balanceador receberia `403` e concluiria que a aplicação está
morta.** É problema de disponibilidade nascido de configuração de segurança — e o inventário (R2)
registra que não há implantação nenhuma hoje, então ninguém percebeu.

**S6 — O contrato público está exposto sem autenticação.** `/v3/api-docs` e a Swagger UI são
`permitAll()`. Para uma API deliberadamente aberta isso é coerente; vale registrar que o documento
revela toda a estrutura de entrada, incluindo os campos de estado psicológico, para qualquer um.
Baixa severidade dado o desenho atual, mas é decisão que deve ser consciente.

**S7 — `permitAll()` no endpoint de negócio é uma escolha declarada, não um descuido.** O `README.md`
justifica: *"as it functions as an open optimization engine"*. Registro sem julgamento, com um
apontamento: **essa justificativa foi escrita quando o contrato não tinha campos de estado
psicológico.** Vale reavaliar se "motor aberto de otimização" continua descrevendo um endpoint que
recebe nome, autoavaliação de desempenho e nível de estresse.

---

## 4. Validação de entrada e injeção

### 4.1 Injeção: superfície essencialmente inexistente

Busca literal por sinks em todo `src/main/java`:

| Sink | Ocorrências |
|---|---|
| `Runtime.getRuntime`, `ProcessBuilder` | 1 — `config/AsyncConfig.java:36`, `availableProcessors()`, **sem entrada de usuário** |
| `executeQuery`, `PreparedStatement`, `createQuery` | **0** |
| `Paths.get`, `new File`, `FileInputStream`, `Files.` | **0** |
| `ScriptEngine`, `Class.forName`, `eval` | **0** |
| `DocumentBuilder`, `SAXParser`, `XMLReader` | **0** |
| `ObjectInputStream`, `readObject` | **0** |
| `new URL` | **0** |

- **Injeção de SQL: impossível.** Não há banco nem driver JDBC no classpath.
- **Injeção de comando: impossível.** Nenhuma execução de processo.
- **Path traversal: impossível.** Nenhuma entrada vira caminho de arquivo, porque não há E/S de
  arquivo.
- **Desserialização insegura de objeto Java: não aplicável.** A entrada é JSON via Jackson, com tipos
  concretos (`record`), sem tipagem polimórfica habilitada.

`URI.create` aparece 18 vezes, todas em `GlobalExceptionHandler`. Dezessete recebem literais. Uma
recebe `request.getRequestURI()` (`GlobalExceptionHandler.java:57` e equivalentes), que é entrada do
usuário refletida no campo `instance` do corpo de erro. Não é vetor de XSS: a resposta é
`application/problem+json` e o valor é codificado como string JSON. Registro pela completude, com
severidade informativa.

### 4.2 Validação: forte na forma, com dois furos de comportamento

Os limites declarados são bons e visivelmente pensados para conter custo computacional:

| Campo | Limite | Arquivo |
|---|---|---|
| `totalStudyDays` | 1 a 365 | `GaConfigDto.java:16-17` |
| `numGenerations` | 10 a 1000 | `GaConfigDto.java:20-21` |
| `populationSize` | 10 a 500 | `GaConfigDto.java:24-25` |
| disciplinas de conhecimentos gerais | máx. 50 | `ExamDto.java:37` |
| eixos temáticos | máx. 50 | `ExamDto.java:42` |
| disciplinas por eixo | máx. 50 | `ThematicAxisDto.java:29` |
| lacunas declaradas | máx. 100, cada valor entre 1,0 e 5,0 | `StudentProfileDto.java:28-29` |
| disponibilidade | máx. 7 dias, 0 a 24 horas | `StudentProfileDto.java:32-33` |
| nome do aluno / da prova | 100 / 200 caracteres | `StudentProfileDto.java:24`, `ExamDto.java:25` |

**S8 — Erro de formato do cliente vira `500`, com pilha em log de ERRO.** Este é o achado de maior
alcance da seção, porque é **um único defeito com três consequências**.

O `GlobalExceptionHandler` é um `@RestControllerAdvice` que **não estende
`ResponseEntityExceptionHandler`**. Por isso as exceções padrão do Spring MVC para erro de cliente
não têm tratador e caem todas no `@ExceptionHandler(Exception.class)`. Medido em execução:

| Requisição | Status devolvido | Status correto |
|---|---|---|
| JSON com tipo incompatível num campo | **500** | 400 |
| JSON sintaticamente malformado | **500** | 400 |
| `GET` no endpoint (só aceita `POST`) | **500** | 405 |
| `PUT` no endpoint | **500** | 405 |
| `DELETE` no endpoint | **500** | 405 |

As três consequências:

1. **O cliente é informado errado.** Recebe "erro interno" por um erro que é dele, sem indicação do
   que corrigir.
2. **O log de ERRO recebe pilha completa a cada erro de cliente** — que é o veículo de S2 e S3.
3. **Qualquer alerta baseado em taxa de 5xx fica inútil**, porque um varredor automático batendo com
   `GET` gera 5xx em volume.

**S9 — Não há limite de tamanho de requisição configurado.** `application.properties` não define
`server.tomcat.max-http-form-post-size`, `server.max-http-request-header-size` nem equivalente.
Valem os padrões do Tomcat. Os `@Size` do Jakarta Validation só agem **depois** de o corpo ser lido e
desserializado por completo — então um corpo enorme é totalmente processado antes de ser recusado.

**S10 — Lacuna que cita disciplina inexistente não é validada.** `knowledgeGaps` é um mapa de
`String` livre, sem referência cruzada com as disciplinas declaradas no edital. É a origem de P5 e,
por consequência, de S3. Corrigir aqui — recusar com `400` no lugar de deixar virar chave nula —
resolve o vazamento de log e o `500` de uma vez.

---

## 5. Dependências com vulnerabilidades conhecidas (SCA)

### 5.1 O que foi tentado, e por que não há tabela de CVEs

**Não consegui executar a análise neste ambiente, e não vou preencher a seção com CVEs de memória.**
Num relatório de segurança isso seria pior do que a ausência: números plausíveis e não verificáveis
levam a decisão errada.

Fontes tentadas, todas bloqueadas pela política de rede deste ambiente:

| Fonte | Resultado |
|---|---|
| `api.osv.dev` (OSV.dev, base do Google) | `403` no CONNECT — as 86 dependências chegaram a ser parseadas e enfileiradas; só a chamada externa falhou |
| `services.nvd.nist.gov` (NVD, base do NIST) | inalcançável — inviabiliza também o OWASP Dependency-Check |
| `ossindex.sonatype.org` (Sonatype OSS Index) | inalcançável |
| `api.github.com/advisories` (GitHub Advisory Database) | `403` — a sessão só acessa endpoints com escopo de repositório |
| `api.github.com/repos/.../dependabot/alerts` | `403` — **"Dependabot alerts are disabled for this repository"** |

A última linha é ela própria um achado, e confirma o R17 do inventário por evidência direta em vez de
inferência: **o repositório não tem nenhuma varredura de dependência ativa, e o Dependabot está
explicitamente desligado.**

### 5.2 O que consegui apurar

**Um caso concreto e verificado de dependência defasada causando defeito real.** A etapa 01b
descobriu, ao escrever o teste de contrato, que `springdoc-openapi 2.5.0` era binariamente
incompatível com o Spring Framework 6.2 do Spring Boot 3.5.5 (`NoSuchMethodError` em
`ControllerAdviceBean.<init>`), e que por causa disso **toda a documentação da API — Swagger UI
inclusive — respondia `500`**. Já corrigido para `2.8.17`. Vale como prova de que o conjunto de
dependências estava genuinamente velho de um jeito que importava, e não só numericamente.

**Inventário exato das bibliotecas de maior superfície de ataque**, para que a varredura possa ser
feita sem refazer este trabalho (86 artefatos no total: 76 `compile`, 10 `runtime`):

| Biblioteca | Versão em uso |
|---|---|
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.44 |
| `org.springframework:spring-web` / `spring-webmvc` / `spring-core` / `spring-expression` | 6.2.10 |
| `org.springframework.security:spring-security-web` / `-core` / `-config` / `-crypto` | 6.5.3 |
| `com.fasterxml.jackson.core:jackson-databind` / `-core` / `-annotations` | 2.19.2 |
| `ch.qos.logback:logback-classic` / `logback-core` | 1.5.18 |
| `org.apache.logging.log4j:log4j-api` / `log4j-to-slf4j` | 2.24.3 |
| `org.yaml:snakeyaml` | 2.4 |
| `org.hibernate.validator:hibernate-validator` | 8.0.3.Final |
| `io.swagger.core.v3:swagger-core-jakarta` | 2.2.47 |
| `org.webjars:swagger-ui` | 5.32.2 |
| `net.logstash.logback:logstash-logback-encoder` | 7.4 |
| `com.bucket4j:bucket4j-core` | 8.10.1 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.2 |
| `io.micrometer:micrometer-core` | 1.15.3 |

**Defasagem de versão** (Maven Central estava acessível; versões de pré-lançamento ignoradas):

| Dependência direta | Em uso | Última estável | Salto |
|---|---|---|---|
| `spring-boot-starter-parent` e todos os *starters* | 3.5.5 | **4.1.1** | linha maior |
| `spring-security-test` | 6.5.3 | 7.1.1 | linha maior |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | 3.1.0 | linha maior |
| `logstash-logback-encoder` | 7.4 | **9.0** | duas linhas maiores |
| `micrometer-registry-prometheus` | 1.15.3 | 1.17.1 | menor |
| `micrometer-tracing-bridge-brave` | 1.5.3 | 1.7.1 | menor |
| `lombok` | 1.18.38 | 1.18.46 | correção |
| `caffeine` | 3.2.2 | 3.2.4 | correção |

**Defasagem não é vulnerabilidade** — é aumento de probabilidade. A conversão de uma na outra exige a
varredura.

### 5.3 Como rodar, onde a rede permitir

```bash
# Opção A — OWASP Dependency-Check (precisa de chave da API do NVD)
mvn org.owasp:dependency-check-maven:check -DnvdApiKey=<chave>

# Opção B — OSV.dev, sem chave nem instalação. Duas etapas:
mvn dependency:list -DoutputFile=/tmp/deps.txt -DincludeScope=runtime
#   depois, para cada linha "grupo:artefato:jar:versao:escopo", enviar em lote a
#   POST https://api.osv.dev/v1/querybatch  com {"queries":[{"package":
#   {"name":"grupo:artefato","ecosystem":"Maven"},"version":"versao"}, ...]}
#   e detalhar cada id retornado em GET https://api.osv.dev/v1/vulns/<id>

# Opção C — a mais barata de manter: ligar o Dependabot no GitHub
#   Settings → Code security → Dependabot alerts + security updates
```

**A opção C é a de maior alavancagem** e resolve o problema de forma contínua, em vez de pontual.

---

## 6. Exposição de rede

### 6.1 TLS

**S11 — Não há TLS configurado nem exigido em nenhuma camada.** Verificado: nenhum `server.ssl.*` no
`application.properties`, nenhum `requiresChannel(...).requiresSecure()` no `SecurityConfig`, nenhum
cabeçalho HSTS emitido. A aplicação serve HTTP puro na porta 8080.

O desenho pressupõe um proxy terminando TLS à frente — mas o inventário (R1, R2) estabeleceu que
**não existe implantação alguma**: sem Docker, sem manifesto de orquestração, sem CI/CD de deploy. O
procedimento documentado no `README.md` é `java -jar`. **Nesse cenário, nome, autoavaliação de
desempenho e nível de estresse do estudante trafegam em texto plano.**

`server.forward-headers-strategy=framework` está configurado (`application.properties:5`), o que
confirma a intenção de rodar atrás de proxy — mas intenção configurada não é proxy existente.

### 6.2 CORS — verificado, e **não** é permissivo

Este é o oposto do risco esperado. Não há **nenhuma** configuração de CORS no repositório: nem
`@CrossOrigin`, nem `addCorsMappings`, nem `CorsConfiguration`. Sem configuração, o Spring não emite
cabeçalhos de autorização de origem cruzada.

Confirmado em execução, com uma requisição de sondagem de origem estranha:

```
OPTIONS /api/v1/optimizer/generate  Origin: https://site-malicioso.example
  -> status 403, Access-Control-Allow-Origin: null
```

**Nenhum site de terceiros consegue chamar esta API pelo navegador.** Registro como propriedade
positiva, com uma ressalva para o futuro: no dia em que existir um front-end, alguém vai precisar
configurar CORS, e o caminho fácil (`allowedOrigins("*")`) é justamente o errado. Vale deixar
anotado antes que aconteça.

### 6.3 Cabeçalhos de segurança

Medidos numa resposta real:

| Cabeçalho | Valor | Avaliação |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | presente (padrão do Spring Security) |
| `X-Frame-Options` | `DENY` | presente |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` | presente — adequado para resposta com dado pessoal |
| `Pragma` / `Expires` | `no-cache` / `0` | presentes |
| `Strict-Transport-Security` | **ausente** | consequência de S11: sem HTTPS, HSTS não faria sentido |
| `Content-Security-Policy` | ausente | baixo impacto numa API JSON; relevante para a Swagger UI |

### 6.4 Superfície exposta

Um processo, uma porta, um endpoint de negócio. Actuator restrito a `health` e `prometheus`
(`application.properties:13`) e ambos exigindo autenticação. **A superfície é mínima e bem contida**
— o problema não é o que está exposto, é que o que está exposto trafega sem TLS.

---

## 7. Limite de requisições e proteção contra abuso

### 7.1 O que existe

O `RateLimitingFilter` usa Bucket4j sobre cache Caffeine: 5 requisições por minuto por IP no padrão
(`application.properties:8-10`), aplicado **somente** a caminhos que começam com
`/api/v1/optimizer/` (`RateLimitingFilter.java:63`). Cobertura de teste de ponta a ponta acrescentada
na etapa 01b.

A proteção computacional é razoável e visivelmente pensada: tetos de validação em gerações e
população, pool dedicado de 8 threads com fila limitada a 50 e rejeição rápida
(`config/AsyncConfig.java:41-45`), e timeout rígido de 30 segundos
(`OptimizerController.java:85`).

### 7.2 Achados

**S12 — O cabeçalho `X-Forwarded-For` é aceito sem validação, e ele escolhe o balde.**
`RateLimitingFilter.java:86-92` lê o primeiro valor do cabeçalho e usa como chave. Se o cabeçalho
vier, `request.getRemoteAddr()` é ignorado.

Com o serviço exposto diretamente — que é o cenário atual, já que não há proxy —, **qualquer cliente
envia o cabeçalho que quiser e obtém um balde novo a cada requisição**, anulando por completo o
limite. É o caminho mais barato para exaustão de CPU: 500 indivíduos × 1000 gerações é a carga máxima
que a validação permite por requisição, e o pool tem 8 threads.

O comportamento está coberto por teste desde a etapa 01b, mas o teste **documenta** o comportamento
atual; não afirma que ele é seguro.

**S13 — O estado do limite é por instância, em memória.** Cache Caffeine local
(`RateLimitingFilter.java:52-55`). Com N réplicas atrás de um balanceador, o limite efetivo é N × 5
por minuto. O `README.md` promete escalabilidade horizontal ilimitada; as duas afirmações não
convivem. Registrado no inventário como R16; aqui ele reaparece pela ótica de abuso.

**S14 — Sem limite de tamanho de corpo (mesmo que S9).** Antes de a validação agir, o corpo é lido e
desserializado inteiro.

**S15 — Não há proteção contra abuso fora de `/api/v1/optimizer/`.** Swagger UI e `/v3/api-docs` não
passam pelo filtro. São baratos de servir, então o risco é baixo — registro pela completude.

---

## 8. Lista priorizada por severidade real

A ordenação pondera **o que está exposto externamente** e **o que envolve dado pessoal de
estudante**, não a severidade genérica de uma categoria. Um item de "gravidade média" num catálogo
sobe aqui se toca dado nominal de estudante; desce se depende de uma implantação que não existe.

### Nível 1 — Dado pessoal de estudante sai do processo sem proteção

| # | Achado | Onde | Por que é nível 1 |
|---|---|---|---|
| **S11** | **Sem TLS: dado pessoal trafega em texto plano.** Nome, autoavaliação de desempenho e nível de estresse | ausência em `application.properties` e `SecurityConfig` | É o **único** achado que expõe o pacote completo de dado pessoal a qualquer um no caminho de rede. Não há proxy TLS porque não há implantação nenhuma (R1, R2) |
| **S3** | **Autoavaliação do estudante vai para o log de ERRO** via a mensagem `Duplicate key null (attempted merging values 4.5 and 2.0)` | `StudentProfileMapper` + `GlobalExceptionHandler:204` | Dado de desempenho sai do processo para o agregador. Disparado por erro banal de digitação do próprio aluno |
| **S1** | **IP do cliente registrado em log a cada bloqueio**, sem mascaramento nem retenção declarada | `RateLimitingFilter:70` → `GlobalExceptionHandler:167` | Dado pessoal indo para sistema de terceiros, coletado sem o titular saber |
| **S2** | **Valor recusado pelo Jackson vai para o log de ERRO** com o caminho do campo | `GlobalExceptionHandler:204` | Mesmo canal de S3. Atenuado por o Jackson já redigir o payload completo |

### Nível 2 — Proteção pública contornável, e erro de cliente virando erro de servidor

| # | Achado | Onde | Por que é nível 2 |
|---|---|---|---|
| **S12** | **`X-Forwarded-For` não validado anula o limite de requisições** | `RateLimitingFilter:86-92` | Com o serviço exposto direto, o limite é decorativo. Caminho barato para exaustão de CPU |
| **S8** | **Erro de cliente vira `500` com pilha em log**: tipo errado, JSON malformado e método não permitido, todos medidos | `GlobalExceptionHandler` não estende `ResponseEntityExceptionHandler` | Um defeito, três consequências — inclusive ser o veículo de S2 e S3 |
| **S10** | **Lacuna citando disciplina inexistente não é validada** | `StudentProfileDto:29`, `StudentProfileMapper` | Causa raiz de S3. Corrigir aqui fecha o vazamento e o `500` de uma vez |
| **S13** | **Limite de requisições é por instância** | `RateLimitingFilter:52-55` | Vira problema real assim que houver mais de uma réplica |

### Nível 3 — Postura e higiene, sem exploração direta hoje

| # | Achado | Onde | Observação |
|---|---|---|---|
| **S5** | `/actuator/health` inacessível para sonda de infraestrutura (`403`) | `SecurityConfig` × `application.properties:14` | Disponibilidade, não confidencialidade. Só se manifesta quando houver implantação |
| **S9 / S14** | Sem limite de tamanho de corpo de requisição | `application.properties` | Padrões do Tomcat valem; combinado com S12 amplia o custo de abuso |
| **S1.5** | Sem política de privacidade, base legal ou aviso de tratamento | ausência no repositório | Conformidade. Decisão de produto e jurídica, não de engenharia |
| **S7** | `permitAll()` no endpoint de negócio | `SecurityConfig:40` | Escolha declarada — mas feita antes de o contrato ganhar campos de estado psicológico |
| **S6** | Contrato OpenAPI público sem autenticação | `SecurityConfig:38` | Coerente com API aberta; revela a estrutura completa de entrada |
| **S4** | `.gitignore` não cobre padrões de segredo | `.gitignore` | Preventivo: hoje não há segredo a proteger |
| **S15** | Sem limite de requisições fora de `/api/v1/optimizer/` | `RateLimitingFilter:63` | Rotas baratas de servir |

### Nível 4 — Aberto por falta de ferramenta, não por decisão

| # | Achado | Situação |
|---|---|---|
| **S16** | **A varredura de dependências (SCA) não pôde ser executada** — todas as bases de vulnerabilidade estão bloqueadas neste ambiente, e o **Dependabot está desligado no repositório** (confirmado pela API). Inventário completo das 86 dependências e três caminhos de execução estão na seção 5 | **desconhecido, não "limpo"** |

**S16 não é um achado de baixa gravidade: é um ponto cego.** Ele não pode ser ordenado junto com os
demais porque não se sabe o que há dentro. Ligar o Dependabot é a ação de maior alavancagem desta
etapa inteira, e custa um clique.

---

## O que este sistema faz certo

Um relatório de segurança que só lista problemas induz a decisão errada sobre onde investir. Quatro
propriedades reduzem risco de forma estrutural, e nenhuma deve ser perdida sem discussão:

1. **Não armazena nada.** Sem banco, sem disco, sem cache persistente. Não há base de estudantes para
   vazar — a defesa mais forte do sistema, e é arquitetural, não configurada.
2. **Não fala com ninguém.** Zero chamadas externas, zero LLM, zero filas. A superfície de
   exfiltração é o log, e só.
3. **A resposta não devolve identificação.** Nenhum DTO de saída carrega o nome do estudante.
4. **A validação de entrada é séria** e os tetos foram escolhidos pensando em custo computacional, o
   que a torna também uma defesa contra abuso.

E dois acertos menores que merecem registro porque são fáceis de perder numa refatoração: o log de
erro de validação **não** inclui valores enviados, e o corpo do `500` **não** vaza mensagem interna
nem pilha (travado por teste desde a etapa 01b).

---

## Resumo em uma tela

- **Sem gateway, sem upload de material, sem LLM, sem banco** — verificado, não suposto. O perfil de
  risco é o de uma API sem estado de endpoint único.
- **Dado pessoal de estudante inclui estado psicológico** (estresse, fadiga, motivação) coletado
  nominalmente. Recomendo tratar como sensível até decisão jurídica em contrário — o público inclui
  adolescentes.
- **O maior risco é a ausência de TLS** (S11): hoje o pacote inteiro trafega em texto plano, e não há
  proxy porque não há implantação.
- **Três caminhos levam dado pessoal para o log** (S1, S2, S3), que é JSON estruturado destinado a
  ELK/Datadog. S3 publica a autoavaliação do aluno por causa de um erro de digitação dele.
- **O limite de requisições é contornável** por cabeçalho forjado (S12), o que o torna decorativo no
  cenário atual.
- **Um defeito único converte erro de cliente em `500`** (S8) — medido em cinco cenários — e é o
  veículo de dois dos três vazamentos de log.
- **Nenhum segredo versionado**, nem no histórico. **IDOR não é expressável** nesta arquitetura — mas
  deixa de valer no dia em que houver persistência por aluno.
- **CORS não é permissivo** e o actuator não é público — dois riscos esperados que a verificação
  descartou.
- **A varredura de dependências é um ponto cego** (S16), não um resultado limpo. Ligar o Dependabot é
  a ação de maior alavancagem desta etapa.

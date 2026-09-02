# Etapa 02b — Correção dos achados de segurança

**Escopo:** ataque aos achados de [`02-diagnostico-seguranca.md`](./02-diagnostico-seguranca.md), na
ordem de severidade real. Estrutura, escrita, performance e escalonamento continuam para as etapas
seguintes.

| | |
|---|---|
| **Data** | 2026-09-01 |
| **Base** | `2fa93cb` (etapa 02) |
| **Branch** | `release/v4.0` |
| **Suíte** | 117 → **145 testes**, todos passando |
| **Cobertura** | instruções 79,2% → **79,4%**; ramos 56,8% → **58,6%**; linhas 80,2% → **80,3%** |

### Vocabulário usado neste documento

- **RFC 7807** — formato padrão de corpo de erro HTTP, com os campos `type`, `title`, `status`,
  `detail` e `instance`.
- **HSTS** (*HTTP Strict Transport Security*) — cabeçalho que instrui o navegador a nunca mais
  acessar aquele domínio por HTTP puro.
- **Terminação TLS** — o ponto da infraestrutura que decifra o tráfego HTTPS e repassa a requisição
  já em claro para a aplicação, normalmente um proxy reverso ou balanceador.
- **`X-Forwarded-For`** — cabeçalho em que um proxy anota o endereço do cliente original. É **texto
  enviado na requisição**, portanto só vale alguma coisa quando quem o escreveu é confiável.
- **Art. 14 da LGPD** — regime próprio para tratamento de dado pessoal de criança e adolescente,
  com exigências mais rígidas de consentimento e finalidade.

---

## 0. As duas perguntas que precedem qualquer correção

### 0(a) Existe autenticação ou autorização hoje?

**Não. Nem neste repositório, nem delegada a componente externo. O endpoint está aberto a qualquer
chamador direto.**

Evidência por busca exaustiva em `src/main`, por ausência confirmada:

| Mecanismo procurado | Ocorrências |
|---|---|
| `@PreAuthorize`, `@Secured`, `@RolesAllowed` | **0** |
| `hasRole`, `hasAuthority` | 1 — `config/SecurityConfig.java:42`, **dentro de um comentário** (`// .requestMatchers("/api/admin/**").hasRole("ADMIN")`) |
| `oauth2`, `jwt`, `JwtDecoder` | **0** |
| `apiKey`, `X-API-Key` | **0** |
| `UserDetailsService`, `AuthenticationProvider` | **0** |
| `httpBasic`, `formLogin` | **0** |

Evidência por comportamento: os cinco testes de `GenerateStudyPlanHappyPathTest` (etapa 01b) fazem
`POST` **sem credencial de nenhum tipo** e recebem `200` com o plano completo. Um chamador anônimo
obtém o serviço inteiro.

**Sobre a hipótese do gateway autenticador.** Procurei por qualquer sinal de que o serviço presuma
estar atrás de um componente que autentica antes — menção a gateway, BFF, Kong, nginx, Istio,
ingress — em `src/main`, no `README.md` e no workflow de CI. **Nenhuma ocorrência.** O único vestígio
de premissa de proxy era `server.forward-headers-strategy=framework`, e ele não tem nada a ver com
autenticação (e foi ele próprio parte do problema — ver item 4).

**O que isso muda na prioridade do resto.** Tudo. Não há camada anterior contendo abuso, nem
identidade a partir da qual autorizar. Logo:

- o limite de requisições **é a única proteção contra abuso que existe**, o que faz do S12 um achado
  mais grave do que pareceria num serviço autenticado;
- não há como restringir por usuário nem auditar por identidade — o endereço de rede é o único
  identificador disponível, o que dá ao S1 uma finalidade legítima concreta (item 3);
- a decisão de manter `permitAll()` **não é desta etapa**. Continua registrada como S7, e o
  diagnóstico já apontou que a justificativa do `README.md` ("open optimization engine") foi escrita
  antes de o contrato ganhar campos de estado psicológico.

### 0(b) A ausência de TLS é por falta de implantação?

**Sim.** O inventário da etapa 00 estabeleceu (R1, R2) que não existe implantação alguma: sem
`Dockerfile`, sem manifesto de orquestração, sem CI/CD de deploy, sem nenhum arquivo `.yml` de
infraestrutura no repositório. O procedimento documentado no `README.md` é `java -jar`. Não há proxy
porque não há para onde implantar.

**A separação de responsabilidade, explicitamente:**

| Responsabilidade | De quem | Situação |
|---|---|---|
| Possuir e renovar o certificado | infraestrutura | fora deste repositório |
| Terminar TLS e repassar a requisição | infraestrutura | fora deste repositório |
| Redirecionar `:80` para `:443` na borda | infraestrutura | fora deste repositório |
| **Recusar requisição em claro quando existe proxy TLS** | **aplicação** | **implementado — item 5** |
| **Emitir HSTS** | **aplicação** | **implementado — item 5** |
| **Nunca aceitar HTTP como padrão de produção** | **aplicação** | **implementado — item 5** |

A aplicação não pode terminar TLS e não deveria tentar: ela não tem o certificado. O que pode fazer,
e agora faz, é **não servir HTTP em silêncio** quando o operador declara que existe TLS na frente.

---

## 1. S8 corrigido — erro de cliente devolve 4xx

### O que era

`GlobalExceptionHandler` é um `@RestControllerAdvice` que não estende
`ResponseEntityExceptionHandler`. As exceções padrão do Spring MVC para erro de cliente não tinham
tratador e caíam todas no `@ExceptionHandler(Exception.class)`.

### O que foi feito

**Tratadores explícitos**, e não herança. A escolha é deliberada: a classe já declara
`handleValidationExceptions` para `MethodArgumentNotValidException`, e estender
`ResponseEntityExceptionHandler` criaria sobreposição com o método protegido de mesma
responsabilidade da superclasse. Tratadores explícitos mantêm um único formato de resposta e um único
lugar onde a decisão é tomada.

Sete tratadores novos em `api/exception/GlobalExceptionHandler.java`:

| Exceção | Status | Tipo RFC 7807 |
|---|---|---|
| `HttpMessageNotReadableException` | **400** | `/errors/malformed-body` |
| `HttpRequestMethodNotSupportedException` | **405** + cabeçalho `Allow` | `/errors/method-not-allowed` |
| `HttpMediaTypeNotSupportedException` | **415** | `/errors/unsupported-media-type` |
| `MissingServletRequestParameterException` | **400** | `/errors/missing-parameter` |
| `MethodArgumentTypeMismatchException` | **400** | `/errors/type-mismatch` |
| `NoResourceFoundException` | **404** | `/errors/not-found` |
| `UnknownSubjectException` (nova, item 2) | **400** | `/errors/unknown-subject` |

### Os cinco cenários do diagnóstico, medidos de novo

| # | Cenário | Antes | **Depois** |
|---|---|---|---|
| 1 | Valor de tipo incompatível num campo | 500 | **400** |
| 2 | JSON sintaticamente malformado | 500 | **400** |
| 3 | `GET` no endpoint | 500 | **405** (com `Allow: POST`) |
| 4 | `PUT` no endpoint | 500 | **405** (com `Allow: POST`) |
| 5 | `DELETE` no endpoint | 500 | **405** (com `Allow: POST`) |

Travados por `security/ClientErrorStatusTest`, que cobre os cinco mais `415`, `404` e — importante —
que **o caminho de sucesso continua devolvendo 200**.

O catch-all continua existindo, e deve: é a rede de segurança do `500` para falha genuinamente
inesperada. O que mudou é que erro de cliente não passa mais por ele.

---

## 2. S2 e S3 corrigidos — dado do aluno não vai mais para o log

Conforme o item 2 do pedido, verifiquei **depois** de S8 se os tratadores novos ainda registravam o
valor bruto. Registravam, e por dois caminhos diferentes, que exigiram correções diferentes.

### S2 — o valor recusado pelo Jackson

A mensagem do Jackson repete o valor: `Cannot deserialize value of type java.lang.Double from String
"..."`. Registrar `ex.getMessage()` ou a pilha publicaria o que o aluno digitou.

**Correção:** o tratador **não lê** a mensagem nem a pilha. Extrai apenas o *caminho estrutural* do
campo via `JsonMappingException.getPath()` — que descreve a posição, não o conteúdo.

Log real, depois da correção:

```
"message":"Unreadable request body on path /api/v1/optimizer/generate
           (field: studentProfile.knowledgeGaps.X)"
```

Diagnóstico preservado (sabe-se qual campo falhou), valor ausente. E o nível caiu de `ERROR` para
`WARN`, o que devolve utilidade ao alerta por taxa de erro.

### S3 — a autoavaliação do aluno, corrigida na raiz

A mensagem era `Duplicate key null (attempted merging values 4.5 and 2.0)`, e `4.5` e `2.0` são as
notas de autoavaliação. Sanitizar o log resolveria o sintoma; a causa era o `StudentProfileMapper`
aceitar lacuna que cita disciplina inexistente e deixá-la virar chave nula (pendência P5 da etapa
01b).

**Correção na origem.** O mapeador agora valida antes de coletar e lança `UnknownSubjectException`,
uma exceção nova cujo contrato está no Javadoc: **carrega apenas os nomes das disciplinas não
reconhecidas, nunca as notas.** Nome de disciplina vem do edital que o próprio cliente enviou e é
contrato público; nota é autoavaliação.

Isso resolve três coisas de uma vez:

1. o vazamento no log (S3);
2. o `500` que o aluno recebia por um erro dele (parte de S8);
3. a pendência **P5** da etapa 01b, que fica **fechada**.

O `Collectors.toMap` foi trocado por construção explícita de `HashMap`. A colisão não pode mais
ocorrer, já que toda chave é validada antes — mas a construção explícita torna essa garantia visível
a quem ler.

### Como isso está travado

`security/LogPrivacyTest` anexa um coletor ao logger do `GlobalExceptionHandler` e inspeciona **a
mensagem formatada e a pilha inteira**, porque era pela pilha que o vazamento acontecia. Afirma que
o valor enviado, o nome do aluno, as notas e a string `Duplicate key` **não aparecem**, que o caminho
do campo **aparece** (diagnóstico preservado), e que o nível é `WARN` e não `ERROR`.

### O que continua valendo, e por quê

O catch-all ainda registra pilha completa para exceção inesperada. É deliberado: sem pilha não há
como diagnosticar incidente real. O risco residual é uma exceção futura cuja mensagem embuta dado
pessoal — mitigado, não eliminado, porque os dois caminhos conhecidos que faziam isso foram fechados
na origem. Registrado como pendência **R1** na seção final.

---

## 3. S1 avaliado — finalidade legítima, dado minimizado

**A finalidade é legítima e o registro foi mantido.** Detecção e contenção de abuso é finalidade
reconhecida, e aqui ela é reforçada pela resposta de 0(a): sem autenticação, **o endereço de rede é
o único identificador disponível**. Remover o registro por completo deixaria a única proteção contra
abuso sem rastro para investigar.

**O dado foi minimizado.** `ClientIpResolver.maskForLogging` reduz o endereço à granularidade de
rede: em IPv4 o último octeto vira `x`; em IPv6 só os três primeiros grupos são mantidos.

Log real, depois da correção:

```
"message":"Rate limit exceeded on path /api/v1/optimizer/generate:
           Rate limit exceeded for client 203.0.113.x"
```

A finalidade continua atendida — dois endereços da mesma rede `/24` colapsam na mesma forma, o que
é exatamente o que permite reconhecer um padrão de abuso vindo de uma origem —, e o que se perde é a
identificação do assinante individual. Travado em `ClientIpResolverTest.Mascaramento`, inclusive a
propriedade de que redes diferentes continuam distinguíveis.

**O balde continua usando o endereço completo.** Mascarar a chave juntaria até 256 clientes de IPv4
num mesmo balde e transformaria o limite numa negação de serviço entre vizinhos. Mascara-se o que
sai para o log, não o que fica na memória do processo.

### Política de retenção sugerida

Não a implemento aqui — retenção é configuração do agregador de logs, fora deste repositório. A
recomendação, para quem for configurá-lo:

| Item | Sugestão | Razão |
|---|---|---|
| Retenção do log com endereço mascarado | **30 a 90 dias** | Cobre a janela de investigação de um padrão de abuso; além disso o valor forense cai rápido |
| Finalidade declarada | *"detecção e contenção de abuso do endpoint público de otimização"* | Precisa constar do registro de operações de tratamento |
| Acesso | restrito a quem opera o serviço | Não é dado de produto, é dado operacional |
| Cache em memória do limitador | já expira em 60 min por inatividade | Retenção mínima, e já existia |

---

## 4. S12 corrigido — e por que a primeira correção não bastava

### A correção óbvia

`ClientIpResolver` (novo) resolve o endereço do cliente com uma regra explícita: o endereço da
conexão é o padrão, porque é a única fonte que o chamador não escolhe. `X-Forwarded-For` só entra
quando a conexão vem de um proxy declarado em `api.trusted-proxies` — **lista vazia por padrão**,
porque a configuração segura tem de ser a que vale sem ninguém configurar nada.

Quando há proxy confiável, a cadeia é percorrida **da direita para a esquerda**, descartando proxies
confiáveis até achar a primeira entrada que não seja um deles. Os valores à esquerda podem ter sido
forjados pelo próprio cliente e são ignorados.

### A parte que quase passou

Escrita a correção, o teste de ponta a ponta mostrou que **o limite continuava burlável**: seis
requisições variando o cabeçalho, nenhuma bloqueada, com capacidade três. O resolvedor estava certo e
o resultado errado.

A causa estava fora dele. Com `server.forward-headers-strategy=framework`, o Spring registra o
`ForwardedHeaderFilter`, que roda **antes** de qualquer filtro da aplicação e **reescreve
`getRemoteAddr()`** com o valor do `X-Forwarded-For` enviado pelo cliente. O resolvedor recebia um
"endereço de conexão" já adulterado e não tinha como saber.

Confirmado inspecionando os beans registrados: `FilterRegistrationBean: forwardedHeaderFilter ->
org.springframework.web.filter.ForwardedHeaderFilter`.

> **Nota de método.** Minha primeira verificação dessa hipótese deu negativa e me desviou por uma
> rodada: chequei `getRemoteAddr()` em `MvcResult.getRequest()`, que devolve a requisição
> **original**, não a que o filtro embrulhou. A hipótese estava certa e a medição errada. O que
> resolveu foi medir o efeito observável — quantas requisições eram bloqueadas — em vez de inspecionar
> o estado intermediário.

**Correção completa:** `server.forward-headers-strategy=none` como padrão, documentado no próprio
`application.properties` com a razão. Passa a `framework` **somente junto com**
`api.trusted-proxies` preenchido e um proxy real na frente.

### Como está travado

Em duas camadas, porque uma só não pegaria este defeito:

- `ClientIpResolverTest` — 12 testes de unidade da lógica de resolução e de mascaramento;
- `ForwardedHeaderConfigTest` — **ponta a ponta pela cadeia real de filtros**: verifica que o
  `ForwardedHeaderFilter` não está registrado, e que seis requisições com cabeçalho variando
  produzem três bloqueios. Antes da correção completa, produziam zero.

---

## 5. S11 — enforcement defensivo implementado

Conforme decidido em 0(b), a aplicação passa a ter postura de produção configurável, sem tentar
assumir responsabilidade de infraestrutura.

**`api.security.require-https`**, desligado por padrão. Quando ligado, `SecurityConfig`:

- exige canal seguro (`requiresChannel().requiresSecure()`) — requisição em claro é recusada com
  redirecionamento para `https://`;
- emite **HSTS** com `max-age` de um ano e `includeSubDomains`, configurável por
  `api.security.hsts-max-age-seconds`.

**Por que o padrão é desligado, e por que isso não é o padrão inseguro.** Ligar sem proxy real
quebraria todo acesso local e — pior — faria a decisão "esta requisição veio por HTTPS?" depender do
`X-Forwarded-Proto`, que só vale vindo de proxy confiável. É o mesmo problema do S12. Por isso as
três chaves da seção de segurança do `application.properties` (`trusted-proxies`, `require-https`,
`forward-headers-strategy`) descrevem **uma única premissa de implantação** e devem ser ligadas
juntas. O `application.properties` diz isso em comentário, no lugar onde alguém vai mexer.

Travado por `HttpsEnforcementTest`, que verifica os dois modos: desligado, requisição em claro é
processada e não há HSTS; ligado, requisição em claro é redirecionada e requisição segura recebe
HSTS com os valores certos.

**O que continua fora do alcance deste repositório**, e precisa existir na infraestrutura antes de
qualquer exposição real: certificado válido e sua renovação; terminação TLS num proxy reverso ou
balanceador; redirecionamento de `:80` para `:443` na borda; e o endereço desse proxy declarado em
`api.trusted-proxies`.

---

## 6. Pendências de decisão humana

Separadas do que foi corrigido porque **não são defeitos**: são escolhas que engenharia não deve
fazer sozinha.

### P1 — Dado psicológico autodeclarado e o Art. 14 da LGPD · **PRIORIDADE ALTA**

**Não decidi, e não devo decidir.** O que registro é o fato técnico, já levantado no diagnóstico:

O contrato coleta `stressLevel`, `fatigueLevel` e `motivationLevel` — medidas autodeclaradas de
estado psicológico e físico — **junto com o nome do estudante**, portanto de forma nominal. A
categoria mais próxima na LGPD é "dado referente à saúde" (art. 5º II), que é dado sensível.

**E o público inclui adolescentes.** Quem presta concurso e vestibular frequentemente tem menos de 18
anos, o que aciona o art. 14 — regime próprio para dado de criança e adolescente, com exigências mais
rígidas de consentimento, finalidade e minimização.

**Recomendação mantida do diagnóstico:** tratar como dado sensível até que alguém com competência
jurídica diga o contrário. O custo de errar para o lado cauteloso é baixo; o de errar para o outro
lado, não.

O que precisa ser decidido por produto e jurídico, não por esta etapa:

1. Base legal para coletar estado psicológico nominalmente.
2. Se há tratamento de dado de adolescente e, havendo, como o art. 14 é atendido.
3. Se os três campos de estado são **necessários** — a alternativa de minimização mais simples é
   torná-los opcionais ou recebê-los desvinculados do nome.
4. Política de privacidade, aviso de tratamento e registro de operações, hoje inexistentes.

### P2 — Terminação TLS de verdade

O enforcement defensivo está implementado, mas **desligado por padrão e inútil sem proxy real**.
Enquanto não houver implantação com TLS, o dado continua trafegando em claro. É infraestrutura, não
código. Requisitos listados no item 5.

### P3 — Dependabot: um clique, e não desta credencial

A varredura de dependências continua sendo o ponto cego declarado na etapa 02 (S16). As bases de
vulnerabilidade seguem bloqueadas neste ambiente e **a consulta à API confirmou que o Dependabot
está desligado no repositório**. Não tentei rodar SCA nesta etapa, conforme o item 7 do pedido.

**Ação do usuário, um clique:** `Settings → Code security → Dependabot alerts` e
`Dependabot security updates`. É a ação de maior alavancagem que resta em segurança, e está fora do
alcance desta credencial.

### P4 — `permitAll()` no endpoint de negócio (S7)

Mantido, porque mudá-lo é decisão de produto e não de correção de segurança. A observação do
diagnóstico continua de pé: a justificativa do `README.md` ("open optimization engine") foi escrita
antes de o contrato ganhar campos de estado psicológico. Se P1 concluir que o dado é sensível, esta
decisão precisa ser revista junto.

### R1 — Risco residual do catch-all

O `handleAllUncaughtException` continua registrando pilha completa para exceção inesperada — é
necessário para diagnosticar incidente real. Se, no futuro, alguma exceção passar a embutir dado
pessoal na mensagem, ela vazará por esse caminho. Os dois caminhos conhecidos foram fechados na
origem; o mecanismo genérico permanece.

---

## 7. O que foi alterado

### Código de produção

| Arquivo | Mudança |
|---|---|
| `api/exception/GlobalExceptionHandler.java` | 7 tratadores novos; log sem valor do usuário; Javadoc com a razão |
| `api/exception/UnknownSubjectException.java` | **novo** — carrega nomes de disciplina, nunca notas |
| `api/mapper/StudentProfileMapper.java` | valida lacunas antes de coletar; `HashMap` explícito |
| `api/controller/ClientIpResolver.java` | **novo** — resolução por conexão, proxies confiáveis, mascaramento |
| `api/controller/RateLimitingFilter.java` | usa o resolvedor; mascara o endereço na mensagem |
| `config/SecurityConfig.java` | exigência de HTTPS e HSTS, sob chave desligada por padrão |
| `resources/application.properties` | `forward-headers-strategy=none`; três chaves novas, documentadas |

### Testes

| Arquivo | Testes | Cobre |
|---|---:|---|
| `security/ClientErrorStatusTest` | 6 | S8 — os cinco cenários, mais 415, 404 e o caminho de sucesso |
| `security/LogPrivacyTest` | 4 | S1, S2, S3 — inspeção da mensagem **e** da pilha |
| `security/ClientIpResolverTest` | 12 | S12 e S1 — resolução, cadeia de proxies, mascaramento |
| `security/ForwardedHeaderConfigTest` | 2 | S12 ponta a ponta, pela cadeia real de filtros |
| `security/HttpsEnforcementTest` | 3 | S11 — os dois modos |
| `api/mapper/MapperRoundTripTest` | 2 atualizados | Comportamento corrigido em lugar do antigo |
| `api/ApiErrorContractTest` | 1 atualizado | O teste do XFF **inverteu de sentido**, e isso é o ponto |

Três testes da etapa 01b **documentavam o comportamento inseguro** e passaram a falhar quando ele foi
corrigido — que é exatamente o que testes de caracterização devem fazer. Foram reescritos para
afirmar o comportamento novo, com o antigo citado no comentário para que a mudança fique legível.

---

## Resumo em uma tela

- **0(a): não há autenticação nenhuma**, nem delegada — verificado por ausência de 14 mecanismos e
  por chamada anônima que recebe `200`. Isso torna o limite de requisições a única proteção
  existente, e por isso o S12 pesa mais do que pesaria num serviço autenticado.
- **0(b): a falta de TLS é falta de implantação.** Terminar TLS é infraestrutura; recusar HTTP e
  emitir HSTS é aplicação, e foi implementado sob chave desligada por padrão.
- **S8 corrigido:** os cinco cenários medidos no diagnóstico agora devolvem 400, 400, 405, 405 e 405.
- **S2 e S3 corrigidos**, o segundo na raiz: o mapeador valida antes de coletar, o que fecha o
  vazamento, o `500` indevido e a pendência P5 da etapa 01b de uma vez.
- **S1 mantido com finalidade declarada e dado minimizado** — `203.0.113.x` no lugar do endereço
  completo, com política de retenção sugerida.
- **S12 corrigido em duas camadas.** A primeira não bastava: `forward-headers-strategy=framework`
  fazia o próprio Spring reescrever `getRemoteAddr()` com o cabeçalho do cliente. Só um teste de
  ponta a ponta pegou isso.
- **145 testes** (eram 117), suíte verde, cobertura de ramos 56,8% → **58,6%**.
- **Quatro pendências humanas**, com a do Art. 14 da LGPD em prioridade alta, e o Dependabot a um
  clique do usuário.

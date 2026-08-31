# Decisão: Ausubel / Sequenciamento por Pré-requisitos

**Etapa:** decisão de arquitetura. Precede qualquer código.
**Questão:** implementar sequenciamento por pré-requisitos nesta versão do produto, ou não implementar
e corrigir a documentação que hoje afirma incorretamente que ele existe?
**Anteriores:** [`01`](./01-auditoria-fitness.md) §3.3, §4 · [`02`](./02-formulacao.md) §5 ·
[`05`](./05-fitness-function.md) §4.1

---

## 1. O problema

A aprendizagem significativa de Ausubel sustenta que material novo precisa se ancorar em
subsunçores já presentes na estrutura cognitiva; sem eles, o resultado é aprendizagem mecânica.
Operacionalmente, isso implica uma restrição de **ordem**: o pré-requisito precede o dependente.

O motor não implementa isso. O cromossomo macro é `Map<Subject, Integer>` — uma contagem de dias por
disciplina, **sem posição no calendário e sem ordem**. Não há como um mapa expressar "A antes de B".

Além disso, e este ponto é novo desta etapa: **a API não coleta a informação de pré-requisitos.**
`SubjectDto` tem `name`, `questionCount` e `cognitiveLoad`. `Subject` tem os mesmos três campos.
Não existe relação de dependência em lugar nenhum do modelo de entrada.

O `README.md` afirma que Ausubel é tratado pelo multiplicador de lacuna de conhecimento. Não é: essa
é uma regra monotônica de triagem ("mais lacuna, mais prioridade"), sem noção de ancoragem nem de
ordem. A correção dessa afirmação é obrigatória e independe da decisão abaixo (§5).

---

## 2. Opção (i) — Redesenhar o encoding para expressar precedência

### 2.1 O que seria preciso construir

| # | Camada | Trabalho | Observação |
|---|---|---|---|
| 1 | **Contrato de API** | `SubjectDto` ganha `prerequisites: List<String>`; validação de referências existentes e ausência de ciclos | Mudança de contrato: versão nova do endpoint ou quebra |
| 2 | **Conteúdo** | Alguém precisa **autorar o DAG de pré-requisitos de cada edital** e mantê-lo quando o edital muda | **Não é engenharia.** É trabalho editorial por concurso, recorrente |
| 3 | **Domínio** | `PrerequisiteGraph` com ordenação topológica e detecção de ciclo | Pequeno |
| 4 | **Encoding** | Trocar `Map<Subject,Integer>` por estrutura com ordem | Ver §2.2 |
| 5 | **Operadores** | `WeightedAverageCrossover`, `RepairingCrossover`, `CreepMutation`, `TransferMutation` e `StudyPlanFactory` precisam preservar **duas** invariantes: orçamento e validade topológica | O maior item |
| 6 | **Reparo** | Ordenação topológica + reempacotamento das datas de início quando um operador quebra a precedência | Novo componente |
| 7 | **Fitness** | Termo de violação de precedência | Pequeno, uma vez que 4–6 existam |
| 8 | **Agendador** | `StudyScheduleGenerator` consome `Map<Subject,Integer>`; passaria a ter de respeitar a ordem | Médio |
| 9 | **Benchmarks** | As 6 estratégias do harness precisam produzir o novo cromossomo; as 8 instâncias precisam de DAGs | Médio |

### 2.2 Dois encodings possíveis

**(a) Sequência explícita de alocações** — cromossomo é uma lista ordenada de `(disciplina, dia)`.
Expressivo, mas o espaço de busca explode (permutações) e todo operador clássico de AG precisa ser
substituído por operadores de permutação com reparo. É o caminho caro.

**(b) Vetor de datas de início** — `Map<Subject, (diaInicio, dias)>`. A ordem emerge das datas, e a
precedência vira a restrição linear `inicio_B ≥ inicio_A + dias_A` para cada aresta `A → B`.
Dobra a dimensão do cromossomo em vez de trocar sua natureza, e os operadores atuais podem ser
adaptados em vez de reescritos. **Se a opção (i) for escolhida um dia, é por aqui que se começa.**

### 2.3 Estimativa de esforço

Comparando com o que esta revisão já custou: as etapas 00 a 05 somadas produziram ~1.400 linhas de
código de produção e benchmark e 6 documentos. A opção (i), pelo caminho (b), é **da mesma ordem de
grandeza ou maior**, concentrada em reescrever operadores e seus testes — a parte do sistema com
menos cobertura antes desta revisão e mais sensível a regressão silenciosa.

E ela é **bloqueada pelo item 2 da tabela**, que engenharia não resolve: sem DAGs autorados por
edital, o encoding fica pronto e sem dados para operar.

### 2.4 Por que a "versão mínima" pedida não existe

O enunciado desta etapa oferece um meio-termo: se a recomendação for (i), implementar ao menos uma
**checagem de viabilidade topológica na fitness**, penalizando indivíduos que agendem um tópico antes
de seus pré-requisitos.

**Essa checagem é vacuosa sobre o encoding atual.** `Map<Subject, Integer>` não tem ordem: não existe
"antes" para verificar. Qualquer validador topológico escrito hoje passaria para **todo** indivíduo,
por construção — não porque os planos respeitam precedência, mas porque a pergunta não é formulável.

Isso é exatamente o defeito que esta revisão passou cinco etapas eliminando. A etapa `01` §0
encontrou **quatro de cinco** componentes da fitness incapazes de alterar qualquer decisão: dois por
guarda de tipo que nunca abria, um por invariante preservada a montante, um por ser constante entre
indivíduos. Acrescentar um quinto — um termo de Ausubel que nunca pode falhar — seria pior do que não
ter nenhum: daria ao produto licença para afirmar que trata pré-requisitos, com um componente que a
própria auditoria classificaria como inerte na etapa seguinte.

**A checagem topológica não é um subconjunto barato da opção (i). Ela pressupõe o encoding.**

---

## 3. Opção (ii) — Não implementar nesta versão e corrigir a documentação

Manter o encoding atual, declarar explicitamente que sequenciamento por pré-requisitos não é uma
capacidade do produto, e corrigir toda afirmação em contrário no repositório.

**Custo:** algumas horas de documentação.
**Risco:** nenhum risco técnico. O risco é comercial — o produto passa a anunciar dois fundamentos
teóricos em vez de três.

---

## 4. Recomendação: **opção (ii)**

Quatro razões, em ordem de peso.

**1. A versão mínima não existe, e a versão completa não cabe nesta rodada.** §2.4 mostra que não há
degrau intermediário: ou se muda o encoding, ou o termo é decorativo. E §2.3 mostra que a mudança de
encoding é da ordem de toda a revisão feita até aqui.

**2. Falta o dado, não só o código.** A API não coleta pré-requisitos e ninguém autorou DAGs de
edital. Mesmo com o encoding pronto, o produto não teria o que executar. Este é um bloqueio de
conteúdo e operação, e é o motivo pelo qual a decisão não pertence só à engenharia.

**3. O encoding é uma decisão já registrada como separada.** A etapa `02` §5 concluiu que a troca de
encoding deve ser tratada como decisão explícita e isolada, porque ela também destrava as limitações
L2, L3 e L6 de `05` §8 — retenção fiel, carga por episódio e aderência a prazo. Fazê-la agora,
enfiada dentro de "implementar Ausubel", subverteria essa decisão e resolveria mal quatro problemas
de uma vez.

**4. Afirmar menos e provar é melhor do que afirmar mais e não sustentar.** O produto vai a uma
reunião com o Colégio Donaduzzi e a um pitch do Centelha. Dois fundamentos implementados, medidos e
documentados — com as aproximações nomeadas como aproximações — sustentam escrutínio. Três
fundamentos em que o terceiro não resiste à primeira pergunta técnica ("como vocês representam a
ordem?") custam a credibilidade dos outros dois junto.

### 4.1 O que o produto pode afirmar sobre Ausubel a partir de agora

**Nada.** Ausubel sai do material técnico. O que existe — o multiplicador de lacuna — é descrito pelo
que faz: *a lacuna de conhecimento declarada pelo aluno personaliza a importância de cada disciplina;
quanto maior a lacuna declarada, maior a prioridade.* É uma regra de triagem monotônica, defensável
por si, sem atribuição teórica.

### 4.2 Gatilhos para reabrir a decisão

Registrados para que a reabertura seja por dado, não por intuição:

1. **O dado passar a existir** — quando houver DAG de pré-requisitos autorado para ao menos um edital
   real, o bloqueio de conteúdo cai e a conta muda.
2. **A decisão de encoding ser tomada por outro motivo** — se L2/L3/L6 justificarem o encoding
   temporal, Ausubel passa a custar apenas o item 7 da tabela de §2.1, e aí vale a pena.
3. **Evidência de que a ordem importa no macro** — hoje não há medição de que planos que respeitam
   precedência produzam melhor desempenho do que planos que a ignoram. Sem isso, a implementação
   seria fé, não engenharia.

---

## 5. Ação aplicada nesta etapa

Conforme a opção (ii) exige, a correção documental foi feita agora, não deixada como pendência:

| Local | Antes | Depois |
|---|---|---|
| `README.md` §Theoretical Foundation | "Meaningful Learning Theory (David Ausubel): (…) subsumers (…) knowledgeGaps" | Ausubel removido. A regra é descrita pelo que faz, sem atribuição teórica |
| `README.md` — Ebbinghaus e Sweller | Atribuídos a `ReviewFocusedStrategy` / `CognitiveLoadBalancingStrategy` (heurísticas pós-AG) | Corrigidos: agora são termos da fitness (`O₃`, `O₄`), com as aproximações nomeadas |
| `README.md` — frase de abertura | "direct computational translation of three established educational theories" | Reescrita: dois fundamentos, com o grau de fidelidade declarado |
| `05-fitness-function.md` §4.1 | já registrava a ausência | Passa a apontar para esta decisão |

Os documentos `00` a `04` **não** foram alterados: eles descrevem a atribuição incorreta como achado
de auditoria, o que continua correto e é o registro histórico de como o erro foi encontrado.

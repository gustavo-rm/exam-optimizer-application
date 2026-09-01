# Release 4.0 — Revisão da função de fitness do algoritmo genético

Este documento é a descrição do pull request de `claude/sinapse-ag-diagnostico-8t1ez7` para
`release/v4.0`. Ele é escrito para quem **não** acompanhou as etapas 00 a 08 da revisão.

---

## O que é este PR

O SINAPSE monta o plano de estudos macro com um algoritmo genético: ele decide **quantos dias** cada
disciplina recebe dentro do orçamento de dias do aluno. Para escolher entre dois planos, o algoritmo
usa uma *função de fitness* — uma nota única que resume a qualidade do plano.

Esta revisão auditou essa função de ponta a ponta, corrigiu o que estava errado, e mediu o resultado
contra alternativas mais simples. São 22 commits e 86 arquivos, dos quais a maior parte é
documentação e um módulo de benchmarks isolado que **não vai para o artefato de produção**.

**Suíte de testes: 73 testes, 0 falhas.**

---

## 1. O que mudou na função de fitness, e por quê

A auditoria inicial (etapa 01) encontrou um problema sério: **quatro dos cinco componentes da
fitness não conseguiam alterar decisão nenhuma**. Dois nunca eram executados porque dependiam de um
tipo de plano que a produção não gera; um era neutralizado por uma regra aplicada antes dele; e um
devolvia sempre o mesmo valor, independentemente do plano. Na prática, a nota de um plano era
decidida por um único termo.

Além disso, esse termo tinha dois defeitos de formulação:

- **A importância das disciplinas entrava sem normalização.** As regras de validação da própria API
  permitem que duas disciplinas do mesmo edital difiram por um fator de 250.000. O resultado era um
  comportamento de "o vencedor leva tudo": o algoritmo concentrava quase todo o orçamento na
  disciplina de maior peso.
- **A curva de aprendizado não tinha teto.** A fórmula `ln(1 + dias)` afirma que o conhecimento
  cresce indefinidamente com o tempo de estudo. A literatura de curvas de prática descreve o
  contrário: o ganho satura. Sem saturação, nunca compensava tirar um dia da disciplina dominante.

### As correções aplicadas

| # | Mudança | Motivo |
|---|---|---|
| 1 | A importância de cada disciplina passa a ser normalizada antes de entrar na conta (todas somam 1) | Impede que o payload determine a escala e torna as notas comparáveis entre editais |
| 2 | `ln(1 + dias)` foi substituída por uma curva com teto, `1 − e^(−dias/τ)` | Ganho saturante, como descrevem as curvas de prática. É o que faz valer a pena espalhar o estudo |
| 3 | Novo objetivo de **retenção na data da prova**, baseado na curva de esquecimento de Ebbinghaus | Conhecimento não retido até a prova vale zero na prova |
| 4 | Novo objetivo de **carga cognitiva diária**, baseado na teoria de Sweller | Um plano que o aluno abandona na terceira semana não é um plano |
| 5 | Os três objetivos passam a ser combinados por soma ponderada normalizada, com pesos `0,50 / 0,30 / 0,20` justificados um a um | A nota fica sempre entre 0 e 1, e um peso alterado sem rebalancear passa a quebrar a inicialização |
| 6 | A violação do piso de dias mínimos passa a ser **graduada**, não binária | Antes, errar o piso de uma disciplina por um dia custava o mesmo que ignorar o piso inteiro. Não havia gradiente de volta à viabilidade |
| 7 | O termo de retenção ganhou peso próprio por disciplina, "temperado" (expoente 0,5) | Enquanto retenção e cobertura de edital dividiam o mesmo vetor de pesos, os dois concordavam em abandonar as mesmas disciplinas, e nada na fitness fazia contrapeso |
| 8 | O limiar de revisão obrigatória foi alinhado ao intervalo do algoritmo SM-2 | Os dois lados do motor de retenção discordavam por um fator de aproximadamente 6 |

Fora da fitness, a etapa 04 corrigiu a **reprodutibilidade**: os operadores de mutação sorteavam de
fontes que nenhuma semente alcança, então a mesma entrada produzia planos diferentes. Antes, 2 de 8
instâncias eram reprodutíveis; agora são 8 de 8.

### O que isso produziu, em números

O algoritmo genético agora supera o baseline guloso por prioridade em **todas as 8 instâncias**, de
+1,25% a +6,35%. Mais importante: ele agora supera também a solução ótima obtida por ganho marginal
guloso em 7 das 8 instâncias, em até +8,3%.

Esse segundo ponto é o que justifica usar um algoritmo genético. Com a fitness antiga, o problema
tinha solução em forma fechada — um algoritmo guloso simples encontrava o ótimo exato em
milissegundos, e o AG apenas empatava com ele gastando de 20 a 400 vezes mais tempo. Com os termos
de retenção e carga cognitiva no agregado, a função deixou de ser separável, e a busca passou a ter
o que encontrar. Há um teste de regressão que falha se essa propriedade se perder.

---

## 2. O problema estatístico: por que o número agregado anterior era inválido

Esta parte importa porque a **conclusão de manchete das etapas 03 e 05 estava apoiada numa
estatística que não se sustenta**.

### O que se queria medir

A pergunta é: *maximizar a nota do plano ajuda ou atrapalha a retenção real do aluno?* Para
responder, o harness compara 6 planejadores diferentes (o AG e 5 alternativas mais simples) em cada
instância, e mede a **correlação de Spearman** entre a nota e a métrica de negócio.

> **Correlação de Spearman** compara *ordenações*, não valores. Ela responde: "o planejador que a
> fitness coloca em primeiro lugar é também o que entrega a melhor retenção?" Varia de −1 (ordem
> perfeitamente invertida) a +1 (ordem perfeitamente alinhada).

### O que estava errado

Os relatórios anteriores juntavam todas as 48 observações — 8 instâncias × 6 planejadores — num
único cálculo de Spearman. Isso compara notas de fitness **entre instâncias diferentes**, e o
próprio código já dizia que isso não é permitido: a nota não tem unidade normalizada entre
instâncias. A nota de `I4` (≈0,90) fica acima da de `I8` (≈0,55) porque são problemas diferentes,
não porque um plano seja melhor. O coeficiente resultante media, sobretudo, **qual instância é
fácil**.

O efeito prático foi inverter o sinal da conclusão. Empilhado, o resultado da etapa 05 parecia
**+0,161**, e o relatório afirmava que "o sinal agregado inverteu". Calculado corretamente, o mesmo
dado dá **−0,460**: a anti-correlação tinha sido reduzida, nunca invertida.

### O método correto, adotado agora

Cada instância tem sua correlação calculada separadamente. Depois, as correlações são combinadas
pela **transformada z de Fisher**.

> **Transformada z de Fisher** é o procedimento padrão para combinar coeficientes de correlação.
> Coeficientes não vivem numa escala aditiva — a distância de 0,90 a 0,95 carrega muito mais
> informação do que de 0,10 a 0,15 — então tirar média simples deles distorce o resultado. A
> transformada converte cada coeficiente para uma escala onde a média faz sentido, calcula a média
> ali, e converte de volta.

Está implementado em `benchmarks/…/metric/CorrelationAggregate.java`, é o **único** caminho pelo qual
um número agregado pode ser produzido, e tem 13 testes. Um deles constrói um caso sintético onde os
dois métodos chegam a conclusões de sinal oposto sobre os mesmos dados, e trava a diferença.

Duas decisões de implementação que mudam números e estão documentadas:

- Instâncias cuja correlação é **indefinida** são excluídas e contadas, nunca somadas como zero.
  Quatro das oito instâncias têm todos os 6 planejadores empatados em 100% de retenção: não existe
  ordenação para correlacionar. Tratá-las como "correlação zero" inventaria uma concordância que
  ninguém mediu.
- A detecção de empates ganhou tolerância numérica. Sem ela, uma média de sete repetições idênticas
  (`0,9749999999999999`) não empatava com o valor exato `0,975` de um planejador determinístico, e o
  coeficiente publicado **dependia de o relatório ter passado por um arquivo CSV ou não**. Corrigido;
  o valor publicado antes era o correto.

---

## 3. A tabela final

Três estados do motor, todos remedidos do zero reconstruindo cada commit original:

| Estado | Correlação agregada | Intervalo de 95% | I² |
|---|---|---|---|
| Antes das correções (etapa 03, média de 10 execuções) | **−0,909** | −0,971 a −0,734 | 0% |
| Depois das correções de fidelidade (etapa 05) | **−0,460** | −0,793 a +0,085 | 15% |
| Depois do ajuste dos pesos de retenção (etapa 06, estado atual) | **−0,106** | −0,597 a +0,443 | **58%** |

**Em linguagem simples:** a correlação negativa entre a nota que o sistema dá a um plano e a
retenção que esse plano de fato entrega caiu de **forte para fraca**. Antes, o plano mais bem
avaliado pelo sistema era sistematicamente um dos piores em retenção; hoje isso praticamente
desapareceu. **A direção da melhora é sólida** — três estados medidos, melhora consistente, cada um
reconstruído do commit original. **O valor exato não é preciso** com os dados atuais, e o intervalo
ainda cruza o zero.

Duas ressalvas que acompanham esses números sempre:

- **O agregado vem de 4 das 8 instâncias.** As outras quatro saturam a métrica em 100% para todos os
  planejadores. Isso foi investigado: elas saturam no **teto**, não no piso — ou seja, nem o pior
  planejador perde disciplina alguma nelas, então a fitness não teria como causar dano. Excluí-las
  não esconde um cenário ruim.
- **I² = 58% no estado atual.**

> **I²** mede quanto da variação entre as instâncias é desacordo real, e não ruído de amostragem.
> Acima de 50%, as instâncias não estão medindo a mesma coisa, e a média passa a ser uma descrição
> fraca de qualquer uma delas individualmente.

O I² subiu de 0% para 58% ao longo da revisão justamente porque as correções funcionaram de forma
desigual: uma instância foi de −0,833 para +0,655, e outra (`I8`) não se moveu. Por isso o relatório
publica também um intervalo de **efeitos aleatórios** (−0,765 a +0,662), que é o honesto quando as
instâncias discordam entre si.

---

## 4. Ausubel: não implementado, decisão mantida, justificativa corrigida

O `README` do projeto afirmava que a aprendizagem significativa de David Ausubel — sequenciamento por
pré-requisitos — estava implementada, atribuída ao multiplicador de lacuna de conhecimento. **Isso
estava errado.** O que existe é uma regra de triagem monotônica ("quanto maior a lacuna declarada
pelo aluno, maior a prioridade"), defensável por si mesma, mas sem relação com a teoria de Ausubel —
que implicaria o oposto num caso importante: material cujos pré-requisitos faltam deveria ser
**adiado**, não promovido. A atribuição foi removida do `README`.

**A decisão de não implementar sequenciamento por pré-requisitos agora está mantida**, mas por
motivos diferentes dos que estavam escritos. Uma verificação independente (etapa 06) buscou lógica de
pré-requisitos em todo o repositório — modelo de domínio, contrato de API, operadores de crossover e
mutação, operadores de reparo, validação de cromossomo e camadas de agendamento — e não encontrou
nenhuma, o que confirma a afirmação. Mas a *justificativa* estava incorreta em dois pontos, ambos
corrigidos:

- Dizia-se que a ordem era "inexprimível por construção". Isso vale para o cromossomo que a produção
  executa, mas **não para o repositório**: existe um segundo cromossomo, indexado no tempo, que a
  função de fitness já aceita sem alteração de assinatura. Ele é código não cabeado, mas existe.
- A estimativa de esforço tratava como trabalho a fazer várias peças que **já estão escritas**
  (encoding com ordem, crossover, mutação e um operador de reparo), e omitia a peça que de fato
  falta: não existe motor evolutivo que use esse cromossomo.

**O bloqueio real não é técnico.** É que ninguém autorou o grafo de pré-requisitos de nenhum edital —
trabalho editorial e curatorial recorrente, por concurso — e a API não coleta esse dado. Enquanto
isso não existir, um validador de precedência seria mais um componente inerte na fitness, que é
exatamente o defeito que esta revisão passou cinco etapas eliminando.

---

## 5. Duas pendências de decisão de produto — não são bugs

Ambas estão medidas, documentadas e rastreadas no índice
[`docs/revisao-ag/README.md`](./README.md). Nenhuma delas bloqueia o merge; ambas precisam de uma
decisão que a engenharia não deve tomar sozinha.

### G12 — A instância `I8` mantém correlação de −0,880 e não respondeu à correção

`I8` simula um edital grande: 40 disciplinas, 280 dias de orçamento, horizonte de 300 dias. É a única
instância que não se moveu com o ajuste dos pesos de retenção, e é a que mais puxa o agregado para
baixo. A explicação originalmente registrada — de que seria efeito do orçamento apertado — **foi
testada e não se confirmou**: uma varredura controlada mostrou que o aperto do orçamento não
reproduz o fenômeno. A hipótese atual, ainda **não medida**, é que o agendador tático estuda apenas
as três disciplinas mais críticas por dia, de modo que com 40 disciplinas a maioria é tocada
raramente, independentemente do que a fitness escolha.

**O que precisa ser decidido, e por quem:** a equipe técnica precisa decidir se vale investigar o
agendador tático agora. O contexto para a decisão é que a amplitude de retenção em `I8` é de apenas
2,5 pontos percentuais — a correlação é forte em ordenação e desprezível em magnitude — e o agendador
tático está fora do escopo desta revisão e tem outros consumidores.

### G13 — A divisão uniforme entrega mais retenção que o AG em duas instâncias

Em `I3`, dividir os dias igualmente entre as disciplinas mantém 96,0% delas dentro da janela de
retenção, contra 76,6% do AG. Em `I4`, 100,0% contra 81,4%.

Investigando, o motivo não é o que parecia. O AG **perde** nos dois termos de conteúdo (cobertura de
edital e retenção) nessas instâncias, e vence pelo termo de carga cognitiva. Ou seja, a troca real
não é "pontuação contra memória", é **"memória contra sustentabilidade da agenda"**: em `I4`, o plano
uniforme entrega mais retenção ao custo de estourar o orçamento cognitivo do aluno em 124 dos 160
dias, contra 23 dias do plano do AG.

Também foi medido que **mexer nos pesos não resolveria**: dobrar o peso da retenção compra 0,6 ponto
percentual em `I3` e piora outra instância. A alavanca que fecharia a diferença é o piso de dias
mínimos por disciplina — um piso de 4 dias leva `I3` a 100% ao custo de 0,52% da cobertura de edital,
sem regredir nenhuma instância.

**O que precisa ser decidido, e por quem:** é uma decisão de produto, não técnica. Alguém responsável
pelo produto precisa responder qual promessa o SINAPSE faz ao aluno — *"cobrimos o edital inteiro"*,
o que implica adotar um piso de cobertura e recusar editais que não caibam no prazo; ou
*"otimizamos sua nota esperada"*, o que implica que sacrificar disciplinas de baixo peso sob
orçamento apertado é o comportamento pretendido e o que precisa mudar é o material de venda.
**Nenhum peso deve ser ajustado antes dessa resposta.**

---

## 6. Escopo: o que este PR não faz

- **Não implementa Ausubel.** A decisão de não implementar foi mantida.
- **Não altera os pesos da fitness** (`0,50 / 0,30 / 0,20`) nem o piso de dias mínimos, que segue em
  1 dia. Qualquer ajuste depende da decisão G13.
- **Não mexe no agendador tático**, que é onde está a hipótese aberta de G12.
- **Não expande o conjunto de instâncias de teste.** Essa decisão foi analisada e registrada: o eixo
  mais óbvio de expansão já foi varrido e não produz instâncias informativas, e os eixos que
  produziriam introduziriam viés de seleção. Os gatilhos para reabrir estão escritos, sendo o
  primeiro deles o uso do número agregado por um avaliador externo.

O módulo de benchmarks fica em `benchmarks/java`, anexado como fonte de teste. Ele **não** entra no
artefato de produção.

---

## 7. Como verificar

```bash
./mvnw -o clean test          # 73 testes, 0 falhas
./mvnw -o dependency:build-classpath -Dmdep.outputFile=cp.txt
CP="target/classes:target/test-classes:$(cat cp.txt)"

java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.harness.BenchmarkMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.RegimeCorrelationMain
java -cp "$CP" com.ia.project.dynamicstudyplanner.benchmark.robustness.SaturationDiagnosticMain
```

## 8. Por onde começar a ler

| Se você quer… | Leia |
|---|---|
| Entender a fitness como ela é hoje | [`05-fitness-function.md`](./05-fitness-function.md) |
| O índice, os números canônicos e o status de tudo | [`README.md`](./README.md) |
| A decisão de produto pendente sobre os pesos (G13) | [`06-limite-troca-pesos.md`](./06-limite-troca-pesos.md) |
| Por que o número agregado anterior era inválido | [`07-correcao-metrica-e-ausubel.md`](./07-correcao-metrica-e-ausubel.md) |
| O estado mais recente e a decisão sobre amostragem | [`08-saturacao-e-amostragem.md`](./08-saturacao-e-amostragem.md) |

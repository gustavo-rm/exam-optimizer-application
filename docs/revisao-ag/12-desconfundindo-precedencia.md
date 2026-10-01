# Medição 12 — Desconfundindo representação de política de precedência

**Documentos anteriores:** [`README.md`](./README.md) ·
[`10-medicao-v2-linha-do-tempo.md`](./10-medicao-v2-linha-do-tempo.md) ·
[`11-varredura-peso-ordem.md`](./11-varredura-peso-ordem.md)
**Este documento:** o fatorial 2×2 representação × política, e a previsão falsificável que ele testa.
**Código:** `plan/PrecedencePolicy`, `plan/PrecedencePolicies`, `sinapse/RequestConditions`,
`sinapse/SearchVitality`, `benchmarks/.../harness/PrecedenceCellsMain`

---

> ## ⚠ Esta seção foi escrita e versionada ANTES de a medição rodar
>
> O commit que a introduz não contém dado nenhum das quatro células. Isso é verificável no histórico, e
> é o ponto: as seções §1 a §4 abaixo são **pré-registro**. Escolher a estatística depois de ver os
> números é o jardim dos caminhos que se bifurcam, e nos dados de ontem ele estava escancarado —
> **quatro estatísticas, duas respostas**:
>
> | estatística | quem ganha |
> |---|---|
> | total de inversões | v1 (420 contra 674) |
> | células zeradas | v2 (147 contra 121, em λ = 0,40) |
> | zeradas pareadas | v2, 54 a 15 |
> | pior célula | v1 (7 contra 9) |
>
> Com esse grau de liberdade, qualquer conclusão é alcançável escolhendo a régua depois. Então a régua
> é escolhida antes.

---

## 1. A previsão que está sendo testada

A v2 publicada combina **duas** mudanças em relação à v1: a representação (linha do tempo em vez de
alocação macro) e a política de precedência (preço ponderado em vez de reparo lexicográfico). A
comparação entre elas, portanto, não diz qual das duas explica o quê.

O relatório 11 mostrou que **política não se reduz a peso**: nenhum λ admissível alcança as 420
inversões da v1, e isso era esperado — reparo lexicográfico **fixa** a métrica, peso apenas a
**precifica**. Incentivo não iguala garantia na dimensão garantida.

O que sobrou sem explicação é o outro lado: a v2 aloca **~1,5 tópico mais** que a v1 em todo λ
(15,0–15,3 contra 13,52) e zera a ordem por completo em mais células. Duas leituras disputam isso:

* **A representação explica o ganho.** A linha do tempo coloca melhor, e bastaria dar-lhe o reparo
  lexicográfico para ter as duas coisas.
* **A política explica o ganho.** Não reparar libera capacidade: a vantagem de cobertura é consequência
  de *não corrigir a ordem*, e a representação não contribui.

A célula que decide é **v2′ = linha do tempo + reparo lexicográfico**, usando o reparador que já
existe aplicado à saída da linha do tempo.

### Os dois desfechos possíveis, declarados agora

| se… | então… |
|---|---|
| a v2′ **igualar ou bater** a v1 em inversões **e preservar** a vantagem de cobertura | a representação é o que explica o ganho. A resposta é linha do tempo com reparo lexicográfico, e a afirmação sobre representação se sustenta |
| a v2′ **perder cobertura até o nível da v1** | a vantagem era consequência da **política**, e a representação não contribui. A afirmação cai |

---

## 2. O desfecho primário, e por que este

> **Primário: a comparação pareada por célula, v2′ contra v1, em inversões `SOFT` — vitórias, empates
> e derrotas sobre as 360 células.**

Três razões, em ordem de peso.

**A unidade de entrega é um aluno, não uma coorte.** O serviço responde um plano por requisição. Somar
inversões sobre 360 células responde "quantas inversões existem no total", que é a pergunta de quem
administra um agregado; a pergunta do produto é "o plano deste aluno está bem ordenado?". A célula é a
unidade de análise porque é a unidade de entrega.

**O pareamento remove os dois confundidores que sobraram.** Instância e semente explicam muito da
variação — a v2 tem 11% de amplitude entre sementes (relatório 11 §7). Comparar v2′ e v1 na mesma
instância, mesma proveniência e mesma semente elimina os dois de uma vez, o que uma média sobre
totais não faz.

**Vitória/empate/derrota não exige uma função de dano que ninguém mediu.** O total pressupõe dano
**aditivo**: nove inversões são nove vezes uma. A pior célula pressupõe dano **categórico**: um plano
mal ordenado é ruim independentemente de quão mal. As duas são hipóteses sobre a experiência do aluno,
e **nenhuma das duas foi medida neste projeto**. Uma estatística ordinal não escolhe entre elas, e é
a escolha honesta quando a função de dano é desconhecida.

### O critério de cobertura, também declarado agora

"Preservar a vantagem de cobertura" precisa de número, ou é interpretável depois. A v1 aloca **13,52**
tópicos e a v2 **15,23**, uma vantagem de **1,71**. Declaro:

| zona | critério | leitura |
|---|---|---|
| **preservada** | v2′ ≥ **14,38** tópicos | ao menos **metade** da vantagem sobrevive |
| ambígua | entre 13,70 e 14,38 | a vantagem encolheu materialmente; nenhuma das duas leituras se sustenta sozinha |
| **perdida** | v2′ ≤ **13,70** | caiu ao nível da v1 (13,52 mais 10% da vantagem como folga) |

### As outras três estatísticas são secundárias, e serão reportadas todas

Total de inversões, células zeradas, zeradas pareadas e pior célula entram no relatório **inclusive
quando contrariarem a primária**. Uma secundária que contraria a primária é informação sobre a forma
da distribuição, não um resultado a ser escolhido no lugar dela.

---

## 3. A faixa admissível de λ

**Nada acima de `CONSTRAINT_VIOLATION` (0,50) entra em conclusão.** Acima daquele ponto uma
*preferência* violada custa mais que um *requisito* violado, e o vetor de pesos deixa de ser coerente
com a distinção que o contrato faz entre `HARD` e `SOFT`. Um resultado medido ali descreve um sistema
que ninguém proporia.

Consequência imediata, aplicada retroativamente ao relatório 11: **a evidência de bimodalidade passa a
ser citada em λ = 0,40, não em 0,80.** Em 0,40 a v2 zera 147 células contra 121 da v1 e a conclusão
sobrevive; as 160 células de 0,80 vêm de uma configuração incoerente e não devem sustentar afirmação.

As quatro células deste relatório rodam em **λ = 0,10**, o valor de produção, que é a configuração que
de fato atenderia um aluno. O braço lexicográfico depende de λ apenas através da busca — o reparo fixa
a métrica depois —, e o braço ponderado tem a sua sensibilidade a λ caracterizada no relatório 11.

---

## 4. A degeneração do espaço de busca da v1 — achada antes de medir, e o que se faz com ela

A invariante de **vitalidade da busca**, construída nesta etapa, disparou na primeira execução. Não na
v2: **na v1**.

Em **12 das 24 instâncias**, `SessionBudget` é exatamente a soma dos pisos por item, a folga é zero,
`StudyPlanFactory` não tem nada para distribuir, e **os 40 indivíduos da geração zero são o mesmo
plano**. O espaço de busca da v1 é um ponto único.

| coordenada | instâncias degeneradas |
|---|---|
| aperto 0,7 | **8 de 8** |
| aperto 1,0, esparso | **4 de 4** |
| aperto 1,0, compacto | 0 de 4 (folga 1) |
| aperto 1,5 | 0 de 8 (folga 5 a 13) |

O mecanismo: `SinapseMinimumDays` dá a cada tópico `ceil(estimatedMinutes / 240) = 1` sessão de piso,
logo a soma dos pisos é o número de tópicos; e em aperto ≤ 1,0 o calendário não comporta mais de uma
sessão por tópico. Orçamento e piso coincidem.

**Isso não é defeito do algoritmo genético nem da biblioteca — é uma propriedade da interação entre
os dois**, e tem três consequências que precisam ficar ditas:

1. **Em metade do fatorial a v1 não é uma busca.** É um alocador determinístico seguido de ordenação,
   reparo e colocação. As 2 440 avaliações reavaliam 40 cópias de um plano.
2. **Nessas 12 instâncias, "a v1 ganha" não é evidência sobre busca nem sobre representação.** É
   evidência de que um alocador forçado mais um reparo lexicográfico produz menos inversões que uma
   busca ponderada — afirmação legítima sobre motores, e muda nada sobre as duas hipóteses de §1.
3. **Explica a variância baixa da v1** registrada no relatório 09 (42 de 72 células idênticas entre
   cinco sementes): onde o espaço é um ponto, a semente não tem como importar.

### O que se faz com isso, decidido antes de ver os resultados

**O desfecho primário continua sobre as 24 instâncias.** Mover a população depois de descobrir uma
propriedade dos dados é precisamente o caminho que se bifurca que §2 existe para fechar.

**E uma secundária pré-declarada restringe às 12 instâncias não degeneradas** — aperto 1,0 compacto e
aperto 1,5 —, onde os dois braços de fato buscam. Só ali a comparação é sobre busca. Se primária e
secundária discordarem, a discordância é o achado, e será relatada como tal.

### A invariante foi dividida, e a razão importa

Ela nasceu esperando que empate total na geração zero fosse defeito. A medição mostrou que é
propriedade da instância. Então:

* **aborta**: a melhor aptidão final pior que a inicial, que significa elitismo quebrado — defeito de
  motor, conserta-se em código;
* **registra**: população inicial totalmente empatada, na coluna `search_initial_distinct` — fato sobre
  a comparação, que o relatório tem de dizer em vez de o harness esconder.

Recusar teria recusado 12 instâncias legítimas da biblioteca. Uma invariante que refuta a realidade
em vez de descrevê-la é uma invariante errada.

---

*As seções de resultado começam abaixo desta linha, e foram escritas depois de rodar.*

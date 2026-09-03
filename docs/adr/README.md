# Registros de decisão arquitetural (ADR)

Um **ADR** (*Architecture Decision Record*) é um documento curto que registra **uma** decisão
arquitetural: o contexto em que foi tomada, a decisão em si, as alternativas descartadas e as
consequências aceitas.

A pergunta que um ADR responde é sempre a mesma: *"por que isto está assim?"* — feita por alguém
que chega meses depois, olha uma escolha estranha e precisa saber se pode mudá-la.

## Por que este diretório existe

O inventário da etapa 00 registrou que o repositório **não tinha ADRs** (§4.4). As decisões
arquiteturais reais existiam, mas dentro de relatórios longos em `docs/revisao-ag/`, no formato de
relatório e sem índice próprio — o que torna impossível responder "quais decisões já foram tomadas
sobre este assunto?" sem reler tudo.

Este diretório começa na etapa 03b e registra, daqui em diante, as decisões não triviais.

## Formato

| Campo | Conteúdo |
|---|---|
| **Estado** | Proposto · **Aceito** · Substituído por ADR-NNNN · Revogado |
| **Contexto** | O que era verdade antes, e o problema concreto |
| **Decisão** | O que se decidiu, no imperativo |
| **Alternativas consideradas** | O que foi descartado, e por quê — a parte que mais se perde com o tempo |
| **Consequências** | O que se aceita em troca, inclusive o que piora |

Um ADR **não é editado** depois de aceito. Se a decisão mudar, escreve-se outro que o substitui, e o
antigo passa a "Substituído por". O histórico é o valor.

## Índice

| # | Título | Estado |
|---|---|---|
| [0001](./0001-abstracoes-de-calculo-no-dominio.md) | Contratos de cálculo de domínio vivem em `domain` | Aceito |
| [0002](./0002-remocao-de-codigo-inalcancavel.md) | Remover código inalcançável sem destino registrado; manter a camada tática | Aceito |
| [0003](./0003-composicao-de-producao-unica.md) | Unificar a cadeia de alocação; manter a composição de fitness duplicada, com guarda | Aceito |
| [0004](./0004-construtor-passo-a-passo-do-contexto.md) | `EvolutionContext` é montado por construtor passo a passo | Aceito |
| [0005](./0005-criterio-de-classificacao-de-erro.md) | O que separa `IllegalArgumentException` (400) de `DomainException` (422) | Aceito |
| [0006](./0006-separacao-de-responsabilidades-do-otimizador.md) | Três classes para quatro responsabilidades no otimizador | Aceito |

# Arquivo — medições do caminho de concurso (01/09/2026)

> **Estes números não são comparáveis a nada do harness atual.** Eles medem um sistema que não
> existe mais. Não os cite, não os copie para um relatório novo e não os use como linha de base.

## O que são

Saída bruta do harness anterior, gravada pelo commit
[`394fade`](../../../commit/394fade) — *fix(benchmarks): corrige empates do Spearman; caracteriza
saturação e decide não expandir a amostra*.

| Arquivo | O que continha |
|---|---|
| `resultados.csv` | 8 instâncias × 6 estratégias × 7 repetições: fitness, tempo, métricas de negócio |
| `robustez.csv` | Determinismo, variância entre 12 sementes e varredura de hiperparâmetros |
| `sensibilidade-pesos.csv` | Varredura de perturbação dos pesos da fitness |
| `limite-troca-pesos.csv` | Estudo do limite de troca entre os termos |
| `regime-alta-carga.csv` | Correlação de métricas no regime de alta carga |

Os relatórios que os interpretam continuam em `docs/revisao-ag/` — 03-validacao, 04-robustez,
05-fitness-function, 06-limite-troca-pesos e 08-saturacao-e-amostragem. Eles também descrevem o
sistema removido, e continuam válidos **como registro histórico**, não como descrição do produto.

## Por que não são comparáveis

O sistema sob teste era o caminho de concurso: `StudyOptimizerService` planejando um `Exam` com
`Subject`s a partir de um `StudentProfile` com autoavaliação e estado psicológico. Ele foi
**removido em EOA-4b**, pelo commit
[`3214e2d`](../../../commit/3214e2d) — *refactor(api)!: remove o caminho de concurso, e o que so
existia para ele*.

Três coisas mudaram de tal modo que nenhuma coluna sobrevive à travessia:

1. **A unidade de planejamento.** Disciplina de concurso virou tópico SINAPSE em EOA-4. A coluna
   `share_top_disciplina` não tem tradução.
2. **A função objetivo.** As duas penalidades multiplicativas e o termo de carga na escala 1..5
   saíram; `dailyLoadBudget` entrou na escala 1..4. Um `fitness_media` de lá e um de cá são números
   de funções diferentes.
3. **As instâncias.** `InstanceLibrary` gerava editais com peso de eixo temático e contagem de
   questões. O domínio atual não tem nenhum desses conceitos.

O limiar de regressão de 2 % que o harness antigo aplicava foi calibrado contra estas instâncias e
**não é herdável**. O harness novo mede a própria variância antes de propor um limiar.

## Por que ficaram no repositório

São o único registro do trabalho de medição anterior — apagá-los em silêncio destruiria a
proveniência de cinco relatórios que ainda estão em `docs/`. Ficam aqui, fora de
`benchmarks/results/`, porque quem abre a pasta de resultados para escrever a tese tem de encontrar
só dado atual.

# Regime de Alta Carga: Onde a Fitness Deixa de Ajudar a Retenção

**Objeto:** transformar a limitação **L4** de [`05-fitness-function.md`](./05-fitness-function.md) §8
— "a anti-correlação persiste em editais grandes com orçamento apertado" — de um ponto isolado
(`I8`) numa fronteira caracterizada.
**Código:** `benchmarks/…/instance/HighLoadInstanceLibrary` · `benchmarks/…/robustness/RegimeCorrelationMain`
**Dados:** `benchmarks/results/regime-alta-carga.csv`

---

## 1. Resultado em uma frase

**A premissa de L4 estava errada.** Não é o aperto do orçamento que faz a fitness prejudicar a
retenção — é a **dispersão dos pesos do edital**. A fronteira, antes da correção desta etapa, ficava
em torno de **30:1** de dispersão efetiva de importância; depois da correção, em torno de **200:1**.

---

## 2. Método

A investigação usou **três varreduras controladas**, cada uma variando exatamente uma coisa, sobre
22 instâncias sintéticas novas somadas às 8 originais. Em todas, a medida é a correlação de Spearman
entre a fitness e a % de disciplinas dentro da janela de retenção, calculada sobre as 6 estratégias
do harness (AG, guloso, ótimo de O₁, melhor-de-N, uniforme, aleatório).

Correlação negativa significa: **o planejador que a fitness classifica em primeiro entrega a pior
retenção.**

Uma nota de leitura importante: `n/d` significa que **todos** os planejadores saturam a métrica em
100% e a correlação é matematicamente indefinida — não é "correlação zero", é "não há o que
correlacionar". Nesses casos a fitness não está prejudicando nada.

---

## 3. Varredura 1 — razão demanda/orçamento: **hipótese falsificada**

`R = (Σ_s sessõesNecessárias_s) / orçamentoDeDias`. Oito instâncias, 20 disciplinas, horizonte de
300 dias, 21h/semana — **só o orçamento varia**.

| Instância | Razão | Spearman | Amplitude da retenção |
|---|---|---|---|
| `R0_50` | 0,50 | n/d | 0,0 pp |
| `R0_75` | 0,75 | n/d | 0,0 pp |
| `R1_00` | 1,00 | n/d | 0,0 pp |
| `R1_50` | 1,50 | n/d | 0,0 pp |
| `R2_00` | 2,00 | n/d | 0,0 pp |
| `R3_00` | 3,00 | n/d | 0,0 pp |
| `R4_00` | 4,00 | n/d | 0,0 pp |
| `R5_00` | 5,00 | n/d | 0,0 pp |

**A métrica satura em 100% para todo planejador em toda razão, inclusive 5,0** — mais apertado que
o próprio `I8` (6,0) e muito além de `I4` (1,8). A razão de demanda **não reproduz o fenômeno**.

Isso já bastaria para descartar a explicação de L4, e as instâncias originais confirmam: `I4` é
negativa a `R = 1,81` (folgado) e `I8` a `R = 6,02` (apertado), enquanto `R5_00`, entre as duas, não
mede correlação nenhuma. A razão não ordena nada.

## 4. Varredura 2 — número de disciplinas: **também falsificada**

Sete instâncias, 5 a 40 disciplinas, razão fixa em ~2,0, tudo o mais constante.

| Instância | Disciplinas | Spearman |
|---|---|---|
| `N05` … `N20` | 5–20 | n/d |
| `N25` | 25 | **+0,655** |
| `N30` | 30 | n/d |
| `N40` | 40 | −0,093 |

Correlações positivas ou indefinidas em quase toda a faixa. O número de disciplinas, isolado, também
não explica.

## 5. Varredura 3 — dispersão dos pesos: **confirmada**

Sete instâncias, 20 disciplinas, razão ~2,0, **só a dispersão dos pesos dos eixos varia**.

| Instância | Dispersão do eixo | Dispersão efetiva da importância | Spearman |
|---|---|---|---|
| `W0001` | 1:1 | 6:1 | n/d |
| `W0003` | 3:1 | 9:1 | n/d |
| `W0010` | 10:1 | 22:1 | n/d |
| `W0030` | 30:1 | **65:1** | **−0,455** |
| `W0100` | 100:1 | 218:1 | **−0,577** |
| `W0300` | 300:1 | 654:1 | **−0,880** |
| `W1000` | 1000:1 | 2182:1 | **−0,880** |

Monotônica e limpa. E, o que é decisivo, **unifica as instâncias originais**: ordenando todos os 30
pontos pela dispersão efetiva, o comportamento se organiza.

> **Dispersão efetiva** é a razão entre a maior e a menor importância normalizada. Não é a mesma
> coisa que a dispersão dos pesos dos eixos: a importância é `questionCount × pesoDoEixo × lacuna`,
> então um edital com pesos de eixo uniformes ainda apresenta dispersão pelos outros dois fatores.
> É por isso que `I8`, com pesos de eixo iguais, chega a 32:1.

## 6. A fronteira

Todos os 30 pontos, ordenados por dispersão efetiva (**estado anterior à correção**):

| Faixa de dispersão | Instâncias | Spearman |
|---|---|---|
| 3:1 – 22:1 | I1, I2, I6, N05–N30, R0_50–R5_00, W0001–W0010, I5, I7 | **n/d ou positiva** — nunca negativa |
| 32:1 – 2182:1 | I8, I3, W0030, N40, W0100, W0300, I4, W1000 | **sempre negativa** |

**Fronteira: entre 22:1 e 32:1 de dispersão efetiva de importância.**

---

## 7. Mecanismo

O termo de retenção ponderava a cobertura pela **mesma** importância normalizada que o termo de
edital usa:

```
O₁ = Σ_s Î_s · maestria_s          O₃ = Σ_s Î_s · cobertura_s
```

Com pesos muito dispersos, `Î` concentra-se em poucas disciplinas e **os dois objetivos concordam em
matar de fome as mesmas** — não há contrapeso dentro da fitness. A métrica de retenção, por sua vez,
conta disciplinas **sem ponderar**, então penaliza exatamente esse resultado.

Ou seja: não era o termo de retenção perdendo curvatura por falta de orçamento (a hipótese de L4).
Era o termo de retenção **não tendo voz própria**.

---

## 8. Correção aplicada

O termo de retenção passa a usar peso próprio, temperado:

```
w₃_s = Î_s^0,5 / Σ_k Î_k^0,5
O₃   = Σ_s w₃_s · min(1, d_s / sessõesNecessárias_s)
```

**A justificativa não é numérica.** O₁ pergunta *"quantos pontos este plano rende na prova?"*, e é
correto ponderá-lo pelo valor de prova. O₃ pergunta outra coisa: *"quanto do que foi estudado
sobrevive até a prova?"* O custo de esquecer não é proporcional ao valor de prova — esquecer uma
disciplina de peso baixo desperdiça os dias já gastos nela, e **esses dias custaram ao aluno o mesmo
que os dias gastos numa disciplina de peso alto**. A ponderação natural da retenção é mais achatada
que a da pontuação. O expoente 0,5 é o ponto temperado entre uniforme (0) e proporcional ao valor de
prova (1): uma disciplina que vale 100× mais na prova pesa 10× mais para efeito de retenção.

*Implementação:* `EvolutionContext.temper` e `RetentionObjective`.

## 9. Efeito medido

| Instância | Dispersão | ρ antes | ρ depois |
|---|---|---|---|
| `I7-horizonte-longo` | 17:1 | +0,000 | **+0,655** |
| `I8-escala` | 32:1 | −0,880 | −0,880 |
| `I3-grande-apertado` | 42:1 | −0,029 | **+0,257** |
| `W0030` | 65:1 | −0,455 | **+0,507** |
| `N40` | 72:1 | −0,093 | **+0,555** |
| `W0100` | 218:1 | −0,577 | **−0,270** |
| `W0300` | 654:1 | −0,880 | **−0,577** |
| `I4-pesos-extremos` | 1636:1 | −0,525 | **−0,093** |
| `W1000` | 2182:1 | −0,880 | **−0,577** |

**A fronteira move de ~30:1 para ~200:1 — seis vezes mais folga.** Abaixo de 200:1, apenas `I8`
permanece negativa. Acima, a correlação continua negativa mas atenuada em cerca de um terço.

Os regimes folgados não regrediram: a vantagem do AG sobre o guloso por prioridade melhorou na
média e não caiu abaixo do limiar de 2% em nenhuma instância (§6 de `05`, tabela atualizada).

## 10. O que fica em aberto

**`I8` é uma anomalia genuína.** Com dispersão de 32:1 — abaixo da nova fronteira — mantém
ρ = −0,880 inalterado pela correção. Não foi explicada por nenhuma das três varreduras.

Duas observações de contexto, sem as quais o número engana:

1. **A amplitude da retenção em `I8` é de 2,5 pontos percentuais** (97,5% a 100%). A correlação é
   forte em *ranking* e desprezível em *magnitude*. Um ρ de −0,880 sobre 2,5 pp não é o mesmo
   problema que um ρ de −0,941 sobre 50 pp, que era o estado de `I4` antes da etapa 05.
2. `I8` combina 40 disciplinas, 5h/dia e compressão pesada do cronograma. É plausível que o driver
   ali seja o agendador tático — `InterleavedCriticalStrategy` estuda apenas as **três** disciplinas
   mais críticas por dia, de modo que com 40 disciplinas a maioria é tocada raramente,
   independentemente do que a fitness escolha. Isso é hipótese, **não medida**, e o teste seria
   variar `INTERLEAVING_FOCUS_SIZE`.

**Recomendação:** registrar como limitação L4 revisada, não perseguir agora. O ganho marginal de
2,5 pp não justifica mexer no agendador tático, que é código fora do escopo desta revisão e tem seus
próprios consumidores.

## 11. Ameaças à validade

1. **Instâncias sintéticas.** Nenhum edital real foi testado. A pergunta que importa para o produto é
   qual dispersão efetiva os editais brasileiros de concurso realmente apresentam — se ficarem
   consistentemente abaixo de 200:1, a limitação é teórica.
2. **A métrica de retenção é uma simulação** com grade 4 fixo, herdada de `03` §9.
3. **`n/d` em massa nas varreduras 1 e 2 limita a inferência.** Não se pode concluir que aquelas
   variáveis *nunca* importam, só que não produzem o efeito nas faixas testadas.
4. **Cinco repetições por instância.** Suficiente para o sinal observado, que é grande; insuficiente
   para afirmações finas sobre pontos isolados como `N25` (+0,655 sobre 4 pp de amplitude).
5. **O expoente 0,5 não foi calibrado**, foi escolhido como ponto médio defensável. Não há medição de
   que 0,4 ou 0,6 seriam piores.

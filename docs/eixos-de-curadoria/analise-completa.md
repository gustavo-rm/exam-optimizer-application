# Saída completa da análise pré-registrada (EOA-1)

Gerado por `python3 -I benchmarks/analysis/curation_axes.py benchmarks/results/curation-axes.csv`, sem edição. Lido por [`../MEDICAO_EIXOS_DE_CURADORIA.md`](../MEDICAO_EIXOS_DE_CURADORIA.md).

## Integridade

- linhas: 7020
- invariants_ok e reproducible verdadeiros: 7020
- all-hard: linhas 1890, ordem invertida 0, órfãos 0
- guloso: células com mais de um plano entre sementes: 0 de 468
- importance_cv efetivo por (disciplinas, nível): 1/close=['0.000000']; 1/extreme=['0.000000']; 1/flat=['0.000000']; 2/close=['0.142857']; 2/extreme=['0.666667']; 2/flat=['0.000000']; 3/close=['0.141421']; 3/extreme=['0.808122']; 3/flat=['0.000000']

## Resumo: onde cada contraste passa o limiar pré-registrado

| eixo | contraste | bloco | motor | folga | métrica | média Δ | plano idêntico |
|---|---|---|---|---|---|---|---|
| importance | close − flat | F | greedy-baseline | 0.7 | order_inversions_all_edges | +1.47 | 0.00 |
| importance | close − flat | F | greedy-baseline | 1.0 | order_inversions_all_edges | +2.06 | 0.00 |
| importance | close − flat | F | greedy-baseline | 1.5 | order_inversions_all_edges | +2.11 | 0.00 |
| importance | extreme − flat | F | greedy-baseline | 0.7 | order_inversions_all_edges | +1.47 | 0.00 |
| importance | extreme − flat | F | greedy-baseline | 1.0 | order_inversions_all_edges | +2.06 | 0.00 |
| importance | extreme − flat | F | greedy-baseline | 1.5 | order_inversions_all_edges | +2.11 | 0.00 |
| importance | extreme − flat | F | ga-timeline | 1.5 | sessions_revision | +5.93 | 0.00 |
| importance | close − flat | S3 | greedy-baseline | 0.7 | topics_scheduled | +4.00 | 0.00 |
| importance | close − flat | S3 | ga-timeline | 1.5 | order_inversions_all_edges | +1.20 | 0.00 |
| importance | extreme − flat | S3 | greedy-baseline | 0.7 | topics_scheduled | +4.00 | 0.00 |
| importance | extreme − flat | S3 | ga-timeline | 0.7 | topics_scheduled | -1.67 | 0.00 |
| importance | extreme − flat | S3 | ga-timeline | 1.0 | sessions_revision | -1.27 | 0.00 |
| importance | extreme − flat | S3 | ga-timeline | 1.5 | order_inversions_all_edges | +2.67 | 0.00 |
| strength | all-soft − curated-mix | F | greedy-baseline | 0.7 | order_inversions_all_edges | +1.72 | 0.33 |
| strength | all-soft − curated-mix | F | greedy-baseline | 0.7 | dependents_without_prerequisite | +1.61 | 0.33 |
| strength | all-soft − curated-mix | F | greedy-baseline | 1.0 | order_inversions_all_edges | +3.22 | 0.33 |
| strength | all-soft − curated-mix | F | greedy-baseline | 1.5 | order_inversions_all_edges | +3.33 | 0.33 |
| strength | all-soft − curated-mix | F | ga | 0.7 | dependents_without_prerequisite | +1.25 | 0.00 |
| strength | all-soft − curated-mix | F | ga | 1.0 | order_inversions_all_edges | +2.23 | 0.00 |
| strength | all-soft − curated-mix | F | ga | 1.5 | order_inversions_all_edges | +2.19 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 0.7 | topics_scheduled | -2.22 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 0.7 | order_inversions_all_edges | +9.21 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 0.7 | dependents_without_prerequisite | +7.68 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 0.7 | long_plus_extended | +3.68 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 0.7 | sessions_revision | +4.85 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 1.0 | order_inversions_all_edges | +21.38 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 1.5 | order_inversions_all_edges | +19.48 | 0.00 |
| strength | all-soft − curated-mix | F | ga-timeline | 1.5 | sessions_revision | +4.74 | 0.00 |
| strength | all-hard − curated-mix | F | greedy-baseline | 0.7 | order_inversions_all_edges | -2.61 | 0.00 |
| strength | all-hard − curated-mix | F | greedy-baseline | 1.0 | order_inversions_all_edges | -4.19 | 0.00 |
| strength | all-hard − curated-mix | F | greedy-baseline | 1.5 | order_inversions_all_edges | -4.44 | 0.00 |
| strength | all-hard − curated-mix | F | ga | 1.0 | order_inversions_all_edges | -1.44 | 0.00 |
| strength | all-hard − curated-mix | F | ga | 1.5 | order_inversions_all_edges | -2.37 | 0.00 |
| strength | all-hard − curated-mix | F | ga-timeline | 0.7 | order_inversions_all_edges | -3.97 | 0.00 |
| strength | all-hard − curated-mix | F | ga-timeline | 0.7 | dependents_without_prerequisite | -1.89 | 0.00 |
| strength | all-hard − curated-mix | F | ga-timeline | 0.7 | sessions_revision | -1.78 | 0.00 |
| strength | all-hard − curated-mix | F | ga-timeline | 1.0 | order_inversions_all_edges | -6.52 | 0.00 |
| strength | all-hard − curated-mix | F | ga-timeline | 1.5 | order_inversions_all_edges | -6.53 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 0.7 | topics_scheduled | -2.60 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 0.7 | order_inversions_all_edges | +4.47 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 0.7 | dependents_without_prerequisite | +4.47 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 0.7 | long_plus_extended | +2.27 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 0.7 | sessions_revision | +1.93 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 1.0 | order_inversions_all_edges | +9.47 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 1.5 | order_inversions_all_edges | +9.67 | 0.00 |
| strength | all-soft − curated-mix | S1 | ga-timeline | 1.5 | sessions_revision | +1.67 | 0.00 |
| strength | all-hard − curated-mix | S1 | greedy-baseline | 1.0 | order_inversions_all_edges | -1.33 | 0.00 |
| strength | all-hard − curated-mix | S1 | greedy-baseline | 1.5 | order_inversions_all_edges | -2.00 | 0.00 |
| strength | all-hard − curated-mix | S1 | ga | 1.5 | order_inversions_all_edges | -2.53 | 0.00 |
| strength | all-hard − curated-mix | S1 | ga-timeline | 0.7 | order_inversions_all_edges | -2.27 | 0.00 |
| strength | all-hard − curated-mix | S1 | ga-timeline | 0.7 | dependents_without_prerequisite | -1.67 | 0.00 |
| strength | all-hard − curated-mix | S1 | ga-timeline | 1.0 | order_inversions_all_edges | -3.73 | 0.00 |
| strength | all-hard − curated-mix | S1 | ga-timeline | 1.5 | order_inversions_all_edges | -4.00 | 0.00 |
| strength | all-soft − curated-mix | S3 | greedy-baseline | 0.7 | order_inversions_all_edges | +2.33 | 0.00 |
| strength | all-soft − curated-mix | S3 | greedy-baseline | 0.7 | dependents_without_prerequisite | +2.33 | 0.00 |
| strength | all-soft − curated-mix | S3 | greedy-baseline | 1.0 | order_inversions_all_edges | +4.00 | 0.00 |
| strength | all-soft − curated-mix | S3 | greedy-baseline | 1.5 | order_inversions_all_edges | +4.67 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga | 1.0 | order_inversions_all_edges | +1.93 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga | 1.5 | order_inversions_all_edges | +4.13 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 0.7 | topics_scheduled | -6.73 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 0.7 | order_inversions_all_edges | +11.00 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 0.7 | dependents_without_prerequisite | +13.27 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 0.7 | long_plus_extended | +6.27 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 0.7 | sessions_revision | +5.33 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.0 | topics_scheduled | -2.40 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.0 | order_inversions_all_edges | +32.67 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.0 | dependents_without_prerequisite | +3.40 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.0 | long_plus_extended | +1.40 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.0 | sessions_revision | -3.00 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.5 | order_inversions_all_edges | +30.53 | 0.00 |
| strength | all-soft − curated-mix | S3 | ga-timeline | 1.5 | sessions_revision | +3.27 | 0.00 |
| strength | all-hard − curated-mix | S3 | greedy-baseline | 0.7 | topics_scheduled | -2.00 | 0.00 |
| strength | all-hard − curated-mix | S3 | greedy-baseline | 0.7 | order_inversions_all_edges | -2.33 | 0.00 |
| strength | all-hard − curated-mix | S3 | greedy-baseline | 1.0 | order_inversions_all_edges | -4.67 | 0.00 |
| strength | all-hard − curated-mix | S3 | greedy-baseline | 1.0 | dependents_without_prerequisite | -1.67 | 0.00 |
| strength | all-hard − curated-mix | S3 | greedy-baseline | 1.5 | order_inversions_all_edges | -6.67 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga | 0.7 | dependents_without_prerequisite | -2.33 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga | 1.0 | order_inversions_all_edges | -3.07 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga | 1.5 | order_inversions_all_edges | -4.60 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 0.7 | topics_scheduled | +3.53 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 0.7 | order_inversions_all_edges | -6.20 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 0.7 | dependents_without_prerequisite | -3.13 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 0.7 | long_plus_extended | -1.60 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 0.7 | sessions_revision | -2.47 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 1.0 | order_inversions_all_edges | -12.27 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 1.0 | sessions_revision | -1.67 | 0.00 |
| strength | all-hard − curated-mix | S3 | ga-timeline | 1.5 | order_inversions_all_edges | -9.87 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 0.7 | order_inversions_all_edges | +2.64 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 0.7 | dependents_without_prerequisite | +1.88 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 0.7 | long_plus_extended | +3.48 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 0.7 | sessions_revision | -4.15 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 1.0 | order_inversions_all_edges | +1.39 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 1.0 | sessions_revision | -4.73 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 1.5 | order_inversions_all_edges | +1.20 | 0.00 |
| effort | tiers-flat − uniform | F | ga-timeline | 1.5 | sessions_revision | -8.41 | 0.00 |
| effort | tiers-narrow − uniform | F | ga | 1.0 | topics_scheduled | -1.96 | 0.00 |
| effort | tiers-narrow − uniform | F | ga | 1.0 | sessions_revision | -7.13 | 0.00 |
| effort | tiers-narrow − uniform | F | ga | 1.5 | sessions_revision | -11.00 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 0.7 | order_inversions_all_edges | +3.97 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 0.7 | dependents_without_prerequisite | +2.12 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 0.7 | long_plus_extended | +3.33 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 0.7 | sessions_revision | -4.44 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 1.0 | order_inversions_all_edges | +2.27 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 1.0 | sessions_revision | -8.28 | 0.00 |
| effort | tiers-narrow − uniform | F | ga-timeline | 1.5 | sessions_revision | -14.60 | 0.00 |
| effort | tiers-platform − uniform | F | greedy-baseline | 0.7 | topics_scheduled | -7.11 | 0.00 |
| effort | tiers-platform − uniform | F | greedy-baseline | 0.7 | long_plus_extended | -2.07 | 0.00 |
| effort | tiers-platform − uniform | F | greedy-baseline | 1.0 | topics_scheduled | -6.59 | 0.00 |
| effort | tiers-platform − uniform | F | greedy-baseline | 1.0 | long_plus_extended | -1.78 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 0.7 | topics_scheduled | -6.33 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 0.7 | long_plus_extended | -1.78 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 1.0 | topics_scheduled | -6.53 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 1.0 | long_plus_extended | -2.67 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 1.0 | sessions_revision | -18.10 | 0.00 |
| effort | tiers-platform − uniform | F | ga | 1.5 | sessions_revision | -26.00 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 0.7 | topics_scheduled | -5.46 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 0.7 | order_inversions_all_edges | +2.00 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 0.7 | dependents_without_prerequisite | +2.87 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 0.7 | long_plus_extended | +2.16 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 0.7 | sessions_revision | -13.42 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 1.0 | topics_scheduled | -1.56 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 1.0 | order_inversions_all_edges | +3.47 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 1.0 | sessions_revision | -19.78 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 1.5 | order_inversions_all_edges | +1.22 | 0.00 |
| effort | tiers-platform − uniform | F | ga-timeline | 1.5 | sessions_revision | -23.95 | 0.00 |
| effort | tiers-flat − uniform | S1 | ga-timeline | 0.7 | order_inversions_all_edges | +1.13 | 0.00 |
| effort | tiers-flat − uniform | S1 | ga-timeline | 0.7 | long_plus_extended | +1.27 | 0.00 |
| effort | tiers-flat − uniform | S1 | ga-timeline | 0.7 | sessions_revision | -1.80 | 0.00 |
| effort | tiers-flat − uniform | S1 | ga-timeline | 1.0 | sessions_revision | -2.40 | 0.00 |
| effort | tiers-flat − uniform | S1 | ga-timeline | 1.5 | sessions_revision | -7.60 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga | 1.0 | order_inversions_all_edges | -1.00 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga | 1.0 | sessions_revision | -4.00 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga | 1.5 | sessions_revision | -6.00 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 0.7 | topics_scheduled | +1.13 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 0.7 | order_inversions_all_edges | +1.60 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 0.7 | sessions_revision | -1.73 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 1.0 | order_inversions_all_edges | +1.33 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 1.0 | sessions_revision | -5.07 | 0.00 |
| effort | tiers-narrow − uniform | S1 | ga-timeline | 1.5 | sessions_revision | -9.53 | 0.00 |
| effort | tiers-platform − uniform | S1 | greedy-baseline | 0.7 | topics_scheduled | -5.33 | 0.00 |
| effort | tiers-platform − uniform | S1 | greedy-baseline | 0.7 | long_plus_extended | -1.67 | 0.00 |
| effort | tiers-platform − uniform | S1 | greedy-baseline | 1.0 | topics_scheduled | -2.33 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 0.7 | topics_scheduled | -5.33 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 0.7 | long_plus_extended | -2.33 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 1.0 | topics_scheduled | -3.13 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 1.0 | order_inversions_all_edges | -1.40 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 1.0 | sessions_revision | -10.00 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 1.5 | order_inversions_all_edges | +1.20 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga | 1.5 | sessions_revision | -13.00 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga-timeline | 0.7 | topics_scheduled | -1.40 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga-timeline | 0.7 | sessions_revision | -6.73 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga-timeline | 1.0 | sessions_revision | -8.00 | 0.00 |
| effort | tiers-platform − uniform | S1 | ga-timeline | 1.5 | sessions_revision | -13.20 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 0.7 | dependents_without_prerequisite | +1.33 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 0.7 | long_plus_extended | +3.87 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 0.7 | sessions_revision | -4.27 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 1.0 | order_inversions_all_edges | +2.00 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 1.0 | sessions_revision | -5.73 | 0.00 |
| effort | tiers-flat − uniform | S3 | ga-timeline | 1.5 | sessions_revision | -10.93 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga | 1.0 | topics_scheduled | -3.53 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga | 1.0 | sessions_revision | -11.00 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga | 1.5 | sessions_revision | -15.00 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 0.7 | order_inversions_all_edges | +1.47 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 0.7 | dependents_without_prerequisite | +1.07 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 0.7 | sessions_revision | -7.47 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 1.0 | order_inversions_all_edges | +3.33 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 1.0 | sessions_revision | -13.07 | 0.00 |
| effort | tiers-narrow − uniform | S3 | ga-timeline | 1.5 | sessions_revision | -20.20 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 0.7 | topics_scheduled | -11.33 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 0.7 | long_plus_extended | -4.33 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 1.0 | topics_scheduled | -10.33 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 1.0 | order_inversions_all_edges | -2.00 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 1.0 | dependents_without_prerequisite | +1.67 | 0.00 |
| effort | tiers-platform − uniform | S3 | greedy-baseline | 1.0 | long_plus_extended | -1.67 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 0.7 | topics_scheduled | -12.67 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 0.7 | long_plus_extended | -3.33 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 1.0 | topics_scheduled | -12.60 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 1.0 | order_inversions_all_edges | -1.40 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 1.0 | long_plus_extended | -3.00 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 1.0 | sessions_revision | -26.13 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga | 1.5 | sessions_revision | -38.00 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 0.7 | topics_scheduled | -11.60 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 0.7 | dependents_without_prerequisite | +1.27 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 0.7 | sessions_revision | -21.67 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 1.0 | topics_scheduled | -4.27 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 1.0 | order_inversions_all_edges | +3.73 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 1.0 | long_plus_extended | -1.40 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 1.0 | sessions_revision | -33.20 | 0.00 |
| effort | tiers-platform − uniform | S3 | ga-timeline | 1.5 | sessions_revision | -29.40 | 0.00 |

## Taxa de plano idêntico por contraste, motor e bloco (todas as folgas)

| eixo | contraste | bloco | greedy-baseline | ga | ga-timeline |
|---|---|---|---|---|---|
| importance | close − flat | F | 0/540 | 184/540 | 14/540 |
| importance | extreme − flat | F | 0/540 | 183/540 | 7/540 |
| importance | close − flat | S1 | 45/45 | 45/45 | 45/45 |
| importance | extreme − flat | S1 | 45/45 | 45/45 | 45/45 |
| importance | close − flat | S3 | 0/45 | 15/45 | 0/45 |
| importance | extreme − flat | S3 | 0/45 | 15/45 | 0/45 |
| strength | all-soft − curated-mix | F | 180/540 | 0/540 | 0/540 |
| strength | all-hard − curated-mix | F | 0/540 | 0/540 | 0/540 |
| strength | all-soft − curated-mix | S1 | 45/45 | 25/45 | 0/45 |
| strength | all-hard − curated-mix | S1 | 10/45 | 10/45 | 0/45 |
| strength | all-soft − curated-mix | S3 | 0/45 | 0/45 | 0/45 |
| strength | all-hard − curated-mix | S3 | 0/45 | 0/45 | 0/45 |
| effort | tiers-flat − uniform | F | 405/405 | 135/405 | 0/405 |
| effort | tiers-narrow − uniform | F | 0/405 | 0/405 | 0/405 |
| effort | tiers-platform − uniform | F | 0/405 | 0/405 | 0/405 |
| effort | tiers-flat − uniform | S1 | 45/45 | 15/45 | 0/45 |
| effort | tiers-narrow − uniform | S1 | 0/45 | 0/45 | 0/45 |
| effort | tiers-platform − uniform | S1 | 0/45 | 0/45 | 0/45 |
| effort | tiers-flat − uniform | S3 | 45/45 | 15/45 | 0/45 |
| effort | tiers-narrow − uniform | S3 | 0/45 | 0/45 | 0/45 |
| effort | tiers-platform − uniform | S3 | 0/45 | 0/45 | 0/45 |

### importance: contrastes contra `flat`, bloco F (2 disciplinas)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| close − flat | greedy-baseline | 0.7 | 180 | 0.00 | topics_scheduled | -0.03 | +0.0 | 25/110/45 | 0.00 | -0.17 -0.42 +0.50 | não |
| close − flat | greedy-baseline | 0.7 | 180 | 0.00 | order_inversions_all_edges | +1.47 | +1.0 | 105/70/5 | 0.00 | +1.00 +1.33 +2.08 | **sim** |
| close − flat | greedy-baseline | 0.7 | 180 | 0.00 | dependents_without_prerequisite | +0.75 | +0.0 | 70/90/20 | 0.00 | +0.50 +0.92 +0.83 | não |
| close − flat | greedy-baseline | 0.7 | 180 | 0.00 | long_plus_extended | +0.42 | +1.0 | 105/25/50 | 0.00 | +1.00 -0.75 +1.00 | não |
| close − flat | greedy-baseline | 0.7 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 180 | 0.00 | topics_scheduled | +0.28 | +0.0 | 35/140/5 | 0.00 | -0.08 +0.33 +0.58 | não |
| close − flat | greedy-baseline | 1.0 | 180 | 0.00 | order_inversions_all_edges | +2.06 | +0.0 | 85/95/0 | 0.00 | +1.25 +2.08 +2.83 | **sim** |
| close − flat | greedy-baseline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 10/160/10 | 0.00 | +0.08 +0.08 -0.17 | não |
| close − flat | greedy-baseline | 1.0 | 180 | 0.00 | long_plus_extended | -0.17 | +0.0 | 10/145/25 | 0.00 | -0.17 -0.50 +0.17 | não |
| close − flat | greedy-baseline | 1.0 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 180 | 0.00 | order_inversions_all_edges | +2.11 | +0.0 | 80/100/0 | 0.00 | +1.33 +2.33 +2.67 | **sim** |
| close − flat | greedy-baseline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 180 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 180 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 180 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 180 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 180 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 180 | 0.02 | topics_scheduled | +0.02 | +0.0 | 49/80/51 | 1.07 | -0.22 +0.03 +0.25 | não |
| close − flat | ga | 1.0 | 180 | 0.02 | order_inversions_all_edges | -0.03 | +0.0 | 37/103/40 | 0.79 | -0.15 +0.32 -0.25 | não |
| close − flat | ga | 1.0 | 180 | 0.02 | dependents_without_prerequisite | -0.01 | +0.0 | 7/166/7 | 0.12 | +0.03 -0.07 +0.00 | não |
| close − flat | ga | 1.0 | 180 | 0.02 | long_plus_extended | -0.06 | +0.0 | 22/135/23 | 0.40 | -0.23 +0.02 +0.05 | não |
| close − flat | ga | 1.0 | 180 | 0.02 | sessions_revision | +0.02 | +0.0 | 10/163/7 | 0.15 | +0.02 -0.05 +0.08 | não |
| close − flat | ga | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 180 | 0.00 | order_inversions_all_edges | +0.46 | +0.0 | 63/80/37 | 1.05 | +0.18 +1.12 +0.07 | não |
| close − flat | ga | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 180 | 0.06 | topics_scheduled | +0.02 | +0.0 | 31/122/27 | 0.53 | +0.02 -0.03 +0.07 | não |
| close − flat | ga-timeline | 0.7 | 180 | 0.06 | order_inversions_all_edges | +0.16 | +0.0 | 48/93/39 | 1.42 | +0.47 +0.12 -0.10 | não |
| close − flat | ga-timeline | 0.7 | 180 | 0.06 | dependents_without_prerequisite | -0.03 | +0.0 | 30/125/25 | 0.57 | -0.18 +0.02 +0.07 | não |
| close − flat | ga-timeline | 0.7 | 180 | 0.06 | long_plus_extended | -0.14 | +0.0 | 21/116/43 | 0.50 | -0.12 -0.13 -0.18 | não |
| close − flat | ga-timeline | 0.7 | 180 | 0.06 | sessions_revision | -0.06 | +0.0 | 48/84/48 | 1.00 | -0.12 -0.07 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 180 | 0.02 | topics_scheduled | +0.05 | +0.0 | 18/151/11 | 0.28 | +0.02 +0.03 +0.10 | não |
| close − flat | ga-timeline | 1.0 | 180 | 0.02 | order_inversions_all_edges | -0.46 | +0.0 | 48/76/56 | 2.29 | -1.25 +0.23 -0.37 | não |
| close − flat | ga-timeline | 1.0 | 180 | 0.02 | dependents_without_prerequisite | +0.03 | +0.0 | 6/168/6 | 0.15 | +0.07 +0.02 +0.02 | não |
| close − flat | ga-timeline | 1.0 | 180 | 0.02 | long_plus_extended | +0.03 | +0.0 | 9/168/3 | 0.10 | +0.00 +0.07 +0.03 | não |
| close − flat | ga-timeline | 1.0 | 180 | 0.02 | sessions_revision | +0.27 | +0.0 | 82/34/64 | 2.42 | +0.27 +0.52 +0.03 | não |
| close − flat | ga-timeline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 180 | 0.00 | order_inversions_all_edges | +0.34 | +0.0 | 59/74/47 | 2.41 | +0.05 +0.62 +0.35 | não |
| close − flat | ga-timeline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 180 | 0.00 | sessions_revision | +0.72 | +0.0 | 88/13/79 | 7.45 | +0.93 +0.95 +0.27 | não |
| extreme − flat | greedy-baseline | 0.7 | 180 | 0.00 | topics_scheduled | -0.03 | +0.0 | 25/110/45 | 0.00 | -0.17 -0.42 +0.50 | não |
| extreme − flat | greedy-baseline | 0.7 | 180 | 0.00 | order_inversions_all_edges | +1.47 | +1.0 | 105/70/5 | 0.00 | +1.00 +1.33 +2.08 | **sim** |
| extreme − flat | greedy-baseline | 0.7 | 180 | 0.00 | dependents_without_prerequisite | +0.75 | +0.0 | 70/90/20 | 0.00 | +0.50 +0.92 +0.83 | não |
| extreme − flat | greedy-baseline | 0.7 | 180 | 0.00 | long_plus_extended | +0.42 | +1.0 | 105/25/50 | 0.00 | +1.00 -0.75 +1.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 180 | 0.00 | topics_scheduled | +0.28 | +0.0 | 35/140/5 | 0.00 | -0.08 +0.33 +0.58 | não |
| extreme − flat | greedy-baseline | 1.0 | 180 | 0.00 | order_inversions_all_edges | +2.06 | +0.0 | 85/95/0 | 0.00 | +1.25 +2.08 +2.83 | **sim** |
| extreme − flat | greedy-baseline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 10/160/10 | 0.00 | +0.08 +0.08 -0.17 | não |
| extreme − flat | greedy-baseline | 1.0 | 180 | 0.00 | long_plus_extended | -0.17 | +0.0 | 10/145/25 | 0.00 | -0.17 -0.50 +0.17 | não |
| extreme − flat | greedy-baseline | 1.0 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 180 | 0.00 | order_inversions_all_edges | +2.11 | +0.0 | 80/100/0 | 0.00 | +1.33 +2.33 +2.67 | **sim** |
| extreme − flat | greedy-baseline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 180 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 180 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 180 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 180 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 180 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 180 | 0.02 | topics_scheduled | +0.17 | +0.0 | 56/79/45 | 1.12 | -0.05 +0.22 +0.35 | não |
| extreme − flat | ga | 1.0 | 180 | 0.02 | order_inversions_all_edges | +0.03 | +0.0 | 36/104/40 | 0.79 | -0.05 +0.40 -0.25 | não |
| extreme − flat | ga | 1.0 | 180 | 0.02 | dependents_without_prerequisite | +0.04 | +0.0 | 11/164/5 | 0.15 | +0.05 +0.07 +0.00 | não |
| extreme − flat | ga | 1.0 | 180 | 0.02 | long_plus_extended | -0.04 | +0.0 | 25/127/28 | 0.45 | -0.15 -0.02 +0.05 | não |
| extreme − flat | ga | 1.0 | 180 | 0.02 | sessions_revision | +0.04 | +0.0 | 12/164/4 | 0.14 | +0.07 -0.02 +0.08 | não |
| extreme − flat | ga | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 180 | 0.00 | order_inversions_all_edges | +0.70 | +0.0 | 69/81/30 | 1.09 | +0.28 +1.35 +0.47 | não |
| extreme − flat | ga | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 180 | 0.04 | topics_scheduled | -0.04 | +0.0 | 29/121/30 | 0.60 | -0.02 -0.12 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 180 | 0.04 | order_inversions_all_edges | +0.29 | +0.0 | 51/83/46 | 1.67 | +1.00 -0.12 -0.02 | não |
| extreme − flat | ga-timeline | 0.7 | 180 | 0.04 | dependents_without_prerequisite | +0.12 | +0.0 | 37/120/23 | 0.55 | -0.10 +0.22 +0.23 | não |
| extreme − flat | ga-timeline | 0.7 | 180 | 0.04 | long_plus_extended | -0.09 | +0.0 | 29/108/43 | 0.53 | +0.12 -0.02 -0.38 | não |
| extreme − flat | ga-timeline | 0.7 | 180 | 0.04 | sessions_revision | -0.25 | +0.0 | 38/81/61 | 1.08 | -0.32 -0.42 -0.02 | não |
| extreme − flat | ga-timeline | 1.0 | 180 | 0.00 | topics_scheduled | +0.06 | +0.0 | 18/152/10 | 0.22 | +0.12 -0.03 +0.10 | não |
| extreme − flat | ga-timeline | 1.0 | 180 | 0.00 | order_inversions_all_edges | +0.29 | +0.0 | 68/68/44 | 2.34 | +0.28 +0.72 -0.13 | não |
| extreme − flat | ga-timeline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | +0.02 | +0.0 | 4/171/5 | 0.11 | +0.00 +0.02 +0.05 | não |
| extreme − flat | ga-timeline | 1.0 | 180 | 0.00 | long_plus_extended | +0.02 | +0.0 | 9/166/5 | 0.11 | +0.03 +0.00 +0.03 | não |
| extreme − flat | ga-timeline | 1.0 | 180 | 0.00 | sessions_revision | +0.26 | +0.0 | 77/28/75 | 2.32 | -0.32 +0.82 +0.27 | não |
| extreme − flat | ga-timeline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 180 | 0.00 | order_inversions_all_edges | +0.81 | +0.0 | 62/74/44 | 2.43 | +0.25 +1.30 +0.88 | não |
| extreme − flat | ga-timeline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 180 | 0.00 | sessions_revision | +5.93 | +4.0 | 125/6/49 | 6.91 | +7.65 +5.33 +4.82 | **sim** |

### importance: contrastes contra `flat`, bloco S, 1 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| close − flat | greedy-baseline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |

### importance: contrastes contra `flat`, bloco S, 3 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| close − flat | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | +4.00 | +4.0 | 15/0/0 | 0.00 | +3.00 +5.00 +4.00 | **sim** |
| close − flat | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +1.00 | +1.0 | 10/5/0 | 0.00 | +1.00 +0.00 +2.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 10/5/0 | 0.00 | +1.00 +0.00 +1.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -0.67 | -1.0 | 5/0/10 | 0.00 | +1.00 -2.00 -1.00 | não |
| close − flat | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.67 | +0.0 | 5/5/5 | 0.00 | +4.00 +0.00 -2.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +1.33 | +2.0 | 10/5/0 | 0.00 | +2.00 +0.00 +2.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | +1.00 | +1.0 | 10/5/0 | 0.00 | +2.00 +1.00 +0.00 | não |
| close − flat | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.67 | +0.0 | 5/10/0 | 0.00 | +2.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.0 | 15 | 0.00 | topics_scheduled | -0.60 | +0.0 | 6/4/5 | 2.49 | -2.40 -0.40 +1.00 | não |
| close − flat | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.07 | +0.0 | 4/8/3 | 0.43 | -0.40 +0.00 +0.60 | não |
| close − flat | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.33 | +0.0 | 0/12/3 | 0.30 | +0.00 +0.00 -1.00 | não |
| close − flat | ga | 1.0 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 1/9/5 | 0.80 | -0.20 -0.40 -0.40 | não |
| close − flat | ga | 1.0 | 15 | 0.00 | sessions_revision | -0.13 | +0.0 | 0/13/2 | 0.16 | +0.00 -0.40 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.67 | -1.0 | 5/2/8 | 1.67 | -0.60 +0.00 -1.40 | não |
| close − flat | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -0.60 | +0.0 | 7/1/7 | 2.96 | -1.20 -0.40 -0.20 | não |
| close − flat | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.07 | +0.0 | 5/4/6 | 2.05 | +0.20 -0.40 +0.40 | não |
| close − flat | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -0.27 | -1.0 | 5/2/8 | 1.39 | -0.20 +0.20 -0.80 | não |
| close − flat | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +0.07 | +0.0 | 6/4/5 | 1.32 | +0.60 -0.20 -0.20 | não |
| close − flat | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | +0.47 | +0.0 | 6/2/7 | 3.26 | +0.60 +0.60 +0.20 | não |
| close − flat | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.27 | +0.0 | 7/3/5 | 1.44 | -0.80 +1.00 +0.60 | não |
| close − flat | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.33 | +0.0 | 7/5/3 | 1.71 | -0.60 +1.20 +0.40 | não |
| close − flat | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.13 | +0.0 | 5/7/3 | 0.74 | +0.20 +0.00 +0.20 | não |
| close − flat | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.20 | +0.0 | 5/7/3 | 0.66 | +0.20 +0.00 +0.40 | não |
| close − flat | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -0.33 | -3.0 | 6/0/9 | 3.39 | +1.80 +0.00 -2.80 | não |
| close − flat | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +1.20 | +1.0 | 9/4/2 | 2.18 | +1.40 +1.80 +0.40 | **sim** |
| close − flat | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| close − flat | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | +1.20 | +3.0 | 9/0/6 | 5.06 | -2.40 +3.40 +2.60 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | +4.00 | +4.0 | 15/0/0 | 0.00 | +3.00 +5.00 +4.00 | **sim** |
| extreme − flat | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +1.00 | +1.0 | 10/5/0 | 0.00 | +1.00 +0.00 +2.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 10/5/0 | 0.00 | +1.00 +0.00 +1.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -0.67 | -1.0 | 5/0/10 | 0.00 | +1.00 -2.00 -1.00 | não |
| extreme − flat | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.67 | +0.0 | 5/5/5 | 0.00 | +4.00 +0.00 -2.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +1.33 | +2.0 | 10/5/0 | 0.00 | +2.00 +0.00 +2.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | +1.00 | +1.0 | 10/5/0 | 0.00 | +2.00 +1.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.67 | +0.0 | 5/10/0 | 0.00 | +2.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.0 | 15 | 0.00 | topics_scheduled | -0.93 | +0.0 | 4/7/4 | 1.45 | -3.20 +0.00 +0.40 | não |
| extreme − flat | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.07 | +0.0 | 4/8/3 | 0.46 | -0.60 +0.20 +0.60 | não |
| extreme − flat | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.47 | +0.0 | 0/11/4 | 0.27 | +0.00 +0.00 -1.40 | não |
| extreme − flat | ga | 1.0 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.27 | -0.20 +0.00 -0.80 | não |
| extreme − flat | ga | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.47 | +0.0 | 3/8/4 | 1.37 | -0.40 -0.20 -0.80 | não |
| extreme − flat | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -1.67 | -2.0 | 3/1/11 | 1.65 | -3.20 -1.60 -0.20 | **sim** |
| extreme − flat | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.67 | +1.0 | 8/2/5 | 2.06 | +1.40 +0.00 +0.60 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -1.00 | -1.0 | 1/4/10 | 0.71 | -1.40 +0.00 -1.60 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +0.67 | +1.0 | 8/3/4 | 1.34 | +1.40 +0.60 +0.00 | não |
| extreme − flat | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | +1.00 | +0.0 | 7/4/4 | 2.59 | +3.40 -0.20 -0.20 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | -1.0 | 5/2/8 | 1.21 | -0.40 -0.80 +1.20 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.20 | -1.0 | 6/1/8 | 2.82 | -1.00 +1.60 -1.20 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.13 | +0.0 | 3/9/3 | 0.69 | +0.80 +0.20 -0.60 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.20 | +0.0 | 5/7/3 | 0.91 | +0.40 -0.20 +0.40 | não |
| extreme − flat | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -1.27 | -1.0 | 6/1/8 | 4.09 | -0.20 -0.20 -3.40 | **sim** |
| extreme − flat | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +2.67 | +3.0 | 11/2/2 | 2.89 | +1.60 +2.60 +3.80 | **sim** |
| extreme − flat | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| extreme − flat | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -0.07 | +1.0 | 9/0/6 | 4.35 | +1.20 +0.80 -2.20 | não |

### strength: contrastes contra `curated-mix`, bloco F (2 disciplinas)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| all-soft − curated-mix | greedy-baseline | 0.7 | 180 | 0.33 | topics_scheduled | +0.06 | +0.0 | 20/130/30 | 0.00 | -0.17 -0.17 +0.50 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 180 | 0.33 | order_inversions_all_edges | +1.72 | +1.0 | 110/60/10 | 0.00 | +1.00 +0.33 +3.83 | **sim** |
| all-soft − curated-mix | greedy-baseline | 0.7 | 180 | 0.33 | dependents_without_prerequisite | +1.61 | +2.0 | 120/60/0 | 0.00 | +1.67 +1.50 +1.67 | **sim** |
| all-soft − curated-mix | greedy-baseline | 0.7 | 180 | 0.33 | long_plus_extended | +0.00 | +0.0 | 30/130/20 | 0.00 | +0.50 -0.33 -0.17 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 180 | 0.33 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 180 | 0.33 | topics_scheduled | +0.06 | +0.0 | 10/170/0 | 0.00 | +0.17 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 180 | 0.33 | order_inversions_all_edges | +3.22 | +3.0 | 120/60/0 | 0.00 | +2.50 +1.83 +5.33 | **sim** |
| all-soft − curated-mix | greedy-baseline | 1.0 | 180 | 0.33 | dependents_without_prerequisite | +0.11 | +0.0 | 20/160/0 | 0.00 | +0.17 +0.17 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 180 | 0.33 | long_plus_extended | +0.00 | +0.0 | 10/160/10 | 0.00 | +0.17 -0.17 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 180 | 0.33 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 180 | 0.33 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 180 | 0.33 | order_inversions_all_edges | +3.33 | +3.0 | 120/60/0 | 0.00 | +2.67 +2.00 +5.33 | **sim** |
| all-soft − curated-mix | greedy-baseline | 1.5 | 180 | 0.33 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 180 | 0.33 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 180 | 0.33 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 180 | 0.00 | topics_scheduled | +0.42 | +0.0 | 75/90/15 | 0.00 | +0.75 -0.25 +0.75 | não |
| all-soft − curated-mix | ga | 0.7 | 180 | 0.00 | order_inversions_all_edges | +0.42 | +0.5 | 90/30/60 | 0.00 | +0.50 -1.00 +1.75 | não |
| all-soft − curated-mix | ga | 0.7 | 180 | 0.00 | dependents_without_prerequisite | +1.25 | +1.5 | 135/45/0 | 0.00 | +1.50 +2.00 +0.25 | **sim** |
| all-soft − curated-mix | ga | 0.7 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 60/60/60 | 0.00 | +1.00 -0.25 -0.75 | não |
| all-soft − curated-mix | ga | 0.7 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.0 | 180 | 0.00 | topics_scheduled | -0.57 | -1.0 | 44/35/101 | 1.03 | -0.30 -0.27 -1.13 | não |
| all-soft − curated-mix | ga | 1.0 | 180 | 0.00 | order_inversions_all_edges | +2.23 | +2.0 | 164/12/4 | 1.27 | +2.07 +1.98 +2.65 | **sim** |
| all-soft − curated-mix | ga | 1.0 | 180 | 0.00 | dependents_without_prerequisite | +0.33 | +0.0 | 58/121/1 | 0.16 | +0.63 +0.37 +0.00 | não |
| all-soft − curated-mix | ga | 1.0 | 180 | 0.00 | long_plus_extended | -0.06 | +0.0 | 23/116/41 | 0.39 | +0.22 -0.23 -0.15 | não |
| all-soft − curated-mix | ga | 1.0 | 180 | 0.00 | sessions_revision | +0.08 | +0.0 | 14/166/0 | 0.13 | +0.13 +0.07 +0.03 | não |
| all-soft − curated-mix | ga | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 180 | 0.00 | order_inversions_all_edges | +2.19 | +2.0 | 151/13/16 | 1.46 | +2.75 +2.52 +1.32 | **sim** |
| all-soft − curated-mix | ga | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | topics_scheduled | -2.22 | +0.0 | 7/94/79 | 0.65 | -2.62 -1.65 -2.38 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | order_inversions_all_edges | +9.21 | +9.0 | 174/5/1 | 2.59 | +10.60 +8.27 +8.75 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | dependents_without_prerequisite | +7.68 | +7.0 | 180/0/0 | 0.95 | +8.12 +5.93 +8.98 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | long_plus_extended | +3.68 | +4.0 | 147/28/5 | 0.51 | +4.15 +2.43 +4.47 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | sessions_revision | +4.85 | +4.0 | 166/4/10 | 1.00 | +4.93 +3.75 +5.87 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | topics_scheduled | -0.03 | +0.0 | 14/152/14 | 0.27 | -0.12 +0.07 -0.03 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | order_inversions_all_edges | +21.38 | +22.0 | 180/0/0 | 3.81 | +21.45 +20.48 +22.20 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | +0.27 | +0.0 | 26/153/1 | 0.22 | +0.32 +0.00 +0.50 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | long_plus_extended | +0.15 | +0.0 | 25/154/1 | 0.11 | +0.28 +0.03 +0.13 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | sessions_revision | +0.34 | +0.0 | 89/26/65 | 2.49 | +0.52 +0.50 +0.02 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | order_inversions_all_edges | +19.48 | +19.0 | 180/0/0 | 3.98 | +19.35 +19.85 +19.23 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | sessions_revision | +4.74 | +3.0 | 127/7/46 | 6.23 | +4.60 +4.88 +4.75 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 180 | 0.00 | topics_scheduled | -0.03 | +0.0 | 30/105/45 | 0.00 | +0.08 -0.17 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 180 | 0.00 | order_inversions_all_edges | -2.61 | -3.0 | 0/10/170 | 0.00 | -3.50 -2.17 -2.17 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 180 | 0.00 | dependents_without_prerequisite | -0.61 | -1.0 | 0/80/100 | 0.00 | -0.42 -1.17 -0.25 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 180 | 0.00 | long_plus_extended | -0.08 | +0.0 | 0/165/15 | 0.00 | +0.00 -0.17 -0.08 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 180 | 0.00 | topics_scheduled | +0.08 | +0.0 | 20/145/15 | 0.00 | +0.42 -0.33 +0.17 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 180 | 0.00 | order_inversions_all_edges | -4.19 | -4.0 | 0/0/180 | 0.00 | -4.00 -4.67 -3.92 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | -0.11 | +0.0 | 0/160/20 | 0.00 | +0.00 -0.25 -0.08 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 5/170/5 | 0.00 | +0.00 -0.08 +0.08 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 180 | 0.00 | order_inversions_all_edges | -4.44 | -4.0 | 0/0/180 | 0.00 | -4.00 -5.33 -4.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 180 | 0.00 | topics_scheduled | -0.08 | +0.0 | 30/120/30 | 0.00 | +0.25 -0.50 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 180 | 0.00 | order_inversions_all_edges | -0.92 | -1.0 | 0/60/120 | 0.00 | -1.75 -1.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 180 | 0.00 | dependents_without_prerequisite | -0.42 | +0.0 | 0/105/75 | 0.00 | -0.25 -1.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 15/150/15 | 0.00 | +0.00 -0.25 +0.25 | não |
| all-hard − curated-mix | ga | 0.7 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.0 | 180 | 0.00 | topics_scheduled | +0.19 | +0.0 | 63/81/36 | 0.86 | +0.48 +0.27 -0.17 | não |
| all-hard − curated-mix | ga | 1.0 | 180 | 0.00 | order_inversions_all_edges | -1.44 | -2.0 | 0/39/141 | 0.42 | -2.17 -1.72 -0.45 | **sim** |
| all-hard − curated-mix | ga | 1.0 | 180 | 0.00 | dependents_without_prerequisite | -0.11 | +0.0 | 0/164/16 | 0.06 | +0.00 -0.32 +0.00 | não |
| all-hard − curated-mix | ga | 1.0 | 180 | 0.00 | long_plus_extended | +0.06 | +0.0 | 31/129/20 | 0.36 | +0.30 +0.08 -0.20 | não |
| all-hard − curated-mix | ga | 1.0 | 180 | 0.00 | sessions_revision | -0.02 | +0.0 | 1/175/4 | 0.06 | +0.00 -0.03 -0.02 | não |
| all-hard − curated-mix | ga | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 180 | 0.00 | order_inversions_all_edges | -2.37 | -2.0 | 0/6/174 | 0.98 | -2.18 -2.90 -2.03 | **sim** |
| all-hard − curated-mix | ga | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 180 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | topics_scheduled | +0.74 | +0.0 | 64/109/7 | 0.64 | +0.62 +1.25 +0.35 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | order_inversions_all_edges | -3.97 | -4.0 | 0/2/178 | 1.02 | -4.78 -3.72 -3.42 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | dependents_without_prerequisite | -1.89 | -2.0 | 0/30/150 | 0.39 | -1.15 -1.75 -2.78 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | long_plus_extended | -0.06 | +0.0 | 49/78/53 | 0.71 | +0.00 -0.83 +0.67 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 180 | 0.00 | sessions_revision | -1.78 | -1.0 | 23/28/129 | 1.30 | -3.32 -0.98 -1.03 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | topics_scheduled | -0.16 | +0.0 | 9/145/26 | 0.24 | +0.00 -0.22 -0.27 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | order_inversions_all_edges | -6.52 | -7.0 | 0/1/179 | 1.30 | -5.82 -6.58 -7.15 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | dependents_without_prerequisite | -0.03 | +0.0 | 0/174/6 | 0.06 | -0.05 -0.02 -0.03 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | long_plus_extended | +0.01 | +0.0 | 9/163/8 | 0.12 | +0.05 -0.12 +0.08 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 180 | 0.00 | sessions_revision | +0.18 | +0.0 | 82/21/77 | 2.35 | +0.88 -0.07 -0.27 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | order_inversions_all_edges | -6.53 | -7.0 | 0/0/180 | 1.49 | -6.35 -5.83 -7.40 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/180/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 180 | 0.00 | sessions_revision | +0.49 | +0.5 | 90/9/81 | 6.85 | -0.57 +1.60 +0.43 | não |

### strength: contrastes contra `curated-mix`, bloco S, 1 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.67 | topics_scheduled | -0.27 | +0.0 | 0/14/1 | 0.53 | +0.00 +0.00 -0.80 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.67 | order_inversions_all_edges | +0.13 | +0.0 | 2/13/0 | 0.16 | +0.00 +0.00 +0.40 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.67 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.67 | long_plus_extended | -0.07 | +0.0 | 0/14/1 | 0.13 | +0.00 +0.00 -0.20 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.67 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.80 | -1.0 | 2/3/10 | 1.06 | -1.20 +0.00 -1.20 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -2.60 | -2.0 | 0/0/15 | 0.43 | -2.00 -2.40 -3.40 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +4.47 | +4.0 | 15/0/0 | 1.35 | +1.80 +5.40 +6.20 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +4.47 | +5.0 | 15/0/0 | 1.39 | +5.20 +5.00 +3.20 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +2.27 | +2.0 | 14/1/0 | 0.52 | +1.80 +1.20 +3.80 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | +1.93 | +2.0 | 12/2/1 | 0.96 | +3.20 +1.80 +0.80 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +9.47 | +9.0 | 15/0/0 | 3.18 | +10.60 +10.20 +7.60 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | +0.20 | +0.0 | 6/4/5 | 0.83 | +0.60 +0.60 -0.60 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +9.67 | +11.0 | 15/0/0 | 2.35 | +11.40 +7.80 +9.80 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | +1.67 | +2.0 | 10/2/3 | 2.73 | +2.80 +0.80 +1.40 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.67 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.67 | order_inversions_all_edges | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 -1.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.67 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.67 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.67 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.33 | +0.0 | 5/10/0 | 0.00 | +0.00 +0.00 +1.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -1.33 | -1.0 | 0/0/15 | 0.00 | -2.00 -1.00 -1.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -2.00 | -2.0 | 0/0/15 | 0.00 | -2.00 -2.00 -2.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.67 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.67 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.67 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.67 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.67 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | topics_scheduled | +0.60 | +0.0 | 7/8/0 | 0.50 | +0.80 +0.00 +1.00 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.47 | +0.0 | 0/9/6 | 0.27 | +0.00 -1.00 -0.40 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.20 | +0.0 | 0/12/3 | 0.16 | +0.00 +0.00 -0.60 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.33 | +0.0 | 5/10/0 | 0.27 | +0.80 +0.00 +0.20 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -2.53 | -3.0 | 0/0/15 | 0.55 | -1.60 -2.80 -3.20 | **sim** |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -0.53 | +0.0 | 2/8/5 | 0.64 | +0.00 +0.20 -1.80 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | -2.27 | -2.0 | 0/1/14 | 0.80 | -2.20 -1.60 -3.00 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -1.67 | -1.0 | 0/0/15 | 0.50 | -1.00 -1.40 -2.60 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +0.13 | +0.0 | 5/7/3 | 0.66 | +0.40 -0.20 +0.20 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -0.07 | +0.0 | 6/3/6 | 1.37 | +1.00 -1.40 +0.20 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -3.73 | -3.0 | 0/0/15 | 1.01 | -2.20 -2.40 -6.60 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | +0.27 | +0.0 | 7/3/5 | 1.16 | -0.40 +0.80 +0.40 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -4.00 | -4.0 | 0/0/15 | 0.76 | -2.60 -4.80 -4.60 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | +0.27 | +0.0 | 7/1/7 | 2.54 | +3.00 -1.20 -1.00 | não |

### strength: contrastes contra `curated-mix`, bloco S, 3 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -0.33 | +0.0 | 5/5/5 | 0.00 | -2.00 +1.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +2.33 | +2.0 | 15/0/0 | 0.00 | +2.00 +1.00 +4.00 | **sim** |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +2.33 | +3.0 | 15/0/0 | 0.00 | +3.00 +3.00 +1.00 | **sim** |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 5/5/5 | 0.00 | +1.00 -1.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 5/5/5 | 0.00 | -1.00 +0.00 +1.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +4.00 | +4.0 | 15/0/0 | 0.00 | +4.00 +4.00 +4.00 | **sim** |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 10/5/0 | 0.00 | +1.00 +0.00 +1.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | -1.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +4.67 | +5.0 | 15/0/0 | 0.00 | +5.00 +4.00 +5.00 | **sim** |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 0.00 | topics_scheduled | +0.33 | +1.0 | 10/0/5 | 0.00 | +2.00 -2.00 +1.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.33 | +0.0 | 5/10/0 | 0.00 | +1.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +1.67 | +2.0 | 10/0/5 | 0.00 | -1.00 +4.00 +2.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 5/5/5 | 0.00 | -1.00 +1.00 +0.00 | não |
| all-soft − curated-mix | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.00 | topics_scheduled | +0.53 | +1.0 | 8/3/4 | 2.32 | +0.40 -0.40 +1.60 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | +1.93 | +2.0 | 14/0/1 | 1.14 | +1.20 +2.20 +2.40 | **sim** |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.47 | +0.0 | 6/7/2 | 0.47 | +0.00 +1.80 -0.40 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.93 | +1.0 | 10/2/3 | 0.83 | +2.20 +1.00 -0.40 | não |
| all-soft − curated-mix | ga | 1.0 | 15 | 0.00 | sessions_revision | +0.13 | +0.0 | 2/13/0 | 0.16 | +0.00 +0.40 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +4.13 | +4.0 | 13/1/1 | 2.73 | +3.80 +5.20 +3.40 | **sim** |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -6.73 | -5.0 | 0/0/15 | 2.40 | -9.20 -4.60 -6.40 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +11.00 | +11.0 | 15/0/0 | 2.39 | +12.60 +9.40 +11.00 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +13.27 | +14.0 | 15/0/0 | 1.32 | +9.00 +17.40 +13.40 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +6.27 | +6.0 | 15/0/0 | 1.03 | +8.20 +4.60 +6.00 | **sim** |
| all-soft − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | +5.33 | +5.0 | 12/2/1 | 3.53 | +8.40 +2.40 +5.20 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | -2.40 | -2.0 | 1/1/13 | 1.93 | -2.00 -2.60 -2.60 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +32.67 | +33.0 | 15/0/0 | 2.81 | +34.40 +28.80 +34.80 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +3.40 | +4.0 | 14/0/1 | 1.64 | +3.40 +4.20 +2.60 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +1.40 | +1.0 | 14/1/0 | 0.64 | +2.00 +1.20 +1.00 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -3.00 | -4.0 | 4/1/10 | 3.57 | -4.80 -3.60 -0.60 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +30.53 | +31.0 | 15/0/0 | 3.77 | +27.60 +34.00 +30.00 | **sim** |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-soft − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | +3.27 | +1.0 | 9/3/3 | 4.07 | +5.00 +3.80 +1.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -2.00 | -2.0 | 0/0/15 | 0.00 | -3.00 -2.00 -1.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | -2.33 | -2.0 | 0/0/15 | 0.00 | -2.00 -2.00 -3.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -1.67 | -2.0 | 0/5/10 | 0.00 | -3.00 +0.00 -2.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -1.33 | -2.0 | 0/5/10 | 0.00 | -2.00 -2.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | -1.00 | -4.0 | 5/0/10 | 0.00 | -4.00 -6.00 +7.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -4.67 | -4.0 | 0/0/15 | 0.00 | -6.00 -4.00 -4.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -1.67 | -2.0 | 0/0/15 | 0.00 | -1.00 -2.00 -2.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | -0.67 | +0.0 | 0/10/5 | 0.00 | -2.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -6.67 | -6.0 | 0/0/15 | 0.00 | -8.00 -6.00 -6.00 | **sim** |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.00 | topics_scheduled | -0.67 | +0.0 | 5/5/5 | 0.00 | +0.00 +2.00 -4.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | -0.67 | -1.0 | 0/5/10 | 0.00 | -1.00 -1.00 +0.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -2.33 | -2.0 | 0/0/15 | 0.00 | -3.00 -2.00 -2.00 | **sim** |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| all-hard − curated-mix | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | topics_scheduled | +1.33 | +1.0 | 10/1/4 | 2.19 | +3.60 +1.00 -0.60 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -3.07 | -3.0 | 0/0/15 | 0.30 | -3.40 -4.00 -1.80 | **sim** |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.87 | -1.0 | 0/5/10 | 0.27 | -1.00 +0.00 -1.60 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.40 | +1.0 | 8/4/3 | 0.64 | +1.00 +0.60 -0.40 | não |
| all-hard − curated-mix | ga | 1.0 | 15 | 0.00 | sessions_revision | -0.13 | +0.0 | 0/13/2 | 0.27 | -0.20 +0.00 -0.20 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -4.60 | -4.0 | 0/0/15 | 1.04 | -5.40 -4.40 -4.00 | **sim** |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | +3.53 | +3.0 | 13/0/2 | 2.30 | +2.60 +4.60 +3.40 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | -6.20 | -6.0 | 0/0/15 | 1.14 | -7.60 -4.80 -6.20 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -3.13 | -3.0 | 0/0/15 | 0.94 | -4.20 -2.40 -2.80 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | -1.60 | -1.0 | 0/3/12 | 1.03 | -0.80 -1.80 -2.20 | **sim** |
| all-hard − curated-mix | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -2.47 | -3.0 | 4/2/9 | 3.30 | -0.60 -2.80 -4.00 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | -0.13 | -1.0 | 4/2/9 | 1.75 | +0.20 -1.60 +1.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -12.27 | -12.0 | 0/0/15 | 1.72 | -13.80 -10.20 -12.80 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | -0.67 | +0.0 | 0/8/7 | 0.51 | -1.00 +0.00 -1.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.27 | +0.0 | 5/7/3 | 0.81 | +0.80 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -1.67 | -2.0 | 2/3/10 | 1.86 | -3.40 -0.60 -1.00 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -9.87 | -9.0 | 0/0/15 | 1.46 | -12.40 -8.60 -8.60 | **sim** |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| all-hard − curated-mix | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -1.20 | +0.0 | 6/2/7 | 3.74 | +0.40 +0.60 -4.60 | não |

### effort: contrastes contra `uniform`, bloco F (2 disciplinas)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| tiers-flat − uniform | greedy-baseline | 0.7 | 135 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 135 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 135 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 135 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 135 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 135 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 135 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 135 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 135 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 135 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 135 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 135 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 135 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 135 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 135 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 135 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 135 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 135 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 135 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 135 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 135 | 0.00 | topics_scheduled | +0.13 | +0.0 | 37/73/25 | 0.65 | +0.38 -0.22 +0.22 | não |
| tiers-flat − uniform | ga | 1.0 | 135 | 0.00 | order_inversions_all_edges | +0.41 | +0.0 | 41/71/23 | 1.02 | +0.40 +0.53 +0.31 | não |
| tiers-flat − uniform | ga | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.14 | +0.0 | 15/119/1 | 0.19 | +0.16 +0.27 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 135 | 0.00 | long_plus_extended | +0.07 | +0.0 | 11/122/2 | 0.11 | +0.24 -0.02 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.01 | +0.0 | 34/64/37 | 0.88 | -0.22 -0.04 +0.31 | não |
| tiers-flat − uniform | ga | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 135 | 0.00 | order_inversions_all_edges | +2.64 | +0.0 | 61/50/24 | 1.51 | +0.96 +5.13 +1.84 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +1.88 | +1.0 | 89/46/0 | 0.45 | +1.93 +1.18 +2.53 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 135 | 0.00 | long_plus_extended | +3.48 | +3.0 | 124/11/0 | 0.53 | +3.76 +3.87 +2.82 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 135 | 0.00 | sessions_revision | -4.15 | -4.0 | 0/45/90 | 0.55 | -5.47 -2.51 -4.47 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.0 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 135 | 0.00 | order_inversions_all_edges | +1.39 | +0.0 | 57/54/24 | 2.41 | +0.56 +0.87 +2.76 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 135 | 0.00 | sessions_revision | -4.73 | -5.0 | 3/4/128 | 2.27 | -4.51 -4.84 -4.82 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 135 | 0.00 | order_inversions_all_edges | +1.20 | +0.0 | 49/56/30 | 2.25 | +1.47 +0.27 +1.87 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 135 | 0.00 | sessions_revision | -8.41 | -10.0 | 23/3/109 | 8.63 | -4.33 -9.09 -11.82 | **sim** |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 135 | 0.00 | topics_scheduled | +0.15 | +0.0 | 55/30/50 | 0.00 | -0.33 -0.78 +1.56 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 135 | 0.00 | order_inversions_all_edges | +0.07 | +0.0 | 10/115/10 | 0.00 | -0.22 +0.00 +0.44 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 20/105/10 | 0.00 | +0.22 +0.00 -0.22 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 135 | 0.00 | long_plus_extended | +0.22 | +0.0 | 30/105/0 | 0.00 | +0.00 +0.00 +0.67 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 135 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 135 | 0.00 | topics_scheduled | -0.22 | +0.0 | 30/60/45 | 0.00 | +0.00 -0.67 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 135 | 0.00 | order_inversions_all_edges | +0.11 | +0.0 | 15/120/0 | 0.00 | +0.33 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 135 | 0.00 | dependents_without_prerequisite | -0.11 | +0.0 | 0/120/15 | 0.00 | -0.33 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 135 | 0.00 | long_plus_extended | +0.11 | +0.0 | 15/120/0 | 0.00 | +0.00 +0.00 +0.33 | não |
| tiers-narrow − uniform | ga | 0.7 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 135 | 0.00 | topics_scheduled | -1.96 | -2.0 | 13/14/108 | 1.44 | -2.09 -2.40 -1.40 | **sim** |
| tiers-narrow − uniform | ga | 1.0 | 135 | 0.00 | order_inversions_all_edges | +0.29 | +0.0 | 35/80/20 | 0.74 | +0.27 +0.02 +0.58 | não |
| tiers-narrow − uniform | ga | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.11 | +0.0 | 15/119/1 | 0.12 | +0.22 +0.11 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 135 | 0.00 | long_plus_extended | -0.44 | +0.0 | 5/85/45 | 0.48 | -0.89 -0.07 -0.38 | não |
| tiers-narrow − uniform | ga | 1.0 | 135 | 0.00 | sessions_revision | -7.13 | -7.0 | 0/0/135 | 0.21 | -7.22 -7.13 -7.04 | **sim** |
| tiers-narrow − uniform | ga | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.16 | +0.0 | 40/61/34 | 1.07 | +0.22 +0.07 +0.18 | não |
| tiers-narrow − uniform | ga | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 135 | 0.00 | sessions_revision | -11.00 | -11.0 | 0/0/135 | 0.00 | -11.00 -11.00 -11.00 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 135 | 0.00 | topics_scheduled | +0.78 | +1.0 | 72/25/38 | 0.73 | +1.24 +0.33 +0.76 | não |
| tiers-narrow − uniform | ga-timeline | 0.7 | 135 | 0.00 | order_inversions_all_edges | +3.97 | +1.0 | 69/53/13 | 1.75 | +3.73 +5.04 +3.13 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +2.12 | +1.0 | 81/52/2 | 0.61 | +1.11 +1.87 +3.38 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 135 | 0.00 | long_plus_extended | +3.33 | +3.0 | 122/12/1 | 0.56 | +3.49 +3.84 +2.67 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 135 | 0.00 | sessions_revision | -4.44 | -4.0 | 2/1/132 | 1.23 | -5.38 -2.76 -5.20 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 135 | 0.00 | order_inversions_all_edges | +2.27 | +1.0 | 68/46/21 | 2.01 | +1.84 +0.62 +4.33 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 135 | 0.00 | sessions_revision | -8.28 | -8.0 | 1/0/134 | 2.04 | -8.22 -8.13 -8.49 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.99 | +0.0 | 47/54/34 | 2.59 | +0.80 +0.09 +2.07 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 135 | 0.00 | sessions_revision | -14.60 | -16.0 | 15/1/119 | 7.96 | -13.53 -13.47 -16.80 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 135 | 0.00 | topics_scheduled | -7.11 | -7.0 | 0/0/135 | 0.00 | -6.44 -8.00 -6.89 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 135 | 0.00 | order_inversions_all_edges | -0.89 | +0.0 | 0/75/60 | 0.00 | -0.67 -0.89 -1.11 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +0.52 | +0.0 | 60/65/10 | 0.00 | +0.44 +0.67 +0.44 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 135 | 0.00 | long_plus_extended | -2.07 | -3.0 | 0/15/120 | 0.00 | -0.89 -2.33 -3.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 135 | 0.00 | topics_scheduled | -6.59 | -6.0 | 0/0/135 | 0.00 | -5.22 -9.78 -4.78 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 135 | 0.00 | order_inversions_all_edges | -0.81 | +0.0 | 0/85/50 | 0.00 | -0.22 -2.00 -0.22 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.44 | +0.0 | 50/85/0 | 0.00 | +0.22 +0.89 +0.22 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 135 | 0.00 | long_plus_extended | -1.78 | -2.0 | 0/10/125 | 0.00 | -2.44 -1.67 -1.22 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 135 | 0.00 | topics_scheduled | -6.33 | -6.0 | 0/0/135 | 0.00 | -5.67 -7.33 -6.00 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 135 | 0.00 | order_inversions_all_edges | -0.22 | +0.0 | 0/105/30 | 0.00 | -0.33 +0.00 -0.33 | não |
| tiers-platform − uniform | ga | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +0.22 | +0.0 | 30/105/0 | 0.00 | +0.33 +0.00 +0.33 | não |
| tiers-platform − uniform | ga | 0.7 | 135 | 0.00 | long_plus_extended | -1.78 | -2.0 | 0/0/135 | 0.00 | -1.00 -1.67 -2.67 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 135 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.0 | 135 | 0.00 | topics_scheduled | -6.53 | -6.0 | 0/0/135 | 0.95 | -5.60 -9.24 -4.76 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 135 | 0.00 | order_inversions_all_edges | -0.36 | +0.0 | 17/70/48 | 0.61 | -0.13 -0.89 -0.07 | não |
| tiers-platform − uniform | ga | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.41 | +0.0 | 41/94/0 | 0.05 | +0.29 +0.96 +0.00 | não |
| tiers-platform − uniform | ga | 1.0 | 135 | 0.00 | long_plus_extended | -2.67 | -3.0 | 0/0/135 | 0.56 | -3.42 -2.58 -2.02 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 135 | 0.00 | sessions_revision | -18.10 | -18.0 | 0/0/135 | 0.16 | -18.13 -18.09 -18.07 | **sim** |
| tiers-platform − uniform | ga | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 135 | 0.00 | order_inversions_all_edges | +0.01 | +0.0 | 35/63/37 | 0.94 | -0.20 +0.04 +0.20 | não |
| tiers-platform − uniform | ga | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 135 | 0.00 | sessions_revision | -26.00 | -26.0 | 0/0/135 | 0.00 | -26.00 -26.00 -26.00 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 135 | 0.00 | topics_scheduled | -5.46 | -4.0 | 1/1/133 | 0.88 | -5.04 -5.47 -5.87 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 135 | 0.00 | order_inversions_all_edges | +2.00 | +0.0 | 57/55/23 | 1.41 | +2.02 +3.56 +0.42 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 135 | 0.00 | dependents_without_prerequisite | +2.87 | +2.0 | 89/46/0 | 0.61 | +1.78 +2.42 +4.42 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 135 | 0.00 | long_plus_extended | +2.16 | +1.0 | 79/32/24 | 0.52 | +2.24 +2.69 +1.53 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 135 | 0.00 | sessions_revision | -13.42 | -13.0 | 0/0/135 | 1.37 | -14.40 -11.69 -14.18 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 135 | 0.00 | topics_scheduled | -1.56 | -1.0 | 0/19/116 | 0.73 | -1.56 -0.93 -2.20 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 135 | 0.00 | order_inversions_all_edges | +3.47 | +1.0 | 73/51/11 | 2.48 | +1.89 +3.29 +5.22 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 135 | 0.00 | dependents_without_prerequisite | +0.45 | +0.0 | 34/101/0 | 0.34 | +0.56 +0.04 +0.76 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 135 | 0.00 | long_plus_extended | -0.44 | +0.0 | 0/80/55 | 0.27 | -0.76 -0.31 -0.24 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 135 | 0.00 | sessions_revision | -19.78 | -19.0 | 0/0/135 | 1.93 | -19.80 -18.31 -21.22 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.5 | 135 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 135 | 0.00 | order_inversions_all_edges | +1.22 | +0.0 | 43/62/30 | 2.30 | +1.00 +0.33 +2.33 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.5 | 135 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 135 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/135/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 135 | 0.00 | sessions_revision | -23.95 | -26.0 | 7/0/128 | 7.28 | -22.16 -24.27 -25.42 | **sim** |

### effort: contrastes contra `uniform`, bloco S, 1 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.67 | +0.0 | 2/7/6 | 1.25 | -0.40 -0.40 -1.20 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.07 | +0.0 | 4/6/5 | 0.97 | -0.20 +0.80 -0.40 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +1.13 | +2.0 | 8/5/2 | 1.08 | +2.20 +0.40 +0.80 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 10/5/0 | 0.00 | +0.00 +1.00 +1.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +1.27 | +1.0 | 15/0/0 | 0.13 | +1.00 +1.00 +1.80 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -1.80 | -2.0 | 0/3/12 | 0.56 | -3.20 -1.60 -0.60 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.93 | +1.0 | 12/1/2 | 1.54 | +1.40 +0.80 +0.60 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -2.40 | -2.0 | 1/0/14 | 1.67 | -2.40 -2.80 -2.00 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.27 | +0.0 | 6/3/6 | 1.85 | +0.00 +0.60 +0.20 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -7.60 | -7.0 | 0/0/15 | 2.86 | -7.40 -6.20 -9.20 | **sim** |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -0.33 | +0.0 | 5/5/5 | 0.00 | -2.00 +1.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | topics_scheduled | -0.67 | -1.0 | 5/0/10 | 0.00 | -2.00 +1.00 -1.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | -0.07 | +0.0 | 0/14/1 | 0.13 | -0.20 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -1.00 | -1.0 | 1/4/10 | 0.93 | -0.60 -1.00 -1.40 | **sim** |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | -4.00 | -4.0 | 0/0/15 | 0.00 | -4.00 -4.00 -4.00 | **sim** |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.27 | +0.0 | 7/2/6 | 1.18 | +0.60 +0.80 -0.60 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | -6.00 | -6.0 | 0/0/15 | 0.00 | -6.00 -6.00 -6.00 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | +1.13 | +1.0 | 14/0/1 | 0.40 | +1.00 +0.60 +1.80 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +1.60 | +2.0 | 11/1/3 | 1.59 | +2.20 +1.40 +1.20 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 9/6/0 | 0.21 | +0.00 +1.00 +1.00 | não |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +0.53 | +1.0 | 8/5/2 | 0.49 | +1.40 +0.60 -0.40 | não |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -1.73 | -2.0 | 0/2/13 | 0.67 | -2.20 -1.60 -1.40 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +1.33 | +2.0 | 11/3/1 | 1.32 | +1.20 +1.40 +1.40 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -5.07 | -6.0 | 0/1/14 | 2.10 | -5.80 -4.40 -5.00 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.33 | +0.0 | 2/8/5 | 1.19 | -0.40 +0.40 -1.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -9.53 | -8.0 | 0/0/15 | 4.09 | -11.00 -9.60 -8.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -5.33 | -5.0 | 0/0/15 | 0.00 | -7.00 -5.00 -4.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | -0.67 | -1.0 | 0/5/10 | 0.00 | -1.00 +0.00 -1.00 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -1.67 | -1.0 | 0/0/15 | 0.00 | -1.00 -1.00 -3.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | -2.33 | -2.0 | 0/0/15 | 0.00 | -2.00 -3.00 -2.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.67 | -1.0 | 0/5/10 | 0.00 | +0.00 -1.00 -1.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.33 | +0.0 | 5/10/0 | 0.00 | +0.00 +0.00 +1.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | -1.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | topics_scheduled | -5.33 | -5.0 | 0/0/15 | 0.00 | -7.00 -5.00 -4.00 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | long_plus_extended | -2.33 | -2.0 | 0/0/15 | 0.00 | -2.00 -1.00 -4.00 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | -3.13 | -3.0 | 0/0/15 | 0.62 | -2.00 -3.00 -4.40 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -1.40 | -1.0 | 1/3/11 | 1.21 | -0.80 -1.00 -2.40 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.20 | +0.0 | 3/12/0 | 0.16 | +0.00 +0.00 +0.60 | não |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | -0.60 | -1.0 | 0/6/9 | 0.13 | -1.00 +0.00 -0.80 | não |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | -10.00 | -10.0 | 0/0/15 | 0.00 | -10.00 -10.00 -10.00 | **sim** |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +1.20 | +1.0 | 9/5/1 | 1.13 | +1.00 +2.20 +0.40 | **sim** |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | -13.00 | -13.0 | 0/0/15 | 0.00 | -13.00 -13.00 -13.00 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -1.40 | -2.0 | 0/3/12 | 0.43 | -2.00 -1.60 -0.60 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.80 | +1.0 | 10/1/4 | 0.87 | +2.20 +0.20 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +1.00 | +1.0 | 9/6/0 | 0.50 | +0.00 +1.40 +1.60 | não |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | -0.73 | +0.0 | 0/8/7 | 0.30 | +0.00 -0.40 -1.80 | não |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -6.73 | -7.0 | 0/0/15 | 0.66 | -8.00 -6.40 -5.80 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +1.13 | +1.0 | 10/3/2 | 1.22 | +1.40 -0.20 +2.20 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -8.00 | -8.0 | 0/0/15 | 1.82 | -9.00 -7.80 -7.20 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.27 | -1.0 | 6/1/8 | 1.72 | -1.20 +1.60 -1.20 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -13.20 | -14.0 | 0/0/15 | 2.56 | -15.40 -13.60 -10.60 | **sim** |

### effort: contrastes contra `uniform`, bloco S, 3 disciplina(s)

| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ | +/=/− | DP entre sementes | média por sorteio | material |
|---|---|---|---|---|---|---|---|---|---|---|---|
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.0 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | greedy-baseline | 1.5 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 0.7 | 15 | 1.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | +0.40 | +1.0 | 8/3/4 | 1.24 | +0.40 +0.40 +0.40 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.60 | -1.0 | 4/2/9 | 1.13 | -0.20 -1.40 -0.20 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 1/13/1 | 0.21 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | +0.07 | +0.0 | 2/12/1 | 0.25 | +0.20 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.53 | +1.0 | 9/2/4 | 1.37 | +1.20 +0.40 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.87 | +1.0 | 9/3/3 | 1.63 | +1.60 -0.20 +1.20 | não |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +1.33 | +2.0 | 12/2/1 | 0.79 | +1.80 +0.80 +1.40 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +3.87 | +4.0 | 14/1/0 | 0.90 | +1.20 +7.60 +2.80 | **sim** |
| tiers-flat − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -4.27 | -2.0 | 0/3/12 | 1.48 | -10.40 -1.60 -0.80 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +2.00 | +3.0 | 11/2/2 | 2.73 | +1.40 +0.60 +4.00 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -5.73 | -5.0 | 2/0/13 | 4.45 | -5.80 -5.20 -6.20 | **sim** |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.07 | -1.0 | 5/1/9 | 2.32 | +0.60 -1.00 +0.60 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-flat − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -10.93 | -16.0 | 5/0/10 | 14.35 | -19.80 -6.40 -6.60 | **sim** |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -1.33 | -2.0 | 5/0/10 | 0.00 | -2.00 +1.00 -3.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | +0.00 +0.00 -1.00 | não |
| tiers-narrow − uniform | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | topics_scheduled | -0.33 | +0.0 | 5/5/5 | 0.00 | -2.00 +1.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.00 | -1.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | -3.53 | -4.0 | 0/3/12 | 1.77 | -1.80 -3.20 -5.60 | **sim** |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -0.53 | +0.0 | 0/10/5 | 0.72 | -0.20 -0.40 -1.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.20 | +0.0 | 3/12/0 | 0.30 | +0.20 +0.00 +0.40 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | -0.33 | +0.0 | 0/10/5 | 0.33 | -0.60 -0.40 +0.00 | não |
| tiers-narrow − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | -11.00 | -11.0 | 0/0/15 | 0.00 | -11.00 -11.00 -11.00 | **sim** |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.40 | +0.0 | 5/6/4 | 1.48 | +0.60 -0.60 +1.20 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | -15.00 | -15.0 | 0/0/15 | 0.00 | -15.00 -15.00 -15.00 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 7/3/5 | 0.86 | +0.80 -0.20 -0.60 | não |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +1.47 | +1.0 | 11/3/1 | 1.29 | +2.60 +1.40 +0.40 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +1.07 | +1.0 | 10/2/3 | 1.02 | +2.00 +0.20 +1.00 | **sim** |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +2.67 | +2.0 | 9/6/0 | 0.69 | +0.00 +6.20 +1.80 | não |
| tiers-narrow − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -7.47 | -8.0 | 0/1/14 | 2.21 | -11.60 -7.40 -3.40 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | -0.13 | +0.0 | 0/13/2 | 0.27 | -0.20 +0.00 -0.20 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +3.33 | +3.0 | 12/2/1 | 2.11 | +1.40 +1.40 +7.20 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | -0.07 | +0.0 | 0/14/1 | 0.13 | +0.00 +0.00 -0.20 | não |
| tiers-narrow − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -13.07 | -13.0 | 0/0/15 | 3.54 | -10.80 -16.20 -12.20 | **sim** |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.67 | +0.0 | 6/2/7 | 2.39 | -0.60 +0.60 -2.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-narrow − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -20.20 | -24.0 | 3/0/12 | 14.51 | -25.20 -15.20 -20.20 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | topics_scheduled | -11.33 | -11.0 | 0/0/15 | 0.00 | -11.00 -12.00 -11.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | order_inversions_all_edges | -0.67 | -1.0 | 0/5/10 | 0.00 | -1.00 +0.00 -1.00 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.33 | +1.0 | 10/0/5 | 0.00 | +1.00 -1.00 +1.00 | não |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | long_plus_extended | -4.33 | -4.0 | 0/0/15 | 0.00 | -3.00 -4.00 -6.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | topics_scheduled | -10.33 | -8.0 | 0/0/15 | 0.00 | -8.00 -8.00 -15.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | order_inversions_all_edges | -2.00 | -2.0 | 0/0/15 | 0.00 | -2.00 -2.00 -2.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +1.67 | +2.0 | 15/0/0 | 0.00 | +1.00 +2.00 +2.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | long_plus_extended | -1.67 | -2.0 | 0/0/15 | 0.00 | -2.00 -2.00 -1.00 | **sim** |
| tiers-platform − uniform | greedy-baseline | 1.0 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | order_inversions_all_edges | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | greedy-baseline | 1.5 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | topics_scheduled | -12.67 | -12.0 | 0/0/15 | 0.00 | -12.00 -12.00 -14.00 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | order_inversions_all_edges | -1.00 | -1.0 | 0/5/10 | 0.00 | -2.00 -1.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +0.0 | 5/10/0 | 0.00 | +2.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | long_plus_extended | -3.33 | -3.0 | 0/0/15 | 0.00 | -5.00 -3.00 -2.00 | **sim** |
| tiers-platform − uniform | ga | 0.7 | 15 | 0.00 | sessions_revision | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | topics_scheduled | -12.60 | -12.0 | 0/0/15 | 2.02 | -13.60 -12.00 -12.20 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | order_inversions_all_edges | -1.40 | -1.0 | 1/2/12 | 0.77 | -1.80 -0.20 -2.20 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +1.0 | 8/7/0 | 0.30 | +1.00 +0.00 +1.00 | não |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | long_plus_extended | -3.00 | -3.0 | 0/0/15 | 0.65 | -3.60 -3.80 -1.60 | **sim** |
| tiers-platform − uniform | ga | 1.0 | 15 | 0.00 | sessions_revision | -26.13 | -26.0 | 0/0/15 | 0.16 | -26.00 -26.40 -26.00 | **sim** |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.07 | +0.0 | 6/2/7 | 1.74 | +0.00 +0.20 -0.40 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga | 1.5 | 15 | 0.00 | sessions_revision | -38.00 | -38.0 | 0/0/15 | 0.00 | -38.00 -38.00 -38.00 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | topics_scheduled | -11.60 | -13.0 | 0/0/15 | 2.15 | -9.20 -14.00 -11.60 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | order_inversions_all_edges | +0.60 | +0.0 | 7/2/6 | 1.55 | +1.40 -1.60 +2.00 | não |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | dependents_without_prerequisite | +1.27 | +1.0 | 10/4/1 | 1.04 | +2.20 +0.40 +1.20 | **sim** |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | long_plus_extended | +0.47 | -1.0 | 7/0/8 | 1.05 | -2.20 +4.40 -0.80 | não |
| tiers-platform − uniform | ga-timeline | 0.7 | 15 | 0.00 | sessions_revision | -21.67 | -20.0 | 0/0/15 | 2.94 | -27.00 -20.20 -17.80 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | topics_scheduled | -4.27 | -4.0 | 0/0/15 | 1.13 | -4.80 -4.00 -4.00 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | order_inversions_all_edges | +3.73 | +3.0 | 12/2/1 | 2.43 | +2.20 +2.20 +6.80 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | dependents_without_prerequisite | +0.67 | +0.0 | 7/8/0 | 0.51 | +1.00 +0.00 +1.00 | não |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | long_plus_extended | -1.40 | -1.0 | 0/1/14 | 0.64 | -2.00 -1.20 -1.00 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.0 | 15 | 0.00 | sessions_revision | -33.20 | -32.0 | 0/0/15 | 3.15 | -32.00 -34.40 -33.20 | **sim** |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | topics_scheduled | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | order_inversions_all_edges | -0.93 | -1.0 | 5/2/8 | 1.99 | -0.60 -0.20 -2.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | dependents_without_prerequisite | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | long_plus_extended | +0.00 | +0.0 | 0/15/0 | 0.00 | +0.00 +0.00 +0.00 | não |
| tiers-platform − uniform | ga-timeline | 1.5 | 15 | 0.00 | sessions_revision | -29.40 | -31.0 | 1/0/14 | 13.37 | -39.00 -22.20 -27.00 | **sim** |

### `ga` contra `greedy-baseline` — bloco F, força e esforço no centro

| importância | folga | métrica | V/E/D (menos inversões / mais tópicos = vitória) | média Δ |
|---|---|---|---|---|
| flat | 0.7 | soft_inversions | 15/0/0 | -2.67 |
| flat | 0.7 | topics_scheduled | 10/5/0 | +1.67 |
| flat | 0.7 | order_inversions_all_edges | 10/5/0 | -1.33 |
| flat | 0.7 | dependents_without_prerequisite | 0/10/5 | +0.33 |
| flat | 1.0 | soft_inversions | 15/0/0 | -2.67 |
| flat | 1.0 | topics_scheduled | 6/3/6 | +0.07 |
| flat | 1.0 | order_inversions_all_edges | 13/2/0 | -1.87 |
| flat | 1.0 | dependents_without_prerequisite | 7/8/0 | -0.47 |
| flat | 1.5 | soft_inversions | 12/3/0 | -1.93 |
| flat | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| flat | 1.5 | order_inversions_all_edges | 12/3/0 | -1.93 |
| flat | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| flat | * | soft_inversions | 42/3/0 | -2.42 |
| flat | * | topics_scheduled | 16/23/6 | +0.58 |
| flat | * | order_inversions_all_edges | 35/10/0 | -1.71 |
| flat | * | dependents_without_prerequisite | 7/33/5 | -0.04 |
| close | 0.7 | soft_inversions | 15/0/0 | -3.33 |
| close | 0.7 | topics_scheduled | 5/10/0 | +0.33 |
| close | 0.7 | order_inversions_all_edges | 10/5/0 | -1.00 |
| close | 0.7 | dependents_without_prerequisite | 10/5/0 | -0.67 |
| close | 1.0 | soft_inversions | 15/0/0 | -3.20 |
| close | 1.0 | topics_scheduled | 0/6/9 | -1.20 |
| close | 1.0 | order_inversions_all_edges | 15/0/0 | -2.53 |
| close | 1.0 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| close | 1.5 | soft_inversions | 14/1/0 | -2.20 |
| close | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| close | 1.5 | order_inversions_all_edges | 14/1/0 | -2.20 |
| close | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| close | * | soft_inversions | 44/1/0 | -2.91 |
| close | * | topics_scheduled | 5/31/9 | -0.29 |
| close | * | order_inversions_all_edges | 39/6/0 | -1.91 |
| close | * | dependents_without_prerequisite | 10/35/0 | -0.22 |
| extreme | 0.7 | soft_inversions | 15/0/0 | -3.33 |
| extreme | 0.7 | topics_scheduled | 5/10/0 | +0.33 |
| extreme | 0.7 | order_inversions_all_edges | 10/5/0 | -1.00 |
| extreme | 0.7 | dependents_without_prerequisite | 10/5/0 | -0.67 |
| extreme | 1.0 | soft_inversions | 15/0/0 | -3.27 |
| extreme | 1.0 | topics_scheduled | 0/6/9 | -1.33 |
| extreme | 1.0 | order_inversions_all_edges | 15/0/0 | -2.60 |
| extreme | 1.0 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| extreme | 1.5 | soft_inversions | 15/0/0 | -2.67 |
| extreme | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| extreme | 1.5 | order_inversions_all_edges | 15/0/0 | -2.67 |
| extreme | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| extreme | * | soft_inversions | 45/0/0 | -3.09 |
| extreme | * | topics_scheduled | 5/31/9 | -0.33 |
| extreme | * | order_inversions_all_edges | 40/5/0 | -2.09 |
| extreme | * | dependents_without_prerequisite | 10/35/0 | -0.22 |
| * | 0.7 | soft_inversions | 45/0/0 | -3.11 |
| * | 0.7 | topics_scheduled | 20/25/0 | +0.78 |
| * | 0.7 | order_inversions_all_edges | 30/15/0 | -1.11 |
| * | 0.7 | dependents_without_prerequisite | 20/20/5 | -0.33 |
| * | 1.0 | soft_inversions | 45/0/0 | -3.04 |
| * | 1.0 | topics_scheduled | 6/15/24 | -0.82 |
| * | 1.0 | order_inversions_all_edges | 43/2/0 | -2.33 |
| * | 1.0 | dependents_without_prerequisite | 7/38/0 | -0.16 |
| * | 1.5 | soft_inversions | 41/4/0 | -2.27 |
| * | 1.5 | topics_scheduled | 0/45/0 | +0.00 |
| * | 1.5 | order_inversions_all_edges | 41/4/0 | -2.27 |
| * | 1.5 | dependents_without_prerequisite | 0/45/0 | +0.00 |
| * | * | soft_inversions | 131/4/0 | -2.81 |
| * | * | topics_scheduled | 26/85/24 | -0.01 |
| * | * | order_inversions_all_edges | 114/21/0 | -1.90 |
| * | * | dependents_without_prerequisite | 27/103/5 | -0.16 |

### `ga` contra `greedy-baseline` — bloco F inteiro

| importância | folga | métrica | V/E/D (menos inversões / mais tópicos = vitória) | média Δ |
|---|---|---|---|---|
| flat | 0.7 | soft_inversions | 100/80/0 | -1.22 |
| flat | 0.7 | topics_scheduled | 35/110/35 | +0.08 |
| flat | 0.7 | order_inversions_all_edges | 80/100/0 | -0.58 |
| flat | 0.7 | dependents_without_prerequisite | 25/110/45 | +0.25 |
| flat | 1.0 | soft_inversions | 83/79/18 | -0.79 |
| flat | 1.0 | topics_scheduled | 21/61/98 | -1.18 |
| flat | 1.0 | order_inversions_all_edges | 86/79/15 | -0.79 |
| flat | 1.0 | dependents_without_prerequisite | 12/147/21 | +0.06 |
| flat | 1.5 | soft_inversions | 82/84/14 | -0.74 |
| flat | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| flat | 1.5 | order_inversions_all_edges | 82/84/14 | -0.74 |
| flat | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| flat | * | soft_inversions | 265/243/32 | -0.92 |
| flat | * | topics_scheduled | 56/351/133 | -0.37 |
| flat | * | order_inversions_all_edges | 248/263/29 | -0.71 |
| flat | * | dependents_without_prerequisite | 37/437/66 | +0.10 |
| close | 0.7 | soft_inversions | 120/60/0 | -3.33 |
| close | 0.7 | topics_scheduled | 25/135/20 | +0.11 |
| close | 0.7 | order_inversions_all_edges | 115/65/0 | -2.06 |
| close | 0.7 | dependents_without_prerequisite | 45/135/0 | -0.50 |
| close | 1.0 | soft_inversions | 119/60/1 | -2.94 |
| close | 1.0 | topics_scheduled | 0/63/117 | -1.44 |
| close | 1.0 | order_inversions_all_edges | 119/60/1 | -2.88 |
| close | 1.0 | dependents_without_prerequisite | 0/171/9 | +0.05 |
| close | 1.5 | soft_inversions | 115/64/1 | -2.39 |
| close | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| close | 1.5 | order_inversions_all_edges | 115/64/1 | -2.39 |
| close | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| close | * | soft_inversions | 354/184/2 | -2.89 |
| close | * | topics_scheduled | 25/378/137 | -0.44 |
| close | * | order_inversions_all_edges | 349/189/2 | -2.44 |
| close | * | dependents_without_prerequisite | 45/486/9 | -0.15 |
| extreme | 0.7 | soft_inversions | 120/60/0 | -3.33 |
| extreme | 0.7 | topics_scheduled | 25/135/20 | +0.11 |
| extreme | 0.7 | order_inversions_all_edges | 115/65/0 | -2.06 |
| extreme | 0.7 | dependents_without_prerequisite | 45/135/0 | -0.50 |
| extreme | 1.0 | soft_inversions | 118/60/2 | -2.83 |
| extreme | 1.0 | topics_scheduled | 0/69/111 | -1.29 |
| extreme | 1.0 | order_inversions_all_edges | 119/61/0 | -2.82 |
| extreme | 1.0 | dependents_without_prerequisite | 0/166/14 | +0.10 |
| extreme | 1.5 | soft_inversions | 112/67/1 | -2.15 |
| extreme | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| extreme | 1.5 | order_inversions_all_edges | 112/67/1 | -2.15 |
| extreme | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| extreme | * | soft_inversions | 350/187/3 | -2.77 |
| extreme | * | topics_scheduled | 25/384/131 | -0.39 |
| extreme | * | order_inversions_all_edges | 346/193/1 | -2.34 |
| extreme | * | dependents_without_prerequisite | 45/481/14 | -0.13 |
| * | 0.7 | soft_inversions | 340/200/0 | -2.63 |
| * | 0.7 | topics_scheduled | 85/380/75 | +0.10 |
| * | 0.7 | order_inversions_all_edges | 310/230/0 | -1.56 |
| * | 0.7 | dependents_without_prerequisite | 115/380/45 | -0.25 |
| * | 1.0 | soft_inversions | 320/199/21 | -2.19 |
| * | 1.0 | topics_scheduled | 21/193/326 | -1.30 |
| * | 1.0 | order_inversions_all_edges | 324/200/16 | -2.16 |
| * | 1.0 | dependents_without_prerequisite | 12/484/44 | +0.07 |
| * | 1.5 | soft_inversions | 309/215/16 | -1.76 |
| * | 1.5 | topics_scheduled | 0/540/0 | +0.00 |
| * | 1.5 | order_inversions_all_edges | 309/215/16 | -1.76 |
| * | 1.5 | dependents_without_prerequisite | 0/540/0 | +0.00 |
| * | * | soft_inversions | 969/614/37 | -2.19 |
| * | * | topics_scheduled | 106/1113/401 | -0.40 |
| * | * | order_inversions_all_edges | 943/645/32 | -1.83 |
| * | * | dependents_without_prerequisite | 127/1404/89 | -0.06 |

### `ga-timeline` contra `greedy-baseline` — bloco F, força e esforço no centro

| importância | folga | métrica | V/E/D (menos inversões / mais tópicos = vitória) | média Δ |
|---|---|---|---|---|
| flat | 0.7 | soft_inversions | 0/1/14 | +2.60 |
| flat | 0.7 | topics_scheduled | 15/0/0 | +4.53 |
| flat | 0.7 | order_inversions_all_edges | 3/3/9 | +1.40 |
| flat | 0.7 | dependents_without_prerequisite | 0/0/15 | +2.07 |
| flat | 1.0 | soft_inversions | 0/0/15 | +4.00 |
| flat | 1.0 | topics_scheduled | 15/0/0 | +6.20 |
| flat | 1.0 | order_inversions_all_edges | 0/1/14 | +4.80 |
| flat | 1.0 | dependents_without_prerequisite | 9/4/2 | -0.47 |
| flat | 1.5 | soft_inversions | 1/2/12 | +2.47 |
| flat | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| flat | 1.5 | order_inversions_all_edges | 1/2/12 | +2.47 |
| flat | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| flat | * | soft_inversions | 1/3/41 | +3.02 |
| flat | * | topics_scheduled | 30/15/0 | +3.58 |
| flat | * | order_inversions_all_edges | 4/6/35 | +2.89 |
| flat | * | dependents_without_prerequisite | 9/19/17 | +0.53 |
| close | 0.7 | soft_inversions | 0/1/14 | +2.27 |
| close | 0.7 | topics_scheduled | 15/0/0 | +3.67 |
| close | 0.7 | order_inversions_all_edges | 1/0/14 | +2.07 |
| close | 0.7 | dependents_without_prerequisite | 0/5/10 | +1.07 |
| close | 1.0 | soft_inversions | 0/1/14 | +3.20 |
| close | 1.0 | topics_scheduled | 15/0/0 | +5.00 |
| close | 1.0 | order_inversions_all_edges | 0/0/15 | +4.07 |
| close | 1.0 | dependents_without_prerequisite | 4/10/1 | -0.20 |
| close | 1.5 | soft_inversions | 1/2/12 | +1.60 |
| close | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| close | 1.5 | order_inversions_all_edges | 1/2/12 | +1.60 |
| close | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| close | * | soft_inversions | 1/4/40 | +2.36 |
| close | * | topics_scheduled | 30/15/0 | +2.89 |
| close | * | order_inversions_all_edges | 2/2/41 | +2.58 |
| close | * | dependents_without_prerequisite | 4/30/11 | +0.29 |
| extreme | 0.7 | soft_inversions | 0/1/14 | +2.00 |
| extreme | 0.7 | topics_scheduled | 14/1/0 | +2.67 |
| extreme | 0.7 | order_inversions_all_edges | 0/2/13 | +1.93 |
| extreme | 0.7 | dependents_without_prerequisite | 0/5/10 | +1.00 |
| extreme | 1.0 | soft_inversions | 0/2/13 | +3.33 |
| extreme | 1.0 | topics_scheduled | 15/0/0 | +5.20 |
| extreme | 1.0 | order_inversions_all_edges | 0/0/15 | +4.27 |
| extreme | 1.0 | dependents_without_prerequisite | 5/9/1 | -0.27 |
| extreme | 1.5 | soft_inversions | 2/4/9 | +2.13 |
| extreme | 1.5 | topics_scheduled | 0/15/0 | +0.00 |
| extreme | 1.5 | order_inversions_all_edges | 2/4/9 | +2.13 |
| extreme | 1.5 | dependents_without_prerequisite | 0/15/0 | +0.00 |
| extreme | * | soft_inversions | 2/7/36 | +2.49 |
| extreme | * | topics_scheduled | 29/16/0 | +2.62 |
| extreme | * | order_inversions_all_edges | 2/6/37 | +2.78 |
| extreme | * | dependents_without_prerequisite | 5/29/11 | +0.24 |
| * | 0.7 | soft_inversions | 0/3/42 | +2.29 |
| * | 0.7 | topics_scheduled | 44/1/0 | +3.62 |
| * | 0.7 | order_inversions_all_edges | 4/5/36 | +1.80 |
| * | 0.7 | dependents_without_prerequisite | 0/10/35 | +1.38 |
| * | 1.0 | soft_inversions | 0/3/42 | +3.51 |
| * | 1.0 | topics_scheduled | 45/0/0 | +5.47 |
| * | 1.0 | order_inversions_all_edges | 0/1/44 | +4.38 |
| * | 1.0 | dependents_without_prerequisite | 18/23/4 | -0.31 |
| * | 1.5 | soft_inversions | 4/8/33 | +2.07 |
| * | 1.5 | topics_scheduled | 0/45/0 | +0.00 |
| * | 1.5 | order_inversions_all_edges | 4/8/33 | +2.07 |
| * | 1.5 | dependents_without_prerequisite | 0/45/0 | +0.00 |
| * | * | soft_inversions | 4/14/117 | +2.62 |
| * | * | topics_scheduled | 89/46/0 | +3.03 |
| * | * | order_inversions_all_edges | 8/14/113 | +2.75 |
| * | * | dependents_without_prerequisite | 18/78/39 | +0.36 |

### `ga-timeline` contra `greedy-baseline` — bloco F inteiro

| importância | folga | métrica | V/E/D (menos inversões / mais tópicos = vitória) | média Δ |
|---|---|---|---|---|
| flat | 0.7 | soft_inversions | 2/70/108 | +7.30 |
| flat | 0.7 | topics_scheduled | 44/104/32 | +0.56 |
| flat | 0.7 | order_inversions_all_edges | 6/66/108 | +4.23 |
| flat | 0.7 | dependents_without_prerequisite | 5/79/96 | +3.35 |
| flat | 1.0 | soft_inversions | 5/65/110 | +8.96 |
| flat | 1.0 | topics_scheduled | 45/135/0 | +1.41 |
| flat | 1.0 | order_inversions_all_edges | 5/66/109 | +9.03 |
| flat | 1.0 | dependents_without_prerequisite | 15/156/9 | -0.02 |
| flat | 1.5 | soft_inversions | 6/68/106 | +7.79 |
| flat | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| flat | 1.5 | order_inversions_all_edges | 6/68/106 | +7.79 |
| flat | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| flat | * | soft_inversions | 13/203/324 | +8.02 |
| flat | * | topics_scheduled | 89/419/32 | +0.66 |
| flat | * | order_inversions_all_edges | 17/200/323 | +7.02 |
| flat | * | dependents_without_prerequisite | 20/415/105 | +1.11 |
| close | 0.7 | soft_inversions | 6/70/104 | +5.31 |
| close | 0.7 | topics_scheduled | 56/102/22 | +0.61 |
| close | 0.7 | order_inversions_all_edges | 15/76/89 | +2.92 |
| close | 0.7 | dependents_without_prerequisite | 0/83/97 | +2.57 |
| close | 1.0 | soft_inversions | 8/66/106 | +6.42 |
| close | 1.0 | topics_scheduled | 44/135/1 | +1.18 |
| close | 1.0 | order_inversions_all_edges | 8/65/107 | +6.51 |
| close | 1.0 | dependents_without_prerequisite | 10/162/8 | +0.02 |
| close | 1.5 | soft_inversions | 8/67/105 | +6.02 |
| close | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| close | 1.5 | order_inversions_all_edges | 8/67/105 | +6.02 |
| close | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| close | * | soft_inversions | 22/203/315 | +5.91 |
| close | * | topics_scheduled | 100/417/23 | +0.59 |
| close | * | order_inversions_all_edges | 31/208/301 | +5.15 |
| close | * | dependents_without_prerequisite | 10/425/105 | +0.86 |
| extreme | 0.7 | soft_inversions | 5/66/109 | +5.53 |
| extreme | 0.7 | topics_scheduled | 57/98/25 | +0.54 |
| extreme | 0.7 | order_inversions_all_edges | 11/75/94 | +3.05 |
| extreme | 0.7 | dependents_without_prerequisite | 0/82/98 | +2.72 |
| extreme | 1.0 | soft_inversions | 3/70/107 | +7.16 |
| extreme | 1.0 | topics_scheduled | 44/136/0 | +1.19 |
| extreme | 1.0 | order_inversions_all_edges | 3/68/109 | +7.26 |
| extreme | 1.0 | dependents_without_prerequisite | 11/162/7 | +0.01 |
| extreme | 1.5 | soft_inversions | 4/72/104 | +6.49 |
| extreme | 1.5 | topics_scheduled | 0/180/0 | +0.00 |
| extreme | 1.5 | order_inversions_all_edges | 4/72/104 | +6.49 |
| extreme | 1.5 | dependents_without_prerequisite | 0/180/0 | +0.00 |
| extreme | * | soft_inversions | 12/208/320 | +6.39 |
| extreme | * | topics_scheduled | 101/414/25 | +0.58 |
| extreme | * | order_inversions_all_edges | 18/215/307 | +5.60 |
| extreme | * | dependents_without_prerequisite | 11/424/105 | +0.91 |
| * | 0.7 | soft_inversions | 13/206/321 | +6.05 |
| * | 0.7 | topics_scheduled | 157/304/79 | +0.57 |
| * | 0.7 | order_inversions_all_edges | 32/217/291 | +3.40 |
| * | 0.7 | dependents_without_prerequisite | 5/244/291 | +2.88 |
| * | 1.0 | soft_inversions | 16/201/323 | +7.51 |
| * | 1.0 | topics_scheduled | 133/406/1 | +1.26 |
| * | 1.0 | order_inversions_all_edges | 16/199/325 | +7.60 |
| * | 1.0 | dependents_without_prerequisite | 36/480/24 | +0.00 |
| * | 1.5 | soft_inversions | 18/207/315 | +6.77 |
| * | 1.5 | topics_scheduled | 0/540/0 | +0.00 |
| * | 1.5 | order_inversions_all_edges | 18/207/315 | +6.77 |
| * | 1.5 | dependents_without_prerequisite | 0/540/0 | +0.00 |
| * | * | soft_inversions | 47/614/959 | +6.78 |
| * | * | topics_scheduled | 290/1250/80 | +0.61 |
| * | * | order_inversions_all_edges | 66/623/931 | +5.92 |
| * | * | dependents_without_prerequisite | 41/1264/315 | +0.96 |


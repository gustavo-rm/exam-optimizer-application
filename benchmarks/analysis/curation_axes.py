"""Análise da medição de curadoria (EOA-1), exatamente como pré-registrada.

Lê benchmarks/results/curation-axes.csv e imprime, em Markdown, as tabelas de
docs/MEDICAO_EIXOS_DE_CURADORIA.md. Nenhum número do relatório sai de outro
lugar. Só biblioteca padrão; rode com:

    python3 -I benchmarks/analysis/curation_axes.py benchmarks/results/curation-axes.csv

O método é o da §5 do relatório, escrito antes dos dados: contraste pareado
(mesmo sorteio, folga, semente, motor e, no bloco F, mesmos níveis dos outros
dois eixos), taxa de plano idêntico, média/mediana da diferença, V/E/D,
desvio-padrão entre sementes, sinal por sorteio, e o limiar |Δ| >= 1 com
sinal concordante nos três sorteios. Sem teste de significância.
"""

import csv
import statistics
import sys
from collections import defaultdict

ENGINES = ["greedy-baseline", "ga", "ga-timeline"]
TIGHTNESS = ["0.700000", "1.000000", "1.500000"]
DECISION = ["topics_scheduled", "order_inversions_all_edges", "dependents_without_prerequisite",
            "long_plus_extended", "sessions_revision"]
REPORTED = ["scheduled_minutes", "soft_inversions", "sessions_study", "peak_over_mean",
            "first_quarter_share"]
AXES = {
    "importance": ("importance_level", "flat", ["close", "extreme"]),
    "strength": ("strength_level", "curated-mix", ["all-soft", "all-hard"]),
    "effort": ("effort_level", "uniform", ["tiers-flat", "tiers-narrow", "tiers-platform"]),
}
KEY = ["subjects", "replicate", "importance_level", "strength_level", "effort_level",
       "tightness", "engine", "seed"]
THRESHOLD = 1.0


def load(path):
    with open(path, newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    for row in rows:
        row["long_plus_extended"] = str(int(row["scheduled_long"]) + int(row["scheduled_extended"]))
    return rows


def index(rows):
    return {tuple(row[k] for k in KEY): row for row in rows}


def value(row, metric):
    return float(row[metric])


def integrity(rows):
    print("## Integridade\n")
    print(f"- linhas: {len(rows)}")
    print(f"- invariants_ok e reproducible verdadeiros: "
          f"{sum(r['invariants_ok'] == 'true' and r['reproducible'] == 'true' for r in rows)}")
    hard = [r for r in rows if r["strength_level"] == "all-hard"]
    print(f"- all-hard: linhas {len(hard)}, ordem invertida "
          f"{sum(int(r['order_inversions_all_edges']) for r in hard)}, órfãos "
          f"{sum(int(r['dependents_without_prerequisite']) for r in hard)}")
    by_cell = defaultdict(set)
    for r in rows:
        if r["engine"] == "greedy-baseline":
            by_cell[tuple(r[k] for k in KEY[:-1])].add(r["plan_hash"])
    print(f"- guloso: células com mais de um plano entre sementes: "
          f"{sum(len(h) > 1 for h in by_cell.values())} de {len(by_cell)}")
    cvs = defaultdict(set)
    for r in rows:
        cvs[(r["subjects"], r["importance_level"])].add(r["importance_cv"])
    print("- importance_cv efetivo por (disciplinas, nível): "
          + "; ".join(f"{k[0]}/{k[1]}={sorted(v)}" for k, v in sorted(cvs.items())))
    print()


def pairs(idx, rows, axis, level, block, subjects=None):
    """Pares (linha do nível, linha da referência) do contraste, no bloco pedido."""
    column, reference, _ = AXES[axis]
    position = KEY.index(column)
    found = []
    for row in rows:
        if row[column] != level or not in_block(row, axis, block, subjects):
            continue
        key = list(tuple(row[k] for k in KEY))
        key[position] = reference
        other = idx.get(tuple(key))
        if other is not None:
            found.append((row, other))
    return found


def in_block(row, axis, block, subjects):
    if block == "F":
        return row["design_role"] == "factorial"
    return (row["subjects"] == subjects
            and row["design_role"] in ("size", "size-" + axis))


def summarise(found, metric):
    diffs = [value(a, metric) - value(b, metric) for a, b in found]
    identical = sum(a["plan_hash"] == b["plan_hash"] for a, b in found)
    by_seedless = defaultdict(list)
    by_replicate = defaultdict(list)
    for (a, b), d in zip(found, diffs):
        by_seedless[tuple(a[k] for k in KEY if k != "seed")].append(d)
        by_replicate[a["replicate"]].append(d)
    seed_sd = statistics.mean(statistics.pstdev(v) for v in by_seedless.values())
    rep_means = [statistics.mean(by_replicate[r]) for r in sorted(by_replicate)]
    mean = statistics.mean(diffs)
    consistent = all(m > 0 for m in rep_means) or all(m < 0 for m in rep_means)
    return {
        "n": len(diffs), "identical": identical / len(diffs), "mean": mean,
        "median": statistics.median(diffs),
        "wtl": (sum(d > 0 for d in diffs), sum(d == 0 for d in diffs), sum(d < 0 for d in diffs)),
        "seed_sd": seed_sd, "rep": rep_means,
        "material": abs(mean) >= THRESHOLD and consistent,
    }


def contrast_table(idx, rows, axis, block, subjects=None):
    _, reference, levels = AXES[axis]
    title = f"bloco F (2 disciplinas)" if block == "F" else f"bloco S, {subjects} disciplina(s)"
    print(f"### {axis}: contrastes contra `{reference}`, {title}\n")
    print("| contraste | motor | folga | n | plano idêntico | métrica | média Δ | mediana Δ "
          "| +/=/− | DP entre sementes | média por sorteio | material |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for level in levels:
        for engine in ENGINES:
            for tight in TIGHTNESS:
                found = [(a, b) for a, b in pairs(idx, rows, axis, level, block, subjects)
                         if a["engine"] == engine and a["tightness"] == tight]
                if not found:
                    continue
                for metric in DECISION:
                    s = summarise(found, metric)
                    print(f"| {level} − {reference} | {engine} | {float(tight):.1f} | {s['n']} "
                          f"| {s['identical']:.2f} | {metric} | {s['mean']:+.2f} "
                          f"| {s['median']:+.1f} | {s['wtl'][0]}/{s['wtl'][1]}/{s['wtl'][2]} "
                          f"| {s['seed_sd']:.2f} | "
                          + " ".join(f"{m:+.2f}" for m in s["rep"])
                          + f" | {'**sim**' if s['material'] else 'não'} |")
    print()


def material_summary(idx, rows):
    print("## Resumo: onde cada contraste passa o limiar pré-registrado\n")
    print("| eixo | contraste | bloco | motor | folga | métrica | média Δ | plano idêntico |")
    print("|---|---|---|---|---|---|---|---|")
    for axis, (_, reference, levels) in AXES.items():
        for block, subjects in (("F", None), ("S", "1"), ("S", "3")):
            for level in levels:
                for engine in ENGINES:
                    for tight in TIGHTNESS:
                        found = [(a, b) for a, b in pairs(idx, rows, axis, level, block, subjects)
                                 if a["engine"] == engine and a["tightness"] == tight]
                        if not found:
                            continue
                        for metric in DECISION:
                            s = summarise(found, metric)
                            if s["material"]:
                                where = "F" if block == "F" else f"S{subjects}"
                                print(f"| {axis} | {level} − {reference} | {where} | {engine} "
                                      f"| {float(tight):.1f} | {metric} | {s['mean']:+.2f} "
                                      f"| {s['identical']:.2f} |")
    print()


def identical_summary(idx, rows):
    print("## Taxa de plano idêntico por contraste, motor e bloco (todas as folgas)\n")
    print("| eixo | contraste | bloco | greedy-baseline | ga | ga-timeline |")
    print("|---|---|---|---|---|---|")
    for axis, (_, reference, levels) in AXES.items():
        for block, subjects in (("F", None), ("S", "1"), ("S", "3")):
            for level in levels:
                cells = []
                for engine in ENGINES:
                    found = [(a, b) for a, b in pairs(idx, rows, axis, level, block, subjects)
                             if a["engine"] == engine]
                    cells.append(f"{sum(a['plan_hash'] == b['plan_hash'] for a, b in found)}"
                                 f"/{len(found)}" if found else "—")
                where = "F" if block == "F" else f"S{subjects}"
                print(f"| {axis} | {level} − {reference} | {where} | " + " | ".join(cells) + " |")
    print()


def engines(idx, rows, challenger, selection, label):
    print(f"### `{challenger}` contra `greedy-baseline` — {label}\n")
    print("| importância | folga | métrica | V/E/D (menos inversões / mais tópicos = vitória) "
          "| média Δ |")
    print("|---|---|---|---|---|")
    metrics = [("soft_inversions", -1), ("topics_scheduled", +1),
               ("order_inversions_all_edges", -1), ("dependents_without_prerequisite", -1)]
    for importance in ["flat", "close", "extreme", "*"]:
        for tight in TIGHTNESS + ["*"]:
            found = []
            for row in rows:
                if row["engine"] != challenger or not selection(row):
                    continue
                if importance != "*" and row["importance_level"] != importance:
                    continue
                if tight != "*" and row["tightness"] != tight:
                    continue
                key = list(tuple(row[k] for k in KEY))
                key[KEY.index("engine")] = "greedy-baseline"
                found.append((row, idx[tuple(key)]))
            if not found:
                continue
            for metric, sign in metrics:
                diffs = [value(a, metric) - value(b, metric) for a, b in found]
                wins = sum(d * sign > 0 for d in diffs)
                ties = sum(d == 0 for d in diffs)
                print(f"| {importance} | {tight if tight == '*' else float(tight)} | {metric} "
                      f"| {wins}/{ties}/{len(diffs) - wins - ties} | {statistics.mean(diffs):+.2f} |")
    print()


def main(path):
    rows = load(path)
    idx = index(rows)
    integrity(rows)
    material_summary(idx, rows)
    identical_summary(idx, rows)
    for axis in AXES:
        contrast_table(idx, rows, axis, "F")
        contrast_table(idx, rows, axis, "S", "1")
        contrast_table(idx, rows, axis, "S", "3")
    center = lambda r: (r["design_role"] == "factorial" and r["strength_level"] == "curated-mix"
                        and r["effort_level"] == "tiers-platform")
    whole = lambda r: r["design_role"] == "factorial"
    for challenger in ("ga", "ga-timeline"):
        engines(idx, rows, challenger, center, "bloco F, força e esforço no centro")
        engines(idx, rows, challenger, whole, "bloco F inteiro")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "benchmarks/results/curation-axes.csv")

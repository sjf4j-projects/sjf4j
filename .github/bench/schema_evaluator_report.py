#!/usr/bin/env python3
"""Summarize main effects (and interactions) for the 2x2 JMH Evaluator experiment."""
import json
import math
import os
import sys
from collections import defaultdict
from pathlib import Path

VARIANTS = ["loop-fresh", "walker-fresh", "loop-reuse", "walker-reuse"]
CONTRASTS = [
    ("anyMatch, leaf=fresh", "loop-fresh", "walker-fresh"),
    ("anyMatch, leaf=reuse", "loop-reuse", "walker-reuse"),
    ("reusedLeaf, traversal=loop", "loop-fresh", "loop-reuse"),
    ("reusedLeaf, traversal=anyMatch", "walker-fresh", "walker-reuse"),
]


def entries(folder, variant):
    results = {}
    for group in ("object", "array"):
        path = folder / (variant + "-" + group + ".json")
        with path.open(encoding="utf-8") as f:
            for row in json.load(f):
                key = (row["benchmark"].rsplit(".", 1)[-1],
                       tuple(sorted(row.get("params", {}).items())))
                if key in results:
                    raise AssertionError("Duplicate case: " + str(key))
                results[key] = row
    return results


def amount(row, metric):
    if metric == "ns/op":
        return row["primaryMetric"]["score"]
    return row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]


def percent(old, new):
    return (new / old - 1.0) * 100 if old > 0 else float("nan")


def geomean_percent(triples):
    ratios = [new / old for old, new in triples if old > 0 and new > 0]
    return (math.exp(sum(math.log(r) for r in ratios) / len(ratios)) - 1) * 100


def fmt(value):
    return ("%+.1f%%" % value) if math.isfinite(value) else "N/A"


def main():
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else "results")
    if any(not (folder / (variant + "-" + group + ".json")).exists()
           for variant in VARIANTS for group in ("object", "array")):
        print("Factorial JMH results not complete yet.")
        return 0

    data = {variant: entries(folder, variant) for variant in VARIANTS}
    keys = set(data[VARIANTS[0]])
    for variant in VARIANTS[1:]:
        if set(data[variant]) != keys:
            raise AssertionError("JMH parameter combinations differ: " + variant)

    print("Factorial comparison (% change in ns/op; positive means the second implementation is slower)")
    print("All variants come from the same pinned source commit, with the same JMH inputs.")
    print("Number of matched benchmark cases:", len(keys))
    print("")

    groups = defaultdict(list)
    for key in keys:
        method, params = key
        p = dict(params)
        groups[(method, p.get("representation", "?"))].append(key)

    header = "%-37s %3s | %-16s %-16s | %-16s %-16s" % (
        "Benchmark / representation", "N", "anyMatch fresh", "anyMatch reuse",
        "reusedLeaf loop", "reusedLeaf walker")
    print(header)
    print("-" * len(header))
    lines = [
        "| Benchmark | N | anyMatch (fresh) | anyMatch (reuse) | reusedLeaf (loop) | reusedLeaf (anyMatch) |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for group, members in sorted(groups.items()):
        vals = []
        for _, old, new in CONTRASTS:
            vals.append(geomean_percent([(amount(data[old][key], "ns/op"),
                                         amount(data[new][key], "ns/op")) for key in members]))
        label = " / ".join(group)
        print("%-37s %3d | %-16s %-16s | %-16s %-16s" %
              (label, len(members), *(fmt(v) for v in vals)))
        lines.append("| %s | %d | %s | %s | %s | %s |" %
                     (label, len(members), *(fmt(v) for v in vals)))
    print("-" * len(header))
    vals = []
    allocs = []
    for _, old, new in CONTRASTS:
        vals.append(geomean_percent([(amount(data[old][key], "ns/op"),
                                     amount(data[new][key], "ns/op")) for key in keys]))
        allocs.append(geomean_percent([(amount(data[old][key], "B/op"),
                                       amount(data[new][key], "B/op")) for key in keys]))
    print("%-37s %3d | %-16s %-16s | %-16s %-16s" %
          ("OVERALL GEOMETRIC MEAN", len(keys), *(fmt(v) for v in vals)))
    print("%-37s %3d | %-16s %-16s | %-16s %-16s" %
          ("ALLOCATIONS B/op geometric mean", len(keys), *(fmt(v) for v in allocs)))
    lines += [
        "| **Overall ns/op** | %d | %s | %s | %s | %s |" %
        (len(keys), *(fmt(v) for v in vals)),
        "| **Overall B/op** | %d | %s | %s | %s | %s |" %
        (len(keys), *(fmt(v) for v in allocs)),
    ]
    print("\nDetailed cases, ns/op followed by B/op:")
    print("benchmark | representation | size | scenario | loop/fresh | walker/fresh | loop/reuse | walker/reuse")
    for key in sorted(keys):
        method, params = key
        p = dict(params)
        measurements = [
            "%.1f (%.0f)" % (amount(data[v][key], "ns/op"), amount(data[v][key], "B/op"))
            for v in VARIANTS
        ]
        print(" | ".join([method, p.get("representation", ""), p.get("length", "-"),
                          p.get("scenario", "")] + measurements))

    summary = "\n".join([
        "## Schema Evaluator 2×2 JMH comparison",
        "",
        "Baseline commit: fc7734219996ba0576cc4e5bbb4cd0e3b805a988",
        "",
        "Positive percentages indicate slower runtime / more allocation.",
        "Geometric-mean relative changes are descriptive; inspect per-case JMH variance.",
        "",
        *lines,
        "",
    ])
    (folder / "summary.md").write_text(summary, encoding="utf-8")
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(summary)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

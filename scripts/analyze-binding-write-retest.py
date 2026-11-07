#!/usr/bin/env python3
"""Summarize JMH Jackson Write paired cases without treating iterations as independent forks."""

import argparse
import itertools
import json
import math
import os
import statistics
from pathlib import Path

BACKENDS = ("jackson2", "jackson3")
MODELS = ("users", "clients")
SIZES = ("10", "100")
SEEDS = ("12345", "324")
IMPLEMENTATIONS = ("runtime", "compiled")


def parse(path):
    with path.open(encoding="utf-8") as inp:
        rows = json.load(inp)
    expected = len(MODELS) * len(SIZES) * len(SEEDS) * len(IMPLEMENTATIONS)
    if len(rows) != expected:
        raise ValueError(f"{path}: expected {expected} JMH cases, got {len(rows)}")
    result = {}
    for row in rows:
        p = row["params"]
        benchmark = row["benchmark"]
        if not benchmark.endswith("FabienWriteBenchmark.write") or p["shape"] != "pojo":
            raise ValueError(f"Unexpected workload: {benchmark}, {p}")
        backend = p["backend"]
        if backend != path.stem or backend not in BACKENDS:
            raise ValueError(f"Wrong backend for {path}: {backend}")
        key = (backend, p["model"], p["sizeKb"], p["seed"], p["implementation"])
        if key in result:
            raise ValueError(f"Duplicate case: {key}")
        forks = row["primaryMetric"]["rawData"]
        if len(forks) != 5 or any(len(fork) != 5 for fork in forks):
            raise ValueError(f"Expected 5 independent forks x 5 measurement iterations: {key}")
        fork_means = [statistics.mean(fork) for fork in forks]
        if not all(math.isfinite(value) and value > 0 for value in fork_means):
            raise ValueError(f"Non-finite or non-positive score: {key}")
        result[key] = fork_means
    for model, size, seed, implementation in itertools.product(
        MODELS, SIZES, SEEDS, IMPLEMENTATIONS
    ):
        key = (path.stem, model, size, seed, implementation)
        if key not in result:
            raise ValueError(f"Missing case: {key}")
    return result


def run(paths, output):
    results = {}
    for path in paths:
        for key, forks in parse(path).items():
            if key in results:
                raise ValueError(f"Duplicate result across files: {key}")
            results[key] = forks
    lines = [
        "# Jackson POJO Write - JDK 25 repeatability",
        "",
        "GitHub-hosted Ubuntu 24.04, JDK 25, 5 independent JMH forks x "
        "5 measurements (300ms), 3 warmups (200ms).",
        "",
        "Each row compares identical backend, model, size and seed. "
        "Positive delta means Compiled is slower than Runtime. "
        "All times are us/op.",
        "",
        "Fork separation: SLOWER means every Compiled fork mean exceeds every "
        "Runtime fork mean; FASTER the reverse; OVERLAP means the "
        "fork-mean ranges overlap. This is directional evidence only, not "
        "a formal statistical significance test.",
        "",
        "| Backend | Model | KB | Seed | Runtime | Compiled | Delta | Fork separation |",
        "|---|---|---:|---:|---:|---:|---:|---|",
    ]
    summary = []
    for backend, model, size in itertools.product(BACKENDS, MODELS, SIZES):
        deltas = []
        signals = []
        for seed in SEEDS:
            runtime = results[(backend, model, size, seed, "runtime")]
            compiled = results[(backend, model, size, seed, "compiled")]
            rm, cm = statistics.mean(runtime), statistics.mean(compiled)
            delta = (cm / rm - 1) * 100
            if min(compiled) > max(runtime):
                signal = "SLOWER"
            elif max(compiled) < min(runtime):
                signal = "FASTER"
            else:
                signal = "OVERLAP"
            deltas.append(cm / rm)
            signals.append(signal)
            lines.append(
                f"| {backend} | {model} | {size} | {seed} | "
                f"{rm:.2f} | {cm:.2f} | {delta:+.1f}% | {signal} |"
            )
        georatio = math.prod(deltas) ** (1 / len(deltas))
        summary.append((backend, model, size, georatio, signals))
    lines.extend([
        "",
        "## Paired-seed summary",
        "",
        "Equal-weight geometric mean of Compiled/Runtime time ratios across "
        "the two seeds (not an average of 10KB and 100KB latencies).",
        "",
        "| Backend | Model | KB | Compiled latency delta | Seed signs | Fork separation |",
        "|---|---|---:|---:|---|---|",
    ])
    for backend, model, size, georatio, signals in summary:
        labels = ", ".join(signals)
        lines.append(
            f"| {backend} | {model} | {size} | "
            f"{(georatio - 1) * 100:+.1f}% | {sum(s == 'SLOWER' for s in signals)}/2 slower | {labels} |"
        )
    lines.extend([
        "",
        "## Interpretation",
        "",
        "If both seeds for a model/size have SLOWER fork separation, the "
        "direction is consistent across all tested forks on this runner. "
        "GitHub-hosted VM performance is noisy and differs from the macOS "
        "Quick baseline: do not compare absolute timings across hosts. "
        "Repeat on the same dedicated machine before publishing numerical claims.",
        "",
    ])
    report = "\n".join(lines)
    output.write_text(report, encoding="utf-8")
    print(report)
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary_file:
            summary_file.write(report)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("json_files", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run(args.json_files, args.output)

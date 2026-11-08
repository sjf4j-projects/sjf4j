#!/usr/bin/env python3
"""Summarize a pair of Creek JMH groups without hiding the raw fork measurements."""

import argparse
import json
import math
from pathlib import Path
import statistics

METHODS = {
    "validation": (
        "pojo_isValid_runtime",
        "pojo_isValid_compiled",
        "map_isValid_runtime",
        "map_isValid_compiled",
        "jsonNode_isValid_runtime",
        "jsonNode_isValid_networknt",
    ),
    "roundtrip": (
        "roundtrip_jackson",
        "roundtrip_compiled",
        "roundtrip_isValid_runtime",
        "roundtrip_isValid_compiled",
    ),
}


def load_group(path: Path, group: str):
    rows = json.loads(path.read_text(encoding="utf-8"))
    found = {}
    for row in rows:
        name = row["benchmark"].rsplit(".", 1)[-1]
        if not row["benchmark"].endswith("CreekSchemaBenchmark." + name):
            raise ValueError(f"Unexpected benchmark: {row['benchmark']}")
        if name not in METHODS[group] or name in found:
            raise ValueError(f"Unexpected or duplicate {group} method: {name}")
        metric = row["primaryMetric"]
        if metric["scoreUnit"] != "us/op" or not math.isfinite(metric["score"]) or metric["score"] <= 0:
            raise ValueError(f"Invalid JMH score for {name}: {metric}")
        raw = metric.get("rawData", [])
        if len(raw) != 3 or any(len(fork) != 5 for fork in raw):
            raise ValueError(f"{name} is not a Full run (expected 3 independent forks x 5 iterations)")
        found[name] = row
    if set(found) != set(METHODS[group]):
        raise ValueError(f"{group}: missing {set(METHODS[group]) - set(found)}")
    return found


def format_alloc(row):
    metric = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
    return f'{metric["score"]:.1f}' if metric and math.isfinite(metric["score"]) else "n/a"


def score(row):
    return row["primaryMetric"]["score"]


def describe_row(name, row):
    metric = row["primaryMetric"]
    return f'| {name} | {score(row):.3f} | {metric["scoreError"]:.3f} | {format_alloc(row)} |'


def compare(rows, title, baseline, candidate):
    before = score(rows[baseline])
    after = score(rows[candidate])
    speedup = before / after
    delta = (after / before - 1.0) * 100.0
    return f'| {title} | {speedup:.3f}x | {delta:+.1f}% |'


def fork_dispersion(row):
    forks = [statistics.mean(fork) for fork in row["primaryMetric"]["rawData"]]
    return 100 * statistics.stdev(forks) / statistics.mean(forks)


def run(validation: Path, roundtrip: Path, output: Path):
    val = load_group(validation, "validation")
    rt = load_group(roundtrip, "roundtrip")
    combined = {**val, **rt}
    lines = [
        "# Creek Schema Full Benchmark — JDK 25",
        "",
        "Draft 2020-12, prebuilt Creek fixture, AverageTime (us/op; lower is better).",
        "3 independent JMH forks, 5 warmups × 400ms, 5 measurements × 400ms, 1 thread.",
        "",
        "**Important:** This GitHub-hosted runner report is exploratory, not a release baseline.",
        "Validate isValid uses non-strict format; Schema and instances are prepared before measurement.",
        "CompiledValidator retains fallback=true for unsupported Creek paths.",
        "NetworkNT 2.x uses a pre-parsed Jackson2 JsonNode and boolean fail-fast validation.",
        "The POJO, Map, and JsonNode representations are distinct and should not be conflated.",
        "",
        "## All measurements",
        "",
        "| Method | us/op | JMH error (±) | B/op |",
        "| --- | ---: | ---: | ---: |",
    ]
    for group in ("validation", "roundtrip"):
        lines.append(f"| **{group.upper()}** | | | |")
        for name in METHODS[group]:
            lines.append(describe_row(name, combined[name]))
    lines += [
        "",
        "## Within-representation comparisons",
        "",
        "Speedup is baseline time divided by candidate time (>1x means candidate faster).",
        "Latency change = (candidate / baseline - 1) × 100%.",
        "",
        "| Comparison | Speedup | Latency change |",
        "| --- | ---: | ---: |",
        compare(val, "POJO: Compiled / Runtime", "pojo_isValid_runtime", "pojo_isValid_compiled"),
        compare(val, "Map: Compiled / Runtime", "map_isValid_runtime", "map_isValid_compiled"),
        compare(val, "JsonNode: NetworkNT / SJF4J Runtime", "jsonNode_isValid_networknt", "jsonNode_isValid_runtime"),
        compare(rt, "Roundtrip: CompiledBinder / Jackson2", "roundtrip_jackson", "roundtrip_compiled"),
        compare(rt, "Roundtrip: CompiledBinder + CompiledValidator / Jackson2 (no validation)",
                "roundtrip_jackson", "roundtrip_isValid_compiled"),
        compare(rt, "Roundtrip: CompiledBinder + CompiledValidator / Jackson2 + Runtime Validator",
                "roundtrip_isValid_runtime", "roundtrip_isValid_compiled"),
        "",
        "## Fork dispersion",
        "",
        "Sample CV of three independent fork means. Large CV or small differences warrant re-measurement.",
        "",
        "| Method | Fork-mean CV |",
        "| --- | ---: |",
    ]
    for group in ("validation", "roundtrip"):
        for name in METHODS[group]:
            lines.append(f'| {name} | {fork_dispersion(combined[name]):.1f}% |')
    lines += [
        "",
        "## Interpretation",
        "",
        "- The primary quality gate is roundtrip_isValid_compiled vs roundtrip_jackson:",
        "  the compiled pipeline includes schema validation while Jackson2 does not.",
        "- The NetworkNT result is directly comparable to jsonNode_isValid_runtime",
        "  (same pre-parsed JsonNode), not to the Map or POJO validations.",
        "- All score and allocation figures are descriptive; JMH errors and fork variability",
        "  must be considered before making claims about performance regressions.",
        "- Use the raw JSON files and environment metadata for comparisons across runs.",
        "",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {output} with {len(combined)} verified full JMH methods")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("validation", type=Path)
    parser.add_argument("roundtrip", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run(args.validation, args.roundtrip, args.output)


if __name__ == "__main__":
    main()

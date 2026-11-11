#!/usr/bin/env python3
"""Summarize the four complete Mapping Full JMH JSON groups without changing raw data."""
import argparse
import json
import math
from pathlib import Path
from statistics import mean

PREFIX = "org.sjf4j.testbench.mapping.MappingFullBenchmark."
GROUPS = {
    "order": (("order_classic", False), ("order_extended", True)),
    "pojo": (("pojo_flat", False), ("pojo_nested", False), ("pojo_update", False)),
    "collection": (("collection_list", True), ("collection_map", True)),
    "json": (("json_map", True), ("json_object", True)),
}
LABELS = {
    "order_classic": "Classic Order (Arey-style)",
    "order_extended": "Extended Order",
    "pojo_flat": "Flat / rename / compute",
    "pojo_nested": "Nested / multi-source / record",
    "pojo_update": "Update / null ignore",
    "collection_list": "List mapping",
    "collection_map": "Map value mapping",
    "json_map": "Map → POJO",
    "json_object": "JsonObject → POJO",
}


def load(path: Path):
    with path.open(encoding="utf-8") as handle:
        rows = json.load(handle)
    if not isinstance(rows, list):
        raise ValueError(f"{path}: expected a JMH JSON array")
    return rows


def calc(row):
    metric = row["primaryMetric"]
    if metric["scoreUnit"] != "ns/op":
        raise ValueError(f"{row['benchmark']}: expected ns/op")
    raw = metric.get("rawData", [])
    if len(raw) != 3 or any(len(fork) != 5 for fork in raw):
        raise ValueError(f"{row['benchmark']}: Full requires 3 forks of 5 iterations")
    fork_means = [mean(float(n) for n in fork) for fork in raw]
    if any(not math.isfinite(x) or x <= 0 for x in fork_means):
        raise ValueError(f"{row['benchmark']}: invalid fork values")
    best2 = mean(sorted(fork_means)[:2])
    instability = max(fork_means) / min(fork_means) > 1.15
    gc = (row.get("secondaryMetrics") or {}).get("gc.alloc.rate.norm")
    if gc is None:
        raise ValueError(f"{row['benchmark']}: missing GC B/op")
    alloc = gc["score"]
    if not math.isfinite(alloc) or alloc < 0:
        raise ValueError(f"{row['benchmark']}: invalid B/op")
    return best2, float(alloc), fork_means, float(metric["score"]), instability


def expected_keys():
    result = set()
    for group, cases in GROUPS.items():
        for case, sized in cases:
            for impl in ("mapstruct", "compiled"):
                for size in (("1", "10", "100") if sized else (None,)):
                    result.add((group, case, size, impl))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("json_files", nargs="+", type=Path,
                        help="four mapping-{order,pojo,collection,json}.json files")
    parser.add_argument("--output", type=Path, help="write Markdown (also prints to stdout)")
    args = parser.parse_args()
    values = {}
    group_seen = set()
    for path in args.json_files:
        group = path.stem.removeprefix("mapping-")
        if group not in GROUPS or group in group_seen:
            raise ValueError(f"Unexpected or duplicate group file: {path}")
        group_seen.add(group)
        for row in load(path):
            name = row["benchmark"]
            if not name.startswith(PREFIX):
                raise ValueError(f"Unexpected benchmark: {name}")
            suffix = name.removeprefix(PREFIX)
            impl = suffix.rsplit("_", 1)[-1]
            case = suffix[:-(len(impl) + 1)]
            size = (row.get("params") or {}).get("sizeKb")
            key = (group, case, size, impl)
            if key in values:
                raise ValueError(f"Duplicate JMH entry: {key}")
            values[key] = calc(row)
    expected = expected_keys()
    if set(values) != expected:
        raise ValueError(f"Expected {len(expected)} exact cases, got {len(values)}. "
                         f"Missing={expected - set(values)}, extra={set(values) - expected}")
    output = [
        "# Mapping Full Benchmark — MapStruct vs SJF4J Compiled",
        "",
        "**Full JMH · JDK 25 · 3 forks · Best 2 Mean · ns/op (lower is better).**",
        "Each implementation independently discards its single slowest fork.",
        "B/op is the untrimmed JMH allocation score; absolute values are shown in separate columns.",
        "",
    ]
    unstable = []
    for group, cases in GROUPS.items():
        output.extend([
            f"## {group.capitalize()}",
            "",
            "| Case | Size | MapStruct | Compiled | MapStruct / Compiled | MapStruct B/op | Compiled B/op |",
            "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
        ])
        for case, sized in cases:
            for size in (("1", "10", "100") if sized else (None,)):
                ma = values[(group, case, size, "mapstruct")]
                co = values[(group, case, size, "compiled")]
                for impl, item in (("MapStruct", ma), ("Compiled", co)):
                    if item[4]:
                        unstable.append((group, case, size, impl, item[2], item[3]))
                size_text = f"{size}KB" if size else "—"
                output.append(
                    f"| {LABELS[case]} | {size_text} | {ma[0]:,.2f} | {co[0]:,.2f} | "
                    f"{ma[0] / co[0]:.2f}× | {ma[1]:,.0f} | {co[1]:,.0f} |"
                )
        output.append("")
    output.extend([
        "## Interpretation",
        "",
        "A ratio above 1× means Compiled is faster; below 1× means MapStruct is faster.",
        "Do not average across sizes or input shapes. Update is an in-place workload.",
        "These results are exploratory when collected on shared GitHub Actions runners.",
        "Original JMH 3-fork means and error bounds remain in the raw JSON; JMH",
        "scoreError must not be applied to the Best 2 values above.",
        "",
    ])
    if unstable:
        output.extend([
            "## Fork stability warnings (>15% spread)",
            "",
            "| Group | Case | Size | Implementation | Fork means (ns/op) | JMH original mean |",
            "| --- | --- | --- | --- | --- | ---: |",
        ])
        for group, case, size, impl, forks, original in unstable:
            output.append(f"| {group} | {LABELS[case]} | {size or '—'} | {impl} | "
                          f"{' / '.join(f'{v:,.2f}' for v in forks)} | {original:,.2f} |")
        output.append("")
    text = "\n".join(output) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
    print(text, end="")


if __name__ == "__main__":
    main()

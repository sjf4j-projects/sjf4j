#!/usr/bin/env python3
"""Report isolated byKey/byIndex reusedLeaf performance effects."""
import json
import math
import os
import sys
from collections import defaultdict
from pathlib import Path

MODES = ["none", "key", "index", "both"]

def load(folder, mode):
    rows = {}
    for suffix in ["object", "array"]:
        with (folder / (mode + "-" + suffix + ".json")).open(encoding="utf-8") as f:
            for x in json.load(f):
                key = (x["benchmark"].rsplit(".", 1)[-1], tuple(sorted(x.get("params", {}).items())))
                if key in rows:
                    raise ValueError("Duplicate benchmark " + str(key))
                rows[key] = x
    return rows

def value(row, metric):
    return (row["primaryMetric"]["score"] if metric == "time"
            else row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"])

def contrast(data, keys, left, right, metric):
    ratios = [value(data[right][k], metric) / value(data[left][k], metric)
              for k in keys if value(data[left][k], metric) > 0 and value(data[right][k], metric) > 0]
    return 100 * (math.exp(sum(map(math.log, ratios)) / len(ratios)) - 1)

def pct(x):
    return f"{x:+.1f}%"

def main():
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else "results")
    data = {mode: load(folder, mode) for mode in MODES}
    keys = set(data["index"])
    for mode in MODES:
        if set(data[mode]) != keys:
            raise AssertionError("Different parameter combinations: " + mode)
    object_cases = [k for k in keys if "properties" in k[0]]
    array_cases = [k for k in keys if "properties" not in k[0]]
    print(f"Matched {len(keys)} cases (object={len(object_cases)}, array={len(array_cases)})")
    print("Positive ns/op is slower; negative B/op means fewer allocations.")
    comparisons = [
        ("byKey: index only -> both", object_cases, "index", "both"),
        ("byKey: none -> key only", object_cases, "none", "key"),
        ("byIndex: none -> index only", array_cases, "none", "index"),
        ("byIndex: key only -> both", array_cases, "key", "both"),
    ]
    summary = [
        "## ReusedLeaf byKey/byIndex factorial benchmark",
        "",
        "Pinned baseline: 030fe874827c31fe6b1707a39812f3b6db62f668",
        "",
        "none: neither reused; key: only byKey reused; index: only byIndex reused (exact main); both: both reused.",
        "",
        "Positive time delta means slower; negative B/op delta means lower allocation.",
        "",
        "| Contrast | Time (ns/op) | Allocation (B/op) | Cases |",
        "|---|---:|---:|---:|",
    ]
    print("\nIsolated effects: geometric mean paired deltas")
    for label, selected, a, b in comparisons:
        time = pct(contrast(data, selected, a, b, "time"))
        alloc = pct(contrast(data, selected, a, b, "alloc"))
        print(f"{label:34} {time:>9} time {alloc:>9} B/op (N={len(selected)})")
        summary.append(f"| {label} | {time} | {alloc} | {len(selected)} |")
    print("\nCURRENT MAIN (index) -> ALWAYS REUSE (both), by benchmark:")
    summary += ["", "### Current main versus always reuse, by benchmark", "",
                "| Benchmark | Cases | Time (ns/op) | Allocation (B/op) |",
                "|---|---:|---:|---:|"]
    groups = defaultdict(list)
    for key in keys:
        groups[(key[0], dict(key[1]).get("representation", "?"))].append(key)
    for (method, rep), selected in sorted(groups.items()):
        label = method + " / " + rep
        time = pct(contrast(data, selected, "index", "both", "time"))
        alloc = pct(contrast(data, selected, "index", "both", "alloc"))
        print(f"{label:50} {time:>9} time {alloc:>9} B/op (N={len(selected)})")
        summary.append(f"| {label} | {len(selected)} | {time} | {alloc} |")
    print("\nDetailed JMH results: ns/op (B/op), columns none / key / index / both")
    for key in sorted(keys):
        print(key[0], dict(key[1]), *[
            "%.2f (%.0f)" % (value(data[mode][key], "time"), value(data[mode][key], "alloc"))
            for mode in MODES])
    text = "\n".join(summary) + "\n"
    (folder / "summary.md").write_text(text, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(text)

if __name__ == "__main__":
    main()

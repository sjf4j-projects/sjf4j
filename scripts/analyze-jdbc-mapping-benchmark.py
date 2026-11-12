#!/usr/bin/env python3
"""Strict JDBC Mapping Full JMH reporter: independent Best 2 Mean forks."""
import argparse
import json
import math
from pathlib import Path

PREFIX = "org.sjf4j.testbench.mapping.JdbcFullBenchmark."
SIZES = ("1", "10", "1000", "100000")
GROUPS = {
    "pojo": {
        "list": ("compiled", "spring", "mybatisAuto", "mybatisExplicit", "mybatisCursor"),
        "row": ("compiled", "spring"),
    },
    "record": {"list": ("compiled", "spring"), "row": ("compiled", "spring")},
    "map": {
        "list": ("compiled", "spring", "mybatisExplicit", "mybatisCursor"),
        "row": ("compiled", "spring"),
    },
}
LABELS = {
    "compiled": "Compiled",
    "spring": "Spring",
    "mybatisAuto": "MyBatis Auto",
    "mybatisExplicit": "MyBatis Explicit",
    "mybatisCursor": "MyBatis Cursor to List",
}


def load_results(paths):
    found = {}
    for path in paths:
        with open(path, encoding="utf-8") as stream:
            for result in json.load(stream):
                name = result["benchmark"]
                if not name.startswith(PREFIX):
                    raise ValueError(f"Unexpected benchmark: {name}")
                group, mode, implementation = name[len(PREFIX):].split("_", 2)
                if group not in GROUPS or implementation not in GROUPS[group].get(mode, ()):
                    raise ValueError(f"Unexpected case: {name}")
                rows = (result.get("params") or {}).get("rows")
                key = (group, mode, implementation, rows)
                if rows not in SIZES or key in found:
                    raise ValueError(f"Duplicate or unexpected rows: {key}")
                metric = result["primaryMetric"]
                if metric["scoreUnit"] not in ("us/op", "µs/op"):
                    raise ValueError(f"Unexpected time unit: {metric['scoreUnit']}")
                raw = metric.get("rawData") or []
                if len(raw) != 3 or any(len(fork) != 5 for fork in raw):
                    raise ValueError(f"Requires 3 complete forks and 5 iterations: {key}")
                fork_means = [sum(fork) / 5 for fork in raw]
                if not all(math.isfinite(v) and v > 0 for v in fork_means):
                    raise ValueError(f"Invalid fork means: {key}")
                best_two = sum(sorted(fork_means)[:2]) / 2
                allocation = (result.get("secondaryMetrics") or {}).get("gc.alloc.rate.norm")
                if not allocation:
                    raise ValueError(f"Missing allocation: {key}")
                b_op = allocation["score"]
                if not math.isfinite(b_op) or b_op < 0:
                    raise ValueError(f"Invalid B/op: {key}")
                found[key] = (best_two, b_op, fork_means)
    expected = {(g, m, i, rows)
                for g, modes in GROUPS.items()
                for m, implementations in modes.items()
                for i in implementations for rows in SIZES}
    if set(found) != expected:
        raise ValueError(f"Missing={sorted(expected - set(found))}; extra={sorted(set(found) - expected)}")
    return found


def render(found):
    lines = [
        "# JDBC Mapping — Full JMH report",
        "",
        "**JDK 25 · 3 independent forks · Best 2 Mean · 1/10/1,000/100,000 rows · 12 columns.**",
        "Timing is µs/op (entire result), lower is better. B/op is untrimmed JMH allocation.",
        "SQL execution is outside timed methods; shared runner results are exploratory.",
        "",
    ]
    unstable = []
    for group, modes in GROUPS.items():
        lines.extend((f"## {group.upper()}", ""))
        for mode, implementations in modes.items():
            lines.extend((f"### {mode.capitalize()}", ""))
            for allocation in (False, True):
                lines.append(f"**{'Allocation (B/op)' if allocation else 'Latency (µs/op)'}**")
                lines.append("")
                header = ["Rows"] + [LABELS[i] for i in implementations]
                ratios = [i for i in implementations if i != "compiled"]
                header.extend(f"{LABELS[i]} / Compiled" for i in ratios)
                lines.append("| " + " | ".join(header) + " |")
                lines.append("| " + " | ".join(["---:"] * len(header)) + " |")
                for rows in SIZES:
                    base = found[(group, mode, "compiled", rows)][1 if allocation else 0]
                    values = [int(rows)]
                    for implementation in implementations:
                        value, alloc, forks = found[(group, mode, implementation, rows)]
                        values.append(f"{alloc:,.0f}" if allocation else f"{value:,.3f}")
                        if not allocation and max(forks) / min(forks) > 1.15:
                            unstable.append((group, mode, implementation, rows, forks))
                    for implementation in ratios:
                        other = found[(group, mode, implementation, rows)][1 if allocation else 0]
                        values.append(f"{other / base:.2f}×" if base > 0 else "—")
                    lines.append("| " + " | ".join(map(str, values)) + " |")
                lines.append("")
            if "mybatisCursor" in implementations:
                lines.append("MyBatis Cursor is fully consumed into a List, not a direct RowMapper API.")
                lines.append("")
    lines.extend(("## Fork stability", ""))
    if unstable:
        lines.append("Latency cases with slowest/fastest fork divergence above 15%:")
        lines.append("")
        for group, mode, implementation, rows, forks in unstable:
            lines.append(f"- {group}/{mode}/{implementation}/{rows}: "
                         f"{max(forks) / min(forks):.2f}×; fork means "
                         + ", ".join(f"{value:.3f}" for value in forks) + " µs")
    else:
        lines.append("No latency case exceeded the 15% slowest/fastest fork-spread threshold.")
    lines.extend((
        "",
        "## Methodology",
        "",
        "- Each case independently discards the slowest of three fork means, then averages the other two.",
        "- Allocation uses the untrimmed JMH mean, not the latency-selected forks.",
        "- All methods produce an equivalent full List, including Row mode's explicit cursor loop.",
        "- Spring Map uses a case-insensitive Map; MyBatis and SJF4J use different Map implementations.",
        "- Invocation setup/teardown may reduce precision for very short (1-row) measurements.",
        "- This measures ResultSet processing, not database SQL/driver/network throughput.",
        "",
    ))
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("results", nargs="+", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = render(load_results(args.results))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report)


if __name__ == "__main__":
    main()

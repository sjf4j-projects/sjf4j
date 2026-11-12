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
        "list": ("spring", "mybatisAuto", "mybatisExplicit", "mybatisCursor", "compiled"),
        "row": ("spring", "compiled"),
    },
    "record": {"list": ("spring", "compiled"), "row": ("spring", "compiled")},
    "map": {
        "list": ("spring", "mybatisExplicit", "mybatisCursor", "compiled"),
        "row": ("spring", "compiled"),
    },
}
LABELS = {
    "compiled": "SJF4J Compiled",
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
    """Group results by competitor; always show absolute metrics before ratios."""
    lines = [
        "# JDBC Mapping — Full JMH report",
        "",
        "**JDK 25 · 3 independent forks · Best 2 Mean · 1/10/1,000/100,000 rows · 12 columns.**",
        "Time is µs/op (entire ResultSet), lower is better; allocations are B/op",
        "from the untrimmed JMH profiler. SQL execution is outside timed methods.",
        "Ratios are competitor / SJF4J Compiled (greater than 1× favors SJF4J).",
        "",
    ]

    def table_row(values):
        return "| " + " | ".join(str(value) for value in values) + " |"

    def timing(value):
        return f"{value:,.3f}"

    def allocated(value):
        return f"{value:,.0f}"

    def ratio(competitor, compiled):
        return f"{competitor / compiled:.2f}×"

    lines.extend([
        "## Spring JDBC",
        "",
        "List and Row modes for POJO, Record and Map.",
        "",
        table_row(("Shape", "Mode", "Rows", "Spring (µs/op)", "Spring (B/op)",
                   "SJF4J Compiled (µs/op)", "SJF4J (B/op)", "Spring / SJF4J")),
        table_row(("---", "---", "---:", "---:", "---:", "---:", "---:", "---:")),
    ])
    for group in ("pojo", "record", "map"):
        for mode in ("list", "row"):
            for rows in SIZES:
                spring_time, spring_bytes, _ = found[(group, mode, "spring", rows)]
                compiled_time, compiled_bytes, _ = found[(group, mode, "compiled", rows)]
                lines.append(table_row((
                    group.upper(), mode.capitalize(), f"{int(rows):,}",
                    timing(spring_time), allocated(spring_bytes),
                    timing(compiled_time), allocated(compiled_bytes),
                    ratio(spring_time, compiled_time),
                )))

    lines.extend([
        "",
        "## MyBatis",
        "",
        "All MyBatis cases materialize Lists. Cursor is not a direct RowMapper API;",
        "MyBatis Record and direct Row paths are not covered in suite 1.",
        "",
        table_row(("Shape", "Mapping", "Rows", "MyBatis (µs/op)", "MyBatis (B/op)",
                   "SJF4J Compiled (µs/op)", "SJF4J (B/op)", "MyBatis / SJF4J")),
        table_row(("---", "---", "---:", "---:", "---:", "---:", "---:", "---:")),
    ])
    for group in ("pojo", "map"):
        for rows in SIZES:
            compiled_time, compiled_bytes, _ = found[(group, "list", "compiled", rows)]
            for implementation in GROUPS[group]["list"]:
                if not implementation.startswith("mybatis"):
                    continue
                other_time, other_bytes, _ = found[(group, "list", implementation, rows)]
                implementation_name = LABELS[implementation].replace("MyBatis ", "")
                lines.append(table_row((
                    group.upper(), implementation_name + " / List", f"{int(rows):,}",
                    timing(other_time), allocated(other_bytes),
                    timing(compiled_time), allocated(compiled_bytes),
                    ratio(other_time, compiled_time),
                )))

    lines.extend(["", "## Fork stability", ""])
    unstable = [
        (group, mode, implementation, rows, fork_means)
        for (group, mode, implementation, rows), (_, _, fork_means) in found.items()
        if max(fork_means) / min(fork_means) > 1.15
    ]
    if unstable:
        lines.append("Latency cases with slowest/fastest fork divergence above 15%:")
        lines.append("")
        for group, mode, implementation, rows, fork_means in sorted(unstable):
            lines.append(
                f"- {group}/{mode}/{implementation}/{rows}: "
                f"{max(fork_means) / min(fork_means):.2f}×; fork means "
                + ", ".join(timing(value) for value in fork_means) + " µs"
            )
    else:
        lines.append("No latency case exceeded the 15% slowest/fastest fork-spread threshold.")

    lines.extend((
        "",
        "## Methodology",
        "",
        "- Each case independently discards the slowest of three fork means, then averages the other two.",
        "- Allocation uses the untrimmed JMH mean, not the latency-selected forks.",
        "- All methods produce equivalent full Lists, including Row mode's explicit cursor loop.",
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

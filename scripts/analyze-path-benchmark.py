#!/usr/bin/env python3
"""Summarize Jayway vs SJF4J Path Full JMH results (Best 2 Mean per exact case)."""

import argparse
import json
import math
from pathlib import Path
from statistics import mean

SHAPES = {
    "map": ("jayway", "runtime", "compiled"),
    "jsonnode": ("jayway", "runtime"),
    "pojo": ("runtime", "compiled"),
}
QUERIES = ("direct", "wildcard", "filter", "descendant", "slice")
SIZES = ("1", "10", "100")
PARSE_EXPRESSIONS = (
    "$.store.book[1].price", "$.store.bicycle.color",
    "$.store.book[*].author", "$..price",
    "$.store.book[?(@.price > 10)].title",
    "$.store.book[0,2].title", "$.store.book[1:4].title",
)


def load(path: Path, shape: str):
    rows = json.loads(path.read_text(encoding="utf-8"))
    results = {}
    for row in rows:
        name = row["benchmark"]
        prefix = ("org.sjf4j.testbench.path.JsonPathBenchmark.parse_"
                  if shape == "parse" else
                  "org.sjf4j.testbench.path.PathFullBenchmark." + shape + "_")
        if not name.startswith(prefix):
            raise ValueError(f"Unexpected {shape} benchmark: {name}")
        if shape == "parse":
            impl = name.removeprefix(prefix)
            query = row["params"]["expr"]
            key = (query, impl)
            if impl not in ("sjf4j", "jayway") or query not in PARSE_EXPRESSIONS:
                raise ValueError(f"Unexpected parse case: {name} / {query}")
        else:
            suffix = name.removeprefix(prefix)
            parts = suffix.rsplit("_", 1)
            if len(parts) != 2:
                raise ValueError(f"Bad benchmark name: {name}")
            query, impl = parts
            size = row["params"]["sizeKb"]
            key = (size, query, impl)
            if size not in SIZES or query not in QUERIES or impl not in SHAPES[shape]:
                raise ValueError(f"Unexpected case: {key}")
        if key in results:
            raise ValueError(f"Duplicate case: {shape} / {key}")
        metric = row["primaryMetric"]
        forks = metric.get("rawData", [])
        if (metric["scoreUnit"] != "ns/op"
                or not math.isfinite(metric["score"]) or metric["score"] <= 0
                or len(forks) != 3 or any(len(fork) != 5 for fork in forks)
                or any(not math.isfinite(value) or value <= 0 for fork in forks for value in fork)):
            raise ValueError(f"Invalid Full JMH samples: {name}")
        results[key] = row
    if shape == "parse":
        expected = {(query, impl) for query in PARSE_EXPRESSIONS
                    for impl in ("sjf4j", "jayway")}
    else:
        expected = {(size, query, impl) for size in SIZES
                    for query in QUERIES for impl in SHAPES[shape]}
    if set(results) != expected:
        raise ValueError(f"{shape}: missing {expected - set(results)}, extra {set(results) - expected}")
    return results


def fork_means(row):
    return [mean(fork) for fork in row["primaryMetric"]["rawData"]]


def best2(row):
    return mean(sorted(fork_means(row))[:2])


def allocation(row):
    metric = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm")
    if metric is None or not math.isfinite(metric["score"]):
        return "n/a"
    return f'{metric["score"]:.1f}'


def describe(row, baseline=None):
    value = best2(row)
    if baseline is None:
        return f"{value:.1f}"
    factor = best2(baseline) / value
    return f"**{value:.1f}** (**{factor:.2f}×** vs {baseline_label(baseline)})"


def baseline_label(row):
    return row["benchmark"].rsplit("_", 1)[-1].capitalize()


def render(out, results):
    lines = [
        "# JSONPath Full Benchmark — JDK 25",
        "",
        "Jayway 3.0.0 vs SJF4J Runtime and CompiledNavigator, on prebuilt documents.",
        "3 independent forks, 5 × 1s warmup + 5 × 1s measurement, 1 thread.",
        "Numbers below are **Best 2 Mean** in ns/op (lower is better): exclude each",
        "case's own slowest fork, then average the other two independent fork means.",
        "The original 3-fork JMH scores and B/op are preserved separately.",
        "Filter and descendant CompiledNavigator methods use runtime fallback.",
        "**Shared GitHub-hosted runners are exploratory, not a controlled release baseline.**",
        "",
        "## Query results — Best 2 Mean (ns/op)",
        "",
        "| Input | Size | Query | Jayway | Runtime | Compiled |",
        "| --- | ---: | --- | ---: | ---: | ---: |",
    ]
    for shape, implementations in SHAPES.items():
        data = results[shape]
        for size in SIZES:
            for query in QUERIES:
                rows = {impl: data[(size, query, impl)] for impl in implementations}
                jayway = rows.get("jayway")
                runtime = rows["runtime"]
                compiled = rows.get("compiled")
                jayway_text = f"{best2(jayway):.1f}" if jayway else "—"
                runtime_text = describe(runtime, jayway) if jayway else describe(runtime)
                compiled_text = describe(compiled, runtime) if compiled else "—"
                lines.append(f"| {shape} | {size}KB | {query} | "
                             f"{jayway_text} | {runtime_text} | {compiled_text} |")
    lines += [
        "",
        "## Expression parsing — Best 2 Mean (ns/op)",
        "",
        "This group measures expression compilation only; no JSON document is parsed or queried.",
        "",
        "| Expression | Jayway | SJF4J |",
        "| --- | ---: | ---: |",
    ]
    parsed = results["parse"]
    for expr in PARSE_EXPRESSIONS:
        jayway = parsed[(expr, "jayway")]
        sjf4j = parsed[(expr, "sjf4j")]
        lines.append(f"| \`{expr}\` | {best2(jayway):.1f} | "
                     f"{describe(sjf4j, jayway)} |")
    lines += [
        "",
        "## Full measurements",
        "",
        "JMH 3-fork score and error are **untrimmed**; error does not apply to Best 2 Mean.",
        "Allocation B/op is JMH's original untrimmed metric, not Best 2 Mean.",
        "",
        "| Input | Case | Best 2 (ns/op) | JMH mean (ns/op) | JMH error (±) | B/op | Fork means (ns/op) |",
        "| --- | --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for shape in (*SHAPES, "parse"):
        data = results[shape]
        keys = ([(size, query, impl) for size in SIZES for query in QUERIES
                 for impl in SHAPES[shape]] if shape != "parse" else
                [(expr, impl) for expr in PARSE_EXPRESSIONS for impl in ("jayway", "sjf4j")])
        for key in keys:
            row = data[key]
            metric = row["primaryMetric"]
            label = "/".join(key)
            fork_text = " / ".join(f"{value:.1f}" for value in fork_means(row))
            lines.append(f'| {shape} | \`{label}\` | {best2(row):.1f} | '
                         f'{metric["score"]:.1f} | {metric["scoreError"]:.1f} | '
                         f'{allocation(row)} | {fork_text} |')
    lines += [
        "",
        "## Interpretation",
        "",
        "- Map and JsonNode Jayway/Runtime comparisons share the **same pre-parsed input**.",
        "- POJO comparisons are SJF4J Runtime vs Compiled; do not imply Jayway POJO performance.",
        "- Compiled filter/descendant use runtime fallback, not fully generated traversal.",
        "- Each result is a separate method/size/input tuple. Best 2 selection is independent.",
        "- Do not use the original JMH score error as a confidence interval for Best 2 Mean.",
        "- The serialized payload sizes are approximate; see benchmark setup log for actual bytes.",
        "- Parse performance is a separate workload; no serialization/deserialization is timed in queries.",
        "",
    ]
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"Generated {out}: 119 verified cases")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for shape in (*SHAPES, "parse"):
        parser.add_argument(shape, type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = {shape: load(getattr(args, shape), shape)
              for shape in (*SHAPES, "parse")}
    render(args.output, result)


if __name__ == "__main__":
    main()

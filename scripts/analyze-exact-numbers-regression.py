"""Temporary report for paired default and exact-mode Binding JMH runs."""
import json
import math
import sys
from pathlib import Path
from statistics import mean

backend, directory = sys.argv[1:]
out = Path(directory)
modes = ["runtime", "compiled"]
if backend not in ("jsonp", "simple"):
    modes.append("native")
if backend in ("jackson2", "jackson3"):
    modes.append("blackbird")


def read(name, keys, expected):
    result = {}
    for row in json.loads((out / (name + ".json")).read_text()):
        p, m = row["params"], row["primaryMetric"]
        key = tuple(p[k] for k in keys)
        raw = m["rawData"]
        if key in result or p["backend"] != backend or m["scoreUnit"] != "us/op":
            raise ValueError(f"Invalid or duplicate row {name}: {key}")
        if len(raw) != 3 or any(len(f) != 5 for f in raw):
            raise ValueError(f"Incomplete forks {name}: {key}")
        best2 = mean(sorted(mean(f) for f in raw)[:2])
        alloc = row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
        if not math.isfinite(best2) or best2 <= 0 or not math.isfinite(alloc) or alloc < 0:
            raise ValueError(f"Invalid score {name}: {key}")
        result[key] = best2, alloc
    if set(result) != expected:
        raise ValueError(f"Missing or unexpected cases {name}: {set(result) ^ expected}")
    return result


def change(a, b):
    return f"{100 * (b / a - 1):+.2f}%"


sizes = ("10", "100")
seeds = ("12345", "324")
print(f"## {backend} — Default Binding A/B")
print("Latency: Best 2 Mean of 3 forks, µs/op. Allocation: JMH B/op. Same runner.")
print()
print("| Workload | Implementation | KB | Seed | A µs/op | B µs/op | Δ | A B/op | B B/op |")
print("|---|---|---:|---:|---:|---:|---:|---:|---:|")
relative = []
for group in ("pojo-read", "map-read", "pojo-write"):
    implementations = modes[:2] if group == "map-read" else modes
    expected = {(i, s, d) for i in implementations for s in sizes for d in seeds}
    a = read("baseline-" + group, ("implementation", "sizeKb", "seed"), expected)
    b = read("current-" + group, ("implementation", "sizeKb", "seed"), expected)
    for key in sorted(expected):
        i, s, d = key
        x, ax = a[key]
        y, bx = b[key]
        print(f"| {group} | {i} | {s} | {d} | {x:.3f} | {y:.3f} | {change(x,y)} | {ax:.1f} | {bx:.1f} |")
    if group != "map-read":
        for s in sizes:
            for d in seeds:
                for comparator in ("native", "blackbird"):
                    if comparator in implementations:
                        ra = a[comparator,s,d][0] / a["compiled",s,d][0]
                        rb = b[comparator,s,d][0] / b["compiled",s,d][0]
                        relative.append(f"| {group} | {s} | {d} | {comparator.title()} / Compiled | {ra:.3f} | {rb:.3f} |")
print()
print("| Workload | KB | Seed | Ratio | A | B |")
print("|---|---:|---:|---|---:|---:|")
print("\n".join(relative))
print()
print(f"## {backend} — exactNumbers false vs true (current)")
print("| Shape | Implementation | KB | Seed | false µs/op | true µs/op | Δ | false B/op | true B/op |")
print("|---|---|---:|---:|---:|---:|---:|---:|---:|")
expected = {(i, s, d, shape, e) for i in modes[:2] for s in sizes for d in seeds
            for shape in ("pojo", "map") for e in ("false", "true")}
data = read("exact", ("implementation", "sizeKb", "seed", "shape", "exactNumbers"), expected)
for i, s, d, shape, _ in sorted(k for k in expected if k[-1] == "false"):
    x, ax = data[i,s,d,shape,"false"]
    y, bx = data[i,s,d,shape,"true"]
    print(f"| {shape} | {i} | {s} | {d} | {x:.3f} | {y:.3f} | {change(x,y)} | {ax:.1f} | {bx:.1f} |")

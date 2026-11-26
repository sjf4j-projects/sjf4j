"""Retest four outliers using original jars, interleaved JVM forks on each runner."""
import json
import math
import statistics as stats
import subprocess
import sys
from pathlib import Path

backend, baseline, current, directory = sys.argv[1:]
out = Path(directory)
out.mkdir(parents=True, exist_ok=True)
jars = {"A": baseline, "B": current}
# Test only the anomalous workload, at both original seeds.
cases = {
    "jackson2": ("WriteBenchmark.write", "100", ["compiled", "native", "blackbird"]),
    "simple": ("ReadBenchmark.read", "10", ["compiled"]),
    "jsonp": ("ReadBenchmark.read", "100", ["runtime"]),
    "snake": ("ReadBenchmark.read", "100", ["compiled"]),
}
method, size, implementations = cases[backend]
selector = "org.sjf4j.testbench.binding.fabien.Fabien" + method + "$"
seeds = ("12345", "324")
fork_count = 8
data = {}

# AB/BA pairs balance measurement order. Each invocation starts one fresh
# fork per selected case, avoiding 8 consecutive forks of one revision.
for pair in range(fork_count):
    order = ("A", "B") if pair % 2 == 0 else ("B", "A")
    for revision in order:
        label = f"{revision}-{pair+1:02d}"
        result = out / (label + ".json")
        command = [
            "java", "-jar", jars[revision], selector,
            "-p", "backend=" + backend,
            "-p", "implementation=" + ",".join(implementations),
            "-p", "model=users", "-p", "shape=pojo",
            "-p", "sizeKb=" + size, "-p", "seed=" + ",".join(seeds),
            "-t", "1", "-f", "1", "-wi", "5", "-i", "5",
            "-w", "1s", "-r", "1s", "-prof", "gc", "-foe", "true",
            "-rf", "json", "-rff", str(result),
        ]
        print(f"=== {backend} {label} ===", flush=True)
        with (out / (label + ".log")).open("w") as log:
            process = subprocess.Popen(command, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, bufsize=1)
            for line in process.stdout:
                sys.stdout.write(line)
                log.write(line)
            if process.wait():
                raise SystemExit(f"JMH failed: {label}")
        rows = json.loads(result.read_text())
        expected = {(impl, seed) for impl in implementations for seed in seeds}
        observed = set()
        for row in rows:
            p, metric = row["params"], row["primaryMetric"]
            key = p["implementation"], p["seed"]
            if key not in expected or key in observed or metric["scoreUnit"] != "us/op":
                raise ValueError(f"Invalid row: {label} {key}")
            observed.add(key)
            raw = metric["rawData"]
            alloc = row["secondaryMetrics"]["gc.alloc.rate.norm"]["score"]
            if len(raw) != 1 or len(raw[0]) != 5 or not all(math.isfinite(v) and v > 0 for v in raw[0]):
                raise ValueError(f"Invalid fork: {label} {key}")
            if not math.isfinite(alloc) or alloc < 0:
                raise ValueError(f"Invalid allocation: {label} {key}")
            data.setdefault((revision, *key), []).append((stats.mean(raw[0]), alloc))
        if observed != expected:
            raise ValueError(f"Missing rows {label}: {expected-observed}")

def lat(rev, impl, seed):
    values = data[rev, impl, seed]
    if len(values) != fork_count:
        raise ValueError("Incomplete forks")
    return [v[0] for v in values]

def delta(a, b):
    return f"{100*(b/a-1):+.2f}%"

def best(rev, impl, seed):
    return stats.mean(sorted(lat(rev, impl, seed))[:6])

lines = [
    f"## {backend}: {method}, Users POJO {size}KB",
    "A = before PR439; B = after PR452. Original JMH jars; eight independent forks per revision/case.",
    "Median is primary; all-fork mean and Best 6 Mean (drop two slowest) are shown as cross-checks.",
    "",
    "| Implementation | Seed | A median | B median | Delta | A mean | B mean | Delta | A best6 | B best6 | Delta | A B/op | B B/op |",
    "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
]
for impl in implementations:
    for seed in seeds:
        a, b = lat("A", impl, seed), lat("B", impl, seed)
        am, bm = stats.median(a), stats.median(b)
        aa, ba = stats.mean(a), stats.mean(b)
        ab, bb = best("A", impl, seed), best("B", impl, seed)
        ax = stats.mean(v[1] for v in data["A", impl, seed])
        bx = stats.mean(v[1] for v in data["B", impl, seed])
        lines.append(f"| {impl} | {seed} | {am:.3f} | {bm:.3f} | {delta(am,bm)} | {aa:.3f} | {ba:.3f} | {delta(aa,ba)} | {ab:.3f} | {bb:.3f} | {delta(ab,bb)} | {ax:.1f} | {bx:.1f} |")

lines.extend(["", "### Individual fork means (us/op; execution order within revision)",
              "| Implementation | Seed | A | B |", "|---|---:|---|---|"])
for impl in implementations:
    for seed in seeds:
        fmt = lambda values: " / ".join(f"{v:.2f}" for v in values)
        lines.append(f"| {impl} | {seed} | {fmt(lat('A',impl,seed))} | {fmt(lat('B',impl,seed))} |")
if backend == "jackson2":
    lines.extend(["", "### Native / Compiled and Blackbird / Compiled (median)",
                  "| Seed | Ratio | A | B |", "|---:|---|---:|---:|"])
    for seed in seeds:
        for impl in ("native", "blackbird"):
            a = stats.median(lat("A",impl,seed))/stats.median(lat("A","compiled",seed))
            b = stats.median(lat("B",impl,seed))/stats.median(lat("B","compiled",seed))
            lines.append(f"| {seed} | {impl.title()} / Compiled | {a:.3f} | {b:.3f} |")
summary = "\n".join(lines) + "\n"
(out/"summary.md").write_text(summary)
print(summary)

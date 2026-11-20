#!/usr/bin/env bash
set -euo pipefail

# Isolated Native Image A/B: Jackson 3.2.3 vs 3.3.0, legacy vs native cursor.
# Run on ONE machine (GraalVM Community 25.5); this does not change main.
# Usage: GRAALVM_HOME=/path/to/graalvm ./scripts/experiment-jackson3-native.sh [full|quick] [output-dir]
PROFILE=${1:-full}
OUT=${2:-build/performance/binding/jackson3-native-ab}
if [[ "$PROFILE" != full && "$PROFILE" != quick ]] || [[ $# -gt 2 ]]; then
  echo "Usage: $0 [full|quick] [output-dir]" >&2
  exit 2
fi
: "${GRAALVM_HOME:?Set GRAALVM_HOME to GraalVM Community 25.5}"
cd "$(dirname "$0")/.."
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
IMAGE=sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native

if [[ "$PROFILE" == full ]]; then
  PROCESSES=3
  TIMING=(-wi 5 -i 5 -w 1s -r 1s)
else
  PROCESSES=1
  TIMING=(-wi 2 -i 2 -w 200ms -r 200ms)
fi

{
  echo "Git SHA: $(git rev-parse HEAD)"
  echo "GraalVM: $("$GRAALVM_HOME/bin/native-image" --version)"
  echo "Machine: $(uname -a)"
  echo "Profile: $PROFILE"
} > "$OUT/environment.txt"

for version in 3.2.3 3.3.0; do
  for cursor in legacy native; do
    label="$version-$cursor"
    mkdir -p "$OUT/$label"
    echo "=== Build Jackson $version / $cursor cursor ==="
    GRAALVM_HOME="$GRAALVM_HOME" JAVA_HOME="$GRAALVM_HOME" \
      ./gradlew --rerun-tasks :sjf4j-testbench:nativeCompile \
      -PbindingNativeImage=true \
      '-PnativeBenchmark=org.sjf4j.testbench.binding.fabien.FabienReadBenchmark.read' \
      "-PbindingJackson3Version=$version" "-PbindingJackson3Cursor=$cursor" \
      > "$OUT/$label/build.log" 2>&1 || {
        tail -n 100 "$OUT/$label/build.log" >&2
        exit 1
      }
    test -x "$IMAGE"
    echo "=== Measure Jackson $version / $cursor cursor ==="
    for ((run=1; run<=PROCESSES; run++)); do
      "$IMAGE" 'org.sjf4j.testbench.binding.fabien.FabienReadBenchmark.read' \
        -f 0 -t 1 -rf json -rff "$OUT/$label/run$run.json" \
        -p backend=jackson3 -p model=users,clients -p shape=pojo \
        -p implementation=native,runtime,compiled -p sizeKb=1,10,100 \
        -p seed=12345 "${TIMING[@]}"
    done
  done
done

python3 - "$OUT" "$PROCESSES" <<'PY'
import csv
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

root = Path(sys.argv[1])
processes = int(sys.argv[2])
versions = ("3.2.3", "3.3.0")
cursors = ("legacy", "native")
implementations = ("native", "runtime", "compiled")
data = {}
for version in versions:
    for cursor in cursors:
        label = f"{version}-{cursor}"
        values = defaultdict(list)
        for path in sorted((root / label).glob("run*.json")):
            for row in json.loads(path.read_text()):
                p = row["params"]
                key = (p["model"], p["sizeKb"], p["implementation"])
                values[key].append(row["primaryMetric"]["score"])
        expected = {(model, size, impl) for model in ("users", "clients")
                    for size in ("1", "10", "100") for impl in implementations}
        if set(values) != expected:
            raise SystemExit(f"{label}: incomplete cases: {expected - set(values)}")
        if any(len(v) != processes for v in values.values()):
            raise SystemExit(f"{label}: expected {processes} processes per case")
        data[label] = {key: statistics.mean(sorted(v)[:2]) for key, v in values.items()}

rows = []
for model in ("clients", "users"):
    for size in ("1", "10", "100"):
        for version in versions:
            for cursor in cursors:
                v = data[f"{version}-{cursor}"]
                native = v[(model, size, "native")]
                runtime = v[(model, size, "runtime")]
                compiled = v[(model, size, "compiled")]
                rows.append([model, size, version, cursor, native, runtime, compiled,
                             native / compiled, runtime / compiled])
with (root / "comparison.csv").open("w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["model", "sizeKb", "jackson3", "cursor", "native_us", "runtime_us",
                "compiled_us", "native_over_compiled", "runtime_over_compiled"])
    w.writerows(rows)

lines = ["# Jackson 3 Native Image A/B", "",
         "Best-two-process means for Full; Quick is exploratory only.", "",
         "| Model | KB | 3.2.3 legacy (µs) | 3.2.3 native (µs) | "
         "3.3.0 legacy (µs) | 3.3.0 native (µs) | "
         "Version effect (legacy) | Version effect (native) | Cursor effect (3.3.0) |",
         "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
for model in ("clients", "users"):
    for size in ("1", "10", "100"):
        a, b, c, d = (data[x][(model, size, "compiled")] for x in
                      ("3.2.3-legacy", "3.2.3-native", "3.3.0-legacy", "3.3.0-native"))
        pct = lambda new, old: f"{(new / old - 1) * 100:+.1f}%"
        lines.append(f"| {model} | {size} | {a:.3f} | {b:.3f} | {c:.3f} | {d:.3f} | "
                     f"{pct(c, a)} | {pct(d, b)} | {pct(d, c)} |")
lines += ["", "## Native / Compiled (higher is better)", "",
          "| Model | KB | 3.2.3 legacy | 3.2.3 native | 3.3.0 legacy | 3.3.0 native |",
          "| --- | ---: | ---: | ---: | ---: | ---: |"]
for model in ("clients", "users"):
    for size in ("1", "10", "100"):
        ratios = []
        for version in versions:
            for cursor in cursors:
                v = data[f"{version}-{cursor}"]
                ratios.append(v[(model, size, "native")] /
                              v[(model, size, "compiled")])
        lines.append(f"| {model} | {size} | " +
                     " | ".join(f"{v:.3f}×" for v in ratios) + " |")
lines += ["", "## Runtime / Compiled (higher is better)", "",
          "| Model | KB | 3.2.3 legacy | 3.2.3 native | 3.3.0 legacy | 3.3.0 native |",
          "| --- | ---: | ---: | ---: | ---: | ---: |"]
for model in ("clients", "users"):
    for size in ("1", "10", "100"):
        ratios = []
        for version in versions:
            for cursor in cursors:
                v = data[f"{version}-{cursor}"]
                ratios.append(v[(model, size, "runtime")] /
                              v[(model, size, "compiled")])
        lines.append(f"| {model} | {size} | " +
                     " | ".join(f"{v:.3f}×" for v in ratios) + " |")
report = "\n".join(lines) + "\n"
(root / "comparison.md").write_text(report)
print(report)
print("Full per-implementation ratios:", root / "comparison.csv")
PY

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: bash scripts/benchmark-mapping-raw.sh <smoke|quick|full> [output-directory]

  smoke  Compile and verify four mapping modes for Map and JsonObject (no baseline).
  quick  Diagnostic JMH: 1 fork, 2 x 200ms warmup and measurement.
  full   JDK 25 JMH: 3 forks, 5 x 1s warmup and measurement.

This script is invoked through bash because GitHub's file API does not
preserve the executable bit for newly created scripts.
MAPPING_RAW_SKIP_BUILD=1 reuses the JMH jar.
MAPPING_EXPECT_CPU_MODEL optionally requires an exact Linux CPU model.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; exit 0; fi
if [[ $# -lt 1 || $# -gt 2 ||
      ( "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ) ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 required, found: $VERSION" >&2
  exit 1
fi

CPU_MODEL="$( (grep -m1 -E '^model name[[:space:]]*:' /proc/cpuinfo 2>/dev/null || true) | cut -d: -f2- | sed 's/^[[:space:]]*//')"
CPU_MODEL="${CPU_MODEL:-unknown}"
if [[ -n "${MAPPING_EXPECT_CPU_MODEL:-}" && "$CPU_MODEL" != "$MAPPING_EXPECT_CPU_MODEL" ]]; then
  echo "CPU mismatch: expected '${MAPPING_EXPECT_CPU_MODEL}', actual '$CPU_MODEL'" >&2
  exit 1
fi

if [[ "${MAPPING_RAW_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew :sjf4j-testbench:jmhJar
fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then echo "JMH jar missing" >&2; exit 1; fi

case "$PROFILE" in
  smoke) ARGS=(-f 0 -wi 0 -i 1 -r 50ms) ;;
  quick) ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms -prof gc) ;;
  full) ARGS=(-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc) ;;
esac

OUT="${2:-build/performance/mapping/raw}"
mkdir -p "$OUT"
RESULT="$OUT/mapping-raw.json"
LOG="$OUT/mapping-raw.log"
META="$OUT/mapping-raw.meta.md"
REPORT="$OUT/mapping-raw-report.md"
rm -f "$RESULT" "$REPORT"

cat > "$META" <<EOF
# Mapping Raw Policy JMH

- Git SHA: $(git rev-parse HEAD)
- Suite: MappingRawPolicyBenchmark v1 (8 cases)
- UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- Java: $VERSION
- CPU model: $CPU_MODEL
- CPU affinity: $(grep Cpus_allowed_list /proc/self/status 2>/dev/null || echo unspecified)
- OS: $(uname -a)
- Profile: $PROFILE
- MapStruct: 1.6.3
- Reporting: Best 2 of 3 independent fork means for Full (slowest excluded)
- Runner: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo shared-GitHub-exploratory; else echo local; fi)
EOF

SELECTOR='^org\.sjf4j\.testbench\.mapping\.MappingRawPolicyBenchmark\.(map|json)_(mapstruct|cast|strict|lenient)$'
"$JAVA_BIN" -jar "$JAR" "$SELECTOR" -t 1 "${ARGS[@]}" \
  -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$REPORT" "$PROFILE" <<'PY'
import json
import math
import sys

path, output, profile = sys.argv[1:]
with open(path, encoding="utf-8") as stream:
    rows = json.load(stream)
prefix = "org.sjf4j.testbench.mapping.MappingRawPolicyBenchmark."
names = {(prefix + shape + "_" + mode) for shape in ("map", "json")
         for mode in ("mapstruct", "cast", "strict", "lenient")}
by_name = {row["benchmark"]: row for row in rows}
if len(rows) != 8 or set(by_name) != names:
    raise SystemExit(f"Expected exactly eight methods, got {list(by_name)}")

for name, row in by_name.items():
    metric = row["primaryMetric"]
    if metric["scoreUnit"] != "ns/op" or not math.isfinite(metric["score"]):
        raise SystemExit(f"Invalid latency for {name}")
    if profile != "smoke":
        forks = 3 if profile == "full" else 1
        iterations = 5 if profile == "full" else 2
        raw = metric.get("rawData", [])
        if len(raw) != forks or any(len(f) != iterations for f in raw):
            raise SystemExit(f"Incomplete JMH fork data for {name}")
        if "gc.alloc.rate.norm" not in (row.get("secondaryMetrics") or {}):
            raise SystemExit(f"Missing B/op for {name}")

if profile != "smoke":
    def score(shape, mode):
        row = by_name[prefix + shape + "_" + mode]
        data = row["primaryMetric"]["rawData"]
        fork_means = sorted(sum(x) / len(x) for x in data)
        return sum(fork_means[:2]) / 2 if profile == "full" else fork_means[0]

    def alloc(shape, mode):
        metric = by_name[prefix + shape + "_" + mode]["secondaryMetrics"]["gc.alloc.rate.norm"]
        return metric["score"]

    lines = [
        "# Mapping Raw Policy Benchmark",
        "",
        f"JDK 25 · {profile} · ns/op (lower is better).",
        "Full reports independent Best 2 Mean; Quick is diagnostic.",
        "",
        "| Input | MapStruct | CAST | STRICT | LENIENT | MapStruct/CAST | CAST B/op | STRICT B/op |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for shape in ("map", "json"):
        values = [score(shape, mode) for mode in ("mapstruct", "cast", "strict", "lenient")]
        lines.append(f"| {shape} | " + " | ".join(f"{v:,.2f}" for v in values)
                     + f" | {values[0] / values[1]:.2f}x"
                     + f" | {alloc(shape, 'cast'):,.0f}"
                     + f" | {alloc(shape, 'strict'):,.0f} |")
    lines += [
        "",
        "All four modes use identical native scalar input and the same output record.",
        "Do not extrapolate lenient cross-type coercion from this input.",
        "Hosted runners are exploratory; compare cross-runs only on matching hardware.",
        "",
    ]
    with open(output, "w", encoding="utf-8") as stream:
        stream.write("\n".join(lines))
    print("\n".join(lines))

print("Mapping raw " + profile + ": 8/8 cases verified")
PY

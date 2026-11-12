#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/benchmark-jdbc-mapping.sh <smoke|quick|full> [pojo|record|map|all] [output-directory]
  smoke  Correctness/setup check (no baseline).
  quick  1 fork, 2 x 200ms warmup/measurement.
  full   JDK 25, 3 forks, 5 x 1s warmup/measurement.
Groups default to all; output under build/performance/jdbc-mapping/<group>/.
JDBC_SKIP_BUILD=1 reuses the JMH jar.
EOF
}
if [[ "$#" == 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then usage; exit 0; fi
if [[ $# -lt 1 || $# -gt 3 ||
  ( "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ) ||
  ( "${2:-all}" != "all" && "${2:-all}" != "pojo" &&
    "${2:-all}" != "record" && "${2:-all}" != "map" ) ]]; then
  usage >&2; exit 2
fi
PROFILE="$1"
GROUP="${2:-all}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then echo "JDK 25 required: $VERSION" >&2; exit 1; fi
CPU_MODEL="$( (grep -m1 -E '^model name[[:space:]]*:' /proc/cpuinfo 2>/dev/null || true) | cut -d: -f2- | sed 's/^[[:space:]]*//')"
if [[ -z "$CPU_MODEL" ]] && command -v lscpu >/dev/null 2>&1; then
  CPU_MODEL="$(lscpu | awk -F: '/^Model name:/ {gsub(/^[[:space:]]+/, "", $2); print $2; exit}')"
fi
CPU_MODEL="${CPU_MODEL:-unknown}"
if [[ -n "${JDBC_EXPECT_CPU_MODEL:-}" && "$CPU_MODEL" != "$JDBC_EXPECT_CPU_MODEL" ]]; then
  echo "CPU mismatch: $CPU_MODEL" >&2; exit 1
fi
if [[ "$GROUP" == "all" ]]; then
  ROOT="${3:-build/performance/jdbc-mapping}"
  if [[ "${JDBC_SKIP_BUILD:-0}" != "1" ]]; then ./gradlew :sjf4j-testbench:jmhJar; fi
  for group in pojo record map; do
    JDBC_SKIP_BUILD=1 bash "$0" "$PROFILE" "$group" "$ROOT/$group"
  done
  echo "JDBC $PROFILE: all 68 cases passed."
  exit 0
fi

if [[ "${JDBC_SKIP_BUILD:-0}" != "1" ]]; then ./gradlew :sjf4j-testbench:jmhJar; fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then echo "JMH jar missing" >&2; exit 1; fi
case "$GROUP" in
  pojo) EXPECTED=28 ;;
  record) EXPECTED=16 ;;
  map) EXPECTED=24 ;;
esac
case "$PROFILE" in
  smoke) ARGS=(-f 0 -wi 0 -i 1 -r 50ms) ;;
  quick) ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms -prof gc) ;;
  full) ARGS=(-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc) ;;
esac
OUT_DIR="${3:-build/performance/jdbc-mapping/$GROUP}"
mkdir -p "$OUT_DIR"
RESULT="$OUT_DIR/jdbc-$GROUP.json"
LOG="$OUT_DIR/jdbc-$GROUP.log"
META="$OUT_DIR/jdbc-$GROUP.meta.md"
rm -f "$RESULT"
cat > "$META" <<EOF
# JDBC Mapping Benchmark

- Source: org.sjf4j.testbench.mapping.JdbcFullBenchmark
- Suite version: 1
- Git SHA: $(git rev-parse HEAD)
- UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- Java: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- CPU model: $CPU_MODEL
- CPU affinity: ${JDBC_CPU_SET:-unrestricted}
- CPU guard: ${JDBC_EXPECT_CPU_MODEL:-none}
- Profile: $PROFILE
- Group: $GROUP
- Expected exact JMH cases: $EXPECTED
- Rows: 1, 10, 1000, 100000; columns: 12
- Mode: AverageTime (us/op), one thread; SQL execution in Invocation setup
- Full: 3 forks, 5 x 1s warmup and 5 x 1s measurement
- Aggregation: independent Best 2 Mean; B/op untrimmed
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)
- Environment: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub-hosted shared runner, exploratory"; else echo local; fi)
EOF
JAVA_CMD=("$JAVA_BIN")
if [[ -n "${JDBC_CPU_SET:-}" ]]; then
  if [[ "$(uname -s)" != Linux ]] || ! command -v taskset >/dev/null 2>&1; then
    echo "JDBC_CPU_SET requires Linux taskset" >&2; exit 1
  fi
  JAVA_CMD=(taskset -c "$JDBC_CPU_SET" "$JAVA_BIN")
elif [[ "$(uname -s)" == Darwin ]] && command -v caffeinate >/dev/null 2>&1; then
  JAVA_CMD=(caffeinate -i "$JAVA_BIN")
fi
SELECTOR='^org\.sjf4j\.testbench\.mapping\.JdbcFullBenchmark\.'"$GROUP"'_(list|row)_'
echo "JDBC $PROFILE/$GROUP: expecting $EXPECTED cases"
"${JAVA_CMD[@]}" -jar "$JAR" "$SELECTOR" -t 1 "${ARGS[@]}" \
  -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$GROUP" "$PROFILE" "$EXPECTED" <<'PY'
import json
import math
import sys

path, group, profile, expected = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
prefix = "org.sjf4j.testbench.mapping.JdbcFullBenchmark."
groups = {
    "pojo": ("list_compiled", "list_spring", "list_mybatisAuto",
             "list_mybatisExplicit", "list_mybatisCursor", "row_compiled", "row_spring"),
    "record": ("list_compiled", "list_spring", "row_compiled", "row_spring"),
    "map": ("list_compiled", "list_spring", "list_mybatisExplicit",
            "list_mybatisCursor", "row_compiled", "row_spring"),
}
required = {(prefix + group + "_" + impl, size)
            for impl in groups[group] for size in ("1", "10", "1000", "100000")}
with open(path, encoding="utf-8") as f:
    results = json.load(f)
keys = [(r["benchmark"], (r.get("params") or {}).get("rows")) for r in results]
if len(results) != expected or len(keys) != len(set(keys)) or set(keys) != required:
    raise SystemExit(f"Expected {expected} cases; missing={required - set(keys)}; extra={set(keys) - required}")
for row in results:
    metric = row["primaryMetric"]
    if metric["scoreUnit"] not in ("us/op", "µs/op") or not math.isfinite(metric["score"]) or metric["score"] <= 0:
        raise SystemExit(f"Invalid timing: {row['benchmark']}")
    if profile != "smoke":
        raw = metric.get("rawData") or []
        forks, iterations = (1, 2) if profile == "quick" else (3, 5)
        if len(raw) != forks or any(len(fork) != iterations for fork in raw):
            raise SystemExit(f"Missing fork/iteration data: {row['benchmark']}")
        if "gc.alloc.rate.norm" not in (row.get("secondaryMetrics") or {}):
            raise SystemExit(f"Missing allocation data: {row['benchmark']}")
print(f"JDBC {profile}/{group}: all {expected} cases passed")
PY
echo "Results and metadata: $OUT_DIR"

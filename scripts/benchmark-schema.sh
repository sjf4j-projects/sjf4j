#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 <smoke|quick|full> [validation|roundtrip|all] [output-directory]

  smoke   Verify all selected Creek cases without forking (not a baseline).
  quick   Short diagnostic run of the selected cases.
  full    JDK 25 Full benchmark: 3 forks, 5 x 1s warmups and 5 x 1s measurements.

Omit the group (or use all) to run validation and roundtrip.
Results default to build/performance/schema/<group>/.
For all, the optional output-directory is the parent of the group folders.
SCHEMA_SKIP_BUILD=1 reuses the existing sjf4j-testbench JMH jar.
EOF
}

if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 || $# -gt 3 ]] ||
    [[ "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ]] ||
    [[ "${2:-all}" != "all" && "${2:-all}" != "validation" &&
       "${2:-all}" != "roundtrip" ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
GROUP="${2:-all}"
OUT_DIR="${3:-build/performance/schema/$GROUP}"
JAVA_BIN=java
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA_BIN="$JAVA_HOME/bin/java"; fi
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 required; found: $VERSION" >&2
  exit 1
fi

# Run both benchmark groups by default, reusing a single prebuilt JMH jar.
# Supplying a group keeps the per-group Actions workflow unchanged.
if [[ "$GROUP" == "all" ]]; then
  OUT_ROOT="${3:-build/performance/schema}"
  if [[ "${SCHEMA_SKIP_BUILD:-0}" != "1" ]]; then
    ./gradlew :sjf4j-testbench:jmhJar
  fi
  for group in validation roundtrip; do
    echo "=== Schema $PROFILE / $group ==="
    SCHEMA_SKIP_BUILD=1 bash "$0" "$PROFILE" "$group" "$OUT_ROOT/$group"
  done
  echo "Schema $PROFILE / all: all 10 JMH cases passed."
  exit 0
fi

if [[ "${SCHEMA_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew :sjf4j-testbench:jmhJar
fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "JMH jar missing in sjf4j-testbench/build/libs" >&2
  exit 1
fi

# Keep the JMH workload tightly scoped to the 10 agreed Creek methods.
# Other methods in CreekSchemaBenchmark remain available for local profiling.
BASE='org.sjf4j.testbench.schema.CreekSchemaBenchmark.'
case "$GROUP" in
  validation)
    METHODS='(pojo_isValid_runtime|pojo_isValid_compiled|map_isValid_runtime|map_isValid_compiled|jsonNode_isValid_runtime|jsonNode_isValid_networknt)'
    EXPECTED=6
    ;;
  roundtrip)
    METHODS='(roundtrip_jackson|roundtrip_compiled|roundtrip_isValid_runtime|roundtrip_isValid_compiled)'
    EXPECTED=4
    ;;
esac

case "$PROFILE" in
  smoke) ARGS=(-f 0 -wi 0 -i 1 -r 50ms) ;;
  quick) ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms -prof gc) ;;
  full)  ARGS=(-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc) ;;
esac

mkdir -p "$OUT_DIR"
RESULT="$OUT_DIR/schema-$GROUP.json"
META="$OUT_DIR/schema-$GROUP.meta.md"
LOG="$OUT_DIR/schema-$GROUP.log"
rm -f "$RESULT"

cat >"$META" <<EOF
# Creek JSON Schema benchmark

- Git SHA: $(git rev-parse HEAD)
- Date (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- JDK: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Profile: $PROFILE
- Group: $GROUP
- Schema: classpath:/schema/creek-2020-12.json (Draft 2020-12)
- Cases expected: $EXPECTED
- Full timing: 3 forks, 5 x 1s warmup, 5 x 1s measurement (other profiles use their own settings)
- Full reporting: Best 2 Mean per method (drop slowest fork, average remaining two); raw JMH metrics are unchanged
- Compiled fallback: enabled for CreekValidator
- Baseline eligible: $(if [[ "$PROFILE" == "full" && "${GITHUB_ACTIONS:-false}" != "true" ]]; then echo yes; else echo no; fi)
- Execution environment: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub Actions (shared hosted runner; exploratory timings)"; else echo "local"; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')
EOF

JAVA_CMD=("$JAVA_BIN")
if [[ "$(uname -s)" == "Darwin" ]] && command -v caffeinate >/dev/null 2>&1; then
  JAVA_CMD=(caffeinate -i "$JAVA_BIN")
fi

echo "Running $PROFILE / $GROUP: $EXPECTED Creek methods"
# JMH uses Matcher.find() for regex selection; anchor to avoid selecting
# pojo_isValid_compiled_strict via the pojo_isValid_compiled prefix.
SELECTOR="$BASE$METHODS"'$'
# pipefail preserves JMH failure while tee keeps the diagnostic log in the artifact.
"${JAVA_CMD[@]}" -jar "$JAR" "$SELECTOR" -t 1 "${ARGS[@]}" \
    -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$EXPECTED" "$PROFILE" "$GROUP" <<'PY'
import json
import math
import sys

path, expected, profile, group = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
methods = {
    "validation": {"pojo_isValid_runtime", "pojo_isValid_compiled",
                   "map_isValid_runtime", "map_isValid_compiled",
                   "jsonNode_isValid_runtime", "jsonNode_isValid_networknt"},
    "roundtrip": {"roundtrip_jackson", "roundtrip_compiled",
                  "roundtrip_isValid_runtime", "roundtrip_isValid_compiled"},
}[group]
with open(path, encoding="utf-8") as source:
    rows = json.load(source)
names = [row["benchmark"] for row in rows]
prefix = "org.sjf4j.testbench.schema.CreekSchemaBenchmark."
if len(rows) != expected or set(names) != {prefix + name for name in methods}:
    raise SystemExit(f"Incomplete or unexpected Creek {profile}/{group} methods: {names}")
for row in rows:
    metric = row["primaryMetric"]
    if metric["scoreUnit"] != "us/op" or not math.isfinite(metric["score"]) or metric["score"] <= 0:
        raise SystemExit(f"Invalid JMH score for {row['benchmark']}")
    if profile == "smoke":
        continue  # -f 0 has no independent fork rawData contract.
    raw = metric.get("rawData", [])
    fork_count = {"quick": 1, "full": 3}[profile]
    iteration_count = {"quick": 2, "full": 5}[profile]
    if len(raw) != fork_count or any(len(fork) != iteration_count for fork in raw):
        raise SystemExit(f"Incomplete {profile} rawData for {row['benchmark']}: "
                         f"expected {fork_count} forks x {iteration_count} iterations")
print(f"Creek {profile}/{group}: all {expected} methods completed.")
PY

echo "Raw JMH JSON, log and metadata: $OUT_DIR"

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 <smoke|quick|full> <validation|roundtrip> [output-directory]

  smoke   Verify all selected Creek cases without forking (not a baseline).
  quick   Short diagnostic run of the selected cases.
  full    JDK 25 Full benchmark: 3 forks, 5 warmups and 5 measurements.

Results default to build/performance/schema/<group>/.
SCHEMA_SKIP_BUILD=1 reuses the existing sjf4j-testbench JMH jar.
EOF
}

if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
if [[ $# -lt 2 || $# -gt 3 ]] ||
    [[ "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ]] ||
    [[ "$2" != "validation" && "$2" != "roundtrip" ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
GROUP="$2"
OUT_DIR="${3:-build/performance/schema/$GROUP}"
JAVA_BIN=java
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA_BIN="$JAVA_HOME/bin/java"; fi
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 required; found: $VERSION" >&2
  exit 1
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
  full)  ARGS=(-f 3 -wi 5 -i 5 -w 400ms -r 400ms -prof gc) ;;
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
# pipefail preserves JMH failure while tee keeps the diagnostic log in the artifact.
"${JAVA_CMD[@]}" -jar "$JAR" "$BASE$METHODS" -t 1 "${ARGS[@]}" \
    -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$EXPECTED" "$PROFILE" <<'PY'
import json
import sys

path, expected, profile = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(path, encoding="utf-8") as source:
    rows = json.load(source)
if len(rows) != expected:
    raise SystemExit(f"Incomplete Creek {profile} benchmark: {len(rows)}/{expected} cases")
names = [row["benchmark"].rsplit(".", 1)[-1] for row in rows]
if len(set(names)) != expected:
    raise SystemExit(f"Duplicate Creek benchmark methods: {names}")
fork_count = {"smoke": 1, "quick": 1, "full": 3}[profile]
for row in rows:
    raw = row["primaryMetric"].get("rawData", [])
    if profile != "smoke" and len(raw) != fork_count:
        raise SystemExit(f"Expected {fork_count} forks for {row['benchmark']}, got {len(raw)}")
print(f"Creek {profile}/{sys.argv[1]}: all {expected} methods completed.")
PY

echo "Raw JMH JSON, log and metadata: $OUT_DIR"

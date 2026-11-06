#!/usr/bin/env bash
set -euo pipefail

# ./scripts/benchmark-binding.sh smoke|quick|full [output-directory]
usage() {
  cat <<EOF
Usage: $0 <smoke|quick|full> [output-directory]

  smoke   Verify every backend at 100KB with both seeds; not a timing baseline.
  quick   Short local checks; a performance diagnostic, not a baseline.
  full    All supported backend/implementation cases on JDK 25.

Results default to build/performance/binding/.
EOF
}

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 2
fi
if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
if [[ $# -gt 2 || ( "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ) ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
OUT_DIR="${2:-build/performance/binding}"

JAVA_BIN=java
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA_BIN="$JAVA_HOME/bin/java"; fi
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 is required for the official binding baseline; found: $VERSION" >&2
  exit 1
fi

./gradlew :sjf4j-testbench:jmhJar
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "JMH jar missing in sjf4j-testbench/build/libs" >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
SHA="$(git rev-parse --short=12 HEAD)"
META="$OUT_DIR/binding-$SHA-$PROFILE-jdk25.meta.md"
cat > "$META" <<EOF
# Binding performance measurement

- Git SHA: $(git rev-parse HEAD)
- Date (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- JDK: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Suite version: 2
- Profile: $PROFILE
- Baseline eligible: $(if [[ "$PROFILE" == "full" ]]; then echo yes; else echo no; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')

## JMH result files

EOF

# Save one JMH JSON file per backend. Each file contains every supported
# implementation for that backend (Blackbird only for Jackson2/3).
# Native JSON-P (JsonValue) and Simple are not comparable POJO/Map binders.
COMMON=(
  'org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'
  -p model=users,clients
  -rf json -t 1
)

# Quick and Full have identical workloads, differing only in JMH timing.
# Smoke exercises the 100KB edge cases at both seeds without expensive forks.
SEED_COUNT=2
case "$PROFILE" in
  smoke)
    SIZE_COUNT=1
    ARGS=(-f 0 -wi 0 -i 1 -r 50ms)
    COMMON+=(-p sizeKb=100 -p seed=12345,324)
    ;;
  quick)
    SIZE_COUNT=3
    ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms)
    COMMON+=(-p sizeKb=1,10,100 -p seed=12345,324 -prof gc)
    ;;
  full)
    SIZE_COUNT=3
    ARGS=(-f 3 -wi 5 -i 5 -w 400ms -r 400ms)
    COMMON+=(-p sizeKb=1,10,100 -p seed=12345,324 -prof gc)
    ;;
esac

# macOS idle sleep can invalidate JMH timings. This inhibits idle system
# sleep while each backend is measured, but cannot prevent MacBook lid sleep.
# Run Full on AC power with the lid open.
JAVA_CMD=("$JAVA_BIN")
if [[ "$(uname -s)" == "Darwin" ]] && command -v caffeinate >/dev/null 2>&1; then
  JAVA_CMD=(caffeinate -i "$JAVA_BIN")
  echo "macOS: caffeinate enabled for each backend (keep the lid open)."
fi

run_backend() {
  local backend="$1" modes="$2"
  local result="$OUT_DIR/binding-$SHA-$PROFILE-jdk25-$backend.json"
  echo "Running $PROFILE / $backend: implementations=$modes"
  "${JAVA_CMD[@]}" -jar "$JAR" "${COMMON[@]}" "${ARGS[@]}" \
      -p "backend=$backend" \
      -p "implementation=$modes" \
      -p "shape=pojo,map" \
      -rff "$result"

  # JMH can report partial results when one case fails. Insist on all requested
  # cases before accepting a per-backend JSON as complete.
  local -a mode_items
  IFS=, read -r -a mode_items <<< "$modes"
  local expected=$(( ${#mode_items[@]} * 2 * 2 * 2 * SIZE_COUNT * SEED_COUNT ))
  local actual
  if [[ ! -s "$result" ]]; then
    echo "Missing or empty JMH result: $result" >&2
    exit 1
  fi
  actual="$(grep -c '"benchmark"[[:space:]]*:' "$result" || true)"
  if [[ "$actual" -ne "$expected" ]]; then
    echo "Incomplete JMH result for $backend: expected $expected entries; got $actual." >&2
    echo "Inspect the preceding JMH output: $result" >&2
    exit 1
  fi

  echo "- $backend: $(basename "$result") (implementations=$modes, results=$actual)" >> "$META"
}

# The backend/implementation matrix is identical for all three profiles.
run_backend jackson2 runtime,compiled,native,blackbird
run_backend jackson3 runtime,compiled,native,blackbird
run_backend fastjson2 runtime,compiled,native
run_backend gson runtime,compiled,native
run_backend jsonp runtime,compiled
run_backend simple runtime,compiled
run_backend snake runtime,compiled,native

echo "Raw JMH JSON files in: $OUT_DIR"
echo "Environment and result index: $META"

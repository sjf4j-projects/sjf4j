#!/usr/bin/env bash
set -euo pipefail

# ./scripts/benchmark-binding.sh smoke|quick|full [output-directory]
usage() {
  cat <<EOF
Usage: $0 <smoke|quick|full> [output-directory]

  smoke   Verify every backend at 100KB with both seeds; not a timing baseline.
  quick   Short local checks; a performance diagnostic, not a baseline.
  full    All supported backend/implementation cases on JDK 25.

Quick/Full results default to build/performance/binding/.
Optional env: BINDING_BACKEND=<backend> selects one backend for quick/full;
              BINDING_SKIP_BUILD=1 reuses a prebuilt JMH jar.
Smoke leaves no result or metadata files.
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
BINDING_BACKEND="${BINDING_BACKEND:-all}"
if [[ "$PROFILE" == "smoke" && "$BINDING_BACKEND" != "all" ]]; then
  echo "BINDING_BACKEND selection is supported only for quick/full." >&2
  exit 2
fi

JAVA_BIN=java
if [[ -n "${JAVA_HOME:-}" ]]; then JAVA_BIN="$JAVA_HOME/bin/java"; fi
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 is required for the official binding baseline; found: $VERSION" >&2
  exit 1
fi

if [[ "${BINDING_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew :sjf4j-testbench:jmhJar
fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "JMH jar missing in sjf4j-testbench/build/libs" >&2
  exit 1
fi

if [[ "$PROFILE" == "smoke" ]]; then
  # JMH's JSON report is needed only to verify that every case completed.
  # Keep it outside the project and remove it even if the run fails.
  SMOKE_TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/sjf4j-binding-smoke.XXXXXX")"
  trap 'rm -rf "$SMOKE_TMP_DIR"' EXIT
else
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
- Baseline eligible: $(if [[ "$PROFILE" == "full" && "${GITHUB_ACTIONS:-false}" != "true" ]]; then echo yes; else echo no; fi)
- Execution environment: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub Actions (shared hosted runner; exploratory timings)"; else echo "local"; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')

## JMH result files

EOF
fi

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

# A single source of truth for backend order and supported implementations.
BACKENDS=(jackson2 jackson3 fastjson2 gson jsonp simple snake)
BACKEND_LABELS=(Jackson2 Jackson3 Fastjson2 Gson JSONP Simple Snake)
MODE_SETS=(
  runtime,compiled,native,blackbird
  runtime,compiled,native,blackbird
  runtime,compiled,native
  runtime,compiled,native
  runtime,compiled
  runtime,compiled
  runtime,compiled,native
)

# Actions runs one backend per job. The default local run still covers all.
if [[ "$BINDING_BACKEND" != "all" ]]; then
  valid_backend=0
  for backend in "${BACKENDS[@]}"; do
    if [[ "$backend" == "$BINDING_BACKEND" ]]; then valid_backend=1; break; fi
  done
  if [[ "$valid_backend" -eq 0 ]]; then
    echo "Unsupported BINDING_BACKEND: $BINDING_BACKEND" >&2
    exit 2
  fi
fi

# Smoke reports each completed backend and marks any remaining backends NOT RUN.
# Derive totals from the same matrix that drives JMH, not a second hardcoded list.
SMOKE_RESULTS=()
SMOKE_PASSED=0
SMOKE_EXPECTED=0
if [[ "$PROFILE" == "smoke" ]]; then
  for modes in "${MODE_SETS[@]}"; do
    IFS=, read -r -a mode_items <<< "$modes"
    SMOKE_EXPECTED=$(( SMOKE_EXPECTED + ${#mode_items[@]} * 2 * 2 * 2 * SIZE_COUNT * SEED_COUNT ))
  done
fi

print_smoke_summary() {
  local i status actual expected
  printf '\nBinding Smoke Summary\n'
  for (( i=0; i<${#BACKENDS[@]}; i++ )); do
    if (( i < ${#SMOKE_RESULTS[@]} )); then
      IFS=: read -r status actual expected <<< "${SMOKE_RESULTS[$i]}"
      printf '%-11s %-8s %s/%s\n' "${BACKEND_LABELS[$i]}" "$status" "$actual" "$expected"
    else
      printf '%-11s NOT RUN\n' "${BACKEND_LABELS[$i]}"
    fi
  done
  printf '\nTOTAL: %s/%s PASSED\n' "$SMOKE_PASSED" "$SMOKE_EXPECTED"
  if [[ "$SMOKE_PASSED" -eq "$SMOKE_EXPECTED" ]]; then
    echo "ALL PASSED"
  else
    echo "SMOKE FAILED"
  fi
}

run_backend() {
  local backend="$1" modes="$2"
  local result
  if [[ "$PROFILE" == "smoke" ]]; then
    result="$SMOKE_TMP_DIR/$backend.json"
  else
    result="$OUT_DIR/binding-$SHA-$PROFILE-jdk25-$backend.json"
  fi
  local -a mode_items
  IFS=, read -r -a mode_items <<< "$modes"
  local expected=$(( ${#mode_items[@]} * 2 * 2 * 2 * SIZE_COUNT * SEED_COUNT ))
  local actual=0 jmh_status=0

  echo "Running $PROFILE / $backend: implementations=$modes"
  # Never mistake an old file for output from the current JMH invocation.
  rm -f "$result"
  "${JAVA_CMD[@]}" -jar "$JAR" "${COMMON[@]}" "${ARGS[@]}" \
      -p "backend=$backend" \
      -p "implementation=$modes" \
      -p "shape=pojo,map" \
      -rff "$result" || jmh_status=$?

  if [[ -s "$result" ]]; then
    actual="$(grep -c '"benchmark"[[:space:]]*:' "$result" || true)"
  fi
  if [[ "$jmh_status" -ne 0 || "$actual" -ne "$expected" ]]; then
    if [[ "$PROFILE" == "smoke" ]]; then
      SMOKE_RESULTS+=("FAIL:$actual:$expected")
      print_smoke_summary
    fi
    if [[ "$jmh_status" -ne 0 ]]; then
      echo "JMH failed for $backend (exit code $jmh_status)." >&2
    fi
    echo "Incomplete JMH result for $backend: expected $expected entries; got $actual." >&2
    if [[ "$PROFILE" == "smoke" ]]; then
      echo "Inspect the preceding JMH output for the failing case." >&2
    else
      echo "Inspect the preceding JMH output: $result" >&2
    fi
    return 1
  fi

  if [[ "$PROFILE" == "smoke" ]]; then
    rm -f "$result"
    SMOKE_RESULTS+=("PASS:$actual:$expected")
    SMOKE_PASSED=$((SMOKE_PASSED + actual))
  else
    echo "- $backend: $(basename "$result") (implementations=$modes, results=$actual)" >> "$META"
  fi
}

for (( i=0; i<${#BACKENDS[@]}; i++ )); do
  if [[ "$BINDING_BACKEND" != "all" && "$BINDING_BACKEND" != "${BACKENDS[$i]}" ]]; then
    continue
  fi
  run_backend "${BACKENDS[$i]}" "${MODE_SETS[$i]}"
done

if [[ "$PROFILE" == "smoke" ]]; then
  print_smoke_summary
else
  echo "Raw JMH JSON files in: $OUT_DIR"
  echo "Environment and result index: $META"
fi

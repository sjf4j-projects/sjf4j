#!/usr/bin/env bash
set -euo pipefail

# ./scripts/benchmark-binding.sh quick|full [output-directory]
usage() {
  cat <<EOF
Usage: $0 <quick|full> [output-directory]

  quick   Short local checks; a performance diagnostic, not a baseline.
  full    All supported backend/implementation groups on JDK 25.

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
if [[ $# -gt 2 || ( "$1" != "quick" && "$1" != "full" ) ]]; then
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
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')

## JMH result files

EOF

# The implementation axis cannot be a full cross product: Blackbird is
# Jackson-only and native JSON-P/Simple POJO/Map binding does not exist.
# Run each valid group independently, retaining all raw JMH JSON files.
COMMON=(
  'org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'
  -p model=users,clients
  -rf json -prof gc -t 1
)

# Quick and Full use exactly the same workloads and implementations.
# Only JMH warmup / measurement / fork settings differ.
SIZE_COUNT=3
SEED_COUNT=2
if [[ "$PROFILE" == "quick" ]]; then
  ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms)
else
  ARGS=(-f 3 -wi 5 -i 5 -w 400ms -r 400ms)
fi
COMMON+=(-p sizeKb=1,10,100 -p seed=12345,324)

run_group() {
  local group="$1" backends="$2" modes="$3" shapes="$4"
  local result="$OUT_DIR/binding-$SHA-$PROFILE-jdk25-$group.json"
  echo "Running $PROFILE / $group: backends=$backends implementations=$modes shapes=$shapes"
  "$JAVA_BIN" -jar "$JAR" "${COMMON[@]}" "${ARGS[@]}" \
      -p "backend=$backends" \
      -p "implementation=$modes" \
      -p "shape=$shapes" \
      -rff "$result"

  # A JMH run may produce a partial report if some cases fail. Do not silently
  # accept missing scores as a successful Quick run or release measurement.
  local -a backend_items mode_items shape_items
  IFS=, read -r -a backend_items <<< "$backends"
  IFS=, read -r -a mode_items <<< "$modes"
  IFS=, read -r -a shape_items <<< "$shapes"
  local expected=$(( ${#backend_items[@]} * ${#mode_items[@]} * ${#shape_items[@]} * 2 * 2 * SIZE_COUNT * SEED_COUNT ))
  local actual
  if [[ ! -s "$result" ]]; then
    echo "Missing or empty JMH result: $result" >&2
    exit 1
  fi
  actual="$(grep -c '"benchmark"[[:space:]]*:' "$result" || true)"
  if [[ "$actual" -ne "$expected" ]]; then
    echo "Incomplete JMH result for $group: expected $expected benchmark entries; got $actual." >&2
    echo "Inspect the preceding JMH output for setup/benchmark failures: $result" >&2
    exit 1
  fi

  echo "- $group: $(basename "$result") (backend=$backends, implementation=$modes, shape=$shapes)" >> "$META"
}

# Every group runs in both profiles, including all seven backends
# and SnakeYAML's native Map route.
run_group jackson jackson2,jackson3 runtime,compiled,native,blackbird pojo,map
run_group native-json fastjson2,gson runtime,compiled,native pojo,map
run_group jsonp-simple jsonp,simple runtime,compiled pojo,map
run_group yaml snake runtime,compiled pojo,map
run_group yaml-native snake native pojo,map

echo "Raw JMH JSON files in: $OUT_DIR"
echo "Environment and result index: $META"

#!/usr/bin/env bash
set -euo pipefail

# ./scripts/benchmark-binding.sh quick|full [output-directory]
PROFILE="${1:-quick}"
OUT_DIR="${2:-build/performance/binding}"
if [[ "$PROFILE" != "quick" && "$PROFILE" != "full" ]]; then
  echo "Usage: $0 quick|full [output-directory]" >&2
  exit 2
fi

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

if [[ "$PROFILE" == "quick" ]]; then
  ARGS=(-p sizeKb=1,10
        -p seed=12345
        -f 1 -wi 3 -i 3 -w 500ms -r 500ms)
else
  ARGS=(-p sizeKb=1,10,100
        -p seed=12345,324
        -f 3 -wi 5 -i 5 -w 400ms -r 400ms)
fi

run_group() {
  local group="$1" backends="$2" modes="$3" shapes="$4"
  local result="$OUT_DIR/binding-$SHA-$PROFILE-jdk25-$group.json"
  echo "Running $PROFILE / $group: backends=$backends implementations=$modes shapes=$shapes"
  "$JAVA_BIN" -jar "$JAR" "${COMMON[@]}" "${ARGS[@]}" \
      -p "backend=$backends" \
      -p "implementation=$modes" \
      -p "shape=$shapes" \
      -rff "$result"
  echo "- $group: $(basename "$result") (backend=$backends, implementation=$modes, shape=$shapes)" >> "$META"
}

if [[ "$PROFILE" == "quick" ]]; then
  run_group jackson jackson2 runtime,compiled,native,blackbird pojo
  run_group fastjson2 fastjson2 runtime,compiled,native pojo
  run_group yaml snake runtime,compiled pojo
else
  run_group jackson jackson2,jackson3 runtime,compiled,native,blackbird pojo,map
  run_group native-json fastjson2,gson runtime,compiled,native pojo,map
  run_group jsonp-simple jsonp,simple runtime,compiled pojo,map
  run_group yaml snake runtime,compiled pojo,map
  run_group yaml-native snake native map
fi

echo "Raw JMH JSON files in: $OUT_DIR"
echo "Environment and result index: $META"

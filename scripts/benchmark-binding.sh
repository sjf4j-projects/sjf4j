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
RESULT="$OUT_DIR/binding-$SHA-$PROFILE-jdk25.json"
META="$OUT_DIR/binding-$SHA-$PROFILE-jdk25.meta.md"
cat > "$META" <<EOF
# Binding performance measurement

- Git SHA: $(git rev-parse HEAD)
- Date (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- JDK: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Suite version: 1
- Profile: $PROFILE
- JMH result: $(basename "$RESULT")
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')
EOF

COMMON=(
  'org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'
  -p implementation=runtime,compiled
  -p model=users,clients
  -rf json -rff "$RESULT"
  -prof gc -t 1
)

if [[ "$PROFILE" == "quick" ]]; then
  ARGS=(-p backend=jackson2,fastjson2,snake
        -p shape=pojo
        -p sizeKb=1,10
        -p seed=12345
        -f 1 -wi 3 -i 3 -w 500ms -r 500ms)
else
  ARGS=(-p backend=jackson2,jackson3,fastjson2,gson,jsonp,simple,snake
        -p shape=pojo,map
        -p sizeKb=1,10,100
        -p seed=12345,324
        -f 3 -wi 5 -i 5 -w 400ms -r 400ms)
fi

"$JAVA_BIN" -jar "$JAR" "${COMMON[@]}" "${ARGS[@]}"
echo "JMH: $RESULT"
echo "Environment: $META"

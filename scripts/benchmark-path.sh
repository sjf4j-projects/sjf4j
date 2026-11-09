#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: bash $0 <smoke|quick|full> <map|jsonnode|pojo|parse> [output-directory]

  smoke  Run setup/equivalence checks for all 1/10/100KB cases (not a baseline).
  quick  One fork for diagnostic performance.
  full   JDK 25 baseline: 3 forks, 5 x 1s warmup and 5 x 1s measurement.

Results default to build/performance/path/<shape>/.
PATH_SKIP_BUILD=1 reuses the already-built testbench JMH jar.
EOF
}

if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
if [[ $# -lt 2 || $# -gt 3 ]] ||
   [[ "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ]] ||
   [[ "$2" != "map" && "$2" != "jsonnode" && "$2" != "pojo" && "$2" != "parse" ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
SHAPE="$2"
OUT_DIR="${3:-build/performance/path/$SHAPE}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 required; found: $VERSION" >&2
  exit 1
fi

if [[ "${PATH_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew :sjf4j-testbench:jmhJar
fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "JMH jar not found under sjf4j-testbench/build/libs" >&2
  exit 1
fi

case "$SHAPE" in
  map)      IMPLEMENTATIONS='(jayway|runtime|compiled)'; EXPECTED=33 ;;
  jsonnode) IMPLEMENTATIONS='(jayway|runtime)'; EXPECTED=30 ;;
  pojo)     IMPLEMENTATIONS='(runtime|compiled)'; EXPECTED=30 ;;
  parse)    IMPLEMENTATIONS='(sjf4j|jayway)'; EXPECTED=14 ;;
esac
case "$PROFILE" in
  smoke) ARGS=(-f 0 -wi 0 -i 1 -r 50ms) ;;
  quick) ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms -prof gc) ;;
  full)  ARGS=(-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc) ;;
esac

mkdir -p "$OUT_DIR"
RESULT="$OUT_DIR/path-$SHAPE.json"
LOG="$OUT_DIR/path-$SHAPE.log"
META="$OUT_DIR/path-$SHAPE.meta.md"
rm -f "$RESULT"

cat > "$META" <<EOF
# JSONPath benchmark

- Source: org.sjf4j.testbench.path.PathFullBenchmark
- Git SHA: $(git rev-parse HEAD)
- Date (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- Java: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Profile: $PROFILE
- Shape: $SHAPE
- Sizes: 1/10/100 KiB (approximate serialized JSON bytes, generated deterministically)
- Queries: direct, wildcard, filter, descendant, slice
- Expected JMH cases: $EXPECTED
- Query compilation and JSON deserialization: excluded from Query groups; Parse group measures compilation only
- CompiledNavigator: fallback=true (filter/descendant use runtime fallback)
- Full reporting: Best 2 Mean per exact method + size; exclude each case's slowest fork
- Baseline eligible: $(if [[ "$PROFILE" == "full" && "${GITHUB_ACTIONS:-false}" != "true" ]]; then echo yes; else echo no; fi)
- Execution: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub Actions (shared runner, exploratory)"; else echo "local"; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')
EOF

JAVA_CMD=("$JAVA_BIN")
if [[ "$(uname -s)" == "Darwin" ]] && command -v caffeinate >/dev/null 2>&1; then
  JAVA_CMD=(caffeinate -i "$JAVA_BIN")
fi

if [[ "$SHAPE" == "parse" ]]; then
  SELECTOR='^org\.sjf4j\.testbench\.path\.JsonPathBenchmark\.parse_(sjf4j|jayway)$'
else
  SELECTOR='^org\.sjf4j\.testbench\.path\.PathFullBenchmark\.'"$SHAPE"'_(direct|wildcard|filter|descendant|slice)_'"$IMPLEMENTATIONS"'$'
fi
echo "Path $PROFILE/$SHAPE: $EXPECTED JMH cases"
"${JAVA_CMD[@]}" -jar "$JAR" "$SELECTOR" -t 1 "${ARGS[@]}" \
  -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$SHAPE" "$PROFILE" "$EXPECTED" <<'PY'
import json
import math
import sys

path, shape, profile, expected = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
if shape == "parse":
    expressions = (
        "$.store.book[1].price", "$.store.bicycle.color",
        "$.store.book[*].author", "$..price",
        "$.store.book[?(@.price > 10)].title",
        "$.store.book[0,2].title", "$.store.book[1:4].title",
    )
    prefix = "org.sjf4j.testbench.path.JsonPathBenchmark.parse_"
    expected_keys = {(prefix + impl, expr)
                     for impl in ("sjf4j", "jayway") for expr in expressions}
    param = "expr"
else:
    queries = ("direct", "wildcard", "filter", "descendant", "slice")
    implementations = {
        "map": ("jayway", "runtime", "compiled"),
        "jsonnode": ("jayway", "runtime"),
        "pojo": ("runtime", "compiled"),
    }[shape]
    prefix = "org.sjf4j.testbench.path.PathFullBenchmark."
    expected_keys = {(prefix + f"{shape}_{query}_{impl}", size)
                     for query in queries for impl in implementations
                     for size in ("1", "10", "100")
                     if shape != "map" or impl != "compiled" or query == "direct"}
    param = "sizeKb"
with open(path, encoding="utf-8") as fp:
    rows = json.load(fp)
keys = {(row["benchmark"], row["params"][param]) for row in rows}
if len(rows) != expected or keys != expected_keys:
    raise SystemExit(f"Path {profile}/{shape}: expected {expected} cases, "
                     f"got {len(rows)} (missing={expected_keys - keys}, extra={keys - expected_keys})")
for row in rows:
    metric = row["primaryMetric"]
    if metric["scoreUnit"] != "ns/op" or not math.isfinite(metric["score"]) or metric["score"] <= 0:
        raise SystemExit(f"Invalid JMH metric: {row['benchmark']}")
    if profile == "smoke":
        continue
    raw = metric.get("rawData", [])
    forks = 3 if profile == "full" else 1
    iterations = 5 if profile == "full" else 2
    if len(raw) != forks or any(len(samples) != iterations for samples in raw):
        raise SystemExit(f"Missing fork rawData: {row['benchmark']}")
print(f"Path {profile}/{shape}: all {expected} JMH cases passed.")
PY

echo "Results and metadata: $OUT_DIR"

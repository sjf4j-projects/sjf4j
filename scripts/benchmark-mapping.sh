#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: ./scripts/benchmark-mapping.sh <smoke|quick|full> [order|pojo|collection|json|all] [output-directory]

  smoke  Validate all selected mappings without forked performance measurement.
  quick  Diagnostic JMH: 1 fork, 2 x 200ms warmup/measurement.
  full   JDK 25 Full: 3 forks, 5 x 1s warmup/measurement.

Omit the group to run all four groups. Results default to
build/performance/mapping/<group>/. For all, the optional directory is
the parent of the group directories.
MAPPING_SKIP_BUILD=1 reuses a previously built testbench JMH jar.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; exit 0; fi
if [[ $# -lt 1 || $# -gt 3 ||
      ( "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ) ||
      ( "${2:-all}" != "all" && "${2:-all}" != "order" &&
        "${2:-all}" != "pojo" && "${2:-all}" != "collection" &&
        "${2:-all}" != "json" ) ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
GROUP="${2:-all}"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
VERSION="$("$JAVA_BIN" -version 2>&1 | head -n 1)"
if ! [[ "$VERSION" =~ \"25([.\"]|$) ]]; then
  echo "JDK 25 required, found: $VERSION" >&2
  exit 1
fi

# Build once for a default all-group run; each individual group remains callable
# independently (including from a GitHub Actions matrix).
if [[ "$GROUP" == "all" ]]; then
  ROOT="${3:-build/performance/mapping}"
  if [[ "${MAPPING_SKIP_BUILD:-0}" != "1" ]]; then
    ./gradlew :sjf4j-testbench:jmhJar
  fi
  for group in order pojo collection json; do
    echo "=== Mapping $PROFILE / $group ==="
    MAPPING_SKIP_BUILD=1 bash "$0" "$PROFILE" "$group" "$ROOT/$group"
  done
  echo "Mapping $PROFILE / all: all 38 JMH entries passed."
  exit 0
fi

if [[ "${MAPPING_SKIP_BUILD:-0}" != "1" ]]; then
  ./gradlew :sjf4j-testbench:jmhJar
fi
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -type f -name '*-jmh.jar' -print | sort | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "JMH jar missing in sjf4j-testbench/build/libs" >&2
  exit 1
fi

case "$GROUP" in
  order)
    METHODS='order_(classic|extended)'
    EXPECTED=8
    ;;
  pojo)
    METHODS='pojo_(flat|nested|update)'
    EXPECTED=6
    ;;
  collection)
    METHODS='collection_(list|map)'
    EXPECTED=12
    ;;
  json)
    METHODS='json_(map|object)'
    EXPECTED=12
    ;;
esac
case "$PROFILE" in
  smoke) ARGS=(-f 0 -wi 0 -i 1 -r 50ms) ;;
  quick) ARGS=(-f 1 -wi 2 -i 2 -w 200ms -r 200ms -prof gc) ;;
  full) ARGS=(-f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc) ;;
esac

OUT_DIR="${3:-build/performance/mapping/$GROUP}"
mkdir -p "$OUT_DIR"
RESULT="$OUT_DIR/mapping-$GROUP.json"
LOG="$OUT_DIR/mapping-$GROUP.log"
META="$OUT_DIR/mapping-$GROUP.meta.md"
rm -f "$RESULT"

cat > "$META" <<EOF
# Mapping Full Benchmark

- Source: org.sjf4j.testbench.mapping.MappingFullBenchmark
- Suite version: 1
- Git SHA: $(git rev-parse HEAD)
- UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- Java: $VERSION
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Profile: $PROFILE
- Group: $GROUP
- Expected JMH entries: $EXPECTED
- Implementations: MapStruct 1.6.3 and SJF4J CompiledMapper
- Full: 3 forks, 5 x 1s warmup, 5 x 1s measurement, one thread
- Full reporting: Best 2 Mean per exact method and size; exclude slowest fork independently
- Allocation: untrimmed JMH gc.alloc.rate.norm
- Baseline eligible: $(if [[ "$PROFILE" == "full" && "${GITHUB_ACTIONS:-false}" != "true" ]]; then echo yes; else echo no; fi)
- Environment: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub-hosted shared runner (exploratory)"; else echo "local"; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## Java runtime

$("$JAVA_BIN" -version 2>&1 | sed 's/^/    /')
EOF

JAVA_CMD=("$JAVA_BIN")
if [[ "$(uname -s)" == "Darwin" ]] && command -v caffeinate >/dev/null 2>&1; then
  JAVA_CMD=(caffeinate -i "$JAVA_BIN")
fi

# Selector is anchored: JMH supports Matcher.find(), not only full matches.
SELECTOR='^org\.sjf4j\.testbench\.mapping\.MappingFullBenchmark\.'"$METHODS"'_(mapstruct|compiled)$'
echo "Mapping $PROFILE/$GROUP: $EXPECTED expected methods + params"
"${JAVA_CMD[@]}" -jar "$JAR" "$SELECTOR" -t 1 "${ARGS[@]}" \
  -rf json -rff "$RESULT" 2>&1 | tee "$LOG"

python3 - "$RESULT" "$GROUP" "$PROFILE" "$EXPECTED" <<'PY'
import json
import math
import sys

path, group, profile, expected = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
prefix = "org.sjf4j.testbench.mapping.MappingFullBenchmark."
sizes = ("1", "10", "100")
impls = ("mapstruct", "compiled")
groups = {
    "order": (("order_classic", False), ("order_extended", True)),
    "pojo": (("pojo_flat", False), ("pojo_nested", False), ("pojo_update", False)),
    "collection": (("collection_list", True), ("collection_map", True)),
    "json": (("json_map", True), ("json_object", True)),
}
expected_keys = {
    (prefix + case + "_" + impl, size)
    for case, sized in groups[group]
    for impl in impls
    for size in (sizes if sized else (None,))
}
with open(path, encoding="utf-8") as handle:
    rows = json.load(handle)
keys = []
for row in rows:
    params = row.get("params") or {}
    keys.append((row["benchmark"], params.get("sizeKb")))
if len(rows) != expected or len(set(keys)) != expected or set(keys) != expected_keys:
    raise SystemExit(
        f"Mapping {group}: expected {expected} exact cases, got {len(rows)}. "
        f"Missing={expected_keys - set(keys)}, extra={set(keys) - expected_keys}"
    )
for row in rows:
    metric = row["primaryMetric"]
    if (metric["scoreUnit"] != "ns/op" or
            not math.isfinite(metric["score"]) or metric["score"] <= 0):
        raise SystemExit(f"Invalid latency score: {row['benchmark']}")
    if profile != "smoke":
        raw = metric.get("rawData", [])
        forks, iterations = (1, 2) if profile == "quick" else (3, 5)
        if len(raw) != forks or any(len(fork) != iterations for fork in raw):
            raise SystemExit(f"Incomplete forks for {row['benchmark']}: {raw}")
        if "gc.alloc.rate.norm" not in (row.get("secondaryMetrics") or {}):
            raise SystemExit(f"Missing B/op for {row['benchmark']}")
print(f"Mapping {profile}/{group}: all {expected} JMH cases passed.")
PY

echo "Result, log and metadata: $OUT_DIR"

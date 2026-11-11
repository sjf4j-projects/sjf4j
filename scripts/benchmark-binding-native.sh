#!/usr/bin/env bash
set -euo pipefail

# GraalVM Native Image binding benchmark: separate from the HotSpot/JVM runner.
usage() {
  cat <<EOF
Usage: $0 <smoke|quick|full> [output-directory]

  smoke   Check all supported Native Image bindings at 100KB (both seeds).
  quick   Diagnostic timing: one native process per backend.
  full    Baseline timing: three independent native processes per backend.

Default output: build/performance/binding/native/.
Optional env: GRAALVM_HOME=<path> (required, with native-image installed);
              BINDING_NATIVE_BACKEND=jackson2|jackson3|fastjson2|all;
              BINDING_NATIVE_SKIP_BUILD=1 to reuse an existing executable;
              BINDING_NATIVE_GRAALVM_VERSION=25.5.5.0.3 (default).
EOF
}

if [[ $# -eq 1 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
if [[ $# -lt 1 || $# -gt 2 ||
      ( "$1" != "smoke" && "$1" != "quick" && "$1" != "full" ) ]]; then
  usage >&2
  exit 2
fi

PROFILE="$1"
OUT_DIR="${2:-build/performance/binding/native}"
BINDING_NATIVE_BACKEND="${BINDING_NATIVE_BACKEND:-all}"
BINDING_NATIVE_SKIP_BUILD="${BINDING_NATIVE_SKIP_BUILD:-0}"
GRAALVM_EXPECTED_VERSION="${BINDING_NATIVE_GRAALVM_VERSION:-25.5.5.0.3}"

BACKENDS=(jackson2 jackson3 fastjson2)
if [[ "$BINDING_NATIVE_BACKEND" != "all" ]]; then
  valid=0
  for backend in "${BACKENDS[@]}"; do
    if [[ "$backend" == "$BINDING_NATIVE_BACKEND" ]]; then valid=1; break; fi
  done
  if [[ "$valid" -ne 1 ]]; then
    echo "Unsupported BINDING_NATIVE_BACKEND: $BINDING_NATIVE_BACKEND" >&2
    exit 2
  fi
fi

if [[ -z "${GRAALVM_HOME:-}" ]]; then
  echo "GRAALVM_HOME must point to a GraalVM 25 distribution with native-image." >&2
  exit 1
fi
JAVA_BIN="$GRAALVM_HOME/bin/java"
NATIVE_IMAGE_BIN="$GRAALVM_HOME/bin/native-image"
if [[ ! -x "$JAVA_BIN" || ! -x "$NATIVE_IMAGE_BIN" ]]; then
  echo "GRAALVM_HOME must contain executable bin/java and bin/native-image." >&2
  exit 1
fi
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1)"
NATIVE_VERSION="$("$NATIVE_IMAGE_BIN" --version 2>&1)"
if ! [[ "$JAVA_VERSION" =~ \"25([.\"]|$) ]]; then
  echo "GraalVM JDK 25 is required: $JAVA_VERSION" >&2
  exit 1
fi
if [[ "$NATIVE_VERSION" != *"$GRAALVM_EXPECTED_VERSION"* ]]; then
  echo "Expected GraalVM Native Image $GRAALVM_EXPECTED_VERSION; found: $NATIVE_VERSION" >&2
  exit 1
fi

# The tracing agent covers every supported backend/implementation and both
# POJO/Map targets. Compile once; select cases only when running the executable.
if [[ "$BINDING_NATIVE_SKIP_BUILD" != "1" ]]; then
  GRAALVM_HOME="$GRAALVM_HOME" JAVA_HOME="$GRAALVM_HOME" \
    ./gradlew :sjf4j-testbench:nativeCompile \
      -PbindingNativeImage=true \
      '-PnativeBenchmark=org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'
fi
IMAGE="sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native"
if [[ ! -x "$IMAGE" ]]; then
  echo "Native JMH image missing: $IMAGE" >&2
  exit 1
fi

JMH='org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'
COMMON=("$JMH" -f 0 -t 1 -rf json
  -p model=users,clients -p shape=pojo,map
  -p implementation=native,runtime,compiled)
case "$PROFILE" in
  smoke)
    RUNS=1
    SIZE_COUNT=1
    SEED_COUNT=2
    ARGS=(-wi 0 -i 1 -r 50ms -p sizeKb=100 -p seed=12345,324)
    ;;
  quick)
    RUNS=1
    SIZE_COUNT=3
    SEED_COUNT=1
    ARGS=(-wi 2 -i 2 -w 200ms -r 200ms -p sizeKb=1,10,100 -p seed=12345)
    ;;
  full)
    RUNS=3
    SIZE_COUNT=3
    SEED_COUNT=1
    ARGS=(-wi 5 -i 5 -w 1s -r 1s -p sizeKb=1,10,100 -p seed=12345)
    ;;
esac

if [[ "$PROFILE" == "smoke" ]]; then
  TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/sjf4j-binding-native-smoke.XXXXXX")"
  trap 'rm -rf "$TEMP_DIR"' EXIT
else
  mkdir -p "$OUT_DIR"
  SHA="$(git rev-parse --short=12 HEAD)"
  META="$OUT_DIR/binding-$SHA-$PROFILE-graalvm25.meta.md"
  IMAGE_BYTES="$(wc -c < "$IMAGE" | tr -d '[:space:]')"
  cat > "$META" <<EOF
# GraalVM Native Image Binding performance

- Git SHA: $(git rev-parse HEAD)
- Date (UTC): $(date -u +'%Y-%m-%dT%H:%M:%SZ')
- Environment: GraalVM Native Image (not GraalVM JVM)
- Suite version: 3
- Profile: $PROFILE
- JVM: $("$JAVA_BIN" -version 2>&1 | head -n 1)
- Native Image compiler: $NATIVE_VERSION
- Native Image build flags: -O2 (no PGO)
- Executable bytes: $IMAGE_BYTES
- OS: $(uname -s)
- Architecture: $(uname -m)
- Hardware: $(uname -a)
- Threads: 1
- JMH forks: 0 (native executable)
- Independent processes: $RUNS
- Seeds: 12345$(if [[ "$PROFILE" == "smoke" ]]; then echo ",324"; fi)
- GC allocation profiler: not used (not assumed equivalent to HotSpot)
- Baseline eligible: $(if [[ "$PROFILE" == "full" && "${GITHUB_ACTIONS:-false}" != "true" ]]; then echo yes; else echo no; fi)
- Execution environment: $(if [[ "${GITHUB_ACTIONS:-false}" == "true" ]]; then echo "GitHub Actions shared runner (exploratory)"; else echo "local"; fi)
- Working tree clean: $(if [[ -z "$(git status --porcelain)" ]]; then echo yes; else echo no; fi)

## JMH results

EOF
fi

PASSED=0
EXPECTED_TOTAL=0
EXPECTED_PER_BACKEND=$((3 * 2 * 2 * 2 * SIZE_COUNT * SEED_COUNT))
for backend in "${BACKENDS[@]}"; do
  if [[ "$BINDING_NATIVE_BACKEND" != "all" &&
        "$BINDING_NATIVE_BACKEND" != "$backend" ]]; then
    continue
  fi
  EXPECTED_TOTAL=$((EXPECTED_TOTAL + EXPECTED_PER_BACKEND))
  for ((run=1; run<=RUNS; run++)); do
    if [[ "$PROFILE" == "smoke" ]]; then
      RESULT="$TEMP_DIR/$backend-run$run.json"
    else
      RESULT="$OUT_DIR/binding-$SHA-$PROFILE-graalvm25-$backend-run$run.json"
    fi
    rm -f "$RESULT"
    echo "Running Native Image $PROFILE / $backend / process $run of $RUNS"
    status=0
    if [[ "$PROFILE" == "full" && "$(uname -s)" == "Linux" &&
          -x /usr/bin/time ]]; then
      # Process-level peak RSS (kB), not a steady-state heap measurement.
      STATS="${RESULT%.json}.time.txt"
      /usr/bin/time -v -o "$STATS" "$IMAGE" "${COMMON[@]}" \
        "${ARGS[@]}" -p "backend=$backend" -rff "$RESULT" || status=$?
    else
      "$IMAGE" "${COMMON[@]}" "${ARGS[@]}" \
        -p "backend=$backend" -rff "$RESULT" || status=$?
    fi
    actual=0
    if [[ -s "$RESULT" ]]; then
      actual="$(grep -c '"benchmark"[[:space:]]*:' "$RESULT" || true)"
    fi
    if [[ "$status" -ne 0 || "$actual" -ne "$EXPECTED_PER_BACKEND" ]]; then
      echo "Native Image $backend run $run failed: exit=$status; cases=$actual/$EXPECTED_PER_BACKEND" >&2
      exit 1
    fi
    if [[ "$PROFILE" != "smoke" ]]; then
      echo "- $backend / run $run: $(basename "$RESULT") ($actual cases)" >> "$META"
    fi
  done
  PASSED=$((PASSED + EXPECTED_PER_BACKEND))
done

if [[ "$PROFILE" == "smoke" ]]; then
  echo "Native Image Smoke: TOTAL $PASSED/$EXPECTED_TOTAL PASSED"
  echo "ALL PASSED"
else
  echo "Native Image results: $OUT_DIR"
  echo "Metadata: $META"
  if [[ "$PROFILE" == "full" ]]; then
    echo "Use the three independent process files per exact case to compute Best 2 Run Mean."
  fi
fi

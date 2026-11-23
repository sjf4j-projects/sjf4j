#!/usr/bin/env bash
# Fast correctness checks only; these are not performance measurements.
set -euo pipefail

jar=$(find sjf4j-testbench/build/libs -maxdepth 1 -name '*-jmh.jar' -type f -print -quit)
if [[ -z "$jar" ]]; then
  echo "JMH jar not found" >&2
  exit 1
fi

benchmark='org.sjf4j.testbench.binding.fabien.Fabien(ReadBenchmark.read|WriteBenchmark.write)'

run_smoke() {
  local backend="$1" modes="$2" models="$3" shapes="$4"
  local size="$5" seeds="$6" expected="$7" duration="$8"
  local report="/tmp/sjf4j-smoke-$backend-$size.json"
  local log="/tmp/sjf4j-smoke-$backend-$size.log"

  if ! java -jar "$jar" "$benchmark" \
      -p "backend=$backend" -p "implementation=$modes" \
      -p "model=$models" -p "shape=$shapes" -p "sizeKb=$size" -p "seed=$seeds" \
      -f 0 -wi 0 -i 1 -r "$duration" -t 1 -rf json -rff "$report" >"$log" 2>&1; then
    tail -100 "$log"
    exit 1
  fi

  if ! python3 - "$report" "$expected" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as file:
    results = json.load(file)
expected = int(sys.argv[2])
if len(results) != expected:
    raise SystemExit(f"Incomplete JMH smoke: {len(results)} results vs {expected} expected")
PY
  then
    tail -100 "$log"
    exit 1
  fi
  echo "$backend / $size KB: $expected cases passed"
}

# Explicitly cover the native SnakeYAML binding path.
run_smoke snake native users,clients pojo,map 10 12345 8 100ms

# Check the largest fixture for every implementation and backend.
for backend in jackson2 jackson3 fastjson2 gson jsonp simple snake; do
  case "$backend" in
    jackson2|jackson3) modes=runtime,compiled,native,blackbird; count=4 ;;
    fastjson2|gson|snake) modes=runtime,compiled,native; count=3 ;;
    jsonp|simple) modes=runtime,compiled; count=2 ;;
  esac
  # Read/write x POJO/map x two seeds, for one model (Clients).
  run_smoke "$backend" "$modes" clients pojo,map 100 12345,324 "$((count * 8))" 50ms
done

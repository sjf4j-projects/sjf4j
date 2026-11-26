#!/usr/bin/env bash
# Temporary paired JVM experiment, one backend per runner.
set -euo pipefail
backend="$1"
base="$2"
current="$3"
out="$4"
mkdir -p "$out"
case "$backend" in
  jackson2|jackson3) modes=runtime,compiled,native,blackbird ;;
  fastjson2|gson|snake) modes=runtime,compiled,native ;;
  jsonp|simple) modes=runtime,compiled ;;
  *) exit 2 ;;
esac
common=(-p "backend=$backend" -p model=users -p sizeKb=10,100 -p seed=12345,324
        -t 1 -f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc -foe true -rf json)
read='org.sjf4j.testbench.binding.fabien.FabienReadBenchmark.read$'
write='org.sjf4j.testbench.binding.fabien.FabienWriteBenchmark.write$'
run() {
  local label="$1" jar="$2" group="$3" selector="$4" implementations="$5" shape="$6"
  java -jar "$jar" "$selector" "${common[@]}" -p "implementation=$implementations" \
    -p "shape=$shape" -rff "$out/$label-$group.json" 2>&1 | tee "$out/$label-$group.log"
}
# Alternate order across groups, keeping all baseline/current measurements on this runner.
run baseline "$base" pojo-read "$read" "$modes" pojo
run current "$current" pojo-read "$read" "$modes" pojo
run current "$current" map-read "$read" runtime,compiled map
run baseline "$base" map-read "$read" runtime,compiled map
run baseline "$base" pojo-write "$write" "$modes" pojo
run current "$current" pojo-write "$write" "$modes" pojo

exact='org.sjf4j.testbench.binding.fabien.ExactNumbersReadBenchmark.read$'
# No-fork smoke is only a correctness check and never used for performance reporting.
java -jar "$current" "$exact" -p "backend=$backend" -p implementation=runtime,compiled \
  -p exactNumbers=false,true -p shape=pojo,map -p sizeKb=100 -p seed=12345 \
  -t 1 -f 0 -wi 0 -i 1 -r 100ms -foe true 2>&1 | tee "$out/exact-smoke.log"
java -jar "$current" "$exact" -p "backend=$backend" -p implementation=runtime,compiled \
  -p exactNumbers=false,true -p shape=pojo,map -p sizeKb=10,100 -p seed=12345,324 \
  -t 1 -f 3 -wi 5 -i 5 -w 1s -r 1s -prof gc -foe true -rf json \
  -rff "$out/exact.json" 2>&1 | tee "$out/exact.log"
python3 scripts/analyze-exact-numbers-regression.py "$backend" "$out" | tee "$out/summary.md"

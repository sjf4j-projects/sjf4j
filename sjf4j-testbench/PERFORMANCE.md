# Performance benchmarks

Run from the repository root. Judge performance **within the same JMH run**;
historical scores and machine-to-machine comparisons are not required.

## CompiledWriteBenchmark

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*CompiledWriteBenchmark[.]json_.*' \
  -f 2 -prof gc -rf json -rff sjf4j-testbench/build/compiled-write.json
```

For each backend and each `pojo/map` + `string/utf8` combination, compare
`_compiled` with all existing `_runtime`, `_native`, and `_blackbird` methods
in that group. Exclude JSON-P's `_tree_*_native` methods: they serialize a
different input. Average time (`us/op`) is lower-is-better; PASS only when
compiled is strictly faster than every comparable method. Missing expected
results are INCOMPLETE, not PASS.

Generate `sjf4j-testbench/build/performance-report.md` from the JMH JSON:
list every group's measured scores, PASS/FAIL/INCOMPLETE, and compiled's lead
over its fastest competitor: `(fastest competitor / compiled - 1) × 100%`.
Include the commit, JDK, and failed groups. If JMH cannot run, report the
error instead of inventing results.

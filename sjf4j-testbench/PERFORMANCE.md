# Performance benchmarks

Run from the repository root. Judge performance **within the same JMH run**;
historical scores and machine-to-machine comparisons are not required.

## ArrayListTraversalBenchmark (JDK comparison)

Compares `randomOrEnhanced` (`instanceof RandomAccess` + indexed `get(i)`),
`randomFor` (direct indexed `get(i)`) and `enhancedFor` over the **same
`ArrayList<String>`**. `arrayIndexed` and `arrayEnhancedFor` compare the two
loops over a **`String[]` containing the same elements**. The benchmark sums
string lengths and returns the result so the element accesses cannot simply be
discarded; setup checks all results. The per-element work is deliberately
small: this isolates traversal rather than measuring JSON writing or a real
`_writePolyBase` call. `size=0` highlights fixed overhead, while larger sizes
show per-element cost. Results are ns per **whole collection**, not ns per element.

Build once with JDK 17 or later, then run the same jar with each desired JDK
(17+). The JMH forks inherit the `java` executable used to launch the jar;
setting `JAVA_HOME` for Gradle alone does not change which JDK executes JMH.
For example, substitute the paths to your installed JDKs:

```bash
JAVA_HOME=/path/to/jdk17 ./gradlew :sjf4j-testbench:jmhJar

/path/to/jdk17/bin/java -version
/path/to/jdk17/bin/java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*ArrayListTraversalBenchmark[.].*' -f 2 -prof gc \
  -rf json -rff sjf4j-testbench/build/arraylist-jdk17.json

/path/to/jdk21/bin/java -version
/path/to/jdk21/bin/java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*ArrayListTraversalBenchmark[.].*' -f 2 -prof gc \
  -rf json -rff sjf4j-testbench/build/arraylist-jdk21.json
```

Compare list methods with list methods (`randomOrEnhanced`, `randomFor`,
`enhancedFor`), and array methods with array methods (`arrayIndexed`,
`arrayEnhancedFor`), at the **same size and JDK**; compare their ratios across
JDKs on the same machine, with the same JMH/JVM options. `randomOrEnhanced`
includes the `instanceof` and fallback branch from the original snippet; the
input is always an `ArrayList`, so the fallback is not taken. JIT compilation
may inline or eliminate iterator machinery, which is part of the difference
being measured. This does not predict the end-to-end cost when element
serialization dominates the loop.

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

## CreatorReadBenchmark

The target is `CreatorUserRecord` (23 fields, including nested records).
It reuses exactly `CompiledReadBenchmark.JSON_DATA` and its UTF-8 form.
The reversed variants change field order only, not values.

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*CreatorReadBenchmark[.].*record_.*' \
  -f 2 -prof gc -rf json -rff sjf4j-testbench/build/creator-read.json
```

For a targeted Fastjson2 run, replace the filter with
`'.*CreatorReadBenchmark[.]fastjson2_record_(string|utf8)_(compiled|runtime|native)$'`.
Compare only identical record shape, backend, and input (String or UTF-8).
Gson's UTF-8 native baseline uses an `InputStreamReader`; JSON-P and Simple
do not have an equivalent native record binder. The reversed variants have
compiled/runtime pairs; the native baselines apply only to ordered input.

Trial setup verifies all 60 method results against Jackson2's native record
read before measuring anything. The lightweight CI smoke verifies correctness
only; it is **not** a performance measurement. Use the normal JMH warmup and
forks for conclusions.

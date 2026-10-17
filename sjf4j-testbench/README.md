# SJF4J Testbench

## CompiledBinder P1/P2 standalone regression and benchmark

These tests are **independent of the existing CompiledRead/WriteBenchmark**:

- `src/test/java/org/sjf4j/testbench/binding/CompiledObntCodecTest.java`: JUnit correctness across Simple, Gson, Jackson2/3, Fastjson2, and JSON-P. Covers JOJO declared/unknown/aliased/read-only fields, JsonObject/JsonArray, @NodeValue, named and patterned codecs, collection codecs, and JSON null.
- `src/jmh/java/org/sjf4j/testbench/binding/compiled/CompiledObntCodecBenchmark.java`: same six backends, comparing Runtime and Compiled for four workloads (Codec POJO, JOJO, JsonObject, JsonArray) with read/write operations. The JMH trial setup checks correctness before timing.

Run correctness tests first:

```bash
./gradlew :sjf4j-testbench:test --tests '*CompiledObntCodecTest'
```

Build and run a focused Fastjson2/Jackson2 benchmark (from the **project root**):

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*CompiledObntCodecBenchmark.*' \
  -p backend=fastjson2,jackson2 -wi 5 -i 8 -w 400ms -r 400ms -f 2 -prof gc
```

To isolate one codec workload, replace the JMH regex with
`'.*CompiledObntCodecBenchmark.codec_.*'`. To cover all six backends, omit
`-p backend=...`. Both the input JSON and write-model construction are outside
JMH measurements; parser/writer creation is **inside** each operation.
The benchmark selects the backend via a pre-bound method reference, introducing
a small, symmetric adapter overhead. Compare Runtime and Compiled **within the
same backend and workload**, not absolute numbers across different machines.

## JOJO read benchmarks (JVM)

`JojoReadBenchmark` isolates the cost of binding a `JsonObject` subclass versus
a plain POJO (unknown names skipped) and native Jackson/Fastjson2 binding with
an extra-properties map (unknown names retained).

| Workload | Declared fields | Unknown fields | Purpose |
| --- | ---: | ---: | --- |
| `static` | 16 | 0 | Declared-property matching, setters, and JOJO construction overhead |
| `mixed` | 8 | 8 | Alternating declared/dynamic name matching and value reading |
| `dynamic` | 2 | 14 | Dynamic map fallback, including nested object/array values |

The three model types have **identical declared fields**. Each measurement
uses identical prebuilt JSON for all backends; JMH trial setup verifies that
JOJO and native-extra retain the same dynamic keys. The plain POJO intentionally
skips unknown keys, so **only the static workload is semantically equivalent**
to JOJO for that baseline. The native-extra model also allocates its extra map
eagerly, whereas JOJO creates its dynamic map on demand.

Build the JMH jar and run all three workloads:

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*JojoReadBenchmark.*' -prof gc
```

For a focused Fastjson2 comparison:

```bash
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*JojoReadBenchmark.*fastjson2.*' -p workload=mixed -prof gc
```

The read benchmark currently uses one fork by default. Compare both latency (`us/op`)
and allocation (`gc.alloc.rate.norm`, bytes/op); JMH warmup and measurement
exclude JSON fixture creation and validation.

## JOJO write benchmarks (JVM)

`JojoWriteBenchmark` reuses the **exact same JSON fixture** as the read benchmark
and measures String serialization. JMH trial setup constructs the objects and
verifies parsed output equivalence for each backend before recording timings.

| Workload | Declared fields | Dynamic fields |
| --- | ---: | ---: |
| `static` | 16 | 0 |
| `mixed` | 8 | 8 |
| `dynamic` | 2 | 14 |

The runtime JOJO and native-extra implementations retain all fields.
The plain POJO intentionally lacks dynamic fields, so it is a comparable
serialization baseline **only for `static`**. For `mixed` and `dynamic`,
POJO writes fewer fields and must not be interpreted as equivalent output.
The native-extra object allocates its extra map eagerly; JOJO uses lazy dynamic
storage. The benchmark covers Jackson2, Jackson3, Fastjson2, plus Simple JSON
runtime (which has no corresponding native-extra baseline).

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*JojoWriteBenchmark.*' -prof gc
```

To focus on a dynamic-heavy workload:

```bash
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*JojoWriteBenchmark.*' -p workload=dynamic -prof gc
```

Compare both throughput latency (`us/op`) and per-operation allocation
(`gc.alloc.rate.norm`, bytes/op). Warmup/measurement exclude fixture setup,
object construction, and output validation.

## GraalVM Native Image Benchmarking

Native Image benchmarks use JMH. During the build, the target benchmark is first run with the GraalVM tracing agent to generate runtime configuration for reflection, resources, and other features. A native executable is then built from that configuration. Use the same benchmark regular expression for both build and execution.

### Prerequisites

- GraalVM for JDK 17 with `native-image` installed.
- `GRAALVM_HOME` points to that GraalVM and its `bin` directory is on `PATH`.

```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-17
export PATH="$GRAALVM_HOME/bin:$PATH"
```

### Build and run

The following example benchmarks `HandWriteBenchmark`:

```bash
./gradlew :sjf4j-testbench:nativeCompile \
  -PnativeBenchmark='.*HandWriteBenchmark.*'

./sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native \
  '.*HandWriteBenchmark.*'
```

`nativeCompile` runs `jmhAgent` and writes the captured configuration to
`sjf4j-testbench/build/native/agent-output/jmhAgent`. The resulting executable is located at
`sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native`.

To use another benchmark, replace both `HandWriteBenchmark` regular expressions with the target benchmark, for example:

```bash
./gradlew :sjf4j-testbench:nativeCompile \
  -PnativeBenchmark='.*JsonReadBenchmark.*'

./sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native \
  '.*JsonReadBenchmark.*'
```

Standard JMH options can be passed to the native executable, for example `-wi 10 -i 10 -w 300ms -r 300ms`.
Use `-h` to display the full list of options.

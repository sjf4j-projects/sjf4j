# SJF4J Testbench

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

The benchmarks use three forks by default. Compare both latency (`us/op`)
and allocation (`gc.alloc.rate.norm`, bytes/op); JMH warmup and measurement
exclude JSON fixture creation and validation.

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

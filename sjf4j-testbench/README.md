# SJF4J Testbench

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

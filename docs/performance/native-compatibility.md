# Native Image compatibility probes

This is a **correctness / AOT reachability diagnostic**, not a JMH benchmark.
The probes live under `sjf4j-testbench/src/test/java/org/sjf4j/testbench/nativeimage`.
Ordinary `:sjf4j-testbench:test` runs the twelve entry points on the JVM,
but does **not** build any native executable.

## Coverage

| Backend | Direct library (Native) | SJF4J Runtime | SJF4J Compiled |
| --- | --- | --- | --- |
| Jackson2 | Separate image | Separate image | Separate image |
| Jackson3 | Separate image | Separate image | Separate image |
| Fastjson2 | Separate image | Separate image | Separate image |
| Gson | Separate image | Separate image | Separate image |

Each executable performs eight checks: Read and Write × Users/Clients ×
POJO/Map, using the same canonical test fixture files. The host JVM generates
JSON input before building any native image. The Compiled cases use **direct**
construction of the APT-generated `_Impl` class, not the reflective
`CompiledInstances.create()` factory. Verification parses output as a
generic Map; it never reflectively serializes a POJO inside the image.

The twelve images are built with **separate backend classpaths**.
This is important because library-bundled
`META-INF/native-image/**/native-image.properties` files are applied whenever
their JAR appears on the native-image classpath.

## Run locally

With **GraalVM Community 25.5.5.0.3** (JDK 25) and its `native-image`
installed:

```bash
export GRAALVM_HOME=/path/to/graalvm
./gradlew :sjf4j-testbench:nativeCompatibility

# Target one backend, one implementation:
./gradlew :sjf4j-testbench:nativeCompatibility \
  -PnativeCompatibilityBackend=gson \
  -PnativeCompatibilityMode=compiled

# Quick JVM-only correctness validation (twelve independent cases):
./gradlew :sjf4j-testbench:nativeCompatibilityJvmTest
```

The Gradle task is explicit/opt-in and **never runs as a dependency of
ordinary `test` or JMH**. The opt-in
[Native Image Compatibility workflow](../../.github/workflows/native-compatibility.yml)
accepts the same backend and mode choices. To run the full compatibility
matrix before merging a same-repository PR, include
`[run-native-compatibility]` in its description.

## Interpretation

Each selected backend/implementation produces an independent build log,
run log (if built), and optional binary under
`sjf4j-testbench/build/native/compatibility/`. The complete matrix is
`results.json`, with these mutually exclusive statuses:

- `BUILD_FAILED`: Native Image image-heap/class initialization, AOT
  compilation, missing resource, or reachable-code error
- `RUN_FAILED`: the binary was built but its Read/Write check failed
  (including missing reflection registrations)
- `PASS`: image built and all eight Read/Write checks passed

The task **continues across failing combinations**, writes their individual
logs and summary, then returns nonzero if any selected combination failed.
A compatibility failure is a **result**, not a reason to silently skip
an implementation or loosen the official Native Image configuration.

**By default: no `--exclude-config`, no Fastjson2-specific class
initialization overrides, no reflection allowlist, and no tracing agent.**
The only baseline build option is `-O0` (diagnostic compilation, not a performance setting).
The option `-PnativeCompatibilityReflectionConfig=path/to/reflect-config.json`
can be used in a separate, explicitly labeled diagnostic to test minimal
reflection metadata after a first baseline failure. Do not mix those results
with the default compatibility matrix. Preserve version, exception cause, and
full build logs when reporting upstream issues.

These findings must be recorded before enabling corresponding entries in
`docs/performance/binding.md` for performance analysis.

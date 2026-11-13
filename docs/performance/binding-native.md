# Binding Native Image performance baseline

This report benchmarks SJF4J Binding in a **GraalVM Native Image AOT executable**,
not on the GraalVM JVM. It uses the same `FabienReadBenchmark` /
`FabienWriteBenchmark` models and Read/Write boundaries as the
[JVM Binding benchmark](binding.md). The two environments are reported separately;
absolute JVM versus AOT timings are not controlled comparisons.

## Code examples

Representative **Fastjson2 Users POJO** read/write calls in the GraalVM
Native Image workload (imports and fixture setup omitted). The Java API calls
are the same as on the JVM; the JMH suite runs inside an AOT executable.

**Fastjson2 Native (direct library)**

```java
Users users = JSON.parseObject(input, Users.class);
byte[] output = JSON.toJSONBytes(users, JSONWriter.Feature.WriteNulls);
```

**SJF4J Runtime**

```java
Fastjson2Binder binder = new Fastjson2Binder();
Users users = (Users) binder.readNode(input, Users.class);
byte[] output = binder.writeNodeAsBytes(users);
```

**SJF4J Compiled**

```java
@CompiledBinder(backend = Backend.FASTJSON2)
interface UsersBinder {
    @Read Users readUsers(byte[] input);
    @Write byte[] writeUsers(Users value);
}

UsersBinder binder = CompiledInstances.create(UsersBinder.class);
Users users = binder.readUsers(input);
byte[] output = binder.writeUsers(users);
```

Instances and fixtures are prepared before measurement. Here **Native** means
direct Fastjson2 calls, while **Native Image** describes the AOT execution
environment. The [JVM Binding examples](binding.md#code-examples) show the same
workload with Jackson2 and Blackbird. See
[`FabienBindings`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienBindings.java)
for the actual backend-specific implementations.

## Comparison dependency versions

The v1.5.1-SNAPSHOT Native Image results include these backends:

| Backend | Benchmark dependency versions |
| --- | --- |
| Jackson2 | Jackson Databind `2.22.3` |
| Jackson3 | Jackson Databind `3.2.3` |
| Fastjson2 | `2.0.66` |
| Gson | `2.13.1` |

JMH is `1.37`. Versions above come from `sjf4j-testbench/build.gradle.kts` at
the [primary serial benchmark commit](https://github.com/sjf4j-projects/sjf4j/commit/1e1d90d3a01cc46072c4a188e1ed1b9760755f0d).
The [parallel confirmation commit](https://github.com/sjf4j-projects/sjf4j/commit/05fc26b5f3e4500973caf95eb7af6a7a2ab50fb3)
declares the same versions.

## GraalVM Community 25.5.5.0.3

### Results — v1.5.1-SNAPSHOT (2026-11-11)

**Primary Native Image Full:** [serial run #43035710364](https://github.com/sjf4j-projects/sjf4j/actions/runs/43035710364)
at commit `1e1d90d3a01cc46072c4a188e1ed1b9760755f0d`. All **288 exact cases**
completed in **three independent native processes each (864 measurements)**,
on a single GitHub Actions runner with **AMD EPYC 7763**. Profile: GraalVM
Community 25.5.5.0.3, Native Image `-O2` without PGO, one thread,
5 × 1s warmup and 5 × 1s measurement. Values are in **µs/op (lower is better)**.

For each exact backend / implementation / operation / model / shape / size,
select the **two lowest process scores** of the three JMH JSON files and take
their arithmetic mean (**Best 2 Run Mean**). The speedup `Native / Compiled`
is calculated from **unrounded** mean latencies; values below 1× mean
Compiled is slower. `Native` here denotes direct third-party library Binding.

#### 100KB POJO measurements

**Latency (µs/op, lower is better).** Serial Full **Best 2 Run Mean**;
`Native / Compiled` is calculated from unrounded scores.

| Operation / Model / Shape | Backend | Native | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 649.338 | 2,652.008 | 481.058 | 1.35× |
| Read / Users / POJO | Jackson3 | 2,793.639 | 2,589.966 | 438.529 | 6.37× |
| Read / Users / POJO | Fastjson2 | 822.225 | 2,214.061 | 149.591 | 5.50× |
| Read / Users / POJO | Gson | 618.298 | 2,691.207 | 537.766 | 1.15× |
| Read / Clients / POJO | Jackson2 | 1,944.627 | 2,289.963 | 572.212 | 3.40× |
| Read / Clients / POJO | Jackson3 | 4,280.246 | 2,246.084 | 524.021 | 8.17× |
| Read / Clients / POJO | Fastjson2 | 711.462 | 1,832.880 | 210.829 | 3.37× |
| Read / Clients / POJO | Gson | 1,837.837 | 2,506.728 | 718.641 | 2.56× |
| Write / Users / POJO | Jackson2 | 574.911 | 1,731.837 | 458.815 | 1.25× |
| Write / Users / POJO | Jackson3 | 1,715.943 | 1,750.203 | 470.719 | 3.65× |
| Write / Users / POJO | Fastjson2 | 309.519 | 1,464.494 | 152.915 | 2.02× |
| Write / Users / POJO | Gson | 1,299.802 | 2,300.490 | 965.934 | 1.35× |
| Write / Clients / POJO | Jackson2 | 839.368 | 1,757.619 | 602.469 | 1.39× |
| Write / Clients / POJO | Jackson3 | 1,989.092 | 1,771.007 | 610.242 | 3.26× |
| Write / Clients / POJO | Fastjson2 | 271.970 | 1,273.153 | 138.999 | 1.96× |
| Write / Clients / POJO | Gson | 1,432.923 | 2,311.289 | 1,091.939 | 1.31× |

#### 100KB Map measurements

**Latency (µs/op, lower is better).** Same serial Full **Best 2 Run Mean**
and column format as POJO. All 1/10/100KB POJO and Map measurements are
in the [complete serial report](binding/fabien-native-v151snapshot-2026-11-11-serial.md).

| Operation / Model / Shape | Backend | Native | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Read / Users / Map | Jackson2 | 564.459 | 566.532 | 564.847 | 1.00× |
| Read / Users / Map | Jackson3 | 564.772 | 551.281 | 549.925 | 1.03× |
| Read / Users / Map | Fastjson2 | 277.030 | 252.515 | 250.341 | 1.11× |
| Read / Users / Map | Gson | 616.906 | 593.915 | 592.958 | 1.04× |
| Read / Clients / Map | Jackson2 | 568.381 | 572.199 | 568.475 | 1.00× |
| Read / Clients / Map | Jackson3 | 539.941 | 525.578 | 527.394 | 1.02× |
| Read / Clients / Map | Fastjson2 | 323.243 | 303.357 | 303.096 | 1.07× |
| Read / Clients / Map | Gson | 856.038 | 748.508 | 743.959 | 1.15× |
| Write / Users / Map | Jackson2 | 618.955 | 584.580 | 590.460 | 1.05× |
| Write / Users / Map | Jackson3 | 614.174 | 588.996 | 584.239 | 1.05× |
| Write / Users / Map | Fastjson2 | 344.150 | 347.369 | 345.895 | 0.99× |
| Write / Users / Map | Gson | 1,453.316 | 1,065.418 | 1,067.398 | 1.36× |
| Write / Clients / Map | Jackson2 | 588.997 | 555.384 | 556.309 | 1.06× |
| Write / Clients / Map | Jackson3 | 587.194 | 557.641 | 558.606 | 1.05× |
| Write / Clients / Map | Fastjson2 | 300.834 | 305.948 | 307.961 | 0.98× |
| Write / Clients / Map | Gson | 1,365.903 | 1,004.108 | 996.970 | 1.37× |

#### Full runs and reproducibility

| Run | Mode | Results | Runner CPU | End-to-end |
| --- | --- | --- | --- | ---: |
| [#43035710364](https://github.com/sjf4j-projects/sjf4j/actions/runs/43035710364) | **Serial (primary)** | 288 cases × 3 | AMD EPYC 7763 for all backends | ~151 min |
| [#43045839302](https://github.com/sjf4j-projects/sjf4j/actions/runs/43045839302) | Parallel (confirmation) | 288 cases × 3 | EPYC 7763 / EPYC 9V45 across backend jobs | ~42 min |

Both runs completed all cases. They support the consistency of **within-backend**
Native / Compiled comparisons, but **do not** establish an apples-to-apples
cross-run comparison of absolute latencies on different CPU models.
The GitHub-hosted results remain **exploratory**, not controlled release
baselines. The Native Image JMH VM-support warning is also a methodological
limitation; this benchmark does not claim native allocation measurements.

**Complete historical results:** [serial Full details](binding/fabien-native-v151snapshot-2026-11-11-serial.md)
(**primary**) and [parallel Full details](binding/fabien-native-v151snapshot-2026-11-11-parallel.md)
(**confirmation**). Each contains all **96 aggregated workload combinations**
(Read/Write × Users/Clients × POJO/Map × 1/10/100KB × four backends), including
Native, Runtime, Compiled and Native / Compiled, using the same table layout
as the JVM report.

**Original artifacts:** [serial Full JMH results](https://github.com/sjf4j-projects/sjf4j/actions/runs/43035710364/artifacts/13873357604)
and [parallel Full combined results](https://github.com/sjf4j-projects/sjf4j/actions/runs/43045839302/artifacts/13868789457).
Both contain three independent process measurements per case; GitHub-hosted
artifacts are retention-limited. The linked Markdown snapshots retain the
aggregated results after artifacts expire.

The Native Image JMH matrix uses a single executable for all four backends:

| Dimension | Values |
| --- | --- |
| Backend | Jackson2, Jackson3, Fastjson2, Gson |
| Implementation | Native, Runtime, Compiled (Blackbird excluded) |
| Operation | Read, Write |
| Model | Users, Clients |
| Shape | POJO, Map |
| Payload | 1, 10, 100KB |
| Seed | 12345 |
| **Quick / Full cases** | **288** |
| Smoke | **192 cases**: 100KB with seeds 12345 and 324 |

Here **Native** (an implementation) still means direct library Binding.
**Native Image** means a GraalVM AOT-compiled executable. The underlying model,
data generator, correctness checks and measured Read/Write boundaries are identical
to the JVM suite. Do not call an unsupported/failed Native Image combination a
performance result; fix the compatibility issue first.

#### Execute Native Image

Install **GraalVM Community 25.5.5.0.3** (JDK 25) including `native-image`.
The script deliberately checks the GraalVM version and builds one JMH Native
Image with `-O2` and no PGO. That executable contains **all four backends**;
backend selection changes only the JMH cases being executed, not the image.
The separate compatibility-image matrix is not required.

**Fastjson2 native-image configuration (benchmark-specific):** Fastjson2 2.0.66
already ships `native-image.properties` that initializes its default reader
and writer providers at build time. In GraalVM 25, their retained helper objects
(`ObjectReaderBaseModule`, converter implementations, and
`ObjectReaderProvider$LRUAutoTypeCache`) caused image-heap initialization
errors. This benchmark **keeps** the library's original metadata and adds
`--initialize-at-build-time=com.alibaba.fastjson2`. The broader setting is
based on a [community workaround in fastjson2 issue #2834](https://github.com/alibaba/fastjson2/issues/2834);
it has **not** been confirmed as a safe or officially recommended general
configuration. It can embed Fastjson2 static state into the executable, and
the result must be disclosed when discussing performance.

**Reachability training:** The `jmhAgent` JVM uses Fastjson2's supported
`-Dfastjson2.creator=reflect` strategy (instead of the JVM's default ASM
strategy) and 100KB Fabien fixtures so nested POJO members, such as
`Clients$Partner.since`, are encountered. This collects reflection metadata
automatically without maintaining a custom `reflect-config.json` for the
benchmark models. The measured native executable does not set that JVM
property.

[Native Image Smoke #43030244927](https://github.com/sjf4j-projects/sjf4j/actions/runs/43030244927)
completed **192/192** correctness cases for all four backends; both subsequent
Full runs above also completed successfully.

```bash
GRAALVM_HOME=/path/to/graalvm ./scripts/benchmark-binding-native.sh smoke
GRAALVM_HOME=/path/to/graalvm ./scripts/benchmark-binding-native.sh quick
GRAALVM_HOME=/path/to/graalvm ./scripts/benchmark-binding-native.sh full

# Optional: reuse a previously built native executable to avoid recompiling.
GRAALVM_HOME=/path/to/graalvm BINDING_NATIVE_SKIP_BUILD=1 \
  BINDING_NATIVE_BACKEND=jackson3 ./scripts/benchmark-binding-native.sh quick
```

The **Binding Native Image Benchmark (GraalVM 25)** Actions workflow has
two manual dropdown inputs: **Profile** (`smoke`, `quick`, `full`) and
**Mode** (`parallel` by default, or `serial`). Both modes build **one
shared Native Image** with Git SHA, compiler stamp and SHA-256 verification:

- `serial` runs **all four backends sequentially on one runner**, suitable
  for comparing absolute latency across backends in the same host environment.
- `parallel` runs **one backend per runner**, up to four concurrently; all
  three implementations of a backend still share the same runner.
- Both modes use the same JMH implementation, parameters and full case matrix.
  The aggregate job checks result completeness and publishes the combined
  JSON, metadata, runner information and a manifest. Failed backend artifacts
  are preserved when available.

To test a same-repository PR, add `[run-native-smoke]` for parallel Smoke,
or `[run-native-serial-smoke]` for serial Smoke, then remove the marker.
Fork PRs cannot start the expensive jobs. **Smoke** checks 48 cases per backend
(**192 total**). **Quick/Full** each cover 72 unique cases per backend
(**288 total**). Full writes **12 JSON files**, three per backend,
totaling **864 process-level case measurements**.

The local script remains unchanged and still works on a single machine.
It writes Quick/Full JMH JSON and metadata under
`build/performance/binding/native/`; local Smoke does not persist JMH results.
The compiled binary is
`sjf4j-testbench/build/native/nativeCompile/sjf4j-benchmark-native`.
An Actions benchmark reuses that executable only when its SHA-256 checksum,
Git SHA and GraalVM compiler stamp agree. A cached local build likewise
requires matching Git SHA and GraalVM version.

GitHub Actions artifacts are named by **workflow run ID**, not run attempt.
This allows **Re-run failed jobs** to reuse the successful Native Image build
and previously completed backend results. If a producer job itself reruns, its
artifact is replaced with `upload-artifact@v4`'s `overwrite: true`; the final
combined artifact has a separate prefix so it cannot be mistaken for a backend
result. All required artifacts are retained for 30 days to match the Actions
partial re-run window.

**Runner comparison:** Native / Runtime / Compiled measurements within a
backend share a runner in both modes. Serial also keeps all four backends
on one runner; parallel may use different host CPU models or contention
across backends. Do not present parallel cross-backend absolute times as
a same-machine ranking.


JMH uses **`-f 0`** inside the Native Image. Full launches **three independent
native executable processes**, each with 5 × 1s warmup and 5 × 1s measurement.
For each exact backend / implementation / operation / model / shape / size / seed,
take the per-process mean from each JSON and discard the slowest process, then
average the remaining two (**Best 2 Run Mean**). These are independent
*process* results, not JVM fork arrays. Quick runs once, 2 × 0.2s warmup
and 2 × 0.2s measurement. Native Image JMH allocation is intentionally not
reported until a valid, comparable native allocation metric is established.

Metadata records the executable's byte size. On Linux, Full also saves GNU
`/usr/bin/time -v` output with **peak process RSS** for each run (not steady
state heap size). JMH launcher startup is **not** an application-startup
benchmark; a separate launch-specific workload is needed for that comparison.
For controlled JVM versus Native Image conclusions, run both on the same
machine under equivalent load and CPU constraints. GitHub-hosted results
remain exploratory.

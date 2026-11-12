# Binding performance baseline

SJF4J's primary Binding workload is **FabienReadBenchmark / FabienWriteBenchmark**.
Benchmark sources are in `sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/`.
The models and data generator are in `sjf4j-testbench/src/main/java`
so regular tests can reuse them.

## JVM — Temurin JDK 25

### Results — v1.5.0 (2026-11-09)

**Full JMH on Temurin 25.0.5 · 3 independent forks · Best 2 Mean · two seeds · 1/10/100KB.** All **1,008** cases completed in [GitHub Actions run #42581094035](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035). Results are exploratory because the seven backend jobs used shared runners. All latency comparisons below are **within each backend**; cross-backend absolute comparisons are not controlled.

#### 100KB POJO measurements

#### Workload models

- **Users:** ordinary JSON scalar values mapped to Java `String`, numeric primitives (`int`/`double`) and `boolean`, plus nested collections/objects. Fields such as `guid` and `registered` remain **strings**, not `UUID` or date/time objects.
- **Clients:** strongly typed values including `UUID`, `LocalDate`, `OffsetDateTime`, `BigDecimal` and enums, alongside primitive values, arrays and nested collections/objects.

**Why Clients POJO Read can differ more:** SJF4J uses `ValueFormat`/`ValueCodec` specialized paths, including fast paths for temporal-value parsing, to avoid part of the generic value-conversion overhead. These paths help explain the larger difference from Jackson Native/Blackbird on **Clients POJO Read** (notably at 100KB); the observed gain also includes other binding-path costs and must not be attributed solely to `ValueFormat`. For **Map Read**, the target is a generic Map rather than the typed `Clients` POJO, so do not infer the same benefit.

**Latency (µs/op, lower is better).** Columns are ordered Native / Blackbird / Runtime / Compiled, followed by `Native / Compiled` (a ratio of unrounded latency measurements; below 1× means Compiled is slower). The tables cover Read and Write for both Users and Clients; the [complete report](binding/fabien-v150-2026-11-09.md) includes all Read/Write, Users/Clients, POJO/Map, and 1/10/100KB combinations.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 182.322 | 158.189 | 177.032 | 148.947 | 1.22× |
| Read / Users / POJO | Jackson3 | 226.058 | 222.663 | 226.480 | 194.044 | 1.16× |
| Read / Users / POJO | Fastjson2 | 63.783 | — | 89.261 | 65.893 | 0.97× |
| Read / Users / POJO | Gson | 324.607 | — | 325.109 | 275.615 | 1.18× |
| Read / Users / POJO | JSON-P | — | — | 356.928 | 322.115 | — |
| Read / Users / POJO | Simple | — | — | 405.426 | 343.667 | — |
| Read / Users / POJO | SnakeYAML | 6,374.043 | — | 4,653.602 | 4,466.424 | 1.43× |
| Write / Users / POJO | Jackson2 | 139.779 | 121.073 | 112.040 | 110.857 | 1.26× |
| Write / Users / POJO | Jackson3 | 184.547 | 198.032 | 177.837 | 168.982 | 1.09× |
| Write / Users / POJO | Fastjson2 | 53.039 | — | 74.299 | 53.399 | 0.99× |
| Write / Users / POJO | Gson | 443.815 | — | 243.785 | 210.322 | 2.11× |
| Write / Users / POJO | JSON-P | — | — | 233.514 | 190.743 | — |
| Write / Users / POJO | Simple | — | — | 501.776 | 478.316 | — |
| Write / Users / POJO | SnakeYAML | 9,208.703 | — | 4,141.737 | 4,108.626 | 2.24× |
| Read / Clients / POJO | Jackson2 | 622.862 | 592.055 | 199.988 | 175.102 | 3.56× |
| Read / Clients / POJO | Jackson3 | 772.862 | 754.982 | 249.817 | 218.543 | 3.54× |
| Read / Clients / POJO | Fastjson2 | 74.604 | — | 103.453 | 74.001 | 1.01× |
| Read / Clients / POJO | Gson | 833.780 | — | 385.232 | 333.997 | 2.50× |
| Read / Clients / POJO | JSON-P | — | — | 491.747 | 445.431 | — |
| Read / Clients / POJO | Simple | — | — | 408.852 | 385.367 | — |
| Read / Clients / POJO | SnakeYAML | 6,704.549 | — | 4,539.373 | 4,380.591 | 1.53× |
| Write / Clients / POJO | Jackson2 | 257.872 | 242.339 | 211.957 | 187.020 | 1.38× |
| Write / Clients / POJO | Jackson3 | 340.871 | 338.389 | 297.200 | 265.962 | 1.28× |
| Write / Clients / POJO | Fastjson2 | 48.670 | — | 79.194 | 49.454 | 0.98× |
| Write / Clients / POJO | Gson | 487.814 | — | 300.414 | 275.900 | 1.77× |
| Write / Clients / POJO | JSON-P | — | — | 300.859 | 263.251 | — |
| Write / Clients / POJO | Simple | — | — | 486.467 | 457.020 | — |
| Write / Clients / POJO | SnakeYAML | 9,186.789 | — | 4,858.748 | 4,713.128 | 1.95× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation scores, averaged equally across two seeds, not the latency-selected forks. The rightmost `Native / Compiled` is the B/op ratio (below 1× means Compiled allocates more), not a latency speedup.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 330,793 | 330,057 | 327,809 | 327,816 | 1.01× |
| Read / Users / POJO | Jackson3 | 297,658 | 296,922 | 294,650 | 294,649 | 1.01× |
| Read / Users / POJO | Fastjson2 | 297,328 | — | 297,273 | 297,272 | 1.00× |
| Read / Users / POJO | Gson | 503,938 | — | 501,018 | 501,018 | 1.01× |
| Read / Users / POJO | JSON-P | — | — | 519,956 | 520,399 | — |
| Read / Users / POJO | Simple | — | — | 504,235 | 504,234 | — |
| Read / Users / POJO | SnakeYAML | 9,088,841 | — | 6,810,561 | 6,694,380 | 1.36× |
| Write / Users / POJO | Jackson2 | 199,054 | 198,319 | 112,061 | 112,061 | 1.78× |
| Write / Users / POJO | Jackson3 | 199,069 | 198,334 | 112,085 | 112,085 | 1.78× |
| Write / Users / POJO | Fastjson2 | 105,116 | — | 105,061 | 105,060 | 1.00× |
| Write / Users / POJO | Gson | 522,167 | — | 216,358 | 216,357 | 2.41× |
| Write / Users / POJO | JSON-P | — | — | 317,176 | 317,176 | — |
| Write / Users / POJO | Simple | — | — | 216,168 | 216,167 | — |
| Write / Users / POJO | SnakeYAML | 6,824,854 | — | 5,866,154 | 5,866,153 | 1.16× |
| Read / Clients / POJO | Jackson2 | 1,241,410 | 1,225,244 | 282,793 | 282,937 | 4.39× |
| Read / Clients / POJO | Jackson3 | 1,213,238 | 1,197,067 | 254,572 | 254,754 | 4.76× |
| Read / Clients / POJO | Fastjson2 | 255,213 | — | 256,333 | 254,605 | 1.00× |
| Read / Clients / POJO | Gson | 1,399,430 | — | 486,071 | 486,214 | 2.88× |
| Read / Clients / POJO | JSON-P | — | — | 590,649 | 590,910 | — |
| Read / Clients / POJO | Simple | — | — | 437,975 | 438,119 | — |
| Read / Clients / POJO | SnakeYAML | 9,134,260 | — | 6,143,448 | 6,143,591 | 1.49× |
| Write / Clients / POJO | Jackson2 | 333,146 | 316,993 | 211,794 | 211,793 | 1.57× |
| Write / Clients / POJO | Jackson3 | 333,161 | 317,009 | 211,506 | 211,506 | 1.58× |
| Write / Clients / POJO | Fastjson2 | 110,524 | — | 110,469 | 110,468 | 1.00× |
| Write / Clients / POJO | Gson | 716,743 | — | 352,870 | 352,870 | 2.03× |
| Write / Clients / POJO | JSON-P | — | — | 432,582 | 432,584 | — |
| Write / Clients / POJO | Simple | — | — | 352,679 | 352,679 | — |
| Write / Clients / POJO | SnakeYAML | 6,530,213 | — | 5,719,682 | 5,722,145 | 1.14× |

**[Complete 1/10/100KB results, all implementations, allocation, and fork stability](binding/fabien-v150-2026-11-09.md)** · **[Original JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035/artifacts/13675482490)**

## Native Image — GraalVM Community 25.5.5.0.3

This is a **separate execution environment** for the **same Fabien Binding workloads**,
not a GraalVM JVM/JIT benchmark. The JVM results above remain the historical reference.

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

#### 100KB POJO and Map measurements

The table covers both Java POJO binding and generic Map binding for each
operation, model, and backend. **Latency (µs/op, lower is better)** uses the
same serial Full **Best 2 Run Mean** described above; `Native / Compiled`
is calculated from unrounded scores. See the [complete serial report](binding/fabien-native-v151snapshot-2026-11-11-serial.md)
for all POJO/Map results at 1/10/100KB.

| Operation / Model / Shape | Backend | Native | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 649.338 | 2,652.008 | 481.058 | 1.35× |
| Read / Users / POJO | Jackson3 | 2,793.639 | 2,589.966 | 438.529 | 6.37× |
| Read / Users / POJO | Fastjson2 | 822.225 | 2,214.061 | 149.591 | 5.50× |
| Read / Users / POJO | Gson | 618.298 | 2,691.207 | 537.766 | 1.15× |
| Read / Users / Map | Jackson2 | 564.459 | 566.532 | 564.847 | 1.00× |
| Read / Users / Map | Jackson3 | 564.772 | 551.281 | 549.925 | 1.03× |
| Read / Users / Map | Fastjson2 | 277.030 | 252.515 | 250.341 | 1.11× |
| Read / Users / Map | Gson | 616.906 | 593.915 | 592.958 | 1.04× |
| Read / Clients / POJO | Jackson2 | 1,944.627 | 2,289.963 | 572.212 | 3.40× |
| Read / Clients / POJO | Jackson3 | 4,280.246 | 2,246.084 | 524.021 | 8.17× |
| Read / Clients / POJO | Fastjson2 | 711.462 | 1,832.880 | 210.829 | 3.37× |
| Read / Clients / POJO | Gson | 1,837.837 | 2,506.728 | 718.641 | 2.56× |
| Read / Clients / Map | Jackson2 | 568.381 | 572.199 | 568.475 | 1.00× |
| Read / Clients / Map | Jackson3 | 539.941 | 525.578 | 527.394 | 1.02× |
| Read / Clients / Map | Fastjson2 | 323.243 | 303.357 | 303.096 | 1.07× |
| Read / Clients / Map | Gson | 856.038 | 748.508 | 743.959 | 1.15× |
| Write / Users / POJO | Jackson2 | 574.911 | 1,731.837 | 458.815 | 1.25× |
| Write / Users / POJO | Jackson3 | 1,715.943 | 1,750.203 | 470.719 | 3.65× |
| Write / Users / POJO | Fastjson2 | 309.519 | 1,464.494 | 152.915 | 2.02× |
| Write / Users / POJO | Gson | 1,299.802 | 2,300.490 | 965.934 | 1.35× |
| Write / Users / Map | Jackson2 | 618.955 | 584.580 | 590.460 | 1.05× |
| Write / Users / Map | Jackson3 | 614.174 | 588.996 | 584.239 | 1.05× |
| Write / Users / Map | Fastjson2 | 344.150 | 347.369 | 345.895 | 0.99× |
| Write / Users / Map | Gson | 1,453.316 | 1,065.418 | 1,067.398 | 1.36× |
| Write / Clients / POJO | Jackson2 | 839.368 | 1,757.619 | 602.469 | 1.39× |
| Write / Clients / POJO | Jackson3 | 1,989.092 | 1,771.007 | 610.242 | 3.26× |
| Write / Clients / POJO | Fastjson2 | 271.970 | 1,273.153 | 138.999 | 1.96× |
| Write / Clients / POJO | Gson | 1,432.923 | 2,311.289 | 1,091.939 | 1.31× |
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

## Baseline policy

- **Java 25** is the primary language/runtime baseline: Temurin JDK 25 for JVM,
  GraalVM Community 25.5.5.0.3 for Native Image. Record the exact vendor and patch build.
- Three execution profiles: **Smoke** verifies correctness at the largest payload
  (100KB, both seeds); **Quick** diagnoses performance; **Full** establishes release
  baselines. Quick and Full execute the **same** case matrix, differing only in
  JMH fork, warmup and measurement parameters. Smoke is never a baseline.
- **Suite version: 3** for new runs: the JVM Quick/Full default has one seed.
  Historical v1.5.0 results used suite 2 and two seeds. Workload, generator,
  target semantics, or measured boundary changes require another version bump.
  Never compare different seed aggregation or suite versions as a controlled A/B test.
- Compare measurements on the same hardware, OS, JDK build, inputs, and settings.
- Read measures parsing from UTF-8 `byte[]` and construction of a new POJO/Map.
  Write measures serialization into a **new byte[]**; value generation, provider
  selection and correctness checks happen outside the timed path.
- Native bindings use their libraries directly, without calling SJF4J during
  measurement. Jackson 2 and 3 have **native** and **blackbird** variants,
  both configured to preserve the expected date/time semantics. Gson native
  has explicit LocalDate / OffsetDateTime adapters. Gson native performs
  UTF-8 decoding/encoding because its API is character-oriented.
- JSON-P's native result is a JsonValue tree, **not** an equivalent Java POJO/Map
  binder; Simple has no external native library. Neither gets a misleading
  native score in this matrix. SnakeYAML native covers **POJO and Map**: POJO uses a native
  SnakeYAML Constructor/Representer configured for UUID and Java-time fields
  without invoking any SJF4J mapping code in the timed operation.
- YAML and JSON use different textual input: compare within a given backend
  and workload, not as a claim that the formats have equal complexity.
- Incompatible backend/implementation combinations intentionally fail early.
  The runner invokes disjoint valid groups instead of producing fake scores.

## Execute

```bash
./scripts/benchmark-binding.sh             # Print usage (no benchmark run)
./scripts/benchmark-binding.sh smoke       # Fast correctness check at 100KB
./scripts/benchmark-binding.sh quick       # Diagnostic performance
./scripts/benchmark-binding.sh full        # Complete baseline measurement
```

Both benchmark classes have a `main(String[] args)` entry point for IDEA.
Edit their local default JMH arguments to choose a backend, implementation,
model, shape or seed. The default selects Jackson 2 (all four modes). Supplied
command-line arguments override these defaults.

**Quick and Full use identical cases:** all 7 backends; Runtime / Compiled
for every backend; Native for Jackson2, Jackson3, Fastjson2, Gson and SnakeYAML; Blackbird for Jackson2 and Jackson3; Users / Clients; POJO / Map;
1/10/100KB; seed 12345 by default (**504 cases**). Set
`BINDING_SEEDS=12345,324` for an optional extended 1,008-case run.
Smoke still checks **both** seeds at 100KB and remains 336 cases.

| JMH setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 (in-process) | 1 | 3 |
| Warmup | 0 | 2 × 0.2s | 5 × 1s |
| Measurement | 1 × 0.05s | 2 × 0.2s | 5 × 1s |
| Data size | 100KB | 1/10/100KB | 1/10/100KB |
| Seeds | 12345/324 | 12345 | 12345 |
| Backend / implementation / model / shape | Same | Same | Same |
| GC profiler | No | Yes | Yes |

Quick and Full emit **one JMH JSON per backend**, not per implementation, and a companion
`.meta.md` file under `build/performance/binding/` (the optional second argument
changes the directory). The `params.implementation` field distinguishes runtime,
compiled, native and (Jackson only) blackbird inside each backend's JSON.
The seven files end in `-jackson2.json`, `-jackson3.json`, `-fastjson2.json`,
`-gson.json`, `-jsonp.json`, `-simple.json`, and `-snake.json`.
This avoids unsupported combinations while retaining raw fork measurements
and `-prof gc` allocation data.
The runner also checks the expected count of benchmark entries in each JSON file,
exiting with an error if JMH reported only a subset of the requested cases.
Smoke is a **correctness check only**: it intentionally omits warmup and forks,
so ignore its recorded throughput and timing scores. Smoke creates **no persistent
JMH JSON or metadata files**: temporary JMH result data used for completeness
checking is deleted after each backend (and cleaned up on failure). Run Smoke first to catch
large-payload failures before investing time in Quick or Full.
A completed Smoke run ends with a per-backend `PASS actual/expected` summary and
`TOTAL: 336/336 PASSED` followed by `ALL PASSED`. A failure instead prints
the failed backend, marks later backends `NOT RUN`, and exits nonzero.

Full is intentionally expensive; execute on an otherwise idle benchmark host.
On macOS the script invokes `caffeinate -i` around each backend to inhibit idle
sleep, but **closing a MacBook lid can still suspend the benchmark**. Keep the
lid open, use AC power, and discard results from a run interrupted by sleep.
After a successful Full run there should be **7 per-backend JSON files plus 1
`.meta.md`**, for **504** JMH benchmark entries (including Read and Write) by default.
The optional two-seed Full produces **1,008** entries.

## GitHub Actions Full (exploratory)

The manual **Binding Full Benchmark (JDK 25)** workflow reuses this script.
In the repository **Actions** tab, select the workflow, click **Run workflow**
and choose the commit/branch to benchmark.

- The JMH jar is compiled once, then each of the seven backends runs in a
  separate Ubuntu 24.04 job under JDK 25. Each backend job has a 330-minute
  timeout, below GitHub's 6-hour per-job limit.
- Each job uploads a backend-specific artifact with **raw JMH JSON** (including
  independent forks and GC allocation metrics), environment metadata, hardware
  information, and a console log. Representative 100KB Users/POJO scores are
  displayed in the Actions job summary. A final job also creates a **combined
  artifact** containing all backend folders, so the entire Full run can be
  downloaded in one archive.
- **Shared GitHub-hosted runner timings are diagnostic, not release baselines.**
  Hardware and scheduling vary between jobs and runs. The metadata explicitly
  records `Baseline eligible: no`, even though the workload and JMH parameters
  are identical to local Full. Do not compare absolute results across backend
  jobs as if they used the same machine.
- The workflow is **manual-only**; it does not run on pushes or pull requests.

To run a single backend locally on JDK 25, for example:

```bash
BINDING_BACKEND=jackson2 ./scripts/benchmark-binding.sh full
```

## Maintaining history in the repository

1. Run **Full** on a clean release commit using the designated JDK 25 host.
   Ensure all expected groups completed and setup verification succeeded.
2. Copy the raw JMH JSON files and the corresponding `.meta.md` to
   `docs/performance/baselines/binding/<release>/` and commit them.
3. Update the release index below with Git SHA, suite version, full JDK
   build, and links to the files or release directory. Do not transcribe
   thousands of scores into Markdown.
4. For a suspected regression, run base and candidate on the same host
   with identical profile and versions (except the intended change).
   Compare matching method + params (including `implementation`),
   independent forks, uncertainty, and allocation differences.
5. Ask AI to read **all** JMH JSON files and the metadata for both commits,
   then summarize speed and memory regressions. No external service is needed.

## Release index

| Release | Commit | JDK | Suite | Results |
| --- | --- | --- | --- | --- |
| v1.5.0 (GitHub Actions; exploratory) | `c17ac35593e7` | 25.0.5 | 2 | [Full 1/10/100KB report](binding/fabien-v150-2026-11-09.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035) |

A controlled, same-hardware release baseline is still pending; this historical
suite-2 Full run used shared GitHub-hosted runners. Suite-3 Native Image results
will be added to this document and the release history once measured.

## Reporting and ranking results

**Full uses 3 independent JMH forks. The default comparison metric is Best 2 Mean,
computed independently for every implementation and every exact benchmark case.**
For AverageTime (`us/op`, lower is better):

1. For a fixed backend, Read/Write, Users/Clients, POJO/Map, size, **seed**, and
   **implementation**, take the arithmetic mean of its 5 measurement iterations
   **within each fork** (`primaryMetric.rawData`: 3 arrays of 5 values).
2. Drop the **single slowest fork** (the **highest** fork mean). Average the
   remaining two fork means: `best2(seed, implementation, case)`.
3. For **suite 3** default results, report `best2` for seed `12345`.
   For historical **suite 2** results, or optional extended runs with
   `BINDING_SEEDS=12345,324`, average the two seed-specific `best2` scores
   with **equal weight**. Retain individual-seed results for drilldown.

**Do not share the excluded fork across implementations or cases.** Native,
Blackbird, Runtime and Compiled each select their own best two forks, as does
each backend, operation, model, shape, payload size and seed. Present the four
implementations **in this fixed order: Native → Blackbird → Runtime → Compiled**
at every reporting level; omit an unsupported implementation rather than
reporting an invented score. Highlight the lowest time in each comparable row.

For metrics where **higher is better** (e.g. throughput), keep the two
**highest** fork means instead. For GC/allocation metrics, report `B/op` with
an explicit aggregation label; if applying Best 2 to that metric, rank the
forks **independently using that metric's own values**, not the time-selected
forks. Preserve all raw forks and the untrimmed JMH `primaryMetric.score`.

**Meaning and caveat:** Best 2 Mean describes the faster observed JVM
execution state; it is deliberately biased toward lower latency. It is
**not** the expected latency of a random fresh JVM fork, and it is not a JMH
confidence interval. When fork means diverge materially (e.g. the slowest
exceeds the fastest by >15%), show the three fork means and original 3-fork
JMH score alongside Best 2, rather than hiding an unstable JIT outcome.
Do not attach JMH's `scoreError` (computed for all 3 forks) to Best 2.

GitHub Actions' per-backend job summary applies Best 2 to its representative
100KB Users/POJO/seed 12345 cases and separately shows the original JMH mean.
The raw per-backend JSON remains unmodified and is the source for all detailed
reports. Existing pre-change Full artifacts used 5 × 0.4s warmup and measurement;
new Full runs use **5 × 1s** each, about **2.5×** as much timed JMH work.
Do not treat across-duration comparisons as controlled A/B tests without
repeating them using identical JMH settings.

## Interpreting changes

For JMH AverageTime, lower is better:
`deltaPercent = 100 * (candidateBest2 / baselineBest2 - 1)`.
For a controlled comparison, use the same fork-selection rule and JMH timing
on baseline and candidate; inspect the original all-fork data as a stability check.

- Under 3%: often noise; continue tracking.
- 3–5%: inspect variability.
- 5–10%: repeat and investigate.
- Above 10%: high-priority investigation.

Thresholds are triage rules, **not proof of regression**. Analyze independent
JMH forks, not iterations within a fork as independent observations.
Track `gc.alloc.rate.norm` (B/op) as well as time.

A separate **Validation** suite and baseline will follow after Binding stabilizes.

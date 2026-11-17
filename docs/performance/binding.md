# Binding JVM performance baseline

SJF4J Binding performance versus direct library binding (**Native**) and, for
Jackson2/3, **Blackbird**. The suite measures independent JSON/YAML
`byte[]` Read and Write operations on Fabien Users/Clients POJO and Map workloads;
fixture creation and backend setup are excluded. The primary workloads are
[`FabienReadBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienReadBenchmark.java)
and [`FabienWriteBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienWriteBenchmark.java).
GraalVM AOT results are documented separately in [Binding Native Image](binding-native.md).

## Comparison dependency versions

**Current result version:** `v1.5.2-SNAPSHOT` (2026-11-17); the previous `v1.5.1-SNAPSHOT` result remains in the [release index](#release-index). Other declared benchmark dependencies:

| Backend | Benchmark dependency versions |
| --- | --- |
| Jackson2 | Jackson Databind `2.22.3`; Blackbird `2.22.3` |
| Jackson3 | Jackson Databind `3.2.3`; Blackbird `3.2.3` |
| Fastjson2 | `2.0.66` |
| Gson | `2.13.1` |
| JSON-P | Jakarta JSON-P API `2.1.3`; Eclipse Parsson `1.1.7` |
| SnakeYAML | `2.5` |
| Simple | SJF4J built-in backend; no external comparison library |

JMH is `1.37`. Versions above are from `sjf4j-testbench/build.gradle.kts` at the [current JVM snapshot commit](https://github.com/sjf4j-projects/sjf4j/commit/555137cf7a98a5ecf0e46ac7efcdd4362e3c0e3e).

## Code examples

Representative **Users POJO** Read and Write calls for each backend (imports
and fixture setup omitted). `input` is a prebuilt JSON `byte[]`; `value` is
a prebuilt `Users` object. **Read and Write are separate measured operations**,
not a timed roundtrip. All mappers, binders and compiled instances are
initialized outside timed methods.

**Jackson Native**

```java
Users parsed = JACKSON2.readValue(input, Users.class);
byte[] encoded = JACKSON2.writeValueAsBytes(value);
```

**Jackson Blackbird**

```java
Users parsed = JACKSON2_BLACKBIRD.readValue(input, Users.class);
byte[] encoded = JACKSON2_BLACKBIRD.writeValueAsBytes(value);
```

**Fastjson2 Native**

```java
Users parsed = JSON.parseObject(input, Users.class);
byte[] encoded = JSON.toJSONBytes(value, JSONWriter.Feature.WriteNulls);
```

**Gson Native**

```java
Users parsed = GSON.fromJson(
        new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8),
        Users.class);
byte[] encoded = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
```

**SJF4J Runtime**

```java
Jackson2Binder binder = new Jackson2Binder();
Users parsed = binder.readNode(input, Users.class);
byte[] encoded = binder.writeNodeAsBytes(value);
```

**SJF4J Compiled**

```java
@CompiledBinder(backend = Backend.JACKSON2)
interface Jackson2Binding {
    @Read Users readUsers(byte[] input);
    @Write byte[] writeUsers(Users value);
}

Jackson2Binding binder = CompiledInstances.create(Jackson2Binding.class);
Users parsed = binder.readUsers(input);
byte[] encoded = binder.writeUsers(value);
```

The snippets show the POJO shape; the benchmark also includes Clients and
Map workloads, plus JSON-P, Simple and SnakeYAML backends. See
[`FabienBindings`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienBindings.java)
and [`FabienNative`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienNative.java)
for the exact backend configurations, especially the preconfigured Gson
adapters used by the benchmark.

## JVM — Temurin JDK 25

### Results — v1.5.2-SNAPSHOT (2026-11-17)

**Full JMH on Temurin 25.0.5 · 3 independent forks · Best 2 Mean · two seeds · 1/10/100KB.** All **1,008** cases completed in [GitHub Actions run #44011622909](https://github.com/sjf4j-projects/sjf4j/actions/runs/44011622909). These are exploratory shared-runner measurements; comparisons of absolute latency across jobs or runs are **not controlled**.

**Previous results are preserved:** [v1.5.1-SNAPSHOT full detailed report](binding/fabien-v151snapshot-2026-11-13.md) · [2026-11-13 original JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017). The new [v1.5.2-SNAPSHOT full report](binding/fabien-v152snapshot-2026-11-17.md) covers every 1/10/100KB case.

**Runner CPUs (new run):** Intel Xeon 6975P-C for Jackson2/3, Fastjson2 and SnakeYAML; AMD EPYC 9V74 for Gson, JSON-P and Simple. The previous run used other CPUs for most backends. **Do not treat old/new µs/op deltas as controlled A/B evidence.** `Native / Compiled` below is computed *within each new backend run*; above 1× means Compiled is faster.

#### Workload models

- **Users:** ordinary JSON scalar values mapped to Java `String`, numeric primitives and booleans, plus nested arrays/objects. The `guid` and `registered` fields remain strings, not typed UUID/time values.
- **Clients:** strongly typed values including `UUID`, `LocalDate`, `OffsetDateTime`, `BigDecimal` and enums, plus primitives, arrays and nested objects.
- **Clients POJO Read:** SJF4J's specialized ValueFormat/ValueCodec conversion paths help explain the large relative advantage over generic Jackson binding; other overhead differences also contribute. Map Read uses generic Maps and must be interpreted separately.

#### 100KB POJO measurements

**Latency (µs/op, lower is better; Best 2 Mean).** Columns are Native / Blackbird / Runtime / Compiled, followed by Native / Compiled. The complete report retains all fork data and additional input sizes.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 203.941 | 175.321 | 190.035 | 168.130 | 1.21× |
| Read / Users / POJO | Jackson3 | 177.626 | 172.209 | 175.334 | 154.915 | 1.15× |
| Read / Users / POJO | Fastjson2 | 66.983 | — | 92.234 | 62.825 | 1.07× |
| Read / Users / POJO | Gson | 324.653 | — | 328.452 | 276.769 | 1.17× |
| Read / Users / POJO | JSON-P | — | — | 358.748 | 323.230 | — |
| Read / Users / POJO | Simple | — | — | 397.698 | 343.756 | — |
| Read / Users / POJO | SnakeYAML | 5258.923 | — | 2956.423 | 2883.891 | 1.82× |
| Write / Users / POJO | Jackson2 | 184.641 | 158.201 | 151.034 | 138.314 | 1.33× |
| Write / Users / POJO | Jackson3 | 163.961 | 167.440 | 142.838 | 140.838 | 1.16× |
| Write / Users / POJO | Fastjson2 | 57.624 | — | 75.113 | 56.816 | 1.01× |
| Write / Users / POJO | Gson | 444.929 | — | 244.082 | 212.536 | 2.09× |
| Write / Users / POJO | JSON-P | — | — | 235.372 | 192.242 | — |
| Write / Users / POJO | Simple | — | — | 500.193 | 479.252 | — |
| Write / Users / POJO | SnakeYAML | 6338.410 | — | 2955.721 | 2875.113 | 2.20× |
| Read / Clients / POJO | Jackson2 | 607.981 | 578.599 | 221.798 | 194.350 | 3.13× |
| Read / Clients / POJO | Jackson3 | 594.815 | 589.579 | 209.817 | 184.320 | 3.23× |
| Read / Clients / POJO | Fastjson2 | 75.874 | — | 111.432 | 73.124 | 1.04× |
| Read / Clients / POJO | Gson | 817.038 | — | 388.026 | 336.439 | 2.43× |
| Read / Clients / POJO | JSON-P | — | — | 492.846 | 435.598 | — |
| Read / Clients / POJO | Simple | — | — | 409.674 | 379.636 | — |
| Read / Clients / POJO | SnakeYAML | 5304.537 | — | 2878.002 | 2852.980 | 1.86× |
| Write / Clients / POJO | Jackson2 | 339.859 | 328.429 | 260.249 | 222.448 | 1.53× |
| Write / Clients / POJO | Jackson3 | 325.825 | 322.104 | 258.515 | 224.159 | 1.45× |
| Write / Clients / POJO | Fastjson2 | 51.584 | — | 80.823 | 52.470 | 0.98× |
| Write / Clients / POJO | Gson | 494.328 | — | 300.723 | 276.068 | 1.79× |
| Write / Clients / POJO | JSON-P | — | — | 304.025 | 263.009 | — |
| Write / Clients / POJO | Simple | — | — | 488.017 | 460.483 | — |
| Write / Clients / POJO | SnakeYAML | 6282.278 | — | 3356.756 | 3305.835 | 1.90× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation scores, averaged equally across both seeds; `Native / Compiled` is the B/op ratio (not a latency speedup).

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 330,793 | 330,057 | 327,809 | 327,809 | 1.01× |
| Read / Users / POJO | Jackson3 | 297,657 | 296,921 | 294,649 | 294,649 | 1.01× |
| Read / Users / POJO | Fastjson2 | 297,328 | — | 297,273 | 297,272 | 1.00× |
| Read / Users / POJO | Gson | 503,938 | — | 501,018 | 501,018 | 1.01× |
| Read / Users / POJO | JSON-P | — | — | 520,493 | 518,432 | — |
| Read / Users / POJO | Simple | — | — | 504,235 | 504,234 | — |
| Read / Users / POJO | SnakeYAML | 9,187,033 | — | 6,757,925 | 6,746,440 | 1.36× |
| Write / Users / POJO | Jackson2 | 199,054 | 198,318 | 112,061 | 112,061 | 1.78× |
| Write / Users / POJO | Jackson3 | 199,070 | 198,334 | 112,085 | 112,085 | 1.78× |
| Write / Users / POJO | Fastjson2 | 105,116 | — | 105,061 | 105,060 | 1.00× |
| Write / Users / POJO | Gson | 522,167 | — | 216,358 | 216,358 | 2.41× |
| Write / Users / POJO | JSON-P | — | — | 317,176 | 317,176 | — |
| Write / Users / POJO | Simple | — | — | 216,168 | 216,167 | — |
| Write / Users / POJO | SnakeYAML | 6,836,564 | — | 5,866,142 | 5,853,193 | 1.17× |
| Read / Clients / POJO | Jackson2 | 1,241,416 | 1,225,244 | 282,800 | 282,937 | 4.39× |
| Read / Clients / POJO | Jackson3 | 1,213,236 | 1,197,084 | 254,609 | 254,753 | 4.76× |
| Read / Clients / POJO | Fastjson2 | 255,213 | — | 256,333 | 254,605 | 1.00× |
| Read / Clients / POJO | Gson | 1,399,426 | — | 486,071 | 486,214 | 2.88× |
| Read / Clients / POJO | JSON-P | — | — | 591,128 | 590,135 | — |
| Read / Clients / POJO | Simple | — | — | 437,975 | 438,119 | — |
| Read / Clients / POJO | SnakeYAML | 9,319,027 | — | 6,298,548 | 6,298,692 | 1.48× |
| Write / Clients / POJO | Jackson2 | 333,147 | 316,993 | 211,482 | 211,482 | 1.58× |
| Write / Clients / POJO | Jackson3 | 333,161 | 317,009 | 211,506 | 211,506 | 1.58× |
| Write / Clients / POJO | Fastjson2 | 110,524 | — | 110,469 | 110,468 | 1.00× |
| Write / Clients / POJO | Gson | 716,743 | — | 352,870 | 352,870 | 2.03× |
| Write / Clients / POJO | JSON-P | — | — | 432,582 | 432,584 | — |
| Write / Clients / POJO | Simple | — | — | 352,679 | 352,679 | — |
| Write / Clients / POJO | SnakeYAML | 6,539,835 | — | 5,724,599 | 5,724,599 | 1.14× |

#### 100KB Map measurements

**Latency (µs/op, lower is better; Best 2 Mean).**

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / Map | Jackson2 | 202.624 | 201.839 | 200.480 | 201.440 | 1.01× |
| Read / Users / Map | Jackson3 | 185.130 | 188.584 | 185.071 | 184.895 | 1.00× |
| Read / Users / Map | Fastjson2 | 110.783 | — | 116.187 | 116.490 | 0.95× |
| Read / Users / Map | Gson | 323.540 | — | 298.747 | 305.337 | 1.06× |
| Read / Users / Map | JSON-P | — | — | 360.548 | 356.440 | — |
| Read / Users / Map | Simple | — | — | 381.605 | 381.381 | — |
| Read / Users / Map | SnakeYAML | 5144.178 | — | 2929.553 | 3007.330 | 1.71× |
| Write / Users / Map | Jackson2 | 180.817 | 183.035 | 166.649 | 166.924 | 1.08× |
| Write / Users / Map | Jackson3 | 194.688 | 192.359 | 167.275 | 166.392 | 1.17× |
| Write / Users / Map | Fastjson2 | 92.062 | — | 92.633 | 93.469 | 0.98× |
| Write / Users / Map | Gson | 435.824 | — | 218.610 | 220.268 | 1.98× |
| Write / Users / Map | JSON-P | — | — | 214.054 | 213.711 | — |
| Write / Users / Map | Simple | — | — | 491.865 | 493.683 | — |
| Write / Users / Map | SnakeYAML | 6141.623 | — | 2973.697 | 2969.199 | 2.07× |
| Read / Clients / Map | Jackson2 | 206.880 | 207.542 | 207.524 | 208.015 | 0.99× |
| Read / Clients / Map | Jackson3 | 189.985 | 190.030 | 189.990 | 190.770 | 1.00× |
| Read / Clients / Map | Fastjson2 | 134.818 | — | 140.324 | 139.089 | 0.97× |
| Read / Clients / Map | Gson | 379.350 | — | 353.261 | 364.736 | 1.04× |
| Read / Clients / Map | JSON-P | — | — | 370.813 | 372.060 | — |
| Read / Clients / Map | Simple | — | — | 447.748 | 451.005 | — |
| Read / Clients / Map | SnakeYAML | 4684.815 | — | 2900.096 | 2893.688 | 1.62× |
| Write / Clients / Map | Jackson2 | 179.214 | 182.015 | 162.124 | 163.238 | 1.10× |
| Write / Clients / Map | Jackson3 | 187.296 | 186.898 | 158.326 | 162.740 | 1.15× |
| Write / Clients / Map | Fastjson2 | 86.494 | — | 86.783 | 86.813 | 1.00× |
| Write / Clients / Map | Gson | 406.661 | — | 226.447 | 223.916 | 1.82× |
| Write / Clients / Map | JSON-P | — | — | 215.081 | 214.944 | — |
| Write / Clients / Map | Simple | — | — | 405.009 | 400.044 | — |
| Write / Clients / Map | SnakeYAML | 6429.458 | — | 3259.026 | 3277.878 | 1.96× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation scores across both seeds; `Native / Compiled` is the B/op ratio.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / Map | Jackson2 | 637,411 | 637,404 | 589,697 | 589,697 | 1.08× |
| Read / Users / Map | Jackson3 | 604,261 | 604,261 | 562,377 | 562,377 | 1.07× |
| Read / Users / Map | Fastjson2 | 557,157 | — | 565,629 | 566,935 | 0.98× |
| Read / Users / Map | Gson | 742,498 | — | 767,686 | 767,686 | 0.97× |
| Read / Users / Map | JSON-P | — | — | 802,939 | 802,939 | — |
| Read / Users / Map | Simple | — | — | 766,123 | 766,123 | — |
| Read / Users / Map | SnakeYAML | 9,429,418 | — | 6,984,293 | 7,058,345 | 1.34× |
| Write / Users / Map | Jackson2 | 196,110 | 196,111 | 112,061 | 112,061 | 1.75× |
| Write / Users / Map | Jackson3 | 196,125 | 196,125 | 112,085 | 112,085 | 1.75× |
| Write / Users / Map | Fastjson2 | 105,117 | — | 105,061 | 105,061 | 1.00× |
| Write / Users / Map | Gson | 513,703 | — | 216,358 | 216,358 | 2.37× |
| Write / Users / Map | JSON-P | — | — | 317,176 | 317,176 | — |
| Write / Users / Map | Simple | — | — | 218,375 | 218,376 | — |
| Write / Users / Map | SnakeYAML | 6,805,345 | — | 5,866,143 | 5,866,140 | 1.16× |
| Read / Clients / Map | Jackson2 | 512,285 | 512,299 | 479,905 | 479,905 | 1.07× |
| Read / Clients / Map | Jackson3 | 472,161 | 472,161 | 447,177 | 447,177 | 1.06× |
| Read / Clients / Map | Fastjson2 | 440,401 | — | 454,677 | 453,429 | 0.97× |
| Read / Clients / Map | Gson | 676,139 | — | 684,999 | 684,999 | 0.99× |
| Read / Clients / Map | JSON-P | — | — | 703,963 | 703,963 | — |
| Read / Clients / Map | Simple | — | — | 631,787 | 631,787 | — |
| Read / Clients / Map | SnakeYAML | 8,426,669 | — | 6,439,040 | 6,439,040 | 1.31× |
| Write / Clients / Map | Jackson2 | 209,233 | 209,233 | 120,425 | 120,425 | 1.74× |
| Write / Clients / Map | Jackson3 | 209,248 | 209,248 | 120,449 | 120,449 | 1.74× |
| Write / Clients / Map | Fastjson2 | 111,701 | — | 111,645 | 111,645 | 1.00× |
| Write / Clients / Map | Gson | 578,647 | — | 261,742 | 261,742 | 2.21× |
| Write / Clients / Map | JSON-P | — | — | 335,697 | 335,698 | — |
| Write / Clients / Map | Simple | — | — | 279,807 | 272,551 | — |
| Write / Clients / Map | SnakeYAML | 6,499,447 | — | 5,630,895 | 5,633,511 | 1.15× |

**[Complete 1/10/100KB report](binding/fabien-v152snapshot-2026-11-17.md)** · **[New JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/44011622909/artifacts/14302041930)** · **[Previous v1.5.1-SNAPSHOT report](binding/fabien-v151snapshot-2026-11-13.md)** · **[Previous artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017)**

## Baseline policy

- **Java 25** is the primary JVM baseline: Temurin JDK 25. Record the exact vendor and patch build.
- Three execution profiles: **Smoke** verifies correctness at the largest payload
  (100KB, both seeds); **Quick** diagnoses performance; **Full** establishes release
  baselines. Quick and Full execute the **same** case matrix, differing only in
  JMH fork, warmup and measurement parameters. Smoke is never a baseline.
- **Suite version: 3**: JVM Quick/Full now default to two seeds (`12345,324`).
  Earlier suite 3 runs defaulted to one seed; the current JVM baseline
  uses two seeds. Workload, generator,
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
1/10/100KB; seeds 12345 and 324 by default (**1,008 cases**). Set
`BINDING_SEEDS=12345` for an optional 504-case single-seed run.
Smoke still checks **both** seeds at 100KB and remains 336 cases.

| JMH setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 (in-process) | 1 | 3 |
| Warmup | 0 | 2 × 0.2s | 5 × 1s |
| Measurement | 1 × 0.05s | 2 × 0.2s | 5 × 1s |
| Data size | 100KB | 1/10/100KB | 1/10/100KB |
| Seeds | 12345/324 | 12345/324 | 12345/324 |
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
`.meta.md`**, for **1,008** JMH benchmark entries (including Read and Write) by default.
An optional single-seed Full (`BINDING_SEEDS=12345`) produces **504** entries.

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
| v1.5.2-SNAPSHOT (GitHub Actions; exploratory) | `555137cf7a98` | 25.0.5 | 3 | [Full 1/10/100KB report](binding/fabien-v152snapshot-2026-11-17.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/44011622909) |
| v1.5.1-SNAPSHOT (GitHub Actions; exploratory) | `13e34997b895` | 25.0.5 | 3 | [Full 1/10/100KB report](binding/fabien-v151snapshot-2026-11-13.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434) |

A controlled, same-hardware release baseline is still pending. The JVM results above
use shared GitHub-hosted runners and are exploratory. The prior v1.5.1-SNAPSHOT snapshot and its original result links remain available in the release index. See [Binding Native Image](binding-native.md)
for the separate GraalVM AOT benchmark.

## Reporting and ranking results

**Full uses 3 independent JMH forks. The default comparison metric is Best 2 Mean,
computed independently for every implementation and every exact benchmark case.**
For AverageTime (`us/op`, lower is better):

1. For a fixed backend, Read/Write, Users/Clients, POJO/Map, size, **seed**, and
   **implementation**, take the arithmetic mean of its 5 measurement iterations
   **within each fork** (`primaryMetric.rawData`: 3 arrays of 5 values).
2. Drop the **single slowest fork** (the **highest** fork mean). Average the
   remaining two fork means: `best2(seed, implementation, case)`.
3. For current **suite 3** default results (`BINDING_SEEDS=12345,324`),
   average the two seed-specific `best2` scores with **equal weight**.
   For earlier single-seed suite 3 runs (or `BINDING_SEEDS=12345`),
   report `best2` for seed `12345`. Retain individual-seed results for drilldown.

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

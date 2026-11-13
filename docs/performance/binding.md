# Binding JVM performance baseline

SJF4J Binding performance versus direct library binding (**Native**) and, for
Jackson2/3, **Blackbird**. The suite measures independent JSON/YAML
`byte[]` Read and Write operations on Fabien Users/Clients POJO and Map workloads;
fixture creation and backend setup are excluded. The primary workloads are
[`FabienReadBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienReadBenchmark.java)
and [`FabienWriteBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/FabienWriteBenchmark.java).
GraalVM AOT results are documented separately in [Binding Native Image](binding-native.md).

## Comparison dependency versions

**SJF4J version:** `v1.5.1-SNAPSHOT`. Other declared benchmark dependencies:

| Backend | Benchmark dependency versions |
| --- | --- |
| Jackson2 | Jackson Databind `2.22.3`; Blackbird `2.22.3` |
| Jackson3 | Jackson Databind `3.2.3`; Blackbird `3.2.3` |
| Fastjson2 | `2.0.66` |
| Gson | `2.13.1` |
| JSON-P | Jakarta JSON-P API `2.1.3`; Eclipse Parsson `1.1.7` |
| SnakeYAML | `2.5` |
| Simple | SJF4J built-in backend; no external comparison library |

JMH is `1.37`. Versions above are from `sjf4j-testbench/build.gradle.kts` at the [JVM baseline commit](https://github.com/sjf4j-projects/sjf4j/commit/13e34997b895880abb37cbe031e8fa6284d2cbb8).

## Code examples

Representative **Users POJO** Read and Write calls for each backend (imports
and fixture setup omitted). `input` is a prebuilt JSON `byte[]`; `value` is
a prebuilt `Users` object. **Read and Write are separate measured operations**,
not a timed roundtrip. All mappers, binders and compiled instances are
initialized outside timed methods.

### Jackson2

**Native**

```java
Users parsed = JACKSON2.readValue(input, Users.class);
byte[] encoded = JACKSON2.writeValueAsBytes(value);
```

**Blackbird**

```java
Users parsed = JACKSON2_BLACKBIRD.readValue(input, Users.class);
byte[] encoded = JACKSON2_BLACKBIRD.writeValueAsBytes(value);
```

**SJF4J Runtime**

```java
Jackson2Binder binder = new Jackson2Binder();
Users parsed = (Users) binder.readNode(input, Users.class);
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

### Jackson3

**Native**

```java
Users parsed = JACKSON3.readValue(input, Users.class);
byte[] encoded = JACKSON3.writeValueAsBytes(value);
```

**Blackbird**

```java
Users parsed = JACKSON3_BLACKBIRD.readValue(input, Users.class);
byte[] encoded = JACKSON3_BLACKBIRD.writeValueAsBytes(value);
```

**SJF4J Runtime**

```java
Jackson3Binder binder = new Jackson3Binder();
Users parsed = (Users) binder.readNode(input, Users.class);
byte[] encoded = binder.writeNodeAsBytes(value);
```

**SJF4J Compiled**

```java
@CompiledBinder(backend = Backend.JACKSON3)
interface Jackson3Binding {
    @Read Users readUsers(byte[] input);
    @Write byte[] writeUsers(Users value);
}

Jackson3Binding binder = CompiledInstances.create(Jackson3Binding.class);
Users parsed = binder.readUsers(input);
byte[] encoded = binder.writeUsers(value);
```

### Fastjson2

**Native**

```java
Users parsed = JSON.parseObject(input, Users.class);
byte[] encoded = JSON.toJSONBytes(value, JSONWriter.Feature.WriteNulls);
```

**SJF4J Runtime**

```java
Fastjson2Binder binder = new Fastjson2Binder();
Users parsed = (Users) binder.readNode(input, Users.class);
byte[] encoded = binder.writeNodeAsBytes(value);
```

**SJF4J Compiled**

```java
@CompiledBinder(backend = Backend.FASTJSON2)
interface Fastjson2Binding {
    @Read Users readUsers(byte[] input);
    @Write byte[] writeUsers(Users value);
}

Fastjson2Binding binder = CompiledInstances.create(Fastjson2Binding.class);
Users parsed = binder.readUsers(input);
byte[] encoded = binder.writeUsers(value);
```

### Gson

**Native**

```java
Users parsed = GSON.fromJson(
        new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8),
        Users.class);
byte[] encoded = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
```

**SJF4J Runtime**

```java
GsonBinder binder = new GsonBinder();
Users parsed = (Users) binder.readNode(input, Users.class);
byte[] encoded = binder.writeNodeAsBytes(value);
```

**SJF4J Compiled**

```java
@CompiledBinder(backend = Backend.GSON)
interface GsonBinding {
    @Read Users readUsers(byte[] input);
    @Write byte[] writeUsers(Users value);
}

GsonBinding binder = CompiledInstances.create(GsonBinding.class);
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

### Results — v1.5.1-SNAPSHOT (2026-11-13)

**Full JMH on Temurin 25.0.5 · 3 independent forks · Best 2 Mean · two seeds · 1/10/100KB.** All **1,008** cases completed in [GitHub Actions run #43359529434](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434). Results are exploratory because the seven backend jobs used shared runners. All latency comparisons below are **within each backend**; cross-backend absolute comparisons are not controlled.

#### 100KB POJO measurements

#### Workload models

- **Users:** ordinary JSON scalar values mapped to Java `String`, numeric primitives (`int`/`double`) and `boolean`, plus nested collections/objects. Fields such as `guid` and `registered` remain **strings**, not `UUID` or date/time objects.
- **Clients:** strongly typed values including `UUID`, `LocalDate`, `OffsetDateTime`, `BigDecimal` and enums, alongside primitive values, arrays and nested collections/objects.

**Why Clients POJO Read can differ more:** SJF4J uses `ValueFormat`/`ValueCodec` specialized paths, including fast paths for temporal-value parsing, to avoid part of the generic value-conversion overhead. These paths help explain the larger difference from Jackson Native/Blackbird on **Clients POJO Read** (notably at 100KB); the observed gain also includes other binding-path costs and must not be attributed solely to `ValueFormat`. For **Map Read**, the target is a generic Map rather than the typed `Clients` POJO, so do not infer the same benefit.

**Latency (µs/op, lower is better).** Columns are ordered Native / Blackbird / Runtime / Compiled, followed by `Native / Compiled` (a ratio of unrounded latency measurements; below 1× means Compiled is slower). The tables cover Read and Write for both Users and Clients; the [complete report](binding/fabien-v151snapshot-2026-11-13.md) includes all Read/Write, Users/Clients, POJO/Map, and 1/10/100KB combinations.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 177.595 | 158.102 | 174.718 | 153.447 | 1.16× |
| Read / Users / POJO | Jackson3 | 153.019 | 153.267 | 159.093 | 139.807 | 1.09× |
| Read / Users / POJO | Fastjson2 | 64.482 | — | 86.065 | 60.999 | 1.06× |
| Read / Users / POJO | Gson | 330.590 | — | 328.403 | 271.620 | 1.22× |
| Read / Users / POJO | JSON-P | — | — | 244.108 | 219.645 | — |
| Read / Users / POJO | Simple | — | — | 406.837 | 346.887 | — |
| Read / Users / POJO | SnakeYAML | 6,374.833 | — | 4,697.523 | 4,490.516 | 1.42× |
| Write / Users / POJO | Jackson2 | 140.128 | 120.689 | 113.374 | 109.639 | 1.28× |
| Write / Users / POJO | Jackson3 | 116.918 | 120.276 | 105.951 | 109.093 | 1.07× |
| Write / Users / POJO | Fastjson2 | 52.650 | — | 74.661 | 53.379 | 0.99× |
| Write / Users / POJO | Gson | 445.068 | — | 242.124 | 210.474 | 2.11× |
| Write / Users / POJO | JSON-P | — | — | 143.160 | 124.976 | — |
| Write / Users / POJO | Simple | — | — | 501.223 | 477.171 | — |
| Write / Users / POJO | SnakeYAML | 9,178.240 | — | 4,192.272 | 4,169.617 | 2.20× |
| Read / Clients / POJO | Jackson2 | 626.388 | 591.842 | 206.844 | 175.376 | 3.57× |
| Read / Clients / POJO | Jackson3 | 614.777 | 605.975 | 185.174 | 162.979 | 3.77× |
| Read / Clients / POJO | Fastjson2 | 75.477 | — | 102.041 | 72.469 | 1.04× |
| Read / Clients / POJO | Gson | 815.650 | — | 389.212 | 338.689 | 2.41× |
| Read / Clients / POJO | JSON-P | — | — | 343.154 | 303.289 | — |
| Read / Clients / POJO | Simple | — | — | 409.944 | 384.785 | — |
| Read / Clients / POJO | SnakeYAML | 6,757.706 | — | 4,512.503 | 4,419.195 | 1.53× |
| Write / Clients / POJO | Jackson2 | 259.067 | 241.285 | 208.808 | 184.108 | 1.41× |
| Write / Clients / POJO | Jackson3 | 233.267 | 233.214 | 207.963 | 187.128 | 1.25× |
| Write / Clients / POJO | Fastjson2 | 50.052 | — | 77.606 | 49.877 | 1.00× |
| Write / Clients / POJO | Gson | 488.746 | — | 300.794 | 274.675 | 1.78× |
| Write / Clients / POJO | JSON-P | — | — | 217.612 | 189.555 | — |
| Write / Clients / POJO | Simple | — | — | 488.860 | 459.223 | — |
| Write / Clients / POJO | SnakeYAML | 9,107.741 | — | 4,820.106 | 4,743.159 | 1.92× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation scores, averaged equally across two seeds, not the latency-selected forks. The rightmost `Native / Compiled` is the B/op ratio (below 1× means Compiled allocates more), not a latency speedup.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 330,793 | 330,057 | 327,809 | 327,809 | 1.01× |
| Read / Users / POJO | Jackson3 | 297,657 | 296,921 | 294,649 | 294,649 | 1.01× |
| Read / Users / POJO | Fastjson2 | 297,328 | — | 297,273 | 297,272 | 1.00× |
| Read / Users / POJO | Gson | 503,938 | — | 501,018 | 501,018 | 1.01× |
| Read / Users / POJO | JSON-P | — | — | 521,259 | 519,966 | — |
| Read / Users / POJO | Simple | — | — | 504,235 | 504,234 | — |
| Read / Users / POJO | SnakeYAML | 9,088,463 | — | 6,809,021 | 6,760,116 | 1.34× |
| Write / Users / POJO | Jackson2 | 199,054 | 198,318 | 112,061 | 112,061 | 1.78× |
| Write / Users / POJO | Jackson3 | 199,069 | 198,333 | 112,085 | 112,085 | 1.78× |
| Write / Users / POJO | Fastjson2 | 105,116 | — | 105,061 | 105,060 | 1.00× |
| Write / Users / POJO | Gson | 522,167 | — | 216,358 | 216,357 | 2.41× |
| Write / Users / POJO | JSON-P | — | — | 317,177 | 317,175 | — |
| Write / Users / POJO | Simple | — | — | 216,168 | 216,167 | — |
| Write / Users / POJO | SnakeYAML | 6,830,678 | — | 5,866,154 | 5,866,154 | 1.16× |
| Read / Clients / POJO | Jackson2 | 1,241,403 | 1,225,264 | 282,793 | 282,937 | 4.39× |
| Read / Clients / POJO | Jackson3 | 1,213,237 | 1,197,084 | 254,609 | 254,753 | 4.76× |
| Read / Clients / POJO | Fastjson2 | 255,213 | — | 256,333 | 254,605 | 1.00× |
| Read / Clients / POJO | Gson | 1,399,426 | — | 486,071 | 486,214 | 2.88× |
| Read / Clients / POJO | JSON-P | — | — | 590,168 | 591,671 | — |
| Read / Clients / POJO | Simple | — | — | 437,975 | 438,119 | — |
| Read / Clients / POJO | SnakeYAML | 9,134,465 | — | 6,143,448 | 6,171,549 | 1.48× |
| Write / Clients / POJO | Jackson2 | 333,145 | 316,993 | 211,793 | 211,793 | 1.57× |
| Write / Clients / POJO | Jackson3 | 333,161 | 317,008 | 211,506 | 211,505 | 1.58× |
| Write / Clients / POJO | Fastjson2 | 110,524 | — | 110,469 | 110,468 | 1.00× |
| Write / Clients / POJO | Gson | 716,743 | — | 352,870 | 352,870 | 2.03× |
| Write / Clients / POJO | JSON-P | — | — | 432,583 | 432,581 | — |
| Write / Clients / POJO | Simple | — | — | 352,679 | 352,679 | — |
| Write / Clients / POJO | SnakeYAML | 6,526,725 | — | 5,719,682 | 5,712,482 | 1.14× |

#### 100KB Map measurements

**Latency (µs/op, lower is better).** Same JDK 25 Full, two-seed and
**Best 2 Mean** methodology as the 100KB POJO measurements above.
`Native / Compiled` uses unrounded latency means.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / Map | Jackson2 | 179.721 | 178.526 | 180.376 | 184.344 | 0.97× |
| Read / Users / Map | Jackson3 | 164.460 | 166.299 | 162.066 | 165.795 | 0.99× |
| Read / Users / Map | Fastjson2 | 96.670 | — | 102.876 | 104.986 | 0.92× |
| Read / Users / Map | Gson | 319.869 | — | 302.408 | 300.575 | 1.06× |
| Read / Users / Map | JSON-P | — | — | 252.982 | 239.253 | — |
| Read / Users / Map | Simple | — | — | 380.590 | 406.804 | — |
| Read / Users / Map | SnakeYAML | 6,331.616 | — | 4,604.388 | 4,624.505 | 1.37× |
| Read / Clients / Map | Jackson2 | 194.525 | 186.087 | 182.832 | 180.787 | 1.08× |
| Read / Clients / Map | Jackson3 | 165.471 | 159.977 | 160.842 | 162.598 | 1.02× |
| Read / Clients / Map | Fastjson2 | 113.499 | — | 120.311 | 121.456 | 0.93× |
| Read / Clients / Map | Gson | 375.787 | — | 353.560 | 361.441 | 1.04× |
| Read / Clients / Map | JSON-P | — | — | 257.400 | 254.190 | — |
| Read / Clients / Map | Simple | — | — | 447.425 | 469.770 | — |
| Read / Clients / Map | SnakeYAML | 5,755.198 | — | 4,445.967 | 4,449.793 | 1.29× |
| Write / Users / Map | Jackson2 | 147.206 | 149.652 | 125.484 | 123.603 | 1.19× |
| Write / Users / Map | Jackson3 | 142.054 | 142.504 | 121.791 | 118.696 | 1.20× |
| Write / Users / Map | Fastjson2 | 87.403 | — | 90.117 | 90.733 | 0.96× |
| Write / Users / Map | Gson | 436.202 | — | 217.690 | 219.140 | 1.99× |
| Write / Users / Map | JSON-P | — | — | 140.256 | 139.939 | — |
| Write / Users / Map | Simple | — | — | 494.756 | 493.006 | — |
| Write / Users / Map | SnakeYAML | 8,970.878 | — | 4,093.452 | 4,188.591 | 2.14× |
| Write / Clients / Map | Jackson2 | 152.400 | 148.769 | 126.071 | 125.693 | 1.21× |
| Write / Clients / Map | Jackson3 | 133.452 | 133.427 | 122.447 | 120.568 | 1.11× |
| Write / Clients / Map | Fastjson2 | 82.553 | — | 84.928 | 84.667 | 0.98× |
| Write / Clients / Map | Gson | 405.338 | — | 224.567 | 222.689 | 1.82× |
| Write / Clients / Map | JSON-P | — | — | 143.572 | 146.263 | — |
| Write / Clients / Map | Simple | — | — | 405.905 | 403.961 | — |
| Write / Clients / Map | SnakeYAML | 9,509.615 | — | 4,635.415 | 4,620.500 | 2.06× |

**Allocation (B/op, lower is better).** Same untrimmed JMH allocation
scores averaged equally over the two seeds as the POJO table above;
the rightmost column denotes the B/op ratio, not a speedup.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / Map | Jackson2 | 637,397 | 637,397 | 589,697 | 589,697 | 1.08× |
| Read / Users / Map | Jackson3 | 604,261 | 604,261 | 562,377 | 562,377 | 1.07× |
| Read / Users / Map | Fastjson2 | 557,157 | — | 569,247 | 570,351 | 0.98× |
| Read / Users / Map | Gson | 742,494 | — | 767,686 | 767,686 | 0.97× |
| Read / Users / Map | JSON-P | — | — | 802,938 | 802,938 | — |
| Read / Users / Map | Simple | — | — | 766,123 | 766,123 | — |
| Read / Users / Map | SnakeYAML | 9,332,617 | — | 6,981,997 | 7,008,793 | 1.33× |
| Read / Clients / Map | Jackson2 | 512,285 | 512,299 | 479,905 | 479,905 | 1.07× |
| Read / Clients / Map | Jackson3 | 472,161 | 472,161 | 447,177 | 447,177 | 1.06× |
| Read / Clients / Map | Fastjson2 | 440,401 | — | 454,677 | 453,429 | 0.97× |
| Read / Clients / Map | Gson | 676,135 | — | 684,999 | 684,999 | 0.99× |
| Read / Clients / Map | JSON-P | — | — | 703,962 | 703,962 | — |
| Read / Clients / Map | Simple | — | — | 631,787 | 631,787 | — |
| Read / Clients / Map | SnakeYAML | 8,271,565 | — | 6,283,939 | 6,283,939 | 1.32× |
| Write / Users / Map | Jackson2 | 196,110 | 196,110 | 112,061 | 112,061 | 1.75× |
| Write / Users / Map | Jackson3 | 196,125 | 196,125 | 112,085 | 112,085 | 1.75× |
| Write / Users / Map | Fastjson2 | 105,117 | — | 105,061 | 105,061 | 1.00× |
| Write / Users / Map | Gson | 513,703 | — | 216,358 | 216,358 | 2.37× |
| Write / Users / Map | JSON-P | — | — | 317,177 | 317,177 | — |
| Write / Users / Map | Simple | — | — | 218,375 | 218,379 | — |
| Write / Users / Map | SnakeYAML | 6,805,260 | — | 5,866,153 | 5,866,154 | 1.16× |
| Write / Clients / Map | Jackson2 | 209,232 | 209,232 | 120,425 | 120,425 | 1.74× |
| Write / Clients / Map | Jackson3 | 209,247 | 209,247 | 120,449 | 120,449 | 1.74× |
| Write / Clients / Map | Fastjson2 | 111,705 | — | 111,645 | 111,645 | 1.00× |
| Write / Clients / Map | Gson | 578,647 | — | 261,742 | 261,742 | 2.21× |
| Write / Clients / Map | JSON-P | — | — | 335,697 | 335,697 | — |
| Write / Clients / Map | Simple | — | — | 274,583 | 276,295 | — |
| Write / Clients / Map | SnakeYAML | 6,499,471 | — | 5,630,905 | 5,633,521 | 1.15× |

**[Complete 1/10/100KB results, all implementations, allocation, and fork stability](binding/fabien-v151snapshot-2026-11-13.md)** · **[Original JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017)**

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
| v1.5.1-SNAPSHOT (GitHub Actions; exploratory) | `13e34997b895` | 25.0.5 | 3 | [Full 1/10/100KB report](binding/fabien-v151snapshot-2026-11-13.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434) |

A controlled, same-hardware release baseline is still pending. The JVM results above
use shared GitHub-hosted runners and are exploratory. See [Binding Native Image](binding-native.md)
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

# Mapping performance baseline

SJF4J **CompiledMapper** versus **MapStruct** for Java object mapping.
This suite deliberately compares only two compile-time generated implementations;
it does not rank unrelated runtime mappers, JSON binding, or JDBC mapping.
The primary workload is `MappingFullBenchmark`.

## Comparison dependency versions

| Component | Version | Role |
| --- | --- | --- |
| MapStruct API and annotation processor | `1.6.3` | External compile-time Java mapping comparison |
| JMH | `1.37` | Benchmark harness |

Versions above are declared in [`sjf4j-testbench/build.gradle.kts`](https://github.com/sjf4j-projects/sjf4j/blob/main/sjf4j-testbench/build.gradle.kts). Record the exact source commit for every new performance snapshot; later dependency changes do not retroactively change historical results.

## JVM — Temurin JDK 25

### Results — v1.5.1-SNAPSHOT (2026-11-12)

**Full JMH on Temurin 25.0.5 · 3 independent forks · Best 2 Mean · one runner · 1/10/100KB.**
[GitHub Actions run #43163182761](https://github.com/sjf4j-projects/sjf4j/actions/runs/43163182761) completed all **70 original suite-3 measurements**. The **68 retained measurements** from that run are reported below; the removed workload is omitted. The current benchmark suite is **version 4** (68 measurements), so this historical run should not be described as a newly executed suite-4 run. Raw JSON, execution metadata and fork-level results remain in the Actions artifacts.

**Environment:** Ubuntu 24.04 GitHub-hosted shared runner, AMD EPYC 9V74, 4 logical CPUs, no requested CPU affinity, Temurin 25.0.5, JMH 1.37, one measurement thread, 5 × 1s warmup and 5 × 1s measurement per fork. These results are **exploratory**, not a controlled hardware release baseline.

**Comparison:** MapStruct 1.6.3 versus SJF4J CompiledMapper. Both implementations map preconstructed and equivalent inputs; serialization, fixture preparation and mapper instantiation are outside the timed methods. A ratio above **1×** means SJF4J is faster; below **1×** means MapStruct is faster.

#### Order

**Sample:** Classic Order is an independently authored version of the [Arey Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark): customer, billing/shipping addresses and two products mapped to a flattened DTO. Extended Order adds `Map`/`JsonObject` fields and a size-scaled product list.

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Classic Order (Arey-style) | — | 14.40 | 13.69 | 1.05× |
| Extended Order (including products) | 1KB | 76.09 | 71.91 | 1.06× |
| Extended Order (including products) | 10KB | 681.33 | 603.12 | 1.13× |
| Extended Order (including products) | 100KB | 9,273.08 | 7,051.78 | 1.31× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Classic Order (Arey-style) | — | 120 | 120 | 1.00× |
| Extended Order (including products) | 1KB | 408 | 408 | 1.00× |
| Extended Order (including products) | 10KB | 3,576 | 3,576 | 1.00× |
| Extended Order (including products) | 100KB | 35,184 | 35,184 | 1.00× |

#### POJO

**Sample:** Small Java objects/records mapped through direct field copy, rename/compute, nested and multi-source mapping, or in-place update that ignores null values. These are fixed-size cases.

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Direct properties → record | — | 2.00 | 1.99 | 1.01× |
| Flat / rename / compute | — | 13.99 | 14.30 | 0.98× |
| Nested single-source → record | — | 3.96 | 3.55 | 1.12× |
| Nested / multi-source / record | — | 4.04 | 4.04 | 1.00× |
| Update / null ignore | — | 1.51 | 1.50 | 1.00× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Direct properties → record | — | 24 | 24 | 1.00× |
| Flat / rename / compute | — | 88 | 88 | 1.00× |
| Nested single-source → record | — | 48 | 48 | 1.00× |
| Nested / multi-source / record | — | 56 | 56 | 1.00× |
| Update / null ignore | — | 0 | 0 | — |

#### Collection

**Sample:** `Item(sku, name, quantity, price)` → `ItemDto`, measured individually and inside `ArrayList`, `LinkedList`, `Map`, or a Map containing one or eight Lists. Collection sizes scale with 1/10/100KB.

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Item → ItemDto | — | 2.53 | 2.52 | 1.00× |
| ArrayList → List<ItemDto> | 1KB | 69.88 | 55.85 | 1.25× |
| ArrayList → List<ItemDto> | 10KB | 592.57 | 345.38 | 1.72× |
| ArrayList → List<ItemDto> | 100KB | 7,121.19 | 4,927.68 | 1.45× |
| LinkedList → List<ItemDto> | 1KB | 74.12 | 81.64 | 0.91× |
| LinkedList → List<ItemDto> | 10KB | 723.11 | 762.59 | 0.95× |
| LinkedList → List<ItemDto> | 100KB | 7,187.78 | 7,718.77 | 0.93× |
| Map<String,Item> → Map<String,ItemDto> | 1KB | 136.06 | 135.88 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 10KB | 1,482.11 | 1,443.90 | 1.03× |
| Map<String,Item> → Map<String,ItemDto> | 100KB | 13,697.11 | 13,900.34 | 0.99× |
| Map with 1 large List | 1KB | 99.25 | 90.31 | 1.10× |
| Map with 1 large List | 10KB | 889.66 | 629.20 | 1.41× |
| Map with 1 large List | 100KB | 8,308.74 | 5,852.63 | 1.42× |
| Map with 8 short Lists | 1KB | 171.04 | 164.76 | 1.04× |
| Map with 8 short Lists | 10KB | 873.39 | 786.86 | 1.11× |
| Map with 8 short Lists | 100KB | 8,517.35 | 7,347.35 | 1.16× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Item → ItemDto | — | 32 | 32 | 1.00× |
| ArrayList → List<ItemDto> | 1KB | 368 | 368 | 1.00× |
| ArrayList → List<ItemDto> | 10KB | 3,536 | 3,536 | 1.00× |
| ArrayList → List<ItemDto> | 100KB | 35,144 | 35,144 | 1.00× |
| LinkedList → List<ItemDto> | 1KB | 368 | 368 | 1.00× |
| LinkedList → List<ItemDto> | 10KB | 3,536 | 3,536 | 1.00× |
| LinkedList → List<ItemDto> | 100KB | 35,144 | 35,144 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 1KB | 792 | 792 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 10KB | 8,088 | 8,088 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 100KB | 78,472 | 78,472 | 1.00× |
| Map with 1 large List | 1KB | 552 | 496 | 1.11× |
| Map with 1 large List | 10KB | 3,720 | 3,664 | 1.02× |
| Map with 1 large List | 100KB | 35,328 | 35,272 | 1.00× |
| Map with 8 short Lists | 1KB | 1,136 | 1,136 | 1.00× |
| Map with 8 short Lists | 10KB | 4,280 | 4,280 | 1.00× |
| Map with 8 short Lists | 100KB | 35,888 | 35,888 | 1.00× |

#### JSON

**Sample:** `Map<String,Object>` or `JsonObject` → `JsonTarget(sku, name, count)`. Only these three fields are mapped; extra unused fields increase the source size to 1/10/100KB.

**Latency (ns/op, lower is better).** MapStruct is compared separately with CAST and STRICT. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | CAST | STRICT | MapStruct / CAST | MapStruct / STRICT |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Map → POJO | 1KB | 5.83 | 5.84 | 6.07 | 1.00× | 0.96× |
| Map → POJO | 10KB | 5.82 | 5.81 | 6.06 | 1.00× | 0.96× |
| Map → POJO | 100KB | 5.82 | 5.84 | 6.07 | 1.00× | 0.96× |
| JsonObject → POJO | 1KB | 6.91 | 6.91 | 8.95 | 1.00× | 0.77× |
| JsonObject → POJO | 10KB | 6.88 | 6.89 | 8.95 | 1.00× | 0.77× |
| JsonObject → POJO | 100KB | 6.92 | 6.90 | 8.95 | 1.00× | 0.77× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | CAST | STRICT | MapStruct / CAST | MapStruct / STRICT |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Map → POJO | 1KB | 24 | 24 | 24 | 1.00× | 1.00× |
| Map → POJO | 10KB | 24 | 24 | 24 | 1.00× | 1.00× |
| Map → POJO | 100KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 1KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 10KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 100KB | 24 | 24 | 24 | 1.00× | 1.00× |

#### Key findings

- **POJO:** Direct, flat, nested and in-place update workloads are generally close to MapStruct. The single-source nested case reaches 1.12×; the update case is effectively equal. The update input uses an existing, non-null target.
- **Collection:** Compiled is faster for ArrayList inputs (1.72× at 10KB, 1.45× at 100KB) and Map values containing Lists (up to 1.42× at 100KB). LinkedList inputs are slower (0.91–0.95×). The generated random-access indexed loop is one relevant implementation difference; these measurements do not isolate its contribution.
- **JSON projection:** CAST is near parity with MapStruct's explicit Java casts. STRICT performs additional OBNT conversion and is not an equivalent-cost comparison (approximately 0.96× on Map and 0.77× on JsonObject). All sizes project only three fixed keys; extra unmapped fields do not imply full-document traversal.
- **Allocation:** Most paired workloads allocate the same amount. Map containing one large List saves 56 B/op with Compiled. The 1KB eight-short-Lists MapStruct result has a fork spread above 15%; inspect raw fork data before drawing a fine-grained conclusion.

**[Complete historical snapshot, fork-stability details and provenance](mapping/mapstruct-v151snapshot-2026-11-12.md)** · **[Original GitHub Actions run and artifacts](https://github.com/sjf4j-projects/sjf4j/actions/runs/43163182761)**

### Workload matrix

**15 independently observable cases**, across the same four groups, expanded to
**68 exact JMH measurements**. The JSON group compares MapStruct with both
SJF4J `RawPolicy.CAST` and `RawPolicy.STRICT`. This supersedes the eight composite cases in
the original v1 suite without dropping the classic and extended baselines.

| Group | Case | What is measured | Size | JMH entries |
| --- | --- | --- | --- | ---: |
| Order | Classic | Arey-style nested address flattening, two products | Fixed | 2 |
| Order | Extended | Nested Bean/Map/JsonObject property extraction plus product list conversion | 1/10/100KB | 6 |
| POJO | Direct | Two same-name POJO properties → record | Fixed | 2 |
| POJO | Flat | Rename + ignore + compute, retained as composite comparison | Fixed | 2 |
| POJO | Nested Profile | Single-source POJO with nested record conversion | Fixed | 2 |
| POJO | Multi-source | Existing Profile + Account → record | Fixed | 2 |
| POJO | Update | Existing target, ignoring null values | Fixed | 2 |
| Collection | Item | One Item → ItemDto, including primitive and scalar leaves | Fixed | 2 |
| Collection | Random-access List | ArrayList<Item> → List<ItemDto> | 1/10/100KB | 6 |
| Collection | Sequential List | LinkedList<Item> → List<ItemDto> | 1/10/100KB | 6 |
| Collection | Map item values | Map<String,Item> → Map<String,ItemDto> | 1/10/100KB | 6 |
| Collection | Map with one List | A single Map value containing the full List | 1/10/100KB | 6 |
| Collection | Map with eight Lists | Same total elements split into eight short Lists | 1/10/100KB | 6 |
| JSON | Map projection | Map<String,Object> → POJO: MapStruct / CAST / STRICT | 1/10/100KB | 9 |
| JSON | JsonObject projection | JsonObject → POJO: MapStruct / CAST / STRICT | 1/10/100KB | 9 |

**Why split the cases?** The original Collection List processes one large
List, while Map<String,List<Item>> processes **eight shorter Lists**, invoking
conversion multiple times. A direct comparison cannot isolate map traversal
overhead from list sizes and JIT inlining behavior. The new item, sequential
list, Map-of-item and **one-value versus eight-value** Map cases isolate those
costs.


The isolated cases are **not additive**: Item + List ≠ Extended Order.
JIT inlining, allocation and call-site profiles depend on context.

The Classic Order scores are not directly comparable with results from the
Arey repository. Empty/partial orders are checked for correctness, not timed
as separate workloads.

The "KB" sizes control deterministic approximate JSON-equivalent **source graph**
size, not a serialized input; mapping benchmarks exclude serialization entirely.
In the JSON group the additional keys grow input width but are intentionally
*unmapped*: that group measures fixed-key projection from wider source objects,
not whole-document conversion. The input shape (Map or JsonObject) is part of
each benchmark method name.

## Baseline policy

- **Current suite version: 4** (68 measurements; removed one two-implementation workload after suite 3). Changing the workload, fixture, semantics or case matrix requires a new suite version. The published 2026-11-12 run is a suite-3 historical run with the removed case excluded from its displayed tables; it is not a suite-4 rerun.

- Only **MapStruct 1.6.3** and **SJF4J CompiledMapper** are measured. Each pair
  has its own JMH method, with the same prebuilt source and equivalent output.
- Generated mapper instance creation, input fixture construction, and value
  equivalence checks occur in JMH Trial setup, never inside the measured path.
- Each invocation creates a fresh target, except **Update**, which deliberately
  updates an existing per-implementation target. The target survives invocations;
  both implementations apply the same null-ignore policy.
- Collection cases require recursively mapped DTO elements and newly allocated
  outer containers. Missing or unequal results **fail setup**.
- The JSON MapStruct comparison uses explicit Java casts for fixed-key extraction
  because JsonObject is not a JavaBean. SJF4J uses the same source and
  target records with `@MappingOptions(raws = RawPolicy.CAST)` and
  `@MappingOptions(raws = RawPolicy.STRICT)`. Both modes must agree with
  MapStruct during JMH setup. CAST performs shallow Java casts, whereas
  STRICT uses OBNT conversion. LENIENT remains validated by processor tests,
  but is not a separate performance case. These explicit adapters are not a
  claim that MapStruct automatically handles arbitrary JsonObject traversal.
- Timing reports are specific to each workload and runtime environment; comparing
  a JSON projection with a full POJO-to-DTO conversion is not meaningful.
- JDK 25 is required for an official run. Record the precise JDK patch, CPU,
  operating system, git SHA and suite version. A GitHub shared runner is
  **exploratory**, not a controlled release baseline.

## Execute

The script builds the existing JMH testbench jar once, then runs all four
groups by default. Supply a group for focused diagnostics. A manual Full
workflow runs all groups **sequentially on one benchmark runner**.

~~~bash
./scripts/benchmark-mapping.sh smoke
./scripts/benchmark-mapping.sh quick
./scripts/benchmark-mapping.sh full
./scripts/benchmark-mapping.sh full pojo
MAPPING_SKIP_BUILD=1 ./scripts/benchmark-mapping.sh quick collection
# Linux only; exact CPU model must match, otherwise fail before running.
MAPPING_EXPECT_CPU_MODEL='AMD EPYC 7763 64-Core Processor' ./scripts/benchmark-mapping.sh full
# Optional core affinity (not a CPU-model guarantee):
MAPPING_CPU_SET='1-2' ./scripts/benchmark-mapping.sh quick collection
~~~

| Setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 x 200ms | 5 x 1s |
| Measurement | 1 x 50ms | 2 x 200ms | 5 x 1s |
| Threads | 1 | 1 | 1 |
| Allocation profiler | No | Yes | Yes |
| Measurements | All 68 | All 68 | All 68 |

The script validates **method and parameter completeness**, positive finite
latency values, and for Quick/Full the full raw fork/iteration grid.
Smoke is a correctness/setup check and has no baseline status.

Output goes to `build/performance/mapping/<group>/` with the raw
`mapping-<group>.json`, `mapping-<group>.log` and
`mapping-<group>.meta.md`. Reports can be generated from raw JSON:

~~~bash
python3 scripts/analyze-mapping-benchmark.py \
  build/performance/mapping/{order,pojo,collection,json}/mapping-*.json \
  --output build/performance/mapping/report.md
~~~

## GitHub Actions Full (exploratory)

The manual [Mapping Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/mapping-full-benchmark.yml)
workflow builds one JMH jar, then runs all groups on **one runner** and uploads
one raw artifact. A separate non-benchmark job aggregates the four JSON files.
The Full workflow accepts `runner_label` (default: `ubuntu-24.04`), an
optional **exact** `cpu_model` guard and an optional `cpu_set` for Linux
`taskset` affinity. If the CPU model differs from the guard, execution fails
rather than silently treating the run as a baseline.

`ubuntu-24.04` does **not** guarantee AMD EPYC 7763 or any other exact CPU
model. The guard can reject mismatches but cannot reserve a model. Use a
dedicated `self-hosted` runner on controlled hardware for repeatable
release-grade baselines. CPU affinity limits scheduling of the JMH process
and its forks; it cannot isolate JIT/GC/compiler threads or virtualized
neighbor load. Do not set it by default without validating the effect.
Pull requests run Smoke only.

## Maintaining history in the repository

1. Publish a new complete Full run as a dated, versioned report under `docs/performance/mapping/`; do not overwrite an older report.
2. Keep source SHA, suite version, precise JDK patch, exact CPU model, runner/affinity metadata and all original JMH JSON with the report or archived artifacts.
3. Update the release index only after confirming all four groups and **68 exact entries** completed and setup equivalence checks passed. If any group failed, clearly identify the report as partial, not a complete baseline.
4. Compare release candidates on the same controlled hardware and profile. Reject comparisons spanning different CPU models and do not infer regressions from unrepeatable virtualized noise.

## Release index

| Release | Suite | Commit | Environment | Full report |
| --- | --- | --- | --- | --- |
| 1.5.1-SNAPSHOT (2026-11-12) | 3 (68 retained of 70) | [`160bb56`](https://github.com/sjf4j-projects/sjf4j/commit/160bb56ba40344315a7a639b4a606a3012ddf660) | Temurin 25.0.5 / EPYC 9V74 / shared runner | [Historical snapshot](mapping/mapstruct-v151snapshot-2026-11-12.md) |

## Reporting and ranking results

**Latency:** AverageTime, ns/op, lower is better. For Full, independently
compute the average of the five iterations within **each fork**, discard the
single slowest fork for **each exact case and implementation**, then average
the other two (**Best 2 Mean**). The two implementations do not share an
excluded fork. In each report row:

- Non-JSON groups show absolute **MapStruct** and **Compiled** latency.
- The JSON group shows three independent scores: **MapStruct**, **CAST** and
  **STRICT**, together with both **MapStruct / CAST** and
  **MapStruct / STRICT** ratios and B/op for all three implementations.
- All ratios use each implementation's own Best 2 Mean; above 1× means
  SJF4J is faster, below 1× means MapStruct is faster.
- **B/op** is the untrimmed JMH GC normalized allocation score, not an
  allocation score selected by the latency forks.
- Preserve all raw JMH forks and the original three-fork JMH mean. JMH
  scoreError does **not** describe Best 2 Mean.
- If the slowest fork differs from the fastest by more than 15%, inspect all
  three forks and flag the result as potentially unstable.

## Interpreting changes

- Do not silently average 1/10/100KB into one headline number; these are separate workloads. A JSON fixed-key projection is not equivalent to a full-object conversion.
- Treat a slowest-to-fastest fork divergence above **15%** as a reason to inspect all three raw forks and report instability, not as evidence of regression.
- **Best 2 Mean** is biased toward lower observed latency; preserve the original three-fork JMH mean and allocation for context. JMH scoreError does not describe Best 2.
- GitHub-hosted runs are exploratory, even when a single job runs the groups sequentially. A CPU guard detects mismatches but cannot guarantee isolation or a consistent load.

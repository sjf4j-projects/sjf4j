# Mapping Benchmark

SJF4J **CompiledMapper** versus **MapStruct** for Java object mapping.
This suite deliberately compares only two compile-time generated implementations;
it does not rank unrelated runtime mappers, JSON binding, or JDBC mapping.

## Workloads

**16 independently observable cases**, across the same four groups, expanded to
**70 exact JMH measurements**. The JSON group compares MapStruct with both
SJF4J `RawPolicy.CAST` and `RawPolicy.STRICT`. This supersedes the eight composite cases in
the original v1 suite without dropping the classic and extended baselines.

| Group | Case | What is measured | Size | JMH entries |
| --- | --- | --- | --- | ---: |
| Order | Classic | Arey-style nested address flattening, two products | Fixed | 2 |
| Order | Header | Nested Bean + Map/JsonObject reads, without product conversion | Fixed | 2 |
| Order | Extended | Header plus product list conversion | 1/10/100KB | 6 |
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
costs. An Order header-only case
separates property extraction from product collection conversion.

The isolated cases are **not additive**: Item + List ≠ Extended Order.
JIT inlining, allocation and call-site profiles depend on context.

**The Classic model** follows the JavaBean shape and fixed two-product fixture
from [arey/java-object-mapper-benchmark](https://github.com/arey/java-object-mapper-benchmark).
It is an independently authored implementation of that workload, not a direct copy
of third-party source code or a claim of score comparability across repositories.
The classic fixture does not change with payload size. Empty/partial order semantics
are verification concerns, not separate headline performance cases.

The "KB" sizes control deterministic approximate JSON-equivalent **source graph**
size, not a serialized input; mapping benchmarks exclude serialization entirely.
In the JSON group the additional keys grow input width but are intentionally
*unmapped*: that group measures fixed-key projection from wider source objects,
not whole-document conversion. The input shape (Map or JsonObject) is part of
each benchmark method name.

## Comparison boundary

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

## Run

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
| Measurements | All 70 | All 70 | All 70 |

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

The manual [Mapping Full Benchmark (JDK 25)](../../.github/workflows/mapping-full-benchmark.yml)
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

## Reporting

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

Do not silently average 1/10/100KB into a single headline number.
If a group cannot complete, the workflow must not present a partial
report as a complete benchmark.

## Results

**Previous v1 runs (eight composite cases; not the new v2 matrix):**
[2026-11-11 #42955426116](https://github.com/sjf4j-projects/sjf4j/actions/runs/42955426116)
and [2026-11-11 #42965730562](https://github.com/sjf4j-projects/sjf4j/actions/runs/42965730562).
Each used independent group runners with differing CPU models; they are
diagnostic historical measurements, not a stable cross-run regression baseline.

No v2 Full run has been recorded yet. Add a dated, versioned report under
`docs/performance/mapping/` after the first complete Full. Preserve raw JMH
JSON, JDK patch, exact CPU model and process affinity metadata. Reject
comparisons spanning different CPU models, and do not infer a regression from
uncontrolled virtualized noise alone.

## Release history

| Release | Suite | Commit | Environment | Full report |
| --- | --- | --- | --- | --- |
| Pending | 3 (isolated + RawPolicy) | — | JDK 25, one runner per Full | — |

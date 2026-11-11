# Mapping Benchmark

SJF4J **CompiledMapper** versus **MapStruct** for Java object mapping.
This suite deliberately compares only two compile-time generated implementations;
it does not rank unrelated runtime mappers, JSON binding, or JDBC mapping.

## Workloads

Eight logical cases across four groups, expanded to 38 exact JMH measurements:

| Group | Case | Workload | Size / variants | Measurements |
| --- | --- | --- | --- | ---: |
| Order | Classic | Arey-style Order -> DTO; nested address flattening and two mapped products | Fixed | 2 |
| Order | Extended | Order -> DTO; nested Bean, nested product collection, Map and JsonObject property reads | 1/10/100KB | 6 |
| POJO | Flat | Same-name property, rename, ignore and computed full name | Fixed | 2 |
| POJO | Nested | Two sources -> record target with nested record conversion | Fixed | 2 |
| POJO | Update | Existing mutable target; ignore null property values | Fixed | 2 |
| Collection | List | List of typed records -> List of DTO records | 1/10/100KB | 6 |
| Collection | Map | Map<String, List<Item>> -> Map<String, List<ItemDto>> | 1/10/100KB | 6 |
| JSON | Map / JsonObject | Fixed-key wide object-like source -> record; measured independently per input representation | 1/10/100KB per representation | 12 |

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
- The JSON MapStruct comparison uses explicit expressions for fixed key extraction
  because JsonObject is not a JavaBean. These are documented adapter expressions,
  not a claim of automatic MapStruct support for arbitrary JSON traversal.
- Timing reports are specific to each workload and runtime environment; comparing
  a JSON projection with a full POJO-to-DTO conversion is not meaningful.
- JDK 25 is required for an official run. Record the precise JDK patch, CPU,
  operating system, git SHA and suite version. A GitHub shared runner is
  **exploratory**, not a controlled release baseline.

## Run

The script builds the existing JMH testbench jar once, then runs all four
groups by default. Supply a group for focused diagnostics or GitHub Actions.

~~~bash
./scripts/benchmark-mapping.sh smoke
./scripts/benchmark-mapping.sh quick
./scripts/benchmark-mapping.sh full
./scripts/benchmark-mapping.sh full pojo
MAPPING_SKIP_BUILD=1 ./scripts/benchmark-mapping.sh quick collection
~~~

| Setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 x 200ms | 5 x 1s |
| Measurement | 1 x 50ms | 2 x 200ms | 5 x 1s |
| Threads | 1 | 1 | 1 |
| Allocation profiler | No | Yes | Yes |
| Cases | All 38 | All 38 | All 38 |

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
workflow builds one JMH jar, runs the four group jobs, publishes raw artifacts,
then produces a combined Markdown report. Pull requests run Smoke only.

## Reporting

**Latency:** AverageTime, ns/op, lower is better. For Full, independently
compute the average of the five iterations within **each fork**, discard the
single slowest fork for **each exact case and implementation**, then average
the other two (**Best 2 Mean**). The two implementations do not share an
excluded fork. In each report row:

- **MapStruct** and **Compiled** are absolute latency scores.
- **MapStruct / Compiled** = MapStruct Best 2 / Compiled Best 2;
  above 1x means Compiled is faster, below 1x means it is slower.
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

No Full run has been recorded for this suite yet. Add a dated, versioned
report under `docs/performance/mapping/` after the first successful Full run.
Preserve the original JMH JSON and environment metadata with the run
artifact; do not invent scores or compare separate GitHub runner jobs as
though they used the same hardware.

## Release history

| Release | Suite | Commit | Environment | Full report |
| --- | --- | --- | --- | --- |
| Pending | 1 | — | JDK 25 | — |

# Path performance baseline

SJF4J **Runtime** and **CompiledNavigator** versus **Jayway JsonPath** for
read-only JSONPath queries on prebuilt Bookstore fixtures; path compilation is
excluded from query latency and benchmarked separately. The primary workload is
[`PathFullBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/path/PathFullBenchmark.java);
`JsonPathBenchmark` is a separate exploratory suite.

## Comparison dependency versions

**SJF4J version:** `v1.5.0`.

Declared versions for the published benchmark (from the [benchmark source commit](https://github.com/sjf4j-projects/sjf4j/commit/148b26e881d6fad2fcb2a06684a779c0880c6baf)):

| Component | Version | Role |
| --- | --- | --- |
| Jayway JsonPath | `3.0.0` | External JSONPath comparison |
| Jackson2 Databind | `2.22.3` | JsonNode input for the Jackson2 JsonNode group |
| JMH | `1.37` | Benchmark harness |

## Code examples

Representative **Map / List direct lookup** for `$.store.book[1].price`
(imports and fixture setup omitted). The parsed paths, compiled navigator and
input Map are prepared outside query measurements.

**Jayway JsonPath**

```java
com.jayway.jsonpath.JsonPath path =
        com.jayway.jsonpath.JsonPath.compile("$.store.book[1].price");
Object price = path.read(map, Configuration.defaultConfiguration());
```

**SJF4J Runtime**

```java
JsonPath path = JsonPath.parse("$.store.book[1].price");
Object price = path.getNode(map);
```

**SJF4J Compiled**

```java
@CompiledNavigator(fallback = true)
interface MapPaths {
    @GetByPath("$.store.book[1].price")
    Object direct(Map<String, Object> root);
}

MapPaths paths = CompiledInstances.create(MapPaths.class);
Object price = paths.direct(map);
```

JSONPath expression parsing is measured separately; the query latency
benchmarks reuse pre-parsed paths. See
[`PathFullBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/path/PathFullBenchmark.java)
for wildcard, filter, descendant, slice and other input representations.

## JVM — Temurin JDK 25

### Results — v1.5.0 (2026-11-11)
**JDK 25.0.5 · JMH · 3 forks · Best 2 Mean · ns/op (lower is better)**

The figures below are measured query latency on pre-parsed data and precompiled paths. Each latency cell reports one absolute value. Speedup columns are ratios of the indicated baseline to the candidate (>1× is faster).

#### Map / List

| Query | JSONPath | Size | Jayway | Runtime | Compiled | Jayway / Runtime | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 296.59 | 59.72 | 11.67 | 4.97× | 5.12× |
| Direct | `$.store.book[1].price` | 10KB | 315.12 | 60.64 | 13.44 | 5.20× | 4.51× |
| Direct | `$.store.book[1].price` | 100KB | 366.31 | 65.53 | 13.45 | 5.59× | 4.87× |
| Wildcard | `$.store.book[*].author` | 1KB | 1,975.57 | 388.59 | 188.96 | 5.08× | 2.06× |
| Wildcard | `$.store.book[*].author` | 10KB | 17,862.32 | 3,739.24 | 1,635.78 | 4.78× | 2.29× |
| Wildcard | `$.store.book[*].author` | 100KB | 219,426.21 | 38,884.79 | 22,580.89 | 5.64× | 1.72× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 3,954.91 | 618.40 | 490.30 | 6.40× | 1.26× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 35,399.81 | 6,085.01 | 5,288.24 | 5.82× | 1.15× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 412,237.70 | 71,869.80 | 62,840.77 | 5.74× | 1.14× |
| Descendant | `$..price` | 1KB | 3,169.49 | 905.67 | 447.23 | 3.50× | 2.03× |
| Descendant | `$..price` | 10KB | 32,864.12 | 9,598.88 | 5,079.61 | 3.42× | 1.89× |
| Descendant | `$..price` | 100KB | 416,657.58 | 130,092.61 | 90,846.61 | 3.20× | 1.43× |
| Slice | `$.store.book[1:4].title` | 1KB | 819.46 | 140.66 | 61.32 | 5.83× | 2.29× |
| Slice | `$.store.book[1:4].title` | 10KB | 782.04 | 140.86 | 74.21 | 5.55× | 1.90× |
| Slice | `$.store.book[1:4].title` | 100KB | 841.49 | 146.56 | 70.72 | 5.74× | 2.07× |

#### Jackson2 JsonNode

| Query | JSONPath | Size | Jayway | Runtime | Compiled | Jayway / Runtime | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 310.31 | 125.79 | 28.21 | 2.47× | 4.46× |
| Direct | `$.store.book[1].price` | 10KB | 327.74 | 158.52 | 29.71 | 2.07× | 5.34× |
| Direct | `$.store.book[1].price` | 100KB | 355.26 | 161.08 | 29.68 | 2.21× | 5.43× |
| Wildcard | `$.store.book[*].author` | 1KB | 2,152.24 | 646.85 | 370.64 | 3.33× | 1.75× |
| Wildcard | `$.store.book[*].author` | 10KB | 22,324.50 | 5,941.88 | 4,918.15 | 3.76× | 1.21× |
| Wildcard | `$.store.book[*].author` | 100KB | 234,799.94 | 69,980.50 | 53,248.32 | 3.36× | 1.31× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 3,800.49 | 1,242.77 | 1,065.95 | 3.06× | 1.17× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 38,175.82 | 12,573.21 | 11,820.30 | 3.04× | 1.06× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 388,883.71 | 128,431.05 | 120,468.52 | 3.03× | 1.07× |
| Descendant | `$..price` | 1KB | 3,521.44 | 1,946.49 | 1,622.74 | 1.81× | 1.20× |
| Descendant | `$..price` | 10KB | 36,723.25 | 21,647.48 | 18,128.49 | 1.70× | 1.19× |
| Descendant | `$..price` | 100KB | 401,328.15 | 219,489.25 | 178,817.86 | 1.83× | 1.23× |
| Slice | `$.store.book[1:4].title` | 1KB | 781.04 | 258.09 | 157.58 | 3.03× | 1.64× |
| Slice | `$.store.book[1:4].title` | 10KB | 785.61 | 301.40 | 232.74 | 2.61× | 1.30× |
| Slice | `$.store.book[1:4].title` | 100KB | 833.20 | 303.61 | 221.58 | 2.74× | 1.37× |

#### POJO

| Query | JSONPath | Size | Runtime | Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 147.11 | 2.74 | 53.68× |
| Direct | `$.store.book[1].price` | 10KB | 139.96 | 2.74 | 51.14× |
| Direct | `$.store.book[1].price` | 100KB | 151.83 | 2.75 | 55.31× |
| Wildcard | `$.store.book[*].author` | 1KB | 842.69 | 65.29 | 12.91× |
| Wildcard | `$.store.book[*].author` | 10KB | 7,818.70 | 470.39 | 16.62× |
| Wildcard | `$.store.book[*].author` | 100KB | 63,425.47 | 4,687.53 | 13.53× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 1,323.10 | 815.76 | 1.62× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 11,222.86 | 8,513.69 | 1.32× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 106,384.23 | 85,613.23 | 1.24× |
| Descendant | `$..price` | 1KB | 1,737.44 | 1,268.45 | 1.37× |
| Descendant | `$..price` | 10KB | 16,446.54 | 11,725.14 | 1.40× |
| Descendant | `$..price` | 100KB | 160,821.66 | 121,166.02 | 1.33× |
| Slice | `$.store.book[1:4].title` | 1KB | 305.46 | 23.14 | 13.20× |
| Slice | `$.store.book[1:4].title` | 10KB | 324.24 | 24.32 | 13.33× |
| Slice | `$.store.book[1:4].title` | 100KB | 290.09 | 22.71 | 12.77× |

#### JSONPath Parsing

Measures parsing JSONPath expressions into reusable query objects: `JsonPath.parse()` for SJF4J and `JsonPath.compile()` for Jayway. This excludes document parsing and query execution.

| JSONPath | Jayway | SJF4J | Jayway / SJF4J |
| --- | ---: | ---: | ---: |
| `$.store.book[1].price` | 271.37 | 101.28 | 2.68× |
| `$.store.bicycle.color` | 185.80 | 97.59 | 1.90× |
| `$.store.book[*].author` | 218.01 | 103.15 | 2.11× |
| `$..price` | 84.01 | 47.79 | 1.76× |
| `$.store.book[?(@.price > 10)].title` | 1,005.83 | 392.75 | 2.56× |
| `$.store.book[0,2].title` | 418.11 | 143.26 | 2.92× |
| `$.store.book[1:4].title` | 306.70 | 122.24 | 2.51× |

#### Allocation — selected 100KB queries

**B/op**, from the original, untrimmed JMH `gc.alloc.rate.norm` metric (lower is better). Each ratio is baseline allocation divided by candidate allocation (>1× means less allocation for the candidate).

| Input | Query | Jayway | Runtime | Compiled | Jayway / Runtime | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Map / List | Wildcard | 608,962 | 15,104 | 15,072 | 40.32× | 1.00× |
| Map / List | Filter | 1,038,699 | 6,858 | 6,808 | 151.45× | 1.01× |
| Map / List | Descendant | 980,859 | 64,407 | 44,617 | 15.23× | 1.44× |
| Jackson2 JsonNode | Wildcard | 1,159,602 | 15,113 | 15,072 | 76.73× | 1.00× |
| Jackson2 JsonNode | Filter | 1,881,523 | 6,855 | 6,809 | 274.47× | 1.01× |
| Jackson2 JsonNode | Descendant | 1,639,971 | 64,402 | 44,617 | 25.46× | 1.44× |
| POJO | Wildcard | — | 15,112 | 15,024 | — | 1.01× |
| POJO | Filter | — | 6,859 | 6,785 | — | 1.01× |
| POJO | Descendant | — | 64,401 | 44,617 | — | 1.44× |

#### Summary

At the tested 1/10/100KB sizes, SJF4J Runtime was faster than Jayway in **30 of 30** comparable Map and JsonNode query cases. CompiledNavigator was faster than Runtime in **45 of 45** comparable Map, JsonNode, and POJO query cases.

All **134 JMH cases** completed: 45 Map, 45 JsonNode, 30 POJO and 14 Parse. All five query types now have CompiledNavigator implementations for all three data representations. Compiled Descendant uses generated traversal rather than full-path runtime fallback; Compiled Filter uses a cached `FilterExpr` predicate, enabled with `fallback=true`.

These results are workload-specific; compare implementations within the same input type and runner job. Map/POJO/Parse and JsonNode used different GitHub-hosted runner CPU models.

**[Full versioned report and methodology](path/bookstore-v150-2026-11-11.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42960489260)**

## Baseline policy

- Deterministic Bookstore fixture; actual JSON sizes: **1,021 / 10,267 / 102,395 bytes** (12 / 123 / 1,229 books), nominal 1/10/100KB; approximately 30% of records satisfy the Filter predicate.
- All data instances and query expressions are created outside timed query operations, with result-equivalence validation before measurement.
- GitHub-hosted Ubuntu 24.04 x86-64 runners with Temurin OpenJDK 25.0.5+8: Map/POJO/Parse used AMD EPYC 7763, JsonNode used AMD EPYC 9V74. Shared runners are exploratory, not a hardware-independent performance guarantee.
- Source commit: [`148b26e881d6`](https://github.com/sjf4j-projects/sjf4j/commit/148b26e881d6fad2fcb2a06684a779c0880c6baf).

- **Measured boundary:** query execution uses pre-parsed data and precompiled paths; the Parse group measures expression compilation only.
- **Comparable inputs:** Jayway comparisons cover Map / List and Jackson2 JsonNode, not POJO. Runtime / Compiled comparisons also cover POJO.
- Smoke validates setup and equivalence, Quick is diagnostic, and Full is the benchmark profile. Neither Smoke nor GitHub-hosted timings constitute a controlled release baseline.

## Execute

JDK 25 is required. Without a group argument, the script runs all four groups (Map, JsonNode, POJO and Parse) and builds the JMH jar once.

```bash
./scripts/benchmark-path.sh smoke
./scripts/benchmark-path.sh quick
./scripts/benchmark-path.sh full
# Optional focused groups:
./scripts/benchmark-path.sh full map
./scripts/benchmark-path.sh full jsonnode
./scripts/benchmark-path.sh full pojo
./scripts/benchmark-path.sh full parse
```

| JMH setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 × 200ms | 5 × 1s |
| Measurement | 1 × 50ms | 2 × 200ms | 5 × 1s |
| Input groups | All by default | All by default | All by default |
| GC profiler | No | Yes | Yes |

Quick/Full outputs are under `build/performance/path/<group>/`, with a JMH JSON file and metadata for each group. Set `PATH_SKIP_BUILD=1` to reuse the built JMH jar for a focused run. A complete run covers 45 Map + 45 JsonNode + 30 POJO + 14 Parse = **134 cases**.

## GitHub Actions Full (exploratory)

[Path Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/path-full-benchmark.yml) → **Run workflow**.

The workflow builds the JMH jar once, runs Map / JsonNode / POJO / Parse in separate GitHub-hosted Ubuntu 24.04 jobs, and uploads raw per-group artifacts plus a combined report. Pull requests run Smoke checks, not Full JMH measurements.

Historical Map/POJO/Parse results used AMD EPYC 7763, while JsonNode used AMD EPYC 9V74. Compare values only within the same group and execution environment; shared runner scheduling is not controlled.

## Maintaining history in the repository

1. Run the complete Full suite with a fixed source commit and record the precise JDK, OS and CPU models.
2. Save a dated, versioned report under `docs/performance/path/`, including source SHA, workflow link, JMH settings and input sizes. Preserve the original raw JMH JSON and environment metadata with the run artifacts.
3. Update the release index below. Never replace the previous performance snapshot with newer measurements.
4. For a regression investigation, compare matching queries and input shapes on equivalent hardware, and inspect all fork means and allocation alongside Best 2 Mean.

## Release index

| Release | Commit | JDK | Results |
| --- | --- | --- | --- |
| v1.5.0 (GitHub Actions; exploratory) | [`148b26e881d6`](https://github.com/sjf4j-projects/sjf4j/commit/148b26e881d6fad2fcb2a06684a779c0880c6baf) | 25.0.5+8 | [Full 1/10/100KB report](path/bookstore-v150-2026-11-11.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42960489260) |

## Reporting and ranking results

**Full uses 3 independent forks.** Use per-case **Best 2 Mean**, calculated independently for each benchmark method, query, data representation and size:

- AverageTime, 1 thread, 3 independent forks, 5 × 1s warmup and 5 × 1s measurement, JMH allocation profiler.
- **Best 2 Mean**: average five iterations within each fork; discard each case's slowest fork independently and average the remaining two. The original JMH score/error and B/op are untrimmed; JMH error is not a Best 2 Mean confidence interval.

- Report latency in **ns/op** (lower is better); each table cell contains an absolute score. Compute speedup from unrounded baseline / candidate latency, so **above 1×** means the candidate is faster.
- Present Jayway, Runtime and Compiled as distinct values when supported; never fill an unsupported POJO/Jayway combination with a made-up score.
- Preserve the original three-fork JMH score, error and B/op; do not attach the original JMH error to Best 2 Mean.

## Interpreting changes

- Best 2 Mean is a selected lower-latency estimate, not a random-fork mean or confidence interval. Inspect all three forks when interpreting small differences.
- Avoid treating different CPU models, JDK builds, input shapes or benchmark profiles as controlled A/B tests.
- Track both latency and **B/op**; a faster score with substantially higher allocation warrants investigation.

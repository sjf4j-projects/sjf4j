# Path performance baseline

SJF4J **Runtime** and **CompiledNavigator** versus **Jayway JsonPath** for
read-only JSONPath queries on prebuilt Bookstore fixtures; path compilation is
excluded from query latency and benchmarked separately. The primary workload is
[`PathFullBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/path/PathFullBenchmark.java);
`JsonPathBenchmark` is a separate exploratory suite.

## Comparison dependency versions

**SJF4J version:** `v1.5.3-SNAPSHOT`.

Declared versions for the published benchmark (from the [benchmark source commit](https://github.com/sjf4j-projects/sjf4j/commit/857c5872ddfc56679a3cf3f635202ac2b90e7b46)):

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
JsonPath path = JsonPath.compile("$.store.book[1].price");
Object price = path.read(map, Configuration.defaultConfiguration());
```

**SJF4J Runtime**
```java
JsonPath path = JsonPath.parse("$.store.book[1].price");
Object price = path.getNode(map);
```

**SJF4J Compiled**
```java
@CompiledNavigator
interface MapPaths {
    MapPaths INSTANCE = CompiledInstances.create(MapPaths.class);
    
    @GetByPath("$.store.book[1].price")
    Object direct(Map<String, Object> root);
}

Object price = MapPaths.INSTANCE.direct(map);
```

JSONPath expression parsing is measured separately; the query latency
benchmarks reuse pre-parsed paths. See
[`PathFullBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/path/PathFullBenchmark.java)
for wildcard, filter, descendant, slice and other input representations.

## JVM — Temurin JDK 25

### Results — v1.5.3-SNAPSHOT (2026-11-28)
**JDK 25.0.5.1 · JMH · 3 forks · Best 2 Mean · ns/op (lower is better)**

The figures below are measured query latency on pre-parsed data and precompiled paths. Each latency cell reports one absolute value. Speedup columns are ratios of the indicated baseline to the candidate (>1× is faster).

#### Map / List

| Query | JSONPath | Size | Jayway | Runtime | Compiled | Jayway / Runtime | Jayway / Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 191.46 | 39.20 | 8.10 | 4.88× | 23.64× | 4.84× |
| Direct | `$.store.book[1].price` | 10KB | 197.71 | 39.87 | 8.85 | 4.96× | 22.33× | 4.50× |
| Direct | `$.store.book[1].price` | 100KB | 205.48 | 41.39 | 8.87 | 4.96× | 23.17× | 4.67× |
| Wildcard | `$.store.book[*].author` | 1KB | 1,175.63 | 249.01 | 117.52 | 4.72× | 10.00× | 2.12× |
| Wildcard | `$.store.book[*].author` | 10KB | 12,302.10 | 2,448.00 | 1,051.13 | 5.03× | 11.70× | 2.33× |
| Wildcard | `$.store.book[*].author` | 100KB | 127,529.18 | 22,910.99 | 14,964.74 | 5.57× | 8.52× | 1.53× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 2,300.54 | 391.46 | 175.28 | 5.88× | 13.13× | 2.23× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 22,188.07 | 4,200.12 | 2,508.56 | 5.28× | 8.84× | 1.67× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 221,703.44 | 43,095.69 | 28,660.73 | 5.14× | 7.74× | 1.50× |
| Descendant | `$..price` | 1KB | 1,777.59 | 564.48 | 255.44 | 3.15× | 6.96× | 2.21× |
| Descendant | `$..price` | 10KB | 18,577.76 | 5,942.79 | 3,141.63 | 3.13× | 5.91× | 1.89× |
| Descendant | `$..price` | 100KB | 194,268.93 | 59,564.58 | 32,316.16 | 3.26× | 6.01× | 1.84× |
| Slice | `$.store.book[1:4].title` | 1KB | 441.69 | 96.56 | 40.05 | 4.57× | 11.03× | 2.41× |
| Slice | `$.store.book[1:4].title` | 10KB | 451.16 | 99.34 | 48.00 | 4.54× | 9.40× | 2.07× |
| Slice | `$.store.book[1:4].title` | 100KB | 470.46 | 103.40 | 46.43 | 4.55× | 10.13× | 2.23× |

#### Jackson2 JsonNode

| Query | JSONPath | Size | Jayway | Runtime | Compiled | Jayway / Runtime | Jayway / Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 238.90 | 105.49 | 25.65 | 2.26× | 9.31× | 4.11× |
| Direct | `$.store.book[1].price` | 10KB | 247.14 | 127.36 | 28.72 | 1.94× | 8.61× | 4.43× |
| Direct | `$.store.book[1].price` | 100KB | 266.49 | 134.68 | 29.06 | 1.98× | 9.17× | 4.63× |
| Wildcard | `$.store.book[*].author` | 1KB | 1,709.13 | 520.31 | 289.96 | 3.28× | 5.89× | 1.79× |
| Wildcard | `$.store.book[*].author` | 10KB | 18,402.11 | 4,739.04 | 3,699.90 | 3.88× | 4.97× | 1.28× |
| Wildcard | `$.store.book[*].author` | 100KB | 190,322.40 | 53,467.58 | 41,766.36 | 3.56× | 4.56× | 1.28× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 2,944.95 | 1,075.47 | 667.96 | 2.74× | 4.41× | 1.61× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 31,445.18 | 10,645.11 | 8,842.48 | 2.95× | 3.56× | 1.20× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 330,910.36 | 103,481.53 | 88,930.16 | 3.20× | 3.72× | 1.16× |
| Descendant | `$..price` | 1KB | 2,445.45 | 1,621.66 | 1,298.56 | 1.51× | 1.88× | 1.25× |
| Descendant | `$..price` | 10KB | 28,796.47 | 17,065.37 | 13,209.54 | 1.69× | 2.18× | 1.29× |
| Descendant | `$..price` | 100KB | 299,105.93 | 170,982.23 | 139,516.61 | 1.75× | 2.14× | 1.23× |
| Slice | `$.store.book[1:4].title` | 1KB | 603.25 | 206.03 | 125.37 | 2.93× | 4.81× | 1.64× |
| Slice | `$.store.book[1:4].title` | 10KB | 602.00 | 251.09 | 172.11 | 2.40× | 3.50× | 1.46× |
| Slice | `$.store.book[1:4].title` | 100KB | 630.86 | 260.22 | 165.04 | 2.42× | 3.82× | 1.58× |

#### POJO

| Query | JSONPath | Size | Runtime | Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 143.44 | 2.73 | 52.49× |
| Direct | `$.store.book[1].price` | 10KB | 147.82 | 2.73 | 54.10× |
| Direct | `$.store.book[1].price` | 100KB | 148.10 | 2.74 | 54.11× |
| Wildcard | `$.store.book[*].author` | 1KB | 811.87 | 65.35 | 12.42× |
| Wildcard | `$.store.book[*].author` | 10KB | 7,556.17 | 473.35 | 15.96× |
| Wildcard | `$.store.book[*].author` | 100KB | 66,429.02 | 4,795.42 | 13.85× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 1,232.77 | 89.41 | 13.79× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 10,869.60 | 1,522.43 | 7.14× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 110,072.63 | 15,362.36 | 7.17× |
| Descendant | `$..price` | 1KB | 1,697.72 | 1,222.15 | 1.39× |
| Descendant | `$..price` | 10KB | 16,281.65 | 11,812.68 | 1.38× |
| Descendant | `$..price` | 100KB | 162,909.61 | 128,770.82 | 1.27× |
| Slice | `$.store.book[1:4].title` | 1KB | 290.81 | 23.22 | 12.52× |
| Slice | `$.store.book[1:4].title` | 10KB | 296.45 | 24.43 | 12.13× |
| Slice | `$.store.book[1:4].title` | 100KB | 272.99 | 23.08 | 11.83× |

#### JSONPath Parsing

Measures parsing JSONPath expressions into reusable query objects: `JsonPath.parse()` for SJF4J and `JsonPath.compile()` for Jayway. This excludes document parsing and query execution.

| JSONPath | Jayway | SJF4J | Jayway / SJF4J |
| --- | ---: | ---: | ---: |
| `$.store.book[1].price` | 157.95 | 67.77 | 2.33× |
| `$.store.bicycle.color` | 112.01 | 63.39 | 1.77× |
| `$.store.book[*].author` | 129.71 | 65.10 | 1.99× |
| `$..price` | 49.44 | 30.06 | 1.64× |
| `$.store.book[?(@.price > 10)].title` | 570.25 | 205.60 | 2.77× |
| `$.store.book[0,2].title` | 227.07 | 98.35 | 2.31× |
| `$.store.book[1:4].title` | 177.12 | 84.35 | 2.10× |

#### Allocation — selected 100KB queries

**B/op**, from the original, untrimmed JMH `gc.alloc.rate.norm` metric (lower is better). Each ratio is baseline allocation divided by candidate allocation (>1× means less allocation for the candidate).

| Input | Query | Jayway | Runtime | Compiled | Jayway / Runtime | Jayway / Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Map / List | Wildcard | 579,465 | 15,104 | 15,072 | 38.36× | 38.45× | 1.00× |
| Map / List | Filter | 1,000,346 | 6,848 | 6,808 | 146.07× | 146.93× | 1.01× |
| Map / List | Descendant | 951,361 | 64,406 | 44,616 | 14.77× | 21.32× | 1.44× |
| Jackson2 JsonNode | Wildcard | 1,189,097 | 15,107 | 15,072 | 78.71× | 78.89× | 1.00× |
| Jackson2 JsonNode | Filter | 1,843,170 | 6,860 | 6,809 | 268.69× | 270.71× | 1.01× |
| Jackson2 JsonNode | Descendant | 1,757,954 | 64,401 | 44,617 | 27.30× | 39.40× | 1.44× |
| POJO | Wildcard | — | 15,112 | 15,024 | — | — | 1.01× |
| POJO | Filter | — | 6,859 | 6,760 | — | — | 1.01× |
| POJO | Descendant | — | 64,401 | 44,617 | — | — | 1.44× |

#### Summary

At the tested 1/10/100KB sizes, SJF4J Runtime was faster than Jayway in **30 of 30** comparable Map and JsonNode query cases. CompiledNavigator was faster than Runtime in **45 of 45** comparable Map, JsonNode, and POJO query cases.

All **134 JMH cases** completed: 45 Map, 45 JsonNode, 30 POJO and 14 Parse. All five query types have CompiledNavigator implementations for all three data representations. Supported Filter predicates compile directly; Descendant traversal is generated without full-path runtime fallback. The Filter workload is numeric (`@.price > 10`); this run does not measure regex function execution.

These are exploratory hosted-runner measurements. Map, JsonNode and Parse used Intel Xeon 6975P-C; POJO used AMD EPYC 7763. Compare implementations within the same input type and runner job. The 1KB POJO compiled Filter has unstable fork means; its 13.79× ratio should not be treated as a stable speedup.

**[Full versioned report and methodology](path/2026-11-28-v1.5.3-snapshot.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/46130448820)**

## Baseline policy

- Deterministic Bookstore fixture; actual JSON sizes: **1,021 / 10,267 / 102,395 bytes** (12 / 123 / 1,229 books), nominal 1/10/100KB; approximately 30% of records satisfy the Filter predicate.
- All data instances and query expressions are created outside timed query operations, with result-equivalence validation before measurement.
- GitHub-hosted Ubuntu 24.04 x86-64 runners with Temurin OpenJDK 25.0.5.1+2: Map/JsonNode/Parse used Intel Xeon 6975P-C, POJO used AMD EPYC 7763. Shared runners are exploratory, not a hardware-independent performance guarantee.
- Source commit: [`857c5872ddfc`](https://github.com/sjf4j-projects/sjf4j/commit/857c5872ddfc56679a3cf3f635202ac2b90e7b46) (PR #480).

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

See the CPU baseline index for matching group/CPU results. Shared runner scheduling is not controlled.

## Maintaining history in the repository

1. Run the complete Full suite with a fixed source commit and record the precise JDK, OS and CPU models.
2. Save a dated, versioned report under `docs/performance/path/`, including source SHA, workflow link, JMH settings and input sizes. Preserve the original raw JMH JSON and environment metadata with the run artifacts.
3. Update the release index below. Never replace the previous performance snapshot with newer measurements.
4. For a regression investigation, compare matching queries and input shapes on equivalent hardware, and inspect all fork means and allocation alongside Best 2 Mean.

## CPU baseline index

| Group | CPU | Latest accepted report |
| --- | --- | --- |
| Map / Parse | AMD EPYC 7763 | [2026-11-11](path/2026-11-11-v1.5.0.md) |
| POJO | AMD EPYC 7763 | [2026-11-28](path/2026-11-28-v1.5.3-snapshot.md) |
| JsonNode | AMD EPYC 9V74 | [2026-11-11](path/2026-11-11-v1.5.0.md) |
| Map / JsonNode / Parse | Intel Xeon 6975P-C | [2026-11-28](path/2026-11-28-v1.5.3-snapshot.md) |

## Release index

| Release | Commit | JDK | Results |
| --- | --- | --- | --- |
| v1.5.3-SNAPSHOT (GitHub Actions; exploratory) | [`857c5872ddfc`](https://github.com/sjf4j-projects/sjf4j/commit/857c5872ddfc56679a3cf3f635202ac2b90e7b46) | 25.0.5.1+2 | [Full 1/10/100KB report](path/2026-11-28-v1.5.3-snapshot.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/46130448820) |
| v1.5.0 (GitHub Actions; exploratory) | [`148b26e881d6`](https://github.com/sjf4j-projects/sjf4j/commit/148b26e881d6fad2fcb2a06684a779c0880c6baf) | 25.0.5+8 | [Full 1/10/100KB report](path/2026-11-11-v1.5.0.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42960489260) |

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

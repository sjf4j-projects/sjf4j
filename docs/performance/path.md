# Path Benchmark

SJF4J JSONPath performance compared with Jayway JsonPath 3.0.0.

## Results — v1.5.0 (2026-11-09)

**JDK 25.0.5 · JMH · 3 forks · Best 2 Mean · ns/op (lower is better)**

The figures below are measured query latency on pre-parsed data and precompiled paths. Each latency cell reports one absolute value. Speedup columns are ratios of the indicated baseline to the candidate (>1× is faster).

### Map / List

| Query | JSONPath | Size | Jayway | Runtime | Compiled | Jayway / Runtime | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 294.61 | 54.50 | 11.69 | 5.41× | 4.66× |
| Direct | `$.store.book[1].price` | 10KB | 315.07 | 57.68 | 13.47 | 5.46× | 4.28× |
| Direct | `$.store.book[1].price` | 100KB | 378.44 | 51.87 | 13.50 | 7.30× | 3.84× |
| Wildcard | `$.store.book[*].author` | 1KB | 1,940.73 | 382.31 | — | 5.08× | — |
| Wildcard | `$.store.book[*].author` | 10KB | 17,905.06 | 3,751.75 | — | 4.77× | — |
| Wildcard | `$.store.book[*].author` | 100KB | 215,715.85 | 39,246.94 | — | 5.50× | — |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 3,916.91 | 598.00 | — | 6.55× | — |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 34,863.80 | 5,943.57 | — | 5.87× | — |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 393,750.23 | 74,114.65 | — | 5.31× | — |
| Descendant | `$..price` | 1KB | 3,264.81 | 903.47 | — | 3.61× | — |
| Descendant | `$..price` | 10KB | 32,092.38 | 9,592.25 | — | 3.35× | — |
| Descendant | `$..price` | 100KB | 399,560.07 | 133,074.85 | — | 3.00× | — |
| Slice | `$.store.book[1:4].title` | 1KB | 800.96 | 139.34 | — | 5.75× | — |
| Slice | `$.store.book[1:4].title` | 10KB | 780.98 | 135.64 | — | 5.76× | — |
| Slice | `$.store.book[1:4].title` | 100KB | 846.97 | 135.37 | — | 6.26× | — |

Map Direct has an additional Runtime / Compiled comparison. Only Map Direct has a CompiledNavigator result; generated multi-target traversal for arbitrary nested `Map<String, Object>` is not supported.

### Jackson2 JsonNode

| Query | JSONPath | Size | Jayway | Runtime | Jayway / Runtime |
| --- | --- | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 361.97 | 145.99 | 2.48× |
| Direct | `$.store.book[1].price` | 10KB | 432.13 | 182.74 | 2.36× |
| Direct | `$.store.book[1].price` | 100KB | 487.85 | 194.43 | 2.51× |
| Wildcard | `$.store.book[*].author` | 1KB | 2,784.55 | 783.74 | 3.55× |
| Wildcard | `$.store.book[*].author` | 10KB | 26,973.89 | 7,003.15 | 3.85× |
| Wildcard | `$.store.book[*].author` | 100KB | 307,876.56 | 93,903.32 | 3.28× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 5,343.50 | 1,661.02 | 3.22× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 50,930.10 | 16,420.06 | 3.10× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 536,955.46 | 165,731.31 | 3.24× |
| Descendant | `$..price` | 1KB | 4,764.61 | 2,525.13 | 1.89× |
| Descendant | `$..price` | 10KB | 50,281.80 | 24,915.09 | 2.02× |
| Descendant | `$..price` | 100KB | 556,192.29 | 296,662.76 | 1.87× |
| Slice | `$.store.book[1:4].title` | 1KB | 1,046.29 | 307.13 | 3.41× |
| Slice | `$.store.book[1:4].title` | 10KB | 1,018.75 | 374.39 | 2.72× |
| Slice | `$.store.book[1:4].title` | 100KB | 1,113.73 | 371.68 | 3.00× |

### POJO

| Query | JSONPath | Size | Runtime | Compiled | Runtime / Compiled |
| --- | --- | ---: | ---: | ---: | ---: |
| Direct | `$.store.book[1].price` | 1KB | 102.33 | 2.14 | 47.74× |
| Direct | `$.store.book[1].price` | 10KB | 111.07 | 2.14 | 51.88× |
| Direct | `$.store.book[1].price` | 100KB | 125.15 | 2.16 | 57.95× |
| Wildcard | `$.store.book[*].author` | 1KB | 642.24 | 53.60 | 11.98× |
| Wildcard | `$.store.book[*].author` | 10KB | 6,236.60 | 405.33 | 15.39× |
| Wildcard | `$.store.book[*].author` | 100KB | 56,173.63 | 4,652.62 | 12.07× |
| Filter | `$.store.book[?(@.price > 10)].title` | 1KB | 937.86 | 595.77 | 1.57× |
| Filter | `$.store.book[?(@.price > 10)].title` | 10KB | 8,842.98 | 6,720.58 | 1.32× |
| Filter | `$.store.book[?(@.price > 10)].title` | 100KB | 82,248.18 | 67,196.53 | 1.22× |
| Descendant | `$..price` | 1KB | 1,318.45 | 1,376.13 | 0.96× |
| Descendant | `$..price` | 10KB | 13,237.55 | 13,533.79 | 0.98× |
| Descendant | `$..price` | 100KB | 128,029.05 | 131,399.37 | 0.97× |
| Slice | `$.store.book[1:4].title` | 1KB | 233.76 | 18.91 | 12.36× |
| Slice | `$.store.book[1:4].title` | 10KB | 220.39 | 19.81 | 11.13× |
| Slice | `$.store.book[1:4].title` | 100KB | 225.77 | 18.89 | 11.95× |

**Compiled Filter and Descendant use runtime fallback**, not fully generated traversal. Compiled Descendant is slightly slower than Runtime.

### JSONPath Parsing

Measures parsing JSONPath expressions into reusable query objects: `JsonPath.parse()` for SJF4J and `JsonPath.compile()` for Jayway. This excludes JSON document parsing and query execution, and is unrelated to `@CompiledNavigator` code generation.

| JSONPath | Jayway | SJF4J | Jayway / SJF4J |
| --- | ---: | ---: | ---: |
| `$.store.book[1].price` | 209.67 | 78.06 | 2.69× |
| `$.store.bicycle.color` | 145.27 | 73.94 | 1.96× |
| `$.store.book[*].author` | 172.46 | 79.76 | 2.16× |
| `$..price` | 64.59 | 36.44 | 1.77× |
| `$.store.book[?(@.price > 10)].title` | 719.35 | 238.49 | 3.02× |
| `$.store.book[0,2].title` | 283.98 | 113.38 | 2.50× |
| `$.store.book[1:4].title` | 233.91 | 99.96 | 2.34× |

### Allocation — selected 100KB queries

**B/op**, from the untrimmed JMH `gc.alloc.rate.norm` metric; lower is better. The ratio is Jayway allocation divided by Runtime allocation (>1× means less allocation for Runtime).

| Input | Query | Jayway | Runtime | Jayway / Runtime |
| --- | --- | ---: | ---: | ---: |
| Map / List | Wildcard | 608,962 | 15,104 | 40.32× |
| Map / List | Filter | 1,038,699 | 36,355 | 28.57× |
| Map / List | Descendant | 980,859 | 64,406 | 15.23× |
| Jackson2 JsonNode | Wildcard | 1,208,762 | 15,118 | 79.96× |
| Jackson2 JsonNode | Filter | 1,881,524 | 26,513 | 70.97× |
| Jackson2 JsonNode | Descendant | 1,757,956 | 64,402 | 27.30× |

## Summary

At the tested 1/10/100KB sizes, SJF4J Runtime was faster than Jayway in **30 of 30** comparable Map and JsonNode query cases. The POJO CompiledNavigator direct, wildcard, and slice queries show substantially lower query latency than POJO Runtime. Filter/Descendant compiled results require the fallback qualification above.

The results are specific to this workload; compare values within the same input type and runner job. Map/JsonNode and POJO/Parse used separate GitHub-hosted runner CPU models.

**[Full versioned report and methodology](path/bookstore-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42601902947)**

## Methodology

- Deterministic Bookstore fixture; actual JSON sizes: 1,021 / 10,267 / 102,395 bytes (12 / 123 / 1,229 books), approximately 30% satisfy the Filter predicate.
- All data instances and query expressions are created outside timed query operations, with result-equivalence validation before measurement.
- AverageTime, 1 thread, 3 independent forks, 5 × 1s warmup and 5 × 1s measurement, JMH allocation profiler.
- **Best 2 Mean**: average five iterations within each fork; discard the slowest fork *independently for every case* and average the remaining two. JMH original mean/error and B/op remain untrimmed; original error does not describe Best 2 Mean.
- Run on shared GitHub-hosted Ubuntu runners; this is exploratory, not a hardware-independent performance guarantee.

## Run

[Path Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/path-full-benchmark.yml) → **Run workflow**.

```bash
bash scripts/benchmark-path.sh full map
bash scripts/benchmark-path.sh full jsonnode
bash scripts/benchmark-path.sh full pojo
bash scripts/benchmark-path.sh full parse
```

`JsonPathBenchmark` is a separate exploratory benchmark. Neither class reuses the other’s fixture, State, or test methods.

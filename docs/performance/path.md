# Path Benchmark

SJF4J JSONPath navigation against **Jayway JsonPath 3.0.0**, with separate Runtime and CompiledNavigator measurements.

## Results — v1.5.0 (2026-11-09)

**JDK 25.0.5 · JMH · 3 forks · Best 2 Mean · 100KB input**

Each cell shows **baseline → SJF4J** in µs/op (lower is better), followed by the speedup. The baseline is Jayway for Map/JsonNode and SJF4J Runtime for POJO.

| Query and JSONPath | Map: Jayway → Runtime | JsonNode: Jayway → Runtime | POJO: Runtime → Compiled |
| --- | ---: | ---: | ---: |
| **Direct** `$.store.book[1].price` | 0.378 → **0.052** (7.30×) | 0.488 → **0.194** (2.51×) | 0.125 → **0.0022** (57.95×) |
| **Wildcard** `$.store.book[*].author` | 215.72 → **39.25** (5.50×) | 307.88 → **93.90** (3.28×) | 56.17 → **4.653** (12.07×) |
| **Filter*** `$.store.book[?(@.price > 10)].title` | 393.75 → **74.11** (5.31×) | 536.96 → **165.73** (3.24×) | 82.25 → **67.20** (1.22×) |
| **Descendant*** `$..price` | 399.56 → **133.07** (3.00×) | 556.19 → **296.66** (1.87×) | **128.03** → 131.40 (0.97×) |
| **Slice** `$.store.book[1:4].title` | 0.847 → **0.135** (6.26×) | 1.114 → **0.372** (3.00×) | 0.226 → **0.019** (11.95×) |

*POJO Compiled Filter and Descendant use **runtime fallback**, not fully generated traversal. The 0.97× Descendant result is a small regression, not a speedup.*

**Highlights:** SJF4J Runtime was faster than Jayway in **all 30** equivalent Map/JsonNode query comparisons across 1/10/100KB. At 100KB, Runtime was **3.00–7.30× faster** for Map and **1.87–3.28× faster** for JsonNode. Compiled POJO Direct, Wildcard, and Slice were **57.95×**, **12.07×**, and **11.95×** faster than POJO Runtime, respectively.

Allocation is also lower: at 100KB, JsonNode Filter allocates **1,881,524 B/op** with Jayway versus **26,513 B/op** with SJF4J Runtime (**98.6% less**). Expression compilation, measured separately on seven paths, was **1.77–3.02× faster** than Jayway.

**[All 1/10/100KB results and methodology](path/bookstore-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42601902947)**

## Benchmark scope

| Input | Jayway | Runtime | Compiled |
| --- | --- | --- | --- |
| Map/List | ✓ | ✓ | Direct only |
| Jackson2 JsonNode | ✓ | ✓ | — |
| POJO | — | ✓ | ✓ |

The same deterministic Bookstore structure is tested at approximately **1KB / 10KB / 100KB** (12 / 123 / 1,229 books). Filters match approximately 30% of books. All JSON inputs and paths are prepared before timing, and query results are checked for equivalence. The Full suite contains **107 cases** including independent expression parsing; it does not time JSON deserialization.

Full uses **3 independent forks**, 5 × 1s warmup and 5 × 1s measurement, one thread, and GC allocation profiling. **Best 2 Mean** averages the two fastest fork means independently for each exact case. The original JMH scores, errors, and B/op are untrimmed; JMH's score error does not apply to Best 2 Mean.

Map/JsonNode and POJO were measured in different GitHub-hosted jobs on different runner CPUs. Compare implementations **within the same input representation**, not absolute POJO latency against Map/JsonNode latency. GitHub-hosted timings are exploratory, not a hardware-independent guarantee.

## Run

[Path Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/path-full-benchmark.yml) → **Run workflow**.

Or run locally:

```bash
bash scripts/benchmark-path.sh full map
bash scripts/benchmark-path.sh full jsonnode
bash scripts/benchmark-path.sh full pojo
bash scripts/benchmark-path.sh full parse
```

The workflow generates the combined Markdown report and downloadable raw JMH artifacts. `JsonPathBenchmark` remains an independent exploratory benchmark and is not reused by `PathFullBenchmark`.

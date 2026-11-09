# Path Benchmark

SJF4J JSONPath runtime and compile-time navigation against **Jayway JsonPath 3.0.0**.
The benchmark focuses on query cost over existing Java object graphs, not JSON parsing.

## Workloads

**JDK 25 · JMH · 3 forks · Best 2 Mean · ns/op (lower is better)**

| Input | Jayway | Runtime | Compiled |
| --- | --- | --- | --- |
| Map/List | ✓ | ✓ | Direct only |
| Jackson2 JsonNode | ✓ | ✓ | — |
| POJO | — | ✓ | ✓ |

Each input uses the same deterministic Bookstore data, with serialized JSON sizes of approximately **1KB / 10KB / 100KB**. The `books` array scales while the shape remains unchanged. About 30% of books match the filter. The benchmark logs the actual JSON size, book count and expected matching count.

| Query | Expression | Measures |
| --- | --- | --- |
| Direct | `$.store.book[1].price` | Single known-location lookup |
| Wildcard | `$.store.book[*].author` | Array traversal and result collection |
| Filter | `$.store.book[?(@.price > 10)].title` | Predicate selection (~30%) |
| Descendant | `$..price` | Recursive property lookup |
| Slice | `$.store.book[1:4].title` | Array slice |

**Implementation boundaries:** all queries and inputs are compiled/constructed during JMH trial setup; only navigation is timed. Equivalence is checked before measurement, including output order and values. The Map/JsonNode Jayway comparisons run against the same pre-parsed input as SJF4J Runtime. CompiledNavigator cannot generate wildcard/filter/slice traversal for arbitrary `Map<String, Object>` nesting, so **only Map Direct** is measured as Compiled. POJO is a **Runtime vs Compiled** comparison, not a Jayway comparison. `@CompiledNavigator(fallback=true)` is used: **POJO Filter and Descendant fall back to runtime** and are not fully compiled navigation results.

**Expression parsing** is measured separately, using the existing `JsonPathBenchmark` seven expressions: SJF4J `JsonPath.parse()` vs Jayway `JsonPath.compile()`. Neither document parsing nor query execution is included in parsing measurements. Mutation/Put/EnsurePut is outside this initial baseline.

## Reporting

Full uses **3 independent forks**, **5 × 1s warmup** and **5 × 1s measurement**, with `-prof gc`. For each exact method and payload size, average the five iterations per fork, **discard only that case's slowest fork**, then average the other two (Best 2 Mean). Apply this independently to Jayway, Runtime and Compiled, and compute speedup ratios from the respective Best 2 Means. For parsing, apply the same rule per expression and implementation.

Keep the original JMH 3-fork mean, each fork mean, the original JMH error and GC allocation (B/op) alongside Best 2. The original error belongs to the untrimmed JMH score, **not** Best 2 Mean. GitHub-hosted runner scores are exploratory; controlled regressions require comparable hardware and identical benchmark versions/settings.

## Run

[Path Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/path-full-benchmark.yml) → **Run workflow**.

Or run locally (JDK 25):

```bash
bash scripts/benchmark-path.sh smoke map
bash scripts/benchmark-path.sh quick jsonnode
bash scripts/benchmark-path.sh full map
bash scripts/benchmark-path.sh full jsonnode
bash scripts/benchmark-path.sh full pojo
bash scripts/benchmark-path.sh full parse
```

Full is manual in GitHub Actions. PRs only compile JMH and run Smoke to validate equivalence. The workflow produces raw JMH JSON, per-shape metadata/logs, a combined artifact and a Markdown analysis via `scripts/analyze-path-benchmark.py`. Results are stored under `build/performance/path/`.

## Historical results

No verified Full result has been recorded yet. Each future versioned run will get its own snapshot under `docs/performance/path/` (for example, `bookstore-v150-YYYY-MM-DD.md`), linked here; do not overwrite an older benchmark snapshot.

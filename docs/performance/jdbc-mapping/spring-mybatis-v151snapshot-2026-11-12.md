# JDBC Mapping — Full JMH, suite 1 (2026-11-12)

[GitHub Actions Full run](https://github.com/sjf4j-projects/sjf4j/actions/runs/43186767777) ·
[Raw JSON, logs, metadata and runner details (workflow artifact)](https://github.com/sjf4j-projects/sjf4j/actions/runs/43186767777/artifacts/13931068826) ·
[Source commit `c66d95c`](https://github.com/sjf4j-projects/sjf4j/commit/c66d95c8f75e5c37f6aff936aecd96c493122c8c)

- **Release:** 1.5.1-SNAPSHOT; **suite:** 1 (12 columns, 1/10/1,000/100,000 rows)
- **Runtime:** Temurin OpenJDK 25.0.5+8, Linux x86_64, `ubuntu-24.04` shared runner
- **CPU:** AMD EPYC 9V45 96-Core Processor; **4 visible logical CPUs**, 15 GiB RAM
- **CPU affinity:** unrestricted; **CPU model guard:** none
- **Completeness:** 68/68 exact JMH case/row pairs; each includes 3 forks × 5 measurement iterations and a GC allocation metric
- **Full profile:** 3 forks, 5 × 1s warmup, 5 × 1s measurement, single thread
- **Measurement:** ResultSet traversal and Java object/Map materialization. SQL execution occurs in invocation setup, outside timed method calls

**Reporting:** AverageTime in **µs/op**, lower is better; discard the slowest per-case fork mean and average the two remaining fork means (**Best 2 Mean**). Allocation is untrimmed JMH `gc.alloc.rate.norm` in B/op. Every ratio is **baseline / SJF4J Compiled** (greater than 1× means SJF4J wins). Raw three-fork JMH means are preserved in the linked artifact; the reported Best 2 Mean is not described by JMH `scoreError`.

## Spring JDBC

The Spring comparison includes both List and Row mappings for all three shapes.
**Timing is µs/op; allocation is B/op.** A larger ratio means SJF4J
Compiled completed the mapping faster.

| Shape | Mode | Rows | Spring (µs/op) | Spring (B/op) | SJF4J Compiled (µs/op) | SJF4J (B/op) | Spring / SJF4J |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| POJO | List | 1 | 1.171 | 5,827 | 0.270 | 3,573 | 4.34× |
| POJO | List | 10 | 10.756 | 28,019 | 1.013 | 5,528 | 10.61× |
| POJO | List | 1,000 | 1,010.230 | 2,515,243 | 83.414 | 234,393 | 12.11× |
| POJO | List | 100,000 | 115,613.982 | 260,871,878 | 11,996.656 | 22,883,538 | 9.64× |
| POJO | Row | 1 | 1.190 | 5,827 | 0.198 | 3,579 | 6.00× |
| POJO | Row | 10 | 10.386 | 28,019 | 1.373 | 5,523 | 7.56× |
| POJO | Row | 1,000 | 1,003.745 | 2,515,244 | 131.289 | 234,393 | 7.65× |
| POJO | Row | 100,000 | 119,922.245 | 251,271,871 | 16,146.615 | 22,883,677 | 7.43× |
| Record | List | 1 | 9.114 | 15,131 | 0.283 | 3,573 | 32.23× |
| Record | List | 10 | 85.306 | 121,033 | 1.040 | 5,528 | 82.00× |
| Record | List | 1,000 | 8,764.423 | 11,819,755 | 84.267 | 234,393 | 104.01× |
| Record | List | 100,000 | 956,047.100 | 1,181,675,806 | 13,465.234 | 22,883,596 | 71.00× |
| Record | Row | 1 | 8.823 | 15,125 | 0.208 | 3,579 | 42.49× |
| Record | Row | 10 | 85.382 | 121,033 | 1.405 | 5,528 | 60.78× |
| Record | Row | 1,000 | 8,836.454 | 11,819,765 | 132.487 | 234,399 | 66.70× |
| Record | Row | 100,000 | 917,069.365 | 1,181,675,282 | 18,223.220 | 22,883,720 | 50.32× |
| Map | List | 1 | 0.401 | 4,669 | 0.243 | 4,123 | 1.65× |
| Map | List | 10 | 3.397 | 16,045 | 1.861 | 10,536 | 1.83× |
| Map | List | 1,000 | 308.258 | 1,315,275 | 182.553 | 763,274 | 1.69× |
| Map | List | 100,000 | 43,028.466 | 131,272,620 | 26,281.873 | 76,072,467 | 1.64× |
| Map | Row | 1 | 0.416 | 4,675 | 0.244 | 4,128 | 1.71× |
| Map | Row | 10 | 3.592 | 16,045 | 1.835 | 10,525 | 1.96× |
| Map | Row | 1,000 | 318.130 | 1,315,280 | 183.451 | 763,274 | 1.73× |
| Map | Row | 100,000 | 46,735.088 | 131,272,643 | 27,383.922 | 76,072,473 | 1.71× |

## MyBatis

MyBatis Auto and Explicit use DefaultResultSetHandler; Cursor is consumed
and materialized into a List (not the same as a direct RowMapper API).
There is no MyBatis Record or direct Row benchmark in this suite.
**Timing is µs/op; allocation is B/op.**

| Shape | Mapping | Rows | MyBatis (µs/op) | MyBatis (B/op) | SJF4J Compiled (µs/op) | SJF4J (B/op) | MyBatis / SJF4J |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| POJO | Auto / List | 1 | 2.978 | 11,317 | 0.270 | 3,573 | 11.03× |
| POJO | Explicit / List | 1 | 1.776 | 7,565 | 0.270 | 3,573 | 6.58× |
| POJO | Cursor / List | 1 | 3.102 | 11,213 | 0.270 | 3,573 | 11.49× |
| POJO | Auto / List | 10 | 7.490 | 20,232 | 1.013 | 5,528 | 7.39× |
| POJO | Explicit / List | 10 | 8.573 | 22,080 | 1.013 | 5,528 | 8.46× |
| POJO | Cursor / List | 10 | 7.624 | 19,965 | 1.013 | 5,528 | 7.52× |
| POJO | Auto / List | 1,000 | 497.950 | 1,042,178 | 83.414 | 234,393 | 5.97× |
| POJO | Explicit / List | 1,000 | 721.718 | 1,661,914 | 83.414 | 234,393 | 8.65× |
| POJO | Cursor / List | 1,000 | 498.337 | 1,042,148 | 83.414 | 234,393 | 5.97× |
| POJO | Auto / List | 100,000 | 57,970.295 | 108,081,576 | 11,996.656 | 22,883,538 | 4.83× |
| POJO | Explicit / List | 100,000 | 83,196.607 | 170,474,949 | 11,996.656 | 22,883,538 | 6.93× |
| POJO | Cursor / List | 100,000 | 56,766.796 | 108,081,007 | 11,996.656 | 22,883,538 | 4.73× |
| Map | Explicit / List | 1 | 1.613 | 7,488 | 0.243 | 4,123 | 6.64× |
| Map | Cursor / List | 1 | 1.606 | 7,443 | 0.243 | 4,123 | 6.62× |
| Map | Explicit / List | 10 | 7.220 | 24,779 | 1.861 | 10,536 | 3.88× |
| Map | Cursor / List | 10 | 7.409 | 24,675 | 1.861 | 10,536 | 3.98× |
| Map | Explicit / List | 1,000 | 609.343 | 1,997,958 | 182.553 | 763,274 | 3.34× |
| Map | Cursor / List | 1,000 | 641.080 | 1,997,854 | 182.553 | 763,274 | 3.51× |
| Map | Explicit / List | 100,000 | 70,233.496 | 204,076,241 | 26,281.873 | 76,072,467 | 2.67× |
| Map | Cursor / List | 100,000 | 76,776.908 | 204,075,753 | 26,281.873 | 76,072,467 | 2.92× |

## Interpretation and caveats

- **POJO:** SJF4J List is 4.34–12.11× faster than Spring and 4.83–11.03× faster than MyBatis Auto. At 100,000 rows, POJO List uses 22.9 MB/op versus Spring's 260.9 MB/op (decimal MB); JMH allocation ratio 11.40×.
- **Record:** Spring DataClassRowMapper is 32.23–104.01× slower than SJF4J List and allocates 51.64× more at 100,000 rows. This is specific to Spring's tested record/constructor mapping path, **not** a general finding about Java records. The cause of this gap requires profiling before attributing it to any one mechanism.
- **Map:** The advantage is smaller and consistent, 1.64–1.83× over Spring in List mode; Map implementations have different semantics and allocation profiles.
- **List vs Row:** The compiled List POJO/Record path can resolve columns once for the full result; the compiled per-row method is invoked for every row. Spring List mode uses RowMapperResultSetExtractor; MyBatis Cursor is streaming-to-List rather than a direct per-row mapper.
- **Fork stability:** Two exact latency cases exceeded a 15% slowest-to-fastest fork spread: `map/list/spring/100000` at **1.32×** (fork means 49,076.843 / 37,239.203 / 48,817.728 µs; Best 2 43,028.466 vs full three-fork mean 45,044.592) and `map/row/compiled/1000` at **1.28×** (231.192 / 186.231 / 180.671 µs; Best 2 183.451 vs three-fork mean 199.365). Treat these comparisons as exploratory.
- **Scope:** This run used a shared GitHub runner, not reserved hardware. H2 ResultSet processing is not full database query performance. JMH allocation profiling can count work in invocation setup/teardown, so B/op should not be presented as pure mapper-only allocations; this matters especially for single-row measurements.
- **Durability:** The original GitHub Actions artifact is retained according to workflow retention settings (30 days when produced). The numerical historical snapshot in this document remains in the repository after the artifact expires.

For implementation definitions and reproducibility commands, see [jdbc-mapping.md](../jdbc-mapping.md).

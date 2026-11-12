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

Spring uses BeanPropertyRowMapper for POJO, DataClassRowMapper for Record,
and ColumnMapRowMapper for Map. Both List and Row modes are included.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks; ratio compares execution time.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 1.171 | 0.270 | 4.34× |
| POJO / List | 10 | 10.756 | 1.013 | 10.61× |
| POJO / List | 1,000 | 1,010.230 | 83.414 | 12.11× |
| POJO / List | 100,000 | 115,613.982 | 11,996.656 | 9.64× |
| POJO / Row | 1 | 1.190 | 0.198 | 6.00× |
| POJO / Row | 10 | 10.386 | 1.373 | 7.56× |
| POJO / Row | 1,000 | 1,003.745 | 131.289 | 7.65× |
| POJO / Row | 100,000 | 119,922.245 | 16,146.615 | 7.43× |
| Record / List | 1 | 9.114 | 0.283 | 32.23× |
| Record / List | 10 | 85.306 | 1.040 | 82.00× |
| Record / List | 1,000 | 8,764.423 | 84.267 | 104.01× |
| Record / List | 100,000 | 956,047.100 | 13,465.234 | 71.00× |
| Record / Row | 1 | 8.823 | 0.208 | 42.49× |
| Record / Row | 10 | 85.382 | 1.405 | 60.78× |
| Record / Row | 1,000 | 8,836.454 | 132.487 | 66.70× |
| Record / Row | 100,000 | 917,069.365 | 18,223.220 | 50.32× |
| Map / List | 1 | 0.401 | 0.243 | 1.65× |
| Map / List | 10 | 3.397 | 1.861 | 1.83× |
| Map / List | 1,000 | 308.258 | 182.553 | 1.69× |
| Map / List | 100,000 | 43,028.466 | 26,281.873 | 1.64× |
| Map / Row | 1 | 0.416 | 0.244 | 1.71× |
| Map / Row | 10 | 3.592 | 1.835 | 1.96× |
| Map / Row | 1,000 | 318.130 | 183.451 | 1.73× |
| Map / Row | 100,000 | 46,735.088 | 27,383.922 | 1.71× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratio compares B/op, not speed.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 5,827 | 3,573 | 1.63× |
| POJO / List | 10 | 28,019 | 5,528 | 5.07× |
| POJO / List | 1,000 | 2,515,243 | 234,393 | 10.73× |
| POJO / List | 100,000 | 260,871,878 | 22,883,538 | 11.40× |
| POJO / Row | 1 | 5,827 | 3,579 | 1.63× |
| POJO / Row | 10 | 28,019 | 5,523 | 5.07× |
| POJO / Row | 1,000 | 2,515,244 | 234,393 | 10.73× |
| POJO / Row | 100,000 | 251,271,871 | 22,883,677 | 10.98× |
| Record / List | 1 | 15,131 | 3,573 | 4.23× |
| Record / List | 10 | 121,033 | 5,528 | 21.89× |
| Record / List | 1,000 | 11,819,755 | 234,393 | 50.43× |
| Record / List | 100,000 | 1,181,675,806 | 22,883,596 | 51.64× |
| Record / Row | 1 | 15,125 | 3,579 | 4.23× |
| Record / Row | 10 | 121,033 | 5,528 | 21.89× |
| Record / Row | 1,000 | 11,819,765 | 234,399 | 50.43× |
| Record / Row | 100,000 | 1,181,675,282 | 22,883,720 | 51.64× |
| Map / List | 1 | 4,669 | 4,123 | 1.13× |
| Map / List | 10 | 16,045 | 10,536 | 1.52× |
| Map / List | 1,000 | 1,315,275 | 763,274 | 1.72× |
| Map / List | 100,000 | 131,272,620 | 76,072,467 | 1.73× |
| Map / Row | 1 | 4,675 | 4,128 | 1.13× |
| Map / Row | 10 | 16,045 | 10,525 | 1.52× |
| Map / Row | 1,000 | 1,315,280 | 763,274 | 1.72× |
| Map / Row | 100,000 | 131,272,643 | 76,072,473 | 1.73× |

## MyBatis

MyBatis Auto and Explicit use DefaultResultSetHandler. Cursor is fully
materialized into a List and is **not** a direct RowMapper API.
No MyBatis Record or direct Row comparison is included in suite 1.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks; ratio compares execution time.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 2.978 | 0.270 | 11.03× |
| POJO / Explicit / List | 1 | 1.776 | 0.270 | 6.58× |
| POJO / Cursor / List | 1 | 3.102 | 0.270 | 11.49× |
| POJO / Auto / List | 10 | 7.490 | 1.013 | 7.39× |
| POJO / Explicit / List | 10 | 8.573 | 1.013 | 8.46× |
| POJO / Cursor / List | 10 | 7.624 | 1.013 | 7.52× |
| POJO / Auto / List | 1,000 | 497.950 | 83.414 | 5.97× |
| POJO / Explicit / List | 1,000 | 721.718 | 83.414 | 8.65× |
| POJO / Cursor / List | 1,000 | 498.337 | 83.414 | 5.97× |
| POJO / Auto / List | 100,000 | 57,970.295 | 11,996.656 | 4.83× |
| POJO / Explicit / List | 100,000 | 83,196.607 | 11,996.656 | 6.93× |
| POJO / Cursor / List | 100,000 | 56,766.796 | 11,996.656 | 4.73× |
| Map / Explicit / List | 1 | 1.613 | 0.243 | 6.64× |
| Map / Cursor / List | 1 | 1.606 | 0.243 | 6.62× |
| Map / Explicit / List | 10 | 7.220 | 1.861 | 3.88× |
| Map / Cursor / List | 10 | 7.409 | 1.861 | 3.98× |
| Map / Explicit / List | 1,000 | 609.343 | 182.553 | 3.34× |
| Map / Cursor / List | 1,000 | 641.080 | 182.553 | 3.51× |
| Map / Explicit / List | 100,000 | 70,233.496 | 26,281.873 | 2.67× |
| Map / Cursor / List | 100,000 | 76,776.908 | 26,281.873 | 2.92× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratio compares B/op, not speed.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 11,317 | 3,573 | 3.17× |
| POJO / Explicit / List | 1 | 7,565 | 3,573 | 2.12× |
| POJO / Cursor / List | 1 | 11,213 | 3,573 | 3.14× |
| POJO / Auto / List | 10 | 20,232 | 5,528 | 3.66× |
| POJO / Explicit / List | 10 | 22,080 | 5,528 | 3.99× |
| POJO / Cursor / List | 10 | 19,965 | 5,528 | 3.61× |
| POJO / Auto / List | 1,000 | 1,042,178 | 234,393 | 4.45× |
| POJO / Explicit / List | 1,000 | 1,661,914 | 234,393 | 7.09× |
| POJO / Cursor / List | 1,000 | 1,042,148 | 234,393 | 4.45× |
| POJO / Auto / List | 100,000 | 108,081,576 | 22,883,538 | 4.72× |
| POJO / Explicit / List | 100,000 | 170,474,949 | 22,883,538 | 7.45× |
| POJO / Cursor / List | 100,000 | 108,081,007 | 22,883,538 | 4.72× |
| Map / Explicit / List | 1 | 7,488 | 4,123 | 1.82× |
| Map / Cursor / List | 1 | 7,443 | 4,123 | 1.81× |
| Map / Explicit / List | 10 | 24,779 | 10,536 | 2.35× |
| Map / Cursor / List | 10 | 24,675 | 10,536 | 2.34× |
| Map / Explicit / List | 1,000 | 1,997,958 | 763,274 | 2.62× |
| Map / Cursor / List | 1,000 | 1,997,854 | 763,274 | 2.62× |
| Map / Explicit / List | 100,000 | 204,076,241 | 76,072,467 | 2.68× |
| Map / Cursor / List | 100,000 | 204,075,753 | 76,072,467 | 2.68× |

## Interpretation and caveats

- **POJO:** SJF4J List is 4.34–12.11× faster than Spring and 4.83–11.03× faster than MyBatis Auto. At 100,000 rows, POJO List uses 22.9 MB/op versus Spring's 260.9 MB/op (decimal MB); JMH allocation ratio 11.40×.
- **Record:** Spring DataClassRowMapper is 32.23–104.01× slower than SJF4J List and allocates 51.64× more at 100,000 rows. This is specific to Spring's tested record/constructor mapping path, **not** a general finding about Java records. The cause of this gap requires profiling before attributing it to any one mechanism.
- **Map:** The advantage is smaller and consistent, 1.64–1.83× over Spring in List mode; Map implementations have different semantics and allocation profiles.
- **List vs Row:** The compiled List POJO/Record path can resolve columns once for the full result; the compiled per-row method is invoked for every row. Spring List mode uses RowMapperResultSetExtractor; MyBatis Cursor is streaming-to-List rather than a direct per-row mapper.
- **Fork stability:** Two exact latency cases exceeded a 15% slowest-to-fastest fork spread: `map/list/spring/100000` at **1.32×** (fork means 49,076.843 / 37,239.203 / 48,817.728 µs; Best 2 43,028.466 vs full three-fork mean 45,044.592) and `map/row/compiled/1000` at **1.28×** (231.192 / 186.231 / 180.671 µs; Best 2 183.451 vs three-fork mean 199.365). Treat these comparisons as exploratory.
- **Scope:** This run used a shared GitHub runner, not reserved hardware. H2 ResultSet processing is not full database query performance. JMH allocation profiling can count work in invocation setup/teardown, so B/op should not be presented as pure mapper-only allocations; this matters especially for single-row measurements.
- **Durability:** The original GitHub Actions artifact is retained according to workflow retention settings (30 days when produced). The numerical historical snapshot in this document remains in the repository after the artifact expires.

For implementation definitions and reproducibility commands, see [jdbc-mapping.md](../jdbc-mapping.md).

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

## POJO

### List

| Rows | Spring | MyBatis Auto | MyBatis Explicit | MyBatis Cursor | SJF4J Compiled | Spring / Compiled | Auto / Compiled | Explicit / Compiled | Cursor / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1.171 | 2.978 | 1.776 | 3.102 | 0.270 | 4.34× | 11.03× | 6.58× | 11.49× |
| 10 | 10.756 | 7.490 | 8.573 | 7.624 | 1.013 | 10.61× | 7.39× | 8.46× | 7.52× |
| 1,000 | 1,010.230 | 497.950 | 721.718 | 498.337 | 83.414 | 12.11× | 5.97× | 8.65× | 5.97× |
| 100,000 | 115,613.982 | 57,970.295 | 83,196.607 | 56,766.796 | 11,996.656 | 9.64× | 4.83× | 6.93× | 4.73× |

**Allocation (B/op)**

| Rows | Spring | MyBatis Auto | MyBatis Explicit | MyBatis Cursor | SJF4J Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 5,827 | 11,317 | 7,565 | 11,213 | 3,573 |
| 10 | 28,019 | 20,232 | 22,080 | 19,965 | 5,528 |
| 1,000 | 2,515,243 | 1,042,178 | 1,661,914 | 1,042,148 | 234,393 |
| 100,000 | 260,871,878 | 108,081,576 | 170,474,949 | 108,081,007 | 22,883,538 |

### Row

| Rows | Spring | Spring B/op | SJF4J Compiled | SJF4J B/op | Spring / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1.190 | 5,827 | 0.198 | 3,579 | 6.00× |
| 10 | 10.386 | 28,019 | 1.373 | 5,523 | 7.56× |
| 1,000 | 1,003.745 | 2,515,244 | 131.289 | 234,393 | 7.65× |
| 100,000 | 119,922.245 | 251,271,871 | 16,146.615 | 22,883,677 | 7.43× |

## Record

### List

| Rows | Spring | Spring B/op | SJF4J Compiled | SJF4J B/op | Spring / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 9.114 | 15,131 | 0.283 | 3,573 | 32.23× |
| 10 | 85.306 | 121,033 | 1.040 | 5,528 | 82.00× |
| 1,000 | 8,764.423 | 11,819,755 | 84.267 | 234,393 | 104.01× |
| 100,000 | 956,047.100 | 1,181,675,806 | 13,465.234 | 22,883,596 | 71.00× |

### Row

| Rows | Spring | Spring B/op | SJF4J Compiled | SJF4J B/op | Spring / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 8.823 | 15,125 | 0.208 | 3,579 | 42.49× |
| 10 | 85.382 | 121,033 | 1.405 | 5,528 | 60.78× |
| 1,000 | 8,836.454 | 11,819,765 | 132.487 | 234,399 | 66.70× |
| 100,000 | 917,069.365 | 1,181,675,282 | 18,223.220 | 22,883,720 | 50.32× |

## Map

### List

| Rows | Spring | MyBatis Explicit | MyBatis Cursor | SJF4J Compiled | Spring / Compiled | Explicit / Compiled | Cursor / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.401 | 1.613 | 1.606 | 0.243 | 1.65× | 6.64× | 6.62× |
| 10 | 3.397 | 7.220 | 7.409 | 1.861 | 1.83× | 3.88× | 3.98× |
| 1,000 | 308.258 | 609.343 | 641.080 | 182.553 | 1.69× | 3.34× | 3.51× |
| 100,000 | 43,028.466 | 70,233.496 | 76,776.908 | 26,281.873 | 1.64× | 2.67× | 2.92× |

**Allocation (B/op)**

| Rows | Spring | MyBatis Explicit | MyBatis Cursor | SJF4J Compiled |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 4,669 | 7,488 | 7,443 | 4,123 |
| 10 | 16,045 | 24,779 | 24,675 | 10,536 |
| 1,000 | 1,315,275 | 1,997,958 | 1,997,854 | 763,274 |
| 100,000 | 131,272,620 | 204,076,241 | 204,075,753 | 76,072,467 |

### Row

| Rows | Spring | Spring B/op | SJF4J Compiled | SJF4J B/op | Spring / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.416 | 4,675 | 0.244 | 4,128 | 1.71× |
| 10 | 3.592 | 16,045 | 1.835 | 10,525 | 1.96× |
| 1,000 | 318.130 | 1,315,280 | 183.451 | 763,274 | 1.73× |
| 100,000 | 46,735.088 | 131,272,643 | 27,383.922 | 76,072,473 | 1.71× |

## Interpretation and caveats

- **POJO:** SJF4J List is 4.34–12.11× faster than Spring and 4.83–11.03× faster than MyBatis Auto. At 100,000 rows, POJO List uses 22.9 MB/op versus Spring's 260.9 MB/op (decimal MB); JMH allocation ratio 11.40×.
- **Record:** Spring DataClassRowMapper is 32.23–104.01× slower than SJF4J List and allocates 51.64× more at 100,000 rows. This is specific to Spring's tested record/constructor mapping path, **not** a general finding about Java records. The cause of this gap requires profiling before attributing it to any one mechanism.
- **Map:** The advantage is smaller and consistent, 1.64–1.83× over Spring in List mode; Map implementations have different semantics and allocation profiles.
- **List vs Row:** The compiled List POJO/Record path can resolve columns once for the full result; the compiled per-row method is invoked for every row. Spring List mode uses RowMapperResultSetExtractor; MyBatis Cursor is streaming-to-List rather than a direct per-row mapper.
- **Fork stability:** Two exact latency cases exceeded a 15% slowest-to-fastest fork spread: `map/list/spring/100000` at **1.32×** (fork means 49,076.843 / 37,239.203 / 48,817.728 µs; Best 2 43,028.466 vs full three-fork mean 45,044.592) and `map/row/compiled/1000` at **1.28×** (231.192 / 186.231 / 180.671 µs; Best 2 183.451 vs three-fork mean 199.365). Treat these comparisons as exploratory.
- **Scope:** This run used a shared GitHub runner, not reserved hardware. H2 ResultSet processing is not full database query performance. JMH allocation profiling can count work in invocation setup/teardown, so B/op should not be presented as pure mapper-only allocations; this matters especially for single-row measurements.
- **Durability:** The original GitHub Actions artifact is retained according to workflow retention settings (30 days when produced). The numerical historical snapshot in this document remains in the repository after the artifact expires.

For implementation definitions and reproducibility commands, see [jdbc-mapping.md](../jdbc-mapping.md).

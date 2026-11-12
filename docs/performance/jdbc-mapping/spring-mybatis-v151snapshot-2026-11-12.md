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

| Rows | Compiled | Spring | MyBatis Auto | MyBatis Explicit | MyBatis Cursor | Spring / Compiled | Auto / Compiled | Explicit / Compiled | Cursor / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.270 | 1.171 | 2.978 | 1.776 | 3.102 | 4.34× | 11.03× | 6.58× | 11.49× |
| 10 | 1.013 | 10.756 | 7.490 | 8.573 | 7.624 | 10.61× | 7.39× | 8.46× | 7.52× |
| 1,000 | 83.414 | 1,010.230 | 497.950 | 721.718 | 498.337 | 12.11× | 5.97× | 8.65× | 5.97× |
| 100,000 | 11,996.656 | 115,613.982 | 57,970.295 | 83,196.607 | 56,766.796 | 9.64× | 4.83× | 6.93× | 4.73× |

**Allocation (B/op)**

| Rows | Compiled | Spring | MyBatis Auto | MyBatis Explicit | MyBatis Cursor |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 3,573 | 5,827 | 11,317 | 7,565 | 11,213 |
| 10 | 5,528 | 28,019 | 20,232 | 22,080 | 19,965 |
| 1,000 | 234,393 | 2,515,243 | 1,042,178 | 1,661,914 | 1,042,148 |
| 100,000 | 22,883,538 | 260,871,878 | 108,081,576 | 170,474,949 | 108,081,007 |

### Row

| Rows | Compiled | Spring | Spring / Compiled | Compiled B/op | Spring B/op |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.198 | 1.190 | 6.00× | 3,579 | 5,827 |
| 10 | 1.373 | 10.386 | 7.56× | 5,523 | 28,019 |
| 1,000 | 131.289 | 1,003.745 | 7.65× | 234,393 | 2,515,244 |
| 100,000 | 16,146.615 | 119,922.245 | 7.43× | 22,883,677 | 251,271,871 |

## Record

### List

| Rows | Compiled | Spring | Spring / Compiled | Compiled B/op | Spring B/op |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.283 | 9.114 | 32.23× | 3,573 | 15,131 |
| 10 | 1.040 | 85.306 | 82.00× | 5,528 | 121,033 |
| 1,000 | 84.267 | 8,764.423 | 104.01× | 234,393 | 11,819,755 |
| 100,000 | 13,465.234 | 956,047.100 | 71.00× | 22,883,596 | 1,181,675,806 |

### Row

| Rows | Compiled | Spring | Spring / Compiled | Compiled B/op | Spring B/op |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.208 | 8.823 | 42.49× | 3,579 | 15,125 |
| 10 | 1.405 | 85.382 | 60.78× | 5,528 | 121,033 |
| 1,000 | 132.487 | 8,836.454 | 66.70× | 234,399 | 11,819,765 |
| 100,000 | 18,223.220 | 917,069.365 | 50.32× | 22,883,720 | 1,181,675,282 |

## Map

### List

| Rows | Compiled | Spring | MyBatis Explicit | MyBatis Cursor | Spring / Compiled | Explicit / Compiled | Cursor / Compiled |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.243 | 0.401 | 1.613 | 1.606 | 1.65× | 6.64× | 6.62× |
| 10 | 1.861 | 3.397 | 7.220 | 7.409 | 1.83× | 3.88× | 3.98× |
| 1,000 | 182.553 | 308.258 | 609.343 | 641.080 | 1.69× | 3.34× | 3.51× |
| 100,000 | 26,281.873 | 43,028.466 | 70,233.496 | 76,776.908 | 1.64× | 2.67× | 2.92× |

**Allocation (B/op)**

| Rows | Compiled | Spring | MyBatis Explicit | MyBatis Cursor |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 4,123 | 4,669 | 7,488 | 7,443 |
| 10 | 10,536 | 16,045 | 24,779 | 24,675 |
| 1,000 | 763,274 | 1,315,275 | 1,997,958 | 1,997,854 |
| 100,000 | 76,072,467 | 131,272,620 | 204,076,241 | 204,075,753 |

### Row

| Rows | Compiled | Spring | Spring / Compiled | Compiled B/op | Spring B/op |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.244 | 0.416 | 1.71× | 4,128 | 4,675 |
| 10 | 1.835 | 3.592 | 1.96× | 10,525 | 16,045 |
| 1,000 | 183.451 | 318.130 | 1.73× | 763,274 | 1,315,280 |
| 100,000 | 27,383.922 | 46,735.088 | 1.71× | 76,072,473 | 131,272,643 |

## Interpretation and caveats

- **POJO:** SJF4J List is 4.34–12.11× faster than Spring and 4.83–11.03× faster than MyBatis Auto. At 100,000 rows, POJO List uses 22.9 MB/op versus Spring's 260.9 MB/op (decimal MB); JMH allocation ratio 11.40×.
- **Record:** Spring DataClassRowMapper is 32.23–104.01× slower than SJF4J List and allocates 51.64× more at 100,000 rows. This is specific to Spring's tested record/constructor mapping path, **not** a general finding about Java records. The cause of this gap requires profiling before attributing it to any one mechanism.
- **Map:** The advantage is smaller and consistent, 1.64–1.83× over Spring in List mode; Map implementations have different semantics and allocation profiles.
- **List vs Row:** The compiled List POJO/Record path can resolve columns once for the full result; the compiled per-row method is invoked for every row. Spring List mode uses RowMapperResultSetExtractor; MyBatis Cursor is streaming-to-List rather than a direct per-row mapper.
- **Fork stability:** Two exact latency cases exceeded a 15% slowest-to-fastest fork spread: `map/list/spring/100000` at **1.32×** (fork means 49,076.843 / 37,239.203 / 48,817.728 µs; Best 2 43,028.466 vs full three-fork mean 45,044.592) and `map/row/compiled/1000` at **1.28×** (231.192 / 186.231 / 180.671 µs; Best 2 183.451 vs three-fork mean 199.365). Treat these comparisons as exploratory.
- **Scope:** This run used a shared GitHub runner, not reserved hardware. H2 ResultSet processing is not full database query performance. JMH allocation profiling can count work in invocation setup/teardown, so B/op should not be presented as pure mapper-only allocations; this matters especially for single-row measurements.
- **Durability:** The original GitHub Actions artifact is retained according to workflow retention settings (30 days when produced). The numerical historical snapshot in this document remains in the repository after the artifact expires.

For implementation definitions and reproducibility commands, see [jdbc-mapping.md](../jdbc-mapping.md).

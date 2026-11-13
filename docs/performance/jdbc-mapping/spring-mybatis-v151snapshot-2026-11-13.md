# JDBC Mapping — Full JMH (2026-11-13)

[GitHub Actions Full run #43359001074](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359001074) ·
[Original report, raw JMH JSON, logs and runner details](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359001074/artifacts/14009670887) ·
[Source commit `391e098`](https://github.com/sjf4j-projects/sjf4j/commit/391e098fd3b5033feea3fd5aa8accd98f776acd2)

- **Release:** `1.5.1-SNAPSHOT`; **JDBC benchmark suite:** 2 (prepared `JdbcRowMapper<T>` with automatic snake_case mapping)
- **Runtime:** Temurin OpenJDK **25.0.5+8**, Linux x86_64, shared GitHub-hosted Ubuntu runner
- **Hardware:** Intel(R) Xeon(R) **6975P-C**, 4 visible logical CPUs, 15 GiB RAM
- **CPU guard / affinity:** no CPU model guard; unrestricted affinity (CPUs 0–3)
- **Completeness:** **68/68** exact JMH cases (POJO 28, Record 16, Map 24); every case includes 3 forks × 5 measurement iterations plus allocation
- **Full profile:** 3 forks, 5 × 1s warmup, 5 × 1s measurement, 1 thread
- **Workload:** 12-column Customer Account fixture; 1 / 10 / 1,000 / 100,000 rows; POJO, Record and Map results. Each operation fully materializes a List; no SQL execution is timed

**Reporting:** AverageTime in **µs/op**, lower is better. For each exact
method and row count, discard its slowest of the three *fork means*, then
average the remaining two (**Best 2 Mean**). Allocation is untrimmed JMH
`gc.alloc.rate.norm` in **B/op**. Each ratio is **competitor / SJF4J Compiled**;
greater than 1× means SJF4J is faster or allocates fewer bytes, respectively.
Ratios use unrounded measurements. The linked artifact preserves the complete
untrimmed raw fork data.

## Spring JDBC

POJO uses `BeanPropertyRowMapper`; Record uses `DataClassRowMapper`;
Map uses `ColumnMapRowMapper`. Both List and Row modes are measured.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 1.358 | 0.684 | 1.98× |
| POJO / List | 10 | 12.934 | 1.437 | 9.00× |
| POJO / List | 1,000 | 1,268.973 | 79.154 | 16.03× |
| POJO / List | 100,000 | 139,484.663 | 8,760.496 | 15.92× |
| POJO / Row | 1 | 1.363 | 0.695 | 1.96× |
| POJO / Row | 10 | 12.740 | 1.436 | 8.87× |
| POJO / Row | 1,000 | 1,273.631 | 80.144 | 15.89× |
| POJO / Row | 100,000 | 134,771.783 | 8,832.778 | 15.26× |
| Record / List | 1 | 9.507 | 0.701 | 13.56× |
| Record / List | 10 | 91.240 | 1.636 | 55.75× |
| Record / List | 1,000 | 9,120.838 | 79.983 | 114.03× |
| Record / List | 100,000 | 979,864.742 | 8,898.443 | 110.12× |
| Record / Row | 1 | 9.332 | 0.728 | 12.82× |
| Record / Row | 10 | 88.979 | 1.617 | 55.03× |
| Record / Row | 1,000 | 8,859.932 | 80.986 | 109.40× |
| Record / Row | 100,000 | 920,998.022 | 9,036.785 | 101.92× |
| Map / List | 1 | 0.456 | 0.273 | 1.67× |
| Map / List | 10 | 4.103 | 1.658 | 2.47× |
| Map / List | 1,000 | 396.564 | 157.779 | 2.51× |
| Map / List | 100,000 | 53,247.847 | 21,282.180 | 2.50× |
| Map / Row | 1 | 0.443 | 0.270 | 1.64× |
| Map / Row | 10 | 4.121 | 1.678 | 2.46× |
| Map / Row | 1,000 | 392.672 | 157.280 | 2.50× |
| Map / Row | 100,000 | 49,393.602 | 22,603.851 | 2.19× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratios compare bytes, not time.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 5,827 | 4,189 | 1.39× |
| POJO / List | 10 | 28,003 | 6,133 | 4.57× |
| POJO / List | 1,000 | 2,515,268 | 235,073 | 10.70× |
| POJO / List | 100,000 | 289,672,019 | 22,884,129 | 12.66× |
| POJO / Row | 1 | 5,821 | 4,200 | 1.39× |
| POJO / Row | 10 | 27,997 | 6,144 | 4.56× |
| POJO / Row | 1,000 | 2,515,265 | 235,078 | 10.70× |
| POJO / Row | 100,000 | 280,072,039 | 22,884,129 | 12.24× |
| Record / List | 1 | 15,232 | 4,195 | 3.63× |
| Record / List | 10 | 121,993 | 6,139 | 19.87× |
| Record / List | 1,000 | 11,819,771 | 235,083 | 50.28× |
| Record / List | 100,000 | 1,200,876,976 | 22,884,132 | 52.48× |
| Record / Row | 1 | 15,221 | 4,200 | 3.62× |
| Record / Row | 10 | 121,998 | 6,128 | 19.91× |
| Record / Row | 1,000 | 11,819,770 | 235,073 | 50.28× |
| Record / Row | 100,000 | 1,181,675,279 | 22,884,137 | 51.64× |
| Map / List | 1 | 4,675 | 4,272 | 1.09× |
| Map / List | 10 | 16,040 | 10,651 | 1.51× |
| Map / List | 1,000 | 1,315,276 | 763,417 | 1.72× |
| Map / List | 100,000 | 131,272,697 | 76,072,567 | 1.73× |
| Map / Row | 1 | 4,664 | 4,261 | 1.09× |
| Map / Row | 10 | 16,045 | 10,675 | 1.50× |
| Map / Row | 1,000 | 1,315,276 | 763,417 | 1.72× |
| Map / Row | 100,000 | 131,272,674 | 76,072,582 | 1.73× |

## MyBatis

Auto, Explicit and Cursor use the configured `DefaultResultSetHandler`;
Cursor results are fully collected. Only POJO and Map List workloads apply.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 3.545 | 0.684 | 5.18× |
| POJO / Explicit / List | 1 | 1.929 | 0.684 | 2.82× |
| POJO / Cursor / List | 1 | 3.560 | 0.684 | 5.20× |
| POJO / Auto / List | 10 | 8.786 | 1.437 | 6.11× |
| POJO / Explicit / List | 10 | 9.960 | 1.437 | 6.93× |
| POJO / Cursor / List | 10 | 8.792 | 1.437 | 6.12× |
| POJO / Auto / List | 1,000 | 580.728 | 79.154 | 7.34× |
| POJO / Explicit / List | 1,000 | 891.189 | 79.154 | 11.26× |
| POJO / Cursor / List | 1,000 | 610.375 | 79.154 | 7.71× |
| POJO / Auto / List | 100,000 | 64,587.199 | 8,760.496 | 7.37× |
| POJO / Explicit / List | 100,000 | 96,274.714 | 8,760.496 | 10.99× |
| POJO / Cursor / List | 100,000 | 68,879.863 | 8,760.496 | 7.86× |
| Map / Explicit / List | 1 | 1.766 | 0.273 | 6.46× |
| Map / Cursor / List | 1 | 1.736 | 0.273 | 6.35× |
| Map / Explicit / List | 10 | 8.486 | 1.658 | 5.12× |
| Map / Cursor / List | 10 | 8.580 | 1.658 | 5.17× |
| Map / Explicit / List | 1,000 | 751.599 | 157.779 | 4.76× |
| Map / Cursor / List | 1,000 | 766.502 | 157.779 | 4.86× |
| Map / Explicit / List | 100,000 | 83,497.845 | 21,282.180 | 3.92× |
| Map / Cursor / List | 100,000 | 88,518.805 | 21,282.180 | 4.16× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratios compare bytes, not time.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 11,291 | 4,189 | 2.70× |
| POJO / Explicit / List | 1 | 7,560 | 4,189 | 1.80× |
| POJO / Cursor / List | 1 | 11,224 | 4,189 | 2.68× |
| POJO / Auto / List | 10 | 20,259 | 6,133 | 3.30× |
| POJO / Explicit / List | 10 | 22,064 | 6,133 | 3.60× |
| POJO / Cursor / List | 10 | 19,851 | 6,133 | 3.24× |
| POJO / Auto / List | 1,000 | 1,042,173 | 235,073 | 4.43× |
| POJO / Explicit / List | 1,000 | 1,661,961 | 235,073 | 7.07× |
| POJO / Cursor / List | 1,000 | 1,068,920 | 235,073 | 4.55× |
| POJO / Auto / List | 100,000 | 108,081,620 | 22,884,129 | 4.72× |
| POJO / Explicit / List | 100,000 | 170,474,891 | 22,884,129 | 7.45× |
| POJO / Cursor / List | 100,000 | 108,081,076 | 22,884,129 | 4.72× |
| Map / Explicit / List | 1 | 7,509 | 4,272 | 1.76× |
| Map / Cursor / List | 1 | 7,448 | 4,272 | 1.74× |
| Map / Explicit / List | 10 | 24,747 | 10,651 | 2.32× |
| Map / Cursor / List | 10 | 23,395 | 10,651 | 2.20× |
| Map / Explicit / List | 1,000 | 1,997,962 | 763,417 | 2.62× |
| Map / Cursor / List | 1,000 | 1,997,858 | 763,417 | 2.62× |
| Map / Explicit / List | 100,000 | 206,209,223 | 76,072,567 | 2.71× |
| Map / Cursor / List | 100,000 | 204,075,960 | 76,072,567 | 2.68× |

## Findings and limitations

- **Prepared RowMapper reuse:** SJF4J List and Row are close after unifying
  the generated row-mapping path. At 1,000 rows the List / Row pairs are
  **79.154 / 80.144 µs** (POJO), **79.983 / 80.986 µs** (Record), and
  **157.779 / 157.280 µs** (Map). Preparation occurs once per operation,
  including in the timed Row benchmark; it is not cached across invocations.
- **POJO, 100,000 rows:** SJF4J List is **15.92×** faster than Spring,
  **7.37×** faster than MyBatis Auto and **10.99×** faster than MyBatis Explicit
  in this run. It allocates **22,884,129 B/op** versus Spring's
  **289,672,019 B/op**.
- **Record, 100,000 rows:** SJF4J List is **110.12×** faster than Spring's
  tested `DataClassRowMapper` path. This is a workload-specific result,
  not a universal claim about Java records.

### Fork stability

The following latency cases exceed the 15% warning threshold
(slowest/fastest fork means). The three original fork means below are
in µs/op, with no trimming in this diagnostic:

| Case | Fork 1 | Fork 2 | Fork 3 | Slowest / fastest |
| --- | ---: | ---: | ---: | ---: |
| Map / List / Compiled / 100,000 | 19,864.680 | 23,453.954 | 22,699.679 | 1.18× |
| Map / Row / Compiled / 100,000 | 23,089.118 | 22,118.584 | 26,062.746 | 1.18× |
| Map / Row / Spring / 100,000 | 51,955.412 | 55,934.846 | 46,831.792 | 1.19× |

Interpret small Map differences cautiously. The original report and raw
JMH JSON remain linked above for detailed per-fork inspection.

### Scope

- The benchmark starts from an **H2 in-memory `ResultSet`**. SQL execution,
  connection acquisition, network I/O and full ORM/SQL session operations
  are outside the timed mapping methods.
- Spring's `ColumnMapRowMapper` implements case-insensitive Map lookup;
  SJF4J and MyBatis use different Map implementations. Returned column
  values are validated, but the internal Map semantics are not identical.
- MyBatis Cursor involves cursor machinery and full List materialization;
  there is no MyBatis Record or per-row `RowMapper` result in this comparison.
- JMH Invocation setup/teardown may influence allocation profiling and the
  apparent precision of one-row workloads. A shared runner does not provide
  controlled hardware; treat results as exploratory.
- Original GitHub Actions artifacts expire under the workflow's retention
  policy. This dated numerical snapshot is retained in the repository.

For the workload schema, sample mapping code, runner commands and current
results index, see [JDBC Mapping performance baseline](../jdbc-mapping.md).

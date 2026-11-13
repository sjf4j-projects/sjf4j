# JDBC Mapping performance baseline

SJF4J **CompiledJdbcMapper** versus **Spring JDBC** and **MyBatis** for
`ResultSet`-to-Java-object mapping. The benchmark measures JDBC row traversal
and object materialization, **not SQL execution, connection acquisition, or
network I/O**. The workload is
[`JdbcFullBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/mapping/JdbcFullBenchmark.java).

## Workload

The **Customer Account** fixture has 12 scalar columns, mapped to three target
shapes: JavaBean (POJO), Java Record, and `Map<String, Object>`.

| SQL column | Java property | Java type |
| --- | --- | --- |
| id | id | long |
| user_name | userName | String |
| email | email | String |
| phone | phone | String |
| age | age | int |
| active | active | boolean |
| balance | balance | BigDecimal |
| login_count | loginCount | int |
| score | score | double |
| city | city | String |
| created_at | createdAt | LocalDateTime |
| updated_at | updatedAt | LocalDateTime |

All scalar values are non-null. SQL `TIMESTAMP` maps to `LocalDateTime`
without timezone conversion. SQL aliases use snake_case; SJF4J applies
`ColumnNamingPolicy.INSENSITIVE_SNAKE`, Spring applies its bean/constructor
matching, and MyBatis Auto enables underscore-to-camel-case matching.
No nested object graph, custom codec or JOJO dynamic property is included.

Every implementation consumes **1 / 10 / 1,000 / 100,000 rows**, returning an
equivalent fully materialized `List`:

- **List:** the mapper consumes the full `ResultSet` and builds the List.
  SJF4J's generated List code prepares a `JdbcRowMapper<T>` once and reuses it
  for every row.
- **Row:** the benchmark owns the `ResultSet.next()` loop and List collection.
  SJF4J prepares `JdbcRowMapper<T>` from `ResultSetMetaData` once per
  invocation, then calls `map(rs)`; Spring uses `RowMapper.mapRow(rs, rowNum)`.
  Preparation is included in the timed operation.
- **MyBatis:** Auto and Explicit map to Lists using `DefaultResultSetHandler`;
  Cursor iterates a MyBatis cursor into a List. There is no equivalent
  direct MyBatis RowMapper case or Record comparison.

## Comparison dependency versions

| Component | Version | Benchmark implementation |
| --- | --- | --- |
| Spring JDBC | `6.2.19` | `BeanPropertyRowMapper`, `DataClassRowMapper`, `ColumnMapRowMapper`, `RowMapperResultSetExtractor` |
| MyBatis | `3.5.19` | `DefaultResultSetHandler`, Auto/Explicit ResultMap and Cursor |
| H2 | `2.3.232` | In-memory JDBC ResultSet provider |
| JMH | `1.37` | Benchmark harness |

These are testbench dependencies in
[`sjf4j-testbench/build.gradle.kts`](../../sjf4j-testbench/build.gradle.kts),
not SJF4J production dependencies.


## Code examples

Core calls for the POJO workload (initialization/configuration omitted):

**Spring JDBC**

```java
RowMapper<AccountBean> mapper = BeanPropertyRowMapper.newInstance(AccountBean.class);
List<AccountBean> result = new RowMapperResultSetExtractor<>(mapper).extractData(rs);
```

**MyBatis**

```java
// A configured DefaultResultSetHandler with an Auto or Explicit ResultMap.
List<AccountBean> result =
        (List<AccountBean>) (List<?>) handler.handleResultSets(select);
```

**SJF4J Compiled**

```java
@CompiledJdbcMapper
interface AccountMapper {
    List<AccountBean> beans(ResultSet rs);
    JdbcRowMapper<AccountBean> prepareBean(ResultSetMetaData metadata);
}

List<AccountBean> result = compiled.beans(rs);

// Row mode (with a fresh ResultSet):
JdbcRowMapper<AccountBean> mapper = compiled.prepareBean(rs.getMetaData());
List<AccountBean> rows = new ArrayList<>();
while (rs.next()) rows.add(mapper.map(rs));
```

Spring mappers/extractors and MyBatis handlers are constructed outside timed
methods. MyBatis's internal handler is invoked directly, not via a full
`SqlSession` query.

## JVM — Temurin JDK 25

### Results — v1.5.1-SNAPSHOT (2026-11-13)

**Full JMH · 68/68 benchmark entries · 3 independent forks · Best 2 Mean.**
[GitHub Actions run #43359001074](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359001074) ·
[Detailed Full report](jdbc-mapping/spring-mybatis-v151snapshot-2026-11-13.md) ·
[Raw JSON, logs and runner metadata](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359001074/artifacts/14009670887) ·
[Source commit `391e098`](https://github.com/sjf4j-projects/sjf4j/commit/391e098fd3b5033feea3fd5aa8accd98f776acd2).

Temurin **25.0.5+8**, Intel **Xeon 6975P-C**, 4 visible logical CPUs,
15 GiB RAM, shared GitHub-hosted Ubuntu runner, unrestricted CPU affinity.

Latency is **µs/op** for mapping the **entire ResultSet** (lower is better).
Each benchmark independently drops the slowest fork mean and averages the
remaining two (**Best 2 Mean**). Allocation is the untrimmed JMH
`gc.alloc.rate.norm` in **B/op**. Each ratio is the competitor divided by
SJF4J **Compiled**; above **1×** favors SJF4J.

#### Spring JDBC

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

#### MyBatis

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

#### Key findings

- **List and Row converge.** At 1,000 rows, SJF4J List and Row measure
  79.154 vs 80.144 µs (POJO), 79.983 vs 80.986 µs (Record), and
  157.779 vs 157.280 µs (Map). The shared prepared-row execution path does
  not introduce a measurable per-row allocation penalty.
- **POJO List at 100,000 rows:** SJF4J is **15.92×** faster than Spring,
  **7.37×** faster than MyBatis Auto and **10.99×** faster than MyBatis Explicit
  on this fixture.
- **Record List at 100,000 rows:** **110.12×** faster than Spring's
  `DataClassRowMapper` in this specific benchmark; this is not a general
  performance claim about all record-mapping workloads.
- **Allocation at 100,000 rows:** POJO and Record SJF4J List each allocate
  about **22.9 MB/op**; Map List allocates about **76.1 MB/op**.

**Fork stability:** Three 100,000-row Map cases exceed the 15% slowest/fastest
fork-spread threshold: SJF4J Map/List **1.18×**, SJF4J Map/Row **1.18×**,
and Spring Map/Row **1.19×**. Interpret small Map differences cautiously.
The full per-fork measurements are in the linked artifact.

## Methodology and limitations

- Full profile: **3 forks**, **5 × 1s warmup**, **5 × 1s measurement**,
  one thread. The 68 entries cover all model, mode, implementation and row-count
  combinations. Fork means are trimmed **independently for each latency case**;
  allocation is **not trimmed** or selected using latency forks.
- H2 tables are populated before measurement. A fresh `ResultSet` is opened
  in JMH Invocation setup and closed in teardown; SQL execution is outside
  timed mapping methods. The timed methods traverse the rows and materialize
  the full List. Invocation-level setup/teardown may affect allocation
  accounting and precision, particularly for one-row cases.
- Trial setup verifies the result size and fields of first/last rows for each
  benchmark path; missing or incorrect mappings fail setup.
- Spring's Map uses case-insensitive key lookup, unlike SJF4J's and MyBatis's
  Map implementations. This is a comparison of returned values rather than
  identical internal Map semantics.
- Shared runners are exploratory: no CPU model guard or affinity restriction
  was applied. Results are not end-to-end database/ORM query benchmarks.
  For regression claims, compare versions on the same controlled machine.

## Execute

```bash
./scripts/benchmark-jdbc-mapping.sh smoke
./scripts/benchmark-jdbc-mapping.sh quick
./scripts/benchmark-jdbc-mapping.sh full
./scripts/benchmark-jdbc-mapping.sh quick pojo
JDBC_SKIP_BUILD=1 ./scripts/benchmark-jdbc-mapping.sh smoke record

# Optional Linux CPU guard and core affinity:
JDBC_EXPECT_CPU_MODEL='Intel(R) Xeon(R) 6975P-C' ./scripts/benchmark-jdbc-mapping.sh full
JDBC_CPU_SET='1-2' ./scripts/benchmark-jdbc-mapping.sh quick map
```

| Setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 × 200ms | 5 × 1s |
| Measurement | 1 × 50ms | 2 × 200ms | 5 × 1s |
| Threads | 1 | 1 | 1 |
| Allocation profiler | No | Yes | Yes |
| Entries | 68 | 68 | 68 |

The launcher runs POJO, Record and Map groups sequentially, validating complete
JMH method/row coverage and raw fork data. Smoke checks correctness/completeness
rather than stable performance.

Local output is written under `build/performance/jdbc-mapping/<group>/`.
Aggregate a Full run using:

```bash
python3 scripts/analyze-jdbc-mapping-benchmark.py \
  build/performance/jdbc-mapping/pojo/jdbc-pojo.json \
  build/performance/jdbc-mapping/record/jdbc-record.json \
  build/performance/jdbc-mapping/map/jdbc-map.json \
  --output build/performance/jdbc-mapping/report.md
```

The [GitHub Actions JDBC Full workflow](https://github.com/sjf4j-projects/sjf4j/actions/workflows/jdbc-mapping-full-benchmark.yml)
runs all three groups on one runner and uploads the raw JSON, logs, report and
hardware metadata. An exact CPU model guard or Linux affinity can be supplied
for controlled runs.

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

**SJF4J version:** `v1.5.3-SNAPSHOT`.

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
List<AccountBean> result = (List<AccountBean>) handler.handleResultSets(select);
```

**SJF4J Compiled**

```java
@CompiledJdbcMapper
interface AccountMapper {
    AccountMapper INSTANCE = CompiledInstances.create(AccountMapper.class);

    JdbcRowMapper<AccountBean> prepare(ResultSetMetaData metadata);
    List<AccountBean> beans(ResultSet rs);
}

JdbcRowMapper<AccountBean> rowMapper = AccountMapper.INSTANCE.prepare(rs.getMetaData());
AccountBean account = rowMapper.map(rs);

List<AccountBean> result = AccountMapper.INSTANCE.beans(rs);
```

Spring mappers/extractors and MyBatis handlers are constructed outside timed
methods. MyBatis's internal handler is invoked directly, not via a full
`SqlSession` query.

## CPU baseline index

| CPU | Latest accepted report | JDK |
| --- | --- | --- |
| AMD EPYC 7763 | [2026-11-27](jdbc-mapping/2026-11-27-v1.5.3-snapshot.md) | Temurin 25.0.5.1+2 |
| Intel Xeon 6975P-C | [2026-11-13](jdbc-mapping/2026-11-13-v1.5.1-snapshot.md) | Temurin 25.0.5+8 |

Compare within-run competitor / Compiled ratios against the matching CPU's
accepted baseline. The latest published results below are from EPYC 7763;
they do not supersede the Xeon baseline.

## JVM — Temurin JDK 25

### Results — v1.5.3-SNAPSHOT (2026-11-27)

**Full JMH · 68/68 benchmark entries · 3 independent forks · Best 2 Mean.**
[GitHub Actions run #45914664049](https://github.com/sjf4j-projects/sjf4j/actions/runs/45914664049) ·
[Detailed Full report](jdbc-mapping/2026-11-27-v1.5.3-snapshot.md) ·
[Raw JSON, logs and runner metadata](https://github.com/sjf4j-projects/sjf4j/actions/runs/45914664049/artifacts/15164010906) ·
[Source commit `9509bc7`](https://github.com/sjf4j-projects/sjf4j/commit/9509bc7bf3a0bbd118e142ef4ceed860473f8929).

Temurin **25.0.5.1+2**, AMD **EPYC 7763**, 4 visible logical CPUs,
15 GiB RAM, shared GitHub-hosted Ubuntu 24.04 runner, unrestricted CPU affinity.

The primary performance metrics are **Spring / Compiled** and
**MyBatis / Compiled**; above 1× favors SJF4J. Latency (µs/op) is for
mapping the entire ResultSet, using independent Best 2 Mean of three forks.
Allocation (B/op) is the untrimmed JMH GC score.

#### Spring JDBC

POJO uses `BeanPropertyRowMapper`; Record uses `DataClassRowMapper`;
Map uses `ColumnMapRowMapper`. Both List and Row modes are measured.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 2.104 | 1.137 | 1.85× |
| POJO / List | 10 | 19.075 | 2.271 | 8.40× |
| POJO / List | 1,000 | 1,923.863 | 134.378 | 14.32× |
| POJO / List | 100,000 | 222,819.029 | 15,643.749 | 14.24× |
| POJO / Row | 1 | 2.093 | 1.112 | 1.88× |
| POJO / Row | 10 | 18.746 | 2.265 | 8.28× |
| POJO / Row | 1,000 | 1,978.577 | 135.692 | 14.58× |
| POJO / Row | 100,000 | 213,536.611 | 15,935.229 | 13.40× |
| Record / List | 1 | 15.037 | 1.149 | 13.09× |
| Record / List | 10 | 150.589 | 2.305 | 65.33× |
| Record / List | 1,000 | 15,566.454 | 132.514 | 117.47× |
| Record / List | 100,000 | 1,527,909.098 | 16,609.100 | 91.99× |
| Record / Row | 1 | 14.713 | 1.160 | 12.68× |
| Record / Row | 10 | 146.198 | 2.252 | 64.93× |
| Record / Row | 1,000 | 15,307.217 | 134.166 | 114.09× |
| Record / Row | 100,000 | 1,524,169.253 | 16,596.037 | 91.84× |
| Map / List | 1 | 0.741 | 0.430 | 1.73× |
| Map / List | 10 | 7.046 | 2.520 | 2.80× |
| Map / List | 1,000 | 600.620 | 242.039 | 2.48× |
| Map / List | 100,000 | 78,261.962 | 33,132.653 | 2.36× |
| Map / Row | 1 | 0.695 | 0.433 | 1.61× |
| Map / Row | 10 | 6.841 | 2.505 | 2.73× |
| Map / Row | 1,000 | 593.019 | 241.920 | 2.45× |
| Map / Row | 100,000 | 81,040.266 | 32,851.942 | 2.47× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratios compare bytes, not time.

| Case | Rows | Spring | Compiled | Spring / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / List | 1 | 5,832 | 4,189 | 1.39× |
| POJO / List | 10 | 28,008 | 6,139 | 4.56× |
| POJO / List | 1,000 | 2,515,332 | 235,073 | 10.70× |
| POJO / List | 100,000 | 289,672,645 | 22,884,350 | 12.66× |
| POJO / Row | 1 | 5,821 | 4,200 | 1.39× |
| POJO / Row | 10 | 27,998 | 6,133 | 4.56× |
| POJO / Row | 1,000 | 2,707,336 | 235,073 | 11.52× |
| POJO / Row | 100,000 | 270,472,645 | 22,884,348 | 11.82× |
| Record / List | 1 | 15,227 | 4,184 | 3.64× |
| Record / List | 10 | 121,999 | 6,192 | 19.70× |
| Record / List | 1,000 | 11,819,965 | 235,073 | 50.28× |
| Record / List | 100,000 | 1,181,679,688 | 22,884,371 | 51.64× |
| Record / Row | 1 | 15,232 | 4,184 | 3.64× |
| Record / Row | 10 | 122,004 | 6,155 | 19.82× |
| Record / Row | 1,000 | 11,819,969 | 235,073 | 50.28× |
| Record / Row | 100,000 | 1,191,279,684 | 22,884,381 | 52.06× |
| Map / List | 1 | 4,675 | 4,267 | 1.10× |
| Map / List | 10 | 16,040 | 10,675 | 1.50× |
| Map / List | 1,000 | 1,315,278 | 763,418 | 1.72× |
| Map / List | 100,000 | 131,272,900 | 76,072,679 | 1.73× |
| Map / Row | 1 | 4,675 | 4,261 | 1.10× |
| Map / Row | 10 | 16,045 | 10,616 | 1.51× |
| Map / Row | 1,000 | 1,315,278 | 763,418 | 1.72× |
| Map / Row | 100,000 | 131,272,940 | 76,072,678 | 1.73× |

#### MyBatis

Auto, Explicit and Cursor use the configured `DefaultResultSetHandler`;
Cursor results are fully collected. Only POJO and Map List workloads apply.

**Latency (µs/op, lower is better).** Best 2 Mean across 3 forks.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 5.582 | 1.137 | 4.91× |
| POJO / Explicit / List | 1 | 3.078 | 1.137 | 2.71× |
| POJO / Cursor / List | 1 | 5.529 | 1.137 | 4.86× |
| POJO / Auto / List | 10 | 13.716 | 2.271 | 6.04× |
| POJO / Explicit / List | 10 | 15.856 | 2.271 | 6.98× |
| POJO / Cursor / List | 10 | 14.063 | 2.271 | 6.19× |
| POJO / Auto / List | 1,000 | 931.700 | 134.378 | 6.93× |
| POJO / Explicit / List | 1,000 | 1,420.285 | 134.378 | 10.57× |
| POJO / Cursor / List | 1,000 | 954.372 | 134.378 | 7.10× |
| POJO / Auto / List | 100,000 | 108,316.283 | 15,643.749 | 6.92× |
| POJO / Explicit / List | 100,000 | 155,632.508 | 15,643.749 | 9.95× |
| POJO / Cursor / List | 100,000 | 112,909.473 | 15,643.749 | 7.22× |
| Map / Explicit / List | 1 | 2.798 | 0.430 | 6.51× |
| Map / Cursor / List | 1 | 2.747 | 0.430 | 6.39× |
| Map / Explicit / List | 10 | 13.087 | 2.520 | 5.19× |
| Map / Cursor / List | 10 | 13.177 | 2.520 | 5.23× |
| Map / Explicit / List | 1,000 | 1,190.690 | 242.039 | 4.92× |
| Map / Cursor / List | 1,000 | 1,191.117 | 242.039 | 4.92× |
| Map / Explicit / List | 100,000 | 132,894.111 | 33,132.653 | 4.01× |
| Map / Cursor / List | 100,000 | 146,203.262 | 33,132.653 | 4.41× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation; ratios compare bytes, not time.

| Case | Rows | MyBatis | Compiled | MyBatis / Compiled |
| --- | ---: | ---: | ---: | ---: |
| POJO / Auto / List | 1 | 11,245 | 4,189 | 2.68× |
| POJO / Explicit / List | 1 | 7,528 | 4,189 | 1.80× |
| POJO / Cursor / List | 1 | 11,213 | 4,189 | 2.68× |
| POJO / Auto / List | 10 | 20,136 | 6,139 | 3.28× |
| POJO / Explicit / List | 10 | 22,059 | 6,139 | 3.59× |
| POJO / Cursor / List | 10 | 19,941 | 6,139 | 3.25× |
| POJO / Auto / List | 1,000 | 1,020,907 | 235,073 | 4.34× |
| POJO / Explicit / List | 1,000 | 1,534,184 | 235,073 | 6.53× |
| POJO / Cursor / List | 1,000 | 1,042,197 | 235,073 | 4.43× |
| POJO / Auto / List | 100,000 | 108,081,719 | 22,884,350 | 4.72× |
| POJO / Explicit / List | 100,000 | 170,475,250 | 22,884,350 | 7.45× |
| POJO / Cursor / List | 100,000 | 108,081,537 | 22,884,350 | 4.72× |
| Map / Explicit / List | 1 | 7,472 | 4,267 | 1.75× |
| Map / Cursor / List | 1 | 7,448 | 4,267 | 1.75× |
| Map / Explicit / List | 10 | 24,699 | 10,675 | 2.31× |
| Map / Cursor / List | 10 | 24,664 | 10,675 | 2.31× |
| Map / Explicit / List | 1,000 | 1,998,142 | 763,418 | 2.62× |
| Map / Cursor / List | 1,000 | 1,998,023 | 763,418 | 2.62× |
| Map / Explicit / List | 100,000 | 204,076,604 | 76,072,679 | 2.68× |
| Map / Cursor / List | 100,000 | 204,076,285 | 76,072,679 | 2.68× |

#### Key findings

- **POJO List at 100,000 rows:** Compiled is **14.24×** faster than Spring,
  **6.92×** faster than MyBatis Auto and **9.95×** faster than MyBatis Explicit.
- **Record List:** Compiled reaches **117.47×** at 1,000 rows and **91.99×** at
  100,000 rows against Spring's `DataClassRowMapper` on this fixture.
- **Map List at 100,000 rows:** **2.36×** against Spring and **4.01×** against
  MyBatis Explicit. Map results are more fork-sensitive than POJO and Record.
- **List and Row:** The prepared-row execution path remains close in both modes
  at 1,000 rows; no additional per-row allocation is evident.
- **Allocation at 100,000 rows:** POJO and Record Compiled List allocate about
  **22.9 MB/op**, Map List about **76.1 MB/op**. The Spring / Compiled allocation
  ratios are **12.66×**, **51.64×** and **1.73×**, respectively.

**Fork stability:** Five Map cases exceed the 15% fork-spread threshold,
including Compiled List/Row at 100,000 rows. See the dated report for the
full fork means. Do not interpret cross-CPU ratio differences as regressions.

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

The [GitHub Actions JDBC Mapping Benchmark](https://github.com/sjf4j-projects/sjf4j/actions/workflows/jdbc-mapping-full-benchmark.yml)
runs POJO, Record and Map in parallel Ubuntu 24.04 jobs. Select only
`profile` (`smoke`, `quick`, `full`; default `smoke`). Pull requests run
Smoke. Each group records its own CPU and JDK; all competitors within a group
share the runner. Raw results and metadata are bundled; Full reports are
generated only when every group succeeds. CPU guard and affinity are local
script options only.

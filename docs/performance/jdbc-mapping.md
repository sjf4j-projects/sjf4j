# JDBC Mapping performance baseline

SJF4J **CompiledJdbcMapper** versus **Spring JDBC** and **MyBatis**, for
ResultSet-to-Java-object conversion. This suite deliberately excludes SQL
execution, connection establishment, transaction management and network I/O.
The workload is JdbcFullBenchmark in sjf4j-testbench.

## Comparison dependency versions

| Component | Version | Role |
| --- | --- | --- |
| Spring JDBC | 6.2.19 | BeanPropertyRowMapper, DataClassRowMapper, ColumnMapRowMapper, RowMapperResultSetExtractor |
| MyBatis | 3.5.19 | DefaultResultSetHandler, automatic and explicit ResultMap, Cursor |
| H2 | 2.3.232 | In-memory JDBC ResultSet provider |
| JMH | 1.37 | Benchmark harness |

Versions are declared in sjf4j-testbench/build.gradle.kts. Spring and MyBatis are
benchmark-only comparison dependencies, not SJF4J production dependencies.
Record each run's source SHA and environment rather than applying current
dependency versions retroactively to old results.

## JVM — Temurin JDK 25

### Suite 2 — prepared RowMapper baseline pending

PR #291 changes the SJF4J JDBC mapping implementation: the default
`ColumnNamingPolicy.INSENSITIVE_SNAKE` resolves SQL column aliases automatically,
and Row mode explicitly prepares `JdbcRowMapper<T>` once from
`ResultSetMetaData` before calling `map(ResultSet)` for each row.
The preparation cost is included in the Row benchmark operation.

A new complete Full run is required before publishing suite 2 performance
results. **The published measurements below are historical suite 1 results**
and must not be presented as measurements of the prepared RowMapper API.

### Historical results — suite 1 (1.5.1-SNAPSHOT, 2026-11-12)

**[First complete Full report](jdbc-mapping/spring-mybatis-v151snapshot-2026-11-12.md)** ·
[GitHub Actions run and raw artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43186767777)

**68/68 exact cases**, from source commit
[`c66d95c`](https://github.com/sjf4j-projects/sjf4j/commit/c66d95c8f75e5c37f6aff936aecd96c493122c8c).
Temurin **25.0.5**, AMD **EPYC 9V45**, shared GitHub-hosted runner.
Latency uses the independently computed **Best 2 Mean** of three forks.
All durations are **µs/op** (mapping the entire ResultSet), and allocations
are untrimmed **B/op**. Each comparison is shown as the competitor's measured
value, followed by **SJF4J Compiled** and the time ratio.

#### Spring JDBC

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

#### MyBatis

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

MyBatis has no equivalent direct `mapRow(ResultSet, int)` API in this suite.
Auto and Explicit map Lists; Cursor maps a streaming cursor into a List.
Only POJO and Map List results appear in this comparison.

#### Key findings

- At 100,000 rows, SJF4J POJO List allocates **22.9 million B/op** versus
  **260.9 million B/op** for Spring (11.40× allocation ratio).
  Record List allocates **22.9 million** versus **1,181.7 million B/op**
  for Spring (51.64×).
- **Two out of 68** exact cases exceed the 15% fork-spread warning:
  Map/List/Spring/100,000 (1.32×) and Map/Row/Compiled/1,000 (1.28×).
  Inspect the raw forks and avoid treating these as stable regressions.
- The run was on shared hardware and remains **exploratory**.
  Results do not measure SQL execution, network I/O or full ORM queries.
  Allocation reporting can include Invocation-level setup/teardown work.

### Workload matrix

The sole fixture is **Customer Account**, twelve scalar columns with matching
JavaBean and Record target shapes.

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

All rows have non-null scalar values. Date/time uses SQL TIMESTAMP mapped to
LocalDateTime; this deliberately excludes timezone conversion. The SQL result
columns retain snake_case names. In suite 2, SJF4J uses
`ColumnNamingPolicy.INSENSITIVE_SNAKE` for automatic property matching;
suite 1 used explicit `@Mapping` aliases. Spring uses JavaBean/constructor matching;
MyBatis Auto uses mapUnderscoreToCamelCase. No custom value codecs, JOJO dynamic
properties or complex nested object graphs are included.

Four row counts are independent exact workloads: **1 / 10 / 1,000 / 100,000**.

| Group | Mode | Implementations | Rows | JMH entries |
| --- | --- | --- | --- | ---: |
| POJO | List | Spring / MyBatis Auto / MyBatis Explicit / MyBatis Cursor / SJF4J Compiled | All four | 20 |
| POJO | Row | Spring / SJF4J Compiled | All four | 8 |
| Record | List | Spring / SJF4J Compiled | All four | 8 |
| Record | Row | Spring / SJF4J Compiled | All four | 8 |
| Map | List | Spring / MyBatis Explicit / MyBatis Cursor / SJF4J Compiled | All four | 16 |
| Map | Row | Spring / SJF4J Compiled | All four | 8 |
| **Total** | | | | **68** |

**List**: the implementation consumes the full ResultSet and returns a List.
Spring uses RowMapperResultSetExtractor; MyBatis uses its internal
DefaultResultSetHandler (not a full SqlSession query).

**Row**: the benchmark owns the same ResultSet.next loop and appends each mapped
row to a List. In suite 2, SJF4J calls the generated prepare method with
`ResultSetMetaData` once per invocation to obtain `JdbcRowMapper<T>`, then
calls `map(ResultSet)` for each current row. The preparation cost is included,
and no ResultSet is retained by the mapping object. Spring calls
`RowMapper.mapRow(ResultSet, int)`. Suite 1 used the old SJF4J
`(ResultSet, int)` API. All paths return an equivalent List.

**MyBatis Cursor**: call handleCursorResultSets, iterate the Cursor and
materialize it into an ArrayList. This is a streaming MyBatis pipeline, not
a direct RowMapper API; it is shown separately, never under Row mode.

### Mapping code examples

Core calls from [JdbcFullBenchmark.java](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/mapping/JdbcFullBenchmark.java).
All examples map the same 12-column ResultSet to `AccountBean`;
mapper initialization and SQL execution are outside JMH timing.

#### SJF4J Compiled

~~~java
@CompiledJdbcMapper
interface AccountMapper {
    List<AccountBean> beans(ResultSet rs);
    JdbcRowMapper<AccountBean> prepareBean(ResultSetMetaData metadata);
}

List<AccountBean> result = compiled.beans(rs);

// Suite 2 streaming/Row mapping:
JdbcRowMapper<AccountBean> prepared = compiled.prepareBean(rs.getMetaData());
while (rs.next()) {
    AccountBean account = prepared.map(rs);
    // process account
}
~~~

#### Spring JDBC

~~~java
BeanPropertyRowMapper<AccountBean> mapper =
        BeanPropertyRowMapper.newInstance(AccountBean.class);
RowMapperResultSetExtractor<AccountBean> extractor =
        new RowMapperResultSetExtractor<>(mapper);

List<AccountBean> result = extractor.extractData(rs);
~~~

#### MyBatis

~~~java
// The preconfigured handler uses Auto or Explicit ResultMap.
List<AccountBean> result =
        (List<AccountBean>) (List<?>) handler.handleResultSets(select);
~~~

Spring's mapper/extractor and MyBatis's `DefaultResultSetHandler` are created
outside the timed path. The benchmark also covers Record, Map, per-row mapping,
and MyBatis Cursor-to-List; see the linked source for those variants.
MyBatis uses `DefaultResultSetHandler` directly, not a full `SqlSession` query.

## Baseline policy

- **Suite version: 2** for new runs, exactly 68 measurements. Suite 1
  remains preserved in its dated historical report, using the original
  `(ResultSet, int)` Row API. Suite 2 uses explicitly prepared
  `JdbcRowMapper<T>` and automatic snake-case naming; its results must be
  recorded separately. Fixture, column type, method, return semantics,
  row-count matrix or implementation changes require a new suite version
  and a separate historical report.
- Populate H2 before JMH measurements. Open a fresh ResultSet during
  Invocation setup and close it in teardown. All timed methods traverse and
  materialize the same number of rows. The query execution is excluded.
- Construct mapper instances, Spring RowMapper/Extractor instances and MyBatis
  ResultMap/handler configuration outside timed mapping methods. ResultSet
  traversal is included in List and Row cases.
- Trial setup checks the full result size and every field in its first and last
  rows for **every benchmark path**. Absent or incorrect mappings fail setup,
  not silently produce a performance number.
- The 100,000-row path is intentionally a memory and GC stress case. Allocation
  and fork spread must be interpreted alongside latency.
- JMH Invocation setup/teardown can affect precision on extremely short
  operations, especially the single-row case; treat tiny latency differences
  cautiously and do not equate one-row JMH scores with individual setter cost.
- Run on JDK 25. Capture exact JDK patch, CPU, OS, source SHA and affinity.
  GitHub-hosted runners are exploratory, not a release-grade controlled baseline.

## Execute

~~~bash
./scripts/benchmark-jdbc-mapping.sh smoke
./scripts/benchmark-jdbc-mapping.sh quick
./scripts/benchmark-jdbc-mapping.sh full
./scripts/benchmark-jdbc-mapping.sh quick pojo
JDBC_SKIP_BUILD=1 ./scripts/benchmark-jdbc-mapping.sh smoke record
# Optional Linux CPU guard / affinity:
JDBC_EXPECT_CPU_MODEL='AMD EPYC 7763 64-Core Processor' ./scripts/benchmark-jdbc-mapping.sh full
JDBC_CPU_SET='1-2' ./scripts/benchmark-jdbc-mapping.sh quick map
~~~

| Setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 x 200ms | 5 x 1s |
| Measurement | 1 x 50ms | 2 x 200ms | 5 x 1s |
| Threads | 1 | 1 | 1 |
| Allocation profiler | No | Yes | Yes |
| Entries | 68 | 68 | 68 |

The launcher builds one JMH jar and runs POJO, Record, Map sequentially
by default. It validates exact JMH method/row parameter coverage, latency
units, finite scores, allocation and (Quick/Full) raw fork grid.
Smoke only establishes correctness/setup and completeness.

Output: build/performance/jdbc-mapping/<group>/ with jdbc-<group>.json,
jdbc-<group>.log and jdbc-<group>.meta.md. Aggregate a Full run:

~~~bash
python3 scripts/analyze-jdbc-mapping-benchmark.py \
  build/performance/jdbc-mapping/pojo/jdbc-pojo.json \
  build/performance/jdbc-mapping/record/jdbc-record.json \
  build/performance/jdbc-mapping/map/jdbc-map.json \
  --output build/performance/jdbc-mapping/report.md
~~~

## GitHub Actions Full (exploratory)

[JDBC Mapping Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/jdbc-mapping-full-benchmark.yml)
runs compilation and testbench tests, then per-group Smoke on pull requests.
Manual Full runs all three groups on a **single runner**, and uploads raw JSON,
logs, metadata, hardware information and the combined report.

workflow_dispatch supports a runner label (ubuntu-24.04 or self-hosted),
an optional exact CPU model guard and optional Linux taskset affinity.
A CPU guard rejects unexpected hardware but cannot reserve a CPU model.
For a release-grade comparison use the same dedicated, controlled machine.

## Maintaining history in the repository

1. Preserve completed Full runs under docs/performance/jdbc-mapping/ as
   dated, versioned reports. Never rewrite a historical snapshot.
2. Record source commit, precise JDK/CPU, suite version, full raw JSON and
   environment/affinity metadata alongside the run.
3. Only update the release index after checking all **68** exact entries and
   successful setup equivalence checks. Label partial/incomplete runs.
4. Avoid attributing shared-runner variance to code changes.

## Release index

| Release | Suite | Commit | Environment | Full report |
| --- | --- | --- | --- | --- |
| 1.5.1-SNAPSHOT (2026-11-12) | 1 | [`c66d95c`](https://github.com/sjf4j-projects/sjf4j/commit/c66d95c8f75e5c37f6aff936aecd96c493122c8c) | Temurin 25.0.5 / EPYC 9V45 / shared runner | [Full snapshot](jdbc-mapping/spring-mybatis-v151snapshot-2026-11-12.md) |

## Reporting and ranking results

**Latency**: AverageTime in **µs/op**, lower is better; every operation maps
the entire result of N rows, not one row. For Full, independently average
the five JMH iterations in each fork, discard the single slowest fork of
**each exact method and row count**, then average the remaining two
(**Best 2 Mean**). Preserve the untrimmed three-fork JMH mean.

**Allocation**: report JMH gc.alloc.rate.norm in B/op, untrimmed; it may include
work in Invocation setup/teardown and should not be claimed as pure mapper-only
allocation, especially for a single row. Do **not**
select allocation forks according to latency. Ratios are
Spring / Compiled or MyBatis / Compiled on unrounded scores; above 1x means
Compiled has lower latency/allocation. Do not compute a MyBatis / Compiled
Row ratio: there is no equivalent direct MyBatis RowMapper.

The reporter groups Spring and MyBatis separately, with **Latency** and
**Allocation** tables for each competitor. Each table follows the same
Case / Rows / competitor / Compiled / competitor / Compiled convention as
binding.md and mapping.md, followed by fork-spread diagnostics. Flag cases
where slowest/fastest fork means exceed 1.15x and investigate instability.

## Interpreting changes

- A 1-row result emphasizes framework/column lookup overhead; 100,000 rows
  emphasizes large-list materialization and GC. Do not average the four
  counts into a single headline score.
- Results depend on H2 ResultSet behavior and the twelve types in this fixture.
  They cannot be extrapolated to actual JDBC driver/network throughput.
- MyBatis Cursor includes cursor machinery and List materialization, while
  MyBatis automatic and explicit mappings use internal handlers. Their
  results must not be described as end-to-end ORM query performance.
- Spring's Map has distinct case-insensitive lookup semantics and allocation.
  Compare exact returned values, not assumed identical internal data structures.
- The Best 2 Mean statistic intentionally biases toward lower observed
  latency; retain JMH raw data and use stable hardware for regressions.

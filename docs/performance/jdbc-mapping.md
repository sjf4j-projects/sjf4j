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

### Results — 1.5.1-SNAPSHOT (2026-11-12)

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

#### MyBatis

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

MyBatis has no equivalent direct `mapRow(ResultSet, int)` API in this suite.
Its Auto and Explicit paths map Lists; Cursor materializes a streaming cursor
into a List. Therefore, only POJO and Map List cases appear in this table.
The Spring table includes List and Row for POJO, Record and Map.

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
columns retain snake_case names, and SJF4J uses explicit Mapping annotations
for different Java property names. Spring uses JavaBean/constructor matching;
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

**Row**: the benchmark owns the same ResultSet.next loop, appends each mapped
row to a List, and invokes CompiledJdbcMapper.mapRow(ResultSet, int) or Spring
RowMapper.mapRow(ResultSet, int). The returned List ensures identical work.

**MyBatis Cursor**: call handleCursorResultSets, iterate the Cursor and
materialize it into an ArrayList. This is a streaming MyBatis pipeline, not
a direct RowMapper API; it is shown separately, never under Row mode.

## Baseline policy

- **Suite version: 1**, exactly 68 measurements. Fixture, column type, method,
  return semantics, row-count matrix or implementation changes require a new
  suite version, and a separate historical report.
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

The reporter creates a Spring comparison table and a MyBatis comparison
table, each including absolute latency, allocation and time ratios for the
relevant shapes and modes, followed by fork-spread diagnostics. Flag cases
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

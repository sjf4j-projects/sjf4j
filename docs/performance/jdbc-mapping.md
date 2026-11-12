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

### Results — pending first complete Full run

There is no published Full baseline for the **suite-2** 12-column workload.
Do not reuse the original 3-column / 1,000-row benchmark's numbers: it had a
different fixture and measurement configuration. After a complete Full
execution, publish a dated report under docs/performance/jdbc-mapping/ and
link it here. Shared GitHub-hosted runners produce exploratory measurements.

#### POJO

Compare SJF4J Compiled, Spring BeanPropertyRowMapper and MyBatis
automatic/explicit result maps in List mode. Compare only Compiled and Spring
in Row mode. MyBatis Cursor is separately labelled as streaming-to-List.

#### Record

Compare SJF4J Compiled against Spring DataClassRowMapper in List and Row modes.
MyBatis record constructor mapping is not included in suite 2: an equivalent,
verified comparison is required before adding it.

#### Map

Compare SJF4J Compiled, Spring ColumnMapRowMapper and MyBatis explicit
ResultMap in List mode, as well as Compiled and Spring in Row mode.
Map implementations differ: Spring uses case-insensitive column names,
while SJF4J and MyBatis have different Map implementation/lookup overhead.
All outputs must agree on the twelve exact column labels and values.

#### Key findings

Populate this section only after a verified Full run, using the raw fork
measurements and allocation scores. Report regressions as well as speedups.

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
columns retain snake_case names, and SJF4J uses the default
`ColumnNamingPolicy.INSENSITIVE_SNAKE` for automatic property matching.
Spring uses JavaBean/constructor matching;
MyBatis Auto uses mapUnderscoreToCamelCase. No custom value codecs, JOJO dynamic
properties or complex nested object graphs are included.

Four row counts are independent exact workloads: **1 / 10 / 1,000 / 100,000**.

| Group | Mode | Implementations | Rows | JMH entries |
| --- | --- | --- | --- | ---: |
| POJO | List | Compiled / Spring / MyBatis Auto / MyBatis Explicit / MyBatis Cursor | All four | 20 |
| POJO | Row | Compiled / Spring | All four | 8 |
| Record | List | Compiled / Spring | All four | 8 |
| Record | Row | Compiled / Spring | All four | 8 |
| Map | List | Compiled / Spring / MyBatis Explicit / MyBatis Cursor | All four | 16 |
| Map | Row | Compiled / Spring | All four | 8 |
| **Total** | | | | **68** |

**List**: the implementation consumes the full ResultSet and returns a List.
Spring uses RowMapperResultSetExtractor; MyBatis uses its internal
DefaultResultSetHandler (not a full SqlSession query).

**Row**: the benchmark owns the ResultSet.next loop and appends mapped rows
to a List. SJF4J calls `CompiledJdbcMapper.prepare(ResultSetMetaData)` once
per invocation to obtain `JdbcRowMapper<T>`, then calls `map(ResultSet)`
per row. Spring uses `RowMapper.mapRow(ResultSet, int)`.
The SJF4J preparation cost is included in the measured operation; no
implicit per-ResultSet cache is used. The returned List ensures identical work.

**MyBatis Cursor**: call handleCursorResultSets, iterate the Cursor and
materialize it into an ArrayList. This is a streaming MyBatis pipeline, not
a direct RowMapper API; it is shown separately, never under Row mode.

## Baseline policy

- **Suite version: 2**, exactly 68 measurements. The previous suite used
  implicit current-row lookup; this suite uses explicit prepared row mapping. Fixture, column type, method,
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
| Pending | 1 | — | — | — |

## Reporting and ranking results

**Latency**: AverageTime in **µs/op**, lower is better; every operation maps
the entire result of N rows, not one row. For Full, independently average
the five JMH iterations in each fork, discard the single slowest fork of
**each exact method and row count**, then average the remaining two
(**Best 2 Mean**). Preserve the untrimmed three-fork JMH mean.

**Allocation**: report JMH gc.alloc.rate.norm in B/op, untrimmed; do **not**
select allocation forks according to latency. Ratios are
Spring / Compiled or MyBatis / Compiled on unrounded scores; above 1x means
Compiled has lower latency/allocation. Do not compute a MyBatis / Compiled
Row ratio: there is no equivalent direct MyBatis RowMapper.

The reporter creates separate latency and allocation tables for List and
Row within each group, as well as fork-spread diagnostics. Flag cases
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

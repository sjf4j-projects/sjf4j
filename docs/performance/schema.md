# Schema performance baseline

SJF4J **Runtime** and **CompiledValidator** versus **NetworkNT** for JSON Schema
validation of the Creek model using Draft 2020-12. The suite also measures
separate serialization/deserialization roundtrips with optional validation.
The primary workload is
[`CreekSchemaBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/schema/CreekSchemaBenchmark.java).

## Comparison dependency versions

**SJF4J version:** `v1.5.0`.

Declared versions for the published benchmark (from the [benchmark source commit](https://github.com/sjf4j-projects/sjf4j/commit/c1d380c6327a32d8302d16738cb63dbd5fd2be5f)):

| Component | Version | Role |
| --- | --- | --- |
| NetworkNT JSON Schema Validator | `2.0.5` | External JSON Schema validation comparison |
| Jackson2 Databind | `2.22.3` | JsonNode validation input and roundtrip comparison |
| JMH | `1.37` | Benchmark harness |

## Code examples

Core validation calls from the Creek benchmark (imports and fixture setup
omitted). Schemas, validators, and inputs are prepared outside timed methods.

**NetworkNT**

```java
Schema validator = SchemaRegistry
        .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
        .getSchema(schema);
validator.initializeValidators();
boolean valid = validator.validate(jsonNode, OutputFormat.BOOLEAN);
```

**SJF4J Runtime**

```java
SchemaPlan plan = JsonSchema.fromJson(schema)
        .createPlan(new SchemaRegistry(SchemaDialect.DRAFT_2020_12));
boolean valid = plan.isValid(jsonNode, false);
```

**SJF4J Compiled**
```java
@CompiledValidator(fallback = true)
interface CreekValidator {
    CreekValidator INSTANCE = CompiledInstances.create(CreekValidator.class);
    
    @ValidJsonSchema(ref = "classpath:/schema/creek-2020-12.json")
    boolean isValid(TestModel value);
}

boolean valid = CreekValidator.INSTANCE.isValid(model);
```

The compiled POJO example uses a different input representation from the
NetworkNT/Runtime `JsonNode` comparison. See
[`CreekSchemaBenchmark`](../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/schema/CreekSchemaBenchmark.java)
for the exact benchmark methods and setup.

## JVM — Temurin JDK 25

### Results — v1.5.0 (2026-11-09)
**JDK 25 · JMH · 3 forks · 5 × 400ms warmup/measurement · original JMH mean · µs/op (lower is better)**

#### Validation

| Input | NetworkNT | Runtime | NetworkNT / Runtime | Compiled | Runtime / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| POJO | — | 3.107 | — | **0.058** | **53.6×** |
| Map | — | 2.295 | — | **0.992** | **2.31×** |
| Jackson2 JsonNode | 9.758 | **4.051** | **2.41×** | — | — |

Ratios divide the left implementation's latency by the right implementation's latency; above 1× means the right implementation is faster. A dash indicates that no comparable result was measured. The NetworkNT and SJF4J Runtime results use the same pre-parsed Jackson2 `JsonNode`. Compiled validation is benchmarked on POJO and Map only.

#### Roundtrip

Each roundtrip serializes the Creek POJO to a JSON `byte[]` and deserializes it back to a POJO. The validation variants additionally run schema validation **on the deserialized POJO** (not during parsing): `Jackson2 + Runtime Validator` uses SJF4J Runtime validation after Jackson2 binding, while `CompiledBinder + CompiledValidator` uses compiled binding and compiled validation. The Jackson2 and CompiledBinder-only variants perform binding without validation.

| Implementation | µs/op | B/op |
| --- | ---: | ---: |
| Jackson2 | 7.024 | 7,696 |
| CompiledBinder | 5.829 | 6,824 |
| Jackson2 + Runtime Validator | 10.779 | 9,816 |
| **CompiledBinder + CompiledValidator** | **5.888** | **6,824** |

**Key result:** CompiledBinder + CompiledValidator is **1.19× faster** than a plain Jackson2 roundtrip **without validation**, with **11.3% lower allocation**.

Validation uses the same prebuilt Creek fixture; the NetworkNT comparison uses the same pre-parsed JsonNode. The compiled validator has `fallback=true`. These results are from GitHub-hosted runners, not a hardware-independent performance guarantee.

**[Full results and methodology](schema/creek-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629)**

## Baseline policy

- **Validation inputs:** use the prebuilt Creek fixture and Draft 2020-12 schema; the NetworkNT and SJF4J Runtime JsonNode results validate the same pre-parsed Jackson2 JsonNode. NetworkNT does not have a directly comparable POJO/Map result in this matrix.
- **Compiled validator:** `fallback=true`; validation uses known-valid inputs with non-strict SJF4J `format` handling. POJO/Map compiled results must not be compared against NetworkNT JsonNode as equivalent representations.
- **Roundtrip boundary:** serialize a Creek POJO to a new JSON `byte[]` and deserialize it; the validation variants additionally validate the deserialized POJO. The Jackson2-only baseline does **not** perform validation.
- **Historical v1.5.0 profile:** 3 JMH forks, 5 × 400ms warmup and 5 × 400ms measurement, original untrimmed JMH mean. **Do not relabel these published values as Best 2 Mean.**
- New Full runs use 3 forks with 5 × 1s warmup and 5 × 1s measurement and are reported using Best 2 Mean. A direct v1.5.0-to-new-Full comparison changes the timing protocol and is not a controlled A/B test.
- Smoke is a correctness check, Quick is diagnostic, and GitHub-hosted Full is exploratory rather than a controlled, same-machine release baseline.

## Execute

JDK 25 is required. The script runs both Validation (six methods) and Roundtrip (four methods) by default and builds the testbench JMH jar once.

```bash
./scripts/benchmark-schema.sh smoke
./scripts/benchmark-schema.sh quick
./scripts/benchmark-schema.sh full
# Optional focused groups:
./scripts/benchmark-schema.sh full validation
./scripts/benchmark-schema.sh full roundtrip
```

| JMH setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 | 1 | 3 |
| Warmup | 0 | 2 × 200ms | 5 × 1s |
| Measurement | 1 × 50ms | 2 × 200ms | 5 × 1s |
| Methods | All 10 by default | All 10 by default | All 10 by default |
| GC profiler | No | Yes | Yes |

Quick and Full save per-group JMH JSON, logs and metadata under `build/performance/schema/<group>/`. Set `SCHEMA_SKIP_BUILD=1` to reuse an existing JMH jar when running an individual group.

## GitHub Actions Full (exploratory)

[Schema Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/schema-full-benchmark.yml) → **Run workflow**.

The workflow builds a shared JMH jar, runs Validation and Roundtrip Full in independent GitHub-hosted Ubuntu 24.04 jobs, and publishes both per-group raw artifacts and a combined report. Pull requests run Smoke checks only.

The published v1.5.0 results are from [Actions run #42543397629](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629). Validation and Roundtrip used separate runner jobs; interpret direct comparisons within the same group.

## Maintaining history in the repository

1. Run both Full groups at a fixed source commit and record the exact JDK, OS, CPU and JMH configuration.
2. Keep the original raw JMH JSON, fork measurements and environment metadata with the run artifacts. Publish a dated, versioned report under `docs/performance/schema/` without overwriting older results.
3. Update the release index with the source SHA, JDK and report link. Note whether the report uses original JMH mean or Best 2 Mean.
4. For a suspected regression, rerun baseline and candidate with matching profiles and data on equivalent hardware; inspect allocation and per-fork variability as well as latency.

## Release index

| Release | Commit | JDK | Metric | Results |
| --- | --- | --- | --- | --- |
| v1.5.0 (GitHub Actions; exploratory) | [`c1d380c6327a`](https://github.com/sjf4j-projects/sjf4j/commit/c1d380c6327a32d8302d16738cb63dbd5fd2be5f) | 25.0.5 | Original 3-fork JMH mean, 400ms | [Creek Full report](schema/creek-v150-2026-11-09.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629) |

## Reporting and ranking results

- **Current Full metric: Best 2 Mean**, calculated independently for every exact benchmark method. Average the five measurement iterations **within each fork**, discard that method's slowest fork, then average the remaining two. Do not share excluded forks across methods or implementations.
- The **historical v1.5.0 tables above use original 3-fork JMH mean**, not Best 2. Preserve their published numbers and aggregation label.
- Report AverageTime in **µs/op** (lower is better). Ratios use unrounded baseline / candidate latency; above 1× indicates a faster candidate. Use a dash when no equivalent result was measured.
- Preserve all three fork means, original JMH score and score error. The JMH error is **not** a Best 2 Mean confidence interval.
- Report **B/op** using the untrimmed `gc.alloc.rate.norm` score, not allocation values selected by the latency fork ranking.

## Interpreting changes

- Treat Validation and Roundtrip as separate workloads, and do not compare different input representations as equivalent.
- The approximately 1.19× roundtrip comparison includes validation **only** on the CompiledBinder + CompiledValidator side; it is not a measurement of isolated validator overhead.
- For a controlled regression claim, match hardware, JDK build, source fixture, benchmark protocol and statistical aggregation. Shared GitHub runners are diagnostic, not a hardware-independent performance guarantee.

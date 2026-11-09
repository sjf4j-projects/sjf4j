# Creek Schema performance benchmark

This benchmark uses the existing `CreekSchemaBenchmark`, the exact
`classpath:/schema/creek-2020-12.json` schema, and the existing `ModelState`
fixture. The goal is to preserve the key SJF4J performance claim:
**CompiledBinder roundtrip plus CompiledValidator validation can outperform
a plain Jackson2 roundtrip without validation.**

## Comparison groups

### Validation (six methods)

| Method | Input | Implementation |
| --- | --- | --- |
| `pojo_isValid_runtime` | `TestModel` POJO | SJF4J `SchemaPlan` |
| `pojo_isValid_compiled` | `TestModel` POJO | `@CompiledValidator` |
| `map_isValid_runtime` | `Map<String,Object>` | SJF4J `SchemaPlan` |
| `map_isValid_compiled` | `Map<String,Object>` | `@CompiledValidator` |
| `jsonNode_isValid_runtime` | Jackson2 `JsonNode` | SJF4J `SchemaPlan` |
| `jsonNode_isValid_networknt` | Same Jackson2 `JsonNode` | NetworkNT 2.0.5 |

All instances are prepared outside JMH measurements from the same Creek
model. Both validators on `JsonNode` receive the **same object instance**.
NetworkNT is the Jackson2-based JSON Schema baseline, not plain Jackson
databind; it uses cached Draft 2020-12 `Schema` and `OutputFormat.BOOLEAN`.
NetworkNT does not validate POJO/Map directly, so compare its score primarily
to `jsonNode_isValid_runtime`, **not** to POJO or Map as equal representations.

### Roundtrip (four methods)

| Method | Serialization + deserialization | Validation |
| --- | --- | --- |
| `roundtrip_jackson` | Jackson2 | No |
| `roundtrip_compiled` | SJF4J CompiledBinder (Jackson2 backend) | No |
| `roundtrip_isValid_runtime` | Jackson2 | SJF4J Runtime |
| `roundtrip_isValid_compiled` | SJF4J CompiledBinder | CompiledValidator |

**Primary KPI**: `roundtrip_jackson` time divided by
`roundtrip_isValid_compiled` time. A ratio above 1 means the complete
compiled pipeline is faster **despite doing additional validation**.
This is a Jackson2 **roundtrip** comparison, not parsing alone.

## Measurement contract

- JDK 25, one thread, JMH AverageTime in `us/op` (lower is better).
- Full: 3 independent forks, 5 × 400ms warmup, 5 × 400ms measurement,
  `-prof gc` for `gc.alloc.rate.norm` in B/op.
- Quick: 1 fork, 2 × 200ms warmup, 2 × 200ms measurement.
- Smoke: no fork and 1 × 50ms measurement, for correctness only.
- The unchanged Creek Schema and fixtures are created/preloaded in JMH `@Setup`.
- `isValid()` is fail-fast and uses `strictFormat=false` for all SJF4J paths.
  NetworkNT uses its boolean fail-fast output with Draft 2020-12 default
  non-asserting `format`. Every setup checks the validators return true for
  the same known-valid fixture.
- The compiled Creek validator intentionally uses `fallback=true` for
  unsupported paths; do not present this as a fallback-free benchmark.
- The benchmark does not include invalid-instance cases or Schema compilation.
- Repository code includes other Creek methods for local profiling, but the
  Full suite explicitly selects only these ten methods.

## Run locally

```bash
./scripts/benchmark-schema.sh smoke validation
./scripts/benchmark-schema.sh smoke roundtrip
./scripts/benchmark-schema.sh quick validation
./scripts/benchmark-schema.sh full validation
./scripts/benchmark-schema.sh full roundtrip
```

Results, logs and metadata go to `build/performance/schema/<group>/`.
Use `SCHEMA_SKIP_BUILD=1` if the JMH Jar was already built.
To produce a combined Markdown summary from a local Full run:

```bash
python3 scripts/analyze-schema-benchmark.py \
  build/performance/schema/validation/schema-validation.json \
  build/performance/schema/roundtrip/schema-roundtrip.json \
  --output build/performance/schema/report.md
```

## GitHub Actions

Run **Schema Full Benchmark (JDK 25)** manually from the Actions tab.
It builds the JMH jar once and runs Validation and Roundtrip in two separate
Ubuntu 24.04 jobs, then generates `report.md` and one Combined Artifact
containing both groups' raw JMH JSON, independent-fork data, GC metrics,
environment metadata, CPU info and full JMH logs.
The full benchmark is **manual-only**, while PR changes to the benchmark
run a short JDK 25 smoke check to catch setup/semantic mismatches.

Results from shared GitHub-hosted runners are **exploratory**, not release
baselines. Treat differences smaller than run-to-run variation cautiously;
retain original raw JMH JSON when asking AI to analyze trends.

## Historical results

- [2026-11-09 Creek Schema Full Benchmark baseline](baselines/creek-2026-11-09.md) — all 10 measured methods, allocation data, JMH errors, three independent fork means, exact run and commit references, and website-ready summary. Note that the linked original Actions artifact has a 30-day retention period; archive raw JMH JSON separately for byte-for-byte reproducibility.

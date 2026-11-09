# Schema Benchmark

SJF4J JSON Schema validation and roundtrip performance, using the Creek model and Draft 2020-12 schema.

## Results — v1.5.0 (2026-11-09)

**JDK 25 · JMH · 3 forks · µs/op (lower is better)**

### Validation

| Input | NetworkNT | Runtime | Compiled |
| --- | ---: | ---: | ---: |
| POJO | — | 3.107 | **0.058** (**53.6× vs Runtime**) |
| Map / JsonNode | 9.758 (JsonNode) | 2.295 (Map)<br>4.051 (JsonNode, **2.41× vs NetworkNT**) | **0.992** (Map, **2.31× vs Runtime**) |

The NetworkNT comparison uses the same pre-parsed Jackson2 `JsonNode` as SJF4J Runtime. The Compiled result in this row uses a `Map`, and its speedup is relative to Runtime on `Map`, not NetworkNT.

### Roundtrip

Each roundtrip serializes the Creek POJO to a JSON `byte[]` and deserializes it back to a POJO. The validation variants additionally run schema validation **on the deserialized POJO** (not during parsing): `Jackson2 + Runtime Validator` uses SJF4J Runtime validation after Jackson2 binding, while `CompiledBinder + CompiledValidator` uses compiled binding and compiled validation. The Jackson2 and CompiledBinder-only variants perform binding without validation.

| Implementation | µs/op | B/op |
| --- | ---: | ---: |
| Jackson2 | 7.024 | 7,696 |
| SJF4J CompiledBinder | 5.829 | 6,824 |
| Jackson2 + Runtime Validator | 10.779 | 9,816 |
| **CompiledBinder + CompiledValidator** | **5.888** | **6,824** |

**Key result:** CompiledBinder + CompiledValidator is **1.19× faster** than a plain Jackson2 roundtrip **without validation**, with **11.3% lower allocation**.

Validation uses the same prebuilt Creek fixture; the NetworkNT comparison uses the same pre-parsed JsonNode. The compiled validator has `fallback=true`. These results are from GitHub-hosted runners, not a hardware-independent performance guarantee.

**[Full results and methodology](schema/creek-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629)**

## Run

[Schema Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/schema-full-benchmark.yml) → **Run workflow**.

Or run locally:

```bash
./scripts/benchmark-schema.sh full validation
./scripts/benchmark-schema.sh full roundtrip
```

The workflow automatically generates a combined Markdown report and raw JMH artifacts.

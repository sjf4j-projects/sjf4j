# Schema Benchmark

SJF4J JSON Schema validation and roundtrip performance, using the Creek model and Draft 2020-12 schema.

## Results — v1.5.0 (2026-11-09)

**JDK 25 · JMH · 3 forks · µs/op (lower is better)**

### Validation

| Input | Runtime | Compiled | Speedup |
| --- | ---: | ---: | ---: |
| POJO | 3.107 | **0.058** | **53.6×** |
| Map | 2.295 | **0.992** | **2.31×** |
| Jackson2 JsonNode | 4.051 (SJF4J) | 9.758 (NetworkNT) | **2.41× SJF4J** |

### Roundtrip

| Implementation | µs/op | B/op |
| --- | ---: | ---: |
| Jackson2 | 7.024 | 7,696 |
| SJF4J CompiledBinder | 5.829 | 6,824 |
| Jackson2 + Runtime Validator | 10.779 | 9,816 |
| **CompiledBinder + CompiledValidator** | **5.888** | **6,824** |

**Key result:** CompiledBinder + CompiledValidator is **1.19× faster** than a plain Jackson2 roundtrip **without validation**, with **11.3% lower allocation**.

Validation uses the same prebuilt Creek fixture; the NetworkNT comparison uses the same pre-parsed JsonNode. The compiled validator has `fallback=true`. These results are from GitHub-hosted runners, not a hardware-independent performance guarantee.

**[Full results and methodology](baselines/creek-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629)**

## Run

[Schema Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/schema-full-benchmark.yml) → **Run workflow**.

Or run locally:

```bash
./scripts/benchmark-schema.sh full validation
./scripts/benchmark-schema.sh full roundtrip
```

The workflow automatically generates a combined Markdown report and raw JMH artifacts.

# Schema Benchmark

SJF4J JSON Schema validation and roundtrip performance, using the Creek model and Draft 2020-12 schema.

## Results — v1.5.0 (2026-11-09)

**JDK 25 · JMH · 3 forks · 5 × 400ms warmup/measurement · original JMH mean · µs/op (lower is better)**

### Validation

| Input | NetworkNT | Runtime | NetworkNT / Runtime | Compiled | Runtime / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| POJO | — | 3.107 | — | **0.058** | **53.6×** |
| Map | — | 2.295 | — | **0.992** | **2.31×** |
| Jackson2 JsonNode | 9.758 | **4.051** | **2.41×** | — | — |

Ratios divide the left implementation's latency by the right implementation's latency; above 1× means the right implementation is faster. A dash indicates that no comparable result was measured. The NetworkNT and SJF4J Runtime results use the same pre-parsed Jackson2 `JsonNode`. Compiled validation is benchmarked on POJO and Map only.

### Roundtrip

Each roundtrip serializes the Creek POJO to a JSON `byte[]` and deserializes it back to a POJO. The validation variants additionally run schema validation **on the deserialized POJO** (not during parsing): `Jackson2 + Runtime Validator` uses SJF4J Runtime validation after Jackson2 binding, while `CompiledBinder + CompiledValidator` uses compiled binding and compiled validation. The Jackson2 and CompiledBinder-only variants perform binding without validation.

| Implementation | µs/op | B/op |
| --- | ---: | ---: |
| Jackson2 | 7.024 | 7,696 |
| CompiledBinder | 5.829 | 6,824 |
| Jackson2 + Runtime Validator | 10.779 | 9,816 |
| **CompiledBinder + CompiledValidator** | **5.888** (**1.19x** vs Jackson2) | **6,824** |

**Key result:** CompiledBinder + CompiledValidator is **1.19× faster** than a plain Jackson2 roundtrip **without validation**, with **11.3% lower allocation**.

Validation uses the same prebuilt Creek fixture; the NetworkNT comparison uses the same pre-parsed JsonNode. The compiled validator has `fallback=true`. These results are from GitHub-hosted runners, not a hardware-independent performance guarantee.

**[Full results and methodology](schema/creek-v150-2026-11-09.md)** · **[GitHub Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42543397629)**

## Run

Current **Full** profile: JDK 25, 3 independent forks, 5 × 1s warmup and 5 × 1s measurement.

**Reporting:** Use **Best 2 Mean** for every exact benchmark method: average the five measurements within each fork, discard that method's slowest fork, and average the remaining two. Compute speedup ratios from these per-method Best 2 Means. Preserve the original JMH 3-fork mean, per-fork results, score error, and B/op; the JMH score error does **not** apply to Best 2 Mean, and B/op remains the untrimmed JMH value.

The v1.5.0 results above are the original **400ms, 3-fork JMH means**, not Best 2 Mean. They remain unchanged as a historical snapshot. Re-run both implementations with the same timing settings for controlled comparisons.

[Schema Full Benchmark (JDK 25)](https://github.com/sjf4j-projects/sjf4j/actions/workflows/schema-full-benchmark.yml) → **Run workflow**.

Or run locally:

```bash
./scripts/benchmark-schema.sh full validation
./scripts/benchmark-schema.sh full roundtrip
```

The workflow automatically generates a combined Markdown report and raw JMH artifacts.

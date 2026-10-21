# Creek Runtime performance investigation

Independent branch from `main`; no changes from PR #68 and no changes to CompiledBinder.

## Reproduction and result sources

- Baseline JMH: [run 39576471877](https://github.com/sjf4j-projects/sjf4j/actions/runs/39576471877) (valid isolated subtype samples and Creek count results).
- Corrected complete JMH: [run 39577820001](https://github.com/sjf4j-projects/sjf4j/actions/runs/39577820001); [raw JSON and logs](https://github.com/sjf4j-projects/sjf4j/actions/runs/39577820001/artifacts/12335531265).
- JDK 17, shared GitHub Actions runner, forked JMH, GC allocation profiler. Results are indicative: the absolute timing changed substantially between runs; within-run relative directions and B/op are more informative.
- Inputs: same prepared JSON for Runtime/Jackson native, parse anew on every measured operation, `@type` first unless explicitly tested otherwise.
- JMH setup checks output equality. Polymorphic `listJson` is extracted from the strongly typed Creek JSON tree so the discriminator is not discarded by root List serialization. Workflow uses `set -o pipefail` to propagate JMH failures.

## Full Creek, with increasing polymorphic elements

Scores in `µs/op`. Columns compare operations in the same corrected run:

| OneOf elements | Jackson native | SJF4J Runtime | Native B/op | Runtime B/op |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 1.318 | 1.620 | 3592 | 3760 |
| 1 | 1.602 | 1.904 | 4008 | 4088 |
| 4 | 2.209 | 2.896 | 4928 | 5136 |
| 8 | 2.840 | 4.028 | 6128 | 6688 |

With 8 elements, the runtime-vs-native difference is 1.188 µs and 560 B/op. The 0-element baseline accounts for 0.302 µs and 168 B/op of that. Incrementally the eight elements add 0.886 µs and 392 B/op more on Runtime than native.

## Polymorphic List extracted from the same Creek JSON

| OneOf elements | Jackson native | SJF4J Runtime | Native B/op | Runtime B/op |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 0.171 | 0.111 | 728 | 632 |
| 1 | 0.394 | 0.307 | 1120 | 872 |
| 4 | 0.978 | 1.126 | 2064 | 2104 |
| 8 | 1.714 | 2.168 | 3232 | 3480 |

0→8 incremental cost: native 1.543 µs / 2504 B vs Runtime 2.057 µs / 2848 B; Runtime therefore spends ~0.514 µs / 344 B more over the same eight list elements. This explains much of the full Creek incremental gap. Separate JMH methods must not be treated as an exact additive timing decomposition due to different inlining contexts.

## Same subtype JSON with direct POJO versus OneOf

All `µs/op`, `B/op` is in parentheses:

| Mode | Score |
| --- | ---: |
| PolyTypeA UUID: Jackson native as PolyBase | 0.284 (832 B) |
| PolyTypeA UUID: Runtime as PolyTypeA | 0.271 (664 B) |
| PolyTypeA UUID: Runtime as PolyBase, type first | 0.277 (712 B) |
| PolyTypeA UUID: Runtime as PolyBase, type last | 0.343 (912 B) |
| PolyTypeB double: Jackson native as PolyBase | 0.245 (912 B) |
| PolyTypeB double: Runtime as PolyTypeB | 0.341 (960 B) |
| PolyTypeB double: Runtime as PolyBase, type first | 0.340 (1008 B) |

### Conclusions grounded in code

1. `PolyTypeA(UUID)` is **not slower** than Jackson native in the isolated case: `StreamingIO.readValueCodec()` uses the cached streaming reader and `Jackson2Reader.readUUID()` parses the native character buffer.
2. `PolyTypeB(double)` **is** slower even *without* OneOf dispatch (0.341 vs 0.245 µs), suggesting a creator-parameter/primitive boxing and conversion cost rather than the discriminator matcher.
3. With discriminator first, Runtime OneOf adds memory (A +48 B, B +48 B over same Runtime direct subtype) but essentially no measured latency over direct subtype. With discriminator last, buffering raw values adds ~0.066 µs and 200 B to A.
4. `OneOfIO._readRemainingObject()` constructs a generic `CreatorState` for one-argument constructors, and `CreatorState` creates an `Object[]`. `StreamingIO.readNode(Double.class)` uses a boxed `readDouble()`; Jackson native is able to bind directly to a primitive double creator. These are **plausible mechanisms**, not proven allocation-site attribution without profiling.
5. Full Creek has a separate ~0.30 µs gap even with zero polymorphic elements; the outer `TestModel` multi-argument creator, list-of-strings, BigDecimal and generic dispatch remain candidates.

### Next tests/optimization scope

- Benchmark a **Runtime-only** single-argument creator specialization, separating `Double` boxing from `CreatorState/Object[]` allocation and validating primitive-null defaults and duplicate creator assignments. Do not add dedicated OneOf type dispatch unless the result demonstrates a useful gain.
- Compare Jackson2 nullable `readDouble()` to primitive `readDoubleValue()` in an equivalent creator benchmark before considering typed runtime creator functions.
- A/B a 0-OneOf Creek model separately to isolate fixed overhead; do not attribute that baseline to UUID/OneOf.
- Rerun locally with at least two forks and full warmups before treating CI microsecond ratios as stable.

## Experiment: one-argument CreatorState without Object[1]

Branch-only experimental implementation: `CreatorInfo.newPojoWithOneArg()` and `CreatorState` storing the one argument directly in the existing field; creators with 0 or 2+ arguments retain the original array representation. Unit tests cover explicit null and missing primitives, duplicate assignment and multi-argument regression.

- Pre-change: [corrected baseline run 39577820001](https://github.com/sjf4j-projects/sjf4j/actions/runs/39577820001)
- Post-change: [run 39578950687](https://github.com/sjf4j-projects/sjf4j/actions/runs/39578950687), [raw report](https://github.com/sjf4j-projects/sjf4j/actions/runs/39578950687/artifacts/12335716888)
- Java CI and CodeQL passed post-change.

### Allocation A/B (B/op)

| Workload | Baseline | One-arg experiment | Change |
| --- | ---: | ---: | ---: |
| PolyTypeA direct | 664 | 640 | -24 |
| PolyTypeA OneOf first | 712 | 688 | -24 |
| PolyTypeA OneOf last | 912 | 888 | -24 |
| PolyTypeB OneOf first | 1008 | 1008 | 0 |
| PolyTypeB direct | 960 | 1016 | +56 |
| List of 8 polymorphic elements | 3480 | 3384 | -96 |
| Full Creek with 8 elements | 6688 | 6648 | -40 |
| Full Creek with 0 elements | 3760 | 3760 | 0 |

For timing, absolute native numbers varied by roughly 40% across shared CI runners, making cross-run speedup ratios unreliable. Within-run relative slowdown for full Creek with 8 elements changed from Runtime 4.028 / Native 2.840 = 1.42x to Runtime 5.060 / Native 3.975 = 1.27x; List 8 changed from 2.168 / 1.714 = 1.26x to 2.799 / 2.484 = 1.13x. These directions are encouraging **but do not establish a stable latency speedup**.

The allocation benefit is limited: `PolyTypeB` (primitive double) did not improve even though `PolyTypeA(UUID)` saved 24 B/op. The remaining bottleneck likely includes boxing and generic creator dispatch. Keep this optimization experimental pending local JMH; do not merge based on shared CI measurements alone.

## Run locally

```bash
./gradlew :sjf4j-testbench:jmhJar
java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*CreekRuntimeDiagnosisBenchmark[.](creek_|list_|subtype_).*' \
  -f 2 -wi 8 -w 500ms -i 10 -r 500ms -t 1 -prof gc \
  -rf json -rff creek-runtime-diagnosis.json
```

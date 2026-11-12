# Mapping Full Benchmark — v1.5.1-SNAPSHOT (2026-11-12)

Historical **suite-3** results from [GitHub Actions run #43163182761](https://github.com/sjf4j-projects/sjf4j/actions/runs/43163182761), at commit [`160bb56ba40344315a7a639b4a606a3012ddf660`](https://github.com/sjf4j-projects/sjf4j/commit/160bb56ba40344315a7a639b4a606a3012ddf660).

The original run produced **70 JMH measurements** across four groups. This snapshot preserves the **68 remaining measurements** after one workload was removed from the source and current benchmark matrix. **It is a filtered historical run, not a new 68-case Full execution.** The unmodified source JMH data (including all original measurements) remains in the workflow artifacts, subject to GitHub retention.

## Environment and reporting

| Field | Value |
| --- | --- |
| Release | `1.5.1-SNAPSHOT` |
| Benchmark suite at run time | 3 |
| Current suite after workload removal | 4 |
| JDK | Temurin 25.0.5 |
| JMH | 1.37 |
| Comparison | MapStruct 1.6.3 / SJF4J CompiledMapper |
| Runner | GitHub-hosted Ubuntu 24.04, shared virtual machine |
| CPU | AMD EPYC 9V74 80-Core Processor; 4 logical CPUs available |
| Affinity | Unrestricted |
| Forks | 3 independent JVMs |
| Warmup / measurement | 5 × 1s / 5 × 1s per fork |
| Threads | 1 |
| Latency statistic | Best 2 Mean (drop slowest of 3 forks independently per exact implementation/case) |
| Allocation statistic | Untrimmed JMH `gc.alloc.rate.norm` (B/op) |
| Baseline status | Exploratory; not controlled hardware |

Latencies are **ns/op**; lower is better. In latency tables, MapStruct / Compiled above 1× indicates SJF4J is faster. Allocation tables report **B/op**, not speedup. The two implementations may discard different forks, and original JMH scoreError does not describe the derived Best 2 Mean. The 1/10/100KB labels represent approximate JSON-equivalent object graph sizes, not JSON parsing or serialization.

## Measurements

#### Order

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Classic Order (Arey-style) | — | 14.40 | 13.69 | 1.05× |
| Extended Order (including products) | 1KB | 76.09 | 71.91 | 1.06× |
| Extended Order (including products) | 10KB | 681.33 | 603.12 | 1.13× |
| Extended Order (including products) | 100KB | 9,273.08 | 7,051.78 | 1.31× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Classic Order (Arey-style) | — | 120 | 120 | 1.00× |
| Extended Order (including products) | 1KB | 408 | 408 | 1.00× |
| Extended Order (including products) | 10KB | 3,576 | 3,576 | 1.00× |
| Extended Order (including products) | 100KB | 35,184 | 35,184 | 1.00× |

#### POJO

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Direct properties → record | — | 2.00 | 1.99 | 1.01× |
| Flat / rename / compute | — | 13.99 | 14.30 | 0.98× |
| Nested single-source → record | — | 3.96 | 3.55 | 1.12× |
| Nested / multi-source / record | — | 4.04 | 4.04 | 1.00× |
| Update / null ignore | — | 1.51 | 1.50 | 1.00× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Direct properties → record | — | 24 | 24 | 1.00× |
| Flat / rename / compute | — | 88 | 88 | 1.00× |
| Nested single-source → record | — | 48 | 48 | 1.00× |
| Nested / multi-source / record | — | 56 | 56 | 1.00× |
| Update / null ignore | — | 0 | 0 | — |

#### Collection

**Latency (ns/op, lower is better).** Ratio = MapStruct / Compiled. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Item → ItemDto | — | 2.53 | 2.52 | 1.00× |
| ArrayList → List<ItemDto> | 1KB | 69.88 | 55.85 | 1.25× |
| ArrayList → List<ItemDto> | 10KB | 592.57 | 345.38 | 1.72× |
| ArrayList → List<ItemDto> | 100KB | 7,121.19 | 4,927.68 | 1.45× |
| LinkedList → List<ItemDto> | 1KB | 74.12 | 81.64 | 0.91× |
| LinkedList → List<ItemDto> | 10KB | 723.11 | 762.59 | 0.95× |
| LinkedList → List<ItemDto> | 100KB | 7,187.78 | 7,718.77 | 0.93× |
| Map<String,Item> → Map<String,ItemDto> | 1KB | 136.06 | 135.88 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 10KB | 1,482.11 | 1,443.90 | 1.03× |
| Map<String,Item> → Map<String,ItemDto> | 100KB | 13,697.11 | 13,900.34 | 0.99× |
| Map with 1 large List | 1KB | 99.25 | 90.31 | 1.10× |
| Map with 1 large List | 10KB | 889.66 | 629.20 | 1.41× |
| Map with 1 large List | 100KB | 8,308.74 | 5,852.63 | 1.42× |
| Map with 8 short Lists | 1KB | 171.04 | 164.76 | 1.04× |
| Map with 8 short Lists | 10KB | 873.39 | 786.86 | 1.11× |
| Map with 8 short Lists | 100KB | 8,517.35 | 7,347.35 | 1.16× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | Compiled | MapStruct / Compiled |
| --- | --- | ---: | ---: | ---: |
| Item → ItemDto | — | 32 | 32 | 1.00× |
| ArrayList → List<ItemDto> | 1KB | 368 | 368 | 1.00× |
| ArrayList → List<ItemDto> | 10KB | 3,536 | 3,536 | 1.00× |
| ArrayList → List<ItemDto> | 100KB | 35,144 | 35,144 | 1.00× |
| LinkedList → List<ItemDto> | 1KB | 368 | 368 | 1.00× |
| LinkedList → List<ItemDto> | 10KB | 3,536 | 3,536 | 1.00× |
| LinkedList → List<ItemDto> | 100KB | 35,144 | 35,144 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 1KB | 792 | 792 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 10KB | 8,088 | 8,088 | 1.00× |
| Map<String,Item> → Map<String,ItemDto> | 100KB | 78,472 | 78,472 | 1.00× |
| Map with 1 large List | 1KB | 552 | 496 | 1.11× |
| Map with 1 large List | 10KB | 3,720 | 3,664 | 1.02× |
| Map with 1 large List | 100KB | 35,328 | 35,272 | 1.00× |
| Map with 8 short Lists | 1KB | 1,136 | 1,136 | 1.00× |
| Map with 8 short Lists | 10KB | 4,280 | 4,280 | 1.00× |
| Map with 8 short Lists | 100KB | 35,888 | 35,888 | 1.00× |

#### JSON

**Latency (ns/op, lower is better).** MapStruct is compared separately with CAST and STRICT. Values use independently selected Best 2 Mean forks; ratios use unrounded scores.

| Case | Size | MapStruct | CAST | STRICT | MapStruct / CAST | MapStruct / STRICT |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Map → POJO | 1KB | 5.83 | 5.84 | 6.07 | 1.00× | 0.96× |
| Map → POJO | 10KB | 5.82 | 5.81 | 6.06 | 1.00× | 0.96× |
| Map → POJO | 100KB | 5.82 | 5.84 | 6.07 | 1.00× | 0.96× |
| JsonObject → POJO | 1KB | 6.91 | 6.91 | 8.95 | 1.00× | 0.77× |
| JsonObject → POJO | 10KB | 6.88 | 6.89 | 8.95 | 1.00× | 0.77× |
| JsonObject → POJO | 100KB | 6.92 | 6.90 | 8.95 | 1.00× | 0.77× |

**Allocation (B/op, lower is better).** Untrimmed JMH `gc.alloc.rate.norm`; ratios in this table compare **B/op**, not time.

| Case | Size | MapStruct | CAST | STRICT | MapStruct / CAST | MapStruct / STRICT |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Map → POJO | 1KB | 24 | 24 | 24 | 1.00× | 1.00× |
| Map → POJO | 10KB | 24 | 24 | 24 | 1.00× | 1.00× |
| Map → POJO | 100KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 1KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 10KB | 24 | 24 | 24 | 1.00× | 1.00× |
| JsonObject → POJO | 100KB | 24 | 24 | 24 | 1.00× | 1.00× |

## Fork stability warnings (>15% spread)

| Group | Case | Size | Implementation | Fork means (ns/op) | Original three-fork JMH mean |
| --- | --- | --- | --- | --- | ---: |
| Collection | Map with 8 short Lists | 1KB | MapStruct | 210.99 / 172.55 / 169.53 | 184.36 |

## Interpretation and reproducibility

- These cases are independent. Isolated Item, List and complete Order measurements are not additive, because JIT inlining and allocation depend on call context.
- Both implementations construct equivalent mapping results in benchmark setup. Update reuses a non-null target and ignores null source properties.
- JSON MapStruct uses explicit Java casts for fixed-key extraction. SJF4J CAST is the similar low-overhead projection path; STRICT performs additional conversion. The JSON input width varies, but each mapping reads only three fixed keys.
- The complete unmodified raw JMH JSON for all four groups, accompanying logs, metadata, runner details and the originally generated report are available via [run #43163182761](https://github.com/sjf4j-projects/sjf4j/actions/runs/43163182761) (artifact name `mapping-full-combined-43163182761-1`; artifacts may expire).
- This historical suite-3 run should not be used as a controlled A/B comparison against future suite-4 runs because the workload matrix has changed.

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

## Workload and measurement contract

This is a **self-contained snapshot of the retained cases**, not a new
benchmark run. The paired MapStruct and SJF4J methods operate on the
**same prebuilt input objects**. Mapper creation, fixture construction,
equivalence assertions and initial null/alias checks occur at JMH
`@Setup(Level.Trial)`, outside timed mapping methods. All displayed
cases return equivalent objects; Update modifies an existing target.

The four independently measured groups contain:

| Group | Measured entries | Workload |
| --- | ---: | --- |
| Order | 8 | Classic fixed-size flattening; Extended Order at 1/10/100KB |
| POJO | 10 | Direct / Flat / Nested Profile / Multi-source / Update |
| Collection | 32 | Item, random/sequential Lists, Map of items, Map of one/eight Lists |
| JSON | 18 | Fixed-key Map / JsonObject projections, MapStruct / CAST / STRICT |
| **Retained total** | **68** | **Originally 70 in suite 3; 2 obsolete entries omitted** |

**Size labels:** 1/10/100KB are approximate *JSON-equivalent sizes of the
source object graphs*. No JSON parsing, generation or size measurement runs
inside the timed methods. The classic Order, POJO methods and single Item
use fixed-size inputs. In sized Collection and Extended Order cases, source
items are generated with `max(1, sizeKb * 1024 / 105)` elements. The
JSON projection source grows by `sizeKb * 1024 / 64` additional,
**unmapped** properties, while each mapping reads exactly three fixed keys.

### Representative mapping declarations

The code below extracts core declarations from
[`MappingFullBenchmark.java`](../../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/mapping/MappingFullBenchmark.java).
It shows comparable API use, not full fixture creation or mapper setup.

```java
// SJF4J
@CompiledMapper
interface Sjf4jMapper {
    @Mapping(target = "surname", source = "last")
    @Mapping(target = "fullName", sources = {"first", "last"},
             compute = "(first, last) -> first + \" \" + last")
    @Mapping(target = "ignored", ignore = true)
    FlatTarget flat(FlatSource source);

    @MappingOptions(nulls = NullValuePolicy.IGNORE)
    void update(UpdateTarget target, UpdateSource source);

    List<ItemDto> list(List<Item> source);
}

// MapStruct
@org.mapstruct.Mapper
interface MapStructMapper {
    @org.mapstruct.Mapping(target = "surname", source = "last")
    @org.mapstruct.Mapping(target = "fullName",
            expression = "java(source.first() + \" \" + source.last())")
    @org.mapstruct.Mapping(target = "ignored", ignore = true)
    FlatTarget flat(FlatSource source);

    @org.mapstruct.BeanMapping(
            nullValuePropertyMappingStrategy =
                    org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
    void update(UpdateSource source, @org.mapstruct.MappingTarget UpdateTarget target);

    List<ItemDto> list(List<Item> source);
}
```

JSON projection is a separate comparison: MapStruct uses explicitly
coded Java casts for three known keys (`sku`, `name`, `count`),
SJF4J **CAST** performs similar raw casts, and SJF4J **STRICT** uses
the OBNT conversion path. All three return the same output for the
preconstructed String/Integer values. This does **not** claim that
MapStruct supports arbitrary JSON object navigation automatically.

## Measurements

### Order

Classic Order independently implements the customer/address/two-product workload from the [Arey Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark); scores are not directly comparable across repositories.

| Case | Sample / Mapping | Size |
| --- | --- | --- |
| Classic | Order → flattened DTO; billing/shipping addresses and two products | Fixed |
| Extended | Order → DTO; nested Bean/`Map`/`JsonObject` fields and product list conversion | 1/10/100KB |


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

### POJO

| Case | Sample / Mapping | Size |
| --- | --- | --- |
| Direct | Two same-name properties → record | Fixed |
| Flat | Rename, ignore and compute properties | Fixed |
| Nested Profile | `Profile` → `ProfileDto` with nested contact conversion | Fixed |
| Multi-source | `Profile` + `Account` → record | Fixed |
| Update | Update an existing object, ignoring null values | Fixed |


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

### Collection

The element mapping is `Item(sku, name, quantity, price)` → `ItemDto`.

| Case | Sample / Mapping | Size |
| --- | --- | --- |
| Item | Single `Item` → `ItemDto` | Fixed |
| ArrayList | `ArrayList<Item>` → `List<ItemDto>` | 1/10/100KB |
| LinkedList | `LinkedList<Item>` → `List<ItemDto>` | 1/10/100KB |
| Map items | `Map<String,Item>` → `Map<String,ItemDto>` | 1/10/100KB |
| Map with 1 List | Map containing one full-size List | 1/10/100KB |
| Map with 8 Lists | Same total elements distributed among eight Lists | 1/10/100KB |

The one-List and eight-List cases separate list conversion from Map traversal and repeated conversion calls. These cases are not additive to Extended Order.


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

### JSON

Only `sku`, `name` and `count` are projected to `JsonTarget`; extra unmapped fields increase input width without changing the mapped output.

| Case | Sample / Mapping | Size |
| --- | --- | --- |
| Map projection | `Map<String,Object>` → `JsonTarget` (MapStruct / CAST / STRICT) | 1/10/100KB |
| JsonObject projection | `JsonObject` → `JsonTarget` (MapStruct / CAST / STRICT) | 1/10/100KB |


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

## Key findings

- **Order:** Classic mapping (customer name, billing/shipping addresses and
  two products) is near parity at 1.05×; the extended order, which also
  maps product Lists, attributes and `JsonObject` metadata, is 1.31×
  at 100KB. The classic fixture is independently modeled on the
  [Arey Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark);
  its values are **not** directly comparable to Arey's published results.
- **POJO:** Direct, flat and in-place null-ignore update are close.
  Single-source nested mapping is 1.12× in this run; nested multi-source
  mapping is approximately equal. Update uses an existing target, including
  null input properties that are deliberately ignored.
- **Collection:** Compiled is ahead on `ArrayList` at 10KB (1.72×) and
  100KB (1.45×), and on a Map containing one large List at 100KB (1.42×).
  `LinkedList` inputs favor MapStruct (0.91–0.95×). `ArrayList`
  and `LinkedList` carry equivalent elements but differ in access pattern.
  These are separate benchmarks; one cannot infer end-to-end Order speed
  by adding isolated Item and Collection timings.
- **JSON:** CAST is near MapStruct parity. STRICT is slower because it
  invokes additional conversion semantics (0.96× for Map; 0.77× for
  `JsonObject`). Equal-looking results across 1/10/100KB are expected:
  the three mapped keys are unchanged as unrelated fields are added.
- **Allocation:** Most corresponding mappings have identical B/op; a Map
  with one List saves 56 B/op in Compiled. Update allocates zero bytes per
  measured operation in both implementations. Allocation is untrimmed and
  must not be interpreted as latency.

## Fork stability warnings (>15% spread)

| Group | Case | Size | Implementation | Fork means (ns/op) | Original three-fork JMH mean |
| --- | --- | --- | --- | --- | ---: |
| Collection | Map with 8 short Lists | 1KB | MapStruct | 210.99 / 172.55 / 169.53 | 184.36 |

## Interpretation and reproducibility

**Original run:** [#43163182761](https://github.com/sjf4j-projects/sjf4j/actions/runs/43163182761),
[commit `160bb56`](https://github.com/sjf4j-projects/sjf4j/commit/160bb56ba40344315a7a639b4a606a3012ddf660),
shared Ubuntu 24.04 runner, AMD EPYC 9V74, Temurin 25.0.5, JMH 1.37.
Artifacts include the complete **unmodified 70-case suite-3 JMH JSON**
(four groups), per-group logs, execution metadata and the original report.
The 68 displayed entries omit the two measurements of the subsequently
removed workload; **the historical run has not been re-executed as suite 4**.

To reproduce a **current** Full run (suite 4), use:

```bash
./scripts/benchmark-mapping.sh full

# Optional controlled Linux CPU model/affinity:
MAPPING_EXPECT_CPU_MODEL='AMD EPYC 9V74 80-Core Processor' \
  ./scripts/benchmark-mapping.sh full
MAPPING_CPU_SET='1-2' ./scripts/benchmark-mapping.sh full collection
```

The script builds the JMH jar, runs Order / POJO / Collection / JSON
sequentially on one runner, and validates benchmark coverage and raw forks.
The `mapping-full-benchmark.yml` manual
[GitHub Actions workflow](https://github.com/sjf4j-projects/sjf4j/actions/workflows/mapping-full-benchmark.yml)
can perform the same current-suite run. This **does not regenerate the
2026-11-12 historical snapshot**, because that snapshot was measured
at the older suite-3 commit. Use the linked original artifacts to verify
its exact historical values.

The benchmark report can be regenerated from a complete raw Full run using:

```bash
python3 scripts/analyze-mapping-benchmark.py \
  build/performance/mapping/{order,pojo,collection,json}/mapping-*.json \
  --output build/performance/mapping/report.md
```

**Interpretation guardrails:**

- Each map operation measures its own mapping call, not source creation,
  serializer execution or a combined pipeline. Mapping a JSON source to
  a few target fields is not comparable with mapping its entire graph.
- The five JMH iterations are averaged **within each fork**, then the
  single slowest fork is dropped **independently for each exact implementation
  and case**. Original three-fork means and B/op remain available in raw JSON;
  `scoreError` does not describe this trimmed statistic.
- A fork spread above **15%** is a stability warning, not regression proof.
  For small `ns/op` cases, JIT/compiler effects and shared virtual-machine
  contention may dominate fine differences. Release-grade A/B comparisons
  require controlled hardware and matching JDK/benchmark profiles.
- Current suite 4 has **68 entries**; this document remains a filtered
  **suite-3 historical record** (68 of 70 measured entries).

For up-to-date policy and commands, see
[Mapping performance baseline](../mapping.md).

# Binding performance baseline

SJF4J's primary Binding workload is **FabienReadBenchmark / FabienWriteBenchmark**.
Benchmark sources are in `sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/`.
The models and data generator are in `sjf4j-testbench/src/main/java`
so regular tests can reuse them.

## Baseline policy

- **JDK 25** is the only primary baseline. Record the exact vendor and patch version.
- Exactly two profiles: **Quick** for diagnostics and **Full** for release baselines.
- **Suite version: 2**. Native and Blackbird were added to the comparison matrix.
  Changes to generator, workload, target semantics, or measurement boundary
  must bump this version. Do not compare incompatible versions directly.
- Compare measurements on the same hardware, OS, JDK build, inputs, and settings.
- Read measures parsing from UTF-8 `byte[]` and construction of a new POJO/Map.
  Write measures serialization into a **new byte[]**; value generation, provider
  selection and correctness checks happen outside the timed path.
- Native bindings use their libraries directly, without calling SJF4J during
  measurement. Jackson 2 and 3 have **native** and **blackbird** variants,
  both configured to preserve the expected date/time semantics. Gson native
  has explicit LocalDate / OffsetDateTime adapters. Gson native performs
  UTF-8 decoding/encoding because its API is character-oriented.
- JSON-P's native result is a JsonValue tree, **not** an equivalent Java POJO/Map
  binder; Simple has no external native library. Neither gets a misleading
  native score in this matrix. SnakeYAML native is only measured for **Map**:
  its native POJO construction lacks equivalent Java-time/UUID behavior.
- YAML and JSON use different textual input: compare within a given backend
  and workload, not as a claim that the formats have equal complexity.
- Incompatible backend/implementation combinations intentionally fail early.
  The runner invokes disjoint valid groups instead of producing fake scores.

## Execute

```bash
./scripts/benchmark-binding.sh quick
./scripts/benchmark-binding.sh full
```

Both benchmark classes have a `main(String[] args)` entry point for IDEA.
Edit their local default JMH arguments to choose a backend, implementation,
model, shape or seed. The default selects Jackson 2 (all four modes). Supplied
command-line arguments override these defaults.

| | Quick | Full |
| --- | --- | --- |
| Backends | Jackson2, Fastjson2, SnakeYAML | All 7 |
| Implementations | Runtime / Compiled; Native for Jackson2/Fastjson2; Blackbird for Jackson2 | Runtime / Compiled everywhere; Native for Jackson2/3, Fastjson2, Gson and SnakeYAML Map; Blackbird for Jackson2/3 |
| Model | Users / Clients | Users / Clients |
| Shape | POJO | POJO / Map |
| Data | 1/10KB, seed 12345 | 1/10/100KB, seeds 12345/324 |
| JMH | 1 fork, 3×0.5s warmup + 3×0.5s measure | 3 forks, 5×0.4s warmup + 5×0.4s measure |

The runner emits **one JMH JSON per valid group** and a companion metadata
Markdown listing all produced files under `build/performance/binding/` (optional
second argument changes that directory). This prevents invalid cross-products,
and retains raw per-fork measurements and `-prof gc` allocation data.
Full is intentionally expensive; execute on an otherwise idle benchmark host.

## Maintaining history in the repository

1. Run **Full** on a clean release commit using the designated JDK 25 host.
   Ensure all expected groups completed and setup verification succeeded.
2. Copy the raw JMH JSON files and the corresponding `.meta.md` to
   `docs/performance/baselines/binding/<release>/` and commit them.
3. Update the release index below with Git SHA, suite version, full JDK
   build, and links to the files or release directory. Do not transcribe
   thousands of scores into Markdown.
4. For a suspected regression, run base and candidate on the same host
   with identical profile and versions (except the intended change).
   Compare matching method + params (including `implementation`),
   independent forks, uncertainty, and allocation differences.
5. Ask AI to read **all** JMH JSON files and the metadata for both commits,
   then summarize speed and memory regressions. No external service is needed.

## Release index

| Release | Commit | JDK | Suite | Results |
| --- | --- | --- | --- | --- |
| _Awaiting first verified Full run_ | — | 25 | 2 | — |

## Interpreting changes

For JMH AverageTime, lower is better:
`deltaPercent = 100 * (candidateScore / baselineScore - 1)`.

- Under 3%: often noise; continue tracking.
- 3–5%: inspect variability.
- 5–10%: repeat and investigate.
- Above 10%: high-priority investigation.

Thresholds are triage rules, **not proof of regression**. Analyze independent
JMH forks, not iterations within a fork as independent observations.
Track `gc.alloc.rate.norm` (B/op) as well as time.

A separate **Validation** suite and baseline will follow after Binding stabilizes.

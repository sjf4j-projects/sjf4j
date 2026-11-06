# Binding performance baseline

SJF4J's primary Binding workload is **FabienReadBenchmark / FabienWriteBenchmark**.
Benchmark sources are in `sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/`.
The fixture models and generator (`Users`, `Clients`, `FabienDataGenerator`)
are in `sjf4j-testbench/src/main/java` for reuse by normal tests.

## Baseline policy

- **JDK 25** is the only primary baseline. Record exact vendor and patch version.
- **Quick** and **Full** are the only profiles; Quick is diagnostic, Full establishes
  a release baseline.
- **Suite version: 1**. If the generator, target object graph, input/output
  semantics, verification rules or measurement boundary changes incompatibly,
  increment this version. Do not compare incompatible suite versions directly.
- Compare only runs on the same hardware/OS, JDK build, inputs and settings;
  report library version changes when testing an upgrade.
- Read measures byte[] input parsing and constructing a fresh POJO or Map.
  Write measures serializing a prebuilt POJO or Map into a **new byte[]**.
  Fixture generation, method selection and correctness verification happen
  outside the measured path. Allocation is recorded via JMH `-prof gc`.
- The SnakeYAML backend measures the JSON-compatible YAML subset, not the
  complete YAML specification. Do not rank JSON-vs-YAML without stating that
  their textual formats differ.
- Runtime and Compiled share the same target semantics. Native-library results
  are excluded from the primary score matrix until equivalent targets, input
  format and settings are verified. The existing CompiledRead/WriteBenchmark
  suite remains available for native/handwritten diagnostics.

## Execute

```bash
./scripts/benchmark-binding.sh quick
./scripts/benchmark-binding.sh full
```

This builds the JMH jar and writes a raw JMH JSON file plus a companion
environment Markdown file under `build/performance/binding/` (override with
the optional second argument). Both require JDK 25.

| | Quick | Full |
| --- | --- | --- |
| Backend | Jackson 2, Fastjson2, SnakeYAML | All 7 |
| Implementation | Runtime / Compiled | Runtime / Compiled |
| Model | Users / Clients | Users / Clients |
| Shape | POJO | POJO / Map |
| Data | 1KB / 10KB, seed 12345 | 1/10/100KB, seeds 12345/324 |
| JMH | 1 fork, 3×0.5s warmup + 3×0.5s measure | 3 forks, 5×0.4s warmup + 5×0.4s measure |

The Full Cartesian product is intentionally expensive; run it on an idle,
dedicated machine when establishing or updating a release baseline.

## Maintaining history in the repository

1. Run **Full** on the exact release commit. Ensure all JMH combinations
   completed successfully and the working tree was clean.
2. Copy the unmodified JSON and matching `.meta.md` from
   `build/performance/binding/` into
   `docs/performance/baselines/binding/<release>/` and commit them.
3. Add one entry in the release index below with the release, commit SHA,
   suite version, exact JDK and relative links. No need to transcribe
   thousands of metrics into Markdown.
4. For a suspected regression, run both the base and candidate commits on
   the same host, preferably interleaved, using the same Full profile.
   Compare corresponding benchmark name + params, inspect fork variability,
   JMH uncertainty and GC allocation changes.
5. Ask AI to read the two JSON files and metadata, and summarize notable
   speed and allocation changes. **No external service is required.**

## Release index

| Release | Commit | JDK | Suite | JMH results | Environment |
| --- | --- | --- | --- | --- | --- |
| _Awaiting the first verified Full run_ | — | 25 | 1 | — | — |

## Interpreting changes

For JMH AverageTime, lower is better:
`deltaPercent = 100 * (candidateScore / baselineScore - 1)`.

- Under 3%: likely measurement noise; monitor.
- 3–5%: check variability.
- 5–10%: repeat and investigate.
- Above 10%: prioritize investigation.

These are screening thresholds, **not proof of regression**. Don't merge
per-iteration measurements from the same JMH fork as independent samples.
Compare independent forks, report uncertainty, and validate regressions with
paired base/head testing. Track `gc.alloc.rate.norm` (B/op) as well as time.

A separate **Validation** suite and baseline can be established later;
do not mix validation measurements into the Binding baseline.

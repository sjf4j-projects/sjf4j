# Binding performance baseline

SJF4J's primary Binding workload is **FabienReadBenchmark / FabienWriteBenchmark**.
Benchmark sources are in `sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/`.
The models and data generator are in `sjf4j-testbench/src/main/java`
so regular tests can reuse them.

## Baseline policy

- **JDK 25** is the only primary baseline. Record the exact vendor and patch version.
- Three execution profiles: **Smoke** verifies correctness at the largest payload
  (100KB, both seeds); **Quick** diagnoses performance; **Full** establishes release
  baselines. Quick and Full execute the **same** case matrix, differing only in
  JMH fork, warmup and measurement parameters. Smoke is never a baseline.
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
  native score in this matrix. SnakeYAML native covers **POJO and Map**: POJO uses a native
  SnakeYAML Constructor/Representer configured for UUID and Java-time fields
  without invoking any SJF4J mapping code in the timed operation.
- YAML and JSON use different textual input: compare within a given backend
  and workload, not as a claim that the formats have equal complexity.
- Incompatible backend/implementation combinations intentionally fail early.
  The runner invokes disjoint valid groups instead of producing fake scores.

## Execute

```bash
./scripts/benchmark-binding.sh             # Print usage (no benchmark run)
./scripts/benchmark-binding.sh smoke       # Fast correctness check at 100KB
./scripts/benchmark-binding.sh quick       # Diagnostic performance
./scripts/benchmark-binding.sh full        # Complete baseline measurement
```

Both benchmark classes have a `main(String[] args)` entry point for IDEA.
Edit their local default JMH arguments to choose a backend, implementation,
model, shape or seed. The default selects Jackson 2 (all four modes). Supplied
command-line arguments override these defaults.

**Quick and Full use identical cases:** all 7 backends; Runtime / Compiled
for every backend; Native for Jackson2, Jackson3, Fastjson2, Gson and SnakeYAML; Blackbird for Jackson2 and Jackson3; Users / Clients; POJO / Map;
1/10/100KB; seeds 12345 and 324.

| JMH setting | Smoke | Quick | Full |
| --- | --- | --- | --- |
| Forks | 0 (in-process) | 1 | 3 |
| Warmup | 0 | 2 × 0.2s | 5 × 1s |
| Measurement | 1 × 0.05s | 2 × 0.2s | 5 × 1s |
| Data size | 100KB | 1/10/100KB | 1/10/100KB |
| Seeds | 12345/324 | 12345/324 | 12345/324 |
| Backend / implementation / model / shape | Same | Same | Same |
| GC profiler | No | Yes | Yes |

Quick and Full emit **one JMH JSON per backend**, not per implementation, and a companion
`.meta.md` file under `build/performance/binding/` (the optional second argument
changes the directory). The `params.implementation` field distinguishes runtime,
compiled, native and (Jackson only) blackbird inside each backend's JSON.
The seven files end in `-jackson2.json`, `-jackson3.json`, `-fastjson2.json`,
`-gson.json`, `-jsonp.json`, `-simple.json`, and `-snake.json`.
This avoids unsupported combinations while retaining raw fork measurements
and `-prof gc` allocation data.
The runner also checks the expected count of benchmark entries in each JSON file,
exiting with an error if JMH reported only a subset of the requested cases.
Smoke is a **correctness check only**: it intentionally omits warmup and forks,
so ignore its recorded throughput and timing scores. Smoke creates **no persistent
JMH JSON or metadata files**: temporary JMH result data used for completeness
checking is deleted after each backend (and cleaned up on failure). Run Smoke first to catch
large-payload failures before investing time in Quick or Full.
A completed Smoke run ends with a per-backend `PASS actual/expected` summary and
`TOTAL: 336/336 PASSED` followed by `ALL PASSED`. A failure instead prints
the failed backend, marks later backends `NOT RUN`, and exits nonzero.

Full is intentionally expensive; execute on an otherwise idle benchmark host.
On macOS the script invokes `caffeinate -i` around each backend to inhibit idle
sleep, but **closing a MacBook lid can still suspend the benchmark**. Keep the
lid open, use AC power, and discard results from a run interrupted by sleep.
After a successful Full run there should be **7 per-backend JSON files plus 1
`.meta.md`**, for 1,008 total JMH benchmark entries (including Read and Write).

## GitHub Actions Full (exploratory)

The manual **Binding Full Benchmark (JDK 25)** workflow reuses this script.
In the repository **Actions** tab, select the workflow, click **Run workflow**
and choose the commit/branch to benchmark.

- The JMH jar is compiled once, then each of the seven backends runs in a
  separate Ubuntu 24.04 job under JDK 25. Each backend job has a 330-minute
  timeout, below GitHub's 6-hour per-job limit.
- Each job uploads a backend-specific artifact with **raw JMH JSON** (including
  independent forks and GC allocation metrics), environment metadata, hardware
  information, and a console log. Representative 100KB Users/POJO scores are
  displayed in the Actions job summary. A final job also creates a **combined
  artifact** containing all backend folders, so the entire Full run can be
  downloaded in one archive.
- **Shared GitHub-hosted runner timings are diagnostic, not release baselines.**
  Hardware and scheduling vary between jobs and runs. The metadata explicitly
  records `Baseline eligible: no`, even though the workload and JMH parameters
  are identical to local Full. Do not compare absolute results across backend
  jobs as if they used the same machine.
- The workflow is **manual-only**; it does not run on pushes or pull requests.

To run a single backend locally on JDK 25, for example:

```bash
BINDING_BACKEND=jackson2 ./scripts/benchmark-binding.sh full
```

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

## Reporting and ranking results

**Full uses 3 independent JMH forks. The default comparison metric is Best 2 Mean,
computed independently for every implementation and every exact benchmark case.**
For AverageTime (`us/op`, lower is better):

1. For a fixed backend, Read/Write, Users/Clients, POJO/Map, size, **seed**, and
   **implementation**, take the arithmetic mean of its 5 measurement iterations
   **within each fork** (`primaryMetric.rawData`: 3 arrays of 5 values).
2. Drop the **single slowest fork** (the **highest** fork mean). Average the
   remaining two fork means: `best2(seed, implementation, case)`.
3. For an overall model/shape/size result, average `best2` for seed `12345` and
   seed `324` with **equal weight**. Retain individual-seed results for drilldown.

**Do not share the excluded fork across implementations or cases.** Native,
Blackbird, Runtime and Compiled each select their own best two forks, as does
each backend, operation, model, shape, payload size and seed. Present the four
implementations **in this fixed order: Native → Blackbird → Runtime → Compiled**
at every reporting level; omit an unsupported implementation rather than
reporting an invented score. Highlight the lowest time in each comparable row.

For metrics where **higher is better** (e.g. throughput), keep the two
**highest** fork means instead. For GC/allocation metrics, report `B/op` with
an explicit aggregation label; if applying Best 2 to that metric, rank the
forks **independently using that metric's own values**, not the time-selected
forks. Preserve all raw forks and the untrimmed JMH `primaryMetric.score`.

**Meaning and caveat:** Best 2 Mean describes the faster, typical compiled
execution state; it is deliberately biased toward lower latency. It is
**not** the expected latency of a random fresh JVM fork, and it is not a JMH
confidence interval. When fork means diverge materially (e.g. the slowest
exceeds the fastest by >15%), show the three fork means and original 3-fork
JMH score alongside Best 2, rather than hiding an unstable JIT outcome.
Do not attach JMH's `scoreError` (computed for all 3 forks) to Best 2.

GitHub Actions' per-backend job summary applies Best 2 to its representative
100KB Users/POJO/seed 12345 cases and separately shows the original JMH mean.
The raw per-backend JSON remains unmodified and is the source for all detailed
reports. Existing pre-change Full artifacts used 5 × 0.4s warmup and measurement;
new Full runs use **5 × 1s** each, about **2.5×** as much timed JMH work.
Do not treat across-duration comparisons as controlled A/B tests without
repeating them using identical JMH settings.

## Interpreting changes

For JMH AverageTime, lower is better:
`deltaPercent = 100 * (candidateBest2 / baselineBest2 - 1)`.
For a controlled comparison, use the same fork-selection rule and JMH timing
on baseline and candidate; inspect the original all-fork data as a stability check.

- Under 3%: often noise; continue tracking.
- 3–5%: inspect variability.
- 5–10%: repeat and investigate.
- Above 10%: high-priority investigation.

Thresholds are triage rules, **not proof of regression**. Analyze independent
JMH forks, not iterations within a fork as independent observations.
Track `gc.alloc.rate.norm` (B/op) as well as time.

A separate **Validation** suite and baseline will follow after Binding stabilizes.

# Binding performance baseline

SJF4J's primary Binding workload is **FabienReadBenchmark / FabienWriteBenchmark**.
Benchmark sources are in `sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/binding/fabien/`.
The models and data generator are in `sjf4j-testbench/src/main/java`
so regular tests can reuse them.

## Results — v1.5.0 (2026-11-09)

**Full JMH on Temurin 25.0.5 · 3 independent forks · Best 2 Mean · two seeds · 1/10/100KB.** All **1,008** cases completed in [GitHub Actions run #42581094035](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035). Results are exploratory because the seven backend jobs used shared runners. All latency comparisons below are **within each backend**; cross-backend absolute comparisons are not controlled.

### 100KB POJO measurements

### Workload models

- **Users:** ordinary JSON scalar values mapped to Java `String`, numeric primitives (`int`/`double`) and `boolean`, plus nested collections/objects. Fields such as `guid` and `registered` remain **strings**, not `UUID` or date/time objects.
- **Clients:** strongly typed values including `UUID`, `LocalDate`, `OffsetDateTime`, `BigDecimal` and enums, alongside primitive values, arrays and nested collections/objects.

**Why Clients POJO Read can differ more:** SJF4J uses `ValueFormat`/`ValueCodec` specialized paths, including fast paths for temporal-value parsing, to avoid part of the generic value-conversion overhead. These paths help explain the larger difference from Jackson Native/Blackbird on **Clients POJO Read** (notably at 100KB); the observed gain also includes other binding-path costs and must not be attributed solely to `ValueFormat`. For **Map Read**, the target is a generic Map rather than the typed `Clients` POJO, so do not infer the same benefit.

**Latency (µs/op, lower is better).** Columns are ordered Native / Blackbird / Runtime / Compiled, followed by `Native / Compiled` (a ratio of unrounded latency measurements; below 1× means Compiled is slower). The tables cover Read and Write for both Users and Clients; the [complete report](binding/fabien-v150-2026-11-09.md) includes all Read/Write, Users/Clients, POJO/Map, and 1/10/100KB combinations.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 182.322 | 158.189 | 177.032 | 148.947 | 1.22× |
| Read / Users / POJO | Jackson3 | 226.058 | 222.663 | 226.480 | 194.044 | 1.16× |
| Read / Users / POJO | Fastjson2 | 63.783 | — | 89.261 | 65.893 | 0.97× |
| Read / Users / POJO | Gson | 324.607 | — | 325.109 | 275.615 | 1.18× |
| Read / Users / POJO | JSON-P | — | — | 356.928 | 322.115 | — |
| Read / Users / POJO | Simple | — | — | 405.426 | 343.667 | — |
| Read / Users / POJO | SnakeYAML | 6,374.043 | — | 4,653.602 | 4,466.424 | 1.43× |
| Write / Users / POJO | Jackson2 | 139.779 | 121.073 | 112.040 | 110.857 | 1.26× |
| Write / Users / POJO | Jackson3 | 184.547 | 198.032 | 177.837 | 168.982 | 1.09× |
| Write / Users / POJO | Fastjson2 | 53.039 | — | 74.299 | 53.399 | 0.99× |
| Write / Users / POJO | Gson | 443.815 | — | 243.785 | 210.322 | 2.11× |
| Write / Users / POJO | JSON-P | — | — | 233.514 | 190.743 | — |
| Write / Users / POJO | Simple | — | — | 501.776 | 478.316 | — |
| Write / Users / POJO | SnakeYAML | 9,208.703 | — | 4,141.737 | 4,108.626 | 2.24× |
| Read / Clients / POJO | Jackson2 | 622.862 | 592.055 | 199.988 | 175.102 | 3.56× |
| Read / Clients / POJO | Jackson3 | 772.862 | 754.982 | 249.817 | 218.543 | 3.54× |
| Read / Clients / POJO | Fastjson2 | 74.604 | — | 103.453 | 74.001 | 1.01× |
| Read / Clients / POJO | Gson | 833.780 | — | 385.232 | 333.997 | 2.50× |
| Read / Clients / POJO | JSON-P | — | — | 491.747 | 445.431 | — |
| Read / Clients / POJO | Simple | — | — | 408.852 | 385.367 | — |
| Read / Clients / POJO | SnakeYAML | 6,704.549 | — | 4,539.373 | 4,380.591 | 1.53× |
| Write / Clients / POJO | Jackson2 | 257.872 | 242.339 | 211.957 | 187.020 | 1.38× |
| Write / Clients / POJO | Jackson3 | 340.871 | 338.389 | 297.200 | 265.962 | 1.28× |
| Write / Clients / POJO | Fastjson2 | 48.670 | — | 79.194 | 49.454 | 0.98× |
| Write / Clients / POJO | Gson | 487.814 | — | 300.414 | 275.900 | 1.77× |
| Write / Clients / POJO | JSON-P | — | — | 300.859 | 263.251 | — |
| Write / Clients / POJO | Simple | — | — | 486.467 | 457.020 | — |
| Write / Clients / POJO | SnakeYAML | 9,186.789 | — | 4,858.748 | 4,713.128 | 1.95× |

**Allocation (B/op, lower is better).** Untrimmed JMH allocation scores, averaged equally across two seeds, not the latency-selected forks. The rightmost `Native / Compiled` is the B/op ratio (below 1× means Compiled allocates more), not a latency speedup.

| Operation / Model / Shape | Backend | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Read / Users / POJO | Jackson2 | 330,793 | 330,057 | 327,809 | 327,816 | 1.01× |
| Read / Users / POJO | Jackson3 | 297,658 | 296,922 | 294,650 | 294,649 | 1.01× |
| Read / Users / POJO | Fastjson2 | 297,328 | — | 297,273 | 297,272 | 1.00× |
| Read / Users / POJO | Gson | 503,938 | — | 501,018 | 501,018 | 1.01× |
| Read / Users / POJO | JSON-P | — | — | 519,956 | 520,399 | — |
| Read / Users / POJO | Simple | — | — | 504,235 | 504,234 | — |
| Read / Users / POJO | SnakeYAML | 9,088,841 | — | 6,810,561 | 6,694,380 | 1.36× |
| Write / Users / POJO | Jackson2 | 199,054 | 198,319 | 112,061 | 112,061 | 1.78× |
| Write / Users / POJO | Jackson3 | 199,069 | 198,334 | 112,085 | 112,085 | 1.78× |
| Write / Users / POJO | Fastjson2 | 105,116 | — | 105,061 | 105,060 | 1.00× |
| Write / Users / POJO | Gson | 522,167 | — | 216,358 | 216,357 | 2.41× |
| Write / Users / POJO | JSON-P | — | — | 317,176 | 317,176 | — |
| Write / Users / POJO | Simple | — | — | 216,168 | 216,167 | — |
| Write / Users / POJO | SnakeYAML | 6,824,854 | — | 5,866,154 | 5,866,153 | 1.16× |
| Read / Clients / POJO | Jackson2 | 1,241,410 | 1,225,244 | 282,793 | 282,937 | 4.39× |
| Read / Clients / POJO | Jackson3 | 1,213,238 | 1,197,067 | 254,572 | 254,754 | 4.76× |
| Read / Clients / POJO | Fastjson2 | 255,213 | — | 256,333 | 254,605 | 1.00× |
| Read / Clients / POJO | Gson | 1,399,430 | — | 486,071 | 486,214 | 2.88× |
| Read / Clients / POJO | JSON-P | — | — | 590,649 | 590,910 | — |
| Read / Clients / POJO | Simple | — | — | 437,975 | 438,119 | — |
| Read / Clients / POJO | SnakeYAML | 9,134,260 | — | 6,143,448 | 6,143,591 | 1.49× |
| Write / Clients / POJO | Jackson2 | 333,146 | 316,993 | 211,794 | 211,793 | 1.57× |
| Write / Clients / POJO | Jackson3 | 333,161 | 317,009 | 211,506 | 211,506 | 1.58× |
| Write / Clients / POJO | Fastjson2 | 110,524 | — | 110,469 | 110,468 | 1.00× |
| Write / Clients / POJO | Gson | 716,743 | — | 352,870 | 352,870 | 2.03× |
| Write / Clients / POJO | JSON-P | — | — | 432,582 | 432,584 | — |
| Write / Clients / POJO | Simple | — | — | 352,679 | 352,679 | — |
| Write / Clients / POJO | SnakeYAML | 6,530,213 | — | 5,719,682 | 5,722,145 | 1.14× |

**[Complete 1/10/100KB results, all implementations, allocation, and fork stability](binding/fabien-v150-2026-11-09.md)** · **[Original JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035/artifacts/13675482490)**

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
| v1.5.0 (GitHub Actions; exploratory) | `c17ac35593e7` | 25.0.5 | 2 | [Full 1/10/100KB report](binding/fabien-v150-2026-11-09.md) · [Actions run](https://github.com/sjf4j-projects/sjf4j/actions/runs/42581094035) |

A controlled, same-hardware release baseline is still pending; this successful Full run used shared GitHub-hosted runners.

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

**Meaning and caveat:** Best 2 Mean describes the faster observed JVM
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

# Jackson3 Compiled Users Write: Tiered JIT Inlining Regression

Date: 2026-11-09 · Investigation: [Draft PR #246](https://github.com/sjf4j-projects/sjf4j/pull/246) · JDK: Temurin 25.0.5, Linux x86_64 · Workload: original Users POJO, 100KB.

## Finding

The original generated Jackson3 Users writer is consistently slower than a second generated writer with **bytecode-identical hot helper methods** under the default tiered HotSpot JIT on tested AMD EPYC hosts. The disparity **disappears under C2-only** and, crucially, **disappears when inlining of the original `_writeStringMember` helper is disabled**. This is a JIT optimization/code-layout interaction, not a PooledByteArrayOutputStream allocation regression.

### Six independently allocated AMD hosts

Full same-commit reproduction: [Actions #42553691265](https://github.com/sjf4j-projects/sjf4j/actions/runs/42553691265). All tests use 3 JMH forks, 5 × 400ms warmup, 5 × 400ms measurements, 1 thread, GC profiler; output bytes verified in trial setup.

| Host | CPU | Default original/sibling, seed 12345 | Default original/sibling, seed 324 | C2-only original/sibling, seed 324 |
| --- | --- | ---: | ---: | ---: |
| 1 | AMD EPYC 9V45 | 1.370× | 1.331× | 1.008× |
| 2 | AMD EPYC 7763 | 1.388× | 1.240× | 1.004× |
| 3 | AMD EPYC 9V45 | 1.382× | 1.205× | 1.004× |
| 4 | AMD EPYC 9V45 | 1.367× | 1.320× | 1.019× |
| 5 | AMD EPYC 7763 | 1.389× | 1.326× | 1.007× |
| 6 | AMD EPYC 9V74 | 1.364× | 1.350× | 0.994× |

A prior experiment on one Intel Xeon 6973P-C host saw no default-tiered regression ([Actions #42551376753](https://github.com/sjf4j-projects/sjf4j/actions/runs/42551376753)); it does **not** establish that Intel cannot reproduce it. Hosted runners differ in hardware and load; compare ratios within one host, not absolute timings across hosts.

### HotSpot LogCompilation

On all six AMD hosts, original and sibling `_writeUser` have identical 517-byte JVM method bodies but different C2 decisions:

| Observed C2 `_writeUser` | Original generated class | Sibling generated class |
| --- | ---: | ---: |
| `_writeStringMember` calls inlined | **5 / 15** | **0 / 15** |
| C2 nmethod total size, EPYC 9V45 | ≈35,880 bytes | ≈23,280–23,344 bytes |

The original writer inlines the first five occurrences of `_writeStringMember`, then runs into `size > DesiredMethodLimit`; the sibling does not inline these calls. On the observed Intel host, both had 0/15 string-helper calls inlined. These records show code bloat, but the decisive causal evidence is below.

### Controlled intervention: forbid only original helper inlining

Causal A/B: [Actions #42554520941](https://github.com/sjf4j-projects/sjf4j/actions/runs/42554520941). The only JVM difference is:

```text
-XX:CompileCommand=dontinline,org.sjf4j.testbench.binding.fabien.FabienBindings$Jackson3_Impl::_writeStringMember
```

No generated or production Java code was changed. Each host ran original and sibling writers with the same data, input seeds, JMH configuration, and allocation profiler.

| CPU | Seed | Default original/sibling | With dontinline original/sibling | Original default → dontinline (µs/op) |
| --- | --- | ---: | ---: | ---: |
| EPYC 9V74 | 12345 | 1.392× | **1.027×** | 233.62 → 171.65 |
| EPYC 9V74 | 324 | 1.331× | **0.993×** | 231.93 → 171.09 |
| EPYC 9V45 | 12345 | 1.371× | **1.009×** | 145.73 → 106.80 |
| EPYC 9V45 | 324 | 1.293× | **1.004×** | 142.74 → 108.90 |
| EPYC 7763 | 12345 | 1.387× | **1.033×** | 301.90 → 224.98 |
| EPYC 7763 | 324 | 1.328× | **1.001×** | 299.73 → 226.53 |

Separate C2 diagnostic forks independently verify that the original `_writeUser` inline count changes from **5/15 → 0/15**, and nmethod size from **≈35.9 KB → 20.4 KB**. Allocation remains essentially the same (≈111.8 KB/op for seed 12345, ≈112.4 KB/op for seed 324).

**Interpretation:** preventing these C2 helper inlines is sufficient to eliminate the tested AMD-tiered-JIT regression. It does not imply that disabling JIT inlining globally or imposing VM flags on library users is an acceptable fix.

## Next engineering experiment

Find a **portable source/code-generation structure** that gives the same desirable JIT shape without VM flags. First experiment only on the Jackson3 generated string-member helper and keep output byte-identical; run the original/sibling JMH on multiple independent hosts with compilation-XML inspection. Verify no regression on Jackson2, Clients, small payloads, and read/mapping paths before proposing production changes. Do not artificially pad bytecode merely to manipulate undocumented HotSpot inline thresholds.

**Scope:** all changes and artifacts remain in this experimental PR. No production code or `main` merge is included in these investigations.

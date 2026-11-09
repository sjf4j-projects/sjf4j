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

## Codegen experiment #12 — distinguish code shape from class identity

[Actions #42556638600](https://github.com/sjf4j-projects/sjf4j/actions/runs/42556638600); 3 independently assigned AMD hosts, same 100KB Users model and byte output, 3 forks.

| Generated class | EPYC 7763 #1 µs/op | EPYC 7763 #2 µs/op | EPYC 9V74 µs/op | C2 helper inlining |
| --- | ---: | ---: | ---: | --- |
| Original FabienBindings.Jackson3 | 302.55 | 301.64 | 231.54 | 5 / 15 |
| Identical API/implementation, new class identity | 217.63 | 218.01 | 165.64 | 0 / 15 |
| Compact branch structure, new class identity | 241.05 | 219.70 | 168.94 | 0 / 15 |
| Static helper with includeNulls argument, new class identity | 219.70 | 222.06 | 168.31 | 0 / 15 |
| Directly expanded String property writes | 316.56 | 316.76 | 236.33 | no helper; ~36.5KB C2 |

Measurements above use seed 12345 (the more repeatable of two seeds). The identical class *clone* also becomes fast: merely observing the other new classes' speed is **not** proof of a codegen fix. Directly expanding the logic worsens C2 machine-code bloat.

## Codegen experiment #13 — SAME original class identity

[Actions #42557554888](https://github.com/sjf4j-projects/sjf4j/actions/runs/42557554888); each host compiles `FabienBindings$Jackson3_Impl` independently three times from the same source, with the original **binary class name, method layout and unchanged workload**. Environment `SJF4J_EXPERIMENT_STRING_MEMBER=baseline|compact|staticFlag` controls only the experimental processor branch; JVM has **no JIT flags**. `javap` differences and output SHA-256 across JMH Jars are verified.

100KB original Users POJO, seed 12345, 3 forks:

| CPU | Baseline original/sibling | Compact original/sibling | StaticFlag original/sibling |
| --- | ---: | ---: | ---: |
| AMD EPYC 7763 | **1.373×** | 1.390× | **1.043×** |
| AMD EPYC 9V74 | **1.393×** | 1.397× | **1.042×** |
| Intel Xeon Platinum 8573C | 1.002× | 1.021× | 1.041× |

On both AMD CPUs, the original helper C2 inline count changes **5/15 → 0/15**, C2 `_writeUser` code size **~36KB → ~22.1KB**, and write latency drops ~25%; the unchanged sibling Writer acts as the same-host reference. On Intel, the baseline has 0/15 inlines already and no significant performance gap, so the 4% difference needs regression checks. Normalized allocation is unchanged. Seed 324 results are substantially noisier; see the raw JMH JSON.

**Candidate generated shape** (no public API and no JVM flags):

```java
private static void _writeStringMember(JsonGenerator g, SerializedString name,
                                      String value, boolean includeNulls) {
    if (value != null || includeNulls) {
        g.writeName(name);
        if (value == null) g.writeNull();
        else g.writeString(value);
    }
}
// Call site: _writeStringMember(g, PREPARED_NAME, value, this.context.includeNulls);
```

The compact instance helper alone **does not** fix the original class; the static helper and explicit includeNulls parameter jointly alter the C2 outcome. Distinguishing the effect of `static` from the extra parameter requires further factorial experimentation if necessary.

## Codegen experiment #14 — broad workload and null-semantics gate

[Successful Actions #42558815983](https://github.com/sjf4j-projects/sjf4j/actions/runs/42558815983), commit `c848709f`. Three runners: two AMD EPYC 7763 and one AMD EPYC 9V74, Temurin JDK25.0.5. Same original generated class identity for Baseline/StaticFlag; 2 JMH forks, four 300ms warmups and five 300ms measurements per case; same-host Runtime controls and normalized GC allocation.

Three-host **geometric mean StaticFlag/Baseline ratio** (lower is better):

| Model | Seed | 1KB | 10KB | 100KB |
| --- | --- | ---: | ---: | ---: |
| Users POJO | 12345 | 0.985× | 1.003× | **0.748×** |
| Users POJO | 324 | 0.984× | **0.750×** | **0.756×** |
| Clients POJO | 12345 | 1.015× | 0.946× | 0.907× |
| Clients POJO | 324 | **1.065×** | 0.916× | 1.020× |

Users 100KB is consistently ≈25% faster on all three AMD hosts. Clients 10KB improves on all hosts; Clients 100KB is mixed but errors are relatively large. **Potential regression:** Clients 1KB seed324 is 4–10% slower on all three hosts (geometric mean ≈6.5%). At this scale JVM state/scheduler noise can matter, so confirm with higher-repetition JMH before accepting any small-payload regression.

**Output correctness:** four `NULL_PROBE` digest lines were byte-for-byte identical between versions on all three hosts. They cover `Users.sample()`/`Clients.sample()`, `includeNulls=true/false`, omission of null object properties, and preservation of null array elements. No meaningful additional GC allocation detected.

The initial run [#42558598460](https://github.com/sjf4j-projects/sjf4j/actions/runs/42558598460) failed at JMH compilation because the test probe used deprecated Jackson2 `JsonNode.fields()` under `-Werror`; fixed to `fieldNames()` before successful run. No performance data from the failed run.

## Codegen experiment #15 — factorial isolation of helper features

[Actions #42559900890](https://github.com/sjf4j-projects/sjf4j/actions/runs/42559900890), same original generated class name, four JMH Jar variants:

| Variant | Static helper? | Explicit boolean `includeNulls` argument? | Helper branch |
| --- | --- | --- | --- |
| baseline | No | No | Original |
| staticFlag | Yes | Yes | Compact |
| instanceFlag | No | Yes | Compact |
| staticLegacy | Yes | Yes | Original |

This distinguishes staticness from explicitly passing null policy and from branch reordering. Focuses on Users 100KB and Clients 1KB/10KB/100KB, includes Runtime controls, byte-identical null-policy checks and C2 diagnostic logs. **Pending: do not choose a production fix until results are verified.**

**Scope:** all experiments remain in Draft PR #246. The diagnostic environment switch and benchmark-only source are not proposed as public APIs or for merge; no production change or `main` merge has been performed.

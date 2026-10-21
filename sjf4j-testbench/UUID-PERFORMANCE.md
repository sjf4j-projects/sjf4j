# UUID streaming performance — first measurement

- Commit: `0a4f6a65e5f2a165f4e6d12524fba2186b65a1b3`
- CI: [UUID Streaming JMH, run 39453543577](https://github.com/sjf4j-projects/sjf4j/actions/runs/39453543577)
- [Raw JMH JSON and stdout artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/39453543577/artifacts/12278394744)
- Environment: GitHub Actions `ubuntu-latest`, Temurin JDK 17, shared runner; **indicative, not a stable hardware benchmark**.
- JMH: average time, 1 fork, 3 warmups × 200 ms, 4 measurements × 200 ms, 1 thread, `-prof gc`. Setup validates the same values and outputs before measuring.
- Scores below use `µs/op` unless otherwise noted. **All comparisons are from the same CI workflow, not historical numbers.**

## End-to-end Fastjson2: compiled vs runtime

| Payload / direction / encoding | Compiled | Runtime | Runtime / Compiled |
| --- | ---: | ---: | ---: |
| Root read, String | 0.046 | 0.102 | 2.22× |
| Root read, UTF-8 | 0.046 | 0.106 | 2.30× |
| Root write, String | 0.042 | 0.099 | 2.36× |
| Root write, UTF-8 | 0.040 | 0.095 | 2.38× |
| POJO read, String | 0.089 | 0.181 | 2.03× |
| POJO read, UTF-8 | 0.093 | 0.182 | 1.96× |
| POJO write, String | 0.081 | 0.199 | 2.46× |
| POJO write, UTF-8 | 0.065 | 0.195 | 3.00× |

In Fastjson2, compiled and runtime **allocate effectively the same amount per operation** for each equivalent shape (e.g., root read String: both ~192 B/op; POJO read String: both ~264 B/op). The compiled/runtime difference measures binder dispatch, not an additional eliminated UUID String allocation.

## Cross-backend POJO UTF-8 comparison

| Backend | Compiled read | Runtime read | Compiled write | Runtime write |
| --- | ---: | ---: | ---: | ---: |
| Fastjson2 | 0.093 | 0.182 | 0.065 | 0.195 |
| Jackson2 | 0.287 | 0.358 | 0.228 | 0.358 |
| Jackson3 | 0.326 | 0.419 | 0.290 | 0.395 |
| Gson | 0.934 | 1.112 | 0.309 | 0.439 |
| JSON-P | 0.884 | 1.021 | 0.297 | 0.792 |
| Simple | 1.807 | 2.263 | 0.254 | 0.358 |

The [raw JMH report](https://github.com/sjf4j-projects/sjf4j/actions/runs/39453543577/artifacts/12278394744) includes all **96 compiled/runtime mode combinations** (6 backends × 16 operations), including String and UTF-8 root and POJO read/write, and their GC allocation results.

Gson, JSON-P and Simple have different UTF-8 reader adapters and allocations; do not compare UTF-8 vs String scores as if their input paths were equivalent.

## Fastjson2 UUID primitive path: native vs original String conversion

Measured separately in the same JMH workflow, using a direct Fastjson2 `JSONReader`/`JSONWriter` both times.

| Operation | Native UUID (ns/op) | Original String conversion (ns/op) | Native B/op | Original B/op |
| --- | ---: | ---: | ---: | ---: |
| Read String | 46.074 | 61.120 | 248 | 328 |
| Read UTF-8 | 42.243 | 59.547 | 248 | 328 |
| Write String | 38.262 | 77.979 | 248 | 328 |
| Write UTF-8 | 40.816 | 76.696 | 224 | 304 |

The native UUID parser/writer eliminates approximately **80 B/op** compared with the original `readString() + FastUuid.parseUUID()` or `FastUuid.toString() + writeString()` in this standalone setup.

**Caution:** 4 short measurement iterations on a shared runner produced wide confidence intervals (e.g. Fastjson2 native read String: 46.074 ± 60.308 ns/op). Direction and allocation are promising, but rerun with more stable hardware, at least 2 forks, longer warmups and measurements before treating ratios as final.

The above does **not** directly compare pre-change SJF4J CompiledBinder against post-change CompiledBinder; original-vs-native measures only the backend-level UUID operation.

## Reproduce

```bash
./gradlew :sjf4j-testbench:jmhJar

java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*CompiledUuidBenchmark[.].*(Compiled|Runtime)$' \
  -p backend=fastjson2,jackson2,jackson3,gson,jsonp,simple \
  -f 2 -wi 7 -w 500ms -i 10 -r 500ms -t 1 -prof gc \
  -rf json -rff uuid-compiled.json

java -jar sjf4j-testbench/build/libs/*-jmh.jar \
  '.*StreamingUuidBenchmark[.].*' \
  -f 2 -wi 7 -w 500ms -i 10 -r 500ms -t 1 -prof gc \
  -rf json -rff uuid-native.json
```

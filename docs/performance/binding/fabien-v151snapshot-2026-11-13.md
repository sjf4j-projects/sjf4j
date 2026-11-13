# Fabien Binding Full Benchmark — v1.5.1-SNAPSHOT — 2026-11-13

> Current JVM performance snapshot. All values below come from the completed [GitHub Actions Binding Full run #43359529434](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434). Shared runners are exploratory, not a hardware-independent baseline.

**1,008 JMH cases · 7 backends · 1/10/100KB · Read/Write · Users/Clients · POJO/Map · 2 seeds.** Latency uses per-case **Best 2 Mean** and equal-weighted seeds; B/op uses untrimmed JMH allocation scores. All comparable tables retain **Native / Blackbird / Runtime / Compiled** in that order; — means no equivalent implementation.

## Results

### Workload models

- **Users:** ordinary JSON scalar values represented as Java `String`, numeric primitives (`int`/`double`) and `boolean`, with nested arrays/objects. `guid` and `registered` are **strings**, even if their contents resemble UUIDs or timestamps.
- **Clients:** stronger Java value typing: `UUID`, `LocalDate`, `OffsetDateTime`, `BigDecimal`, enums, numeric/boolean primitives, arrays and nested objects.

**Interpretation of Clients POJO Read:** SJF4J supports specialized `ValueFormat`/`ValueCodec` paths, including temporal parsing fast paths. These can reduce value-conversion overhead compared with generic native binding, contributing to the pronounced Clients POJO Read differences for Jackson2/Jackson3. Other differences in object binding and parsing also contribute; no isolated benchmark here attributes the full speedup to this one mechanism. Map Read uses generic Map targets and should be interpreted separately.

The following results cover every supported 1/10/100KB × Users/Clients × POJO/Map × Read/Write combination, with all implementations reported in the fixed order Native, Blackbird, Runtime, Compiled. The rightmost `Native / Compiled` column is an independently calculated ratio; results below 1× indicate a regression. `—` means no equivalent Native or compiled comparison. No backend is excluded from the tables.

## Provenance and metric definitions

- [GitHub Actions Binding Full run #43359529434](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434), workflow_dispatch on `main`, source commit `13e34997b895880abb37cbe031e8fa6284d2cbb8`. Seven backend jobs and the bundling job succeeded.
- [Original JMH JSON, per-fork iterations, allocation and runner metadata](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017). GitHub artifacts may expire.
- **GitHub-hosted runner CPUs (per backend):** Jackson2/Jackson3/Fastjson2/JSON-P — AMD EPYC 9V45; Gson/Simple — AMD EPYC 9V74; SnakeYAML — AMD EPYC 7763. Cross-backend absolute latency is not controlled.
- **1,008 exact JMH cases:** 7 backends, Read/Write, Users/Clients, POJO/Map, sizes 1/10/100KB, seeds 12345 and 324, applicable implementations Native / Blackbird / Runtime / Compiled.
- **Temurin JDK 25, JMH AverageTime**, one thread, 3 independent forks, 5 × 1s warmup and 5 × 1s measurement; GC allocation profiler. Figures are **µs/op**, lower is better.
- **Comparison dependency versions (at the source commit):** Jackson2 Databind and Blackbird `2.22.3`; Jackson3 Databind and Blackbird `3.2.3`; Fastjson2 `2.0.66`; Gson `2.13.1`; Jakarta JSON-P API `2.1.3` with Eclipse Parsson `1.1.7`; SnakeYAML `2.5`. Simple is an SJF4J built-in backend, not a separate library.
- **Latency Best 2 Mean:** average 5 measurement iterations within each fork; select the 2 smallest fork means **for each exact case (including implementation and seed)**. Then average seed 12345 and seed 324 equally, without pooling forks across implementations.
- **Memory B/op:** arithmetic mean of the **untrimmed** `gc.alloc.rate.norm.score` for the two seeds; do not assign the latency Best 2 selection or JMH score error to allocations.
- A ratio such as **Native / Compiled = 3.56×** measures speedup, not a separate observation. Values in the tables are rounded; ratios use unrounded data. **—** means a comparison is not available or equivalent.
- **JSON-P** has no directly equivalent native Java POJO/Map binder in this suite; **Simple** has no external native implementation. Blackbird is only present for Jackson2/3. These omissions are intentional, not failures.
- **Read** converts a UTF-8 byte[] to a new POJO/Map. **Write** serializes a POJO/Map to a newly produced byte[]. Fixture generation, backend selection and semantic verification are outside timed operations. Comparisons are meaningful **within a backend**; backend jobs use different GitHub runner CPUs. YAML and JSON are separate formats.

## Latency details — Best 2 Mean, µs/op

### Read

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 2.253 | 2.014 | 2.147 | 1.953 | 1.15× |
| jackson2 | 10KB | 18.289 | 16.080 | 18.069 | 16.182 | 1.13× |
| jackson2 | 100KB | 177.595 | 158.102 | 174.718 | 153.447 | 1.16× |
| jackson3 | 1KB | 1.832 | 1.750 | 1.869 | 1.668 | 1.10× |
| jackson3 | 10KB | 15.652 | 15.108 | 16.321 | 14.592 | 1.07× |
| jackson3 | 100KB | 153.019 | 153.267 | 159.093 | 139.807 | 1.09× |
| fastjson2 | 1KB | 0.668 | — | 0.892 | 0.655 | 1.02× |
| fastjson2 | 10KB | 6.372 | — | 8.288 | 6.014 | 1.06× |
| fastjson2 | 100KB | 64.482 | — | 86.065 | 60.999 | 1.06× |
| gson | 1KB | 4.825 | — | 4.640 | 3.813 | 1.27× |
| gson | 10KB | 33.858 | — | 34.890 | 29.166 | 1.16× |
| gson | 100KB | 330.590 | — | 328.403 | 271.620 | 1.22× |
| jsonp | 1KB | — | — | 2.931 | 2.837 | — |
| jsonp | 10KB | — | — | 25.287 | 23.149 | — |
| jsonp | 100KB | — | — | 244.108 | 219.645 | — |
| simple | 1KB | — | — | 5.756 | 5.049 | — |
| simple | 10KB | — | — | 41.961 | 35.173 | — |
| simple | 100KB | — | — | 406.837 | 346.887 | — |
| snake | 1KB | 64.141 | — | 49.016 | 48.282 | 1.33× |
| snake | 10KB | 619.189 | — | 449.732 | 440.927 | 1.40× |
| snake | 100KB | 6,374.833 | — | 4,697.523 | 4,490.516 | 1.42× |

#### Users / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 2.231 | 2.238 | 2.249 | 2.252 | 0.99× |
| jackson2 | 10KB | 18.585 | 19.246 | 19.281 | 19.672 | 0.94× |
| jackson2 | 100KB | 179.721 | 178.526 | 180.376 | 184.344 | 0.97× |
| jackson3 | 1KB | 1.959 | 1.945 | 1.873 | 1.890 | 1.04× |
| jackson3 | 10KB | 17.597 | 17.491 | 17.447 | 17.664 | 1.00× |
| jackson3 | 100KB | 164.460 | 166.299 | 162.066 | 165.795 | 0.99× |
| fastjson2 | 1KB | 1.042 | — | 1.248 | 1.278 | 0.82× |
| fastjson2 | 10KB | 9.991 | — | 11.459 | 11.547 | 0.87× |
| fastjson2 | 100KB | 96.670 | — | 102.876 | 104.986 | 0.92× |
| gson | 1KB | 4.459 | — | 3.897 | 3.763 | 1.19× |
| gson | 10KB | 33.713 | — | 30.229 | 30.275 | 1.11× |
| gson | 100KB | 319.869 | — | 302.408 | 300.575 | 1.06× |
| jsonp | 1KB | — | — | 3.047 | 2.851 | — |
| jsonp | 10KB | — | — | 25.213 | 24.136 | — |
| jsonp | 100KB | — | — | 252.982 | 239.253 | — |
| simple | 1KB | — | — | 5.499 | 5.356 | — |
| simple | 10KB | — | — | 39.242 | 39.064 | — |
| simple | 100KB | — | — | 380.590 | 406.804 | — |
| snake | 1KB | 61.415 | — | 48.779 | 48.509 | 1.27× |
| snake | 10KB | 600.461 | — | 454.830 | 450.682 | 1.33× |
| snake | 100KB | 6,331.616 | — | 4,604.388 | 4,624.505 | 1.37× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 3.094 | 2.796 | 2.547 | 2.192 | 1.41× |
| jackson2 | 10KB | 56.944 | 51.785 | 21.539 | 18.796 | 3.03× |
| jackson2 | 100KB | 626.388 | 591.842 | 206.844 | 175.376 | 3.57× |
| jackson3 | 1KB | 2.549 | 2.490 | 2.126 | 1.841 | 1.38× |
| jackson3 | 10KB | 54.821 | 53.288 | 19.218 | 16.699 | 3.28× |
| jackson3 | 100KB | 614.777 | 605.975 | 185.174 | 162.979 | 3.77× |
| fastjson2 | 1KB | 0.823 | — | 1.070 | 0.725 | 1.14× |
| fastjson2 | 10KB | 7.454 | — | 10.257 | 7.125 | 1.05× |
| fastjson2 | 100KB | 75.477 | — | 102.041 | 72.469 | 1.04× |
| gson | 1KB | 5.358 | — | 4.627 | 3.888 | 1.38× |
| gson | 10KB | 77.565 | — | 40.038 | 33.610 | 2.31× |
| gson | 100KB | 815.650 | — | 389.212 | 338.689 | 2.41× |
| jsonp | 1KB | — | — | 3.398 | 2.911 | — |
| jsonp | 10KB | — | — | 33.643 | 28.800 | — |
| jsonp | 100KB | — | — | 343.154 | 303.289 | — |
| simple | 1KB | — | — | 5.840 | 5.233 | — |
| simple | 10KB | — | — | 39.690 | 36.170 | — |
| simple | 100KB | — | — | 409.944 | 384.785 | — |
| snake | 1KB | 64.797 | — | 50.267 | 48.968 | 1.32× |
| snake | 10KB | 677.451 | — | 461.460 | 450.664 | 1.50× |
| snake | 100KB | 6,757.706 | — | 4,512.503 | 4,419.195 | 1.53× |

#### Clients / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 2.488 | 2.424 | 2.443 | 2.359 | 1.05× |
| jackson2 | 10KB | 21.416 | 20.579 | 20.185 | 20.084 | 1.07× |
| jackson2 | 100KB | 194.525 | 186.087 | 182.832 | 180.787 | 1.08× |
| jackson3 | 1KB | 1.958 | 1.889 | 1.860 | 1.787 | 1.10× |
| jackson3 | 10KB | 18.035 | 17.873 | 17.125 | 17.280 | 1.04× |
| jackson3 | 100KB | 165.471 | 159.977 | 160.842 | 162.598 | 1.02× |
| fastjson2 | 1KB | 1.141 | — | 1.155 | 1.283 | 0.89× |
| fastjson2 | 10KB | 11.082 | — | 12.577 | 12.671 | 0.87× |
| fastjson2 | 100KB | 113.499 | — | 120.311 | 121.456 | 0.93× |
| gson | 1KB | 4.640 | — | 4.169 | 4.071 | 1.14× |
| gson | 10KB | 39.154 | — | 35.802 | 35.767 | 1.09× |
| gson | 100KB | 375.787 | — | 353.560 | 361.441 | 1.04× |
| jsonp | 1KB | — | — | 3.207 | 3.175 | — |
| jsonp | 10KB | — | — | 26.394 | 26.053 | — |
| jsonp | 100KB | — | — | 257.400 | 254.190 | — |
| simple | 1KB | — | — | 5.867 | 5.903 | — |
| simple | 10KB | — | — | 40.788 | 40.618 | — |
| simple | 100KB | — | — | 447.425 | 469.770 | — |
| snake | 1KB | 60.701 | — | 50.057 | 50.131 | 1.21× |
| snake | 10KB | 577.400 | — | 450.325 | 450.250 | 1.28× |
| snake | 100KB | 5,755.198 | — | 4,445.967 | 4,449.793 | 1.29× |

### Write

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1.592 | 1.378 | 1.483 | 1.279 | 1.24× |
| jackson2 | 10KB | 14.113 | 12.285 | 15.428 | 12.355 | 1.14× |
| jackson2 | 100KB | 140.128 | 120.689 | 113.374 | 109.639 | 1.28× |
| jackson3 | 1KB | 1.488 | 1.393 | 1.425 | 1.705 | 0.87× |
| jackson3 | 10KB | 12.190 | 12.763 | 14.558 | 12.661 | 0.96× |
| jackson3 | 100KB | 116.918 | 120.276 | 105.951 | 109.093 | 1.07× |
| fastjson2 | 1KB | 0.583 | — | 0.785 | 0.554 | 1.05× |
| fastjson2 | 10KB | 5.168 | — | 7.285 | 5.050 | 1.02× |
| fastjson2 | 100KB | 52.650 | — | 74.661 | 53.379 | 0.99× |
| gson | 1KB | 4.886 | — | 2.529 | 2.164 | 2.26× |
| gson | 10KB | 47.328 | — | 24.733 | 21.649 | 2.19× |
| gson | 100KB | 445.068 | — | 242.124 | 210.474 | 2.11× |
| jsonp | 1KB | — | — | 1.691 | 1.593 | — |
| jsonp | 10KB | — | — | 14.498 | 12.686 | — |
| jsonp | 100KB | — | — | 143.160 | 124.976 | — |
| simple | 1KB | — | — | 2.928 | 2.609 | — |
| simple | 10KB | — | — | 27.446 | 24.767 | — |
| simple | 100KB | — | — | 501.223 | 477.171 | — |
| snake | 1KB | 81.618 | — | 43.370 | 43.001 | 1.90× |
| snake | 10KB | 937.942 | — | 418.987 | 413.437 | 2.27× |
| snake | 100KB | 9,178.240 | — | 4,192.272 | 4,169.617 | 2.20× |

#### Users / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1.731 | 1.721 | 1.489 | 1.484 | 1.17× |
| jackson2 | 10KB | 17.664 | 18.455 | 13.200 | 13.104 | 1.35× |
| jackson2 | 100KB | 147.206 | 149.652 | 125.484 | 123.603 | 1.19× |
| jackson3 | 1KB | 1.686 | 1.670 | 1.487 | 1.523 | 1.11× |
| jackson3 | 10KB | 16.808 | 17.083 | 12.627 | 12.800 | 1.31× |
| jackson3 | 100KB | 142.054 | 142.504 | 121.791 | 118.696 | 1.20× |
| fastjson2 | 1KB | 0.894 | — | 0.892 | 0.900 | 0.99× |
| fastjson2 | 10KB | 9.077 | — | 9.013 | 8.974 | 1.01× |
| fastjson2 | 100KB | 87.403 | — | 90.117 | 90.733 | 0.96× |
| gson | 1KB | 4.563 | — | 2.380 | 2.402 | 1.90× |
| gson | 10KB | 45.758 | — | 22.322 | 22.195 | 2.06× |
| gson | 100KB | 436.202 | — | 217.690 | 219.140 | 1.99× |
| jsonp | 1KB | — | — | 1.614 | 1.622 | — |
| jsonp | 10KB | — | — | 14.030 | 14.284 | — |
| jsonp | 100KB | — | — | 140.256 | 139.939 | — |
| simple | 1KB | — | — | 2.787 | 2.768 | — |
| simple | 10KB | — | — | 26.752 | 26.691 | — |
| simple | 100KB | — | — | 494.756 | 493.006 | — |
| snake | 1KB | 76.463 | — | 43.036 | 43.065 | 1.78× |
| snake | 10KB | 887.713 | — | 416.134 | 416.044 | 2.13× |
| snake | 100KB | 8,970.878 | — | 4,093.452 | 4,188.591 | 2.14× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1.769 | 1.710 | 1.971 | 1.615 | 1.10× |
| jackson2 | 10KB | 23.870 | 23.124 | 20.258 | 18.000 | 1.33× |
| jackson2 | 100KB | 259.067 | 241.285 | 208.808 | 184.108 | 1.41× |
| jackson3 | 1KB | 1.719 | 1.660 | 2.037 | 1.646 | 1.04× |
| jackson3 | 10KB | 22.739 | 22.198 | 19.497 | 17.344 | 1.31× |
| jackson3 | 100KB | 233.267 | 233.214 | 207.963 | 187.128 | 1.25× |
| fastjson2 | 1KB | 0.553 | — | 0.782 | 0.577 | 0.96× |
| fastjson2 | 10KB | 5.257 | — | 7.908 | 5.277 | 1.00× |
| fastjson2 | 100KB | 50.052 | — | 77.606 | 49.877 | 1.00× |
| gson | 1KB | 4.947 | — | 2.549 | 2.280 | 2.17× |
| gson | 10KB | 50.097 | — | 28.943 | 25.911 | 1.93× |
| gson | 100KB | 488.746 | — | 300.794 | 274.675 | 1.78× |
| jsonp | 1KB | — | — | 1.881 | 1.631 | — |
| jsonp | 10KB | — | — | 20.638 | 17.932 | — |
| jsonp | 100KB | — | — | 217.612 | 189.555 | — |
| simple | 1KB | — | — | 2.852 | 2.486 | — |
| simple | 10KB | — | — | 32.463 | 29.718 | — |
| simple | 100KB | — | — | 488.860 | 459.223 | — |
| snake | 1KB | 80.273 | — | 41.881 | 40.646 | 1.97× |
| snake | 10KB | 867.955 | — | 472.941 | 467.978 | 1.85× |
| snake | 100KB | 9,107.741 | — | 4,820.106 | 4,743.159 | 1.92× |

#### Clients / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1.532 | 1.501 | 1.364 | 1.385 | 1.11× |
| jackson2 | 10KB | 15.567 | 15.322 | 12.464 | 12.717 | 1.22× |
| jackson2 | 100KB | 152.400 | 148.769 | 126.071 | 125.693 | 1.21× |
| jackson3 | 1KB | 1.496 | 1.508 | 1.377 | 1.343 | 1.11× |
| jackson3 | 10KB | 13.735 | 13.789 | 12.286 | 12.252 | 1.12× |
| jackson3 | 100KB | 133.452 | 133.427 | 122.447 | 120.568 | 1.11× |
| fastjson2 | 1KB | 0.831 | — | 0.813 | 0.812 | 1.02× |
| fastjson2 | 10KB | 8.404 | — | 8.385 | 8.336 | 1.01× |
| fastjson2 | 100KB | 82.553 | — | 84.928 | 84.667 | 0.98× |
| gson | 1KB | 4.177 | — | 2.249 | 2.213 | 1.89× |
| gson | 10KB | 41.809 | — | 22.219 | 22.248 | 1.88× |
| gson | 100KB | 405.338 | — | 224.567 | 222.689 | 1.82× |
| jsonp | 1KB | — | — | 1.516 | 1.501 | — |
| jsonp | 10KB | — | — | 14.106 | 14.510 | — |
| jsonp | 100KB | — | — | 143.572 | 146.263 | — |
| simple | 1KB | — | — | 2.522 | 2.509 | — |
| simple | 10KB | — | — | 26.450 | 26.365 | — |
| simple | 100KB | — | — | 405.905 | 403.961 | — |
| snake | 1KB | 79.958 | — | 41.761 | 41.110 | 1.94× |
| snake | 10KB | 898.990 | — | 450.444 | 456.677 | 1.97× |
| snake | 100KB | 9,509.615 | — | 4,635.415 | 4,620.500 | 2.06× |

## Allocation details — untrimmed JMH B/op

Memory uses the original JMH profiler estimate, averaged equally across the two seeds. These values do **not** undergo Best 2 fork selection.

### Read

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 4,832 | 4,816 | 4,728 | 4,728 | 1.02× |
| jackson2 | 10KB | 33,620 | 33,540 | 33,260 | 33,260 | 1.01× |
| jackson2 | 100KB | 330,793 | 330,057 | 327,809 | 327,809 | 1.01× |
| jackson3 | 1KB | 4,148 | 4,132 | 4,020 | 4,020 | 1.03× |
| jackson3 | 10KB | 30,124 | 30,044 | 29,740 | 29,740 | 1.01× |
| jackson3 | 100KB | 297,657 | 296,921 | 294,649 | 294,649 | 1.01× |
| fastjson2 | 1KB | 3,372 | — | 3,316 | 3,316 | 1.02× |
| fastjson2 | 10KB | 29,476 | — | 29,420 | 29,420 | 1.00× |
| fastjson2 | 100KB | 297,328 | — | 297,273 | 297,272 | 1.00× |
| gson | 1KB | 16,644 | — | 16,604 | 16,604 | 1.00× |
| gson | 10KB | 61,040 | — | 60,740 | 60,740 | 1.00× |
| gson | 100KB | 503,938 | — | 501,018 | 501,018 | 1.01× |
| jsonp | 1KB | — | — | 13,992 | 14,023 | — |
| jsonp | 10KB | — | — | 61,080 | 60,752 | — |
| jsonp | 100KB | — | — | 521,259 | 519,966 | — |
| simple | 1KB | — | — | 30,696 | 30,696 | — |
| simple | 10KB | — | — | 73,884 | 73,884 | — |
| simple | 100KB | — | — | 504,235 | 504,234 | — |
| snake | 1KB | 99,748 | — | 83,603 | 83,704 | 1.19× |
| snake | 10KB | 905,071 | — | 673,871 | 666,735 | 1.36× |
| snake | 100KB | 9,088,463 | — | 6,809,021 | 6,760,116 | 1.34× |

#### Users / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 7,176 | 7,176 | 6,816 | 6,816 | 1.05× |
| jackson2 | 10KB | 66,259 | 66,252 | 61,128 | 61,135 | 1.08× |
| jackson2 | 100KB | 637,397 | 637,397 | 589,697 | 589,697 | 1.08× |
| jackson3 | 1KB | 6,492 | 6,492 | 6,236 | 6,236 | 1.04× |
| jackson3 | 10KB | 62,756 | 62,756 | 58,236 | 58,236 | 1.08× |
| jackson3 | 100KB | 604,261 | 604,261 | 562,377 | 562,377 | 1.07× |
| fastjson2 | 1KB | 5,372 | — | 5,500 | 5,500 | 0.98× |
| fastjson2 | 10KB | 57,040 | — | 58,384 | 58,040 | 0.98× |
| fastjson2 | 100KB | 557,157 | — | 569,247 | 570,351 | 0.98× |
| gson | 1KB | 18,740 | — | 18,796 | 18,796 | 1.00× |
| gson | 10KB | 86,512 | — | 89,128 | 89,128 | 0.97× |
| gson | 100KB | 742,494 | — | 767,686 | 767,686 | 0.97× |
| jsonp | 1KB | — | — | 16,664 | 16,664 | — |
| jsonp | 10KB | — | — | 90,817 | 90,817 | — |
| jsonp | 100KB | — | — | 802,938 | 802,938 | — |
| simple | 1KB | — | — | 32,784 | 32,784 | — |
| simple | 10KB | — | — | 101,752 | 101,752 | — |
| simple | 100KB | — | — | 766,123 | 766,123 | — |
| snake | 1KB | 101,124 | — | 85,928 | 86,032 | 1.18× |
| snake | 10KB | 927,857 | — | 708,230 | 700,843 | 1.32× |
| snake | 100KB | 9,332,617 | — | 6,981,997 | 7,008,793 | 1.33× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 6,168 | 6,116 | 4,552 | 4,552 | 1.36× |
| jackson2 | 10KB | 110,959 | 109,552 | 30,703 | 30,720 | 3.61× |
| jackson2 | 100KB | 1,241,403 | 1,225,264 | 282,793 | 282,937 | 4.39× |
| jackson3 | 1KB | 5,380 | 5,328 | 3,740 | 3,740 | 1.44× |
| jackson3 | 10KB | 107,448 | 106,048 | 27,168 | 27,199 | 3.95× |
| jackson3 | 100KB | 1,213,237 | 1,197,084 | 254,609 | 254,753 | 4.76× |
| fastjson2 | 1KB | 3,040 | — | 3,000 | 2,952 | 1.03× |
| fastjson2 | 10KB | 26,664 | — | 26,720 | 26,504 | 1.01× |
| fastjson2 | 100KB | 255,213 | — | 256,333 | 254,605 | 1.00× |
| gson | 1KB | 17,620 | — | 16,276 | 16,276 | 1.08× |
| gson | 10KB | 135,529 | — | 59,188 | 59,212 | 2.29× |
| gson | 100KB | 1,399,426 | — | 486,071 | 486,214 | 2.88× |
| jsonp | 1KB | — | — | 13,736 | 13,736 | — |
| jsonp | 10KB | — | — | 65,358 | 65,419 | — |
| jsonp | 100KB | — | — | 590,168 | 591,671 | — |
| simple | 1KB | — | — | 30,212 | 30,212 | — |
| simple | 10KB | — | — | 67,736 | 67,760 | — |
| simple | 100KB | — | — | 437,975 | 438,119 | — |
| snake | 1KB | 98,501 | — | 83,796 | 83,986 | 1.17× |
| snake | 10KB | 925,165 | — | 647,319 | 649,887 | 1.42× |
| snake | 100KB | 9,134,465 | — | 6,143,448 | 6,171,549 | 1.48× |

#### Clients / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 6,656 | 6,656 | 6,344 | 6,344 | 1.05× |
| jackson2 | 10KB | 53,036 | 53,043 | 49,927 | 49,933 | 1.06× |
| jackson2 | 100KB | 512,285 | 512,299 | 479,905 | 479,905 | 1.07× |
| jackson3 | 1KB | 5,520 | 5,520 | 5,383 | 5,376 | 1.03× |
| jackson3 | 10KB | 47,891 | 47,891 | 45,684 | 45,684 | 1.05× |
| jackson3 | 100KB | 472,161 | 472,161 | 447,177 | 447,177 | 1.06× |
| fastjson2 | 1KB | 4,472 | — | 4,504 | 4,504 | 0.99× |
| fastjson2 | 10KB | 44,188 | — | 45,728 | 45,776 | 0.97× |
| fastjson2 | 100KB | 440,401 | — | 454,677 | 453,429 | 0.97× |
| gson | 1KB | 18,348 | — | 18,280 | 18,280 | 1.00× |
| gson | 10KB | 78,380 | — | 79,012 | 79,012 | 0.99× |
| gson | 100KB | 676,135 | — | 684,999 | 684,999 | 0.99× |
| jsonp | 1KB | — | — | 16,144 | 16,144 | — |
| jsonp | 10KB | — | — | 78,704 | 78,704 | — |
| jsonp | 100KB | — | — | 703,962 | 703,962 | — |
| simple | 1KB | — | — | 31,924 | 31,924 | — |
| simple | 10KB | — | — | 86,560 | 86,560 | — |
| simple | 100KB | — | — | 631,787 | 631,787 | — |
| snake | 1KB | 98,420 | — | 85,376 | 85,504 | 1.15× |
| snake | 10KB | 853,667 | — | 662,265 | 662,265 | 1.29× |
| snake | 100KB | 8,271,565 | — | 6,283,939 | 6,283,939 | 1.32× |

### Write

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 2,052 | 2,036 | 1,900 | 1,900 | 1.08× |
| jackson2 | 10KB | 21,074 | 20,994 | 11,992 | 11,992 | 1.76× |
| jackson2 | 100KB | 199,054 | 198,318 | 112,061 | 112,061 | 1.78× |
| jackson3 | 1KB | 2,076 | 2,060 | 1,924 | 1,924 | 1.08× |
| jackson3 | 10KB | 21,089 | 21,009 | 12,016 | 12,016 | 1.76× |
| jackson3 | 100KB | 199,069 | 198,333 | 112,085 | 112,085 | 1.78× |
| fastjson2 | 1KB | 1,388 | — | 1,332 | 1,332 | 1.04× |
| fastjson2 | 10KB | 10,916 | — | 10,860 | 10,860 | 1.01× |
| fastjson2 | 100KB | 105,116 | — | 105,061 | 105,060 | 1.00× |
| gson | 1KB | 5,480 | — | 2,832 | 2,832 | 1.94× |
| gson | 10KB | 60,620 | — | 22,420 | 22,420 | 2.70× |
| gson | 100KB | 522,167 | — | 216,358 | 216,357 | 2.41× |
| jsonp | 1KB | — | — | 3,800 | 3,776 | — |
| jsonp | 10KB | — | — | 33,209 | 33,209 | — |
| jsonp | 100KB | — | — | 317,177 | 317,175 | — |
| simple | 1KB | — | — | 2,640 | 2,640 | — |
| simple | 10KB | — | — | 22,228 | 22,228 | — |
| simple | 100KB | — | — | 216,168 | 216,167 | — |
| snake | 1KB | 72,338 | — | 62,304 | 62,304 | 1.16× |
| snake | 10KB | 712,732 | — | 592,547 | 592,547 | 1.20× |
| snake | 100KB | 6,830,678 | — | 5,866,154 | 5,866,154 | 1.16× |

#### Users / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1,988 | 1,988 | 1,900 | 1,900 | 1.05× |
| jackson2 | 10KB | 20,754 | 20,754 | 11,992 | 11,992 | 1.73× |
| jackson2 | 100KB | 196,110 | 196,110 | 112,061 | 112,061 | 1.75× |
| jackson3 | 1KB | 2,012 | 2,012 | 1,924 | 1,924 | 1.05× |
| jackson3 | 10KB | 20,769 | 20,769 | 12,016 | 12,016 | 1.73× |
| jackson3 | 100KB | 196,125 | 196,125 | 112,085 | 112,085 | 1.75× |
| fastjson2 | 1KB | 1,388 | — | 1,332 | 1,332 | 1.04× |
| fastjson2 | 10KB | 10,916 | — | 10,860 | 10,860 | 1.01× |
| fastjson2 | 100KB | 105,117 | — | 105,061 | 105,061 | 1.00× |
| gson | 1KB | 5,296 | — | 2,832 | 2,832 | 1.87× |
| gson | 10KB | 59,700 | — | 22,420 | 22,420 | 2.66× |
| gson | 100KB | 513,703 | — | 216,358 | 216,358 | 2.37× |
| jsonp | 1KB | — | — | 3,800 | 3,776 | — |
| jsonp | 10KB | — | — | 33,209 | 33,209 | — |
| jsonp | 100KB | — | — | 317,177 | 317,177 | — |
| simple | 1KB | — | — | 2,688 | 2,688 | — |
| simple | 10KB | — | — | 22,468 | 22,468 | — |
| simple | 100KB | — | — | 218,375 | 218,379 | — |
| snake | 1KB | 72,031 | — | 62,304 | 62,304 | 1.16× |
| snake | 10KB | 706,635 | — | 592,547 | 592,547 | 1.19× |
| snake | 100KB | 6,805,260 | — | 5,866,153 | 5,866,154 | 1.16× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 2,268 | 2,216 | 2,068 | 2,068 | 1.10× |
| jackson2 | 10KB | 32,390 | 30,990 | 20,520 | 20,520 | 1.58× |
| jackson2 | 100KB | 333,145 | 316,993 | 211,793 | 211,793 | 1.57× |
| jackson3 | 1KB | 2,292 | 2,240 | 2,084 | 2,084 | 1.10× |
| jackson3 | 10KB | 32,405 | 31,005 | 20,504 | 20,504 | 1.58× |
| jackson3 | 100KB | 333,161 | 317,008 | 211,506 | 211,505 | 1.58× |
| fastjson2 | 1KB | 1,308 | — | 1,252 | 1,252 | 1.04× |
| fastjson2 | 10KB | 11,252 | — | 11,196 | 11,196 | 1.01× |
| fastjson2 | 100KB | 110,524 | — | 110,469 | 110,468 | 1.00× |
| gson | 1KB | 6,164 | — | 3,168 | 3,168 | 1.95× |
| gson | 10KB | 78,292 | — | 34,260 | 34,260 | 2.29× |
| gson | 100KB | 716,743 | — | 352,870 | 352,870 | 2.03× |
| jsonp | 1KB | — | — | 3,892 | 3,872 | — |
| jsonp | 10KB | — | — | 41,893 | 41,893 | — |
| jsonp | 100KB | — | — | 432,583 | 432,581 | — |
| simple | 1KB | — | — | 2,976 | 2,976 | — |
| simple | 10KB | — | — | 34,068 | 34,068 | — |
| simple | 100KB | — | — | 352,679 | 352,679 | — |
| snake | 1KB | 69,123 | — | 60,940 | 60,940 | 1.13× |
| snake | 10KB | 658,569 | — | 577,450 | 577,455 | 1.14× |
| snake | 100KB | 6,526,725 | — | 5,719,682 | 5,712,482 | 1.14× |

#### Clients / MAP

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| jackson2 | 1KB | 1,988 | 1,988 | 1,900 | 1,900 | 1.05× |
| jackson2 | 10KB | 21,870 | 21,870 | 12,836 | 12,836 | 1.70× |
| jackson2 | 100KB | 209,232 | 209,232 | 120,425 | 120,425 | 1.74× |
| jackson3 | 1KB | 2,012 | 2,012 | 1,924 | 1,924 | 1.05× |
| jackson3 | 10KB | 21,885 | 21,885 | 12,860 | 12,860 | 1.70× |
| jackson3 | 100KB | 209,247 | 209,247 | 120,449 | 120,449 | 1.74× |
| fastjson2 | 1KB | 1,340 | — | 1,284 | 1,284 | 1.04× |
| fastjson2 | 10KB | 11,408 | — | 11,352 | 11,352 | 1.00× |
| fastjson2 | 100KB | 111,705 | — | 111,645 | 111,645 | 1.00× |
| gson | 1KB | 5,640 | — | 3,008 | 3,008 | 1.88× |
| gson | 10KB | 65,836 | — | 26,616 | 26,612 | 2.47× |
| gson | 100KB | 578,647 | — | 261,742 | 261,742 | 2.21× |
| jsonp | 1KB | — | — | 3,564 | 3,540 | — |
| jsonp | 10KB | — | — | 33,429 | 33,429 | — |
| jsonp | 100KB | — | — | 335,697 | 335,697 | — |
| simple | 1KB | — | — | 2,912 | 2,860 | — |
| simple | 10KB | — | — | 26,420 | 26,432 | — |
| simple | 100KB | — | — | 274,583 | 276,295 | — |
| snake | 1KB | 68,487 | — | 60,788 | 60,788 | 1.13× |
| snake | 10KB | 653,312 | — | 569,783 | 569,783 | 1.15× |
| snake | 100KB | 6,499,471 | — | 5,630,905 | 5,633,521 | 1.15× |

## Fork stability

**18 of 1,008 cases** had slowest/fastest fork mean >1.15. Best 2 Mean describes the faster observed JVM state; it is not a confidence interval or guaranteed typical speed. Original three-fork JMH means and scores remain available in the artifact.

| Backend | Operation | Model | Shape | Size | Seed | Implementation | Fork means (µs/op) | Slowest / Fastest |
| --- | --- | --- | --- | ---: | ---: | --- | --- | ---: |
| jsonp | Read | users | pojo | 10KB | 12345 | runtime | 24.968 / 24.596 / 36.409 | 1.48× |
| jackson3 | Read | users | map | 10KB | 12345 | compiled | 17.113 / 17.879 / 23.234 | 1.36× |
| jackson3 | Write | users | pojo | 10KB | 12345 | blackbird | 14.759 / 11.634 / 11.381 | 1.30× |
| jackson3 | Read | clients | pojo | 100KB | 12345 | native | 611.939 / 783.493 / 618.710 | 1.28× |
| simple | Read | users | map | 100KB | 12345 | compiled | 481.875 / 384.502 / 481.963 | 1.25× |
| jackson3 | Read | users | pojo | 100KB | 324 | blackbird | 154.275 / 153.749 / 191.789 | 1.25× |
| simple | Read | users | pojo | 10KB | 12345 | runtime | 50.179 / 41.202 / 50.683 | 1.23× |
| jackson3 | Write | users | pojo | 100KB | 12345 | blackbird | 121.430 / 146.072 / 119.147 | 1.23× |
| jackson2 | Read | users | map | 1KB | 324 | compiled | 2.351 / 2.814 / 2.305 | 1.22× |
| simple | Read | clients | map | 100KB | 324 | runtime | 405.312 / 494.586 / 410.038 | 1.22× |
| fastjson2 | Read | clients | map | 10KB | 324 | native | 10.866 / 13.203 / 10.963 | 1.22× |
| jackson3 | Write | users | pojo | 10KB | 324 | blackbird | 12.757 / 15.481 / 15.279 | 1.21× |
| jackson3 | Write | users | map | 1KB | 324 | compiled | 1.813 / 1.509 / 1.551 | 1.20× |
| jackson3 | Write | users | map | 100KB | 12345 | blackbird | 145.783 / 167.609 / 140.787 | 1.19× |
| simple | Read | clients | map | 100KB | 324 | compiled | 487.243 / 487.859 / 412.559 | 1.18× |
| jackson3 | Write | clients | pojo | 100KB | 324 | runtime | 194.183 / 199.117 / 226.520 | 1.17× |
| jsonp | Read | clients | map | 1KB | 12345 | runtime | 3.272 / 3.244 / 3.765 | 1.16× |
| jackson3 | Write | users | pojo | 1KB | 324 | blackbird | 1.568 / 1.390 / 1.361 | 1.15× |

## Scope limitations

These are results for the provided Fabien fixtures, not general throughput guarantees. Each backend ran in a separate shared GitHub Actions job; do not interpret cross-backend absolute runtime rankings as controlled benchmarks. For release regression testing, run both commits under identical JDK/OS/hardware and profile. `Native/Compiled` >1× does not imply **all** gains arise from compilation: Runtime often captures most benefits. For JSON-P/Simple, the lack of equivalent Native is an explicit limit of this comparison matrix.
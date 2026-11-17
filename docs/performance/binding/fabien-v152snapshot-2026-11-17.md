# Fabien Binding Full Benchmark — v1.5.2-SNAPSHOT — 2026-11-17

> Current exploratory JVM performance snapshot. Previous v1.5.1-SNAPSHOT [complete report](fabien-v151snapshot-2026-11-13.md) and [original JMH artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017) are retained, not replaced.

**1,008 JMH cases · seven backends · 1/10/100KB · Read/Write · Users/Clients · POJO/Map · two seeds.**

## Provenance and methodology

- [GitHub Actions run #44011622909](https://github.com/sjf4j-projects/sjf4j/actions/runs/44011622909), source commit [`555137cf7a98`](https://github.com/sjf4j-projects/sjf4j/commit/555137cf7a98a5ecf0e46ac7efcdd4362e3c0e3e), seven backend jobs completed successfully.
- [Original JMH JSON, independent forks, GC metrics and runner details](https://github.com/sjf4j-projects/sjf4j/actions/runs/44011622909/artifacts/14302041930) (Actions artifacts expire according to retention settings).
- **Temurin OpenJDK 25.0.5**, JMH **1.37**, AverageTime (`µs/op`, lower is better), one thread, three independent forks, five 1s warmup iterations and five 1s measurement iterations; `-prof gc`.
- **Best 2 Mean latency:** take the average of five measurements within each fork, exclude the slowest fork independently for each exact case and implementation, average the other two fork means, then average seeds **12345** and **324** with equal weight.
- **Allocation (`B/op`):** average the **untrimmed** JMH `gc.alloc.rate.norm.score` across both seeds. Allocation is not based on the latency-selected forks.
- **Runner CPUs:** Jackson2, Jackson3, Fastjson2 and SnakeYAML used **Intel Xeon 6975P-C**; Gson, JSON-P and Simple used **AMD EPYC 9V74**.
- **Warning:** GitHub-hosted shared runners are not a controlled performance laboratory. The [2026-11-13 report](fabien-v151snapshot-2026-11-13.md) used AMD EPYC 9V45 for Jackson2/3, Fastjson2 and JSON-P, EPYC 9V74 for Gson and Simple, and EPYC 7763 for SnakeYAML. Do **not** attribute cross-run absolute-time differences to code changes, or compare different backends as if their CPUs were identical.
- Versions: Jackson2 Databind and Blackbird 2.22.3, Jackson3 Databind and Blackbird 3.2.3, Fastjson2 2.0.66, Gson 2.13.1, JSON-P API 2.1.3 / Parsson 1.1.7, SnakeYAML 2.5.
- **Models:** Users uses scalar `String`, numbers and booleans with nested objects/collections (UUID-like data are strings); Clients includes typed UUID, Java time, BigDecimal and enums.
- Native/Blackbird, Runtime and Compiled perform **independent** `byte[]` Read or Write measurements; fixtures, binder setup and correctness checks are excluded from timing. `—` means unavailable. JSON-P and Simple have no equivalent Native mode.

## Latency details — Best 2 Mean, µs/op

Columns are **Native → Blackbird → Runtime → Compiled**. `Native / Compiled` is computed from the unrounded measured latency, with higher ratios indicating more relative speed; a ratio below 1× means Compiled is slower.

### Read

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2.611 | 2.256 | 2.313 | 2.131 | 1.23× |
| Jackson2 | 10KB | 20.716 | 17.728 | 19.170 | 17.004 | 1.22× |
| Jackson2 | 100KB | 203.941 | 175.321 | 190.035 | 168.130 | 1.21× |
| Jackson3 | 1KB | 2.173 | 2.127 | 2.044 | 1.855 | 1.17× |
| Jackson3 | 10KB | 18.091 | 17.460 | 17.546 | 15.820 | 1.14× |
| Jackson3 | 100KB | 177.626 | 172.209 | 175.334 | 154.915 | 1.15× |
| Fastjson2 | 1KB | 0.753 | — | 1.014 | 0.709 | 1.06× |
| Fastjson2 | 10KB | 6.404 | — | 8.853 | 5.908 | 1.08× |
| Fastjson2 | 100KB | 66.983 | — | 92.234 | 62.825 | 1.07× |
| Gson | 1KB | 4.795 | — | 4.703 | 3.801 | 1.26× |
| Gson | 10KB | 33.974 | — | 34.972 | 28.640 | 1.19× |
| Gson | 100KB | 324.653 | — | 328.452 | 276.769 | 1.17× |
| JSON-P | 1KB | — | — | 4.337 | 3.889 | — |
| JSON-P | 10KB | — | — | 36.891 | 33.866 | — |
| JSON-P | 100KB | — | — | 358.748 | 323.230 | — |
| Simple | 1KB | — | — | 5.621 | 5.019 | — |
| Simple | 10KB | — | — | 41.773 | 35.035 | — |
| Simple | 100KB | — | — | 397.698 | 343.756 | — |
| SnakeYAML | 1KB | 41.269 | — | 31.073 | 30.753 | 1.34× |
| SnakeYAML | 10KB | 409.486 | — | 292.055 | 286.376 | 1.43× |
| SnakeYAML | 100KB | 5258.923 | — | 2956.423 | 2883.891 | 1.82× |

#### Users / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2.524 | 2.566 | 2.474 | 2.442 | 1.03× |
| Jackson2 | 10KB | 21.197 | 21.425 | 21.161 | 21.140 | 1.00× |
| Jackson2 | 100KB | 202.624 | 201.839 | 200.480 | 201.440 | 1.01× |
| Jackson3 | 1KB | 2.168 | 2.164 | 2.157 | 2.148 | 1.01× |
| Jackson3 | 10KB | 19.784 | 19.922 | 19.240 | 19.252 | 1.03× |
| Jackson3 | 100KB | 185.130 | 188.584 | 185.071 | 184.895 | 1.00× |
| Fastjson2 | 1KB | 1.197 | — | 1.385 | 1.418 | 0.84× |
| Fastjson2 | 10KB | 11.050 | — | 12.566 | 12.693 | 0.87× |
| Fastjson2 | 100KB | 110.783 | — | 116.187 | 116.490 | 0.95× |
| Gson | 1KB | 4.451 | — | 3.889 | 3.787 | 1.18× |
| Gson | 10KB | 33.767 | — | 30.314 | 30.378 | 1.11× |
| Gson | 100KB | 323.540 | — | 298.747 | 305.337 | 1.06× |
| JSON-P | 1KB | — | — | 4.085 | 4.090 | — |
| JSON-P | 10KB | — | — | 36.318 | 36.095 | — |
| JSON-P | 100KB | — | — | 360.548 | 356.440 | — |
| Simple | 1KB | — | — | 5.353 | 5.465 | — |
| Simple | 10KB | — | — | 39.048 | 39.007 | — |
| Simple | 100KB | — | — | 381.605 | 381.381 | — |
| SnakeYAML | 1KB | 39.555 | — | 31.444 | 31.646 | 1.25× |
| SnakeYAML | 10KB | 393.258 | — | 295.750 | 296.063 | 1.33× |
| SnakeYAML | 100KB | 5144.178 | — | 2929.553 | 3007.330 | 1.71× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 3.157 | 2.954 | 2.685 | 2.319 | 1.36× |
| Jackson2 | 10KB | 55.422 | 51.967 | 22.794 | 19.803 | 2.80× |
| Jackson2 | 100KB | 607.981 | 578.599 | 221.798 | 194.350 | 3.13× |
| Jackson3 | 1KB | 2.654 | 2.640 | 2.309 | 1.978 | 1.34× |
| Jackson3 | 10KB | 53.180 | 52.931 | 21.132 | 18.607 | 2.86× |
| Jackson3 | 100KB | 594.815 | 589.579 | 209.817 | 184.320 | 3.23× |
| Fastjson2 | 1KB | 0.829 | — | 1.175 | 0.757 | 1.10× |
| Fastjson2 | 10KB | 7.575 | — | 10.984 | 7.195 | 1.05× |
| Fastjson2 | 100KB | 75.874 | — | 111.432 | 73.124 | 1.04× |
| Gson | 1KB | 5.390 | — | 4.667 | 3.920 | 1.37× |
| Gson | 10KB | 77.484 | — | 39.867 | 33.587 | 2.31× |
| Gson | 100KB | 817.038 | — | 388.026 | 336.439 | 2.43× |
| JSON-P | 1KB | — | — | 4.679 | 3.954 | — |
| JSON-P | 10KB | — | — | 45.482 | 40.535 | — |
| JSON-P | 100KB | — | — | 492.846 | 435.598 | — |
| Simple | 1KB | — | — | 5.796 | 5.147 | — |
| Simple | 10KB | — | — | 39.407 | 35.368 | — |
| Simple | 100KB | — | — | 409.674 | 379.636 | — |
| SnakeYAML | 1KB | 41.263 | — | 31.851 | 31.071 | 1.33× |
| SnakeYAML | 10KB | 437.733 | — | 294.850 | 288.114 | 1.52× |
| SnakeYAML | 100KB | 5304.537 | — | 2878.002 | 2852.980 | 1.86× |

#### Clients / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2.682 | 2.671 | 2.624 | 2.598 | 1.03× |
| Jackson2 | 10KB | 22.832 | 22.559 | 21.760 | 21.500 | 1.06× |
| Jackson2 | 100KB | 206.880 | 207.542 | 207.524 | 208.015 | 0.99× |
| Jackson3 | 1KB | 2.128 | 2.144 | 2.127 | 2.123 | 1.00× |
| Jackson3 | 10KB | 19.945 | 20.158 | 19.142 | 19.354 | 1.03× |
| Jackson3 | 100KB | 189.985 | 190.030 | 189.990 | 190.770 | 1.00× |
| Fastjson2 | 1KB | 1.329 | — | 1.367 | 1.458 | 0.91× |
| Fastjson2 | 10KB | 13.178 | — | 14.950 | 14.817 | 0.89× |
| Fastjson2 | 100KB | 134.818 | — | 140.324 | 139.089 | 0.97× |
| Gson | 1KB | 4.654 | — | 4.190 | 4.080 | 1.14× |
| Gson | 10KB | 39.114 | — | 36.033 | 36.117 | 1.08× |
| Gson | 100KB | 379.350 | — | 353.261 | 364.736 | 1.04× |
| JSON-P | 1KB | — | — | 4.352 | 4.276 | — |
| JSON-P | 10KB | — | — | 37.496 | 37.727 | — |
| JSON-P | 100KB | — | — | 370.813 | 372.060 | — |
| Simple | 1KB | — | — | 5.855 | 5.826 | — |
| Simple | 10KB | — | — | 41.269 | 40.773 | — |
| Simple | 100KB | — | — | 447.748 | 451.005 | — |
| SnakeYAML | 1KB | 39.050 | — | 32.454 | 32.401 | 1.21× |
| SnakeYAML | 10KB | 373.449 | — | 293.264 | 294.803 | 1.27× |
| SnakeYAML | 100KB | 4684.815 | — | 2900.096 | 2893.688 | 1.62× |

### Write

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 1.795 | 1.587 | 1.665 | 1.381 | 1.30× |
| Jackson2 | 10KB | 17.958 | 15.512 | 17.284 | 14.137 | 1.27× |
| Jackson2 | 100KB | 184.641 | 158.201 | 151.034 | 138.314 | 1.33× |
| Jackson3 | 1KB | 1.667 | 1.575 | 1.725 | 1.449 | 1.15× |
| Jackson3 | 10KB | 16.500 | 15.350 | 17.784 | 15.455 | 1.07× |
| Jackson3 | 100KB | 163.961 | 167.440 | 142.838 | 140.838 | 1.16× |
| Fastjson2 | 1KB | 0.611 | — | 0.737 | 0.579 | 1.05× |
| Fastjson2 | 10KB | 5.729 | — | 7.816 | 5.684 | 1.01× |
| Fastjson2 | 100KB | 57.624 | — | 75.113 | 56.816 | 1.01× |
| Gson | 1KB | 4.927 | — | 2.516 | 2.171 | 2.27× |
| Gson | 10KB | 47.332 | — | 25.026 | 21.558 | 2.20× |
| Gson | 100KB | 444.929 | — | 244.082 | 212.536 | 2.09× |
| JSON-P | 1KB | — | — | 2.726 | 2.128 | — |
| JSON-P | 10KB | — | — | 23.739 | 19.103 | — |
| JSON-P | 100KB | — | — | 235.372 | 192.242 | — |
| Simple | 1KB | — | — | 2.914 | 2.607 | — |
| Simple | 10KB | — | — | 27.405 | 24.847 | — |
| Simple | 100KB | — | — | 500.193 | 479.252 | — |
| SnakeYAML | 1KB | 56.798 | — | 31.048 | 30.079 | 1.89× |
| SnakeYAML | 10KB | 620.111 | — | 304.847 | 296.259 | 2.09× |
| SnakeYAML | 100KB | 6338.410 | — | 2955.721 | 2875.113 | 2.20× |

#### Users / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2.037 | 2.087 | 1.803 | 1.800 | 1.13× |
| Jackson2 | 10KB | 21.520 | 23.222 | 16.876 | 16.763 | 1.28× |
| Jackson2 | 100KB | 180.817 | 183.035 | 166.649 | 166.924 | 1.08× |
| Jackson3 | 1KB | 1.986 | 1.992 | 1.844 | 1.872 | 1.06× |
| Jackson3 | 10KB | 23.062 | 23.145 | 16.918 | 16.815 | 1.37× |
| Jackson3 | 100KB | 194.688 | 192.359 | 167.275 | 166.392 | 1.17× |
| Fastjson2 | 1KB | 0.916 | — | 0.867 | 0.875 | 1.05× |
| Fastjson2 | 10KB | 9.419 | — | 9.243 | 9.406 | 1.00× |
| Fastjson2 | 100KB | 92.062 | — | 92.633 | 93.469 | 0.98× |
| Gson | 1KB | 4.600 | — | 2.382 | 2.380 | 1.93× |
| Gson | 10KB | 46.115 | — | 22.442 | 22.303 | 2.07× |
| Gson | 100KB | 435.824 | — | 218.610 | 220.268 | 1.98× |
| JSON-P | 1KB | — | — | 2.242 | 2.228 | — |
| JSON-P | 10KB | — | — | 21.566 | 21.485 | — |
| JSON-P | 100KB | — | — | 214.054 | 213.711 | — |
| Simple | 1KB | — | — | 2.770 | 2.762 | — |
| Simple | 10KB | — | — | 26.681 | 26.652 | — |
| Simple | 100KB | — | — | 491.865 | 493.683 | — |
| SnakeYAML | 1KB | 53.360 | — | 31.068 | 30.843 | 1.73× |
| SnakeYAML | 10KB | 594.840 | — | 305.387 | 303.760 | 1.96× |
| SnakeYAML | 100KB | 6141.623 | — | 2973.697 | 2969.199 | 2.07× |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 1.826 | 1.821 | 2.075 | 1.617 | 1.13× |
| Jackson2 | 10KB | 30.880 | 29.976 | 23.340 | 21.085 | 1.46× |
| Jackson2 | 100KB | 339.859 | 328.429 | 260.249 | 222.448 | 1.53× |
| Jackson3 | 1KB | 1.780 | 1.787 | 2.109 | 1.692 | 1.05× |
| Jackson3 | 10KB | 30.173 | 29.563 | 23.528 | 21.057 | 1.43× |
| Jackson3 | 100KB | 325.825 | 322.104 | 258.515 | 224.159 | 1.45× |
| Fastjson2 | 1KB | 0.559 | — | 0.791 | 0.574 | 0.97× |
| Fastjson2 | 10KB | 5.544 | — | 8.294 | 5.756 | 0.96× |
| Fastjson2 | 100KB | 51.584 | — | 80.823 | 52.470 | 0.98× |
| Gson | 1KB | 4.940 | — | 2.565 | 2.234 | 2.21× |
| Gson | 10KB | 50.395 | — | 29.030 | 25.945 | 1.94× |
| Gson | 100KB | 494.328 | — | 300.723 | 276.068 | 1.79× |
| JSON-P | 1KB | — | — | 2.614 | 2.114 | — |
| JSON-P | 10KB | — | — | 28.125 | 23.830 | — |
| JSON-P | 100KB | — | — | 304.025 | 263.009 | — |
| Simple | 1KB | — | — | 2.834 | 2.492 | — |
| Simple | 10KB | — | — | 32.326 | 29.495 | — |
| Simple | 100KB | — | — | 488.017 | 460.483 | — |
| SnakeYAML | 1KB | 52.522 | — | 30.155 | 29.516 | 1.78× |
| SnakeYAML | 10KB | 568.882 | — | 328.304 | 324.143 | 1.76× |
| SnakeYAML | 100KB | 6282.278 | — | 3356.756 | 3305.835 | 1.90× |

#### Clients / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 1.811 | 1.836 | 1.705 | 1.668 | 1.09× |
| Jackson2 | 10KB | 19.968 | 19.945 | 15.956 | 15.694 | 1.27× |
| Jackson2 | 100KB | 179.214 | 182.015 | 162.124 | 163.238 | 1.10× |
| Jackson3 | 1KB | 1.820 | 1.821 | 1.754 | 1.763 | 1.03× |
| Jackson3 | 10KB | 19.317 | 19.357 | 16.066 | 16.029 | 1.21× |
| Jackson3 | 100KB | 187.296 | 186.898 | 158.326 | 162.740 | 1.15× |
| Fastjson2 | 1KB | 0.856 | — | 0.805 | 0.807 | 1.06× |
| Fastjson2 | 10KB | 8.696 | — | 8.521 | 8.429 | 1.03× |
| Fastjson2 | 100KB | 86.494 | — | 86.783 | 86.813 | 1.00× |
| Gson | 1KB | 4.179 | — | 2.266 | 2.244 | 1.86× |
| Gson | 10KB | 41.964 | — | 22.244 | 22.166 | 1.89× |
| Gson | 100KB | 406.661 | — | 226.447 | 223.916 | 1.82× |
| JSON-P | 1KB | — | — | 2.101 | 2.073 | — |
| JSON-P | 10KB | — | — | 20.522 | 20.499 | — |
| JSON-P | 100KB | — | — | 215.081 | 214.944 | — |
| Simple | 1KB | — | — | 2.546 | 2.517 | — |
| Simple | 10KB | — | — | 26.293 | 26.403 | — |
| Simple | 100KB | — | — | 405.009 | 400.044 | — |
| SnakeYAML | 1KB | 52.470 | — | 29.738 | 29.785 | 1.76× |
| SnakeYAML | 10KB | 593.654 | — | 320.109 | 322.135 | 1.84× |
| SnakeYAML | 100KB | 6429.458 | — | 3259.026 | 3277.878 | 1.96× |

## Allocation details — untrimmed JMH B/op

Lower is better. B/op is the arithmetic mean across both seeds and **does not** use the latency Best 2 fork selection. The last column is intentionally omitted here because a latency speedup ratio does not apply to allocations.

### Read

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 4,832 | 4,816 | 4,728 | 4,728 |
| Jackson2 | 10KB | 33,620 | 33,540 | 33,260 | 33,260 |
| Jackson2 | 100KB | 330,793 | 330,057 | 327,809 | 327,809 |
| Jackson3 | 1KB | 4,148 | 4,132 | 4,020 | 4,020 |
| Jackson3 | 10KB | 30,124 | 30,044 | 29,740 | 29,740 |
| Jackson3 | 100KB | 297,657 | 296,921 | 294,649 | 294,649 |
| Fastjson2 | 1KB | 3,372 | — | 3,316 | 3,316 |
| Fastjson2 | 10KB | 29,476 | — | 29,420 | 29,420 |
| Fastjson2 | 100KB | 297,328 | — | 297,273 | 297,272 |
| Gson | 1KB | 16,644 | — | 16,604 | 16,604 |
| Gson | 10KB | 61,036 | — | 60,740 | 60,740 |
| Gson | 100KB | 503,938 | — | 501,018 | 501,018 |
| JSON-P | 1KB | — | — | 13,992 | 14,084 |
| JSON-P | 10KB | — | — | 60,783 | 61,120 |
| JSON-P | 100KB | — | — | 520,493 | 518,432 |
| Simple | 1KB | — | — | 30,696 | 30,696 |
| Simple | 10KB | — | — | 73,884 | 73,884 |
| Simple | 100KB | — | — | 504,235 | 504,234 |
| SnakeYAML | 1KB | 101,144 | — | 85,236 | 85,236 |
| SnakeYAML | 10KB | 919,100 | — | 681,422 | 681,422 |
| SnakeYAML | 100KB | 9,187,033 | — | 6,757,925 | 6,746,440 |

#### Users / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7,176 | 7,176 | 6,816 | 6,816 |
| Jackson2 | 10KB | 66,252 | 66,259 | 61,128 | 61,135 |
| Jackson2 | 100KB | 637,411 | 637,404 | 589,697 | 589,697 |
| Jackson3 | 1KB | 6,492 | 6,492 | 6,236 | 6,236 |
| Jackson3 | 10KB | 62,756 | 62,756 | 58,236 | 58,236 |
| Jackson3 | 100KB | 604,261 | 604,261 | 562,377 | 562,377 |
| Fastjson2 | 1KB | 5,372 | — | 5,500 | 5,500 |
| Fastjson2 | 10KB | 57,040 | — | 58,284 | 58,456 |
| Fastjson2 | 100KB | 557,157 | — | 565,629 | 566,935 |
| Gson | 1KB | 18,744 | — | 18,796 | 18,796 |
| Gson | 10KB | 86,508 | — | 89,128 | 89,128 |
| Gson | 100KB | 742,498 | — | 767,686 | 767,686 |
| JSON-P | 1KB | — | — | 16,664 | 16,664 |
| JSON-P | 10KB | — | — | 90,817 | 90,817 |
| JSON-P | 100KB | — | — | 802,939 | 802,939 |
| Simple | 1KB | — | — | 32,784 | 32,784 |
| Simple | 10KB | — | — | 101,752 | 101,752 |
| Simple | 100KB | — | — | 766,123 | 766,123 |
| SnakeYAML | 1KB | 102,520 | — | 87,280 | 87,564 |
| SnakeYAML | 10KB | 942,543 | — | 717,483 | 713,322 |
| SnakeYAML | 100KB | 9,429,418 | — | 6,984,293 | 7,058,345 |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6,168 | 6,116 | 4,552 | 4,552 |
| Jackson2 | 10KB | 110,959 | 109,552 | 30,696 | 30,727 |
| Jackson2 | 100KB | 1,241,416 | 1,225,244 | 282,800 | 282,937 |
| Jackson3 | 1KB | 5,380 | 5,328 | 3,740 | 3,740 |
| Jackson3 | 10KB | 107,449 | 106,048 | 27,168 | 27,192 |
| Jackson3 | 100KB | 1,213,236 | 1,197,084 | 254,609 | 254,753 |
| Fastjson2 | 1KB | 3,040 | — | 3,000 | 2,952 |
| Fastjson2 | 10KB | 26,664 | — | 26,720 | 26,504 |
| Fastjson2 | 100KB | 255,213 | — | 256,333 | 254,605 |
| Gson | 1KB | 17,620 | — | 16,276 | 16,276 |
| Gson | 10KB | 135,529 | — | 59,188 | 59,212 |
| Gson | 100KB | 1,399,426 | — | 486,071 | 486,214 |
| JSON-P | 1KB | — | — | 13,736 | 13,736 |
| JSON-P | 10KB | — | — | 65,338 | 65,524 |
| JSON-P | 100KB | — | — | 591,128 | 590,135 |
| Simple | 1KB | — | — | 30,212 | 30,212 |
| Simple | 10KB | — | — | 67,736 | 67,760 |
| Simple | 100KB | — | — | 437,975 | 438,119 |
| SnakeYAML | 1KB | 100,247 | — | 85,666 | 85,268 |
| SnakeYAML | 10KB | 940,171 | — | 663,170 | 662,461 |
| SnakeYAML | 100KB | 9,319,027 | — | 6,298,548 | 6,298,692 |

#### Clients / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6,656 | 6,656 | 6,344 | 6,344 |
| Jackson2 | 10KB | 53,036 | 53,036 | 49,920 | 49,920 |
| Jackson2 | 100KB | 512,285 | 512,299 | 479,905 | 479,905 |
| Jackson3 | 1KB | 5,520 | 5,520 | 5,376 | 5,376 |
| Jackson3 | 10KB | 47,884 | 47,884 | 45,684 | 45,684 |
| Jackson3 | 100KB | 472,161 | 472,161 | 447,177 | 447,177 |
| Fastjson2 | 1KB | 4,472 | — | 4,504 | 4,504 |
| Fastjson2 | 10KB | 44,188 | — | 45,728 | 45,680 |
| Fastjson2 | 100KB | 440,401 | — | 454,677 | 453,429 |
| Gson | 1KB | 18,344 | — | 18,280 | 18,280 |
| Gson | 10KB | 78,372 | — | 79,012 | 79,012 |
| Gson | 100KB | 676,139 | — | 684,999 | 684,999 |
| JSON-P | 1KB | — | — | 16,144 | 16,144 |
| JSON-P | 10KB | — | — | 78,705 | 78,705 |
| JSON-P | 100KB | — | — | 703,963 | 703,963 |
| Simple | 1KB | — | — | 31,924 | 31,924 |
| Simple | 10KB | — | — | 86,560 | 86,560 |
| Simple | 100KB | — | — | 631,787 | 631,787 |
| SnakeYAML | 1KB | 99,700 | — | 87,347 | 87,214 |
| SnakeYAML | 10KB | 870,251 | — | 677,350 | 679,894 |
| SnakeYAML | 100KB | 8,426,669 | — | 6,439,040 | 6,439,040 |

### Write

#### Users / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2,052 | 2,036 | 1,900 | 1,900 |
| Jackson2 | 10KB | 21,074 | 20,994 | 11,992 | 11,992 |
| Jackson2 | 100KB | 199,054 | 198,318 | 112,061 | 112,061 |
| Jackson3 | 1KB | 2,076 | 2,060 | 1,924 | 1,924 |
| Jackson3 | 10KB | 21,089 | 21,009 | 12,016 | 12,016 |
| Jackson3 | 100KB | 199,070 | 198,334 | 112,085 | 112,085 |
| Fastjson2 | 1KB | 1,388 | — | 1,332 | 1,332 |
| Fastjson2 | 10KB | 10,916 | — | 10,860 | 10,860 |
| Fastjson2 | 100KB | 105,116 | — | 105,061 | 105,060 |
| Gson | 1KB | 5,480 | — | 2,832 | 2,832 |
| Gson | 10KB | 60,620 | — | 22,424 | 22,420 |
| Gson | 100KB | 522,167 | — | 216,358 | 216,358 |
| JSON-P | 1KB | — | — | 3,800 | 3,776 |
| JSON-P | 10KB | — | — | 33,209 | 33,209 |
| JSON-P | 100KB | — | — | 317,176 | 317,176 |
| Simple | 1KB | — | — | 2,640 | 2,640 |
| Simple | 10KB | — | — | 22,228 | 22,228 |
| Simple | 100KB | — | — | 216,168 | 216,167 |
| SnakeYAML | 1KB | 72,402 | — | 62,304 | 62,304 |
| SnakeYAML | 10KB | 712,738 | — | 592,546 | 592,546 |
| SnakeYAML | 100KB | 6,836,564 | — | 5,866,142 | 5,853,193 |

#### Users / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 1,988 | 1,988 | 1,900 | 1,900 |
| Jackson2 | 10KB | 20,754 | 20,754 | 11,992 | 11,992 |
| Jackson2 | 100KB | 196,110 | 196,111 | 112,061 | 112,061 |
| Jackson3 | 1KB | 2,012 | 2,012 | 1,924 | 1,924 |
| Jackson3 | 10KB | 20,769 | 20,769 | 12,016 | 12,016 |
| Jackson3 | 100KB | 196,125 | 196,125 | 112,085 | 112,085 |
| Fastjson2 | 1KB | 1,388 | — | 1,332 | 1,332 |
| Fastjson2 | 10KB | 10,916 | — | 10,860 | 10,860 |
| Fastjson2 | 100KB | 105,117 | — | 105,061 | 105,061 |
| Gson | 1KB | 5,296 | — | 2,836 | 2,832 |
| Gson | 10KB | 59,700 | — | 22,424 | 22,420 |
| Gson | 100KB | 513,703 | — | 216,358 | 216,358 |
| JSON-P | 1KB | — | — | 3,800 | 3,776 |
| JSON-P | 10KB | — | — | 33,213 | 33,209 |
| JSON-P | 100KB | — | — | 317,176 | 317,176 |
| Simple | 1KB | — | — | 2,688 | 2,696 |
| Simple | 10KB | — | — | 22,468 | 22,472 |
| Simple | 100KB | — | — | 218,375 | 218,376 |
| SnakeYAML | 1KB | 71,993 | — | 62,304 | 62,304 |
| SnakeYAML | 10KB | 706,658 | — | 592,546 | 592,546 |
| SnakeYAML | 100KB | 6,805,345 | — | 5,866,143 | 5,866,140 |

#### Clients / POJO

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 2,268 | 2,216 | 2,060 | 2,060 |
| Jackson2 | 10KB | 32,390 | 30,990 | 20,480 | 20,480 |
| Jackson2 | 100KB | 333,147 | 316,993 | 211,482 | 211,482 |
| Jackson3 | 1KB | 2,292 | 2,240 | 2,084 | 2,084 |
| Jackson3 | 10KB | 32,405 | 31,005 | 20,504 | 20,504 |
| Jackson3 | 100KB | 333,161 | 317,009 | 211,506 | 211,506 |
| Fastjson2 | 1KB | 1,308 | — | 1,252 | 1,252 |
| Fastjson2 | 10KB | 11,252 | — | 11,196 | 11,196 |
| Fastjson2 | 100KB | 110,524 | — | 110,469 | 110,468 |
| Gson | 1KB | 6,164 | — | 3,168 | 3,168 |
| Gson | 10KB | 78,292 | — | 34,260 | 34,260 |
| Gson | 100KB | 716,743 | — | 352,870 | 352,870 |
| JSON-P | 1KB | — | — | 3,896 | 3,872 |
| JSON-P | 10KB | — | — | 41,893 | 41,893 |
| JSON-P | 100KB | — | — | 432,582 | 432,584 |
| Simple | 1KB | — | — | 2,976 | 2,976 |
| Simple | 10KB | — | — | 34,068 | 34,068 |
| Simple | 100KB | — | — | 352,679 | 352,679 |
| SnakeYAML | 1KB | 69,196 | — | 60,948 | 60,948 |
| SnakeYAML | 10KB | 659,241 | — | 577,454 | 577,449 |
| SnakeYAML | 100KB | 6,539,835 | — | 5,724,599 | 5,724,599 |

#### Clients / Map

| Backend | Size | Native | Blackbird | Runtime | Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 1,988 | 1,988 | 1,900 | 1,900 |
| Jackson2 | 10KB | 21,870 | 21,870 | 12,836 | 12,836 |
| Jackson2 | 100KB | 209,233 | 209,233 | 120,425 | 120,425 |
| Jackson3 | 1KB | 2,012 | 2,012 | 1,924 | 1,924 |
| Jackson3 | 10KB | 21,885 | 21,885 | 12,860 | 12,860 |
| Jackson3 | 100KB | 209,248 | 209,248 | 120,449 | 120,449 |
| Fastjson2 | 1KB | 1,340 | — | 1,284 | 1,284 |
| Fastjson2 | 10KB | 11,408 | — | 11,352 | 11,352 |
| Fastjson2 | 100KB | 111,701 | — | 111,645 | 111,645 |
| Gson | 1KB | 5,640 | — | 3,008 | 3,008 |
| Gson | 10KB | 65,836 | — | 26,612 | 26,612 |
| Gson | 100KB | 578,647 | — | 261,742 | 261,742 |
| JSON-P | 1KB | — | — | 3,564 | 3,540 |
| JSON-P | 10KB | — | — | 33,433 | 33,429 |
| JSON-P | 100KB | — | — | 335,697 | 335,698 |
| Simple | 1KB | — | — | 2,940 | 2,852 |
| Simple | 10KB | — | — | 26,420 | 26,424 |
| Simple | 100KB | — | — | 279,807 | 272,551 |
| SnakeYAML | 1KB | 68,465 | — | 60,788 | 60,788 |
| SnakeYAML | 10KB | 653,271 | — | 569,782 | 569,782 |
| SnakeYAML | 100KB | 6,499,447 | — | 5,630,895 | 5,633,511 |

## Historical comparison

The [2026-11-13 v1.5.1-SNAPSHOT report](fabien-v151snapshot-2026-11-13.md) and [its original artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/43359529434/artifacts/14012601017) are preserved. For comparative interpretation, focus first on **Native / Compiled within the same backend job**. Absolute latency across releases is exploratory because the runner CPU families differ. In particular, Jackson2's shared `FastUuid` writer and Jackson3's compiled native structural traversal are present in this v1.5.2-SNAPSHOT source commit.

See [Binding JVM performance](../binding.md) for the current 100KB highlights, baseline policy and execution instructions.

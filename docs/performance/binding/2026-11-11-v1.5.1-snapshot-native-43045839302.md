# Native Image Fabien Binding Full — v1.5.1-SNAPSHOT — 2026-11-11 — Parallel

> Historical Native Image performance snapshot. Run [#43045839302](https://github.com/sjf4j-projects/sjf4j/actions/runs/43045839302), SHA `05fc26b5f3e4500973caf95eb7af6a7a2ab50fb3`.

**288 exact JMH cases × 3 independent native processes = 864 process-level results.** Four backends; Read/Write; Users/Clients; POJO/Map; 1/10/100KB; seed 12345.

- Environment: GraalVM Community 25.5.5.0.3 / Native Image, `-O2`, no PGO; Ubuntu 24.04 Linux x86_64, AMD EPYC 7763 / EPYC 9V45 across backend runners.
- **Comparison dependency versions (at the source commit):** Jackson2 Databind `2.22.3`; Jackson3 Databind `3.2.3`; Fastjson2 `2.0.66`; Gson `2.13.1`.
- Mode: parallel; Native, Runtime and Compiled for each backend run on the same runner.
- JMH 1.37: one thread; five 1s warmup and five 1s measurement iterations, `-f 0`; three independent processes per exact case.
- Metric: µs/op (lower is better); Native / Compiled = direct third-party Binding latency / generated Binding latency.
- **Best 2 Run Mean:** for each exact case, select the two lowest JMH process scores and take their arithmetic mean. Ratios are computed before display rounding.
- [Original JMH JSON, runner metadata and process statistics](https://github.com/sjf4j-projects/sjf4j/actions/runs/43045839302/artifacts/13868789457). Artifacts expire; this file keeps all 96 aggregated case combinations.
- GitHub shared runners are exploratory. A different CPU model cannot be treated as an equivalent measurement environment.

## Latency results — Best 2 Run Mean, µs/op

### Read

#### Users / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7.875 | 25.587 | 5.913 | 1.33× |
| Jackson2 | 10KB | 68.474 | 266.446 | 47.289 | 1.45× |
| Jackson2 | 100KB | 667.428 | 2,527.232 | 473.922 | 1.41× |
| Jackson3 | 1KB | 17.154 | 14.535 | 2.694 | 6.37× |
| Jackson3 | 10KB | 157.528 | 147.910 | 22.181 | 7.10× |
| Jackson3 | 100KB | 1,540.205 | 1,409.623 | 224.305 | 6.87× |
| Fastjson2 | 1KB | 6.452 | 20.982 | 1.803 | 3.58× |
| Fastjson2 | 10KB | 92.408 | 225.167 | 14.853 | 6.22× |
| Fastjson2 | 100KB | 843.042 | 2,135.029 | 148.758 | 5.67× |
| Gson | 1KB | 4.932 | 16.337 | 4.315 | 1.14× |
| Gson | 10KB | 37.038 | 161.042 | 31.055 | 1.19× |
| Gson | 100KB | 366.156 | 1,539.799 | 316.337 | 1.16× |

#### Users / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.944 | 6.799 | 6.749 | 1.03× |
| Jackson2 | 10KB | 57.356 | 55.949 | 55.590 | 1.03× |
| Jackson2 | 100KB | 572.367 | 559.559 | 558.572 | 1.02× |
| Jackson3 | 1KB | 3.323 | 3.336 | 3.263 | 1.02× |
| Jackson3 | 10KB | 26.446 | 27.760 | 27.625 | 0.96× |
| Jackson3 | 100KB | 260.270 | 273.317 | 274.696 | 0.95× |
| Fastjson2 | 1KB | 3.117 | 3.156 | 3.104 | 1.00× |
| Fastjson2 | 10KB | 27.811 | 25.905 | 25.791 | 1.08× |
| Fastjson2 | 100KB | 274.671 | 254.811 | 254.342 | 1.08× |
| Gson | 1KB | 5.207 | 4.691 | 4.699 | 1.11× |
| Gson | 10KB | 36.246 | 34.738 | 34.953 | 1.04× |
| Gson | 100KB | 351.288 | 329.706 | 335.920 | 1.05× |

#### Clients / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 8.976 | 20.114 | 6.757 | 1.33× |
| Jackson2 | 10KB | 156.687 | 208.028 | 58.357 | 2.68× |
| Jackson2 | 100KB | 1,956.052 | 2,205.969 | 561.093 | 3.49× |
| Jackson3 | 1KB | 14.829 | 11.257 | 3.303 | 4.49× |
| Jackson3 | 10KB | 210.984 | 117.951 | 29.098 | 7.25× |
| Jackson3 | 100KB | 2,554.728 | 1,275.258 | 274.207 | 9.32× |
| Fastjson2 | 1KB | 4.567 | 15.006 | 2.101 | 2.17× |
| Fastjson2 | 10KB | 59.443 | 164.247 | 21.048 | 2.82× |
| Fastjson2 | 100KB | 693.782 | 1,775.495 | 210.505 | 3.30× |
| Gson | 1KB | 5.880 | 13.296 | 4.920 | 1.20× |
| Gson | 10KB | 87.561 | 135.818 | 42.204 | 2.07× |
| Gson | 100KB | 1,098.593 | 1,434.647 | 418.005 | 2.63× |

#### Clients / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7.642 | 7.726 | 7.608 | 1.00× |
| Jackson2 | 10KB | 60.954 | 61.508 | 61.256 | 1.00× |
| Jackson2 | 100KB | 573.072 | 572.725 | 572.725 | 1.00× |
| Jackson3 | 1KB | 3.394 | 3.401 | 3.411 | 0.99× |
| Jackson3 | 10KB | 28.215 | 30.287 | 29.344 | 0.96× |
| Jackson3 | 100KB | 263.150 | 271.016 | 270.027 | 0.97× |
| Fastjson2 | 1KB | 3.249 | 3.119 | 3.039 | 1.07× |
| Fastjson2 | 10KB | 31.738 | 30.663 | 30.664 | 1.04× |
| Fastjson2 | 100KB | 321.789 | 305.012 | 303.550 | 1.06× |
| Gson | 1KB | 6.164 | 5.482 | 5.270 | 1.17× |
| Gson | 10KB | 50.753 | 43.205 | 42.724 | 1.19× |
| Gson | 100KB | 475.168 | 420.858 | 420.467 | 1.13× |

### Write

#### Users / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.712 | 17.977 | 5.312 | 1.26× |
| Jackson2 | 10KB | 60.001 | 179.369 | 46.345 | 1.29× |
| Jackson2 | 100KB | 587.390 | 1,718.406 | 463.038 | 1.27× |
| Jackson3 | 1KB | 11.277 | 10.884 | 3.209 | 3.51× |
| Jackson3 | 10KB | 106.619 | 108.183 | 28.580 | 3.73× |
| Jackson3 | 100KB | 1,010.150 | 1,038.622 | 276.987 | 3.65× |
| Fastjson2 | 1KB | 3.263 | 14.212 | 1.671 | 1.95× |
| Fastjson2 | 10KB | 31.031 | 146.152 | 14.887 | 2.08× |
| Fastjson2 | 100KB | 305.327 | 1,389.020 | 154.173 | 1.98× |
| Gson | 1KB | 8.543 | 14.049 | 6.125 | 1.39× |
| Gson | 10KB | 79.747 | 138.047 | 58.264 | 1.37× |
| Gson | 100KB | 831.388 | 1,336.832 | 561.910 | 1.48× |

#### Users / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.986 | 6.684 | 6.716 | 1.04× |
| Jackson2 | 10KB | 62.867 | 59.646 | 59.503 | 1.06× |
| Jackson2 | 100KB | 615.525 | 596.905 | 595.243 | 1.03× |
| Jackson3 | 1KB | 4.623 | 4.068 | 4.085 | 1.13× |
| Jackson3 | 10KB | 38.810 | 36.707 | 36.843 | 1.05× |
| Jackson3 | 100KB | 371.092 | 356.240 | 355.139 | 1.04× |
| Fastjson2 | 1KB | 3.533 | 3.449 | 3.483 | 1.01× |
| Fastjson2 | 10KB | 35.283 | 34.987 | 35.135 | 1.00× |
| Fastjson2 | 100KB | 349.913 | 346.803 | 343.922 | 1.02× |
| Gson | 1KB | 8.895 | 7.210 | 7.060 | 1.26× |
| Gson | 10KB | 85.733 | 64.345 | 64.790 | 1.32× |
| Gson | 100KB | 901.301 | 627.389 | 613.403 | 1.47× |

#### Clients / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 5.899 | 13.920 | 4.904 | 1.20× |
| Jackson2 | 10KB | 76.027 | 157.602 | 55.868 | 1.36× |
| Jackson2 | 100KB | 849.561 | 1,717.054 | 606.913 | 1.40× |
| Jackson3 | 1KB | 8.966 | 8.593 | 3.002 | 2.99× |
| Jackson3 | 10KB | 105.670 | 95.461 | 33.797 | 3.13× |
| Jackson3 | 100KB | 1,176.711 | 1,060.998 | 362.384 | 3.25× |
| Fastjson2 | 1KB | 2.984 | 10.761 | 1.853 | 1.61× |
| Fastjson2 | 10KB | 27.449 | 115.573 | 14.998 | 1.83× |
| Fastjson2 | 100KB | 270.464 | 1,233.670 | 135.693 | 1.99× |
| Gson | 1KB | 8.083 | 11.557 | 5.874 | 1.38× |
| Gson | 10KB | 82.925 | 124.027 | 60.926 | 1.36× |
| Gson | 100KB | 876.544 | 1,307.385 | 625.448 | 1.40× |

#### Clients / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.225 | 6.004 | 5.983 | 1.04× |
| Jackson2 | 10KB | 59.650 | 56.058 | 56.276 | 1.06× |
| Jackson2 | 100KB | 588.106 | 561.120 | 557.907 | 1.05× |
| Jackson3 | 1KB | 3.949 | 3.609 | 3.636 | 1.09× |
| Jackson3 | 10KB | 35.943 | 34.230 | 34.390 | 1.05× |
| Jackson3 | 100KB | 352.235 | 342.488 | 335.810 | 1.05× |
| Fastjson2 | 1KB | 3.176 | 3.105 | 3.092 | 1.03× |
| Fastjson2 | 10KB | 30.147 | 29.698 | 29.480 | 1.02× |
| Fastjson2 | 100KB | 305.685 | 298.845 | 296.199 | 1.03× |
| Gson | 1KB | 8.344 | 6.406 | 6.504 | 1.28× |
| Gson | 10KB | 80.388 | 60.294 | 60.100 | 1.34× |
| Gson | 100KB | 833.680 | 579.911 | 579.442 | 1.44× |

## Notes

- The suite validates semantic equivalence in JMH `@Setup`, outside measured method calls.
- No Native Image `gc.alloc.rate.norm` is reported; RSS from GNU `/usr/bin/time -v` is peak process RSS, not an equivalent JVM B/op metric.
- This report is a run-specific snapshot, not a hardware-independent performance baseline.

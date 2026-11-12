# Native Image Fabien Binding Full — v1.5.1-SNAPSHOT — 2026-11-11 — Serial

> Historical Native Image performance snapshot. Run [#43035710364](https://github.com/sjf4j-projects/sjf4j/actions/runs/43035710364), SHA `1e1d90d3a01cc46072c4a188e1ed1b9760755f0d`.

**288 exact JMH cases × 3 independent native processes = 864 process-level results.** Four backends; Read/Write; Users/Clients; POJO/Map; 1/10/100KB; seed 12345.

- Environment: GraalVM Community 25.5.5.0.3 / Native Image, `-O2`, no PGO; Ubuntu 24.04 Linux x86_64, AMD EPYC 7763 on one runner.
- Mode: serial; Native, Runtime and Compiled for each backend run on the same runner.
- JMH 1.37: one thread; five 1s warmup and five 1s measurement iterations, `-f 0`; three independent processes per exact case.
- Metric: µs/op (lower is better); Native / Compiled = direct third-party Binding latency / generated Binding latency.
- **Best 2 Run Mean:** for each exact case, select the two lowest JMH process scores and take their arithmetic mean. Ratios are computed before display rounding.
- [Original JMH JSON, runner metadata and process statistics](https://github.com/sjf4j-projects/sjf4j/actions/runs/43035710364/artifacts/13873357604). Artifacts expire; this file keeps all 96 aggregated case combinations.
- GitHub shared runners are exploratory. A different CPU model cannot be treated as an equivalent measurement environment.

## Latency results — Best 2 Run Mean, µs/op

### Read

#### Users / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7.762 | 26.520 | 5.953 | 1.30× |
| Jackson2 | 10KB | 66.538 | 277.598 | 47.960 | 1.39× |
| Jackson2 | 100KB | 649.338 | 2,652.008 | 481.058 | 1.35× |
| Jackson3 | 1KB | 30.008 | 25.689 | 5.100 | 5.88× |
| Jackson3 | 10KB | 291.362 | 272.256 | 44.659 | 6.52× |
| Jackson3 | 100KB | 2,793.639 | 2,589.966 | 438.529 | 6.37× |
| Fastjson2 | 1KB | 6.421 | 21.813 | 1.791 | 3.59× |
| Fastjson2 | 10KB | 89.696 | 233.881 | 14.878 | 6.03× |
| Fastjson2 | 100KB | 822.225 | 2,214.061 | 149.591 | 5.50× |
| Gson | 1KB | 8.455 | 28.498 | 7.657 | 1.10× |
| Gson | 10KB | 64.038 | 286.803 | 54.476 | 1.18× |
| Gson | 100KB | 618.298 | 2,691.207 | 537.766 | 1.15× |

#### Users / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.917 | 6.778 | 6.676 | 1.04× |
| Jackson2 | 10KB | 56.932 | 56.611 | 56.871 | 1.00× |
| Jackson2 | 100KB | 564.459 | 566.532 | 564.847 | 1.00× |
| Jackson3 | 1KB | 6.472 | 6.260 | 6.188 | 1.05× |
| Jackson3 | 10KB | 57.361 | 55.351 | 55.490 | 1.03× |
| Jackson3 | 100KB | 564.772 | 551.281 | 549.925 | 1.03× |
| Fastjson2 | 1KB | 3.066 | 3.153 | 3.081 | 1.00× |
| Fastjson2 | 10KB | 28.080 | 25.656 | 25.467 | 1.10× |
| Fastjson2 | 100KB | 277.030 | 252.515 | 250.341 | 1.11× |
| Gson | 1KB | 8.782 | 8.128 | 8.086 | 1.09× |
| Gson | 10KB | 62.032 | 59.749 | 59.939 | 1.03× |
| Gson | 100KB | 616.906 | 593.915 | 592.958 | 1.04× |

#### Clients / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 8.933 | 20.756 | 6.686 | 1.34× |
| Jackson2 | 10KB | 155.266 | 215.834 | 58.707 | 2.64× |
| Jackson2 | 100KB | 1,944.627 | 2,289.963 | 572.212 | 3.40× |
| Jackson3 | 1KB | 25.756 | 19.484 | 5.659 | 4.55× |
| Jackson3 | 10KB | 363.926 | 209.386 | 52.766 | 6.90× |
| Jackson3 | 100KB | 4,280.246 | 2,246.084 | 524.021 | 8.17× |
| Fastjson2 | 1KB | 4.595 | 15.420 | 2.105 | 2.18× |
| Fastjson2 | 10KB | 60.606 | 168.347 | 21.149 | 2.87× |
| Fastjson2 | 100KB | 711.462 | 1,832.880 | 210.829 | 3.37× |
| Gson | 1KB | 9.921 | 22.863 | 8.548 | 1.16× |
| Gson | 10KB | 149.933 | 234.861 | 72.249 | 2.08× |
| Gson | 100KB | 1,837.837 | 2,506.728 | 718.641 | 2.56× |

#### Clients / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7.561 | 7.397 | 7.337 | 1.03× |
| Jackson2 | 10KB | 60.325 | 60.490 | 60.363 | 1.00× |
| Jackson2 | 100KB | 568.381 | 572.199 | 568.475 | 1.00× |
| Jackson3 | 1KB | 6.350 | 6.224 | 6.156 | 1.03× |
| Jackson3 | 10KB | 55.148 | 54.111 | 54.590 | 1.01× |
| Jackson3 | 100KB | 539.941 | 525.578 | 527.394 | 1.02× |
| Fastjson2 | 1KB | 3.224 | 3.059 | 3.001 | 1.07× |
| Fastjson2 | 10KB | 31.622 | 30.600 | 30.478 | 1.04× |
| Fastjson2 | 100KB | 323.243 | 303.357 | 303.096 | 1.07× |
| Gson | 1KB | 10.612 | 9.480 | 9.451 | 1.12× |
| Gson | 10KB | 87.683 | 77.375 | 77.306 | 1.13× |
| Gson | 100KB | 856.038 | 748.508 | 743.959 | 1.15× |

### Write

#### Users / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.662 | 18.165 | 5.275 | 1.26× |
| Jackson2 | 10KB | 58.262 | 181.264 | 45.996 | 1.27× |
| Jackson2 | 100KB | 574.911 | 1,731.837 | 458.815 | 1.25× |
| Jackson3 | 1KB | 18.505 | 18.486 | 5.469 | 3.38× |
| Jackson3 | 10KB | 179.317 | 183.322 | 47.325 | 3.79× |
| Jackson3 | 100KB | 1,715.943 | 1,750.203 | 470.719 | 3.65× |
| Fastjson2 | 1KB | 3.321 | 14.770 | 1.658 | 2.00× |
| Fastjson2 | 10KB | 31.472 | 154.437 | 14.833 | 2.12× |
| Fastjson2 | 100KB | 309.519 | 1,464.494 | 152.915 | 2.02× |
| Gson | 1KB | 13.534 | 24.246 | 10.770 | 1.26× |
| Gson | 10KB | 122.366 | 240.850 | 97.142 | 1.26× |
| Gson | 100KB | 1,299.802 | 2,300.490 | 965.934 | 1.35× |

#### Users / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 7.131 | 6.654 | 6.700 | 1.06× |
| Jackson2 | 10KB | 63.645 | 58.849 | 58.557 | 1.09× |
| Jackson2 | 100KB | 618.955 | 584.580 | 590.460 | 1.05× |
| Jackson3 | 1KB | 7.133 | 6.815 | 6.761 | 1.05× |
| Jackson3 | 10KB | 61.207 | 59.205 | 59.242 | 1.03× |
| Jackson3 | 100KB | 614.174 | 588.996 | 584.239 | 1.05× |
| Fastjson2 | 1KB | 3.482 | 3.459 | 3.460 | 1.01× |
| Fastjson2 | 10KB | 34.808 | 35.213 | 35.091 | 0.99× |
| Fastjson2 | 100KB | 344.150 | 347.369 | 345.895 | 0.99× |
| Gson | 1KB | 14.913 | 11.654 | 11.605 | 1.29× |
| Gson | 10KB | 139.678 | 107.751 | 107.390 | 1.30× |
| Gson | 100KB | 1,453.316 | 1,065.418 | 1,067.398 | 1.36× |

#### Clients / POJO

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 5.921 | 14.139 | 4.905 | 1.21× |
| Jackson2 | 10KB | 75.447 | 161.605 | 55.528 | 1.36× |
| Jackson2 | 100KB | 839.368 | 1,757.619 | 602.469 | 1.39× |
| Jackson3 | 1KB | 14.794 | 14.419 | 5.157 | 2.87× |
| Jackson3 | 10KB | 177.015 | 162.379 | 56.928 | 3.11× |
| Jackson3 | 100KB | 1,989.092 | 1,771.007 | 610.242 | 3.26× |
| Fastjson2 | 1KB | 3.059 | 11.012 | 1.867 | 1.64× |
| Fastjson2 | 10KB | 27.738 | 119.129 | 15.047 | 1.84× |
| Fastjson2 | 100KB | 271.970 | 1,273.153 | 138.999 | 1.96× |
| Gson | 1KB | 12.995 | 19.857 | 10.230 | 1.27× |
| Gson | 10KB | 132.436 | 217.101 | 105.240 | 1.26× |
| Gson | 100KB | 1,432.923 | 2,311.289 | 1,091.939 | 1.31× |

#### Clients / Map

| Backend | Size | Native | Runtime | Compiled | Native / Compiled |
| --- | ---: | ---: | ---: | ---: | ---: |
| Jackson2 | 1KB | 6.400 | 6.026 | 6.031 | 1.06× |
| Jackson2 | 10KB | 59.814 | 55.848 | 55.873 | 1.07× |
| Jackson2 | 100KB | 588.997 | 555.384 | 556.309 | 1.06× |
| Jackson3 | 1KB | 6.355 | 6.048 | 6.094 | 1.04× |
| Jackson3 | 10KB | 58.123 | 56.599 | 56.288 | 1.03× |
| Jackson3 | 100KB | 587.194 | 557.641 | 558.606 | 1.05× |
| Fastjson2 | 1KB | 3.166 | 3.110 | 3.133 | 1.01× |
| Fastjson2 | 10KB | 29.895 | 30.333 | 30.224 | 0.99× |
| Fastjson2 | 100KB | 300.834 | 305.948 | 307.961 | 0.98× |
| Gson | 1KB | 14.335 | 10.828 | 10.791 | 1.33× |
| Gson | 10KB | 133.300 | 101.475 | 101.687 | 1.31× |
| Gson | 100KB | 1,365.903 | 1,004.108 | 996.970 | 1.37× |

## Notes

- The suite validates semantic equivalence in JMH `@Setup`, outside measured method calls.
- No Native Image `gc.alloc.rate.norm` is reported; RSS from GNU `/usr/bin/time -v` is peak process RSS, not an equivalent JVM B/op metric.
- This report is a run-specific snapshot, not a hardware-independent performance baseline.

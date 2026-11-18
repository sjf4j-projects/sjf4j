# Experimental Regex Comparison — SJF4J vs Jayway

**Exploratory result; not part of the published Path performance baseline.**

- **Benchmark source:** [RegexFunctionComparisonBenchmark](../../../sjf4j-testbench/src/jmh/java/org/sjf4j/testbench/path/RegexFunctionComparisonBenchmark.java)
- **Source commit:** [2115f16479cd](https://github.com/sjf4j-projects/sjf4j/commit/2115f16479cd5eb247026c394d968836d5828b43)
- **GitHub Actions run:** [#44209026594](https://github.com/sjf4j-projects/sjf4j/actions/runs/44209026594)
- **Raw JMH JSON/log/runner info:** [artifact](https://github.com/sjf4j-projects/sjf4j/actions/runs/44209026594/artifacts/14384967421)
- **Environment:** GitHub-hosted Ubuntu 24.04, Temurin JDK 25.0.5, JMH 1.37, Jayway JsonPath 3.0.0
- **Methodology:** AverageTime, 1 thread, 2 forks, 3 × 300ms warmup and 4 × 400ms measurement per fork, GC allocation profiler. Results are means of the two fork means.

## Workload

Each operation evaluates a precompiled JSONPath over prebuilt Map/List data with 16, 128 or 1024 rows, returning the matching names. Setup validates that all three approaches produce identical results. Exactly 25% of names fully match `user[0-9]{6}` and 50% contain that expression. Other values are negative cases.

Jayway does not expose the RFC 9535 `match()` and `search()` functions. Jayway's `=~` uses `Matcher.matches()`, while SJF4J's `=~` uses `Matcher.find()`. These equivalents return the same results for the benchmark's newline-free values:

| Goal | SJF4J function | Jayway regex equivalent | SJF4J precompiled operator control |
| --- | --- | --- | --- |
| Full match | `match(@.name, 'user[0-9]{6}')` | `@.name =~ /user[0-9]{6}/` | `@.name =~ /^user[0-9]{6}$/` |
| Search | `search(@.name, 'user[0-9]{6}')` | `@.name =~ /.*user[0-9]{6}.*/` | `@.name =~ /user[0-9]{6}/` |

SJF4J functions compile a regex for each evaluated candidate. Jayway and SJF4J regex operators reuse patterns compiled with their paths. This is a practical workload comparison, not identical compiled-regex strategies.

## Query latency — ns/op (lower is better)

| Mode | Rows | SJF4J function | SJF4J operator | Jayway operator | Jayway / SJF4J function |
| --- | ---: | ---: | ---: | ---: | ---: |
| match | 16 | 2,776.9 | 626.8 | 2,012.1 | 0.72× |
| match | 128 | 22,851.0 | 5,043.8 | 15,846.1 | 0.69× |
| match | 1024 | 186,610.5 | 42,171.8 | 126,390.7 | 0.68× |
| search | 16 | 3,000.9 | 961.9 | 3,635.9 | 1.21× |
| search | 128 | 23,613.9 | 7,830.5 | 30,235.1 | 1.28× |
| search | 1024 | 195,719.6 | 63,150.0 | 229,711.7 | 1.17× |

A Jayway / SJF4J ratio above 1 means SJF4J function is faster; below 1 means Jayway is faster.

## Allocation — B/op

| Mode | Rows | SJF4J function | SJF4J operator | Jayway operator |
| --- | ---: | ---: | ---: | ---: |
| match | 16 | 25,128 | 2,344 | 11,408 |
| match | 128 | 205,313 | 17,912 | 94,256 |
| match | 1024 | 1,643,039 | 143,897 | 791,690 |
| search | 16 | 25,768 | 2,344 | 13,456 |
| search | 128 | 205,840 | 18,441 | 106,577 |
| search | 1024 | 1,645,256 | 146,113 | 898,524 |

At 1024 rows, SJF4J function queries take 4.43× (match) / 3.10× (search) the time of SJF4J precompiled regex operator queries, with approximately 11× the allocation. Repeated `Pattern.compile()` is the expected leading cost. Compared with Jayway's equivalent queries, SJF4J `match()` is 1.48× slower and `search()` 1.17× faster at 1024 rows.

## Limitations and reproduction

These are exploratory GitHub-hosted runner results. The whole-path benchmark includes filter traversal and result collection; it does not isolate standalone regular-expression throughput. The Jayway search form using `.*...*` is equivalent for these fixture strings but not generally identical to substring searching with line breaks.

On JDK 25:

`@bash
./gradlew :sjf4j-testbench:jmhJar
JAR="$(find sjf4j-testbench/build/libs -maxdepth 1 -name '*-jmh.jar' | sort | head -n 1)"
java -jar "$JAR" \
  '^org\.sjf4j\.testbench\.path\.RegexFunctionComparisonBenchmark\.(match|search)_(sjf4j_function|sjf4j_operator|jayway_regex)$' \
  -t 1 -f 2 -wi 3 -i 4 -w 300ms -r 400ms -prof gc
`@

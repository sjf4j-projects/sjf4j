---
title: We Benchmarked Java JSONPath Engines. Here Are the Numbers.
published: false
tags: java,json,benchmark,performance
description: A JMH comparison of SJF4J JSONPath and Jayway JsonPath, plus a look at POJO, JOJO, and Map/List traversal inside SJF4J.
---

If you use JSONPath on the JVM, Jayway JsonPath is usually the default reference point.

So we built a JMH benchmark suite to answer a narrow question:

How fast is `SJF4J JSONPath` compared with `Jayway JsonPath` on the same workloads?

We also looked at a second question that matters specifically for SJF4J:

How does JSONPath performance change across `Map/List`, `JOJO`, and plain `POJO` object graphs?

The short version:

- on a shared parsed tree, `SJF4J` is clearly faster than Jayway
- on `Map/List` object graphs, the gap gets much larger
- inside SJF4J itself, `Map/List` is fastest, `JOJO` is close behind, and plain `POJO` is slower

## What We Measured

The benchmarks use JMH with:

- `5 x 300ms` warmup
- `8 x 300ms` measurement
- `2` forks
- `1` thread

Expressions were limited to overlapping features both engines support cleanly:

- `$.store.book[1].price`
- `$.store.bicycle.color`
- `$.store.book[*].author`
- `$..price`
- `$.store.book[?(@.price > 10)].title`
- `$.store.book[0,2].title`

We intentionally removed `parse_and_query` from the main comparison.

Why? Because the default parse stacks are not equivalent:

- Jayway defaults to `json-smart`
- SJF4J, in this benchmark environment, defaults to Jackson

That is useful for measuring full application stacks, but not ideal for a fair JSONPath engine comparison. The article below therefore focuses on compile and query workloads where the data model is controlled.

## Headline Results

Geometric mean, lower is better:

| Benchmark group | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `compile` | `97.384 ns/op` | `124.862 ns/op` | SJF4J `1.28x` faster |
| `query_definite` | `99.618 ns/op` | `236.808 ns/op` | SJF4J `2.38x` faster |
| `query_indefinite` | `656.191 ns/op` | `1293.942 ns/op` | SJF4J `1.97x` faster |
| `query_map_list_definite` | `31.297 ns/op` | `199.548 ns/op` | SJF4J `6.38x` faster |
| `query_map_list_indefinite` | `279.825 ns/op` | `1300.625 ns/op` | SJF4J `4.65x` faster |

The most important numbers are the query groups.

That is where JSONPath engines spend their time in production.

## 1) Query on Shared Jackson `JsonNode`

This is the cleanest engine-to-engine comparison.

Both libraries query the same Jackson `JsonNode` tree.

### Definite paths

| Expression | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `$.store.book[1].price` | `110.673 ns/op` | `260.086 ns/op` | SJF4J `2.35x` faster |
| `$.store.bicycle.color` | `89.666 ns/op` | `215.613 ns/op` | SJF4J `2.40x` faster |

### Indefinite paths

| Expression | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `$.store.book[*].author` | `342.135 ns/op` | `910.107 ns/op` | SJF4J `2.66x` faster |
| `$..price` | `1736.489 ns/op` | `3877.442 ns/op` | SJF4J `2.23x` faster |
| `$.store.book[?(@.price > 10)].title` | `1291.514 ns/op` | `1660.363 ns/op` | SJF4J `1.29x` faster |
| `$.store.book[0,2].title` | `241.630 ns/op` | `478.430 ns/op` | SJF4J `1.98x` faster |

On a shared AST, `SJF4J JSONPath` is consistently faster than Jayway.

## 2) Query on `Map/List` Object Graphs

Jayway supports `Map/List` style ASTs through its default provider, so we added a dedicated comparison there too.

### Definite paths on `Map/List`

| Expression | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `$.store.book[1].price` | `37.384 ns/op` | `280.279 ns/op` | SJF4J `7.50x` faster |
| `$.store.bicycle.color` | `26.201 ns/op` | `142.070 ns/op` | SJF4J `5.42x` faster |

### Indefinite paths on `Map/List`

| Expression | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `$.store.book[*].author` | `135.085 ns/op` | `1044.647 ns/op` | SJF4J `7.73x` faster |
| `$..price` | `543.237 ns/op` | `2495.255 ns/op` | SJF4J `4.59x` faster |
| `$.store.book[?(@.price > 10)].title` | `822.982 ns/op` | `1836.256 ns/op` | SJF4J `2.23x` faster |
| `$.store.book[0,2].title` | `101.521 ns/op` | `597.848 ns/op` | SJF4J `5.89x` faster |

If your application already works on plain Java object graphs, this is the strongest external result in the whole benchmark.

## 3) Path Compilation

Compilation matters less than query execution, but it still shows parser overhead and cacheability costs.

| Expression | SJF4J | Jayway | Result |
|---|---:|---:|---:|
| `$.store.book[1].price` | `89.114 ns/op` | `137.203 ns/op` | SJF4J `1.54x` faster |
| `$.store.bicycle.color` | `61.480 ns/op` | `77.323 ns/op` | SJF4J `1.26x` faster |
| `$.store.book[*].author` | `66.924 ns/op` | `90.109 ns/op` | SJF4J `1.35x` faster |
| `$..price` | `31.744 ns/op` | `39.567 ns/op` | SJF4J `1.25x` faster |
| `$.store.book[?(@.price > 10)].title` | `486.671 ns/op` | `453.657 ns/op` | Jayway `1.07x` faster |
| `$.store.book[0,2].title` | `150.582 ns/op` | `220.836 ns/op` | SJF4J `1.47x` faster |

`SJF4J` wins the compile group overall, and wins `5` of the `6` expressions here.

## 4) POJO vs JOJO vs Map/List Inside SJF4J

Jayway does not offer the same native-object traversal model that SJF4J does, so this part is an internal SJF4J comparison.

It answers a practical design question:

If you want JSONPath over Java object graphs, what shape is fastest?

### What these three shapes mean

- `Map/List`: generic object graph, minimal access overhead
- `POJO`: regular typed Java class
- `JOJO`: SJF4J's typed JSON object model, extending `JsonObject`

### Geometric mean

| Benchmark group | Map/List | JOJO | POJO |
|---|---:|---:|---:|
| `definite` | `31.149 ns/op` | `42.849 ns/op` | `94.216 ns/op` |
| `indefinite` | `270.137 ns/op` | `372.569 ns/op` | `553.901 ns/op` |

### Representative results

| Expression | Map/List | JOJO | POJO |
|---|---:|---:|---:|
| `$.store.book[1].price` | `37.120 ns/op` | `46.371 ns/op` | `102.063 ns/op` |
| `$.store.bicycle.color` | `26.139 ns/op` | `39.594 ns/op` | `86.972 ns/op` |
| `$.store.book[*].author` | `131.063 ns/op` | `195.971 ns/op` | `373.986 ns/op` |
| `$..price` | `495.953 ns/op` | `914.353 ns/op` | `955.331 ns/op` |
| `$.store.book[?(@.price > 10)].title` | `807.028 ns/op` | `875.524 ns/op` | `1099.642 ns/op` |

This is one of the more interesting SJF4J-specific findings.

`Map/List` is still the fastest representation, which is what you would expect from direct structural access.

But `JOJO` is much closer to `Map/List` than plain `POJO` is.

That matters because `JOJO` keeps a typed programming model while staying much more JSON-native than ordinary POJOs.

In other words:

- if you want maximum traversal speed, use `Map/List`
- if you want typed models without giving up too much JSONPath speed, `JOJO` is the better fit

## What To Take Away

This benchmark does not claim that one library is faster in every possible environment.

It does show a few concrete things.

- `SJF4J JSONPath` is faster than Jayway on shared-tree query workloads
- `SJF4J` is much faster on `Map/List` object graphs
- `SJF4J` is competitive to faster on compilation overall
- inside SJF4J, `JOJO` offers a strong middle ground between raw `Map/List` speed and plain `POJO` ergonomics

That last point is easy to miss if you only think about JSONPath as a string-over-AST feature.

For SJF4J, JSONPath is also a way to navigate native Java object graphs under consistent JSON semantics. The object model you choose has real performance consequences.

## Benchmark Sources

- benchmark code: `sjf4j/src/jmh/java/org/sjf4j/JsonPathCompareBenchmark.java`
- engine comparison results: `sjf4j/build/reports/jsonpath-compare-final-native.csv`
- object model comparison results: `sjf4j/build/reports/jsonpath-pojo-jojo-maplist.csv`

## Bottom Line

If your baseline is Jayway JsonPath, the numbers here are straightforward:

`SJF4J JSONPath` is faster on the query workloads that matter most.

And if you are already working with native Java object graphs, SJF4J's `JOJO` model gives you a notably better JSONPath performance profile than plain POJOs, without dropping down to untyped maps everywhere.

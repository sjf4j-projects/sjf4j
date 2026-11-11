# Compiled Mapping: Raw Value Conversion

`@MappingOptions(raws = ...)` selects how an automatically resolved
**runtime-unknown source value** is converted to a **statically typed target**
inside a `@CompiledMapper` method.

This setting is per mapper method and defaults to `RawPolicy.STRICT`, retaining
the historical behavior of SJF4J 1.5.x.

| Policy | Semantics | Examples |
| --- | --- | --- |
| `CAST` | Java cast/unboxing; **no** OBNT adaptation or recursive copy at this conversion point | `(String) raw`, `(Map<String,Item>)(Object)raw` |
| `STRICT` | Strict OBNT adaptation through `NodeMapper.toXxx` / `to` | `Integer → Long` allowed; `"42" → Integer` rejected |
| `LENIENT` | Lenient OBNT scalar adaptation through `NodeMapper.asXxx` / `as` | `"42" → Integer`, `"yes" → Boolean` |

Example:

~~~java
@CompiledMapper
interface UserMapper {
    @MappingOptions(raws = RawPolicy.CAST)
    User cast(JsonObject source);

    @MappingOptions(raws = RawPolicy.STRICT)
    User strict(JsonObject source);

    @MappingOptions(raws = RawPolicy.LENIENT)
    User lenient(JsonObject source);
}
~~~

## Exact trigger and precedence

`CAST`, `STRICT`, and `LENIENT` are considered when a source value's
**compile-time NodeKind is `COMPILE_TIME_UNKNOWN`** (for example, an `Object`
field, an element of `Map<String,Object>`, or an external JSON representation).
The strategy does not apply to statically known `String`, `int`, `Map` or
`List` source values.

Direct assignments and explicit mapping methods are selected before this policy.
Known-type scalar conversions and compiled structural conversion are unaffected.

- `Object → Map<String,Item>` with `CAST`: cast the whole reference,
  preserving identity; **no generic value validation** occurs.
- `Map<String,Object> → Map<String,Item>` with `CAST`: the outer typed
  Map is traversed and a new Map is created; each `Object → Item` value is
  cast independently.
- `Object → User` with `CAST`: cast the entire object to `User`; it
  cannot transform a Map into a POJO.
- With `STRICT` or `LENIENT`, compiled POJO and container mapping remains
  structural. The lenient rule applies to each unknown scalar leaf that the
  compiler resolves; `NodeMapper.as(raw, User.class)` by itself does not
  recursively make POJO properties lenient.

Because of generic erasure, `CAST` cannot verify the type arguments of
`Map<K,V>` or `List<T>`. The generator places
`@SuppressWarnings("unchecked")` on the individual generated local when
necessary to support projects using `-Xlint:unchecked -Werror`.

## Focused MapStruct comparison

`MappingRawPolicyBenchmark` compares only **two input representations** and
**four mapping methods**. Each operation reads `sku`, `name`, and `count`
from an already prepared Map or JsonObject and creates the same record.

MapStruct uses explicit Java cast expressions for `sku` and `name`, as
required for non-JavaBean map sources. Its input semantics match the compiled
`CAST` mode, not `STRICT` or `LENIENT`.

Run with JDK 25:

~~~bash
bash scripts/benchmark-mapping-raw.sh smoke
bash scripts/benchmark-mapping-raw.sh quick
bash scripts/benchmark-mapping-raw.sh full
~~~

The dedicated `Mapping Raw Policy Benchmark (JDK 25)` Action runs Smoke
for pull requests and Full only on manual dispatch. It accepts an optional
exact CPU-model guard. Full results provide independent **Best 2 Mean** of
three forks per method, original raw JMH samples, and `gc.alloc.rate.norm`
allocation measurements.

A GitHub-hosted runner is **not** a controlled hardware baseline. Run all
compared methods on one runner and compare different Full runs only after
checking CPU model, JDK build, and environmental noise. Benchmarking the
native scalar input used here does **not** measure the additional cost of
actual lenient coercion from strings or booleans.

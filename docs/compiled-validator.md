# CompiledValidator

`@CompiledValidator` generates validation methods for statically selected JSON
Schemas. This document defines the public method contract and tracks **direct
code-generation coverage**, separately from JSON Schema support provided by
the runtime `SchemaPlan`.

## Usage

```java
@CompiledValidator(nullAsMissing = false)
public interface UserValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    boolean isValid(User user);

    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    boolean isValid(User user, boolean strictFormat);

    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    void requireValid(User user);

    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    ValidationResult validate(User user, boolean strictFormat);
}

UserValidator validator = CompiledInstances.create(UserValidator.class);
```

The return type, not the method name, selects the validation operation:

| Return type | Equivalent SchemaPlan semantics | Failure behavior |
| --- | --- | --- |
| `boolean` | `isValid(value, strictFormat)` | Fail fast, return false; do not create diagnostics |
| `void` | `requireValid(value, strictFormat)` | Fail fast; throw `ValidationException` with diagnostics |
| `ValidationResult` | `validate(value, false, strictFormat)` | Collect **all** errors, return the result |

Each abstract method accepts **one fully resolved value parameter**, optionally
followed by a **primitive** `boolean strictFormat` parameter. The default when
omitted is `true`. The name of the second parameter is unrestricted. It controls
JSON Schema `format` assertions only; other validation keywords remain active.
`Boolean`, other extra parameters, unsupported return types, and type variables
are rejected at annotation processing time.

**Null POJO contract:** `null` input is skipped (boolean `true`, void
returns normally, `ValidationResult.SUCCESS`). This is deliberately distinct
from directly validating the JSON value `null` through `SchemaPlan`.

`@ValidJsonSchema` specifies the schema, not validation execution options.
Selection precedence is method annotation, first-parameter annotation, and
annotations on the value type and its ancestors (parent first). An inline
schema takes precedence over a URI reference; otherwise the type's
conventional schema filename is searched. Compiled and fallback execution must
produce equivalent validation results for supported input representations.

## Null-valued object properties

By default (`@CompiledValidator(nullAsMissing = false)`), the compiled
validator follows JSON Schema: an object property with value `null` is
**present**, so `required` succeeds, while its property schema can reject
the JSON null value.

For Java typed objects that cannot distinguish unset from explicitly null,
opt in to `@CompiledValidator(nullAsMissing = true)`:
- a null object member is treated as missing; `required` then fails for
  that member, and a non-required property schema is skipped;
- null elements **inside arrays remain present** and are validated as JSON null;
- the rule also applies to nested object members, including validation through
  a runtime SchemaPlan fallback, using an explicit object projection on that
  non-default path;
- this is a Java object convention, **not** a change to JSON Schema semantics.

## Compilation and fallback

```java
@CompiledValidator(fallback = false)
public interface FastValidator {
    boolean isValid(MyPojo value);
}
```

The default is `fallback = true`, which allows the generator to use a
`SchemaPlan` for unsupported keywords or a remaining property subtree. With
`fallback = false`, **every check for every method must be emitted directly**;
otherwise annotation processing fails. It never silently generates a
`SchemaPlan` call. The restriction applies to both return types and keyword
combinations, not only to isolated keywords.

The generated implementation supports
`CompiledInstances.create(Validator.class)` and the existing
`CompiledInstances.create(Validator.class, RuntimeContext)` constructor
protocol. Current validation execution does **not** read options from
`RuntimeContext`; the optional per-call `strictFormat` argument is independent
of it. This retains a compatible factory entry point without inventing
configuration before it is needed.

## JSON Schema types and Java / Nodes dispatch

The generator uses the **declared Java property or collection-element type**
whenever that type proves the JSON shape. In that case it emits ordinary Java
operations directly, not a redundant series of `instanceof` tests. Only
`Object`, a dynamically accessed object member, or another unresolvable value
shape needs runtime JSON-type inspection and strict Nodes conversion.

| JSON Schema `type` | Statically known Java representation | Unknown / dynamic value via Nodes | Current direct compilation |
| --- | --- | --- | --- |
| `string` | `String` as-is; `Character` / `char` with `String.valueOf`; `Enum` with `name()` | Check `JsonType.rawOf(value.getClass()) == STRING`, then `Nodes.toString(value)` | Yes, including `minLength`/`maxLength`/string `enum` |
| `number` | Primitive/boxed numeric type, `BigDecimal`, `BigInteger`: direct `Numbers.compare` | Check numeric JSON type, then `Nodes.toNumber(value)` | Yes, including numeric bounds |
| `integer` | Same numeric types, plus `Numbers.isSemanticInteger` | Strict `Nodes.toNumber` + `Numbers.isSemanticInteger`, **not** coercion with `Nodes.toInt` | Yes |
| `boolean` | `boolean` / `Boolean` | Strict `Nodes.toBoolean(value)` | Runtime fallback for keyword compilation |
| `null` | Java `null` or JSON null representation | JSON-null classification; no numeric/string coercion | Runtime fallback for keyword compilation |
| `object` | POJO declared getters or Map/JsonObject/JOJO member operations | `Nodes.getInObject`, `Nodes.keySetInObject`; no container copy | Root object directly; nested objects conditional/fallback |
| `array` | `List`/`Set`/`Collection` size + typed for-each, or Java-array length + for-each | JSON array shape, `Nodes.sizeInArray` and `Nodes.iteratorInArray` | Yes for supported `items` and bounds |

`Nodes.toXxx` are **strict**, not the lenient `Nodes.asXxx` APIs: a
JSON string containing `"42"` must not silently satisfy `type: number`.
For unknown values, the generator checks the JSON representation type
**before** calling `Nodes.toString` or `Nodes.toNumber`, so an incompatible
value returns `false` rather than causing a conversion exception. Runtime
value-codec representations can be recognized through
`JsonType.rawOf(value.getClass())`.

For example, a statically declared `String getText()` yields a direct path:

```java
String text = value.getText();
if (text == null) return false;
int length = text.codePointCount(0, text.length());
if (length < 2) return false;
```

An `Object getText()` or a dynamic Map value uses a representation check
followed by `Nodes.toString`, without materializing an intermediate
`JsonObject` or `JsonArray`. When the compiler cannot prove the type or the
schema is unsupported, it still emits a localized `SchemaPlan` fallback.

This mapping describes the intended shape dispatch; **it does not claim**
every combination of the JSON Schema `type` keyword has a direct compiler
implementation yet (notably `boolean`, `null`, and nested `object`).

## Direct keyword coverage

**Status meaning:** Direct = emitted Java checks without a runtime plan;
Conditional = a known Java representation/root shape and/or localized
`SchemaPlan` fallback; No = use `SchemaPlan` for that constraint. This
matrix covers the production `org.sjf4j.processor.schema` generator, not the retired
incubator prototype.

The direct POJO path requires a **public final POJO with public readable
properties** (public getters without checked exceptions, or public fields).
The root is also supported for **Map, JsonObject and JOJO** representations:
dynamic keys are checked at runtime, including `required` and
`additionalProperties:false`. When a dynamic property's value type
cannot be determined statically, only that property's validation falls back.
The root may contain `type:object`, `properties`, `required`,
`additionalProperties` (boolean), and non-applicative metadata including
`$schema`, `title`, `definitions`, and `$defs`. For
`additionalProperties:false`, all readable Java properties must appear in
the schema's declared property set; otherwise the complete root falls back.
Required keys refer to readable POJO properties: **the value may be null**
without violating `required`, although its property schema may reject null.

| Keyword / feature | Direct code generation | Runtime SchemaPlan | Creek status |
| --- | --- | --- | --- |
| Boolean root schemas (`true`, `false`) | Yes | Yes | Supported |
| `type:object`, `properties` | Conditional: POJO direct fields; Map/JsonObject/JOJO dynamic keys | Yes | Phase 1 |
| `required` | POJO presence; Map/JOJO dynamic key presence; opt-in null-as-missing | Yes | Phase 1 |
| `additionalProperties:false` | Static POJO or runtime key scan for Map/JsonObject/JOJO | Yes | Phase 1 |
| `type:string` | Native `String`, `Character`, and enum fields/items | Yes | Phase 1 |
| `minLength`, `maxLength` | Direct string Unicode code-point length | Yes | Phase 1 |
| `enum` | Direct for string-only enumeration of native strings/enums | Yes | Phase 1 |
| `const` | No | Yes | Later |
| `type:number`, `type:integer` | Native numeric Java field types | Yes | Phase 1 |
| `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum` | Direct numeric comparisons | Yes | Phase 1 |
| `type:array` | Direct for declared `List`/`Set`/`Collection` | Yes | Phase 1 |
| `minItems`, `maxItems` | Direct Collection size checks | Yes | Phase 1 |
| `items` | Recursive keyword checks for typed nested collections; unsupported item subtrees use a cached plan | Yes | Phase 1 mixed |
| `format` | No | Yes, honors `strictFormat` | Later |
| `$ref`, `definitions`, `$defs` | Pure reference to a compile-time-resolvable JSON Pointer target; finite reference chains only; unsupported cycles/anchors fallback | Yes | Partial |
| `oneOf` | No (localized item `SchemaPlan` for Creek) | Yes | Next phase |
| `anyOf`, `allOf`, `not` | No | Yes | Later |
| `if`, `then`, `else` | No | Yes | Later |
| `unevaluatedProperties`, `unevaluatedItems` | No | Yes | Later |
| Root metadata (`$schema`, `title`, etc.) | Accepted when non-applicative | Yes | Phase 1 |

**Conformance boundary:** direct value checks are selected only for known
native Java value shapes; custom `@NodeValue`/codec types remain on the full
runtime path. Unsupported properties fall back as whole subtrees, not partial
predicates. Array `items` are traversed recursively: a supported item's
constraints compile directly; an unsupported subtree uses a cached item plan.
Local and classpath/file resource JSON Pointer `$ref` targets are resolved
against the compile-time resource snapshot, without runtime fetches. A
`@ValidJsonSchema(ref = "document.json#/$defs/name")` selection can itself
enter direct compilation when its target schema is supported. Named/dynamic anchors, reference
cycles, or sibling assertions that cannot be proven safe fall back.
`fallback=false` rejects **any** required subplan at compilation time.
Support is about the listed combinations, not arbitrary JSON Schema
keyword combinations.

The `sjf4j-testbench` module contains `CompiledValidatorKeywordTest` and
`CompiledValidatorTypeDispatchTest` (typed Java paths versus dynamic Nodes
conversions). Processor source-level assertions reject redundant type tests
in known `String`/`Number` paths.

The `sjf4j-testbench` module contains `CompiledValidatorKeywordTest`:
independent positive and negative cases for `minLength`, `maxLength`,
`minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`,
string enum, integer bounds, nested array `items` and sizes,
`required` and `additionalProperties` across POJO/Map/JsonObject/JOJO,
null-as-missing modes, and intra-/cross-resource `$ref` compilation. Generated-code assertions remain in
`CompiledValidatorContractProcessorTest`.

## Processor architecture and regression checks

The annotation processor is split into **Analyze → Compile → Emit** stages,
without a keyword plugin framework:

- `SchemaGenerator` handles the validator interface and generated-class lifecycle.
  `SchemaMethodGenerator` resolves method contracts and schema annotations.
- `SchemaResourceResolver` supplies compile-time documents and fragment selections.
  `SchemaCompiler` dispatches to `ObjectCompiler`, `ScalarCompiler`, and
  `ArrayCompiler`, producing a recursive `SchemaCheck` for supported constraints.
  The compiler **does not** assemble Java source statements.
- `SchemaValue` classifies declared Java value shapes. `SchemaEmitter` generates
  fail-fast predicates from the semantic checks, using native Java operations
  when a type is known and strict Nodes conversions for dynamic values.
- `SchemaFallbackEmitter` owns per-interface resource deduplication, root-plan
  usage, localized URI-based subplan references, and emitted lazy `SchemaPlan`
  helper classes. Reference names are assigned only when emitting, not inferred
  by parsing generated strings.

A mixed tree preserves a direct root and supported child checks while delegating
an unsupported array item or property subtree to a local plan. If a keyword's
evaluation semantics require state from other keywords (for example,
`unevaluatedProperties`), the compiler conservatively falls back at the
relevant scope rather than combining semantically incompatible checks.
`fallback=false` rejects the entire method when any required subtree remains
unsupported.

Regression suites:
`CompiledValidatorArchitectureTest` compiles representative interfaces with
javac to inspect emitted source for a single shared fallback plan across
methods, one shared item subplan, distinct bindings, zero fallback for
fully compiled methods, and rejection of unsupported mixed trees.
`CompiledValidatorContractProcessorTest` checks type-specialized code emission
and the partial Creek fallback boundary. The testbench Creek fixtures compare
`SchemaPlan` against compiled validation for valid and invalid inputs.
The JMH Creek benchmark also validates negative POJO cases before measurement.

To compile and test:

```bash
./gradlew :sjf4j-processor:test :sjf4j-testbench:test :sjf4j-testbench:jmhJar
```

For an actual **three-fork** Creek comparison under identical non-strict format
settings (the published JMH method annotations provide the warmup/measurement
defaults):

```bash
JMH_JAR=$(find sjf4j-testbench/build/libs -name '*jmh*.jar' -print -quit)
java -cp "$JMH_JAR" org.openjdk.jmh.Main \
  'org.sjf4j.testbench.schema.CreekSchemaBenchmark\\.(schema_is_valid_pojo|compiled_schema_is_valid_pojo_non_strict)

The unchanged Draft 2020-12 Creek schema is at
`sjf4j-testbench/src/jmh/resources/schema/creek-2020-12.json`; its
benchmark is `CreekSchemaBenchmark`. It includes `required`,
`additionalProperties:false`, decimal comparison, an enum, lists,
`minLength`, polymorphic `oneOf` with `$ref`, and UUID format.

1. **Phase 1 (implemented):** direct root, `required`, closed
   `additionalProperties`, native numeric/enum/string checks and recursive
   array item traversal, including finite local `$ref` expansion.
   The original Creek `oneOf` remains a localized cached item
   `SchemaPlan`; this is **mixed compiled/runtime**, not full compilation.
2. **Next:** compile the two known polymorphic branches and their local
   references (including UUID value-codec handling).
3. Require `@CompiledValidator(fallback=false)` to compile the **original**
   Creek schema unchanged, and compare all three return modes against
   `SchemaPlan` for strict and non-strict format.
4. Benchmark the full boolean path separately from fail-fast diagnostics and
   aggregate `ValidationResult`. Measure with JMH, same model and format mode.

The original Creek benchmark is non-strict, so direct performance comparisons
use `strictFormat=false`. CI compiles the benchmark and runs correctness
fixtures; **no JMH throughput result is claimed** from CI alone.
 \
  -f 3
```

JMH timings depend on CPU, JDK, GC and host load. Compilation and JUnit
correctness checks alone do not establish throughput parity; compare both
methods in the same execution environment before drawing performance
conclusions.

## Creek benchmark milestone

The unchanged Draft 2020-12 Creek schema is at
`sjf4j-testbench/src/jmh/resources/schema/creek-2020-12.json`; its
benchmark is `CreekSchemaBenchmark`. It includes `required`,
`additionalProperties:false`, decimal comparison, an enum, lists,
`minLength`, polymorphic `oneOf` with `$ref`, and UUID format.

1. **Phase 1 (implemented):** direct root, `required`, closed
   `additionalProperties`, native numeric/enum/string checks and recursive
   array item traversal, including finite local `$ref` expansion.
   The original Creek `oneOf` remains a localized cached item
   `SchemaPlan`; this is **mixed compiled/runtime**, not full compilation.
2. **Next:** compile the two known polymorphic branches and their local
   references (including UUID value-codec handling).
3. Require `@CompiledValidator(fallback=false)` to compile the **original**
   Creek schema unchanged, and compare all three return modes against
   `SchemaPlan` for strict and non-strict format.
4. Benchmark the full boolean path separately from fail-fast diagnostics and
   aggregate `ValidationResult`. Measure with JMH, same model and format mode.

The original Creek benchmark is non-strict, so direct performance comparisons
use `strictFormat=false`. CI compiles the benchmark and runs correctness
fixtures; **no JMH throughput result is claimed** from CI alone.

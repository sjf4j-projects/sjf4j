# CompiledValidator

`@CompiledValidator` generates validation methods for statically selected JSON
Schemas. This document defines the public method contract and tracks **direct
code-generation coverage**, separately from JSON Schema support provided by
the runtime `SchemaPlan`.

## Usage

```java
@CompiledValidator
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
omitted is `false`, matching `SchemaPlan` and JSON Schema Draft 2020-12's
format-annotation behavior. The name of the second parameter is unrestricted. It controls
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

Compiled and runtime validation follow JSON Schema semantics: a property
with value `null` is **present**. The `required` keyword succeeds for it,
while its property schema may reject the null value. Missing members must be
represented as absent properties.

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

| Keyword / feature | Direct code generation | Runtime SchemaPlan |
| --- | --- | --- |
| Boolean root schemas (`true`, `false`) | Yes | Yes |
| `type:object`, `properties` | Conditional: POJO direct fields; Map/JsonObject/JOJO dynamic keys; type-less object assertions supported | Yes |
| `required` | POJO presence; Map/JOJO dynamic key presence; opt-in null-as-missing | Yes |
| `additionalProperties:false` | Static POJO or runtime key scan for Map/JsonObject/JOJO | Yes |
| `minProperties`, `maxProperties` | Static POJO property count or runtime object-size check; null-as-missing uses runtime fallback | Yes |
| `type:string` | Native `String`, `Character`, and enum fields/items | Yes |
| `minLength`, `maxLength` | Direct string Unicode code-point length | Yes |
| `pattern` | Static precompiled ECMA-compatible regex; Java `Matcher.find()` | Yes |
| `enum` | String enums plus scalar JSON literals (string/number/boolean/null), including type-less scalar assertions; object/array literals fall back | Yes |
| `const` | Scalar JSON literals with OBNT numeric equality (e.g. 1 equals 1.0); object/array constants fall back | Yes |
| `type:number`, `type:integer` | Native numeric Java field types | Yes |
| `type:boolean`, `type:null`, union `type` arrays | Direct shape predicates (including JSON semantic integer checks); combined scalar const/enum when supported | Yes |
| `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum` | Direct native integral comparisons for 64-bit integer bounds; cached BigDecimal constants otherwise | Yes |
| `multipleOf` | Native `%` for integral primitives/wrappers with a positive 64-bit integer divisor; otherwise cached BigDecimal and exact remainder | Yes |
| `type:array` | Direct for declared `List`/`Set`/`Collection` | Yes |
| `minItems`, `maxItems` | Direct Collection size checks | Yes |
| `items` | Recursive keyword checks for typed nested collections; unsupported item subtrees use a cached plan | Yes |
| `format` | Known typed-string constraints use `FormatValidator` when `strictFormat` is true; assertion vocabularies and non-standard dialects fall back | Yes; assertion vocabulary may enforce format even in non-strict mode |
| `$ref`, `definitions`, `$defs` | Pure reference to a compile-time-resolvable JSON Pointer target; finite reference chains only; unsupported cycles/anchors fallback | Yes |
| `oneOf` | General branch counting with statically compiled predicates (overlap allowed); optional optimized subtype dispatch when proven disjoint | Yes |
| `allOf`, `anyOf`, `not` | Direct boolean predicates for fully compiled branches; siblings are conjoined; unsupported/stateful branches fall back as one scope | Yes |
| `if`, `then`, `else` | Static conditional dispatch with constant folding; missing `if` or unused branches ignored | Yes |
| `unevaluatedProperties`, `unevaluatedItems` | No | Yes |
| Root metadata (`$schema`, `title`, etc.) | Accepted when non-applicative | Yes |

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
Scalar `const` and `enum` checks use `Nodes.equals` to preserve JSON
numeric equality across Java number classes. Under a known standard
vocabulary, typed-string `format` checks use the existing
`FormatValidator` and the `strictFormat` flag. Explicit format-assertion
vocabularies conservatively retain the runtime `SchemaPlan` behavior.
General `oneOf` branch-count compilation emits one static boolean predicate per
branch and rejects zero or multiple matches. Branch overlap is legal; no Java
`@OneOf` annotation or discriminator is required. Direct compilation currently
requires all branches to be expressible with supported scalar, array, object,
reference, or nested `oneOf` checks. Unsupported combinations fall back as an
entire `oneOf` subtree, not a subset of branches. The specialized disjoint Java-subtype path remains available when its
preconditions are met. Unknown subtypes and strict-format cases retain runtime
fallback behavior. No evaluated tracking is generated; any `unevaluated*`
keyword at the relevant schema scope retains full-scope runtime evaluation.

`allOf`, `anyOf`, and `not` reuse static boolean branch helpers: all branches
must pass for `allOf`, any one may pass for `anyOf` (including overlaps), and
`not` inverts a full subschema. Assertions alongside applicators are combined
with logical AND, not skipped. Unsupported or evaluated-state-dependent branches
fall back as a complete combinator scope. The boolean direct path preserves
`strictFormat`; diagnostic modes retain SchemaPlan.

Conditional `if/then/else` compiles to one boolean predicate for `if` and
exactly one selected branch. If `if` is absent, `then` and `else` have no
validation effect; a lone `if` has no effect either. Compile-time `true` or
`false` conditions select a branch without compiling the unused branch.
Object assertions such as `properties`, `required`, and
`additionalProperties` can omit `type:object`, including schemas with
`required` but no `properties`. Their checks are skipped for dynamic
non-object values, consistent with JSON Schema applicability. Conditional
subschemas involving stateful evaluated-location tracking retain full-scope
runtime validation.

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
  `SchemaCompiler` dispatches to `ObjectCompiler`, `ScalarCompiler`,
  `ArrayCompiler`, and the conservative `OneOfCompiler`, producing recursive
  `SchemaCheck` nodes for supported constraints.
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
and fallback boundaries. `CompiledValidatorOneOfProcessorTest` checks
direct dispatch and verifies that private annotated fields cannot be used
for native Java member access.

To compile and test:

```bash
./gradlew :sjf4j-processor:test :sjf4j-testbench:test :sjf4j-testbench:jmhJar
```

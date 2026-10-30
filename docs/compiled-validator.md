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

## Direct keyword coverage

**Status meaning:** Direct = a check emitted in Java without a runtime plan;
Conditional = only specific root/POJO shapes, with other subtrees possibly
falling back; No = runtime `SchemaPlan` is used. The table describes
the **main-source** `ValidationGenerator`, not retired incubator experiments.
Combination restrictions always apply: currently the direct POJO root can
contain only `type: object` and `properties`, must match all readable Java
properties, and must use a public final POJO with public no-throw getters.

| Keyword / feature | Direct code generation | Runtime SchemaPlan | Creek milestone |
| --- | --- | --- | --- |
| Boolean root schemas (`true`, `false`) | Yes | Yes | Already supported |
| `type: object`, `properties` | Conditional | Yes | Expand root support |
| `type: string` | Conditional for `String` properties | Yes | Direct |
| `minLength`, `maxLength` | Conditional for direct strings (code-point length) | Yes | Direct |
| `required` | No | Yes | Direct |
| `additionalProperties` | No | Yes | Direct |
| `type: number`, `type: integer` | No | Yes | Direct |
| `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum` | No | Yes | Direct |
| `enum`, `const` | No | Yes | Direct |
| `type: array`, `items` | No | Yes | Direct |
| `minItems`, `maxItems` | No | Yes | Direct |
| `format` | No | Yes (subject to `strictFormat`) | Direct, preserving value-codec semantics |
| `$ref`, `definitions`, `$defs` | Only trivial root reference to a boolean document | Yes | Compile known local references |
| `oneOf` | No | Yes | Later milestone |
| `anyOf`, `allOf`, `not` | No | Yes | Later |
| `if`, `then`, `else` | No | Yes | Later |
| `unevaluatedProperties`, `unevaluatedItems` | No | Yes | Later |
| Root metadata (`$schema`, `title`, etc.) | Not accepted by direct-root path yet | Yes | Ignore when semantically inert |

In partial direct mode, unsupported property subtrees use cached fragment
`SchemaPlan` instances. `fallback=false` currently rejects these mixed cases;
direct coverage **does not** mean a full schema has been compiled.

## Creek benchmark milestone

The unchanged Draft 2020-12 Creek schema is at
`sjf4j-testbench/src/jmh/resources/schema/creek-2020-12.json`; its
benchmark is `CreekSchemaBenchmark`. It includes `required`,
`additionalProperties:false`, decimal comparison, an enum, lists,
`minLength`, polymorphic `oneOf` with `$ref`, and UUID format.

1. Compile the root object and all non-polymorphic properties directly,
   retaining a localized `SchemaPlan` for the `oneOf` elements.
2. Compile the two known polymorphic branches and their local references.
3. Require `@CompiledValidator(fallback=false)` to compile the **original**
   Creek schema without changing it; check correctness against runtime
   `SchemaPlan` for all three return modes and both format modes.
4. Benchmark the fully compiled `boolean` path separately from fail-fast
   diagnostics and complete `ValidationResult` collection. Keep JMH inputs,
   format mode, and resource setup equivalent.

This is a *target*, not a claim that Creek is fully compiled today. At present
its complete schema uses a runtime-plan fallback. The original Creek benchmark
is non-strict, so comparisons use `strictFormat=false` on the new API.

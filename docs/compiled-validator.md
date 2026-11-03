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

}

UserValidator validator = CompiledInstances.create(UserValidator.class);

// Aggregated diagnostics are an explicit runtime opt-in.
@CompiledValidator(fallback = true)
public interface UserDiagnosticValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    ValidationResult validate(User user, boolean strictFormat);
}
```

The return type, not the method name, selects the validation operation:

| Return type | Equivalent SchemaPlan semantics | Failure behavior |
| --- | --- | --- |
| `boolean` | `isValid(value, strictFormat)` | Fail fast, return false; do not create diagnostics |
| `void` | `requireValid(value, strictFormat)` | Native fail-fast checks when fully compiled; on failure, produce diagnostics and throw `ValidationException` |
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

### External schema resources and incremental compilation

`@CompiledValidator` supports `@ValidJsonSchema(ref = "...")` from a method,
parameter, type or superclass, and also the implicit file lookup when both
`value` and `ref` are empty. Schemas can reference other documents via
`$ref` as well.

**Incremental compilation caveat:** Java's JSR 269 annotation processing API
does not track changes to external JSON files as dependencies of generated Java
code. Copying resources before compilation solves resource availability, but
not change detection. If a referenced schema changes without Java sources
changing, a build may skip regeneration and leave compiled validation rules
stale. Build tooling must track schema inputs and rerun annotation processing;
a clean rebuild is a safe fallback. Specific Gradle/Maven configuration will
be documented separately.

## Null-valued object properties

Compiled and runtime validation follow JSON Schema semantics: a property
with value `null` is **present**. The `required` keyword succeeds for it,
while its property schema may reject the null value. Missing members must be
represented as absent properties.

## Compilation and fallback

```java
@CompiledValidator // fallback=false by default
public interface FastValidator {
    boolean isValid(MyPojo value);
}
```

The default is `fallback = false`: **every assertion must be compiled**
or annotation processing fails. Set `fallback = true` explicitly to allow
`SchemaPlan` for unsupported keywords or a remaining property subtree.
Methods returning `ValidationResult` always use runtime `SchemaPlan.validate`
for complete diagnostics, and therefore **require `fallback = true`**.
Otherwise annotation processing reports an explicit error. Unsupported assertions never silently
fall back to `SchemaPlan`. For `void requireValid`, compiled assertions emit
fail-fast guards and construct a single `ValidationResult.Message` with the
keyword, schema URI/pointer and instance path **only on failure**. They never
revalidate the root through `SchemaPlan`. Explicitly permitted unsupported
subtrees use their own `SchemaPlan.requireValid` once. Aggregated
`ValidationResult` methods continue to use the runtime engine. The restriction
applies to both return types and keyword combinations, not only to isolated
keywords.

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

### Compiled oneOf subtype pruning

A `oneOf` starts from **independent schema branch predicates**, compiled
against the declared Java type. Missing statically declared properties use
Nodes runtime object access; this is **not** a `SchemaPlan` fallback.

When `@OneOf` maps Java subtypes to discriminator values and both the JSON
Schema branch constraints and a public constant Java property prove those
values disjoint, the processor emits only the surviving subtype/branch checks.
For example, `PolyTypeA.@type = "poly-a"` cannot satisfy a branch requiring
`@type = "poly-b"`, so that cross-combination is pruned. This is an optional
optimization, not the semantic basis of `oneOf`: matching still means
**exactly one** branch validates. Runtime classes absent from the mapping
must evaluate the generic compiled branches where possible; otherwise the
original `SchemaPlan` fallback rules apply.

## Direct keyword coverage

**Status meaning:** Direct = emitted Java checks without a runtime plan;
Conditional = a known Java representation/root shape and/or localized
`SchemaPlan` fallback; No = use `SchemaPlan` for that constraint. This
matrix covers the production `org.sjf4j.processor.schema` generator, not the retired
incubator prototype.

The compiler separates **schema compilation** from **value access**.
For a complete public final POJO projection, an absent `required` property is a
compile-time constant rejection (not a SchemaPlan fallback or compilation
error); inside `oneOf` or `anyOf`, this allows branch pruning.
JsonObject and JOJO declarations use native `containsKey`, `getNode`,
`keySet`, and `size` operations rather than generic Nodes dispatch.
A supported keyword is emitted as Java checks regardless of whether the declared
Java type exposes all properties. A public final POJO with an accessible,
fully known projection uses Java getters/fields directly. JsonObject and JOJO
use their native APIs, while interfaces, non-final classes, unknown property
types, and Map use `Nodes` to inspect the **runtime** property set. Nodes access is compiled execution, **not SchemaPlan fallback**.
A `@NodeValue` or registered value-codec instance remains an **atomic
leaf**, even if its serialized raw representation is a Map or List. Object and
array structural keywords must **not** traverse that raw encoding. Generated
predicates check whether the runtime value is a structural node before calling
Nodes, including for `Object`-typed declarations. A container-valued logical
leaf has no object/array schema type; without an explicit `type`, object-only
keywords are inapplicable to that leaf. A plain Map/List still behaves as a
normal traversable object/array. Scalar raw encodings remain available for
leaf assertions such as const/enum and scalar constraints.
Only unsupported schema semantics trigger `fallback=true` or a compilation
error when `fallback=false`.

Dynamic object checks include a runtime JSON-object guard: an explicit
`type:object` rejects non-object values; object-specific keywords without
`type` do not apply to non-objects. A missing property skips its
`properties` subschema; a present property holding `null` is still present
for `required`. Runtime subclass properties are included for
`additionalProperties:false` and property counts. Generic `oneOf` can
compile independent predicates without requiring `@OneOf` on the declared
base type, provided that **every branch** can compile without SchemaPlan.

The root may contain `type:object`, `properties`, `required`,
`additionalProperties` (boolean), and non-applicative metadata including
`$schema`, `title`, `definitions`, and `$defs`.

| Keyword / feature | Direct code generation | Runtime SchemaPlan |
| --- | --- | --- |
| Boolean root schemas (`true`, `false`) | Yes | Yes |
| `type:object`, `properties` | Direct: final POJO getters/fields when safe, otherwise Nodes for runtime object keys (including base types/interfaces); type-less object assertions supported | Yes |
| `required` | Final POJO presence or runtime Nodes key presence; opt-in null-as-missing | Yes |
| `additionalProperties:false` | Static POJO or runtime key scan for Map/JsonObject/JOJO | Yes |
| `minProperties`, `maxProperties` | Static POJO property count or runtime object-size check; null-as-missing uses runtime fallback | Yes |
| `dependentRequired` | Direct dynamic-key checks and constant final-POJO evaluation; ignored under Draft 7 | Yes |
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
| `oneOf` | Compile all branch predicates against the declared Java type; optionally specialize and prune subtype/branch combinations when the discriminator and Java compile-time constant prove disjointness. Unknown runtime subclasses use compiled generic predicates where available | Yes, only if generic branches cannot compile |
| `allOf`, `anyOf`, `not` | Direct boolean predicates for fully compiled branches; siblings are conjoined; unsupported/stateful branches fall back as one scope | Yes |
| `if`, `then`, `else` | Static conditional dispatch with constant folding; missing `if` or unused branches ignored | Yes |
| `unevaluatedProperties`, `unevaluatedItems` | No | Yes |
| Root metadata (`$schema`, `title`, etc.) | Accepted when non-applicative | Yes |

**Conformance boundary:** direct scalar checks are fastest for native Java
shapes. Dynamically typed checks use `JsonType.of(value)`, including the default
scalar shape of `@NodeValue`/ValueCodec types. Map/List-backed ValueCodecs remain
opaque leaves (`UNKNOWN`) and are never traversed as structural nodes. Selected
RuntimeContext formats are not consulted by `JsonType.of()`; validation of
codec-converted values still uses runtime fallback where direct conversion is
not supported. Unsupported properties fall back as whole subtrees, not partial
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
`strictFormat`; `requireValid` reuses its fail-fast guards and defers
diagnostic creation until an assertion fails. Aggregating `ValidationResult`
continues to use `SchemaPlan`.

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


## Official Draft 2020-12 compiled conformance harness

The official test-suite download, runtime test and compiled test are owned by
`sjf4j-testbench`. The downloaded upstream suite is kept under
`sjf4j-testbench/build/official-json-schema-tests/suite` rather than checked in.

```bash
# Run once to populate local build/ from upstream main:
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests

# Then offline (no GitHub access) using the cached suite:
./gradlew :sjf4j-testbench:officialRuntimeTest
./gradlew :sjf4j-testbench:officialCompiledTest

# Pin a revision for repeatable local testing (updates the cache if necessary):
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests -PjsonSchemaSuiteRef=<40-character-SHA>

# Check for the newest upstream main revision and update the cache if necessary:
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests
```

`generateOfficialCompiledCases` produces one independent interface per
non-optional Draft 2020-12 schema group, plus an unchanged test-to-group index,
under `build/generated/officialCompiled/`. Generated schema resources and
interfaces are build artifacts, not hand-maintained or checked in. All top-level
`tests/draft2020-12/*.json` files are discovered; `optional/` is excluded.

`OfficialCompiledTest` uses javac and the production `CodegenProcessor` to
compile each schema binding, initially batching by upstream file and recursively
splitting any failed batch. It runs one JUnit dynamic test per original instance
and never skips JSON `null`. A group that cannot compile is reported as
`COMPILE_ERROR` for all its examples, rather than aborting discovery or
silently passing. Runtime execution errors and assertion mismatches are likewise
recorded without changing application code. The reports are written to
`build/reports/officialCompiled/all/{summary,groups,gaps,details}.json`, including the suite
revision, total examples, per-file counts and unaccounted count.

This first-stage harness tests **conformance using `fallback=true`** and
records compilation blockers. It does not yet determine whether compiled
methods are direct, partially falling back, or wholly falling back. Those
classifications and any production fixes are separate follow-up work.


### Official compiled test selection

Only **one Gradle test task** executes official compiled cases:
`officialCompiledTest`. It runs every mandatory Draft 2020-12 case by default
(the `optional/` subtree is excluded). Phase 2 and Phase 3 represent
coverage categories in the **same** runner; they are not separate Gradle tasks.

```bash
# Once per local checkout, or to select a known upstream revision:
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests -PjsonSchemaSuiteRef=YOUR_40_CHARACTER_SHA

# Entire mandatory suite (default), offline using the cached revision:
./gradlew :sjf4j-testbench:officialCompiledTest

# Optional focused debugging with direct-only compilation probes:
./gradlew :sjf4j-testbench:officialCompiledTest -PofficialCompiledCategory=phase2 -PofficialCompiledProbeDirect=true
./gradlew :sjf4j-testbench:officialCompiledTest -PofficialCompiledCategory=phase3 -PofficialCompiledProbeDirect=true
./gradlew :sjf4j-testbench:officialCompiledTest -PofficialCompiledCategory=unevaluated -PofficialCompiledProbeDirect=true
```

The case categories `phase2` (basic, scalar, object), `phase3`
(array, applicator), `unevaluated`, and `reference` remain available
for local diagnostics. The default `all` runs every mandatory file found
in the downloaded suite, including newly introduced official files. There is
no hardcoded upstream SHA, file count, schema group count, or assertion count;
coverage is verified against the generated index and actual official files.

The generated per-group interfaces still offer both
`@CompiledValidator(fallback=false)` and `fallback=true`.
Set `-PofficialCompiledProbeDirect=true` to try direct compilation first;
unsupported groups are retried with fallback enabled and their direct
diagnostics are retained. Without the probe, execution uses `fallback=true`
and the report marks groups `UNPROBED`, not `DIRECT`.

Results are written to
`sjf4j-testbench/build/reports/officialCompiled/<category>/`:
`summary.json`, `groups.json`, `gaps.json`, and `details.json`.
Individual official assertions, including JSON null, are always recorded.
A green fallback run does not imply fully direct-compiled support.

### Suite cache, local build, publishing and IntelliJ main

The **suite downloader is not a dependency of everyday tests or build**.
`downloadOfficialSchemaTests` resolves the requested revision (by default
upstream `main`) when **explicitly invoked**. If the local
`OFFICIAL_TEST_SUITE_REV` SHA matches the requested revision and the expected
fixture directories exist, it **does not fetch/extract the ZIP again**.
A 40-character `-PjsonSchemaSuiteRef` avoids even the revision lookup.
Otherwise it downloads into a staging folder before replacing the cache.

`./gradlew build` (from the repository root) runs the subprojects'
`build` tasks. In `:sjf4j-testbench:check` we include
`officialRuntimeTest` and the **full** `officialCompiledTest`.
Both use the locally cached suite, and compiled bindings are generated only
when missing or out-of-date. There is **no network request** during build.
On a fresh checkout without a local suite, these official test tasks are
marked **SKIPPED**: use the explicit download task once to opt into them.
Gradle `clean` deletes this build-directory cache, so a subsequent build
also requires one explicit download to restore the official tests.

`publish`, `publishToMavenLocal` and `publishToMavenCentral` (including
individual Maven publication upload tasks) depend on
`officialPublishGate`, across published modules, not just
`:sjf4j-schema`. The gate invokes `downloadOfficialSchemaTests` **first** to check upstream
`main` and reuse the cached ZIP if the revision is unchanged, then
regenerates bindings as required and runs the full official Runtime and
Compiled suites. Supplying a pinned `-PjsonSchemaSuiteRef` while publishing
is rejected; it cannot bypass the latest-suite requirement.
A failed upstream check, compilation error, or test failure blocks publication. This applies even if the cache was already
present.

To run **Compiled** directly as an IntelliJ *Application*, open
`org.sjf4j.testbench.schema.official.OfficialCompiledTest` under
`sjf4j-testbench/src/test/java` and run its `public static void main`.
The entrypoint accepts positional arguments:

```text
OfficialCompiledTest [category [file.json [groupIndex [testIndex]]]]
```

Examples in IntelliJ's **Program arguments**:

```text
phase3
unevaluated
phase3 unevaluatedItems.json 0
phase3 unevaluatedItems.json 0 1
```

It automatically locates the suite and generated resources when the working
directory is either the repository root or `sjf4j-testbench`. It uses the
production annotation processor via the JDK's `JavaCompiler`, probes direct
compilation by default, and writes reports under
`sjf4j-testbench/build/reports/officialCompiled/<category>/`.
No network traffic or Gradle invocation takes place inside `main`.

**First-time setup for IntelliJ main:**

```bash
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests
./gradlew :sjf4j-testbench:generateOfficialCompiledCases
```

`OfficialRuntimeTest` also retains its `main(String[])` entrypoint.
By default it runs mandatory Draft 2020-12 runtime cases from the same
cached directory; its sole optional argument overrides the suite root.

Neither entrypoint auto-downloads or changes the official fixtures.


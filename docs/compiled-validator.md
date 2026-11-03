# CompiledValidator

`@CompiledValidator` generates Java validation methods from a JSON Schema at annotation-processing time. Unsupported schema features can optionally use the runtime `SchemaPlan`.

## Usage

```java
@CompiledValidator
public interface UserValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    boolean isValid(User user);

    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    void requireValid(User user, boolean strictFormat);
}

UserValidator validator = CompiledInstances.create(UserValidator.class);
boolean valid = validator.isValid(user);
```

The method's **return type** determines its behavior:

| Return type | Behavior |
| --- | --- |
| `boolean` | Fail fast; return `false` on a validation failure |
| `void` | Fail fast; throw `ValidationException` on failure |
| `ValidationResult` | Collect errors via `SchemaPlan`; requires `fallback = true` |

Methods take one value argument and optionally a primitive `boolean strictFormat`. The default is `false` (format-annotation semantics). Additional arguments and type variables are unsupported.

**Null semantics:** a Java `null` root value is skipped and considered valid; this differs from validating JSON `null` with `SchemaPlan`. An object property explicitly set to `null` is **present**, so it satisfies `required` even when its value schema rejects null.

`@ValidJsonSchema` may appear on the method, value parameter, or value type (and ancestors); that is also the lookup precedence. Inline `value` takes precedence over `ref`. Resources support `classpath:/`, `file:/`, and resolvable JSON Pointer fragments. With neither `value` nor `ref`, conventional schema-file lookup applies.

## Compilation and fallback

`fallback = false` is the default: unsupported schema constraints cause a **compile-time error**. Opt in to runtime fallback when needed:

```java
@CompiledValidator(fallback = true)
public interface UserDiagnosticValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    ValidationResult validate(User user);
}
```

Supported assertions compile into Java predicates. A partially supported schema can delegate an unsupported subtree to `SchemaPlan`; keywords that need shared evaluation state (notably `unevaluated*`) fall back at the appropriate scope. `ValidationResult` methods always use the runtime engine for complete diagnostics. `CompiledInstances.create(Validator.class, RuntimeContext)` is supported as a factory, but validation options currently come from method arguments, not `RuntimeContext`.

### Direct code-generation coverage

| Feature | Direct compilation |
| --- | --- |
| Boolean schemas; `type`; scalar `enum` / `const` | Supported; object/array literals may require fallback |
| String: `minLength`, `maxLength`, `pattern` | Supported, including Unicode code-point lengths |
| Number: bounds, `multipleOf` | Supported for native numeric types and exact comparisons |
| Object: `properties`, `required`, `additionalProperties: false`, property counts, `dependentRequired` | Supported for suitable POJOs and dynamic Nodes access |
| Array: `items`, `minItems`, `maxItems` | Supported where element constraints can compile |
| Applicators: `allOf`, `anyOf`, `oneOf`, `not`, `if/then/else` | Supported when their branches can compile |
| `$ref`, `$defs` | Compile-time-resolvable JSON Pointer targets; cycles/anchors may fall back |
| `format` | Supported typed-string checks when appropriate; `strictFormat` and vocabularies affect semantics |
| `unevaluatedProperties`, `unevaluatedItems` | Runtime fallback (evaluation-location tracking not generated) |

The generator uses direct Java member access for statically known types and strict `Nodes` access for dynamic values; **Nodes access is still compiled execution**, not a `SchemaPlan` fallback. `@NodeValue` and ValueCodec objects are atomic leaves, even when their raw encoding is a Map/List. `oneOf` may prune impossible branches when known discriminator constants prove them disjoint.

Coverage is conditional on the Java type and complete schema context; the table does not imply that every keyword combination compiles directly.

## Official JSON Schema tests

Official fixtures live in `sjf4j-testbench/build/official-json-schema-tests/suite/` and are **not committed**.

```bash
# Populate or update the local cache (checks the upstream revision):
./gradlew :sjf4j-testbench:downloadOfficialSchemaTests

# Use the cached fixtures without downloading:
./gradlew :sjf4j-testbench:officialRuntimeTest
./gradlew :sjf4j-testbench:officialCompiledTest

# Optional: narrow a compiled test run and probe fallback=false:
./gradlew :sjf4j-testbench:officialCompiledTest -PofficialCompiledCategory=unevaluated -PofficialCompiledProbeDirect=true
```

`officialCompiledTest` includes **all non-optional Draft 2020-12 cases** by default. It generates independent validator bindings, isolates javac failures by schema group, and records every test (including JSON null) in `sjf4j-testbench/build/reports/officialCompiled/<category>/`:

- `summary.json`: overall and category counts
- `groups.json`, `gaps.json`: compilation mode and unresolved groups
- `details.json`: individual assertions

Categories include `all` (default), `basic`, `scalar`, `object`, `array`, `applicator`, `unevaluated`, `reference`, `phase2`, and `phase3`. The last two are filters, **not separate Gradle tasks**. When direct probing is disabled, compilation mode is reported as `UNPROBED`.

### IntelliJ main

Run `OfficialCompiledTest.main(String[])` from `sjf4j-testbench/src/test/java` with optional arguments:

```text
[category [file.json [groupIndex [testIndex]]]]
```

For example: `phase3 unevaluatedItems.json 0 1`. Before first use, run `downloadOfficialSchemaTests` and `generateOfficialCompiledCases` once. `OfficialRuntimeTest.main(String[])` is also available.

### Build and publish

`./gradlew build` uses the **locally cached** official suite and does not download it. If no cache exists (including after `clean`), official test tasks are skipped; download the suite explicitly to enable them.

Published modules depend on `officialPublishGate`, which invokes `downloadOfficialSchemaTests` to check upstream `main`, then requires **full Runtime and Compiled official tests** to pass. A pinned `-PjsonSchemaSuiteRef` or narrowed category is rejected for publication.

**Incremental compilation:** changing an external schema resource does not necessarily invalidate Java annotation-processing outputs. Track schema inputs in the build or force recompilation when changing referenced schemas.

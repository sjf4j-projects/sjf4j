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

To preload an entire set of referenced schemas at annotation-processing time, specify an optional file-system **registry directory**:

```java
@CompiledValidator(fallback = true, registryDir = "file:/path/to/schemas/")
public interface ExternalValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/strict.json")
    boolean isValid(Object value);
}
```

The processor calls `SchemaRegistry.indexDir()` once per directory and retrieval-base combination in an annotation-processing run. The registry recursively indexes all `.json` files using `SchemaLoader.Local.load()`, preserving retrieval URIs and canonical `$id` aliases before resolving references. An indexed schema is not compiled into a `SchemaPlan` until needed. When local files represent remotely retrieved schemas **without `$id`**, set `registryBaseUri` to their logical retrieval directory, for example `registryBaseUri = "http://localhost:1234/"`. Files are still read from `registryDir`, but `draft2020-12/integer.json` is indexed as `http://localhost:1234/draft2020-12/integer.json`. No HTTP requests are made. The default `registryDir = ""` and `registryBaseUri = ""` preserve existing behavior. File-system directories are supported (absolute `file:` URI or working-directory-relative path ending in `/`); classpath directory enumeration and automatic HTTP fetching are deliberately unsupported. Runtime fallback must be able to read the **same directory** after deployment; fully direct validators do not need it at runtime. Gradle incremental builds must track this directory as an input.

The method's **return type** determines its behavior:

| Return type | Behavior |
| --- | --- |
| `boolean` | Fail fast; return `false` on a validation failure |
| `void` | Fail fast; throw `ValidationException` on failure |
| `ValidationResult` | Collect errors via `SchemaPlan`; requires `fallback = true` |

Methods take one value argument and optionally a primitive `boolean strictFormat`. The default is `false` (format-annotation semantics). Additional arguments and type variables are unsupported.

**Null semantics:** a Java `null` root value represents the JSON `null` instance and is validated against the schema, just like `SchemaPlan`: `false` and `{"type":"object"}` reject it, while `true` and `{"type":"null"}` accept it. Object-only keywords such as `required` do not apply to a null instance unless an enclosing schema requires the object type. An object property explicitly set to `null` is **present**, so it satisfies `required` even when its value schema rejects null.

`@ValidJsonSchema` may appear on the method, value parameter, or value type (and ancestors); that is also the lookup precedence. Inline `value` takes precedence over `ref`. Resources support `classpath:/`, `file:/`, and resolvable JSON Pointer fragments. With neither `value` nor `ref`, conventional schema-file lookup applies.

## Compilation and fallback

`fallback = false` is the default: unsupported schema constraints cause a **compile-time error**. **CompiledValidator currently supports Draft 2020-12 only.** A schema without `$schema` uses 2020-12 by default. Explicit Draft-07 or Draft 2019-09 declarations are compilation errors, even with `fallback = true`; unknown custom meta-schemas require explicit runtime fallback because their vocabularies may differ. The runtime `SchemaRegistry` and `SchemaPlanner` retain existing multi-draft support. Opt in to runtime fallback when needed:

```java
@CompiledValidator(fallback = true)
public interface UserDiagnosticValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    ValidationResult validate(User user);
}
```

Supported assertions compile into Java predicates. A partially supported schema can delegate an unsupported subtree to `SchemaPlan`; keywords that need shared evaluation state (notably `unevaluated*`) fall back at the appropriate scope. `ValidationResult` methods always use the runtime engine for complete diagnostics.

When a validator needs fallback, annotation processing reports the **first identified semantic reason**. With `fallback=false` this is a compilation error; with `fallback=true` it is a compiler warning. For example, `allOf + unevaluatedProperties requires evaluated-location tracking`. The diagnostic includes the relevant schema keyword location where known; unfamiliar constructs may still produce a generic reason. `CompiledInstances.create(Validator.class, RuntimeContext)` is supported as a factory, but validation options currently come from method arguments, not `RuntimeContext`.

### Direct code-generation coverage

| Feature | Direct compilation |
| --- | --- |
| Boolean schemas; `type`; scalar `enum` / `const` | Supported; object/array literals may require fallback |
| String: `minLength`, `maxLength`, `pattern` | Supported, including Unicode code-point lengths |
| Number: bounds, `multipleOf` | Supported for native numeric types and exact comparisons |
| Object: `properties`, `patternProperties`, `additionalProperties` (boolean or schema), `propertyNames`, `required`, `dependentRequired`, `dependentSchemas`, property counts | Supported when child schemas compile; POJO/OBNT affect access code, not fallback eligibility |
| Array: `prefixItems`, `items`, `contains`, `minContains`, `maxContains`, `uniqueItems`, `minItems`, `maxItems` | Independent compiled checks; `items` starts after local `prefixItems`; `contains` counts matching elements; `uniqueItems` uses runtime JSON equality semantics |
| Applicators: `allOf`, `anyOf`, `oneOf`, `not`, `if/then/else` | Supported when their branches can compile |
| `$ref`, `$defs`, `$anchor` | Direct static references resolved by `SchemaRegistry` and `SchemaPlanner` at annotation-processing time, using `SchemaPlan.schema()` / `schemaUri()`; includes nested `$id`, registered external schemas, JSON Pointer, named anchors and recursive references; generated direct validators do not invoke runtime SchemaPlan |
| `format` | Supported typed-string checks when appropriate; `strictFormat` and vocabularies affect semantics |
| `unevaluatedProperties`, `unevaluatedItems` | Direct only for isolated root-local coverage (object: `properties` / `patternProperties`; array: `prefixItems` / suffix, plus co-located `items` + `unevaluatedItems` where `items` already validates the entire remaining suffix). Combinations needing cross-schema evaluated-location tracking still fall back. |

The generator uses direct Java member access for statically known types and strict `Nodes` access for dynamic values; **Nodes access is still compiled execution**, not a `SchemaPlan` fallback. `@NodeValue` and ValueCodec objects are atomic leaves, even when their raw encoding is a Map/List. `oneOf` may prune impossible branches when known discriminator constants prove them disjoint.

Direct compilation eligibility depends on the **schema semantics**, never on whether the Java instance is a POJO or OBNT. Each keyword, including `type`, compiles independently; related object keywords share only their required local evaluation scope. The table does not imply that every keyword combination or dialect compiles directly.

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

`officialCompiledTest` includes **all non-optional Draft 2020-12 cases** by default. The generated validators default to `fallback = false`; only groups manually allowlisted in `OfficialCompiledTest.fallbackGroups()` run with `fallback = true`. Each group's fallback selection is owned by that Java test class, not a separate config file. New or unsupported groups fail compilation rather than silently switching to runtime validation. Each group is compiled independently on failure, and every assertion (including JSON null) is recorded in `sjf4j-testbench/build/reports/officialCompiled/<category>/`:

- `summary.json`: overall and category counts
- `groups.json`, `gaps.json`: compilation mode and unresolved groups
- `details.json`: individual assertions

Categories include `all` (default), `basic`, `scalar`, `object`, `array`, `applicator`, `unevaluated`, `reference`, `phase2`, and `phase3`. The last two are filters, **not separate Gradle tasks**. Reports label each group `DIRECT` or `FALLBACK` according to its explicit setting; `COMPILE_ERROR` identifies an unallowlisted unsupported schema or other compile failure. With `-PofficialCompiledProbeDirect=true`, every listed fallback group is also compiled with `fallback=false`, and a stale allowance that now compiles directly fails the test.

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

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

To mock or preload specific retrieval URIs, configure the binding itself using
`@ValidJsonSchema.externalSchemas` (an inline JSON object) or
`@ValidJsonSchema.externalSchemasRef` (a local JSON mapping file):

```java
@CompiledValidator
interface MockedValidator {
    @ValidJsonSchema(
        ref = "classpath:/schemas/user.json",
        externalSchemasRef = "classpath:/schemas/external.json")
    boolean isValid(Object value);
}
```

`external.json` follows the official JSON Schema Test Suite format:

```json
{
  "https://example.org/common": {"type": "string"},
  "https://example.org/always": true
}
```

Each key must be an absolute retrieval URI without a fragment. Each value must
be an object or boolean JSON Schema. Inline `externalSchemas` takes precedence
over `externalSchemasRef`. Both are scoped to the individual schema binding, so
different methods may use different documents under the same URI. These mappings
are compiled without HTTP requests and reconstructed from the annotation for
runtime fallback.
The mapping file must remain available at runtime if fallback is enabled.
Changes to external mapping files must be tracked as build inputs.

At annotation-processing time, each schema binding builds its own `SchemaRegistry` from the root schema, local references, and explicitly mapped external schemas. The processor never fetches remote URIs over HTTP. There is no directory-scanning configuration on `@CompiledValidator`; for a collection of offline schemas, supply a URI-to-schema mapping file through `externalSchemasRef`. At runtime, use `registry.index(new SchemaLoader.Local(directory).loadDir())` to preload a local directory independently.

`externalSchemasRef` must remain accessible after deployment when runtime fallback is used. Fully compiled validators do not load these resources at runtime. Build tooling must track the mapping file and referenced schema files as inputs for incremental compilation.

The method's **return type** determines its behavior:

| Return type | Behavior |
| --- | --- |
| `boolean` | Fail fast; return `false` on a validation failure |
| `void` | Fail fast; throw `ValidationException` on failure |
| `ValidationResult` | Collect errors via `SchemaPlan`; requires `fallback = true` |

Methods take one value argument and optionally a primitive `boolean strictFormat`. The default is `false` (format-annotation semantics). Additional arguments and type variables are unsupported.

**Null semantics:** a Java `null` root value represents the JSON `null` instance and is validated against the schema, just like `SchemaPlan`: `false` and `{"type":"object"}` reject it, while `true` and `{"type":"null"}` accept it. Object-only keywords such as `required` do not apply to a null instance unless an enclosing schema requires the object type. An object property explicitly set to `null` is **present**, so it satisfies `required` even when its value schema rejects null.

`@ValidJsonSchema` may appear on the method, value parameter, or value type (and ancestors); that is also the lookup precedence. Inline `value` takes precedence over `ref`; similarly, inline `externalSchemas` takes precedence over `externalSchemasRef`. Resources support `classpath:/`, `file:/`, and resolvable JSON Pointer fragments. With neither `value` nor `ref`, conventional schema-file lookup applies.

## Compilation and fallback

`fallback = false` is the default: unsupported schema constraints cause a **compile-time error**. **CompiledValidator supports Draft-07, Draft 2019-09, and Draft 2020-12.** A schema without `$schema` uses 2020-12 by default. Standard dialects use their effective keyword semantics, including nested and referenced schema scopes; tuple `items`/`additionalItems`, legacy `dependencies`, and Draft-07 `$ref` siblings are handled during direct compilation. Features not yet directly compiled (notably complex cross-schema evaluated-location tracking) still require explicit `fallback = true`. Unknown custom meta-schemas require runtime fallback because their vocabularies may differ. Opt in to runtime fallback when needed:

```java
@CompiledValidator(fallback = true)
public interface UserDiagnosticValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    ValidationResult validate(User user);
}
```

Supported assertions compile into Java predicates. A partially supported schema can delegate an unsupported subtree to `SchemaPlan`; keywords that need shared evaluation state (notably `unevaluated*`) fall back at the appropriate scope. `ValidationResult` methods always use the runtime engine for complete diagnostics.

When a validator needs fallback, annotation processing reports the **first unsupported construct at its schema location**. Where evaluation-location propagation crosses a keyword boundary, the diagnostic names the actual combination, e.g. `allOf + unevaluatedProperties requires runtime fallback: cross-schema evaluated-location tracking is not supported by CompiledValidator`. The error also advises `@CompiledValidator(fallback=true)`. Remaining limitations such as cross-schema evaluated-location tracking, `$vocabulary` interpretation, and `ValidationResult` aggregation are diagnosed explicitly; a second keyword is never invented. With `fallback=false` this is a compilation error; `fallback=true` permits runtime fallback without a compiler warning. Non-semantic projection failures report the specific schema keyword and location where possible, not a fabricated combination. For inherited Java types with multiple `@ValidJsonSchema` annotations, boolean and `void` validator methods compile each binding independently, applying parent bindings before child bindings. If any binding cannot compile directly, the entire method uses runtime fallback with a diagnostic naming the failing binding. `ValidationResult` methods continue using runtime aggregation. `CompiledInstances.create(Validator.class, NodeConfig)` is supported, but validation options currently come from method arguments rather than `NodeConfig`.

### Direct code-generation coverage

| Feature | Direct compilation |
| --- | --- |
| Boolean schemas; `type`; scalar `enum` / `const` | Supported; object/array literals may require fallback |
| String: `minLength`, `maxLength`, `pattern` | Supported, including Unicode code-point lengths |
| Number: bounds, `multipleOf` | Supported for native numeric types and exact comparisons |
| Object: `properties`, `patternProperties`, `additionalProperties` (boolean or schema), `propertyNames`, `required`, `dependentRequired`, `dependentSchemas`, legacy `dependencies`, property counts | Supported when child schemas compile; POJO/OBNT affect access code, not fallback eligibility |
| Array: `prefixItems`, `items`, `additionalItems`, `contains`, `minContains`, `maxContains`, `uniqueItems`, `minItems`, `maxItems` | Independent compiled checks; Draft-07/2019-09 tuple `items` uses `additionalItems` for the suffix, while Draft 2020-12 uses `prefixItems` + `items`; `contains` counts matching elements; `uniqueItems` uses runtime JSON equality semantics |
| Applicators: `allOf`, `anyOf`, `oneOf`, `not`, `if/then/else` | Supported when their branches can compile |
| `$dynamicRef` / Draft 2019-09 `$recursiveRef` | Uses `SchemaRegistry.resolveDynamicRef(URI, List<SchemaPlan>)` and the currently compiled schema-resource scope to specialize dynamic targets into static helper calls, including different targets reached through separate branches. A 2019-09 `$recursiveAnchor: true` is the empty dynamic anchor. Recursive contexts reuse helpers; unsupported cross-schema evaluation or excessive specialization still falls back. |
| `$ref`, `$defs`, `$anchor` | Direct static references resolved by `SchemaRegistry` and `SchemaPlanner` at annotation-processing time, using `SchemaPlan.schema()` / `schemaUri()`; includes nested `$id`, registered external schemas, JSON Pointer, named anchors and recursive references; generated direct validators do not invoke runtime SchemaPlan |
| `format` | Supported typed-string checks when appropriate; `strictFormat` and vocabularies affect semantics |
| `unevaluatedProperties`, `unevaluatedItems` | Direct only for isolated root-local coverage (object: `properties` / `patternProperties`, plus co-located `additionalProperties` which already evaluates every unmatched key; array: `prefixItems` / suffix, plus co-located `items` + `unevaluatedItems` where `items` already validates the entire remaining suffix). Co-located `not` and literal-true `allOf` do not export evaluated locations and can compile alongside `unevaluated*`. A simple `allOf` can also compile when its branches export only statically known property names, regex patterns, array prefix positions, or complete coverage (`additionalProperties: true` / `items: true`); this is planned at compile time without runtime evaluated-location state. Conditional, referenced, and nested coverage still fall back. |

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
./gradlew :sjf4j-testbench:officialAnnotationTest

```

`officialRuntimeTest` runs required and optional runtime cases together for all three drafts. It scans each root file once per draft, runs root `format-assertion` groups with strict format checking, and includes compatible groups from `validation/tests/optional/**/*.json`. Optional files retain their own `tags` semantics. Each draft has one summary with separate REQUIRED and OPTIONAL counts, followed only by failed cases and execution errors. REQUIRED means required by SJF4J's support policy: all root groups, including format assertions, and `optional/format-assertion.json`. Although the specification makes format assertion support optional, SJF4J requires these supported cases to pass `check` and publication. Remaining OPTIONAL results are diagnostic. The vocabulary fixture runs without forcing strict format, to test the schema's own vocabulary. No report artifact is generated. Compiled and Annotation suites remain strict.

`officialCompiledTest` runs all SJF4J-required validation cases for **Draft-07, Draft 2019-09 and Draft 2020-12** against the upstream unified `validation/tests/` directory, including `optional/format-assertion.json`. The compiled validator receives the group-specific strict format flag; the vocabulary fixture uses the existing explicit runtime fallback for custom dialects without forcing the flag. The test runner applies each group's `compatibility` and `tags` without modifying the official fixtures. Each compatible group produces an independent generated validator and classpath resources. Generated fixtures use `@ValidJsonSchema.externalSchemasRef` with a per-group JSON mapping resource, without an internal javac option. Runtime fallbacks load the same mapping resources. The generated test copies add an explicit `$schema` only for older drafts when missing from the original Schema. No public `@CompiledValidator` annotation attributes are added.

Gradle/JUnit always run all required drafts and groups; use `OfficialCompiledMain` positional arguments for a filtered run. The generated validators default to `fallback=false`; only the **explicit, per-draft fallback groups** in `OfficialCompiledTest.fallbackGroups()` use `fallback=true`. These groups are audited against the unified suite, including cross-schema `unevaluated*` coverage, dynamic/recursive references and custom vocabularies. Each fallback group is also probed with `fallback=false`; a stale allowance fails the suite. Unexpected compile errors or assertion mismatches are never silently skipped. Every assertion, including JSON null, is recorded in `sjf4j-testbench/build/reports/officialCompiled/<draft>/all/`:

- `summary.json`: overall and category counts
- `groups.json`, `gaps.json`: compilation mode and unresolved groups
- `details.json`: individual assertions

Reports retain per-keyword category breakdowns, but there are no category filtering parameters. Reports label each group `DIRECT` or `FALLBACK` according to its explicit setting; `COMPILE_ERROR` identifies an unallowlisted unsupported schema or other compile failure. By default, every listed fallback group is also compiled with `fallback=false`, and a stale allowance that now compiles directly fails the test. There is no flag to skip this conformance check.

### IntelliJ

Run `OfficialCompiledTest` as a **JUnit test** to see and rerun individual dynamic cases in the test tree.

To run a filtered subset as an Application, use `OfficialCompiledMain.main(String[])` from `sjf4j-testbench/src/test/java` with optional arguments:

```text
[dialect [file.json [groupIndex [testIndex]]]]
```

For example: `draft2019-09 unevaluatedItems.json 0 1`, or `draft2020-12 ref.json`. The first argument is the dialect (`all`, `draft7`, `draft2019-09`, or `draft2020-12`; default: `all`). The standalone Main selects the dialect from program arguments only; it does not read `sjf4j.schema.officialDraft` or the JUnit category property. Before first use, run `downloadOfficialSchemaTests` and `generateOfficialCompiledCases` once. Run `OfficialRuntimeMain.main(String[])` as an Application; it uses the same `[dialect [file.json [groupIndex [testIndex]]]]` positional arguments. Runtime also accepts a relative `optional/.../file.json` path in the file position. All three official Main entrypoints accept the same four positions and do not use JVM or Gradle properties to select tests.

Run `OfficialAnnotationTest` as a **JUnit test**. By default it checks every compatible case across **Draft-07, Draft 2019-09 and Draft 2020-12**, grouped by dialect and file in the JUnit tree. A failing compatible case fails the build; there is no draft-specific failure allowlist. The official `compatibility` metadata is evaluated against each draft: an omitted value applies to every draft, a bare version sets a minimum, `<=` sets a maximum, `=` requires an exact version, and comma-separated constraints all apply. Incompatible cases (including `9999`, for future features) are counted as `Skipped`, not failures. The Gradle annotation task always runs all three drafts; select a dialect via the standalone Main arguments. The JUnit runner also verifies accounting by draft: every source Group is either compatible or skipped, and every source assertion is either executed or skipped. After all file tests, it prints per-draft file, Group, test, passed, failed, error and skipped counts. A separate local regression test covers the same external retrieval URI resolving to different annotation schemas in different Groups (the current official Annotation fixtures do not exercise externalSchemas).

For standalone strict execution of compatible annotation cases or a filtered file/group/test, run `OfficialAnnotationMain.main(String[])` as an Application with optional arguments `[dialect [file.json [groupIndex [testIndex]]]]`. The dialect is `all` (default), `draft7`, `draft2019-09`, or `draft2020-12`. For example: `draft2019-09 applicators.json 5 0`. Group/test indices are zero-based integers; compatibility constraints are interpreted against the selected draft. The JUnit annotation suite gates all three drafts; only the standalone Main's positional arguments select its dialect.

### Build and publish

`./gradlew build` uses the **locally cached** official suite and does not download it. If no cache exists (including after `clean`), official test tasks are skipped; download the suite explicitly to enable them.

Published modules depend on `officialPublishGate`, which invokes `downloadOfficialSchemaTests` to check upstream `main`, then requires **full Runtime, Compiled and Annotation official tests** across the supported drafts to pass. The only remaining relevant Gradle property, `-PjsonSchemaSuiteRef`, controls the upstream suite download revision (not which test cases run); publishing requires upstream `main`.

**Incremental compilation:** changing an external schema resource does not necessarily invalidate Java annotation-processing outputs. Track schema inputs in the build or force recompilation when changing referenced schemas.

# Processor Test Matrix

This matrix is intentionally strict: the goal is to find bugs, not to prove that the current suite is "good enough". A feature is treated as covered only when the test directly exercises the relevant generated behavior or compile-time diagnostic.

## Scope

- Runtime usage tests: `sjf4j-jdk17-test/src/test/java/org/sjf4j/jdk17/processor/**`
- Annotation-processor compile/diagnostic tests: `sjf4j-processor/src/test/java/org/sjf4j/processor/**`
- Public processor APIs:
  - `org.sjf4j.annotation.path.*`
  - `org.sjf4j.annotation.mapper.*`
  - `org.sjf4j.annotation.schema.*`

## Status legend

- **Covered**: direct positive and relevant boundary behavior is tested.
- **Partial**: tested only through smoke/integration paths, only positive paths, or only diagnostics.
- **Gap**: no meaningful direct test found.
- **P0**: high-risk bug-finding target; add before relying on the feature.
- **P1**: important coverage gap or contract ambiguity.
- **P2**: useful regression hardening.

## Summary

| Area | Runtime usage coverage | Compile-time diagnostics coverage | Risk |
| --- | --- | --- | --- |
| Path processor | Strong overall; broad coverage of get/put/ensure/if-parent/if-absent across POJO, record, containers, arrays, JsonObject, JsonArray | Strong, but concentrated in a large test class | Medium: boundary and syntax gaps remain |
| Mapper processor | Strongest runtime coverage; create/update, nested, collections, enum, paths, compute, policies | Strong, but annotation-contract diagnostics are incomplete | Medium: options and target-path combinations remain |
| Schema validator processor | Thin; only a few jdk17 runtime scenarios | Moderate diagnostics, but method-shape and option contracts are incomplete | High: largest bug-finding opportunity |

## Path processor matrix

| Feature / contract | Runtime tests | Diagnostics tests | Status | Priority / action |
| --- | --- | --- | --- | --- |
| `@CompiledPath` registry smoke | `PathProcessorTest` in jdk17 test | `PathProcessorTest.generateGetAccessor` | Covered | Keep as smoke only |
| `@GetByPath` POJO/record/getter/public field/boolean getter | `GetByPathTest` | broad generated execution test | Covered | None |
| `@GetByPath` map/list/array/JsonObject/JsonArray | `GetByPathTest` | broad generated execution test | Covered | None |
| Missing reference path returns `null`; primitive missing throws | `GetByPathTest` | broad generated execution test | Covered | None |
| Dynamic params `{idx}` / `{name}` for get | `GetByPathTest` | invalid param diagnostics | Covered | None |
| `@PutByPath` old-value return and void return | `PutByPathTest` | broad generated execution test | Covered | None |
| `@PutByPath` map/list/array/JsonObject/JsonArray | `PutByPathTest` | broad generated execution test | Covered | None |
| Append `[+]` behavior | `PutByPathTest` | invalid append diagnostics | Partial | P1: add `PutIfParentPresent` append and final-return boundary cases |
| `@PutIfParentPresentByPath` skip missing parent | `PutIfParentPresentByPathTest` | diagnostics for primitive missing/annotation conflict | Covered | None |
| `@EnsurePutByPath` creates map/list/POJO/Json parents | `EnsurePutByPathTest` | generated execution and invalid ensure diagnostics | Covered | None |
| `@EnsurePutIfAbsentByPath` absent/null/existing semantics | `EnsurePutIfAbsentByPathTest` | generated execution and invalid ensure diagnostics | Covered | None |
| Dynamic put/ensure indexes and keys | Existing path tests | invalid param diagnostics | Partial | P1: add negative out-of-range, dynamic JsonArray index, and ensure index gap cases |
| JSON Pointer support or explicit rejection in path annotations | Not clearly covered | Not clearly covered | Gap | P1: document and test accepted syntax; suggested `PathProcessorSyntaxDiagnosticsTest` |
| Invalid method shapes and annotation conflicts | Limited jdk17 smoke | `PathProcessorTest` diagnostics | Partial | P2: split diagnostics or add focused cases for easier bug isolation |

## Mapper processor matrix

| Feature / contract | Runtime tests | Diagnostics tests | Status | Priority / action |
| --- | --- | --- | --- | --- |
| `@CompiledMapper` registry smoke | all mapper jdk17 tests | generated compile/execution tests | Covered | None |
| Create mapping to no-args bean | `MapperSimpleTest`, `MapperComplexTest` | generated execution | Covered | None |
| Create mapping to record and constructor target | `MapperSimpleTest`, `MapperComplexTest` | generated execution | Covered | None |
| Update mapping in-place | `MapperUpdateTest`, `MapperCollectionTest` | invalid update diagnostics | Covered | None |
| Rename, ignore, same-name automapping | `MapperSimpleTest`, `MapperUpdateTest` | generated execution | Covered | None |
| Inline compute and `this::helper` compute | `MapperSimpleTest`, `MapperComplexTest` | helper diagnostics | Covered | None |
| Multiple source parameters | `MapperSimpleTest`, `MapperUpdateTest` | ambiguity and unsupported form diagnostics | Covered | None |
| Source JSONPath / JSON Pointer | `MapperSimpleTest` | unsupported wildcard diagnostics | Partial | P2: add more null/missing and primitive-target boundaries if bugs appear |
| Dotted map key remains literal property name | `MapperSimpleTest` | processor dotted-key test | Covered | None |
| Collections/maps root mapping | `MapperCollectionTest` | invalid collection diagnostics | Covered | None |
| Collection/map update policies: array/object/null policies | `MapperCollectionTest` | invalid setter-only policy diagnostics | Covered | None |
| `MapperOptions.listType/setType/mapType` | Not directly found | Not directly found | Gap | P1: add `MapperCustomContainerTypeTest` |
| Nested mapper explicit selection | `MapperCollectionTest`, `MapperNestedAutoTest` | ambiguity diagnostics | Covered | None |
| Auto nested bean/record/constructor mapping | `MapperNestedAutoTest` | ambiguous auto mapper diagnostics | Covered | None |
| Enum/string-to-enum fallback | `MapperNestedAutoTest` | missing enum constant diagnostic | Covered | None |
| Target path strict / ensure / if-parent-present | `MapperTargetPathTest` | target path generated execution and invalid diagnostics | Covered for bean paths | P1: extend to map/list/JsonObject target paths |
| Target path + compute | `MapperTargetPathTest` | generated execution | Covered | None |
| Target path + `nestedMapper` / container policies | Not directly found | Not directly found | Gap | P1: add `MapperTargetPathNestedMapperTest` |
| Annotation parameter conflicts (`ignore` with source/compute, bad compute body, no-op marker) | Not directly found | Limited | Gap | P1: add `MapperAnnotationContractDiagnosticsTest` |

## Schema validator processor matrix

| Feature / contract | Runtime tests | Diagnostics tests | Status | Priority / action |
| --- | --- | --- | --- | --- |
| `@CompiledSchemaValidator` registry smoke | `CompiledSchemaValidatorUsageTest` | `SchemaValidatorProcessorTest.generateSchemaValidatorMethods` | Covered | None |
| Return kinds: boolean, void, `ValidationResult` | `CompiledSchemaValidatorUsageTest` | generated execution, invalid return type, and fallback=false result diagnostic | Covered | None |
| Inline `@ValidJsonSchema.value` | `CompiledSchemaValidatorUsageTest` | processor tests | Covered | None |
| `@ValidJsonSchema.ref` / convention-based schema | Not found | fallback reason indirectly only | Gap | P1: decide contract and test diagnostics/fallback behavior |
| Inherited schema annotation | Not in jdk17 usage | processor test only | Partial | P2: jdk17 usage optional |
| `type`, `required`, `properties`, `additionalProperties=false` | `CompiledSchemaValidatorUsageTest` | processor tests | Partial | P1: add more object/record/bean combinations |
| String constraints: `minLength`, `maxLength`, `pattern` | `CompiledSchemaValidatorFastPathStringNumberTest` | limited | Covered | None |
| Number constraints: min/max/exclusive boundaries | `CompiledSchemaValidatorFastPathStringNumberTest` | limited | Covered | None |
| Array constraints: `items`, `minItems`, `maxItems` | `CompiledSchemaValidatorFastPathStringNumberTest` | processor items test | Covered | None |
| `enum` and `const` | `CompiledSchemaValidatorConstEnumTest` | generator supports these paths | Covered | None |
| Combinators: `allOf`, `anyOf`, `oneOf`, `not` | `CompiledSchemaValidatorUsageTest`, `CompiledSchemaValidatorCombinatorTest` | limited | Covered | None |
| Conditional: `if` / `then` / `else` | `CompiledSchemaValidatorCombinatorTest` | generator supports this path | Covered | None |
| Unsupported fast-path schemas with `fallback=false` | fallback=true runtime in `CompiledSchemaValidatorFallbackOptionsTest` | processor diagnostics for fallback=false | Covered | None |
| `ValidatorOptions.strictFormat` | `CompiledSchemaValidatorFallbackOptionsTest` | Not found | Covered for runtime fallback | P2: add compile-time generated-source sanity only if needed |
| Method-shape diagnostics: no params, multi params, non-POJO, invalid return | Not applicable | `SchemaValidatorProcessorTest.rejectInvalidMethodShapesAndReturnTypes` | Covered | None |

## Prioritized bug-finding backlog

### P0

No remaining P0 items from this pass. Schema `ref` / convention-based schema remains a P1 contract gap.

### P1

1. Add mapper custom container type tests for `MapperOptions.listType/setType/mapType`.
2. Add mapper annotation contract diagnostics for invalid `@Mapping` combinations and unsupported compute forms.
3. Add mapper target-path tests for map/list/JsonObject and `nestedMapper` combinations.
4. Add path syntax diagnostics clarifying JSONPath vs JSON Pointer support for path annotations.
5. Add path boundary runtime tests for negative out-of-range, dynamic JsonArray index, ensure index gaps, and append return semantics.
6. Decide and test schema `@ValidJsonSchema.ref` / convention-based schema contract.

### P2

1. Split very large processor diagnostic test files only if future failures become hard to localize.
2. Add additional `CompiledRegistry` failure-path tests for mapper/schema generated class lookup.
3. Keep generated-source string assertions only for critical hot-path regressions; prefer runtime semantic assertions elsewhere.

## Organization notes

- Keep `sjf4j-jdk17-test` focused on real generated-class behavior through `CompiledRegistry`.
- Keep `sjf4j-processor` focused on javac integration, generated-source sanity checks, and diagnostics.
- Do not count compile-time diagnostics as runtime behavior coverage.
- Do not count broad smoke tests as boundary coverage.
- Prefer small, named test classes over adding more scenarios to already-large processor tests when adding new coverage.

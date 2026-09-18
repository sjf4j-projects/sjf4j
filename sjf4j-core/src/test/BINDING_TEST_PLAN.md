# Binding test plan

## Goal and scope

This plan defines the test suite for `org.sjf4j.binding`: the public streaming
protocol, the generic object-binding engine, the built-in Simple implementation,
and parser backend conformance. It is informed by Jackson databind's separation
of serialization, deserialization, creators, property discovery, containers,
type handling, and failure behavior.

The suite verifies **SJF4J semantics**, not Jackson compatibility. In particular,
it uses SJF4J annotations and makes no requirement to support Jackson features
such as mix-ins, views, default typing, object identity, or coercion flags unless
SJF4J first exposes an equivalent API.

Out of scope: JSON parser grammar and escaping details owned by individual reader
implementations; node metadata discovery owned by `org.sjf4j.node`; facade-native
API behavior owned by `org.sjf4j.facade`. Binding tests consume those layers and
check their binding contract at the boundary.

## Test layers and ownership

| Layer | Primary subject | Assertion style | Backends |
| --- | --- | --- | --- |
| API | `StreamingBinder`, `StreamingReader`, `StreamingWriter`, `StreamingContext` | Protocol and convenience-method behavior | small fake reader/writer plus Simple |
| Engine | `StreamingIO`, `FieldBinder`, `FieldWriter`, `OneOfIO` | Branch-level typed binding behavior and failures | Simple reader/writer |
| Simple | `SimpleJsonBinder` and `SimplePropertiesBinder` | End-to-end implementation behavior | Simple only |
| Portable contract | `JsonBinder` plus reader/writer protocol | Semantics shared by implementations of this API | Simple (currently) |
| Integration | `sjf4j-integration-gson` `GsonJsonBinder` | Its separate public binding implementation | integration-Gson only |
| Legacy facade | `org.sjf4j.facade` API and modes | Facade-specific behavior, not `JsonBinder` conformance | each facade package |

Do not duplicate engine cases in integration or facade tests. A portable case is
added to the core contract only when every core implementation of this API is
required to pass it. A known implementation limitation belongs in that
implementation's test package with a link to the limitation.

## Target layout

Existing tests remain in their production-package mirror. Reusable abstract
contracts and fixtures remain test-only under `org.sjf4j.testsupport` as required
by `src/test/README.md`.

```text
src/test/java/org/sjf4j/
├── testsupport/binding/
│   └── JsonBindingContract.java                 # existing portable binder cases
├── binding/
│   ├── StreamingBinderTest.java                 # planned public entry points
│   ├── StreamingContextTest.java                # planned context defaults/formats
│   ├── StreamingReaderDefaultMethodTest.java    # planned protocol defaults
│   ├── StreamingWriterDefaultMethodTest.java    # planned protocol defaults
│   ├── StreamingIOReadTest.java                 # planned dispatch matrix
│   ├── StreamingIOWriteTest.java                # existing; complete write matrix
│   ├── FieldBinderTest.java                     # planned field/setter reads
│   ├── FieldWriterTest.java                     # planned field/getter writes
│   ├── OneOfIOTest.java                         # planned CURRENT scope and errors
│   ├── CreatorPojoBindingTest.java              # existing; extend creator matrix
│   ├── RecordPojoBindingTest.java                # planned canonical-record creator path
│   ├── future/                                 # reserved; no executable tests yet
│   │   ├── BuilderBindingTest.java
│   │   ├── PolymorphicRegistryTest.java
│   │   ├── ObjectReferenceBindingTest.java
│   │   └── UpdateBindingTest.java
│   └── simple/
│       ├── SimpleJsonBindingTest.java           # existing
│       ├── SimpleJsonBindingContractTest.java   # existing adapter
│       ├── SimpleJsonReaderTest.java            # existing
│       ├── SimpleJsonWriterTest.java            # existing
│       ├── SimpleNodeBinderTest.java            # existing
│       ├── SimplePropertiesBinderTest.java      # existing
│       └── SimpleYamlBinderTest.java            # planned unavailable-placeholder contract
```

`future/` is a reserved design namespace, not a request to add empty directories
or skipped tests. A test moves into the normal binding layout only after its public
API and default/error semantics have been specified.

Do not add a helper or abstract contract merely to make this tree complete. The
existing `JsonBindingContract` keeps its fixtures locally; extract a test-support
fixture only after at least two same-module consumers need it. Core test sources
also must not depend on the integration-Gson module.

## Portable JSON binding contract

Extend `JsonBindingContract` in small, behavior-named methods. It currently
specifies core `JsonBinder` behavior; an implementation outside this module gets
equivalent behavior-named cases in its own module until shared test-fixture support
is deliberately introduced. The contract is divided as follows:

| Priority | Area | Required cases |
| --- | --- | --- |
| P0 | Binder lifecycle | one root value; trailing root value rejected; `String`, UTF-8 `byte[]`, `Reader`/`Writer`, and stream entry points produce equivalent values; null input fails immediately |
| P1 | Reader structure | object/array nesting; `peekToken`/`peekTokenId`; `nextIf*`; `nextName`; `nextNameMatch` known/unknown/end/reordered; `skipNext` for every scalar and nested value |
| P1 | Writer structure | empty and nested object/array; separators; prepared `PropertyName`; all fused property methods; default and overridden `flushTo` behavior |
| P0 | Scalars | null; String including empty/Unicode/control characters; boolean; char; every primitive and boxed numeric type; `BigInteger`, `BigDecimal`, `Number`; enum; `Optional` null behavior |
| P1 | Numeric boundaries | min/max byte/short/int/long; large integer/scale-sensitive decimal; negative zero; overflow; invalid/non-finite values according to the documented JSON backend contract |
| P1 | Untyped values | `Object` creates ordered `Map`, `List`, scalar, and null values; duplicate object key policy is explicitly tested as last-value-wins if retained |
| P0 | Containers | generic and nested `Map<String, T>`, `List<T>`, `Set<T>`; read all primitive arrays; write `boolean[]`, `byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, `double[]`, and object arrays; empty/null containers; null elements/values; insertion ordering where API promises it |
| P1 | Unsupported array shape | `char[]` writes fail with a controlled `BindingException` until SJF4J deliberately adds support; this is not portable successful serialization |
| P0 | POJOs | no-arg field POJO; field/setter/getter property; aliases; unknown member skip; inheritance; `JsonObject` dynamic read/write flags; null inclusion |
| P0 | Creators | `@NodeCreator`, aliases, property order independence, constructor generic parameter, buffered setter property, primitive/default/missing input behavior |
| P1 | Value codecs | root and field codecs for string, boolean, numbers, `Object`, `Map`, and `List`; null bypass; selected default format; codec exception cause preservation |
| P1 | OneOf | JSON-type mapping, discriminator mapping, `CURRENT` scope, fallback null, mismatch failure; parent discriminator before and after the field |
| P0 | Binder errors | convenience methods throw `BindingException`, retain a cause, and identify the target type; Simple-reader paths include object property and array index |

For contract results, compare parsed structure rather than JSON text unless exact
serialization is part of the API. This permits insignificant backend formatting
differences but catches semantic drift.

## Engine tests (P0/P1)

These are focused tests with Simple reader/writer to make each `StreamingIO`
branch observable. They complement, rather than replace, the portable contract.

### `StreamingIOReadTest` (P0)

1. Token-to-target dispatch table: object, array, string, number, boolean, and
   null against every accepted target category.
2. `Object` raw recursion: nested object/array, order, duplicate keys, and null.
3. Map/List/Set concrete implementations, nested generic resolution, and container
   construction failure. Test null separately from empty.
4. All eight primitive array paths, capacity growth (>8 and >16 elements), empty
   array, exact-size shrink, object array, and invalid/null primitive element.
5. `JsonObject`, `JsonArray`, and typed `JsonArray` subclass paths.
6. Scalar mismatch failures: object/array/scalar mismatch, empty char, enum name,
   numeric overflow, and unsupported target type.
7. `readValueWithCodec` accepted raw types and each unsupported raw type. The latter
   is a deliberate negative contract until codec raw-type support is expanded.

### `FieldBinderTest` and `FieldWriterTest` (P1)

Test public/private field access, getter-only and setter-only properties, all
primitive values, reference null, generic-dependent field resolution, aliases,
field-level codec, and field-level `OneOf`. Add failing getter/setter/constructor
fixtures to assert the operation and original cause are retained. Include inherited
members, field/getter/setter name/type conflicts, and static/synthetic exclusion
according to the property-discovery contract in `org.sjf4j.node`.

### `StreamingIOWriteTest` (P0/P1)

Complete the existing write suite with the mirror of the accepted root-value
categories: scalars, codecs, POJOs, maps, lists, sets, `JsonObject`/`JsonArray`,
object arrays, and each primitive-array branch. Pin the currently special `char[]`
case rather than treating it as covered by the read matrix. Exercise RandomAccess
and iterator-backed lists, stable set iteration, null omission for map/POJO/dynamic
properties, and controlled failures for null map keys or unsupported root types.

### `CreatorPojoBindingTest` and `OneOfIOTest` (P0/P1)

Creator cases cover no-arg vs annotated creator selection, parameter aliases,
JSON property order, missing values, duplicate creator property, primitive null,
constructor exception, and creator property plus later setter assignment.

`OneOfIOTest` covers no mapping, ambiguous mapping if representable, bad JSON type,
discriminator path/key behavior, fallback null consuming the entire value, and
subsequent-property recovery. It distinguishes direct `OneOfIO` (which rejects
`PARENT`) from `StreamingIO.readPojo` PARENT handling. The latter pins its actual
limits separately: a path discriminator is rejected; different parent keys are
rejected; more than one deferred raw field is rejected; and multiple fields may be
read when a shared discriminator has already appeared. Do not accidentally narrow
the last case.

### `StreamingBinderTest`, reader/writer contracts, and context (P1)

Use minimal recording/failing reader and writer implementations to test default
methods independently from any JSON parser: UTF-8 conversion, `flush` then
`flushTo`, null argument checks, exception wrapping, default reader end-of-document
check, boxed reader/writer methods, prepared names, and fused property methods.
Reader-protocol failures are asserted as `IOException` (or the documented
backend-native failure); only binder convenience methods promise the
`BindingException` wrapper. Resource ownership is a prerequisite API decision:
caller-supplied Reader/Writer/InputStream/OutputStream should remain caller-owned,
while String/byte convenience methods close their internally created reader. Add
close assertions only after this rule is documented. `StreamingContextTest` pins
`EMPTY` defaults, `includeNulls`, node binder forwarding, and value-format
selection.

## Simple implementation tests (P0/P1)

Keep parser grammar/escaping and raw node conversion in `binding.simple`, not in
the portable contract. Complete these cases:

| Subject | Additional cases |
| --- | --- |
| `SimpleJsonBinder` | all input/output overloads, close/flush behavior, trailing document, wrapper/cause shape |
| `SimpleJsonReader` | whitespace, nested skip, name matching, token id, malformed separator/bracket/string/escape/number, integer overflow, decimal precision, surrogate pairs, error path |
| `SimpleJsonWriter` | escaping, all scalar methods, non-finite numbers, separator state, invalid call order, prepared/fused names, flush to writer/stream |
| `SimpleNodeBinder` | node ↔ POJO/Map/container conversion, deep-copy isolation, generic/inherited metadata, creator and codec dispatch, unsupported conversion failures |
| `SimplePropertiesBinder` | round trip for scalar nested objects and contiguous arrays; escaped/path-like names; replacement/preservation rules; all documented losses/rejections (non-object root, sparse array, null array member, scalar/container conflict, unrepresentable empty container) |
| `SimpleYamlBinder` | both reader and writer factory calls fail with `BindingException` and a message that declares YAML unavailable; do not silently test it as a JSON binder |

## Integration and facade coverage (P1)

The core `JsonBinder` contract currently applies to `SimpleJsonBinder`. The
separate `sjf4j-integration-gson` module exposes another `JsonBinder`; keep its
tests under `sjf4j-integration-gson/src/test/java/org/sjf4j/integration/gson/binding`
and give it equivalent behavior-named cases without making core test sources depend
on that module. Extract cross-module test fixtures only if the duplication becomes
material and the build deliberately adds test-fixture support.

Jackson 2/3, Gson, Fastjson2, and JSON-P under `org.sjf4j.facade` use the legacy
facade/streaming API, not `org.sjf4j.binding.JsonBinder`. Their tests remain in the
matching `org.sjf4j.facade.<backend>` packages and retain their own mode matrix.
They are not adapters to this binding contract.

For either implementation family, narrowly scoped backend deltas must be explicit
rather than silently excluded:

* native `Number` class returned for untyped JSON numbers;
* rejection/representation of non-finite floating point values;
* duplicate property and trailing-document behavior;
* exact reader error path/message where the native parser cannot expose the same
  detail;
* reader/writer `NameMatcher`/`PropertyName` fast-path equivalence;
* absent optional dependency and factory failure behavior.

YAML belongs in a separate `YamlBindingContract` only after the SnakeYAML facade
declares which YAML-specific semantics are portable. It must not be forced through
the JSON parser contract.

## Deferred capability suites

These Jackson-inspired areas are valuable but have no current SJF4J binding API.
Their proposed directories document the future boundary; do not add `@Disabled`
tests before a design decision.

| Reserved suite | Prerequisite design decision | Minimum eventual coverage |
| --- | --- | --- |
| `future/BuilderBindingTest` | builder discovery annotation/configuration | builder method naming, build failure, immutable collection fields |
| `future/PolymorphicRegistryTest` | explicit safe subtype registration API | type tag format, allowed/unknown subtype, tag collision, security rejection |
| `future/UpdateBindingTest` | update/merge API and null policy | existing instance mutation, map/list merge vs replace, null/delete behavior |
| `future/ObjectReferenceBindingTest` | reference/identity model | cycles, forward reference, unresolved reference, depth/resource limit |

Records are not deferred: existing node metadata recognizes their canonical
constructor, so `RecordPojoBindingTest` belongs with the current creator path.
Cover canonical construction, component aliases where supported, missing/null
components, and generic/nested records. No suite is planned for Jackson mix-ins,
views, default typing, or arbitrary coercion configuration until SJF4J deliberately
adopts an equivalent feature.

`PropertiesBinder` currently has one implementation, `SimplePropertiesBinder`.
Keep its behavior in `SimplePropertiesBinderTest`; extract a portable Properties
contract only when a second implementation needs it.

## Execution order and quality gates

1. Stabilize and broaden the core portable P0 contract for Simple; add equivalent
   integration-Gson cases in its own module.
2. Add `StreamingIOReadTest`, creator, OneOf, and codec negative paths; these have
   the greatest untested branch density.
3. Add public default-method and field accessor failure tests.
4. Complete Simple and Properties edge suites, then backend deltas.
5. Add a new future suite only with its production API specification.

Every new bug must first receive the smallest reproducing test in its ownership
layer, then be promoted to the portable contract only if all backends are expected
to share the behavior. Run `./gradlew :sjf4j-core:test` during implementation;
before a commit also run the repository-required `:sjf4j:test` and
`:sjf4j-testbench:test`, preserve at least 75% branch coverage, and benchmark a
touched hot path where a test exposes a performance-sensitive change.

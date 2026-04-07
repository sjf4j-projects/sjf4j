# Changelog

All notable changes to **SJF4J (Simple JSON Facade for Java)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]
### Added
- Added Jackson 3 facade integration with runtime auto-detection, global config entry points, Jackson3 `JsonNode` support, and JDK 17 coverage/JMH evaluation.
- Added container metadata and factory paths in `NodeRegistry` for concrete `Map`/`List`/`Set` target types.
- Added `Sjf4jConfig.PlainPojoFieldAccess` with global bean-only and field-based switches for plain-POJO binding.

### Improved
- Improved `Nodes.to(...)`, `NodeFacade.readNode(...)`, and streaming IO binding so concrete `Map`/`List`/`Set` targets are created with their declared container implementations when supported.
- Improved `Nodes.copy(...)` and `deepNode(...)` to preserve concrete container types when possible and fall back to default mutable containers only on unsupported source implementations.
- Improved streaming `AnyOf` binding by caching container element/value `AnyOf` metadata on `FieldInfo` and avoiding redundant runtime `TypeInfo` lookups on hot read paths.
- Improved plain-POJO binding contracts with explicit `BEAN_ONLY` / `FIELD_BASED` global policy, backend-aligned plugin-module fallback rules, and consistent handling across Jackson, Jackson 3, Gson, and Fastjson2.
- Improved plain-POJO fallback rules so default binding stays bean-oriented, `@NodeProperty` is the only field-level force-bind signal, and record component accessors continue to work under `BEAN_ONLY`.
- Improved JSONPath/JSON Pointer handling so numeric pointer tokens preserve object-key semantics, filter strings unescape consistently, regex flags parse more strictly, and `&&` / `||` short-circuit during filter evaluation.

### Changed
- Changed `@AnyOf.Scope.SELF` to `CURRENT` for discriminator lookup naming.

### Fixed
- Fixed binding consistency for concrete container fields and root targets across shared and exclusive streaming backends.
- Fixed transient-field precedence so transient members are always ignored first, and transient fields annotated with `@NodeProperty` now fail fast during metadata analysis.
- Fixed backend contract drift in tests and plugin-module routing for plain POJOs, including Gson/Fastjson2 private-field behavior under `BEAN_ONLY` and `FIELD_BASED`.
- Fixed Jackson/Jackson3 module installation so existing mapper annotation introspectors remain active alongside SJF4J `@NodeProperty` support.
- Fixed JSON Pointer parsing to reject invalid `~` escapes, preserve leading-zero numeric tokens, and route numeric pointer segments to object keys when the runtime container is object-shaped.
- Fixed `stddev()` to return standard deviation instead of variance, and reject terminal descendant paths like `$..` during compile time.


## [1.1.6] - 2026.04.02
### Added
- Added `JsonPath.compileCached(String)` to provide an explicit cached compile path.
- Added `PathCache` interface with built-in `ConcurrentHashMap` as default implementation.
- Added `JsonPath.compute(...)` overloads to update every matched target from its current content.
- Added `NodeConverter`, `NodeMapper`, and `NodeMapperBuilder` for path-driven object graph mapping.
- Added `NamingStrategy` and `@NodeNaming` to map JSON property names like `snake_case` to Java fields without per-field annotations.

### Improved
- Improved node conversion extensibility so exact source/target converters can participate in nested POJO and collection binding flows.

### Changed
- Changed `new JsonArray(...)`/`new JsonObject(...)` to `JsonArray.of(...)` and `JsonObject.of(...)` for simpler literal-style container creation.
- Changed `SchemaValidator` to validate `@ValidJsonSchema` along class inheritance chains (parent and child annotations both applied).

### Fixed
- Fixed inherited POJO field resolution when parent and child define the same key, ensuring child field metadata is preserved and parent metadata does not overwrite it.



## [1.1.5] - 2026.03.11
### Added
- Added `JSON-P` facade integration with runtime auto-detection in `FacadeFactory`.
- Added `AnyOf` resolution support in `StreamingFacade` and `NodeFacade`.

### Improved
- Improved streaming read performance with an array container-kind fast path.
- Improved node conversion internals and reduced duplicated conversion paths in `SimpleNodeFacade`.
- Improved consistency of constructor discovery and AnyOf handling across streaming and node conversion pipelines.
### Changed
- Changed `NodeFacade` conversion contract to support `readNode(node, type, deepCopy)` and route deep copy through a unified path.
- Changed `Nodes.toPojo(...)` to delegate POJO materialization to `NodeFacade` for centralized conversion behavior.
- Changed exception mapping to use more unified and compact binding/conversion error messages.
### Fixed
- Fixed several semantic consistency issues across node/path/patch behavior.
- Fixed edge-case conversion mismatches in POJO and dynamic-field binding flows.



## [1.1.4] - 2026.02.24

### Added
- Added `@ValidJsonSchema` and `SchemaValidator` for schema-based validation.
- Added `FacadeNodes` with backend adapters `JacksonNodes` and `GsonNodes`.
- Added/expanded tests for schema/path/facade/JDK17 scenarios.

### Improved
- Optimized JSON Schema validation hot paths and compile/store flow.
- Improved consistency of streaming IO behavior across facades.
- Improved JsonPath internals by moving token model to segment model.
- Improved cross-module behavior consistency across node/path/patch operations.

### Changed
- Renamed `NodeType` -> `NodeKind`.
- Renamed `PathToken` -> `PathSegment`.
- Renamed `PathUtil` -> `Paths`.
- Renamed `PatchUtil` -> `Patches`.
- Renamed annotations:
    - `@Decode` -> `@RawToValue`
    - `@Encode` -> `@ValueToRaw`
    - `@Copy` -> `@ValueCopy`

### Fixed
- Fixed multiple node/path/patch semantic alignment issues.
- Fixed schema loading/compilation edge cases and validation result handling.
- Fixed backend-specific streaming conversion consistency issues.


## [1.1.3] - 2026.02.04
### Added
- Added `skipNode()` method to `StreamingReader` interface, enabling efficient skipping of entire JSON nodes 
  during streaming parsing without fully deserializing them.
- Added `@NodeCreator` annotation to support ***custom object construction with parameterized constructors***. 

### Improved
- Enhanced POJO deserialization to ***automatically support Java records*** (JDK 14+). Record classes are now 
  recognized and handled seamlessly, with their canonical constructors used for instantiation without requiring 
  explicit `@NodeCreator` annotations.
- Improved constructor parameter name resolution for `@NodeCreator` annotated constructors, supporting both 
  `@NodeProperty` explicit naming and automatic parameter name detection (when compiled with `-parameters` flag).

## [1.1.1] - 2026.01.26
### Fixed
- Make `SchemaStore.register` public


## [1.1.0] - 2026.01.25
### Added
- Introduced `JsonSchema` module for ***JSON Schema validation*** (see: [json-schema.org](https://json-schema.org/)).
  Fully implements **JSON Schema Draft 2020-12**, with all official test cases passing.
- Added `@NodeField` annotation to allow ***custom mapping between POJO fields and node/property names***.

### Improved
- Unified and optimized `asMap`, `asList`, and `asArray` APIs across `Node`, 
  `JsonObject`, and `JsonPath` for more consistent structural access.

### Changed
- Renamed: `NodeUtil` to `Nodes`
- Renamed: `FunctionRegistry` to `PathFunctionRegistry`
- Renamed: `@Convertible` to `@NodeValue`

### Fixed
- `@NodeValue` now correctly supports ***annotation overrides in subclasses***, 
  even when the annotation is declared on a superclass.
- Fixed incorrect detection logic for ***missing no-argument constructors in POJO binding*** scenarios.


## [1.0.3] - 2026.01.04
### Improved
- Differentiated the semantics of `equals()` and `nodeEquals()`
- Clarified the distinction between `toNode()` and `deepNode()`
- Improved the output format of `inspect()`

### Changed
- Minor renaming and alignment of core API method names

### Fixed
- Benchmark issues


## [1.0.2] - 2025.12.25
### Added
 - Added support for **JSON Patch (RFC 6902)** via the `JsonPatch` API.
 - Added the `JsonPointer` class, providing an API consistent with `JsonPath` while exclusively 
 supporting **JSON Pointer (RFC 6901)** expressions.
 - Introduced the `@Convertible` annotation and the `NodeRegistry` class to enable a **pluggable custom type conversion mechanism**.
 - Extended **JsonPath filter expressions** with the `=~` operator, providing full regular expression matching support.
 - Added native support for `enum` types.

### Improved
 - Optimized `JsonPath` evaluation performance
 - Improved overall conversion and traversal efficiency

## [1.0.1] - 2025.12.15
### Added
 - A simple build-in JSON reader/writer
 - SJF4J now Fully Supports JSONPath. 
   - Added support for `Filter` and `Function`, including the `eval()` methods.
   - Added support for registering custom functions via `FunctionRegistry`.

--- 
## [1.0.0] - 2025.12.05

### Added
 - All this project.

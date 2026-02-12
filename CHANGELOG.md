# Changelog

All notable changes to **SJF4J (Simple JSON Facade for Java)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [Unreleased]
### Added
- Added support for Jackson `JsonNode` and Gson `JsonElement` in `Nodes`, 
  via `FacadeNodes`, `JacksonNodes`, and `GsonNodes`.
### Improved
- Improve `JsonPath` performance by replacing `PathToken` to `PathSegment`.
- Enhanced error reporting with precise location information during path parsing.
### Changed
- Renamed `NodeType` to `NodeKind` for clearer semantics.
- Removed `TypedNode`; typed access now uses `Nodes.Access`

### Fixed
### Removed

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


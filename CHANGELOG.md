# Changelog

All notable changes to **SJF4J (Simple JSON Facade for Java)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- JSON Schema

### Improved
- `JsonPatch.applyCopy(Class<?> clazz)`

### Changed
### Fixed
- POJO analyze

### Removed


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


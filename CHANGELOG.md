# Changelog

All notable changes to **SJF4J (Simple JSON Facade for Java)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
 - Introduced `@Convertible` annotation and `Converter` interface to support a pluggable custom conversion mechanism
 - Added native support for `enum` types
 - Added support for `JsonPatch` (RFC 6902)
 - Added class `JsonPointer`, with the same API as `JsonPath` but only accepts JSON Pointer (RFC 6901) expressions.

### Improved
 - Optimized `JsonPath` evaluation performance
 - Improved overall conversion and traversal efficiency

### Changed
### Fixed
### Removed

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


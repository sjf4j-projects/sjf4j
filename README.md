# SJF4J — Simple JSON Facade for Java

![License](https://img.shields.io/github/license/sjf4j-projects/sjf4j)
[![Maven Central](https://img.shields.io/maven-central/v/org.sjf4j/sjf4j)](https://central.sonatype.com/search?q=sjf4j)
[![javadoc](https://javadoc.io/badge2/org.sjf4j/sjf4j/javadoc.svg)](https://javadoc.io/doc/org.sjf4j/sjf4j)
![Supported Dialects](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fsupported_versions.json)  
![Draft 2020-12](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2020-12.json)
![Draft 2019-09](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2019-09.json)
![Draft 7](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft7.json)    
![Build](https://img.shields.io/github/actions/workflow/status/sjf4j-projects/sjf4j/gradle.yml?branch=main)
[![codecov](https://codecov.io/gh/sjf4j-projects/sjf4j/graph/badge.svg?branch=main)](https://codecov.io/gh/sjf4j-projects/sjf4j)
![Stars](https://img.shields.io/github/stars/sjf4j-projects/sjf4j?style=social)


SJF4J is foremost **a simple JSON facade for Java**, 
evolving into **a unified structural processing layer built on JSON semantics**.

It brings JSON semantics directly to Java objects, combining unified structural processing, 
multiple serialization backends, and compile-time code generation.

## Quickstart

**Add SJF4J to your project (JDK 8+):**
```gradle
implementation("org.sjf4j:sjf4j:{version}")
```

**Define your Java objects:**
```java
public class User {
    public String name;
    public int age;
    public Address address;
}

public class Address {
    public String city;
}
```

**Work directly with Java objects using runtime APIs:**
```java
Sjf4j sjf4j = new Sjf4j();

// Bind JSON to a POJO
User user = sjf4j.fromJson(json, User.class);

// Navigate and modify using JSONPath
JsonPath.parse("$.address.city").put(user, "Shanghai");

// Apply JSON Patch
JsonPatch.fromJson(patchJson).apply(user);

// Validate with JSON Schema
JsonSchema.fromJson(schemaJson).createPlan().validate(user);

// Transform to another POJO
AnotherUser another = sjf4j.convert(user, AnotherUser.class);

// Serialize to JSON
String output = sjf4j.toJsonString(another);
```

**Accelerate with compile-time code generation, minimizing reflection, dynamic dispatch, and runtime interpretation:**

```java
@CompiledBinder
interface UserBinder {
    @Read User read(String json);
    @Write String write(User user);
}

UserBinder binder = CompiledInstances.create(UserBinder.class);
User user = binder.read(json);
```


**Core capabilities:**

| Capability | Runtime | Compile-time |
| --- | --- | --- |
| OBNT Operations | `Nodes` | — |
| Data Binding | `Sjf4j.fromXxx()`, `toXxx()` | `@CompiledBinder` |
| JSONPath Navigation | `JsonPath` | `@CompiledNavigator` |
| JSON Schema Validation | `JsonSchema` | `@CompiledValidator` |
| JSON Patch & Merge Patch | `JsonPatch` | — |
| Object Mapping | `Sjf4j.convert()` | `@CompiledMapper` |
| JDBC Mapping | — | `@CompiledJdbcMapper` |


## Why SJF4J?

Java already has excellent JSON libraries. SJF4J builds on them, adding a common programming model for structured Java data rather than introducing another mandatory JSON tree.

- **JSON semantics on Java objects.** Apply [JSONPath (RFC 9535)](https://www.rfc-editor.org/info/rfc9535/), [JSON Pointer (RFC 6901)](https://www.rfc-editor.org/info/rfc6901/), [JSON Patch (RFC 6902)](https://www.rfc-editor.org/info/rfc6902/), and [JSON Schema](https://json-schema.org/) directly to POJOs, Maps, Lists, and other supported nodes—without first converting them into a separate tree.
- **Multiple backends, one API.** Use [Jackson 2/3](https://github.com/FasterXML/jackson), [Gson](https://github.com/google/gson), [Fastjson2](https://github.com/alibaba/fastjson2), [JSON-P](https://github.com/jakartaee/jsonp-api), YAML via [SnakeYAML](https://github.com/snakeyaml/snakeyaml), or built-in Java Properties support.
- **Runtime flexibility, compiled performance.** Start with runtime APIs, then generate type-specific binding, navigation, validation, mapping, and JDBC mapping code for performance-sensitive paths.

## Core Capabilities

### Modeling — Object-Based Node Tree (OBNT)

OBNT treats existing Java objects as structural nodes instead of requiring a dedicated JSON tree.

| Node kind | Java representations |
| --- | --- |
| Object | POJO, `Map`, `JsonObject`, JOJO |
| Array | `List`, Java array, `JsonArray`, JAJO |
| Value | `String`, `Number`, `Boolean`, `null`, custom value types |

Use `Nodes` to work with these structures consistently. For extensibility, **JOJO** (`JsonObject` subclass) combines typed properties with dynamic members; **JAJO** (`JsonArray` subclass) supports an extensible array model. `@NodeValue`, `@OneOf`, and `ExternalNode` provide custom scalar, polymorphic, and external-node integration.

[Modeling documentation](https://sjf4j.org/docs/modeling)

### Binding — JSON, YAML, and Properties

Use the same Java model across formats and supported parser backends.

```java
Sjf4j sjf4j = new Sjf4j();
User user = sjf4j.fromJson(json, User.class);
String yaml = sjf4j.toYamlString(user);
```

Runtime binding supports POJOs, collections, generic types, and registered value codecs. For hot paths, `@CompiledBinder` generates specialized readers and writers at compile time.

[Binding documentation](https://sjf4j.org/docs/binding)

### Navigating — JSONPath and JSON Pointer

Read and modify object graphs using standard paths, including filters and recursive descent.

```java
JsonPath.parse("$.address.city").put(user, "Shanghai");
String city = JsonPath.parse("/address/city").getString(user);
```

In addition to `get` and `find`, paths support structural mutations such as `add`, `remove`, and `ensurePut`. `@CompiledNavigator` generates direct Java navigation code for known paths.

[Navigating documentation](https://sjf4j.org/docs/navigating)

### Patching — JSON Patch and Merge Patch

Apply [JSON Patch (RFC 6902)](https://www.rfc-editor.org/info/rfc6902/) operations directly to Java objects.

```java
JsonPatch patch = JsonPatch.fromJson(
    "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Alice Zhang\"}]");
patch.apply(user);  // Updates user in place
```

SJF4J also supports [JSON Merge Patch (RFC 7386)](https://www.rfc-editor.org/info/rfc7386/) and Indexed Merge Patch for partial array updates.

[Patching documentation](https://sjf4j.org/docs/patching)

### Validating — JSON Schema

Validate POJOs and other OBNT nodes directly against JSON Schema, without reserializing them. Runtime validation supports Draft 2020-12, 2019-09, and Draft 7.

```java
JsonSchema schema = JsonSchema.fromJson(
    "{\"type\":\"object\",\"required\":[\"name\"]}");
SchemaPlan plan = schema.createPlan();
ValidationResult result = plan.validate(user);
```

Reuse `SchemaPlan` for repeated validation. `@CompiledValidator` generates schema-specific checks at compile time; unsupported direct-compilation features can explicitly fall back to runtime validation. `@ValidJsonSchema` integrates JSON Schema with Bean Validation.

[Validation documentation](https://sjf4j.org/docs/validating) · [Compiled validator details](docs/compiled-validator.md)

### Mapping — Objects and JDBC Results

Convert compatible object properties at runtime or generate richer transformations at compile time.

```java
AnotherUser another = sjf4j.convert(user, AnotherUser.class);
```

`@CompiledMapper` supports property renaming, expressions, nested mapping, and object creation. `@CompiledJdbcMapper` maps JDBC `ResultSet` rows to Java objects using generated code.

[Mapping documentation](https://sjf4j.org/docs/mapping)

## Performance and Compatibility

SJF4J uses JMH to benchmark runtime and compiled binding, navigation, validation, and mapping against relevant alternatives. See [Benchmarks](https://sjf4j.org/docs/benchmarks) and the [versioned JSON Schema results](docs/performance/schema) for methodology and reproducible measurements.

JSON Schema compatibility is also tracked by [Bowtie](https://bowtie.report/); JSONPath behavior can be compared with the [JSONPath Comparison Project](https://cburgmer.github.io/json-path-comparison/).

## Setup Notes

- **Java:** JDK 8+ for the runtime. Examples using Java text blocks require JDK 15+.
- **Backends:** Add a preferred JSON parser to the classpath; JSON supports a built-in simple fallback. YAML requires SnakeYAML.
- **JSON Schema:** Add `org.sjf4j:sjf4j-schema:{version}` for schema validation.
- **Compile-time generation:** Add `org.sjf4j:sjf4j-processor:{version}` as an annotation processor dependency.

```gradle
implementation("org.sjf4j:sjf4j:{version}")
implementation("org.sjf4j:sjf4j-schema:{version}")
annotationProcessor("org.sjf4j:sjf4j-processor:{version}")
```

## Contributing

Issues, bug reports, documentation improvements, benchmarks, and pull requests are welcome. [Open an issue](https://github.com/sjf4j-projects/sjf4j/issues/new) to report unexpected behavior or propose an improvement.

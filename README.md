
![License](https://img.shields.io/github/license/sjf4j-projects/sjf4j)
[![Maven Central](https://img.shields.io/maven-central/v/org.sjf4j/sjf4j)](https://central.sonatype.com/search?q=sjf4j)
[![javadoc](https://javadoc.io/badge2/org.sjf4j/sjf4j-core/javadoc.svg)](https://javadoc.io/doc/org.sjf4j/sjf4j-core)
![Supported Dialects](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fsupported_versions.json)  
![Draft 2020-12](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2020-12.json)
![Draft 2019-09](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2019-09.json)
![Draft 7](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft7.json)    
![Build](https://img.shields.io/github/actions/workflow/status/sjf4j-projects/sjf4j/gradle.yml?branch=main)
[![codecov](https://codecov.io/gh/sjf4j-projects/sjf4j/graph/badge.svg?branch=main)](https://codecov.io/gh/sjf4j-projects/sjf4j)
![Stars](https://img.shields.io/github/stars/sjf4j-projects/sjf4j?style=social)

**SJF4J (Simple JSON Facade for Java)** provides **a unified structural processing layer built on JSON semantics**, 
bringing data binding, navigation, validation, mapping, and patching together.

- **JSON Semantics, Native Java Objects**   
  Apply [JSONPath](https://www.rfc-editor.org/info/rfc9535/), [JSON Pointer](https://www.rfc-editor.org/info/rfc6901/),
  [JSON Patch](https://www.rfc-editor.org/info/rfc6902/), and [JSON Schema](https://json-schema.org/) directly to POJOs,
  Maps, Lists, and domain models — no intermediate JSON trees required.


- **Multiple Backends, One API**  
  Work seamlessly across JSON backends ([Jackson 2.x/3.x](https://github.com/FasterXML/jackson-databind),
  [Gson](https://github.com/google/gson), [Fastjson2](https://github.com/alibaba/fastjson2), [JSON-P](https://github.com/jakartaee/jsonp-api)), YAML ([SnakeYAML](https://github.com/snakeyaml/snakeyaml)),
  and other formats — all through a unified API.


- **Runtime Flexibility, Compiled Speed**  
  Enjoy flexible runtime APIs and highly optimized compile-time code generation.



## Quickstart

**Add SJF4J to your project (JDK 8+):**
```gradle
implementation("org.sjf4j:sjf4j:{version}")     // All-in-one
```

**Start with an ordinary Java class:**
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


**Work directly with these objects using JSON-oriented APIs:**
```java
String json = "{\"name\":\"Alice\",\"age\":18,\"address\":{\"city\":\"Beijing\"}}";

// JSON → POJO
Sjf4j sjf4j = new Sjf4j();
User user = sjf4j.fromJson(json, User.class);

// Modify the POJO directly using JSONPath
JsonPath.parse("$.address.city").put(user, "Chongqing");

// POJO → JSON
String output = sjf4j.toJsonString(user);
```

No special base classes, annotations, or intermediate JSON trees are required.


## Explore Features

The following table provides an overview of SJF4J's capabilities
and their corresponding Runtime and Compile-time APIs.

| Feature | Capability | Runtime | Compile-time |
| --- | --- | --- | --- |
| Modeling | Structural Operations | `Nodes` | — |
| Binding | Data Binding | `Sjf4j.from/toXxx()` | `@CompiledBinder` |
| Navigating | JSONPath Navigation | `JsonPath` | `@CompiledNavigator` |
| Validating | JSON Schema Validation | `JsonSchema` | `@CompiledValidator` |
| Mapping | Object Mapping | `Sjf4j.convert()` | `@CompiledMapper` |
| — | JDBC Mapping | — | `@CompiledJdbcMapper` |
| Patching | JSON Patch & Merge Patch | `JsonPatch` | — |



### Modeling

**OBNT (Object Based Node Tree)** provides a unified structural model for native Java objects, without requiring a dedicated JSON tree representation.

OBNT defines three kinds of nodes:

| Node kind| Java representations |
| --- | --- |
| **Object node** | `Map`, POJOs, `JsonObject`, JOJOs |
| **Array node** | `List`, Java arrays, `JsonArray`, JAJOs |
| **Value node** | Strings, numbers, booleans, and custom value types |

Use `Nodes` to work with these structures through a consistent API.
```java
// Access and convert property values
Object raw = Nodes.getInObject(user, "name");
String name = Nodes.toString(raw);

// Iterate over object properties
Nodes.forEachObject(user, (key, value) -> 
        System.out.println(key + ": " + value));

// Compare objects by JSON semantics
if (!Nodes.equals(user, userMap)) {
    // Inspect object structure
    System.out.println("user: " + Nodes.inspect(user));   
}
```

OBNT also supports extensible object models:

- Extend `JsonObject` to create a **JOJO (JSON Object Java Object)**, combining typed properties with dynamic members in a hybrid object model.
- Extend `JsonArray` to create a **JAJO (JSON Array Java Object)**, a typed, extensible array node.
- Use `@NodeValue` to model a Java type as a logical value node.
- Use `@OneOf` to model polymorphic node types.
- Register an `ExternalNode` to integrate your own structural types into OBNT.

Learn more → [Modeling (OBNT)](https://sjf4j.org/docs/modeling)


### Binding

SJF4J provides unified data binding across [JSON](https://www.rfc-editor.org/info/rfc8259), [YAML](https://yaml.org/spec/1.2.2/), and [Java Properties](https://docs.oracle.com/javase/8/docs/api/java/util/Properties.html), supporting POJOs, Maps, Lists, generic types, and custom value types.

**Runtime**
```java
Sjf4j sjf4j = new Sjf4j();

User user = sjf4j.fromJson(json, User.class);
String yaml = sjf4j.toYamlString(user);
```

**Compiled**
```java
@CompiledBinder
interface UserJsonBinder {
    @Read User read(String json);
}

@CompiledBinder(format = Format.YAML, backend = Backend.SNAKE)
interface UserYamlBinder {
    @Write String write(User user);
}
```

SJF4J's open streaming architecture supports custom data formats and backends.  
Its rich binding capabilities include `@OneOf` polymorphism, multi-format `ValueCodec` support,
and fully managed `ExternalNode` integration.

Learn more → [Binding (Formats)](https://sjf4j.org/docs/binding)


### Navigating

Query and modify Java object graphs using [JSONPath (RFC 9535)](https://www.rfc-editor.org/info/rfc9535/)
or [JSON Pointer (RFC 6901)](https://www.rfc-editor.org/info/rfc6901/).

**Runtime**
```java
// Query with recursive descent and filtering (JOJO)
List<String> names = student.findByPath("$..friends[?@.scores.math >= 90].name");

// Modify a POJO via JSON Pointer
JsonPath.parse("/address/city").put(user, "Shanghai");
```

**Compiled**
```java
@CompiledNavigator
interface UserNavigator {
    @GetByPath("$.address.city")
    String getCity(User user);

    @PutByPath("$.address.city")
    String putCity(User user, String city);
}
```

SJF4J combines standards-compliant JSONPath querying with powerful structural navigation and mutation, operating directly on OBNT without intermediate JSON trees.

Learn more → [Navigating (JSONPath)](https://sjf4j.org/docs/navigating)


### Validating

Validate Java objects directly against [JSON Schema](https://json-schema.org/),
without intermediate JSON tree construction.

**Runtime**
```java
JsonSchema schema = JsonSchema.fromJson("""
{
  "type": "object",
  "properties": {
    "name": {"type": "string", "minLength": 1},
    "age": {"type": "integer", "minimum": 0}
  },
  "required": ["name"]
}
""");

SchemaPlan plan = schema.createPlan();
ValidationResult result = plan.validate(user);
```

**Compiled**
```java
@CompiledValidator
interface UserValidator {
    @ValidJsonSchema(ref = "classpath:/schemas/user.json")
    boolean isValid(User user);
}
```

Supports JSON Schema Draft **2020-12**, **2019-09**, and **7**,
including full validation and annotation collection.

*Experimental: JSON Schema can also be used to generate Java models
through the online [Schema-to-Java Generator](https://sjf4j.org/generator).*

Learn more → [Validating (JSON Schema)](https://sjf4j.org/docs/validating)


### Mapping

Convert between Maps, POJOs, values, nested object structures, and other OBNT nodes.

**Runtime**
```java
Map<String, Object> data = new HashMap<>();
data.put("name", "Alice");
data.put("age", 18);
data.put("address", Map.of("city", "Shanghai"));

User user = sjf4j.convert(data, User.class);
```

**Compiled**

`@CompiledMapper` supports property renaming, nested mapping,
custom expressions and creation strategies.
```java
@CompiledMapper
interface UserMapper {
    @Mapping(target = "fullName", source = "name")
    AnotherUser toAnother(User user);
}
```

For JDBC results, `@CompiledJdbcMapper` generates direct mappings from `ResultSet` to Java objects:
```java
@CompiledJdbcMapper
interface UserJdbcMapper {
    List<User> users(ResultSet resultSet);
}
```

Learn more → [Mapping (Object-to-object)](https://sjf4j.org/docs/mapping)


### Patching

Apply [JSON Patch (RFC 6902)](https://www.rfc-editor.org/info/rfc6902/)
and [JSON Merge Patch (RFC 7386)](https://www.rfc-editor.org/info/rfc7386/) directly to Java object graphs.
```java
JsonPatch patch = JsonPatch.fromJson("""
        [{"op": "replace", "path": "/name", "value": "Alice Zhang"}]
        """);

patch.apply(user);  // Update in place
```

Learn more → [Patching (JSON Patch)](https://sjf4j.org/docs/patching)


## Enjoy Compiled APIs

SJF4J's Compiled APIs can deliver **up to 50× faster execution** than Runtime through compile-time code generation.

### How It Works

Define an annotated interface:
```java
@CompiledNavigator
interface UserNavigator {
    @GetByPath("$.address.city")
    String getCity(User user);
}
```

The annotation processor generates an implementation such as `UserNavigator_Impl`:
```java
// Simplified illustration of generated code
public final class UserNavigator_Impl implements UserNavigator {

    @Override
    public String getCity(User user) {
        if (user == null) return null;
        Address address = user.address;
        return address == null ? null : address.city;
    }
}
```

This implementation is automatically generated at compile-time, producing readable, debuggable Java source code that the JVM can optimize just like hand-written code.


### Where's the Code

Generated implementations use the `_Impl` suffix and are written to the standard annotation-processing output directory.

For Gradle:
```text
build/
└── generated/
    └── sources/
        └── annotationProcessor/
            └── java/
                └── main/
                    └── com/example/
                        └── UserNavigator_Impl.java
```

For Maven, typically: `target/generated-sources/annotations/com/example/UserNavigator_Impl.java`

### How to Use

Add the annotation processor to your project:
```java
dependencies {
    annotationProcessor("org.sjf4j:sjf4j-processor:{version}")
}
```

Use `CompiledInstances.create()` to instantiate generated implementations:
```java
UserNavigator navigator = CompiledInstances.create(UserNavigator.class);
```

More commonly, declare a static `INSTANCE` field directly in the interface:
```java
@CompiledNavigator
interface UserNavigator {
    UserNavigator INSTANCE = CompiledInstances.create(UserNavigator.class);

    @GetByPath("$.address.city")
    String getCity(User user);
}
```

Then use it:
```java
String city = UserNavigator.INSTANCE.getCity(user);
```

## Benchmarks

SJF4J's performance and standards compliance are evaluated through independent comparisons and [JMH](https://github.com/openjdk/jmh) benchmarks.

**JSON Binding**
- [🔗 Java JSON Benchmark](https://github.com/fabienrenaud/java-json-benchmark) — Independent JSON library benchmarks.
- [Binding Full Benchmarks](docs/performance/binding.md) — Native JSON backends vs. Runtime and Compiled.
- [Binding Native Image Full Benchmarks](docs/performance/binding-native.md) — GraalVM AOT, direct library Binding vs. Runtime and Compiled.

**JSONPath Navigation**
- [🔗 JSONPath Comparison](https://cburgmer.github.io/json-path-comparison/) — Cross-implementation specification compatibility.
- [JSONPath Full Benchmarks](docs/performance/path.md) — Jayway JsonPath vs. Runtime and Compiled.

**JSON Schema Validation**
- [🔗 Creek Service](https://www.creekservice.org/json-schema-validation-comparison/) — Independent JVM validation benchmarks.
- [🔗 Bowtie](https://bowtie.report/) — Cross-language JSON Schema compliance.
- [JSON Schema Full Benchmarks](docs/performance/schema.md) — NetworkNT vs. Runtime and Compiled, including roundtrip performance.

**Object Mapping**
- [🔗 Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark) — Independent comparison with Java mapping frameworks.
- [Object Mapping Full Benchmarks](docs/performance/mapping.md) — MapStruct vs. CompiledMapper.

**JDBC Result Mapping**
- [JDBC Mapping Full Benchmarks](docs/performance/jdbc-mapping.md) — Spring JDBC and MyBatis vs. CompiledJdbcMapper.



Learn more → [Benchmarks](https://sjf4j.org/docs/benchmarks)

## Contributing
SJF4J is an evolving open-source project, and community feedback and contributions
are essential to its continued improvement.

All contributions are welcome! Feel free to [open an issue](https://github.com/sjf4j-projects/sjf4j/issues/new).


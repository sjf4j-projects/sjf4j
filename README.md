# SJF4J — Simple JSON Facade for Java

![License](https://img.shields.io/github/license/sjf4j-projects/sjf4j)
[![Maven Central](https://img.shields.io/maven-central/v/org.sjf4j/sjf4j)](https://central.sonatype.com/search?q=sjf4j)
[![Javadoc](https://img.shields.io/badge/javadoc-sjf4j-green)](https://javadoc.io/doc/org.sjf4j/sjf4j)
[![Javadoc](https://img.shields.io/badge/javadoc-sjf4j--schema-green)](https://javadoc.io/doc/org.sjf4j/sjf4j-schema)
[![Javadoc](https://img.shields.io/badge/javadoc-sjf4j--processor-green)](https://javadoc.io/doc/org.sjf4j/sjf4j-processor)  
![Supported Dialects](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fsupported_versions.json)
![Draft 2020-12](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2020-12.json)
![Draft 2019-09](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft2019-09.json)
![Draft 7](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fcompliance%2Fdraft7.json)    
![Build](https://img.shields.io/github/actions/workflow/status/sjf4j-projects/sjf4j/gradle.yml?branch=main)
[![codecov](https://codecov.io/gh/sjf4j-projects/sjf4j/graph/badge.svg?branch=main)](https://codecov.io/gh/sjf4j-projects/sjf4j)
![Stars](https://img.shields.io/github/stars/sjf4j-projects/sjf4j?style=social)

SJF4J is a lightweight JSON facade and **high-performance structural processing layer** for Java.  
It sits above multiple JSON parsers — including [Jackson](https://github.com/FasterXML/jackson-databind),
[Gson](https://github.com/google/gson), [Fastjson2](https://github.com/alibaba/fastjson2),
and [JSON-P](https://github.com/jakartaee/jsonp-api) — while also supporting YAML
(via [SnakeYAML](https://github.com/snakeyaml/snakeyaml)) and Java Properties.

Built on JSON-related standards and semantics, SJF4J provides a consistent programming model across parsers,
data formats, and native Java object graphs.

It unifies [modeling](https://sjf4j.org/docs/modeling) (OBNT),
[binding](https://sjf4j.org/docs/binding) (Multi-Format), [navigating](https://sjf4j.org/docs/navigating) (JSON Path), 
[patching](https://sjf4j.org/docs/patching) (JSON Patch), [validating](https://sjf4j.org/docs/validating) (JSON Schema), 
and [mapping](https://sjf4j.org/docs/mapping) (Object-to-object) under one API model.


## Install
SJF4J requires **JDK 8+**, with no external dependencies beyond the data parsers you choose to add.

Gradle:
```groovy
implementation("org.sjf4j:sjf4j:{version}")
```

Maven:
```xml
<dependency>
    <groupId>org.sjf4j</groupId>
    <artifactId>sjf4j</artifactId>
    <version>{version}</version>
</dependency>
```

**Optional Runtime Dependencies**  
Parsers are enabled automatically when their corresponding libraries are present,
and can also be configured explicitly when needed.

- **JSON**
  - Include one of: `Jackson 3.x`, `Jackson 2.x`, `Gson`, `Fastjson2`, or `JSON-P` (with `Parsson` or others).  
  - By default, SJF4J automatically detects and uses the first available implementation in that order.
  - If none are detected, it falls back to a built-in simple JSON parser (functional but slower).
  - Or configure the backend explicitly, for example:
    ```java
    Sjf4j.builder().jsonFacadeProvider(Jackson2JsonFacade.provider()).build();
    ```

- **YAML**
  - Include `SnakeYAML` (the YAML 1.1 backend).
  - YAML support requires SnakeYAML at runtime; unlike JSON, there is no built-in YAML parser fallback.

- **Java Properties**
  - Built-in support.
  - Conversion is inherently limited by its flat key-value structure.

- **In-Memory** 
  - Built-in support.
  - Provides the same JSON-semantic APIs on in-memory object graphs via OBNT. 
  - Useful even without external data sources (e.g., DB result mapping, complex nested data processing).

Common runtime dependencies (pick as needed):

```groovy
// Jackson 3
implementation("tools.jackson.core:jackson-databind:{jackson3-version}")

// Jackson 2
implementation("com.fasterxml.jackson.core:jackson-databind:{jackson2-version}")

// Gson
implementation("com.google.code.gson:gson:{gson-version}")

// Fastjson2
implementation("com.alibaba.fastjson2:fastjson2:{fastjson2-version}")

// JSON-P API + Parsson implementation
implementation("jakarta.json:jakarta.json-api:{jsonp-version}")
implementation("org.eclipse.parsson:parsson:{parsson-version}")

// YAML
implementation("org.yaml:snakeyaml:{snakeyaml-version}")
```


## Quickstart

### 1-minute example

Start with `JsonObject` when you want to parse JSON, access values directly,
and use JSON-semantic navigation and mutation APIs immediately.

```java
JsonObject jo = JsonObject.fromJson(
        "{" +
        "\"name\":\"Alice\"," +
        "\"age\":18," +
        "\"scores\":{\"math\":59}" +
        "}");

String name = jo.getString("name");
int age = jo.getInt("age");
int math = jo.getIntByPath("$.scores.math");

jo.putByPath("$.scores.art", 95);

String out = jo.toJson();
```

`JsonObject` is one of SJF4J’s dynamic JSON object representations. Others include `Map`, `POJO`, and `JOJO`.

### 5-minutes walkthrough

SJF4J is built around a single structural model: the **Object-Based Node Tree (OBNT)**.
- All structured data in SJF4J are mapped into OBNT.
- All nodes in OBNT are represented as native Java objects -- no dedicated AST.
- All APIs operate directly on native Java objects.
- All APIs follow -- or extend -- standard JSON semantics.

The following example demonstrates a complete lifecycle for processing structured data:
```text
Modeling  →  Binding  →  Navigating  →  Patching  →  Validating  →  Mapping
```

#### Modeling

For demonstration purposes, this examples use JOJO (`JsonObject`-based objects) since it handles typed fields and dynamic properties, 
making it suitable for objects with extra or unknown data.

> **Note**: Standard POJOs are fully supported as well, with equivalent navigation, mutation, and validation capabilities.

Define a JOJO `Student`:
```java
public class Student extends JsonObject {
    private String name;
    private Map<String, Integer> scores;
    private List<Student> friends;
    // Getters and setters
}
```

Learn more → [Modeling (OBNT)](https://sjf4j.org/docs/modeling)



#### Binding

Use `Sjf4j` to encode and decode structured data across multiple formats.
```java
String json = """
{
    "name": "Alice",
    "scores": {"math": 59, "art": 95},
    "friends": [
        {"name": "Bill", "active": true, "scores": {"math": 83}},
        {"name": "Cindy", "friends": [{"name": "David", "scores": {"math": 95}}]}
    ],
    "age": 18
}
""";

Student student = new Sjf4j().fromJson(json, Student.class);
```

Now `student` exposes two complementary access models:
- Strongly-typed Java getters/setters
- JSON-semantic dynamic APIs

```java
student.getName();                  // Alice
student.getInt("age");              // 18
```

Learn more → [Binding (Multi-Format)](https://sjf4j.org/docs/binding)

#### Navigating

Every OBNT node supports declarative structural navigating, expressive querying,
and precise mutation via `JSON Path` (RFC 9535) or `JSON Pointer` (RFC 6901).
```java
student.getIntByPath("$.scores.math");
// 59

student.findByPath("$..friends[?@.scores.math >= 90].name", String.class);  
// ["David"]

student.ensurePutByPath("/friends/0/scores/music", 100);
// Bill's scores becomes: {"math": 83, "music": 100}
```

Learn more → [Navigating (JSON Path)](https://sjf4j.org/docs/navigating)

#### Patching

Every OBNT node supports standard-compliant structural updates 
via `JSON Patch` (RFC 6902) or `JSON Merge Patch` (RFC 7386).
```java
JsonPatch patch = JsonPatch.fromJson("""
[
    { "op": "replace", "path": "/name", "value": "Alice Zhang" },
    { "op": "add",     "path": "/scores/physics", "value": 91 }
]
""");

patch.apply(student);
```

The changes are applied in-place:
```java
student.getName();                              // "Alice Zhang"
student.getIntByPath("$.scores.physics");       // 91
```

Learn more → [Patching (JSON Patch)](https://sjf4j.org/docs/patching)

#### Validating

Declare `JSON Schema` (Draft 2020-12) constraints with `@ValidJsonSchema` (Jakarta/Bean Validation style).
```java
@ValidJsonSchema("""
{
  "$ref": "#/$defs/Student",
  "$defs": {
    "Student": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "minLength": 1
        },
        "scores": {
          "type": "object",
          "additionalProperties": {"type": "integer", "minimum": 0}
        },
        "friends": {
          "type": "array",
          "items": {"$ref": "#/$defs/Student"}
        }
      },
      "required": ["name"]
    }
  }
}
""")
public class Student extends JsonObject {
    private String name;
    private Map<String, Integer> scores;
    private List<Student> friends;
}
```

Validate at runtime:
```java
SchemaValidator validator = new SchemaValidator();
validator.validate(student).isValid();                  // true
```

Learn more → [Validating (JSON Schema)](https://sjf4j.org/docs/validating)

#### Mapping

While `JsonPatch` focuses on in-place partial modification,
`@CompiledMapper` generates a direct mapper implementation at compile time.
```java
@CompiledMapper
public interface StudentMapper {
    @Mapping(target = "studentName", source = "name")
    @EnsureMapping(target = "$.info.school", compute = "() -> \"PKU\"")
    @Mapping(target = "/totalScore", sources = "scores",
             compute = "scores -> scores.values().stream().mapToInt(i -> i).sum()")
    StudentDto toDto(Student student);
}

StudentMapper mapper = CompiledNodes.of(StudentMapper.class);

StudentDto studentDto = mapper.toDto(student);
```

Learn more → [`Mapping` (Object-to-object)](https://sjf4j.org/docs/mapping)


## Benchmarks
SJF4J delivers high performance with minimal overhead while providing a unified JSON-semantic processing model.

**Reflection Access Benchmark**  
Lambda-based accessor generation minimizes reflection overhead,
delivering performance close to direct field or method invocation.

**JSON Binding Benchmark**  
SJF4J operates on top of underlying JSON parsers while adding structural
capabilities and flexible binding annotations.  
In most cases, the additional overhead remains modest compared to native
JSON libraries.

**JSON Path Navigating Benchmark**  
`JsonPath` shows strong performance in `compile` and `query` workloads, while also providing `mutation` operations.   
For performance-critical paths, `@CompiledPath` uses bytecode generation and can be tens of times faster than standard path evaluation.

**JSON Schema Validating Benchmark**  
SJF4J fully supports JSON Schema Draft `2020-12`/`2019-09`/`draft-07` and consistently ranks
among the top-performing Java implementations in
[Creek Service](https://www.creekservice.org/json-schema-validation-comparison/)
and [Bowtie](https://bowtie.report/) benchmarks.

**Object-to-object Mapping Benchmark**  
`@CompiledMapper` uses compile-time code generation to deliver performance close to hand-written mapping code 
while supporting JSON-style paths, computed fields, and heterogeneous object structures, 
placing SJF4J among the fastest Java mapping implementations in [Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark).

Learn more → [Benchmarks](https://sjf4j.org/docs/benchmarks)

## Contributing

Contributions of all kinds are welcome — including code, documentation, bug reports,
examples, benchmarks, ideas, and feedback.  
To get started, please [open an issue](https://github.com/sjf4j-projects/sjf4j/issues/new).


JSON is one of the most widely used structured data formats today, 
backed by a mature ecosystem of standards, specifications, and RFCs.

> *Does Java need a JSON-oriented programming model?*

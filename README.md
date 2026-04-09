# SJF4J — Simple JSON Facade for Java

![License](https://img.shields.io/github/license/sjf4j-projects/sjf4j)
![Supported Dialects](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fsupported_versions.json)
![Maven Central](https://img.shields.io/maven-central/v/org.sjf4j/sjf4j)
[![javadoc](https://javadoc.io/badge2/org.sjf4j/sjf4j/javadoc.svg)](https://javadoc.io/doc/org.sjf4j/sjf4j)
![Stars](https://img.shields.io/github/stars/sjf4j-projects/sjf4j?style=social)  
![Build](https://img.shields.io/github/actions/workflow/status/sjf4j-projects/sjf4j/gradle.yml?branch=main)
[![codecov](https://codecov.io/gh/sjf4j-projects/sjf4j/graph/badge.svg?branch=main)](https://codecov.io/gh/sjf4j-projects/sjf4j)

**SJF4J** is a lightweight facade over multiple JSON libraries, 
including [Jackson](https://github.com/FasterXML/jackson-databind), 
[Gson](https://github.com/google/gson), 
[Fastjson2](https://github.com/alibaba/fastjson2),
[JSON-P](https://github.com/jakartaee/jsonp-api). 
Beyond JSON, it also supports YAML (via [SnakeYAML](https://github.com/snakeyaml/snakeyaml))
and Java Properties (built-in).

SJF4J provides **a unified JSON-semantic structural processing layer**, 
offering consistent APIs for **modeling** (OBNT), **parsing** (JSON/YAML), 
**navigating** (JSON Path), **patching** (JSON Patch), **validating** (JSON Schema), 
and **mapping** across data formats and native object graphs.

## Install
SJF4J requires **JDK 8+** and has no external dependencies.

Gradle
```groovy
implementation("org.sjf4j:sjf4j:{version}")
```
Maven
```xml
<dependency>
    <groupId>org.sjf4j</groupId>
    <artifactId>sjf4j</artifactId>
    <version>{version}</version>
</dependency>
```

**Optional Runtime Backends**  
SJF4J itself has no external runtime dependencies.  
Format support is activated automatically when the corresponding libraries are present.

- **JSON**
  - Include one of: `Jackson 3.x`, `Jackson 2.x`, `Gson`, `Fastjson2`, or `JSON-P` (with `Parsson` or other).  
  - By default, SJF4J automatically detects and uses the first available implementation in that order.
  - If none are detected, it falls back to a built-in simple JSON parser (functional but slower).

- **YAML**
  - Include: `SnakeYAML`.

- **Java Properties**
  - Built-in supported.
  - Conversions from Java Properties are inherently constrained by its flat key-value structure.

- **In-Memory** (no external data)  
  - SJF4J can operate directly on in-memory object graphs through OBNT,
    providing the same JSON-semantic APIs.

## Quickstart

SJF4J is built around a single structural model: the **Object-Based Node Tree (OBNT)**.
- All structured data in SJF4J are mapped into OBNT.
- All nodes in OBNT are represented as native Java objects -- no dedicated AST.
- All APIs operate directly on native Java objects.
- All APIs follow -- or extend -- standard JSON semantics.

The following example demonstrates a complete lifecycle for processing structured data:
> **Modeling → Parsing → Navigating → Patching → Validating → Mapping**

### Modeling

**JOJO (JSON Object Java Object)** extends `JsonObject` and unifies typed Java fields 
with dynamic JSON properties in a single object model.

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

### Parsing

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

Student student = Sjf4j.fromJson(json, Student.class);
```

Now `student` exposes two complementary access models:
- Strongly-typed Java getters/setters
- JSON-semantic dynamic APIs

```java
student.getName();                  // Alice
student.getInteger("age");          // 18
```

Learn more → [Parsing (JSON/YAML)](https://sjf4j.org/docs/parsing)

### Navigating

Every OBNT node supports declarative structural navigating, expressive querying,
and precise mutation via `JSON Path` (RFC 9535) or `JSON Pointer` (RFC 6901).
```java
student.getIntegerByPath("$.scores.math");
// 59

student.findByPath("$..friends[?@.scores.math >= 90].name", String.class);  
// ["David"]

student.ensurePutByPath("/friends/0/scores/music", 100);
// Bill's scores becomes: {"math": 83, "music": 100}
```

Learn more → [Navigating (JSON Path)](https://sjf4j.org/docs/navigating)

### Patching

Every OBNT node supports standard-compliant structural updates via `JSON Patch` (RFC 6902).
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
student.getIntegerByPath("$.scores.physics");   // 91
```

Learn more → [Patching (JSON Patch)](https://sjf4j.org/docs/patching)

### Validating

Declare `JSON Schema` (Draft 2020-12) constraints with `@ValidJsonSchema` (like Jakarta Validation style).
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

### Mapping

While `JsonPatch` focuses on in-place partial modification,
`NodeMapper` produces a new structure from the source object graph.
```java
NodeMapper<Student, StudentDto> mapper = NodeMapper
    .builder(Student.class, StudentDto.class)
    .copy("studentName", "name")
    .ensureValue("$.info.school", "PKU")
    .compute("/avgScore", root -> 
            root.getScores().values().stream().mapToInt(i -> i).average().orElse(0))
    .build();

StudentDto studentDto = mapper.map(student);
```

Learn more → [Mapping (Transformation)](https://sjf4j.org/docs/mapping)


## Benchmarks
SJF4J delivers high performance with minimal overhead while providing a unified JSON-semantic processing model.

**Reflection Access Benchmark**  
Lambda-based accessor generation minimizes reflection overhead,
delivering performance close to direct field or method invocation.

**JSON Parsing Benchmark**  
SJF4J operates on top of underlying JSON parsers while adding structural
capabilities and flexible binding annotations.  
In most cases, the additional overhead remains modest compared to native
JSON libraries.

**JSON Path Navigating Benchmark**  
SJF4J shows strong performance in `compile` and `query` workloads, while also providing `mutation` operations.  
Within SJF4J, `Map/List` achieves the highest speed, with `JOJO` generally closer to `Map/List` than plain `POJO`.

**JSON Schema Validating Benchmark**  
SJF4J fully supports JSON Schema Draft 2020-12 and consistently ranks
among the top-performing Java implementations in
[Bowtie](https://bowtie.report/#/implementations/java-sjf4j) benchmarks.

Learn more → [Benchmarks](https://sjf4j.org/docs/benchmarks)

## Contributing
Given that JSON has evolved into a well-defined and widely adopted specification,
SJF4J began as an exploration of *What JSON-Oriented development might look like in Java*.

Contributions of code, docs, bug reports, examples, benchmarks, and careful feedback are all valuable.

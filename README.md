# SJF4J — Simple JSON Facade for Java

![License](https://img.shields.io/github/license/sjf4j-projects/sjf4j)
![Supported Dialects](https://img.shields.io/endpoint?url=https%3A%2F%2Fbowtie.report%2Fbadges%2Fjava-org.sjf4j-sjf4j%2Fsupported_versions.json)
![Maven Central](https://img.shields.io/maven-central/v/org.sjf4j/sjf4j)
[![javadoc](https://javadoc.io/badge2/org.sjf4j/sjf4j/javadoc.svg)](https://javadoc.io/doc/org.sjf4j/sjf4j)
![Stars](https://img.shields.io/github/stars/sjf4j-projects/sjf4j?style=social)  
![Build](https://img.shields.io/github/actions/workflow/status/sjf4j-projects/sjf4j/gradle.yml?branch=main)

**SJF4J** is a lightweight facade over multiple JSON libraries, 
including [Jackson 2.x](https://github.com/FasterXML/jackson-databind), 
[Gson](https://github.com/google/gson), 
[Fastjson2](https://github.com/alibaba/fastjson2),
[JSON-P](https://github.com/jakartaee/jsonp-api).  
Beyond JSON, it also supports for YAML (via [SnakeYAML](https://github.com/snakeyaml/snakeyaml))
and Java Properties (built-in).

SJF4J provides **a unified JSON-semantic structural processing layer**,
delivering consistent and expressive APIs for parsing, navigation, transformation, 
and validation across formats and native object graphs.
## Install
SJF4J requires `JDK 8+` and has no external dependencies.

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
Format support is activated only when the corresponding libraries are present.

- **JSON** — Include one of: `Jackson 2.x`, `Gson`, `Fastjson2`, or `JSON-P` (with `Parsson` or other).  
  SJF4J automatically detects and uses the first available implementation in that order.
  If none are detected, it falls back to a built-in minimal JSON parser (functional but slower).


- **YAML** — Include `SnakeYAML`.


- **Java Properties** — A built-in parser is provided.  
  Conversions from Java Properties are inherently constrained by its flat key-value structure.


- **In-Memory Usage** (No External Data) — SJF4J does not require external parsing.  
  It can operate directly on in-memory object graphs through OBNT,
  providing the same JSON-semantic APIs for navigation, transformation, and validation.

## Quickstart

SJF4J is built around a single structural model: the **Object-Based Node Tree (OBNT)**.
- All structured data are mapped into OBNT.
- All nodes in OBNT are represented as native Java objects -- no dedicated AST.
- All APIs operate directly on native Java objects.
- All APIs follow -- or extend -- standard JSON semantics.

The following example, while slightly more elaborate, 
demonstrates the whole lifecycle:
> **Modeling → Parsing → Navigation → Transformation → Validation**

### Modeling with JOJO

JOJO (JSON Object Java Object) eliminates the traditional trade-off 
between typed Java models and dynamic JSON structures.

> JOJO extends `JsonObject`, unifying typed Java fields and dynamic JSON properties within a single object model.

```java
public class Student extends JsonObject {
    private String name;
    private Map<String, Integer> scores;
    private List<Student> friends;
    // Getters and setters
}
```
Define a JOJO: `Student`.

Learn more → [Modeling (OBNT)](https://sjf4j.org/docs/modeling)

### Parsing from JSON

`Sjf4j` is the unified facade for structured data encoding and decoding across multiple formats.
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

Learn more → [Parsing (Codec)](https://sjf4j.org/docs/parsing)

### Navigating with JSON Path
OBNT enables declarative structural navigation, expressive querying,
and precise mutation through `JSON Path` or `JSON Pointer`.
```java
student.getIntegerByPath("$.scores.math");
// 59

student.findByPath("$..friends[?@.scores.math >= 90].name", String.class);  
// ["David"]

student.ensurePutByPath("/friends/0/scores/music", 100);
// Bill's scores becomes: {"math": 83, "music": 100}
```

Learn more → [Navigation (JSON Path)](https://sjf4j.org/docs/navigation)

### Transforming with JSON Patch

Apply standard-compliant structural updates using `JSON Patch`.
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

Learn more → [Transformation (JSON Patch)](https://sjf4j.org/docs/transformation)

### Validating with JSON Schema

Declare JSON Schema constraints with `@ValidJsonSchema` (Jakarta Bean Validation–style).
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

Learn more → [Validation (JSON Schema)](https://sjf4j.org/docs/validation)

## Benchmarks
SJF4J is designed to minimize structural overhead while preserving unified semantics.

**JSON Parsing Performance**  
Using SJF4J introduces approximately 5–10% overhead compared to native JSON libraries,
while providing a unified API, structural abstraction, and extended functionality.

**Reflection Access Performance**  
Lambda-based accessor generation minimizes reflection overhead,
delivering performance close to direct field or method invocation.

**JSON Schema Validation Performance**  
SJF4J fully supports JSON Schema draft 2020-12 and
consistently ranks among the top-performing Java implementations in [Bowtie](https://bowtie.report/#/implementations/java-sjf4j) benchmarks.

Learn more → [Benchmarks](https://sjf4j.org/docs/benchmarks)

## Contributing
Given that JSON has evolved into a well-defined and widely adopted specification,
SJF4J began as an exploration of what JSON-Oriented Development might look like in Java.

If you find the project interesting,
contributions of all kinds — code, docs, bug reports, discussions, examples, or benchmarks — are welcome~!




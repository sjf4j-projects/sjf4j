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

It provides a consistent programming model across parsers, data formats, and native Java object graphs, 
built on JSON-related standards and semantics.

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

<details>
<summary><strong>Optional Runtime Dependencies</strong></summary>

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
</details>


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

System.out.println(jo.toJson());
```

`JsonObject` is one of SJF4J's JSON object representations. Others include:
- `Map`
- Standard Java POJOs
- JOJOs (`JsonObject`-based objects)


### 5-minute walkthrough

The following example demonstrates a complete lifecycle for processing structured data:
```text
Modeling  →  Binding  →  Navigating  →  Patching  →  Validating  →  Mapping
```


#### Modeling

A standard POJO works out of the box:
```java
public class Student { 
    private String name; 
    private Map<String, Integer> scores; 
    private List<Student> friends; 
    
    // getters and setters 
}
```

Or, you can also extend `JsonObject` to create a JOJO, which supports additional dynamic properties while retaining typed fields.
```java
public class StudentJojo extends JsonObject {
    private String name;
    private Map<String, Integer> scores;
    private List<Student> friends;
    
    // Getters and setters
}
```

Learn more → [Modeling (OBNT)](https://sjf4j.org/docs/modeling)


#### Binding

Use `Sjf4j` to bind JSON into Java objects.

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

Once bound, SJF4J works directly on the Java object graph:
```java
student.getName();                  // Alice

student.getInt("age");              // 18
```

Learn more → [Binding (Multi-Format)](https://sjf4j.org/docs/binding)

#### Navigating

Navigate and mutate object graphs using JSON Path (RFC 9535) or JSON Pointer (RFC 6901).
```java
JsonPath.parse("$.scores.math").getIntByPath(student);
// 59

JsonPath.parse("$..friends[?@.scores.math >= 90].name").findByPath(student, String.class);      
// ["David"]

JsonPath.parse("/friends/0/scores/music").ensurePutByPath(student, 100);
// Bill's scores becomes: {"math": 83, "music": 100}
```

JOJOs additionally provide shortcut methods:
```java
studentJojo.getIntByPath("$.scores.math");
```

For performance-critical workloads, `@CompiledPath` can generate direct access code at compile time 
and deliver near hand-written navigation performance.  
```java
@CompiledPath
interface StudentPath {

    @GetByPath("$.scores.math")
    int getScoresMath(Student student);   
}
```

Learn more → [Navigating (JSON Path)](https://sjf4j.org/docs/navigating)

#### Patching

Apply standard-compliant structural updates using JSON Patch (RFC 6902).
```java
JsonPatch patch = JsonPatch.fromJson("""
[
    { "op": "replace", "path": "/name",           "value": "Alice Zhang" },
    { "op": "add",     "path": "/scores/physics", "value": 91 }
]
""");

patch.apply(student);
```

Changes are applied directly to the object graph:
```java
student.getName();                              
// "Alice Zhang"

student.getScores().get("physics");       
// 91
```

SJF4J also supports JSON Merge Patch (RFC 7386) and Indexed Merge Patch, a convenient extension for partial array updates.

Learn more → [Patching (JSON Patch)](https://sjf4j.org/docs/patching)

#### Validating

Validate Java object graphs with JSON Schema Draft 2020-12.
```java
JsonSchema schema = JsonSchema.fromJson("""
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1
    },
    "scores": {
      "type": "object",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["name"]
}
""");

SchemaPlan plan = schema.createPlan();

ValidationResult result = plan.validate(student);

boolean valid = result.isValid();
```

You can also use `@ValidJsonSchema` for Bean Validation style integration.
```java
@ValidJsonSchema("""
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1
    },
    "scores": {
      "type": "object",
      "additionalProperties": {
        "type": "integer",
        "minimum": 0
      }
    }
  },
  "required": ["name"]
}
""")
public class Student {
    // ...
}
```

SJF4J fully supports JSON Schema Draft 2020-12, 2019-09, and draft-07, 
and can validate Java object graphs directly without first converting them into a dedicated JSON tree.


Learn more → [Validating (JSON Schema)](https://sjf4j.org/docs/validating)

#### Mapping

Generate object mappers at compile time using `@CompiledMapper`.
```java
@CompiledMapper
public interface StudentMapper {
    
    @Mapping(target = "studentName", source = "name")
    @Mapping(target = "totalScore", sources = "scores",
            compute = "scores -> scores.values().stream().mapToInt(i -> i).sum()")
    StudentDto toDto(Student student);
}
```

Use it:
```java
StudentMapper mapper = CompiledNodes.of(StudentMapper.class);

StudentDto studentDto = mapper.toDto(student);
```

`@CompiledMapper` supports direct field mapping, JSON-style paths, computed fields, and nested mappings.

Learn more → [`Mapping` (Object-to-object)](https://sjf4j.org/docs/mapping)

## Why Does This Work?

SJF4J is built around a unified structural model called the **Object-Based Node Tree** ([OBNT](https://sjf4j.org/docs/modeling)).
- All structured data in SJF4J are mapped into OBNT.
- All nodes in OBNT are represented as native Java objects -- no dedicated AST.
- All APIs operate directly on native Java objects.
- All APIs follow -- or extend -- standard JSON semantics.

As a result, SJF4J can apply JSON-style operations directly to your existing Java object graph 
without first converting it into a dedicated JSON tree.

Learn more → [Architecture](https://sjf4j.org/docs/architecture)


## Benchmarks

SJF4J combines a unified JSON-semantic programming model with top-tier performance across a wide range of workloads, 
as demonstrated by JMH benchmarks and independent third-party evaluations.

**Reflection Access Benchmark**  
Lambda-based accessor generation minimizes reflection overhead,
delivering performance close to direct field or method invocation.

**JSON Binding Benchmark**  
SJF4J operates on top of underlying JSON parsers while adding structural
capabilities and flexible binding annotations.  
In most cases, the additional overhead remains modest compared to native
JSON libraries.

**JSON Path Navigating Benchmark**  
`JsonPath` provides high-performance querying and mutation operations.  
For performance-critical paths, `@CompiledPath` generates direct access code 
and can be tens of times faster than interpreted path evaluation.

**JSON Schema Validating Benchmark**  
SJF4J fully supports JSON Schema Draft `2020-12`, `2019-09`, and `draft-07`.  
It consistently ranks among the top-performing Java implementations in
[Creek Service](https://www.creekservice.org/json-schema-validation-comparison/)
and [Bowtie](https://bowtie.report/) benchmarks.

**Object-to-object Mapping Benchmark**  
`@CompiledMapper` delivers performance close to hand-written mapping code 
and ranks among the fastest Java mapping frameworks in the [Java Object Mapper Benchmark](https://github.com/arey/java-object-mapper-benchmark).

Learn more → [Benchmarks](https://sjf4j.org/docs/benchmarks)


## Contributing

Contributions of all kinds are welcome — including code, documentation, bug reports,
examples, benchmarks, ideas, and feedback.    
To get started, please [open an issue](https://github.com/sjf4j-projects/sjf4j/issues/new).

JSON is one of the most widely used structured data formats today, 
backed by a mature ecosystem of standards, specifications, and RFCs.

> *Does Java need a JSON-oriented programming model?*

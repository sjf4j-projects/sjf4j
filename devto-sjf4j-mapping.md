# MapStruct Maps Beans. SJF4J Maps Structure

Compile-time mapping gets more interesting when your data is not just JavaBeans.

*Use SJF4J's `@CompiledMapper` to move data between POJOs, maps, and JSON-like objects—at compile time.*

Object mapping sounds harmless: turn a `User` into a `UserDto`.

Then reality arrives:

- Field names differ.
- Data lives several levels deep.
- A DTO needs a computed value.
- An update must not erase an existing collection.
- The upstream system sends a `Map`, a `JsonObject`, or JSON with a strong personality.

At that point, a handwritten mapper can become a low-budget mystery movie: every `null` is a suspect.

SJF4J's `@CompiledMapper` takes a more direct route: **it generates type-safe mapping code at compile time, with no reflection at runtime.**

```groovy
implementation("org.sjf4j:sjf4j:{version}")
annotationProcessor("org.sjf4j:sjf4j-processor:{version}")
```

## Start with matching names

Suppose we have two ordinary Java objects:

```java
public class User {
    private String name;
    private int age;
    // getters and setters
}

public class UserDto {
    private String name;
    private int age;
    // getters and setters
}
```

Declare a mapper interface:

```java
@CompiledMapper
public interface UserMapper {
    UserDto toDto(User user);
}
```

Use it like this:

```java
UserMapper mapper = CompiledNodes.instanceOf(UserMapper.class);
UserDto dto = mapper.toDto(user);
```

Compatible properties with the same name are mapped automatically. You write the interface; SJF4J generates the implementation during compilation. At runtime, it is just direct Java code.

In other words, the mapper does not rummage through properties while your application is running. It memorized the route before work started.

## Describe differences, not everything

The real world is rarely this tidy. Perhaps the domain model uses `name`, while the public DTO uses `displayName`:

```java
@CompiledMapper
public interface UserMapper {

    @Mapping(target = "displayName", source = "name")
    UserDto toDto(User user);
}
```

There is no need to repeat every matching field. Keep auto-mapping for the obvious parts and use annotations for the exceptions. Your mapper stays a short description of the transformation instead of becoming a tax form.

## Read data from nested structures

SJF4J mapping is built on its structural object model, so a source is not limited to JavaBean properties. `source` can use JSONPath or JSON Pointer:

```java
@CompiledMapper
public interface UserViewMapper {

    @Mapping(target = "city", source = "$.profile.city")
    @Mapping(target = "country", source = "/profile/country")
    UserView toView(UserPayload source);
}
```

This is useful at integration boundaries: map data from a JSON-like object directly into a typed view without first manually unpacking it.

Targets can use paths too:

```java
@CompiledMapper
public interface UserViewMapper {

    @Mapping(target = "$.profile.displayName", source = "name")
    UserView toView(User user);
}
```

When intermediate objects may be missing, choose the behavior explicitly:

- `@EnsureMapping` creates missing parent objects when needed.
- `@MappingIfParentPresent` skips the write when the final parent is absent.

That is considerably more pleasant than three nested null checks and three emergency constructors.

## Computed values: keep them small

DTOs often need derived properties:

```java
@CompiledMapper
public interface UserMapper {

    @Mapping(
        target = "fullName",
        sources = {"firstName", "lastName"},
        compute = "(first, last) -> first + \" \" + last"
    )
    UserDto toDto(User user);
}
```

`compute` is processed at compile time and emitted as generated Java code. It is not interpreted dynamically at runtime.

For anything larger than a small expression, prefer a helper method:

```java
@CompiledMapper
public interface UserMapper {

    @Mapping(
        target = "fullName",
        sources = {"firstName", "lastName"},
        compute = "this::join"
    )
    UserDto toDto(User user);

    default String join(String first, String last) {
        return first + " " + last;
    }
}
```

A practical rule: **mapping converts shapes; business workflows belong in business code.** If a `compute` expression starts discussing discounts, risk scoring, and the heat death of the universe, it needs its own home.

## More than POJO-to-POJO mapping

SJF4J understands structural shapes, not only JavaBeans. It supports mapping across:

- POJOs and records;
- `Map`, `JsonObject`, and JOJO;
- `List`, `Set`, Java arrays, and `JsonArray`;
- nested collections and maps;
- scalar values, enums, and `@NodeValue` types;
- limited type-level `@OneOf` dispatch.

For example, map a JSON-style `Map` directly into a DTO:

```java
@CompiledMapper
public interface UserMapper {
    UserDto toDto(Map<String, Object> source);
}
```

Or project a Java object into a structural representation:

```java
@CompiledMapper
public interface UserMapper {
    Map<String, Object> toMap(User user);
}
```

This is especially useful for API adapters, configuration transformations, event payloads, and models with dynamic extension fields.

## Updating an existing object needs a policy

Creating a new object is simple. Updating an existing one is where accidental data loss likes to hide.

```java
@CompiledMapper
public interface UserMapper {

    @MapperOptions(nulls = NullValuePolicy.IGNORE)
    void update(UserDto target, User source);
}
```

For update methods, the first parameter is the target and subsequent parameters are sources. `NullValuePolicy.IGNORE` keeps an existing value when the source value is `null`.

Collections and maps have explicit update policies too:

```java
@CompiledMapper
public interface UserMapper {

    @MapperOptions(arrays = ArrayPolicy.ADD)
    void update(UserDto target, User source);
}
```

Collections can be replaced, cleared and refilled, or appended to. Maps can be merged, cleared and refilled, or populated only for missing entries.

Making these policies explicit matters. “Delete all of the user's tags” is usually not a product requirement; it is the first line of an incident report.

## Strict conversion is a feature

SJF4J deliberately does not generate every possible loose conversion, such as arbitrary `String -> Number` or `Boolean -> String` coercion.

When conversion could lose meaning, make the intent explicit with a mapper method, helper method, `compute` expression, or `@NodeValue` codec.

The framework can move boxes for you. It should not guess whether the box contains a cat.

## When should you use SJF4J Mapping?

If you only map JavaBeans to JavaBeans and your team is already deeply invested in MapStruct, continuing with MapStruct is entirely reasonable.

SJF4J is particularly compelling when your mapping crosses structural boundaries:

- Java objects to and from `Map`, `JsonObject`, or JOJO;
- JSONPath or JSON Pointer reads and writes;
- nested JSON-style structures;
- compile-time generation with no runtime reflection;
- mapping semantics shared with JSON binding, navigation, and patching.

## Closing thought

Good object mapping should not become business-code noise, and it should not perform archaeological reflection during production traffic.

SJF4J's `@CompiledMapper` keeps the model simple:

1. Map matching properties automatically.
2. Describe only the differences.
3. Use paths at structural boundaries.
4. Generate direct code at compile time.
5. Be cautious when a conversion is ambiguous.

Keep mapping readable, verifiable, and predictable. There are already enough DTOs in the world; mappers do not need to become a second domain model.

---

**Tags:** `java` `json` `performance` `mapping`

- Documentation: https://sjf4j.org/docs/mapping
- GitHub: https://github.com/sjf4j-projects/sjf4j

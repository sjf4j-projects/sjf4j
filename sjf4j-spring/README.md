# Spring Framework Integration

`sjf4j-spring` registers compiled SJF4J interfaces as ordinary Spring Beans,
without requiring Spring Boot. Supports **Spring Framework 6.2+ / Java 17+**.

```kotlin
implementation("org.sjf4j:sjf4j-spring:{version}")
annotationProcessor("org.sjf4j:sjf4j-processor:{version}")
```

Enable SJF4J once in a Spring configuration:

```java
@Configuration
@EnableSjf4j
public class AppConfig {
}
```

Declare a compiled interface and inject it as any other Bean:

```java
@CompiledMapper
public interface UserMapper {
    UserDto toDto(User user);
}

@Service
public class UserService {
    @Autowired
    private UserMapper mapper;

    public UserDto convert(User user) {
        return mapper.toDto(user);
    }
}
```

The annotation processor writes `META-INF/sjf4j/compiled.interfaces`. The
Spring integration collects these indexes across classpath JARs and registers
lazy singleton Beans. An explicitly registered Bean of the same interface
takes priority. Compiled instances use compile-time settings, independently of
runtime `NodeConfig` Beans. `@CompiledBinder.includeNulls` and `@CompiledBinder.exactNumbers` determine
compiled and fallback write/read policies for the entire interface.

Spring must discover your application configuration and service (for example,
through `AnnotationConfigApplicationContext` and `@ComponentScan`). SJF4J
compiled interfaces themselves need no package scanning.

For **Spring Boot 3.x**, depend on
[`sjf4j-spring-boot-starter`](../sjf4j-spring-boot-starter/) instead;
its auto-configuration enables this integration with no `@EnableSjf4j`
annotation.

Generated implementations are `final`, so AOP on these Beans requires
interface-based proxies. Imported mappers declared through
`@CompiledMapper(importing = ...)` are instantiated internally, not through
Spring. Spring AOT and GraalVM Native Image are not yet supported.

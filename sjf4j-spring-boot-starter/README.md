# Spring Boot Integration

The `sjf4j-spring-boot-starter` registers compiled SJF4J interfaces as
ordinary Spring Beans. No `@Component`, `@Bean`, package scan, or per-interface
configuration is required.

```kotlin
implementation("org.sjf4j:sjf4j-spring-boot-starter:{version}")
annotationProcessor("org.sjf4j:sjf4j-processor:{version}")
```

```java
@CompiledMapper
public interface UserMapper {
    UserDto toDto(User source);
}

@Service
public class UserService {
    public UserService(UserMapper mapper) {
        // A Spring-managed implementation
    }
}
```

The annotation processor writes `META-INF/sjf4j/compiled.interfaces` containing the
binary names of successfully generated interfaces. At startup, the starter
collects **all** indexes from the classpath (including dependency JARs),
de-duplicates them, and registers lazy singleton BeanDefinitions. Spring creates
instances through `CompiledInstances.create(type, context)`: it uses an
application-provided `RuntimeContext` when one exists, or
`RuntimeContext.EMPTY` otherwise. Explicit user Beans of the same type win.

Interfaces can be injected, looked up by type through `getBean()`, obtained
through `ObjectProvider`, or injected as a collection. Standard Spring
post-processing and bean lifecycle callbacks apply. Since generated classes
are `final`, AOP on compiled beans requires interface-based JDK proxies
(e.g. `spring.aop.proxy-target-class=false`), not CGLIB subclass proxies.

Note: mappers included through `@CompiledMapper(importing = ...)` are created
internally by SJF4J and do not reuse Spring-managed instances. Spring AOT and
GraalVM Native Image are not yet supported by this integration. Requires
Spring Boot 3.x and Java 17 or newer.

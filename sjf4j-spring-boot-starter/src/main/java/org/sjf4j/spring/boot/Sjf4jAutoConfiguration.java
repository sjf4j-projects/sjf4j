package org.sjf4j.spring.boot;

import org.sjf4j.spring.Sjf4jSpringConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/** Enables the shared SJF4J Spring integration automatically in Spring Boot. */
@AutoConfiguration
@Import(Sjf4jSpringConfiguration.class)
public class Sjf4jAutoConfiguration {
}

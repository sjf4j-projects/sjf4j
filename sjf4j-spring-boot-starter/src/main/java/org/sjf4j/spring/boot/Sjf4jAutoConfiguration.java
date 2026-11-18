package org.sjf4j.spring.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Registers indexed compiled SJF4J interfaces without Spring annotations on generated code. */
@AutoConfiguration
public class Sjf4jAutoConfiguration {

    @Bean
    public static CompiledBeanDefinitionRegistrar sjf4jCompiledBeanDefinitionRegistrar() {
        return new CompiledBeanDefinitionRegistrar();
    }
}

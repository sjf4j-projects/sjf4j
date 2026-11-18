package org.sjf4j.spring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Framework-level registration shared by plain Spring and Spring Boot. */
@Configuration(proxyBeanMethods = false)
public class Sjf4jSpringConfiguration {

    @Bean
    public static CompiledBeanDefinitionRegistrar sjf4jCompiledBeanDefinitionRegistrar() {
        return new CompiledBeanDefinitionRegistrar();
    }
}

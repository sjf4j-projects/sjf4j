package org.sjf4j.spring;

import org.junit.jupiter.api.Test;
import org.sjf4j.NodeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainSpringIntegrationTest {

    @Test
    void enableSjf4jInjectsGeneratedImplementationsWithoutSpringBoot() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(AppConfig.class)) {

            UserService service = context.getBean(UserService.class);
            SpringFixtures.UserMapper mapper = context.getBean(SpringFixtures.UserMapper.class);
            assertSame(mapper, service.mapper);
            assertEquals("Ada", service.convert(new SpringFixtures.User("Ada")).name);

            assertEquals("Ada", context.getBean(SpringFixtures.UserNavigator.class)
                    .name(Map.of("name", "Ada")));
            assertEquals("Ada", context.getBean(SpringFixtures.UserBinder.class)
                    .read("{\"name\":\"Ada\"}").name);
            assertNotNull(context.getBean(SpringFixtures.UserJdbcMapper.class));
            assertTrue(context.getBean(SpringFixtures.UserValidator.class)
                    .valid(Map.of("name", "Ada")));

            assertSame(mapper, context.getBean(SpringFixtures.UserMapper.class));
        }
    }

    @Test
    void explicitBeanOverridesGeneratedMapper() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(OverrideConfig.class)) {

            assertEquals(1, context.getBeanNamesForType(SpringFixtures.UserMapper.class).length);
            assertEquals("custom", context.getBean(SpringFixtures.UserMapper.class)
                    .toDto(new SpringFixtures.User("Ada")).name);
        }
    }

    @Test
    void registersCompiledBeansAlongsideRuntimeNodeConfig() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(RuntimeConfig.class)) {

            assertTrue(context.containsBean("nodeConfig"));
            assertTrue(!context.getBean(NodeConfig.class).includeNulls);
            assertNotNull(context.getBean(SpringFixtures.UserMapper.class));
        }
    }

    @Test
    void noCompiledBeansAreRegisteredWithoutEnableSjf4j() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DisabledConfig.class)) {
            assertEquals(0, context.getBeanNamesForType(SpringFixtures.UserMapper.class).length);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableSjf4j
    static class AppConfig {
        @Bean
        UserService userService() {
            return new UserService();
        }
    }

    static class UserService {
        @Autowired
        SpringFixtures.UserMapper mapper;

        SpringFixtures.UserDto convert(SpringFixtures.User user) {
            return mapper.toDto(user);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableSjf4j
    static class OverrideConfig {
        @Bean
        SpringFixtures.UserMapper customMapper() {
            return source -> {
                SpringFixtures.UserDto dto = new SpringFixtures.UserDto();
                dto.name = "custom";
                return dto;
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableSjf4j
    static class RuntimeConfig {
        @Bean
        NodeConfig nodeConfig() {
            return new NodeConfig(false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DisabledConfig {
    }
}

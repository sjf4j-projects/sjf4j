package org.sjf4j.spring.boot;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.spring.CompiledBeanDefinitionRegistrar;
import org.sjf4j.spring.EnableSjf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Sjf4jAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Sjf4jAutoConfiguration.class));

    @Test
    void registersAllCompiledKindsAsStandardBeans() {
        runner.withUserConfiguration(ConsumerConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CompiledFixtures.UserMapper.class);
            assertThat(context).hasSingleBean(CompiledFixtures.UserNavigator.class);
            assertThat(context).hasSingleBean(CompiledFixtures.UserBinder.class);
            assertThat(context).hasSingleBean(CompiledFixtures.UserJdbcMapper.class);
            assertThat(context).hasSingleBean(CompiledFixtures.UserValidator.class);

            CompiledFixtures.UserMapper mapper = context.getBean(CompiledFixtures.UserMapper.class);
            assertThat(mapper.toDto(new CompiledFixtures.User("Ada")).name).isEqualTo("Ada");
            assertThat(context.getBean(CompiledFixtures.UserNavigator.class)
                    .name(Map.of("name", "Ada"))).isEqualTo("Ada");
            assertThat(context.getBean(CompiledFixtures.UserBinder.class)
                    .read("{\"name\":\"Ada\"}").name).isEqualTo("Ada");
            assertThat(context.getBean(CompiledFixtures.UserValidator.class)
                    .valid(Map.of("name", "Ada"))).isTrue();

            Consumer consumer = context.getBean(Consumer.class);
            assertThat(consumer.mapper).isSameAs(mapper);
            assertThat(consumer.provider.getObject()).isSameAs(mapper);
            assertThat(consumer.mappers).containsExactly(mapper);
        });
    }

    @Test
    void explicitUserBeanWins() {
        runner.withUserConfiguration(OverrideConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CompiledFixtures.UserMapper.class);
            assertThat(context.getBean(CompiledFixtures.UserMapper.class)
                    .toDto(new CompiledFixtures.User("Ada")).name).isEqualTo("custom");
        });
    }

    @Test
    void explicitPrototypeBeanWins() {
        runner.withUserConfiguration(PrototypeOverrideConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(CompiledFixtures.UserMapper.class);
            assertThat(context.getBeanNamesForType(CompiledFixtures.UserMapper.class))
                    .containsExactly("prototypeMapper");
            assertThat(context.getBeanFactory().isPrototype("prototypeMapper")).isTrue();

            CompiledFixtures.UserMapper first = context.getBean(CompiledFixtures.UserMapper.class);
            CompiledFixtures.UserMapper second = context.getBean(CompiledFixtures.UserMapper.class);
            assertThat(first).isNotSameAs(second);
            assertThat(first.toDto(new CompiledFixtures.User("Ada")).name).isEqualTo("prototype");
        });
    }

    @Test
    void explicitEnableAndBootAutoConfigurationShareOneRegistrar() {
        runner.withUserConfiguration(AlsoEnableSjf4jConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CompiledBeanDefinitionRegistrar.class);
            assertThat(context).hasSingleBean(CompiledFixtures.UserMapper.class);
        });
    }

    @Test
    void acceptsUserRuntimeContext() {
        runner.withBean(RuntimeContext.class, () -> RuntimeContext.EMPTY)
                .run(context -> assertThat(context).hasSingleBean(CompiledFixtures.UserMapper.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableSjf4j
    static class AlsoEnableSjf4jConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean
        Consumer consumer(CompiledFixtures.UserMapper mapper,
                ObjectProvider<CompiledFixtures.UserMapper> provider,
                List<CompiledFixtures.UserMapper> mappers) {
            return new Consumer(mapper, provider, mappers);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OverrideConfiguration {
        @Bean
        CompiledFixtures.UserMapper customMapper() {
            return source -> {
                CompiledFixtures.UserDto result = new CompiledFixtures.UserDto();
                result.name = "custom";
                return result;
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrototypeOverrideConfiguration {
        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        CompiledFixtures.UserMapper prototypeMapper() {
            // Use a new object rather than a stateless lambda (which the JVM may cache).
            return new CompiledFixtures.UserMapper() {
                @Override
                public CompiledFixtures.UserDto toDto(CompiledFixtures.User source) {
                    CompiledFixtures.UserDto result = new CompiledFixtures.UserDto();
                    result.name = "prototype";
                    return result;
                }
            };
        }
    }

    static final class Consumer {
        final CompiledFixtures.UserMapper mapper;
        final ObjectProvider<CompiledFixtures.UserMapper> provider;
        final List<CompiledFixtures.UserMapper> mappers;
        Consumer(CompiledFixtures.UserMapper mapper,
                ObjectProvider<CompiledFixtures.UserMapper> provider,
                List<CompiledFixtures.UserMapper> mappers) {
            this.mapper = mapper;
            this.provider = provider;
            this.mappers = mappers;
        }
    }
}

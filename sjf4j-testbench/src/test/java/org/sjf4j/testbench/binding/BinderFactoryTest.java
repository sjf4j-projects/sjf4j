package org.sjf4j.testbench.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.backend.fastjson2.binding.Fastjson2BinderProvider;
import org.sjf4j.backend.gson.binding.GsonBinderProvider;
import org.sjf4j.backend.jackson2.binding.Jackson2BinderProvider;
import org.sjf4j.backend.jackson3.binding.Jackson3BinderProvider;
import org.sjf4j.backend.jsonp.binding.JsonpBinderProvider;
import org.sjf4j.backend.snake.binding.SnakeBinderProvider;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinderFactoryTest {

    @Test
    void discoversAllBackendsAndSelectsHighestPriorityProviderPerFormat() {
        List<BinderProvider> jsonProviders = BinderFactory.providers(Format.JSON);
        List<BinderProvider> yamlProviders = BinderFactory.providers(Format.YAML);

        assertTrue(jsonProviders.stream().anyMatch(Jackson3BinderProvider.class::isInstance));
        assertTrue(jsonProviders.stream().anyMatch(Jackson2BinderProvider.class::isInstance));
        assertTrue(jsonProviders.stream().anyMatch(GsonBinderProvider.class::isInstance));
        assertTrue(jsonProviders.stream().anyMatch(Fastjson2BinderProvider.class::isInstance));
        assertTrue(jsonProviders.stream().anyMatch(JsonpBinderProvider.class::isInstance));
        assertTrue(yamlProviders.stream().anyMatch(SnakeBinderProvider.class::isInstance));

        assertInstanceOf(Jackson3BinderProvider.class, BinderFactory.provider(Format.JSON));
        assertInstanceOf(SnakeBinderProvider.class, BinderFactory.provider(Format.YAML));
    }

}

package org.sjf4j.backend.snake.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BindingFactory;
import org.sjf4j.binding.Format;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnakeBinderProviderTest {

    @Test
    void createsBinderWithSuppliedOptions() {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setIndent(4);
        BinderProvider provider = SnakeBinderProvider.of(new LoaderOptions(), dumperOptions);

        SnakeBinder binder = assertInstanceOf(SnakeBinder.class, provider.create(RuntimeContext.EMPTY));

        assertTrue(binder.writeNodeAsString(Map.of("parent", Map.of("child", "value")))
                .contains("\n    child: value"));
    }

    @Test
    void isDiscoveredAsTheDefaultYamlBinderProvider() {
        BinderProvider provider = BindingFactory.provider(Format.YAML);

        assertInstanceOf(SnakeBinderProvider.class, provider);
        assertInstanceOf(SnakeBinder.class, provider.create(RuntimeContext.EMPTY));
    }

    @Test
    void rejectsNullOptions() {
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();

        assertThrows(NullPointerException.class, () -> SnakeBinderProvider.of(null, dumperOptions));
        assertThrows(NullPointerException.class, () -> SnakeBinderProvider.of(loaderOptions, null));
    }
}

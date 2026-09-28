package org.sjf4j.backend.jackson3.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BindingFactory;
import org.sjf4j.binding.Format;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3BinderProviderTest {

    @Test
    void createsBinderWithSuppliedFactory() {
        JsonFactory factory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
        BinderProvider provider = Jackson3BinderProvider.of(factory);

        Jackson3Binder binder = assertInstanceOf(Jackson3Binder.class,
                provider.create(RuntimeContext.EMPTY));

        assertNull(binder.readNode("/* configured */ null", Object.class));
    }

    @Test
    void rejectsNullFactory() {
        assertThrows(NullPointerException.class, () -> Jackson3BinderProvider.of(null));
    }

    @Test
    void listsCachedDiscoveredProviders() {
        BinderProvider provider = BindingFactory.provider(Format.JSON);

        assertTrue(BindingFactory.providers().contains(provider));
        assertTrue(BindingFactory.providers(Format.JSON).contains(provider));
        assertThrows(UnsupportedOperationException.class,
                () -> BindingFactory.providers().add(provider));
        assertThrows(UnsupportedOperationException.class,
                () -> BindingFactory.providers(Format.JSON).add(provider));
    }
}

package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BindingFactory;
import org.sjf4j.binding.Format;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Jackson2BinderProviderTest {

    @Test
    void createsBinderWithSuppliedFactory() {
        JsonFactory factory = new JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS);
        BinderProvider provider = Jackson2BinderProvider.of(factory);

        Jackson2Binder binder = assertInstanceOf(Jackson2Binder.class,
                provider.create(RuntimeContext.EMPTY));

        assertNull(binder.readNode("/* configured */ null", Object.class));
    }

    @Test
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BindingFactory.provider(Format.JSON);

        assertInstanceOf(Jackson2BinderProvider.class, provider);
        assertInstanceOf(Jackson2Binder.class, provider.create(RuntimeContext.EMPTY));
    }

    @Test
    void rejectsNullFactory() {
        assertThrows(NullPointerException.class, () -> Jackson2BinderProvider.of(null));
    }
}

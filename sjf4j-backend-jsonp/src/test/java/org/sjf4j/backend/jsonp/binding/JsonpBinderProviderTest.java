package org.sjf4j.backend.jsonp.binding;

import jakarta.json.spi.JsonProvider;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonpBinderProviderTest {

    @Test
    void createsBinderWithSuppliedJsonProvider() {
        BinderProvider provider = JsonpBinderProvider.of(JsonProvider.provider());

        JsonpBinder binder = assertInstanceOf(JsonpBinder.class, provider.create(RuntimeContext.EMPTY));

        assertNull(binder.readNode("null", Object.class));
    }

    @Test
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BinderFactory.provider(Format.JSON);

        assertInstanceOf(JsonpBinderProvider.class, provider);
        assertInstanceOf(JsonpBinder.class, provider.create(RuntimeContext.EMPTY));
    }

    @Test
    void rejectsNullJsonProvider() {
        assertThrows(NullPointerException.class, () -> JsonpBinderProvider.of(null));
    }
}

package org.sjf4j.backend.jsonp.binding;

import jakarta.json.spi.JsonProvider;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void remainsUnavailableWhenOnlyJsonpApiIsVisible() throws Exception {
        URL api = JsonProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL core = BinderProvider.class.getProtectionDomain().getCodeSource().getLocation();
        URL backend = JsonpBinderProvider.class.getProtectionDomain().getCodeSource().getLocation();

        // Isolate API/backend from the provider implementation and ServiceLoader.
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{api, core, backend}, null)) {
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> providerClass = Class.forName(
                    "org.sjf4j.backend.jsonp.binding.JsonpBinderProvider", true, loader);
            Object provider = providerClass.getConstructor().newInstance();
            assertEquals(false, providerClass.getMethod("isAvailable").invoke(provider));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void rejectsNullJsonProvider() {
        assertThrows(NullPointerException.class, () -> JsonpBinderProvider.of(null));
    }
}

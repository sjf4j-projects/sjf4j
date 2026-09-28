package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GsonBinderProviderTest {

    @Test
    void createsBinderWithSuppliedGson() {
        Gson gson = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
        BinderProvider provider = GsonBinderProvider.of(gson);

        GsonBinder binder = assertInstanceOf(GsonBinder.class, provider.create(RuntimeContext.EMPTY));

        assertNull(binder.readNode("/* configured */ null", Object.class));
    }

    @Test
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BinderFactory.provider(Format.JSON);

        assertInstanceOf(GsonBinderProvider.class, provider);
        assertInstanceOf(GsonBinder.class, provider.create(RuntimeContext.EMPTY));
    }

    @Test
    void rejectsNullGson() {
        assertThrows(NullPointerException.class, () -> GsonBinderProvider.of(null));
    }
}

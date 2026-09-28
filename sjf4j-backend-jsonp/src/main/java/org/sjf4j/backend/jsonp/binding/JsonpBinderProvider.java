package org.sjf4j.backend.jsonp.binding;

import jakarta.json.spi.JsonProvider;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

/** Service provider for the JSON-P JSON binder. */
public final class JsonpBinderProvider implements BinderProvider {

    private static final String JSON_PROVIDER_TYPE = "jakarta.json.spi.JsonProvider";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_PROVIDER_TYPE, JsonpBinderProvider.class.getClassLoader());

    public JsonpBinderProvider() {
    }

    /**
     * Creates a provider that uses {@code provider} for every binder it creates.
     */
    public static BinderProvider of(JsonProvider provider) {
        JsonProvider checkedProvider = Asserts.notNull(provider, "provider");
        return BinderProvider.of(Format.JSON, 200,
                context -> new JsonpBinder(checkedProvider, context));
    }

    @Override
    public Format format() {
        return Format.JSON;
    }

    @Override
    public boolean isAvailable() {
        return AVAILABLE;
    }

    @Override
    public int priority() {
        return 200;
    }

    @Override
    public Binder<?, ?> create(RuntimeContext context) {
        return new JsonpBinder(JsonProvider.provider(), context);
    }
}

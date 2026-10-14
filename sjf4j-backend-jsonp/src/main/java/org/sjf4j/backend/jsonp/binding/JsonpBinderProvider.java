package org.sjf4j.backend.jsonp.binding;

import jakarta.json.JsonException;
import jakarta.json.spi.JsonProvider;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

/**
 * Service provider for JSON-P binding. Availability requires a JSON-P
 * implementation, not just the Jakarta API; this is the lowest-priority
 * optional JSON backend.
 */
public final class JsonpBinderProvider implements BinderProvider {

    private static final String JSON_PROVIDER_TYPE = "jakarta.json.spi.JsonProvider";
    private static final boolean AVAILABLE = detectAvailability();

    /** Creates a discoverable provider for the installed JSON-P implementation. */
    public JsonpBinderProvider() {
    }

    /**
     * Creates a provider that uses {@code provider} for every binder it creates.
     * @param provider the JSON-P implementation
     * @return a JSON binder provider using this implementation
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
        if (!AVAILABLE) {
            throw new IllegalStateException("no Jakarta JSON-P implementation available; add a JSON-P provider");
        }
        return new JsonpBinder(JsonProvider.provider(), context);
    }

    private static boolean detectAvailability() {
        if (!BinderProvider.isClassAvailable(
                JSON_PROVIDER_TYPE, JsonpBinderProvider.class.getClassLoader())) {
            return false;
        }
        try {
            JsonProvider.provider();
            return true;
        } catch (JsonException e) {
            // JSON-P 2.1 falls back to its default provider class when no service
            // is present. Only a missing default implementation is optional;
            // an explicitly configured provider failure must remain visible.
            if (e.getCause() instanceof ClassNotFoundException
                    && System.getProperty(JsonProvider.JSONP_PROVIDER_FACTORY) == null) {
                return false;
            }
            throw e;
        }
    }
}

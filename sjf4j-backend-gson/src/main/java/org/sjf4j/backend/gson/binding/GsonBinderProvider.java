package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

/**
 * Service provider for Gson JSON binding, selected below Jackson and above
 * Fastjson2 by default. {@link #of(Gson)} uses the supplied Gson instance.
 */
public final class GsonBinderProvider implements BinderProvider {

    private static final String GSON_TYPE = "com.google.gson.Gson";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            GSON_TYPE, GsonBinderProvider.class.getClassLoader());

    /** Creates a discoverable provider with default Gson settings. */
    public GsonBinderProvider() {
    }

    /**
     * Creates a provider that uses {@code gson} for every binder it creates.
     * @param gson the shared Gson instance
     * @return a JSON binder provider using this Gson instance
     */
    public static BinderProvider of(Gson gson) {
        Gson checkedGson = Asserts.notNull(gson, "gson");
        return BinderProvider.of(Format.JSON, 400,
                context -> new GsonBinder(checkedGson, context));
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
        return 400;
    }

    @Override
    public Binder<?, ?> create(RuntimeContext context) {
        return new GsonBinder(new Gson(), context);
    }
}

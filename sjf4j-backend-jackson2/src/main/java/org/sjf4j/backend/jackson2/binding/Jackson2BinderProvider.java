package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.StreamingBinder;
import org.sjf4j.util.Asserts;

/** Service provider for the Jackson 2 JSON binder. */
public final class Jackson2BinderProvider implements BinderProvider {

    private static final String JSON_FACTORY_TYPE = "com.fasterxml.jackson.core.JsonFactory";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_FACTORY_TYPE, Jackson2BinderProvider.class.getClassLoader());

    public Jackson2BinderProvider() {
    }

    /**
     * Creates a provider that uses {@code factory} for every binder it creates.
     */
    public static BinderProvider of(JsonFactory factory) {
        JsonFactory checkedFactory = Asserts.notNull(factory, "factory");
        return BinderProvider.of(Format.JSON, 500,
                context -> new Jackson2Binder(checkedFactory, context));
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
        return 500;
    }

    @Override
    public StreamingBinder<?, ?> create(RuntimeContext context) {
        return new Jackson2Binder(new JsonFactory(), context);
    }

}

package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

/**
 * Service provider for Jackson 2 JSON binding, selected below Jackson 3 by
 * default. {@link #of(JsonFactory)} reuses and configures the supplied factory.
 */
public final class Jackson2BinderProvider implements BinderProvider {

    private static final String JSON_FACTORY_TYPE = "com.fasterxml.jackson.core.JsonFactory";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_FACTORY_TYPE, Jackson2BinderProvider.class.getClassLoader());

    /** Creates a discoverable provider with a default Jackson 2 factory. */
    public Jackson2BinderProvider() {
    }

    /**
     * Creates a provider that uses {@code factory} for every binder it creates.
     * @param factory the shared Jackson 2 factory, configured in place
     * @return a JSON binder provider using this factory
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
    public Binder<?, ?> create(RuntimeContext context) {
        return new Jackson2Binder(new JsonFactory(), context);
    }

}

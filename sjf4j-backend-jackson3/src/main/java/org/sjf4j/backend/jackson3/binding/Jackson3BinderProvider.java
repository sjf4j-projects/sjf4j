package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;
import tools.jackson.core.json.JsonFactory;

/**
 * Service provider for Jackson 3 JSON binding, the highest-priority built-in
 * JSON backend. {@link #of(JsonFactory)} uses a rebuilt copy of the factory.
 */
public final class Jackson3BinderProvider implements BinderProvider {

    private static final String JSON_FACTORY_TYPE = "tools.jackson.core.json.JsonFactory";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_FACTORY_TYPE, Jackson3BinderProvider.class.getClassLoader());

    /** Creates a discoverable provider with a default Jackson 3 factory. */
    public Jackson3BinderProvider() {
    }

    /**
     * Creates a provider that uses {@code factory} for every binder it creates.
     * @param factory the Jackson 3 factory rebuilt for each binder
     * @return a JSON binder provider using this factory
     */
    public static BinderProvider of(JsonFactory factory) {
        JsonFactory checkedFactory = Asserts.notNull(factory, "factory");
        return BinderProvider.of(Format.JSON, 600,
                context -> new Jackson3Binder(checkedFactory, context));
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
        return 600;
    }

    @Override
    public Binder<?, ?> create(RuntimeContext context) {
        return new Jackson3Binder(new JsonFactory(), context);
    }

}

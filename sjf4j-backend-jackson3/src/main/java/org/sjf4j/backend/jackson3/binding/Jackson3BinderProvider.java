package org.sjf4j.backend.jackson3.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;
import tools.jackson.core.json.JsonFactory;

/** Service provider for the Jackson 3 JSON binder. */
public final class Jackson3BinderProvider implements BinderProvider {

    private static final String JSON_FACTORY_TYPE = "tools.jackson.core.json.JsonFactory";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_FACTORY_TYPE, Jackson3BinderProvider.class.getClassLoader());

    public Jackson3BinderProvider() {
    }

    /**
     * Creates a provider that uses {@code factory} for every binder it creates.
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

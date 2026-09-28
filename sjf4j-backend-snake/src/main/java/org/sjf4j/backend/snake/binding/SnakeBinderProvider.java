package org.sjf4j.backend.snake.binding;

import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;

/** Service provider for the SnakeYAML binder. */
public final class SnakeBinderProvider implements BinderProvider {

    private static final String YAML_TYPE = "org.yaml.snakeyaml.Yaml";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            YAML_TYPE, SnakeBinderProvider.class.getClassLoader());

    public SnakeBinderProvider() {
    }

    /**
     * Creates a provider that uses the supplied options for every binder it creates.
     */
    public static BinderProvider of(LoaderOptions loaderOptions, DumperOptions dumperOptions) {
        LoaderOptions checkedLoaderOptions = Asserts.notNull(loaderOptions, "loaderOptions");
        DumperOptions checkedDumperOptions = Asserts.notNull(dumperOptions, "dumperOptions");
        return BinderProvider.of(Format.YAML, 100,
                context -> new SnakeBinder(checkedLoaderOptions, checkedDumperOptions, context));
    }

    @Override
    public Format format() {
        return Format.YAML;
    }

    @Override
    public boolean isAvailable() {
        return AVAILABLE;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Binder<?, ?> create(RuntimeContext context) {
        return new SnakeBinder(new LoaderOptions(), new DumperOptions(), context);
    }
}

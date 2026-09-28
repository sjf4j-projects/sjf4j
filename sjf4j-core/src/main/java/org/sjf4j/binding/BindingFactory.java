package org.sjf4j.binding;

import org.sjf4j.binding.simple.SimpleJsonBinder;
import org.sjf4j.binding.simple.SimplePropertiesBinder;
import org.sjf4j.binding.simple.SimpleYamlBinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Resolves the framework-default binder providers.
 *
 * <p>Providers are discovered once through {@link ServiceLoader}. For each
 * format, the available provider with the highest priority is selected; equal
 * priorities are rejected to keep backend selection deterministic. JSON falls
 * back to the built-in simple binder and YAML to the built-in unavailable YAML
 * binder when no optional provider is available.</p>
 */
public final class BindingFactory {

    private static final BinderProvider SIMPLE_JSON =
            BinderProvider.of(Format.JSON, 0, SimpleJsonBinder::new);
    private static final BinderProvider SIMPLE_YAML =
            BinderProvider.of(Format.YAML, 0, SimpleYamlBinder::new);

    private static final List<BinderProvider> PROVIDERS = load();
    private static final Map<Format, BinderProvider> SELECTED_PROVIDERS = select(PROVIDERS);

    private BindingFactory() {
    }

    /**
     * Returns the selected provider for {@code format}.
     */
    public static BinderProvider provider(Format format) {
        Objects.requireNonNull(format, "format");
        BinderProvider provider = SELECTED_PROVIDERS.get(format);
        if (provider != null) {
            return provider;
        }
        if (Format.JSON.equals(format)) {
            return SIMPLE_JSON;
        }
        if (Format.YAML.equals(format)) {
            return SIMPLE_YAML;
        }
        throw new IllegalArgumentException("no binding provider available for format '" + format.id() + "'");
    }

    /**
     * Returns the selected JSON binder provider.
     */
    public static BinderProvider jsonBinderProvider() {
        return provider(Format.JSON);
    }

    /**
     * Returns the selected YAML binder provider.
     */
    public static BinderProvider yamlBinderProvider() {
        return provider(Format.YAML);
    }

    /**
     * Returns all ServiceLoader-discovered providers in discovery order.
     *
     * <p>The returned list includes unavailable providers and is immutable.</p>
     */
    public static List<BinderProvider> providers() {
        return PROVIDERS;
    }

    /**
     * Returns all ServiceLoader-discovered providers for {@code format} in discovery order.
     *
     * <p>The returned list includes unavailable providers and is immutable.</p>
     */
    public static List<BinderProvider> providers(Format format) {
        Objects.requireNonNull(format, "format");
        List<BinderProvider> result = new ArrayList<>();
        for (BinderProvider provider : PROVIDERS) {
            if (format.equals(provider.format())) {
                result.add(provider);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<BinderProvider> load() {
        ClassLoader loader = BinderProvider.class.getClassLoader();
        List<BinderProvider> providers = new ArrayList<>();
        for (BinderProvider provider : ServiceLoader.load(BinderProvider.class, loader)) {
            providers.add(provider);
        }
        return Collections.unmodifiableList(providers);
    }

    private static Map<Format, BinderProvider> select(List<BinderProvider> providers) {
        Map<Format, BinderProvider> selected = new LinkedHashMap<>();
        for (BinderProvider provider : providers) {
            Format format = Objects.requireNonNull(provider.format(), "provider.format()");
            if (!provider.isAvailable()) {
                continue;
            }
            BinderProvider old = selected.get(format);
            int priority = provider.priority();
            if (old == null || priority > old.priority()) {
                selected.put(format, provider);
            } else if (priority == old.priority()) {
                throw new IllegalStateException("multiple binding providers have priority " + priority
                        + " for format '" + format.id() + "': '" + old.getClass().getName() + "' and '"
                        + provider.getClass().getName() + "'");
            }
        }
        return selected;
    }

}

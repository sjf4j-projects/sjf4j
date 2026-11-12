package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;
import org.sjf4j.schema.SchemaRegistry;

import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates implementations of {@code @CompiledValidator} interfaces.
 *
 * <p>Owns interface validation and generated-class lifecycle. Method analysis,
 * schema selection, keyword compilation and shared fallback preparation are
 * delegated to {@link SchemaMethodGenerator}.</p>
 */
public final class SchemaGenerator {

    private final ProcessorContext context;
    // One directory load per annotation-processing run, shared by interfaces.
    private final Map<String, SchemaRegistry> registryCache = new LinkedHashMap<>();
    private final Map<String, Map<URI, URI>> externalSchemas;

    public SchemaGenerator(ProcessorContext context) {
        this.context = context;
        this.externalSchemas = loadExternalIndex(context);
    }

    /**
     * An internal javac fixture bridge. The index is scoped by the validator's
     * fully qualified interface name, so independent official groups cannot
     * collide even when they reuse the same retrieval URI.
     *
     * This is deliberately not part of @CompiledValidator or its public API.
     * Normal annotation-processing runs have no index and use registryDir.
     */
    private static Map<String, Map<URI, URI>> loadExternalIndex(ProcessorContext context) {
        String path = context.processingEnvironment.getOptions()
                .get("sjf4j.internal.schemaExternalIndex");
        if (path == null) return Collections.emptyMap();

        Path index = Paths.get(path);
        final List<String> lines;
        try {
            lines = Files.readAllLines(index, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read schema resource index: " + index, e);
        }
        if (lines.isEmpty() || !"class\turi\tsource".equals(lines.get(0))) {
            throw new IllegalArgumentException("Invalid schema resource index: " + index);
        }

        Map<String, Map<URI, URI>> bindings = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isEmpty()) continue;
            String[] fields = lines.get(i).split("\\t", -1);
            if (fields.length != 3 || fields[0].isEmpty()) {
                throw new IllegalArgumentException("Invalid schema resource index row " + (i + 1));
            }
            URI logical = URI.create(fields[1]);
            URI source = URI.create(fields[2]);
            if (!logical.isAbsolute() || !source.isAbsolute()
                    || !("classpath".equals(source.getScheme()) || "file".equals(source.getScheme()))) {
                throw new IllegalArgumentException("Invalid schema URI mapping at row " + (i + 1)
                        + ": " + logical + " -> " + source);
            }
            Map<URI, URI> sources = bindings.computeIfAbsent(fields[0],
                    ignored -> new LinkedHashMap<>());
            if (sources.putIfAbsent(logical, source) != null) {
                throw new IllegalArgumentException("Duplicate schema retrieval URI for "
                        + fields[0] + ": " + logical);
            }
        }
        return bindings;
    }

    /** Generates one compiled validator implementation. */
    public void generate(TypeElement iface) {
        if (!iface.getTypeParameters().isEmpty()) {
            context.error(iface, "@CompiledValidator cannot declare type parameters");
            return;
        }

        GeneratedClass generated = GeneratedClass.forInterface(context, iface);
        // Keep the CompiledInstances constructor protocol unchanged.
        generated.enableRuntimeContext();

        CompiledValidator options = iface.getAnnotation(CompiledValidator.class);
        // A fresh method generator per interface preserves plan sharing within
        // that interface without leaking metadata across generated classes.
        SchemaMethodGenerator methods = new SchemaMethodGenerator(
                context, generated, options.registryDir(), options.registryBaseUri(), registryCache,
                externalSchemas.getOrDefault(iface.getQualifiedName().toString(), Collections.emptyMap()));
        for (ResolvedMethod method : context.methods.abstractMethods(iface)) {
            methods.generate(method, generated, options.fallback());
        }

        if (!generated.isValid()) return;
        methods.emitFallbacks(generated);
        generated.write();
    }
}

package org.sjf4j.processor.schema;

import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.processor.ProcessorContext;
import org.sjf4j.processor.code.GeneratedClass;
import org.sjf4j.processor.method.ResolvedMethod;

import javax.lang.model.element.TypeElement;

/**
 * Generates implementations of {@code @CompiledValidator} interfaces.
 *
 * <p>Owns interface validation and generated-class lifecycle. Method analysis,
 * schema selection, keyword compilation and shared fallback preparation are
 * delegated to {@link SchemaMethodGenerator}.</p>
 */
public final class SchemaGenerator {

    private final ProcessorContext context;

    public SchemaGenerator(ProcessorContext context) {
        this.context = context;
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
        SchemaMethodGenerator methods = new SchemaMethodGenerator(context);
        for (ResolvedMethod method : context.methods.abstractMethods(iface)) {
            methods.generate(method, generated, options.fallback());
        }

        if (!generated.isValid()) return;
        methods.emitFallbacks(generated);
        generated.write();
    }
}

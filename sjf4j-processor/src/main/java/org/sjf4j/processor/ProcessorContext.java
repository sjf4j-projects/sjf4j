package org.sjf4j.processor;

import org.sjf4j.processor.access.NodeAccessResolver;
import org.sjf4j.processor.discovery.CompiledMetadata;
import org.sjf4j.processor.discovery.RegistryDiscovery;
import org.sjf4j.processor.annotation.NodeAnnotations;
import org.sjf4j.processor.method.InterfaceMethodResolver;
import org.sjf4j.processor.property.PropertyResolver;
import org.sjf4j.processor.type.TypeSystem;
import org.sjf4j.util.Asserts;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compiler services and shared metadata resolvers for one processing run.
 * Errors are reported against source elements rather than written into
 * generated code.
 */
public final class ProcessorContext {

    public final Types typeUtils;
    public final Elements elements;
    public final Messager messager;
    public final Filer filer;
    public final ProcessingEnvironment processingEnvironment;

    public final TypeSystem types;
    public final RegistryDiscovery discovery;
    public final NodeAnnotations annotations;
    public final PropertyResolver properties;
    public final NodeAccessResolver access;
    public final InterfaceMethodResolver methods;

    private final Map<String, TypeElement> compiledInterfaces = new TreeMap<>();

    public ProcessorContext(ProcessingEnvironment environment) {
        Asserts.notNull(environment, "environment");

        this.typeUtils = environment.getTypeUtils();
        this.elements = environment.getElementUtils();
        this.messager = environment.getMessager();
        this.filer = environment.getFiler();
        this.processingEnvironment = environment;

        this.types = new TypeSystem(typeUtils, elements);
        this.discovery = new RegistryDiscovery(this);
        this.annotations = new NodeAnnotations();
        this.properties = new PropertyResolver(typeUtils, elements, messager, types, annotations);
        this.access = new NodeAccessResolver(types, properties);
        this.methods = new InterfaceMethodResolver(typeUtils, elements, types);
    }

    /** Records an implementation only after its source file was emitted successfully. */
    public void recordCompiledInterface(TypeElement type) {
        compiledInterfaces.put(elements.getBinaryName(type).toString(), type);
    }

    /** Writes a framework-neutral index once, in the final processing round. */
    public void writeCompiledIndex() {
        if (compiledInterfaces.isEmpty()) return;

        try {
            FileObject file = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", "META-INF/sjf4j/compiled.idx",
                    compiledInterfaces.values().toArray(new Element[0]));
            try (Writer out = file.openWriter()) {
                for (String name : compiledInterfaces.keySet()) {
                    out.write(name);
                    out.write('\n');
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write SJF4J compiled interface index: " + e.getMessage());
        }
    }

    /** Per-interface, immutable-source metadata view. */
    public CompiledMetadata metadata(TypeElement owner) {
        return new CompiledMetadata(this, owner);
    }

    /** Emits a compile-time error at the supplied source element. */
    public void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }

}

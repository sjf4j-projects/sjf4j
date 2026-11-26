package org.sjf4j.processor;

import org.sjf4j.processor.access.NodeAccessResolver;
import org.sjf4j.processor.annotation.NodeAnnotations;
import org.sjf4j.processor.discovery.CompiledMetadata;
import org.sjf4j.processor.method.InterfaceMethodResolver;
import org.sjf4j.processor.property.PropertyResolver;
import org.sjf4j.processor.type.TypeSystem;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.List;

/**
 * Compiler services for exactly one compiled interface.
 * Registry visibility and all dependent caches are fixed for its lifetime.
 */
public final class CompilationContext {

    private final ProcessorContext processor;
    public final TypeElement owner;
    public final Types typeUtils;
    public final Elements elements;
    public final Filer filer;
    public final ProcessingEnvironment processingEnvironment;

    public final TypeSystem types;
    public final NodeAnnotations annotations;
    public final PropertyResolver properties;
    public final NodeAccessResolver access;
    public final InterfaceMethodResolver methods;
    public final CompiledMetadata metadata;

    CompilationContext(ProcessorContext processor, TypeElement owner, boolean useRegistries) {
        this.processor = processor;
        this.owner = owner;
        this.typeUtils = processor.typeUtils;
        this.elements = processor.elements;
        this.filer = processor.filer;
        this.processingEnvironment = processor.processingEnvironment;

        List<TypeElement> registries = useRegistries
                ? processor.discovery.registries(owner) : Collections.emptyList();
        this.types = new TypeSystem(typeUtils, elements, processor.discovery.externalTypes(registries));
        this.annotations = new NodeAnnotations();
        this.properties = new PropertyResolver(typeUtils, elements, processor.messager, types, annotations);
        this.access = new NodeAccessResolver(types, properties);
        this.methods = new InterfaceMethodResolver(typeUtils, elements, types);
        this.metadata = new CompiledMetadata(this, registries);
    }

    public void error(Element element, String message) {
        processor.error(element, message);
    }

    public void recordCompiledInterface(TypeElement type) {
        processor.recordCompiledInterface(type);
    }
}

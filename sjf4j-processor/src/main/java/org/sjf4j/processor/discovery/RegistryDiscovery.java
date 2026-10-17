package org.sjf4j.processor.discovery;

import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.processor.ProcessorContext;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers registry holders. Only source declarations in the current processing
 * run can be enumerated; dependency classes require an explicit entry point.
 *
 * <p>This class discovers holder types, not codec instances. It never loads
 * application classes or executes initialization code.
 */
public final class RegistryDiscovery {

    private static final String BUILTIN_VALUE_CODECS = "org.sjf4j.value.ValueCodec";

    private final ProcessorContext context;
    private final Map<String, TypeElement> sourceRegistries = new LinkedHashMap<>();

    public RegistryDiscovery(ProcessorContext context) {
        this.context = context;
    }

    /** Collects registry holders declared in this compilation round. */
    public void collect(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(NodeValue.class)) {
            if (element.getKind() != ElementKind.FIELD) continue;
            Element holder = element.getEnclosingElement();
            if (holder instanceof TypeElement) {
                TypeElement type = (TypeElement) holder;
                sourceRegistries.put(type.getQualifiedName().toString(), type);
            }
        }
    }

    /** Built-in and source registries, with a stable order and no duplicates. */
    public List<TypeElement> registries(TypeElement owner) {
        Map<String, TypeElement> result = new LinkedHashMap<>();
        TypeElement builtins = context.elements.getTypeElement(BUILTIN_VALUE_CODECS);
        if (builtins != null) {
            result.put(builtins.getQualifiedName().toString(), builtins);
        }

        List<String> names = new ArrayList<>(sourceRegistries.keySet());
        Collections.sort(names);
        for (String name : names) {
            result.putIfAbsent(name, sourceRegistries.get(name));
        }
        return new ArrayList<>(result.values());
    }
}

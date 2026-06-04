package org.sjf4j.processor.path;

import org.sjf4j.processor.NameAllocator;

import javax.lang.model.element.VariableElement;
import java.util.Map;

/**
 * Holds the generated parameter name mapping and local name allocator for one
 * CompiledPath method emission.
 */
final class PathNameScope {
    final NameAllocator names;
    private final Map<VariableElement, String> params;

    PathNameScope(NameAllocator names, Map<VariableElement, String> params) {
        this.names = names;
        this.params = params;
    }

    String param(VariableElement element) {
        String name = params.get(element);
        return name == null ? element.getSimpleName().toString() : name;
    }
}

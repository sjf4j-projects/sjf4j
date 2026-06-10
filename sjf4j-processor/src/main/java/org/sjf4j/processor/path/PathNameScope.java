package org.sjf4j.processor.path;

import org.sjf4j.processor.NameAllocator;

import javax.lang.model.element.VariableElement;
import java.util.Map;

/**
 * Holds the generated parameter name mapping and local name allocator for one
 * CompiledPath method emission.
 *
 * <p>Path methods may contain dynamic path parameters whose Java source names
 * differ from generated method parameter names after collision avoidance.  This
 * scope provides a single lookup point for those names while sharing the same
 * allocator for path temporaries emitted by the method.</p>
 */
final class PathNameScope {
    final NameAllocator names;
    private final Map<VariableElement, String> params;

    PathNameScope(NameAllocator names, Map<VariableElement, String> params) {
        this.names = names;
        this.params = params;
    }

    /**
     * Returns the generated source name for a method parameter, falling back to
     * the declared name when no remapping was necessary.
     */
    String param(VariableElement element) {
        String name = params.get(element);
        return name == null ? element.getSimpleName().toString() : name;
    }
}

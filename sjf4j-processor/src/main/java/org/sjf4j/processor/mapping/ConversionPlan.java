package org.sjf4j.processor.mapping;

import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Fully compiled interface-wide conversion graph and its imported mapper dependencies. */
final class ConversionPlan {

    private final Map<ConverterResolver.Conversion, ConversionCompiler.CompiledConversion> compiled;
    private final List<ConversionCompiler.CompiledConversion> ordered;
    private final List<TypeElement> importedMappers;

    ConversionPlan(Map<ConverterResolver.Conversion, ConversionCompiler.CompiledConversion> compiled,
                   List<ConversionCompiler.CompiledConversion> ordered,
                   List<TypeElement> importedMappers) {
        this.compiled = Collections.unmodifiableMap(new IdentityHashMap<>(compiled));
        this.ordered = Collections.unmodifiableList(new ArrayList<>(ordered));
        this.importedMappers = Collections.unmodifiableList(new ArrayList<>(importedMappers));
    }

    ConversionCompiler.CompiledConversion get(ConverterResolver.Conversion selected) {
        return compiled.get(selected);
    }

    List<ConversionCompiler.CompiledConversion> ordered() {
        return ordered;
    }

    List<TypeElement> importedMappers() {
        return importedMappers;
    }
}

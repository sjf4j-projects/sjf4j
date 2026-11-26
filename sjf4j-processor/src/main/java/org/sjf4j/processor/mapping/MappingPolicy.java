package org.sjf4j.processor.mapping;

import org.sjf4j.annotation.mapping.ArrayPolicy;
import org.sjf4j.annotation.mapping.NullValuePolicy;
import org.sjf4j.annotation.mapping.ObjectPolicy;
import org.sjf4j.annotation.mapping.RawPolicy;
import org.sjf4j.annotation.mapping.ReusePolicy;
import org.sjf4j.processor.annotation.AnnotationValues;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolved method-level mapping options, shared by nested conversion plans. */
final class MappingPolicy {

    final NullValuePolicy nulls;
    final ArrayPolicy arrays;
    final ObjectPolicy objects;
    final RawPolicy raws;
    final ReusePolicy reuse;
    final List<String> converters;

    private MappingPolicy(NullValuePolicy nulls, ArrayPolicy arrays, ObjectPolicy objects,
                          RawPolicy raws, ReusePolicy reuse, List<String> converters) {
        this.nulls = nulls;
        this.arrays = arrays;
        this.objects = objects;
        this.raws = raws;
        this.reuse = reuse;
        this.converters = Collections.unmodifiableList(new ArrayList<>(converters));
    }

    static MappingPolicy resolve(Elements elements, AnnotationMirror options) {
        return new MappingPolicy(
                enumValue(elements, options, "nulls", NullValuePolicy.SET_TO_NULL),
                enumValue(elements, options, "arrays", ArrayPolicy.SET),
                enumValue(elements, options, "objects", ObjectPolicy.PUT),
                enumValue(elements, options, "raws", RawPolicy.STRICT),
                enumValue(elements, options, "reuse", ReusePolicy.ASSIGNABLE),
                converters(elements, options));
    }

    private static <E extends Enum<E>> E enumValue(Elements elements, AnnotationMirror options,
                                                    String name, E fallback) {
        String value = AnnotationValues.text(AnnotationValues.withDefaults(elements, options, name),
                fallback.name());
        return Enum.valueOf(fallback.getDeclaringClass(), value);
    }

    private static List<String> converters(Elements elements, AnnotationMirror options) {
        AnnotationValue value = AnnotationValues.withDefaults(elements, options, "converters");
        if (value == null || !(value.getValue() instanceof List)) return Collections.emptyList();

        List<String> references = new ArrayList<>();
        for (Object item : (List<?>) value.getValue()) {
            Object raw = ((AnnotationValue) item).getValue();
            if (raw instanceof String) {
                String reference = ((String) raw).trim();
                if (!reference.isEmpty()) references.add(reference);
            }
        }
        return references;
    }
}

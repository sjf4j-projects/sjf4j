package org.sjf4j.processor.discovery;

import org.sjf4j.processor.CompilationContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Metadata visible to one generated interface, independent of runtime registries. */
public final class CompiledMetadata {
    private final ValueCodecResolver valueCodecs;

    public CompiledMetadata(CompilationContext context, List<TypeElement> registries) {
        this.valueCodecs = new ValueCodecResolver(context, registries);
    }

    public boolean hasValueCodec(TypeMirror type) {
        return valueCodecs.contains(type);
    }

    public ValueCodecMetadata valueCodec(TypeMirror type, String format) {
        return valueCodecs.resolve(type, format);
    }

    public List<ValueCodecMetadata> valueCodecs(TypeMirror type) {
        return valueCodecs.all(type);
    }

    public TypeMirror stableCodecRawType(TypeMirror type, String format) {
        return valueCodecs.stableRawType(type, format);
    }
}

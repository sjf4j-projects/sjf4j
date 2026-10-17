package org.sjf4j.processor.discovery;

import org.sjf4j.processor.ProcessorContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Metadata visible to one generated interface, independent of runtime registries. */
public final class CompiledMetadata {
    private final ValueCodecResolver valueCodecs;

    public CompiledMetadata(ProcessorContext context, TypeElement owner) {
        this.valueCodecs = new ValueCodecResolver(context, context.discovery.registries(owner));
    }

    public ValueCodecMetadata valueCodec(TypeMirror type, String format) {
        return valueCodecs.resolve(type, format);
    }
}

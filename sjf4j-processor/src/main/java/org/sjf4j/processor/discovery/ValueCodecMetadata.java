package org.sjf4j.processor.discovery;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

/** Compile-time declaration of a registered ValueCodec, never a codec instance. */
public final class ValueCodecMetadata {
    public final TypeMirror valueType;
    public final TypeMirror rawType;
    public final String format;
    public final Element declaration;

    ValueCodecMetadata(TypeMirror valueType, TypeMirror rawType,
            String format, Element declaration) {
        this.valueType = valueType;
        this.rawType = rawType;
        this.format = format;
        this.declaration = declaration;
    }
}

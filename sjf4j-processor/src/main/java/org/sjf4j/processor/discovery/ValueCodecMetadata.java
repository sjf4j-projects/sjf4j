package org.sjf4j.processor.discovery;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/** Compile-time declaration of a registered ValueCodec, never a codec instance. */
public final class ValueCodecMetadata {
    public final TypeMirror valueType;
    public final TypeMirror rawType;
    public final List<String> formats;
    public final VariableElement declaration;

    ValueCodecMetadata(TypeMirror valueType, TypeMirror rawType,
                       List<String> formats, VariableElement declaration) {
        this.valueType = valueType;
        this.rawType = rawType;
        this.formats = formats;
        this.declaration = declaration;
    }
}

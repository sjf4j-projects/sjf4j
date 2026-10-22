package org.sjf4j.processor.binding;

/**
 * Compile-time candidates for direct built-in value IO.
 *
 * Only a selected built-in codec may use its intrinsic. Generated binders
 * verify the actual ValueInfo codec identity once during initialization,
 * preserving custom registrations and explicit format selection.
 */
enum IntrinsicCodec {
    UUID("java.util.UUID", "standard",
            "org.sjf4j.value.ValueCodec.UUID_CODEC", "readUuid", "writeUuid");

    private final String type;
    private final String format;
    private final String codec;
    private final String readMethod;
    private final String writeMethod;

    IntrinsicCodec(String type, String format, String codec,
                   String readMethod, String writeMethod) {
        this.type = type;
        this.format = format;
        this.codec = codec;
        this.readMethod = readMethod;
        this.writeMethod = writeMethod;
    }

    static IntrinsicCodec forValue(BindingValue value) {
        if (!value.isCodec() || value.codecPattern() != null) return null;
        for (IntrinsicCodec intrinsic : values()) {
            if (intrinsic.type.equals(value.type().toString())
                    && (value.codecName() == null
                    || intrinsic.format.equals(value.codecName()))) {
                return intrinsic;
            }
        }
        return null;
    }

    String fieldName(BindingValue value) {
        return value.helperName() + "Intrinsic";
    }

    String codec() { return codec; }
    String readMethod() { return readMethod; }
    String writeMethod() { return writeMethod; }
}

package org.sjf4j.binding;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Unified streaming writer for structured data.
 *
 * <p>The interface defines common structural semantics while allowing
 * implementations to provide backend-specific fast paths.</p>
 *
 * <p>Generated binders should prefer prepared {@link PropertyName}s,
 * primitive value methods, and property-value fused methods where possible.</p>
 */
public interface StreamingWriter extends Closeable, Flushable {

    /*
     * ----------------------------------------------------------------------
     * Prepared property names
     * ----------------------------------------------------------------------
     */

    /**
     * Prepared property name used by generated and runtime binders.
     *
     * <p>An implementation may carry backend-specific precomputed state,
     * for example:</p>
     *
     * <ul>
     *   <li>Fastjson2 encoded/raw field name</li>
     *   <li>Jackson SerializedString</li>
     *   <li>pre-escaped UTF-8 or UTF-16 representation</li>
     *   <li>plain String fallback</li>
     * </ul>
     */
    interface PropertyName {

        /**
         * Canonical property name.
         */
        String name();
    }


    /*
     * ----------------------------------------------------------------------
     * Document
     * ----------------------------------------------------------------------
     */

    /**
     * Prepares this writer to write one document.
     */
    default void startDocument() throws IOException {
    }

    /**
     * Completes the current document.
     *
     * <p>This method does not imply {@link #flush()} or {@link #close()}.</p>
     */
    default void endDocument() throws IOException {
    }

    /**
     * Transfers buffered output to {@code output}.
     *
     * <p>For implementations whose native writer is not backed by a
     * {@link Writer} or {@link OutputStream}. The default implementation does nothing.</p>
     */
    default void flushTo(Writer output) throws IOException {}
    default void flushTo(OutputStream output) throws IOException {}


    /*
     * ----------------------------------------------------------------------
     * Structure
     * ----------------------------------------------------------------------
     */

    void startObject() throws IOException;

    void endObject() throws IOException;

    void startArray() throws IOException;

    void endArray() throws IOException;

    default void separateProperty() throws IOException {}

    default void separateElement() throws IOException {}

    /*
     * ----------------------------------------------------------------------
     * Property names
     * ----------------------------------------------------------------------
     */

    /**
     * Writes an object property name.
     */
    void writeName(String name) throws IOException;

    /**
     * Writes a prepared object property name.
     *
     * <p>This is the preferred API for generated and cached runtime
     * bindings. Backends should override this method when they can use
     * the prepared representation directly.</p>
     */
    default void writeName(PropertyName name) throws IOException {
        writeName(name.name());
    }


    /*
     * ----------------------------------------------------------------------
     * Null
     * ----------------------------------------------------------------------
     */

    void writeNull() throws IOException;


    /*
     * ----------------------------------------------------------------------
     * String
     * ----------------------------------------------------------------------
     */

    /**
     * Writes a non-null String value.
     */
    void writeStringValue(String value) throws IOException;

    /**
     * Writes a nullable String value.
     */
    default void writeString(String value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeStringValue(value);
        }
    }


    /*
     * ----------------------------------------------------------------------
     * Primitive values
     * ----------------------------------------------------------------------
     */

    void writeLongValue(long value) throws IOException;

    void writeIntValue(int value) throws IOException;

    void writeShortValue(short value) throws IOException;

    void writeByteValue(byte value) throws IOException;

    void writeDoubleValue(double value) throws IOException;

    void writeFloatValue(float value) throws IOException;

    void writeBooleanValue(boolean value) throws IOException;

    void writeCharValue(char value) throws IOException;


    /*
     * ----------------------------------------------------------------------
     * Boxed values
     * ----------------------------------------------------------------------
     */

    default void writeLong(Long value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeLongValue(value);
        }
    }

    default void writeInt(Integer value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeIntValue(value);
        }
    }

    default void writeShort(Short value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeShortValue(value);
        }
    }

    default void writeByte(Byte value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeByteValue(value);
        }
    }

    default void writeDouble(Double value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeDoubleValue(value);
        }
    }

    default void writeFloat(Float value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeFloatValue(value);
        }
    }

    default void writeBoolean(Boolean value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBooleanValue(value);
        }
    }


    /*
     * ----------------------------------------------------------------------
     * Generic / arbitrary precision numbers
     * ----------------------------------------------------------------------
     */

    /**
     * Writes a non-null Number using the backend's natural numeric
     * representation.
     *
     * <p>This method is primarily intended for dynamic binding where
     * the exact numeric Java type is not statically known.</p>
     */
    void writeNumberValue(Number value) throws IOException;

    default void writeNumber(Number value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeNumberValue(value);
        }
    }

    default void writeBigIntegerValue(BigInteger value) throws IOException {
        writeNumberValue(value);
    }

    default void writeBigInteger(BigInteger value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBigIntegerValue(value);
        }
    }

    default void writeBigDecimalValue(BigDecimal value) throws IOException {
        writeNumberValue(value);
    }

    default void writeBigDecimal(BigDecimal value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBigDecimalValue(value);
        }
    }


    /*
     * ----------------------------------------------------------------------
     * Fused property-value fast paths
     *
     * Generated binders should prefer these methods for object properties.
     *
     * Default implementations preserve correctness. Backends may override
     * individual methods when their native API provides a faster path.
     * ----------------------------------------------------------------------
     */

    default void writeNullProperty(PropertyName name) throws IOException {
        writeName(name);
        writeNull();
    }

    default void writeStringProperty(PropertyName name, String value) throws IOException {
        writeName(name);
        writeString(value);
    }

    default void writeLongProperty(PropertyName name, long value) throws IOException {
        writeName(name);
        writeLongValue(value);
    }

    default void writeIntProperty(PropertyName name, int value) throws IOException {
        writeName(name);
        writeIntValue(value);
    }

    default void writeShortProperty(PropertyName name, short value) throws IOException {
        writeName(name);
        writeShortValue(value);
    }

    default void writeByteProperty(PropertyName name, byte value) throws IOException {
        writeName(name);
        writeByteValue(value);
    }

    default void writeDoubleProperty(PropertyName name, double value) throws IOException {
        writeName(name);
        writeDoubleValue(value);
    }

    default void writeFloatProperty(PropertyName name, float value) throws IOException {
        writeName(name);
        writeFloatValue(value);
    }

    default void writeBooleanProperty(PropertyName name, boolean value) throws IOException {
        writeName(name);
        writeBooleanValue(value);
    }

    default void writeNumberProperty(PropertyName name, Number value) throws IOException {
        writeName(name);
        writeNumber(value);
    }

    default void writeBigIntegerProperty(PropertyName name, BigInteger value) throws IOException {
        writeName(name);
        writeBigInteger(value);
    }

    default void writeBigDecimalProperty(PropertyName name, BigDecimal value) throws IOException {
        writeName(name);
        writeBigDecimal(value);
    }


    /*
     * ----------------------------------------------------------------------
     * Nested structure property fast paths
     * ----------------------------------------------------------------------
     */

    /**
     * Writes a property name followed by an object start.
     */
    default void startObject(PropertyName name) throws IOException {
        writeName(name);
        startObject();
    }

    /**
     * Writes a property name followed by an array start.
     */
    default void startArray(PropertyName name) throws IOException {
        writeName(name);
        startArray();
    }

}

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
 * <p>Generated binders should prefer prepared {@link PreparedName}s,
 * primitive value methods, and property-value fused methods where possible.</p>
 */
public abstract class StreamingWriter implements Closeable, Flushable {

    /*
     * --------------------------------------------------------------
     * Prepared Property Names
     * --------------------------------------------------------------
     */
    private final StreamingBinder<?, ?> binder;

    protected StreamingWriter(StreamingBinder<?, ?> binder) {
        this.binder = binder;
    }

    public final StreamingBinder<?, ?> binder() {
        return binder;
    }

    /*
     * --------------------------------------------------------------
     * Document
     * --------------------------------------------------------------
     */

    /**
     * Prepares this writer to write one document.
     */
    public void startDocument() throws IOException {};

    /**
     * Completes the current document.
     *
     * <p>This method does not imply {@link #flush()} or {@link #close()}.</p>
     */
    public void endDocument() throws IOException {};

    /**
     * Transfers buffered output to {@code output}.
     *
     * <p>For implementations whose native writer is not backed by a
     * {@link Writer} or {@link OutputStream}. The default implementation does nothing.</p>
     */
    public void flushTo(Writer output) throws IOException {}
    public void flushTo(OutputStream output) throws IOException {}


    /*
     * --------------------------------------------------------------
     * Structure
     * --------------------------------------------------------------
     */

    public abstract void startObject() throws IOException;

    public abstract void endObject() throws IOException;

    public abstract void startArray() throws IOException;

    public abstract void endArray() throws IOException;

    public void separateProperty() throws IOException {};

    public void separateElement() throws IOException {};

    /*
     * --------------------------------------------------------------
     * Property Names
     * --------------------------------------------------------------
     */

    /**
     * Writes an object property name.
     */
    public abstract void writeName(String name) throws IOException;

    /*
     * --------------------------------------------------------------
     * Null
     * --------------------------------------------------------------
     */

    public abstract void writeNull() throws IOException;


    /*
     * --------------------------------------------------------------
     * String
     * --------------------------------------------------------------
     */

    /**
     * Writes a non-null String value.
     */
    public abstract void writeStringValue(String value) throws IOException;

    /**
     * Writes a nullable String value.
     */
    public void writeString(String value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeStringValue(value);
        }
    }


    /*
     * --------------------------------------------------------------
     * Primitive Values
     * --------------------------------------------------------------
     */

    public abstract void writeLongValue(long value) throws IOException;

    public abstract void writeIntValue(int value) throws IOException;

    public abstract void writeShortValue(short value) throws IOException;

    public abstract void writeByteValue(byte value) throws IOException;

    public abstract void writeDoubleValue(double value) throws IOException;

    public abstract void writeFloatValue(float value) throws IOException;

    public abstract void writeBooleanValue(boolean value) throws IOException;

    public abstract void writeCharValue(char value) throws IOException;


    /*
     * --------------------------------------------------------------
     * Boxed Values
     * --------------------------------------------------------------
     */

    public void writeLong(Long value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeLongValue(value);
        }
    }

    public void writeInt(Integer value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeIntValue(value);
        }
    }

    public void writeShort(Short value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeShortValue(value);
        }
    }

    public void writeByte(Byte value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeByteValue(value);
        }
    }

    public void writeDouble(Double value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeDoubleValue(value);
        }
    }

    public void writeFloat(Float value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeFloatValue(value);
        }
    }

    public void writeBoolean(Boolean value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBooleanValue(value);
        }
    }


    /*
     * --------------------------------------------------------------
     * Generic / Arbitrary Precision Numbers
     * --------------------------------------------------------------
     */

    /**
     * Writes a non-null Number using the backend's natural numeric
     * representation.
     *
     * <p>This method is primarily intended for dynamic binding where
     * the exact numeric Java type is not statically known.</p>
     */
    public abstract void writeNumberValue(Number value) throws IOException;

    public void writeNumber(Number value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeNumberValue(value);
        }
    }

    public void writeBigIntegerValue(BigInteger value) throws IOException {
        writeNumberValue(value);
    }

    public void writeBigInteger(BigInteger value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBigIntegerValue(value);
        }
    }

    public void writeBigDecimalValue(BigDecimal value) throws IOException {
        writeNumberValue(value);
    }

    public void writeBigDecimal(BigDecimal value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeBigDecimalValue(value);
        }
    }


    /*
     * --------------------------------------------------------------
     * Fused Property-Value Fast Paths
     * --------------------------------------------------------------
     */

    /*
     * Generated binders should prefer these methods for object properties.
     * Default implementations preserve correctness. Backends may override
     * individual methods when their native API provides a faster path.
     */

    /**
     * Writes a prepared object property name.
     *
     * <p>This is the preferred API for generated and cached runtime
     * bindings. Backends should override this method when they can use
     * the prepared representation directly.</p>
     */
    public void writeName(PreparedName preparedName) throws IOException {
        PreparedName.SimplePreparedName snw = (PreparedName.SimplePreparedName) preparedName;
        writeName(snw.name);
    }


}

package org.sjf4j.binding;

import org.sjf4j.annotation.binding.Backend;
import org.sjf4j.node.PojoInfo;
import org.sjf4j.node.PropertyInfo;
import org.sjf4j.util.Asserts;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Unified streaming writer for structured data.
 *
 * <p>The interface defines common structural semantics while allowing
 * implementations to provide backend-specific fast paths.</p>
 *
 * <p>Generated binders should prefer prepared {@link CompiledName}s,
 * primitive value methods, and property-value fused methods where possible.</p>
 */
public abstract class StreamingWriter implements Closeable, Flushable {

    protected final Backend backend;

    protected StreamingWriter(Backend backend) {
        this.backend = Asserts.notNull(backend, "backend");
    }


    /*
     * Document
     */

    public void startDocument() throws IOException {
    }

    public void endDocument() throws IOException {
    }


    /*
     * Structure
     */

    public abstract void startObject() throws IOException;

    public abstract void endObject() throws IOException;

    public abstract void startArray() throws IOException;

    public abstract void endArray() throws IOException;

    public void separateProperty() throws IOException {
    }

    public void separateElement() throws IOException {
    }


    /*
     * Property name
     */

    public abstract void writeName(String name) throws IOException;

    public void writeName(String name, boolean separated) throws IOException {
        if (separated) {
            separateProperty();
        }
        writeName(name);
    }

    public final CompiledName[] compiledNames(PojoInfo pojoInfo) {
        BackendCache cache = pojoInfo.backendCache(backend);
        CompiledName[] compiledNames = cache.compiledNames;
        if (compiledNames == null) {
            PropertyInfo[] properties = pojoInfo.readableProperties;
            compiledNames = new CompiledName[properties.length];
            for (int i = 0; i < properties.length; i++) {
                compiledNames[i] = createCompiledName(properties[i].name);
            }
            cache.compiledNames = compiledNames;
        }
        return compiledNames;
    }

    public CompiledName createCompiledName(String name) {
        return new CompiledName(name);
    }

    public void writeName(CompiledName name) throws IOException {
        writeName(name.name());
    }

    public void writeName(CompiledName name, boolean separated) throws IOException {
        if (separated) {
            separateProperty();
        }
        writeName(name);
    }


    /*
     * Null / String
     */

    public abstract void writeNull() throws IOException;

    public abstract void writeStringValue(String value) throws IOException;

    public void writeString(String value) throws IOException {
        if (value == null) {
            writeNull();
        } else {
            writeStringValue(value);
        }
    }


    /*
     * Primitive
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
     * Boxed
     */

    public void writeLong(Long value) throws IOException {
        if (value == null) writeNull();
        else writeLongValue(value);
    }

    public void writeInt(Integer value) throws IOException {
        if (value == null) writeNull();
        else writeIntValue(value);
    }

    public void writeShort(Short value) throws IOException {
        if (value == null) writeNull();
        else writeShortValue(value);
    }

    public void writeByte(Byte value) throws IOException {
        if (value == null) writeNull();
        else writeByteValue(value);
    }

    public void writeDouble(Double value) throws IOException {
        if (value == null) writeNull();
        else writeDoubleValue(value);
    }

    public void writeFloat(Float value) throws IOException {
        if (value == null) writeNull();
        else writeFloatValue(value);
    }

    public void writeBoolean(Boolean value) throws IOException {
        if (value == null) writeNull();
        else writeBooleanValue(value);
    }

    public void writeChar(Character value) throws IOException {
        if (value == null) writeNull();
        else writeCharValue(value);
    }


    /*
     * Number
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

}
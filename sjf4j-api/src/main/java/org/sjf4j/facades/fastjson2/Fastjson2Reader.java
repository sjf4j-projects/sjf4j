package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import lombok.NonNull;
import org.sjf4j.JsonException;
import org.sjf4j.facades.FacadeReader;

import java.io.IOException;
import java.math.BigDecimal;

public class Fastjson2Reader implements FacadeReader {

    private final JSONReader reader;

    public Fastjson2Reader(@NonNull JSONReader reader) {
        this.reader = reader;
    }

    @Override
    public int peekTokenId() throws IOException {
        if (reader.isObject()) {
            return ID_START_OBJECT;
        } else if (reader.current() == '}') {
            return ID_END_OBJECT;
        } else if (reader.isArray()) {
            return ID_START_ARRAY;
        } else if (reader.current() == ']') {
            return ID_END_ARRAY;
        } else if (reader.isString()) {
            return ID_STRING;
        } else if (reader.isNumber()) {
            return ID_NUMBER;
        } else if (reader.current() == 't' || reader.current() == 'f') {    // I can do it!
            return ID_BOOLEAN;
        } else if (reader.isNull()) {
            return ID_NULL;
        } else {
            return ID_UNKNOWN;
        }
    }

    @Override
    public void startDocument() throws IOException {
        // Nothing
    }

    @Override
    public void endDocument() throws IOException {
        // Nothing
    }

    @Override
    public void startObject() throws IOException {
        if (!reader.nextIfObjectStart()) {
            throw new JsonException("Expected token 'START_OBJECT', but got " + reader.current());
        }
    }

    @Override
    public void endObject() throws IOException {
        if (!reader.nextIfObjectEnd()) {
            throw new JsonException("Expected token 'END_OBJECT', but got " + reader.current());
        }
    }

    @Override
    public void startArray() throws IOException {
        if (!reader.nextIfArrayStart()) {
            throw new JsonException("Expected token 'START_ARRAY', but got " + reader.current());
        }
    }

    @Override
    public void endArray() throws IOException {
        if (!reader.nextIfArrayEnd()) {
            throw new JsonException("Expected token 'END_ARRAY', but got " + reader.current());
        }
    }

    @Override
    public String nextName() throws IOException {
        return reader.readFieldName();
    }

    @Override
    public String nextString() throws IOException {
        return reader.readString();
    }

    @Override
    public Number nextNumber() throws IOException {
        Number n = reader.readNumber();
        // Double is more popular and common usage
        if (n instanceof BigDecimal) {
            double f = n.doubleValue();
            if (Double.isFinite(f)) return f;
        }
        return n;
    }

    @Override
    public Boolean nextBoolean() throws IOException {
        return reader.readBoolValue();
    }

    @Override
    public void nextNull() throws IOException {
        reader.nextIfNull();
    }

    @Override
    public boolean hasNext() throws IOException {
        int tid = peekTokenId();
        return tid != ID_END_OBJECT && tid != ID_END_ARRAY;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}

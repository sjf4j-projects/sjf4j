package org.sjf4j.facades.simple;

import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.PojoRegistry;
import org.sjf4j.facades.FacadeReader;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Deprecated
public class ObjectReader implements FacadeReader {

    private Object object;

    public ObjectReader(Object object) {
        this.object = object;
    }

    @Override
    public int peekTokenId() throws IOException {
        if (object == null) {
            return ID_NULL;
        } else if (object instanceof CharSequence || object instanceof Character) {
            return ID_STRING;
        } else if (object instanceof Number) {
            return ID_NUMBER;
        } else if (object instanceof Boolean) {
            return ID_BOOLEAN;
        } else if (object instanceof JsonObject || object instanceof Map) {
            return ID_START_OBJECT;
        } else if (object instanceof JsonArray || object instanceof List || object.getClass().isArray()) {
            return ID_START_ARRAY;
        } else if (PojoRegistry.isPojo(object.getClass())) {
            return ID_START_OBJECT;
        } else {
            return ID_UNKNOWN;
        }
    }

    @Override
    public void startDocument() throws IOException {

    }

    @Override
    public void endDocument() throws IOException {

    }

    @Override
    public void startObject() throws IOException {

    }

    @Override
    public void endObject() throws IOException {

    }

    @Override
    public void startArray() throws IOException {

    }

    @Override
    public void endArray() throws IOException {

    }

    @Override
    public String nextName() throws IOException {
        return "";
    }

    @Override
    public String nextString() throws IOException {
        return "";
    }

    @Override
    public Number nextNumber() throws IOException {
        return null;
    }

    @Override
    public Boolean nextBoolean() throws IOException {
        return null;
    }

    @Override
    public void nextNull() throws IOException {

    }

    @Override
    public boolean hasNext() throws IOException {
        return false;
    }

    @Override
    public void close() throws IOException {

    }
}

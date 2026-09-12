package org.sjf4j.testbench.handwritten;

import com.alibaba.fastjson2.JSONReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct Fastjson2 streaming reader for an untyped JSON object graph.
 *
 * V2:
 * - dispatches by reader.current() first
 * - keeps LinkedHashMap for objects
 * - keeps ArrayList for arrays
 */
public final class Fastjson2HandMapV2Reader {

    private Fastjson2HandMapV2Reader() {
    }

    public static Object read(JSONReader reader) {
        return readValue(reader);
    }

    private static Object readValue(JSONReader reader) {
        switch (reader.current()) {
            case '{':
                return readObject(reader);

            case '[':
                return readArray(reader);

            case '"':
                return reader.readString();

            case 't':
            case 'f':
                return reader.readBoolValue();

            case 'n':
                reader.readNull();
                return null;

            default:
                return reader.readNumber();
        }
    }

    private static Map<String, Object> readObject(JSONReader reader) {
        if (!reader.nextIfObjectStart()) {
            throw new IllegalStateException(
                    "expected object, but was " + reader.current());
        }

        LinkedHashMap<String, Object> object =
                new LinkedHashMap<String, Object>();

        while (!reader.nextIfObjectEnd()) {
            String name = reader.readFieldName();
            Object value = readValue(reader);
            object.put(name, value);
        }

        return object;
    }

    private static List<Object> readArray(JSONReader reader) {
        if (!reader.nextIfArrayStart()) {
            throw new IllegalStateException(
                    "expected array, but was " + reader.current());
        }

        ArrayList<Object> array = new ArrayList<Object>();

        while (!reader.nextIfArrayEnd()) {
            array.add(readValue(reader));
        }

        return array;
    }
}
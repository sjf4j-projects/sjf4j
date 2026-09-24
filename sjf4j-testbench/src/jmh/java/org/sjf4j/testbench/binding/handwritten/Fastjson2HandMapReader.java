package org.sjf4j.testbench.binding.handwritten;

import com.alibaba.fastjson2.JSONReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Direct Fastjson2 streaming reader for an untyped JSON object graph. */
public final class Fastjson2HandMapReader {

    private Fastjson2HandMapReader() {}

    public static Object read(JSONReader reader) {
        char ch = reader.current();
        if (reader.nextIfObjectStart()) {
            LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
            while (!reader.nextIfObjectEnd()) {
                object.put(reader.readFieldName(), read(reader));
            }
            return object;
        }
        if (reader.nextIfArrayStart()) {
            ArrayList<Object> array = new ArrayList<Object>();
            while (!reader.nextIfArrayEnd()) {
                array.add(read(reader));
            }
            return array;
        }
        if (ch == '"') {
            return reader.readString();
        }
        if (ch == 't' || ch == 'f') {
            return reader.readBoolValue();
        }
        if (ch == 'n') {
            reader.readNull();
            return null;
        }
        return reader.readNumber();
    }
}

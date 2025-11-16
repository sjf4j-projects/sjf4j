package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

public class Fastjson2Walker {


    public static Object walk2Null(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        } else if (reader.isString()) {
            return reader.readString();
        } else if (reader.isNumber()) {
            return reader.readNumber();
        } else if (reader.current() == 't' || reader.current() == 'f') { // Catch it!
            return reader.readBool();
        } else if (reader.nextIfObjectStart()) {
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                Object value = walk2Null(reader);
            }
            return null;
        } else if (reader.nextIfArrayStart()) {
            while (!reader.nextIfArrayEnd()) {
                Object value = walk2Null(reader);
            }
            return null;
        } else {
            throw new IllegalStateException("Unexpected char '" + reader.current() + "' at " + reader.info());
        }
    }


    public static Object walk2Jo(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        } else if (reader.isString()) {
            return reader.readString();
        } else if (reader.isNumber()) {
            return reader.readNumber();
        } else if (reader.current() == 't' || reader.current() == 'f') { // Catch it!
            return reader.readBool();
        } else if (reader.nextIfObjectStart()) {
            JsonObject jo = new JsonObject();
            while (!reader.nextIfObjectEnd()) {
                String key = reader.readFieldName();
                Object value = walk2Jo(reader);
                jo.put(key, value);
            }
            return jo;
        } else if (reader.nextIfArrayStart()) {
            JsonArray ja = new JsonArray();
            while (!reader.nextIfArrayEnd()) {
                Object value = walk2Jo(reader);
                ja.add(value);
            }
            return ja;
        } else {
            throw new IllegalStateException("Unexpected char '" + reader.current() + "' at " + reader.info());
        }
    }


}

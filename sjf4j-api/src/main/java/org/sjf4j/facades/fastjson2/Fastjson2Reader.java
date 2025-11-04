package org.sjf4j.facades.fastjson2;

import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.math.BigDecimal;

public class Fastjson2Reader {

    public static Object readAny(JSONReader reader) {
        if (reader.nextIfNull()) {
            return null;
        } else if (reader.isString()) {
            return reader.readString();
        } else if (reader.isNumber()) {
            Number n = reader.readNumber();
            if (n instanceof BigDecimal) {
                double f = n.doubleValue();
                if (Double.isFinite(f)) return f;   // Double is more popular and faster
            }
            return n;
        } else if (reader.current() == 't' || reader.current() == 'f') { // Catch it!
            return reader.readBool();
        } else if (reader.nextIfObjectStart()) {
            JsonObject jo = new JsonObject();
            while (!reader.nextIfObjectEnd()) {
                String k = reader.readFieldName();
                Object v = readAny(reader);
                jo.put(k, v);
            }
            return jo;
        } else if (reader.nextIfArrayStart()) {
            JsonArray ja = new JsonArray();
            while (!reader.nextIfArrayEnd()) {
                Object v = readAny(reader);
                ja.add(v);
            }
            return ja;
        } else {
            throw new IllegalStateException("Unexpected char '" + reader.current() + "' at " + reader.info());
        }
    }



//    public static JsonObject readObject(Reader input, JSONReader.Feature[] readerFeatures) {
//        JSONObject fastjo = JSON.parseObject(input, readerFeatures);
//        return convertObject(fastjo);
//    }
//
//    public static JsonArray readArray(Reader input, JSONReader.Feature[] readerFeatures) {
//        JSONArray fastja = JSON.parseArray(input, readerFeatures);
//        return convertArray(fastja);
//    }
//
//    public static JsonObject convertObject(JSONObject fastjo) {
//        if (fastjo == null) return null;
//        JsonObject jo = new JsonObject();
//        for (Map.Entry<String, Object> entry : fastjo.entrySet()) {
//            String key = entry.getKey();
//            Object value = entry.getValue();
//            if (value instanceof JSONObject) {
//                jo.put(key, convertObject((JSONObject) value));
//            } else if (value instanceof JSONArray) {
//                jo.put(key, convertArray((JSONArray) value));
//            } else if (value instanceof BigDecimal) {
//                double d = ((BigDecimal) value).doubleValue();
//                if (Double.isFinite(d)) {
//                    jo.put(key, d);
//                } else {
//                    jo.put(key, value);
//                }
//            } else {
//                jo.put(key, value);
//            }
//        }
//        return jo;
//    }
//
//    public static JsonArray convertArray(JSONArray fastja) {
//        if (fastja == null) return null;
//        JsonArray ja = new JsonArray();
//        for (Object value : fastja) {
//            if (value instanceof JSONObject) {
//                ja.add(convertObject((JSONObject) value));
//            } else if (value instanceof JSONArray) {
//                ja.add(convertArray((JSONArray) value));
//            } else {
//                ja.add(value);
//            }
//        }
//        return ja;
//    }


}

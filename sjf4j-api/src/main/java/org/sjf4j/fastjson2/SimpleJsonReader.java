package org.sjf4j.fastjson2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Since the streaming API of Fastjson2 is rather confusing,
 * it's better to use the official `JSON.parseObject` and then convert it to JsonObject.
 * Perhaps it will be re-implemented in the future.
 */
public class SimpleJsonReader {


    public static JsonObject readObject(Reader input, JSONReader.Feature[] readerFeatures) {
        JSONObject fastjo = JSON.parseObject(input, readerFeatures);
        return convertObject(fastjo);
    }

    public static JsonArray readArray(Reader input, JSONReader.Feature[] readerFeatures) {
        JSONArray fastja = JSON.parseArray(input, readerFeatures);
        return convertArray(fastja);
    }

    public static JsonObject convertObject(JSONObject fastjo) {
        if (fastjo == null) return null;
        JsonObject jo = new JsonObject();
        for (Map.Entry<String, Object> entry : fastjo.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof JSONObject) {
                jo.put(key, convertObject((JSONObject) value));
            } else if (value instanceof JSONArray) {
                jo.put(key, convertArray((JSONArray) value));
            } else if (value instanceof BigDecimal) {
                double d = ((BigDecimal) value).doubleValue();
                if (Double.isFinite(d)) {
                    jo.put(key, d);
                } else {
                    jo.put(key, value);
                }
            } else {
                jo.put(key, value);
            }
        }
        return jo;
    }

    public static JsonArray convertArray(JSONArray fastja) {
        if (fastja == null) return null;
        JsonArray ja = new JsonArray();
        for (Object value : fastja) {
            if (value instanceof JSONObject) {
                ja.add(convertObject((JSONObject) value));
            } else if (value instanceof JSONArray) {
                ja.add(convertArray((JSONArray) value));
            } else {
                ja.add(value);
            }
        }
        return ja;
    }


}

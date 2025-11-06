package org.sjf4j.facades.fastjson2;


import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.ObjectRegistry;
import org.sjf4j.PojoRegistry;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;


public class Fastjson2Writer {


    public static void writeAny(JSONWriter writer, Object object) {
        Object value = ObjectRegistry.tryObject2Value(object);
        if (value == null) {
            writer.writeNull();
        } else if (value instanceof CharSequence || value instanceof Character) {
            writer.writeString(value.toString());
        } else if (value instanceof Number) {
            if (value instanceof Long || value instanceof Integer) {
                writer.writeInt64(((Number) value).longValue());
            } else if (value instanceof Float || value instanceof Double) {
                writer.writeDouble(((Number) value).doubleValue());
            } else if (value instanceof BigInteger) {
                writer.writeBigInt(((BigInteger) value));
            } else if (value instanceof BigDecimal) {
                writer.writeDecimal(((BigDecimal) value));
            } else {
                writer.writeInt64(((Number) value).longValue());
            }
        } else if (value instanceof Boolean) {
            writer.writeBool((Boolean) value);
        } else if (value instanceof JsonObject) {
            writer.startObject();
            ((JsonObject) value).forEach((k, v) -> {
                writer.writeName(k);
                writer.writeColon();
                writeAny(writer, v);
            });
            writer.endObject();
        } else if (value instanceof Map) {
            writer.startObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                writer.writeName(entry.getKey().toString());
                writer.writeColon();
                writeAny(writer, entry.getValue());
            }
        } else if (value instanceof JsonArray) {
            writer.startArray();
            JsonArray ja = (JsonArray) value;
            for (int i = 0; i < ja.size(); i++) {
                if (i > 0) writer.writeComma();
                writeAny(writer, ja.getObject(i));
            }
            writer.endArray();
        } else if (value instanceof List) {
            writer.startArray();
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) writer.writeComma();
                writeAny(writer, list.get(i));
            }
            writer.endArray();
        } else if (value.getClass().isArray()) {
            writer.startArray();
            for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) writer.writeComma();
                writeAny(writer, Array.get(value, i));
            }
            writer.endArray();
        } else if (PojoRegistry.isPojo(value.getClass())) {
            writer.startObject();
            for (Map.Entry<String, PojoRegistry.FieldInfo> entry :
                    PojoRegistry.getPojoInfo(value.getClass()).getFields().entrySet()) {
                writer.writeName(entry.getKey());
                writer.writeColon();
                Object subValue = null;
                try {
                    subValue = entry.getValue().getGetter().invoke(value);
                } catch (Throwable e) {
                    throw new JsonException("Failed to invoke getter for field '" + entry.getKey() + "' in POJO " +
                            value.getClass().getName(), e);
                }
                writeAny(writer, subValue);
            }
            writer.endObject();
        } else {
            throw new IllegalStateException("Unsupported object type '" + object.getClass().getName() +
                    "', expected one of [JsonObject, JsonArray, String, Number, Boolean] or a type registered in " +
                    "ObjectRegistry or a valid POJO, or a Map/List/Array of such elements.");
        }
    }


}

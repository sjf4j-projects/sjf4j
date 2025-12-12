package org.sjf4j.facade.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JacksonWalker {


    public static Object walk2Map(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();
        if (token == null) token = p.nextToken();
        switch (token) {
            case NOT_AVAILABLE:
                return null;
            case START_OBJECT:
                Map<String, Object> map = JsonConfig.global().mapSupplier.create();
                p.nextToken();
                JsonToken jt;
                while ((jt = p.currentToken()) != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    JsonToken t = p.nextToken();
                    Object value = walk2Map(p);
                    map.put(name, value);
                }
                p.nextToken();
                return map;
            case START_ARRAY:
                List<Object> list = new ArrayList<>();
                p.nextToken();
                while (p.currentToken() != JsonToken.END_ARRAY) {
                    Object value = walk2Map(p);
                    list.add(value);
                }
                p.nextToken();
                return list;
            case FIELD_NAME:
                String vn = p.currentName();
                p.nextToken();
                return vn;
            case VALUE_STRING:
                String vs = p.getText();
                JsonToken vt = p.nextToken();
                return vs;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                Number vnum = p.getNumberValue();
                JsonToken nt = p.nextToken();
                return vnum;
            case VALUE_TRUE:
            case VALUE_FALSE:
                Boolean vb = p.nextBooleanValue();
                p.nextToken();
                return vb;
            case VALUE_NULL:
                p.nextToken();
                return null;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }

    public static Object walk2Jo(JsonParser p) throws IOException {
        JsonToken token = p.currentToken();
        if (token == null) token = p.nextToken();
        switch (token) {
            case NOT_AVAILABLE:
                return null;
            case START_OBJECT:
                JsonObject jo = new JsonObject();
                p.nextToken();
                JsonToken jt;
                while ((jt = p.currentToken()) != JsonToken.END_OBJECT) {
                    String name = p.currentName();
                    JsonToken t = p.nextToken();
                    Object value = walk2Jo(p);
                    jo.put(name, value);
                }
                p.nextToken();
                return jo;
            case START_ARRAY:
                JsonArray ja = new JsonArray();
                p.nextToken();
                while (p.currentToken() != JsonToken.END_ARRAY) {
                    Object value = walk2Jo(p);
                    ja.add(value);
                }
                p.nextToken();
                return ja;
            case FIELD_NAME:
                String vn = p.currentName();
                p.nextToken();
                return vn;
            case VALUE_STRING:
                String vs = p.getText();
                JsonToken vt = p.nextToken();
                return vs;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                Number vnum = p.getNumberValue();
                JsonToken nt = p.nextToken();
                return vnum;
            case VALUE_TRUE:
            case VALUE_FALSE:
                Boolean vb = p.nextBooleanValue();
                p.nextToken();
                return vb;
            case VALUE_NULL:
                p.nextToken();
                return null;
            default:
                throw new IllegalStateException("Unexpected token: " + token);
        }
    }

}

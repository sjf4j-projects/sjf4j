package org.sjf4j;

import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class JsonContainer {

    public abstract int size();

    /// By path

    public boolean containsByPath(@NonNull String path) {
        return JsonPath.compile(path).contains(this);
    }

    // Object
    public Object getObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).getObject(this);
    }

    public Object getObjectByPath(@NonNull String path, Object defaultValue) {
        return JsonPath.compile(path).getObject(this, defaultValue);
    }

    // String
    public String getStringByPath(@NonNull String path) {
        return JsonPath.compile(path).getString(this);
    }
    public String getStringByPath(@NonNull String path, String defaultValue) {
        return JsonPath.compile(path).getString(this, defaultValue);
    }

    // Long
    public Long getLongByPath(@NonNull String path) {
        return JsonPath.compile(path).getLong(this);
    }
    public long getLongByPath(@NonNull String path, long defaultValue) {
        return JsonPath.compile(path).getLong(this, defaultValue);
    }

    // Integer
    public Integer getIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).getInteger(this);
    }
    public int getIntegerByPath(@NonNull String path, int defaultValue) {
        return JsonPath.compile(path).getInteger(this, defaultValue);
    }

    // Short
    public Short getShortByPath(@NonNull String path) {
        return JsonPath.compile(path).getShort(this);
    }
    public short getShortByPath(@NonNull String path, short defaultValue) {
        return JsonPath.compile(path).getShort(this, defaultValue);
    }

    // Byte
    public Byte getByteByPath(@NonNull String path) {
        return JsonPath.compile(path).getByte(this);
    }
    public byte getByteByPath(@NonNull String path, byte defaultValue) {
        return JsonPath.compile(path).getByte(this, defaultValue);
    }

    // Double
    public Double getDoubleByPath(@NonNull String path) {
        return JsonPath.compile(path).getDouble(this);
    }
    public double getDoubleByPath(@NonNull String path, double defaultValue) {
        return JsonPath.compile(path).getDouble(this, defaultValue);
    }

    // Float
    public Float getFloatByPath(@NonNull String path) {
        return JsonPath.compile(path).getFloat(this);
    }
    public float getFloatByPath(@NonNull String path, float defaultValue) {
        return JsonPath.compile(path).getFloat(this, defaultValue);
    }

    // BigInteger
    public BigInteger getBigIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).getBigInteger(this);
    }
    public BigInteger getBigIntegerByPath(@NonNull String path, BigInteger defaultValue) {
        return JsonPath.compile(path).getBigInteger(this, defaultValue);
    }

    // BigDecimal
    public BigDecimal getBigDecimalByPath(@NonNull String path) {
        return JsonPath.compile(path).getBigDecimal(this);
    }
    public BigDecimal getBigDecimalByPath(@NonNull String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).getBigDecimal(this, defaultValue);
    }

    // Boolean
    public Boolean getBooleanByPath(@NonNull String path) {
        return JsonPath.compile(path).getBoolean(this);
    }
    public boolean getBooleanByPath(@NonNull String path, boolean defaultValue) {
        return JsonPath.compile(path).getBoolean(this, defaultValue);
    }

    // JsonObject
    public JsonObject getJsonObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).getJsonObject(this);
    }

    public JsonObject getJsonObjectByPath(@NonNull String path, JsonObject defaultValue) {
        return JsonPath.compile(path).getJsonObject(this, defaultValue);
    }

    // JsonArray
    public JsonArray getJsonArrayByPath(@NonNull String path) {
        return JsonPath.compile(path).getJsonArray(this);
    }

    public JsonArray getJsonArrayByPath(@NonNull String path, JsonArray defaultValue) {
        return JsonPath.compile(path).getJsonArray(this, defaultValue);
    }

    // Put by path
    public void putByPath(@NonNull String path, Object value) {
        JsonPath.compile(path).put(this, value);
    }

    public void putByPathIfNonNull(@NonNull String path, Object value) {
        JsonPath.compile(path).putIfNonNull(this, value);
    }

    public void putByPathIfAbsent(@NonNull String path, Object value) {
        JsonPath.compile(path).putIfAbsent(this, value);
    }

    public void removeByPath(@NonNull String path) {
        JsonPath.compile(path).remove(this);
    }

}

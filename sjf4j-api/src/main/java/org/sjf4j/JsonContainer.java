package org.sjf4j;

import lombok.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;

public abstract class JsonContainer {

    public abstract int size();

    /// By path

    public boolean containsByPath(@NonNull String path) {
        return JsonPath.compile(path).hasNonNull(this);
    }

    // Object
    public Object getObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).readObject(this);
    }

    public Object getObjectByPath(@NonNull String path, Object defaultValue) {
        return JsonPath.compile(path).readObject(this, defaultValue);
    }

    // String
    public String getStringByPath(@NonNull String path) {
        return JsonPath.compile(path).readString(this);
    }
    public String getStringByPath(@NonNull String path, String defaultValue) {
        return JsonPath.compile(path).readString(this, defaultValue);
    }

    // Long
    public Long getLongByPath(@NonNull String path) {
        return JsonPath.compile(path).readLong(this);
    }
    public long getLongByPath(@NonNull String path, long defaultValue) {
        return JsonPath.compile(path).readLong(this, defaultValue);
    }

    // Integer
    public Integer getIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).readInteger(this);
    }
    public int getIntegerByPath(@NonNull String path, int defaultValue) {
        return JsonPath.compile(path).readInteger(this, defaultValue);
    }

    // Short
    public Short getShortByPath(@NonNull String path) {
        return JsonPath.compile(path).readShort(this);
    }
    public short getShortByPath(@NonNull String path, short defaultValue) {
        return JsonPath.compile(path).readShort(this, defaultValue);
    }

    // Byte
    public Byte getByteByPath(@NonNull String path) {
        return JsonPath.compile(path).readByte(this);
    }
    public byte getByteByPath(@NonNull String path, byte defaultValue) {
        return JsonPath.compile(path).readByte(this, defaultValue);
    }

    // Double
    public Double getDoubleByPath(@NonNull String path) {
        return JsonPath.compile(path).readDouble(this);
    }
    public double getDoubleByPath(@NonNull String path, double defaultValue) {
        return JsonPath.compile(path).readDouble(this, defaultValue);
    }

    // Float
    public Float getFloatByPath(@NonNull String path) {
        return JsonPath.compile(path).readFloat(this);
    }
    public float getFloatByPath(@NonNull String path, float defaultValue) {
        return JsonPath.compile(path).readFloat(this, defaultValue);
    }

    // BigInteger
    public BigInteger getBigIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).readBigInteger(this);
    }
    public BigInteger getBigIntegerByPath(@NonNull String path, BigInteger defaultValue) {
        return JsonPath.compile(path).readBigInteger(this, defaultValue);
    }

    // BigDecimal
    public BigDecimal getBigDecimalByPath(@NonNull String path) {
        return JsonPath.compile(path).readBigDecimal(this);
    }
    public BigDecimal getBigDecimalByPath(@NonNull String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).readBigDecimal(this, defaultValue);
    }

    // Boolean
    public Boolean getBooleanByPath(@NonNull String path) {
        return JsonPath.compile(path).readBoolean(this);
    }
    public boolean getBooleanByPath(@NonNull String path, boolean defaultValue) {
        return JsonPath.compile(path).readBoolean(this, defaultValue);
    }

    // JsonObject
    public JsonObject getJsonObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).readJsonObject(this);
    }

    public JsonObject getJsonObjectByPath(@NonNull String path, JsonObject defaultValue) {
        return JsonPath.compile(path).readJsonObject(this, defaultValue);
    }

    // JsonArray
    public JsonArray getJsonArrayByPath(@NonNull String path) {
        return JsonPath.compile(path).readJsonArray(this);
    }

    public JsonArray getJsonArrayByPath(@NonNull String path, JsonArray defaultValue) {
        return JsonPath.compile(path).readJsonArray(this, defaultValue);
    }

    // Put by path
    public void putByPath(@NonNull String path, Object value) {
        JsonPath.compile(path).put(this, value);
    }

    public void putNonNullByPath(@NonNull String path, Object value) {
        JsonPath.compile(path).putNonNull(this, value);
    }

    public void putByPathIfAbsentOrNull(@NonNull String path, Object value) {
        JsonPath.compile(path).putIfAbsentOrNull(this, value);
    }

    public void removeByPath(@NonNull String path) {
        JsonPath.compile(path).remove(this);
    }

}

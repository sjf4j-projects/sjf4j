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
        return JsonPath.compile(path).findObject(this);
    }
    public Object getObjectByPath(@NonNull String path, Object defaultValue) {
        return JsonPath.compile(path).findObject(this, defaultValue);
    }

    // String
    public String getStringByPath(@NonNull String path) {
        return JsonPath.compile(path).findString(this);
    }
    public String getStringByPath(@NonNull String path, String defaultValue) {
        return JsonPath.compile(path).findString(this, defaultValue);
    }
    public String asStringByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsString(this);
    }
    public String asStringByPath(@NonNull String path, String defaultValue) {
        return JsonPath.compile(path).findAsString(this, defaultValue);
    }

    // Long
    public Long getLongByPath(@NonNull String path) {
        return JsonPath.compile(path).findLong(this);
    }
    public long getLongByPath(@NonNull String path, long defaultValue) {
        return JsonPath.compile(path).findLong(this, defaultValue);
    }
    public Long asLongByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsLong(this);
    }
    public long asLongByPath(@NonNull String path, long defaultValue) {
        return JsonPath.compile(path).findAsLong(this, defaultValue);
    }

    // Integer
    public Integer getIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).findInteger(this);
    }
    public int getIntegerByPath(@NonNull String path, int defaultValue) {
        return JsonPath.compile(path).findInteger(this, defaultValue);
    }
    public Integer asIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsInteger(this);
    }
    public int asIntegerByPath(@NonNull String path, int defaultValue) {
        return JsonPath.compile(path).findAsInteger(this, defaultValue);
    }

    // Short
    public Short getShortByPath(@NonNull String path) {
        return JsonPath.compile(path).findShort(this);
    }
    public short getShortByPath(@NonNull String path, short defaultValue) {
        return JsonPath.compile(path).findShort(this, defaultValue);
    }
    public Short asShortByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsShort(this);
    }
    public short asShortByPath(@NonNull String path, short defaultValue) {
        return JsonPath.compile(path).findAsShort(this, defaultValue);
    }

    // Byte
    public Byte getByteByPath(@NonNull String path) {
        return JsonPath.compile(path).findByte(this);
    }
    public byte getByteByPath(@NonNull String path, byte defaultValue) {
        return JsonPath.compile(path).findByte(this, defaultValue);
    }
    public Byte asByteByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsByte(this);
    }
    public byte asByteByPath(@NonNull String path, byte defaultValue) {
        return JsonPath.compile(path).findAsByte(this, defaultValue);
    }

    // Double
    public Double getDoubleByPath(@NonNull String path) {
        return JsonPath.compile(path).findDouble(this);
    }
    public double getDoubleByPath(@NonNull String path, double defaultValue) {
        return JsonPath.compile(path).findDouble(this, defaultValue);
    }
    public Double asDoubleByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsDouble(this);
    }
    public double asDoubleByPath(@NonNull String path, double defaultValue) {
        return JsonPath.compile(path).findAsDouble(this, defaultValue);
    }

    // Float
    public Float getFloatByPath(@NonNull String path) {
        return JsonPath.compile(path).findFloat(this);
    }
    public float getFloatByPath(@NonNull String path, float defaultValue) {
        return JsonPath.compile(path).findFloat(this, defaultValue);
    }
    public Float asFloatByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsFloat(this);
    }
    public float asFloatByPath(@NonNull String path, float defaultValue) {
        return JsonPath.compile(path).findAsFloat(this, defaultValue);
    }

    // BigInteger
    public BigInteger getBigIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).findBigInteger(this);
    }
    public BigInteger getBigIntegerByPath(@NonNull String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findBigInteger(this, defaultValue);
    }
    public BigInteger asBigIntegerByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsBigInteger(this);
    }
    public BigInteger asBigIntegerByPath(@NonNull String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findAsBigInteger(this, defaultValue);
    }

    // BigDecimal
    public BigDecimal getBigDecimalByPath(@NonNull String path) {
        return JsonPath.compile(path).findBigDecimal(this);
    }
    public BigDecimal getBigDecimalByPath(@NonNull String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findBigDecimal(this, defaultValue);
    }
    public BigDecimal asBigDecimalByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsBigDecimal(this);
    }
    public BigDecimal asBigDecimalByPath(@NonNull String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findAsBigDecimal(this, defaultValue);
    }

    // Boolean
    public Boolean getBooleanByPath(@NonNull String path) {
        return JsonPath.compile(path).findBoolean(this);
    }
    public boolean getBooleanByPath(@NonNull String path, boolean defaultValue) {
        return JsonPath.compile(path).findBoolean(this, defaultValue);
    }
    public Boolean asBooleanByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsBoolean(this);
    }
    public boolean asBooleanByPath(@NonNull String path, boolean defaultValue) {
        return JsonPath.compile(path).findAsBoolean(this, defaultValue);
    }

    // JsonObject
    public JsonObject getJsonObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).findJsonObject(this);
    }
    public JsonObject getJsonObjectByPath(@NonNull String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findJsonObject(this, defaultValue);
    }
    public JsonObject asJsonObjectByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsJsonObject(this);
    }
    public JsonObject asJsonObjectByPath(@NonNull String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findAsJsonObject(this, defaultValue);
    }

    // JsonArray
    public JsonArray getJsonArrayByPath(@NonNull String path) {
        return JsonPath.compile(path).findJsonArray(this);
    }
    public JsonArray getJsonArrayByPath(@NonNull String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findJsonArray(this, defaultValue);
    }
    public JsonArray asJsonArrayByPath(@NonNull String path) {
        return JsonPath.compile(path).findAsJsonArray(this);
    }
    public JsonArray asJsonArrayByPath(@NonNull String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findAsJsonArray(this, defaultValue);
    }

    // Clazz
    public <T> T getByPath(@NonNull String path, @NonNull Class<T> clazz) {
        return JsonPath.compile(path).find(this, clazz);
    }
    public <T> T getByPath(@NonNull String path, @NonNull T... reified) {
        return JsonPath.compile(path).find(this, reified);
    }
    public <T> T asByPath(@NonNull String path, @NonNull Class<T> clazz) {
        return JsonPath.compile(path).findAs(this, clazz);
    }
    public <T> T asByPath(@NonNull String path, @NonNull T... reified) {
        return JsonPath.compile(path).findAs(this, reified);
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

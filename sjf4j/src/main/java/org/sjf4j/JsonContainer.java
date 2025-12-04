package org.sjf4j;

import org.sjf4j.util.ContainerUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.function.BiFunction;

public abstract class JsonContainer {

    public abstract int size();

    /// By path

    public boolean containsByPath(String path) {
        return JsonPath.compile(path).hasNonNull(this);
    }

    // Object
    public Object getObjectByPath(String path) {
        return JsonPath.compile(path).findObject(this);
    }
    public Object getObjectByPath(String path, Object defaultValue) {
        return JsonPath.compile(path).findObject(this, defaultValue);
    }

    // String
    public String getStringByPath(String path) {
        return JsonPath.compile(path).findString(this);
    }
    public String getStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).findString(this, defaultValue);
    }
    public String asStringByPath(String path) {
        return JsonPath.compile(path).findAsString(this);
    }
    public String asStringByPath(String path, String defaultValue) {
        return JsonPath.compile(path).findAsString(this, defaultValue);
    }

    // Long
    public Long getLongByPath(String path) {
        return JsonPath.compile(path).findLong(this);
    }
    public long getLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).findLong(this, defaultValue);
    }
    public Long asLongByPath(String path) {
        return JsonPath.compile(path).findAsLong(this);
    }
    public long asLongByPath(String path, long defaultValue) {
        return JsonPath.compile(path).findAsLong(this, defaultValue);
    }

    // Integer
    public Integer getIntegerByPath(String path) {
        return JsonPath.compile(path).findInteger(this);
    }
    public int getIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).findInteger(this, defaultValue);
    }
    public Integer asIntegerByPath(String path) {
        return JsonPath.compile(path).findAsInteger(this);
    }
    public int asIntegerByPath(String path, int defaultValue) {
        return JsonPath.compile(path).findAsInteger(this, defaultValue);
    }

    // Short
    public Short getShortByPath(String path) {
        return JsonPath.compile(path).findShort(this);
    }
    public short getShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).findShort(this, defaultValue);
    }
    public Short asShortByPath(String path) {
        return JsonPath.compile(path).findAsShort(this);
    }
    public short asShortByPath(String path, short defaultValue) {
        return JsonPath.compile(path).findAsShort(this, defaultValue);
    }

    // Byte
    public Byte getByteByPath(String path) {
        return JsonPath.compile(path).findByte(this);
    }
    public byte getByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).findByte(this, defaultValue);
    }
    public Byte asByteByPath(String path) {
        return JsonPath.compile(path).findAsByte(this);
    }
    public byte asByteByPath(String path, byte defaultValue) {
        return JsonPath.compile(path).findAsByte(this, defaultValue);
    }

    // Double
    public Double getDoubleByPath(String path) {
        return JsonPath.compile(path).findDouble(this);
    }
    public double getDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).findDouble(this, defaultValue);
    }
    public Double asDoubleByPath(String path) {
        return JsonPath.compile(path).findAsDouble(this);
    }
    public double asDoubleByPath(String path, double defaultValue) {
        return JsonPath.compile(path).findAsDouble(this, defaultValue);
    }

    // Float
    public Float getFloatByPath(String path) {
        return JsonPath.compile(path).findFloat(this);
    }
    public float getFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).findFloat(this, defaultValue);
    }
    public Float asFloatByPath(String path) {
        return JsonPath.compile(path).findAsFloat(this);
    }
    public float asFloatByPath(String path, float defaultValue) {
        return JsonPath.compile(path).findAsFloat(this, defaultValue);
    }

    // BigInteger
    public BigInteger getBigIntegerByPath(String path) {
        return JsonPath.compile(path).findBigInteger(this);
    }
    public BigInteger getBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findBigInteger(this, defaultValue);
    }
    public BigInteger asBigIntegerByPath(String path) {
        return JsonPath.compile(path).findAsBigInteger(this);
    }
    public BigInteger asBigIntegerByPath(String path, BigInteger defaultValue) {
        return JsonPath.compile(path).findAsBigInteger(this, defaultValue);
    }

    // BigDecimal
    public BigDecimal getBigDecimalByPath(String path) {
        return JsonPath.compile(path).findBigDecimal(this);
    }
    public BigDecimal getBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findBigDecimal(this, defaultValue);
    }
    public BigDecimal asBigDecimalByPath(String path) {
        return JsonPath.compile(path).findAsBigDecimal(this);
    }
    public BigDecimal asBigDecimalByPath(String path, BigDecimal defaultValue) {
        return JsonPath.compile(path).findAsBigDecimal(this, defaultValue);
    }

    // Boolean
    public Boolean getBooleanByPath(String path) {
        return JsonPath.compile(path).findBoolean(this);
    }
    public boolean getBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).findBoolean(this, defaultValue);
    }
    public Boolean asBooleanByPath(String path) {
        return JsonPath.compile(path).findAsBoolean(this);
    }
    public boolean asBooleanByPath(String path, boolean defaultValue) {
        return JsonPath.compile(path).findAsBoolean(this, defaultValue);
    }

    // JsonObject
    public JsonObject getJsonObjectByPath(String path) {
        return JsonPath.compile(path).findJsonObject(this);
    }
    public JsonObject getJsonObjectByPath(String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findJsonObject(this, defaultValue);
    }
    public JsonObject asJsonObjectByPath(String path) {
        return JsonPath.compile(path).findAsJsonObject(this);
    }
    public JsonObject asJsonObjectByPath(String path, JsonObject defaultValue) {
        return JsonPath.compile(path).findAsJsonObject(this, defaultValue);
    }

    // JsonArray
    public JsonArray getJsonArrayByPath(String path) {
        return JsonPath.compile(path).findJsonArray(this);
    }
    public JsonArray getJsonArrayByPath(String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findJsonArray(this, defaultValue);
    }
    public JsonArray asJsonArrayByPath(String path) {
        return JsonPath.compile(path).findAsJsonArray(this);
    }
    public JsonArray asJsonArrayByPath(String path, JsonArray defaultValue) {
        return JsonPath.compile(path).findAsJsonArray(this, defaultValue);
    }

    // Clazz
    public <T> T getByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).find(this, clazz);
    }
    public <T> T asByPath(String path, Class<T> clazz) {
        return JsonPath.compile(path).findAs(this, clazz);
    }
    
    // Put by path
    public void putByPath(String path, Object value) {
        JsonPath.compile(path).put(this, value);
    }

    public void putNonNullByPath(String path, Object value) {
        JsonPath.compile(path).putNonNull(this, value);
    }

    public void putByPathIfAbsent(String path, Object value) {
        JsonPath.compile(path).putIfAbsent(this, value);
    }

    public void removeByPath(String path) {
        JsonPath.compile(path).remove(this);
    }


    /// Walk

    public void walk(BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, visitor);
    }

    public void walk(JsonWalker.Target target,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, visitor);
    }

    public void walk(JsonWalker.Target target, JsonWalker.Order order,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, order, visitor);
    }

    public void walk(JsonWalker.Target target, JsonWalker.Order order, int maxDepth,
                     BiFunction<JsonPath, Object, JsonWalker.Control> visitor) {
        JsonWalker.walk(this, target, order, maxDepth, visitor);
    }

    /// Base

    @SuppressWarnings("EqualsDoesntCheckParameterClass")
    @Override
    public boolean equals(Object target) {
        return ContainerUtil.equals(this, target);
    }

    public String inspect() {
        return ContainerUtil.inspect(this);
    }

}

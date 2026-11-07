package org.sjf4j.testbench.model;

import lombok.Value;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

@Value
@NodeValue
public class StringValue {
    String value;

    @RawToValue
    public static StringValue fromRaw(String raw) {
        return new StringValue(raw);
    }

    @ValueToRaw
    public String toRaw() {
        return value;
    }
}

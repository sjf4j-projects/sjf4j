package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Jackson2GeneratorTest {

    @Test
    void writesValuesAndNullableDefaults() throws Exception {
        StringWriter output = new StringWriter();
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        Jackson2Generator generator = binder.createGenerator(output);
        generator.writeArrayStart();
        generator.writeString("text");
        generator.writeCharValue('x');
        generator.writeLongValue(1L);
        generator.writeIntValue(2);
        generator.writeShortValue((short) 3);
        generator.writeByteValue((byte) 4);
        generator.writeDoubleValue(5.5d);
        generator.writeFloatValue(6.5f);
        generator.writeBooleanValue(true);
        generator.writeNumberValue(new BigInteger("12345678901234567890"));
        generator.writeNumberValue(new BigDecimal("7.25"));
        generator.writeNumberValue(new AtomicInteger(8));
        generator.writeString(null);
        generator.writeNumber(null);
        generator.writeBigInteger(null);
        generator.writeBigDecimal(null);
        generator.writeArrayEnd();
        generator.end();
        generator.flush();

        assertEquals("[\"text\",\"x\",1,2,3,4,5.5,6.5,true,12345678901234567890,7.25,8,null,null,null,null]", output.toString());
        generator.close();
    }

    @Test
    void writesPreparedNamesAndFusedProperties() throws Exception {
        StringWriter output = new StringWriter();
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        Jackson2Generator generator = binder.createGenerator(output);
        Jackson2CompiledName text = new Jackson2CompiledName("te\"xt");
        Jackson2CompiledName number = new Jackson2CompiledName("number");
        generator.writeObjectStart();
        generator.writeName(text);
        generator.writeString("value");
        generator.writeName(number);
        generator.writeIntValue(42);
        generator.writeName(new Jackson2CompiledName("decimal"));
        generator.writeBigDecimal(new BigDecimal("1.20"));
        generator.writeName(new Jackson2CompiledName("nullable"));
        generator.writeString(null);
        generator.writeName(new Jackson2CompiledName("nullValue"));
        generator.writeNull();
        generator.writeObjectEnd();
        generator.close();

        assertEquals("{\"te\\\"xt\":\"value\",\"number\":42,\"decimal\":1.20,\"nullable\":null,\"nullValue\":null}", output.toString());
    }
}

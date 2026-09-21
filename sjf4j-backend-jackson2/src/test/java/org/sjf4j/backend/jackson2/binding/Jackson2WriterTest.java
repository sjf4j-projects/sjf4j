package org.sjf4j.backend.jackson2.binding;

import com.fasterxml.jackson.core.JsonFactory;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Jackson2WriterTest {

    @Test
    void writesValuesAndNullableDefaults() throws Exception {
        StringWriter output = new StringWriter();
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        Jackson2Writer writer = binder.createWriter(output);
        writer.startArray();
        writer.writeString("text");
        writer.writeCharValue('x');
        writer.writeLongValue(1L);
        writer.writeIntValue(2);
        writer.writeShortValue((short) 3);
        writer.writeByteValue((byte) 4);
        writer.writeDoubleValue(5.5d);
        writer.writeFloatValue(6.5f);
        writer.writeBooleanValue(true);
        writer.writeNumberValue(new BigInteger("12345678901234567890"));
        writer.writeNumberValue(new BigDecimal("7.25"));
        writer.writeNumberValue(new AtomicInteger(8));
        writer.writeString(null);
        writer.writeNumber(null);
        writer.writeBigInteger(null);
        writer.writeBigDecimal(null);
        writer.endArray();
        writer.flush();

        assertEquals("[\"text\",\"x\",1,2,3,4,5.5,6.5,true,12345678901234567890,7.25,8,null,null,null,null]", output.toString());
        writer.close();
    }

    @Test
    void writesPreparedNamesAndFusedProperties() throws Exception {
        StringWriter output = new StringWriter();
        Jackson2Binder binder = new Jackson2Binder(new JsonFactory());
        Jackson2Writer writer = binder.createWriter(output);
        Jackson2PreparedName text = new Jackson2PreparedName("te\"xt");
        Jackson2PreparedName number = new Jackson2PreparedName("number");
        writer.startObject();
        writer.writeName(text);
        writer.writeString("value");
        writer.writeName(number);
        writer.writeIntValue(42);
        writer.writeName(new Jackson2PreparedName("decimal"));
        writer.writeBigDecimal(new BigDecimal("1.20"));
        writer.writeName(new Jackson2PreparedName("nullable"));
        writer.writeString(null);
        writer.writeName(new Jackson2PreparedName("nullValue"));
        writer.writeNull();
        writer.endObject();
        writer.close();

        assertEquals("{\"te\\\"xt\":\"value\",\"number\":42,\"decimal\":1.20,\"nullable\":null,\"nullValue\":null}", output.toString());
    }
}

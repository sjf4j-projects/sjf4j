package org.sjf4j.backend.jsonp.binding;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonpWriterTest {

    @Test
    void writesStructuralPrimitiveAndGenericNumberValues() throws Exception {
        StringWriter output = new StringWriter();
        JsonpWriter writer = new JsonpBinder().createWriter(output);

        writer.startObject();
        writer.writeName("values");
        writer.startArray();
        writer.writeStringValue("text");
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
        writer.endArray();
        writer.endObject();
        writer.flush();

        assertEquals("{\"values\":[\"text\",\"x\",1,2,3,4,5.5,6.5,true,12345678901234567890,7.25,8,null,null]}",
                output.toString());
        writer.close();
    }
}

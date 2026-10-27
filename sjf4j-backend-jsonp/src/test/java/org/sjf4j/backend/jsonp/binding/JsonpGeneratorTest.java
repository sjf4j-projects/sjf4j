package org.sjf4j.backend.jsonp.binding;

import org.junit.jupiter.api.Test;
import org.sjf4j.binding.CompiledName;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonpGeneratorTest {

    @Test
    void writesStructuralPrimitiveAndGenericNumberValues() throws Exception {
        StringWriter output = new StringWriter();
        JsonpGenerator generator = new JsonpBinder().createGenerator(output);

        generator.writeObjectStart();
        generator.writeName("values");
        generator.writeArrayStart();
        generator.writeStringValue("text");
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
        generator.writeArrayEnd();
        generator.writeObjectEnd();
        generator.end();

        assertEquals("{\"values\":[\"text\",\"x\",1,2,3,4,5.5,6.5,true,12345678901234567890,7.25,8,null,null]}",
                output.toString());
        generator.close();
    }

    @Test
    void writesCompiledNames() throws Exception {
        StringWriter output = new StringWriter();
        JsonpGenerator generator = new JsonpBinder().createGenerator(output);

        generator.writeObjectStart();
        generator.writeName(new CompiledName("te\"xt"));
        generator.writeString("value");
        generator.writeName(new CompiledName("number"));
        generator.writeIntValue(42);
        generator.writeObjectEnd();
        generator.close();

        assertEquals("{\"te\\\"xt\":\"value\",\"number\":42}", output.toString());
    }
}

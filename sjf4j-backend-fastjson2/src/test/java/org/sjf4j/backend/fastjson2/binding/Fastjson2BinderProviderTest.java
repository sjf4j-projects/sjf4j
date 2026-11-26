package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.NodeConfig;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.binding.Format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Fastjson2BinderProviderTest {

    @Test
    void createsBinderWithSuppliedContexts() {
        JSONReader.Context readerContext = JSONFactory.createReadContext(
                JSONReader.Feature.AllowUnQuotedFieldNames);
        JSONWriter.Context writerContext = JSONFactory.createWriteContext();
        BinderProvider provider = Fastjson2BinderProvider.of(readerContext, writerContext);

        Fastjson2Binder binder = assertInstanceOf(Fastjson2Binder.class,
                provider.create(NodeConfig.DEFAULT));

        assertEquals(7, ((Value) binder.readNode("{value:7}", Value.class)).value);
    }

    @Test
    void sharedContextIsNotMutatedByNumericPolicy() throws Exception {
        JSONReader.Context context = JSONFactory.createReadContext(
                JSONReader.Feature.AllowUnQuotedFieldNames,
                JSONReader.Feature.UseLongForInts);
        long features = context.getFeatures();
        BinderProvider provider = Fastjson2BinderProvider.of(context, JSONFactory.createWriteContext());
        Fastjson2Binder ordinary = (Fastjson2Binder) provider.create(NodeConfig.DEFAULT);
        Fastjson2Binder exact = (Fastjson2Binder) provider.create(NodeConfig.EXACT_NUMBERS);
        assertEquals(features, context.getFeatures());
        assertEquals(0.1d, ordinary.readNode("0.1", Number.class));
        assertEquals(new java.math.BigDecimal("0.1"), exact.readNode("0.1", Number.class));
        assertEquals(new java.math.BigInteger("9223372036854775808"),
                exact.readNode("9223372036854775808", Number.class));
        assertEquals(new java.math.BigDecimal("1.23e-400"),
                exact.readNode("1.23e-400", Number.class));
        try (Fastjson2Parser parser = exact.createParser("0.1", false)) {
            parser.start();
            assertEquals(0.1d, parser.readNumber());
            parser.end();
        }
        try (Fastjson2Parser parser = ordinary.createParser("0.1", true)) {
            parser.start();
            assertEquals(new java.math.BigDecimal("0.1"), parser.readNumber());
            parser.end();
        }
        assertEquals(features, context.getFeatures());
    }

    @Test
    void incompatibleExternalReaderIsRejectedWithoutMutatingItsContext() {
        JSONReader.Context context = JSONFactory.createReadContext(JSONReader.Feature.UseDoubleForDecimals);
        long features = context.getFeatures();
        try (JSONReader reader = JSONReader.of("0.1", context)) {
            assertThrows(org.sjf4j.exception.BindingException.class,
                    () -> new Fastjson2Binder(NodeConfig.EXACT_NUMBERS).createParser(reader));
            assertEquals(features, context.getFeatures());
        }
        JSONReader.Context exactContext = JSONFactory.createReadContext(JSONReader.Feature.UseBigDecimalForDoubles);
        try (Fastjson2Parser parser = new Fastjson2Binder(NodeConfig.EXACT_NUMBERS)
                .createParser(JSONReader.of("1e-400", exactContext))) {
            assertEquals(new java.math.BigDecimal("1e-400"), parser.readNumber());
        }
    }

    @Test
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BinderFactory.provider(Format.JSON);

        assertInstanceOf(Fastjson2BinderProvider.class, provider);
        assertInstanceOf(Fastjson2Binder.class, provider.create(NodeConfig.DEFAULT));
    }

    @Test
    void rejectsNullContexts() {
        JSONReader.Context readerContext = JSONFactory.createReadContext();
        JSONWriter.Context writerContext = JSONFactory.createWriteContext();

        assertThrows(NullPointerException.class, () -> Fastjson2BinderProvider.of(null, writerContext));
        assertThrows(NullPointerException.class, () -> Fastjson2BinderProvider.of(readerContext, null));
    }

    static class Value {
        public int value;
    }
}

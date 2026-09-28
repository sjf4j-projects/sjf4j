package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.jupiter.api.Test;
import org.sjf4j.RuntimeContext;
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
                provider.create(RuntimeContext.EMPTY));

        assertEquals(7, ((Value) binder.readNode("{value:7}", Value.class)).value);
    }

    @Test
    void isDiscoveredAsTheDefaultJsonBinderProvider() {
        BinderProvider provider = BinderFactory.provider(Format.JSON);

        assertInstanceOf(Fastjson2BinderProvider.class, provider);
        assertInstanceOf(Fastjson2Binder.class, provider.create(RuntimeContext.EMPTY));
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

package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.BinderProvider;
import org.sjf4j.binding.Format;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

/**
 * Service provider for Fastjson2 JSON binding, selected below Gson and above
 * JSON-P by default. {@link #of(JSONReader.Context, JSONWriter.Context)} reuses
 * the supplied contexts for created binders.
 */
public final class Fastjson2BinderProvider implements BinderProvider {

    private static final String JSON_FACTORY_TYPE = "com.alibaba.fastjson2.JSONFactory";
    private static final boolean AVAILABLE = BinderProvider.isClassAvailable(
            JSON_FACTORY_TYPE, Fastjson2BinderProvider.class.getClassLoader());

    /** Creates a discoverable provider with default Fastjson2 contexts. */
    public Fastjson2BinderProvider() {
    }

    /**
     * Creates a provider that uses the supplied contexts for every binder it creates.
     * @param readerContext the shared read context
     * @param writerContext the shared write context
     * @return a JSON binder provider using these contexts
     */
    public static BinderProvider of(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        JSONReader.Context checkedReaderContext = Asserts.notNull(readerContext, "readerContext");
        JSONWriter.Context checkedWriterContext = Asserts.notNull(writerContext, "writerContext");
        return BinderProvider.of(Format.JSON, 300,
                context -> new Fastjson2Binder(checkedReaderContext, checkedWriterContext, context));
    }

    @Override
    public Format format() {
        return Format.JSON;
    }

    @Override
    public boolean isAvailable() {
        return AVAILABLE;
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public Binder<?, ?> create(RuntimeContext context) {
        return new Fastjson2Binder(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), context);
    }
}

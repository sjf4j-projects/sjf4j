package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

/** JSON binder backed directly by Fastjson2's streaming reader and writer. */
public final class Fastjson2JsonBinder extends JsonBinder<Fastjson2Reader, Fastjson2Writer> {

    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    public Fastjson2JsonBinder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), StreamingContext.EMPTY);
    }

    public Fastjson2JsonBinder(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        this(readerContext, writerContext, StreamingContext.EMPTY);
    }

    public Fastjson2JsonBinder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                               StreamingContext context) {
        super(context);
        this.readerContext = Objects.requireNonNull(readerContext, "readerContext");
        this.writerContext = Objects.requireNonNull(writerContext, "writerContext");
    }

    @Override
    public Fastjson2Reader createReader(Reader input) throws IOException {
        return new Fastjson2Reader(JSONReader.of(Objects.requireNonNull(input, "input"), readerContext));
    }

    @Override
    public Fastjson2Reader createReader(String input) throws IOException {
        return new Fastjson2Reader(JSONReader.of(Objects.requireNonNull(input, "input"), readerContext));
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) throws IOException {
        return new Fastjson2Reader(JSONReader.of(Objects.requireNonNull(input, "input"), readerContext));
    }

    /** Creates a streaming reader that wraps the supplied Fastjson2 reader. */
    public Fastjson2Reader createReader(JSONReader reader) {
        return new Fastjson2Reader(Objects.requireNonNull(reader, "reader"));
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) throws IOException {
        return new Fastjson2Writer(this, JSONWriter.of(writerContext), Objects.requireNonNull(output, "output"));
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) throws IOException {
        return new Fastjson2Writer(this, JSONWriter.ofUTF8(writerContext),
                Objects.requireNonNull(output, "output"));
    }

    /** Creates a streaming writer that wraps the supplied Fastjson2 writer. */
    public Fastjson2Writer createWriter(JSONWriter writer) {
        return new Fastjson2Writer(this, Objects.requireNonNull(writer, "writer"));
    }
}

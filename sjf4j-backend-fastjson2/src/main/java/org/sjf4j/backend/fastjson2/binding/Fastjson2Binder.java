package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.binding.JsonBinder;
import org.sjf4j.binding.StreamingContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** JSON binder backed directly by Fastjson2's streaming reader and writer. */
public final class Fastjson2Binder extends JsonBinder<Fastjson2Reader, Fastjson2Writer> {

    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    public Fastjson2Binder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), StreamingContext.EMPTY);
    }

    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        this(readerContext, writerContext, StreamingContext.EMPTY);
    }

    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                           StreamingContext context) {
        super(context);
        this.readerContext = Objects.requireNonNull(readerContext, "readerContext");
        this.writerContext = Objects.requireNonNull(writerContext, "writerContext");
        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals);
        if (context.includeNulls) {
            this.writerContext.config(JSONWriter.Feature.WriteNulls);
        }
    }


    /*
     * --------------------------------------------------------------
     * Read
     * --------------------------------------------------------------
     */

    /** Creates a streaming reader that wraps the supplied Fastjson2 reader. */
    public Fastjson2Reader createReader(JSONReader reader) {
        Objects.requireNonNull(reader, "reader");
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(Reader input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(InputStream input) {
        Objects.requireNonNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, StandardCharsets.UTF_8, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(String input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) throws IOException {
        Objects.requireNonNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }


    /*
     * --------------------------------------------------------------
     * Write
     * --------------------------------------------------------------
     */


    /** Creates a streaming writer that wraps the supplied Fastjson2 writer. */
    public Fastjson2Writer createWriter(JSONWriter writer) {
        Objects.requireNonNull(writer, "writer");
        return new Fastjson2Writer(this, writer);
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) throws IOException {
        Objects.requireNonNull(output, "output");
        return new Fastjson2Writer(this, JSONWriter.of(writerContext));
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) throws IOException {
        Objects.requireNonNull(output, "output");
        return new Fastjson2Writer(this, JSONWriter.ofUTF8(writerContext));
    }

}

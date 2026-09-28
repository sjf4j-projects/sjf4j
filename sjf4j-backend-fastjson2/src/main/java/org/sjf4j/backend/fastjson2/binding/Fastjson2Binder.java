package org.sjf4j.backend.fastjson2.binding;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** JSON binder backed directly by Fastjson2's streaming reader and writer. */
public final class Fastjson2Binder extends Binder<Fastjson2Reader, Fastjson2Writer> {

    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

    public Fastjson2Binder() {
        this(JSONFactory.createReadContext(), JSONFactory.createWriteContext(), RuntimeContext.EMPTY);
    }

    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext) {
        this(readerContext, writerContext, RuntimeContext.EMPTY);
    }

    public Fastjson2Binder(JSONReader.Context readerContext, JSONWriter.Context writerContext,
                           RuntimeContext context) {
        super(context);
        this.readerContext = Asserts.notNull(readerContext, "readerContext");
        this.writerContext = Asserts.notNull(writerContext, "writerContext");
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
        Asserts.notNull(reader, "reader");
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(InputStream input) {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, StandardCharsets.UTF_8, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(String input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return new Fastjson2Reader(JSONReader.of(input, readerContext));
    }


    /*
     * --------------------------------------------------------------
     * Write
     * --------------------------------------------------------------
     */


    /** Creates a streaming writer that wraps the supplied Fastjson2 writer. */
    public Fastjson2Writer createWriter(JSONWriter writer) {
        Asserts.notNull(writer, "writer");
        return new Fastjson2Writer(this, writer);
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Writer(this, JSONWriter.of(writerContext));
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) throws IOException {
        Asserts.notNull(output, "output");
        return new Fastjson2Writer(this, JSONWriter.ofUTF8(writerContext));
    }

}

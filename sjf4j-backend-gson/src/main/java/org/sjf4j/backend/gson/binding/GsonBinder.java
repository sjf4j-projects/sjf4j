package org.sjf4j.backend.gson.binding;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.RuntimeContext;
import org.sjf4j.binding.Binder;
import org.sjf4j.util.Asserts;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;


/**
 * JSON binder using Gson's streaming API. Supplied Gson settings are used
 * when creating readers and writers; writers enable null serialization so
 * SJF4J can apply its own {@code includeNulls} setting.
 */
public final class GsonBinder extends Binder<GsonReader, GsonWriter> {

    private final Gson gson;

    /** Creates a binder with a default Gson instance and runtime settings. */
    public GsonBinder() {
        this(new Gson(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder using the supplied Gson instance.
     * @param gson the Gson instance used for reader and writer creation
     */
    public GsonBinder(Gson gson) {
        this(gson, RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder using the supplied Gson instance and runtime settings.
     * @param gson the Gson instance used for reader and writer creation
     * @param context the SJF4J runtime settings
     */
    public GsonBinder(Gson gson, RuntimeContext context) {
        super(context);
        this.gson = Asserts.notNull(gson, "gson");
    }

    /**
     * Creates a streaming reader that wraps the supplied Gson reader.
     * @param reader the Gson reader
     * @return a SJF4J streaming reader
     */
    public GsonReader createReader(JsonReader reader) {
        return new GsonReader(Asserts.notNull(reader, "reader"));
    }

    @Override
    public GsonReader createReader(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonReader createReader(InputStream input) throws IOException {
        Asserts.notNull(input, "input");
        return createReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @Override
    public GsonReader createReader(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return createReader(new ByteArrayInputStream(input));
    }

    /**
     * Creates a streaming writer that wraps the supplied Gson writer.
     * @param writer the Gson writer
     * @return a SJF4J streaming writer
     */
    public GsonWriter createWriter(JsonWriter writer) {
        Asserts.notNull(writer, "writer");
        writer.setSerializeNulls(true);
        return new GsonWriter(writer);
    }

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return createWriter(gson.newJsonWriter(output));
    }

    @Override
    public GsonWriter createWriter(OutputStream output) throws IOException {
        Asserts.notNull(output, "output");
        return createWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }


    @Override
    public byte[] writeNodeAsBytes(Object node) {
        return writeNodeAsString(node).getBytes(StandardCharsets.UTF_8);
    }
}

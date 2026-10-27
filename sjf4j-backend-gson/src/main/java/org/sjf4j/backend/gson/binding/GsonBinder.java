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
public final class GsonBinder extends Binder<GsonParser, GsonGenerator> {

    private final Gson gson;

    /** Creates a binder with a default Gson instance and runtime settings. */
    public GsonBinder() {
        this(new Gson(), RuntimeContext.EMPTY);
    }

    /**
     * Creates a binder with default backend settings and a supplied runtime context.
     * @param context the SJF4J runtime settings
     */
    public GsonBinder(RuntimeContext context) {
        this(new Gson(), context);
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
    public GsonParser createParser(JsonReader reader) {
        return new GsonParser(Asserts.notNull(reader, "reader"));
    }

    @Override
    public GsonParser createParser(Reader input) throws IOException {
        Asserts.notNull(input, "input");
        return new GsonParser(gson.newJsonReader(input));
    }

    @Override
    public GsonParser createParser(InputStream input) throws IOException {
        Asserts.notNull(input, "input");
        return createParser(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @Override
    public GsonParser createParser(byte[] input) throws IOException {
        Asserts.notNull(input, "input");
        return createParser(new ByteArrayInputStream(input));
    }

    /**
     * Creates a streaming writer that wraps the supplied Gson writer.
     * @param writer the Gson writer
     * @return a SJF4J streaming writer
     */
    public GsonGenerator createGenerator(JsonWriter writer) {
        Asserts.notNull(writer, "writer");
        writer.setSerializeNulls(true);
        return new GsonGenerator(writer);
    }

    @Override
    public GsonGenerator createGenerator(Writer output) throws IOException {
        Asserts.notNull(output, "output");
        return createGenerator(gson.newJsonWriter(output));
    }

    @Override
    public GsonGenerator createGenerator(OutputStream output) throws IOException {
        Asserts.notNull(output, "output");
        return createGenerator(new OutputStreamWriter(output, StandardCharsets.UTF_8));
    }


    @Override
    public byte[] writeNodeAsBytes(Object node) {
        return writeNodeAsString(node).getBytes(StandardCharsets.UTF_8);
    }
}

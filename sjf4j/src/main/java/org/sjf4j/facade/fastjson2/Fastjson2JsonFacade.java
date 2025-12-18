package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public class Fastjson2JsonFacade implements JsonFacade<Fastjson2Reader, Fastjson2Writer> {

    private final JSONReader.Feature[] readerFeatures;
    private final JSONWriter.Feature[] writerFeatures;
    private final JSONReader.Context ctx;

    public Fastjson2JsonFacade() {
        this(new JSONReader.Feature[0], new JSONWriter.Feature[0]);
    }

    public Fastjson2JsonFacade(JSONWriter.Feature... writerFeatures) {
        this(new JSONReader.Feature[0], writerFeatures);
    }

    public Fastjson2JsonFacade(JSONReader.Feature... readerFeatures) {
        this(readerFeatures, new JSONWriter.Feature[0]);
    }

    public Fastjson2JsonFacade(JSONReader.Feature[] readerFeatures,
                               JSONWriter.Feature[] writerFeatures) {
        if (readerFeatures == null) throw new IllegalArgumentException("ReaderFeatures must not be null");
        if (writerFeatures == null) throw new IllegalArgumentException("WriterFeatures must not be null");
        this.readerFeatures = readerFeatures;
        this.writerFeatures = writerFeatures;
        this.ctx = JSONFactory.createReadContext(readerFeatures);

        // With Module
        if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE) {
            ObjectReaderProvider provider = JSONFactory.getDefaultObjectReaderProvider();
//            provider.register(JsonArray.class, new Fastjson2Module.JsonArrayReader());
            provider.register(new Fastjson2Module.MyReaderModule());
            this.ctx.setExtraProcessor((object, key, value) -> {
                if (object instanceof JsonObject) {
                    ((JsonObject) object).put(key, value);
                }
            });
        }
        if (JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
            ObjectWriterProvider provider = JSONFactory.getDefaultObjectWriterProvider();
            provider.register(new Fastjson2Module.MyWriterModule());
        }
    }

    @Override
    public Fastjson2Reader createReader(Reader input) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(InputStream input) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, new JSONReader.Context(readerFeatures));
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(String input) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) {
        JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures));
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Writer createWriter(Writer output) {
        JSONWriter writer = JSONWriter.of(writerFeatures);
        return new Fastjson2Writer(writer);
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) {
        JSONWriter writer = JSONWriter.ofUTF8(writerFeatures);
        return new Fastjson2Writer(writer);
    }


    /// Read

    @Override
    public Object readNode(Reader input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures))) {
                    return Fastjson2StreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, ctx)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8,
                        new JSONReader.Context(readerFeatures))) {
                    return Fastjson2StreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, ctx)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures))) {
                    return Fastjson2StreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, ctx)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        switch (JsonConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JSONReader reader = JSONReader.of(input, new JSONReader.Context(readerFeatures))) {
                    return Fastjson2StreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, ctx)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    /// Write

    @Override
    public void writeNode(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONWriter writer = JSONWriter.of(writerFeatures);
                    Fastjson2StreamingUtil.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    String json = JSON.toJSONString(node, writerFeatures);
                    output.write(json);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }


    @Override
    public void writeNode(OutputStream output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONWriter writer = JSONWriter.ofUTF8(writerFeatures);
                    Fastjson2StreamingUtil.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try {
                    byte[] bs = JSON.toJSONBytes(node, writerFeatures);
                    output.write(bs);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }


}

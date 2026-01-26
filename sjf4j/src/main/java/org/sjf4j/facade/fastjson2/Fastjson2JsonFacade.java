package org.sjf4j.facade.fastjson2;

import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.reader.ObjectReaderProvider;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.JsonObject;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.node.Types;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Fastjson2JsonFacade implements JsonFacade<Fastjson2Reader, Fastjson2Writer> {

//    private final JSONReader.Feature[] readerFeatures;
//    private final JSONWriter.Feature[] writerFeatures;
    private final JSONReader.Context readerContext;
    private final JSONWriter.Context writerContext;

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
        Objects.requireNonNull(readerFeatures, "readerFeatures is null");
        Objects.requireNonNull(writerFeatures, "writerFeatures is null");
//        this.readerFeatures = readerFeatures;
//        this.writerFeatures = writerFeatures;
        this.readerContext = JSONFactory.createReadContext(readerFeatures);
        this.writerContext = JSONFactory.createWriteContext(writerFeatures);

        // Use Double but BigDecimal
        this.readerContext.config(JSONReader.Feature.UseDoubleForDecimals);

        // With Module
        if (Sjf4jConfig.global().readMode == Sjf4jConfig.ReadMode.USE_MODULE) {
            ObjectReaderProvider provider = JSONFactory.getDefaultObjectReaderProvider();
            provider.register(new Fastjson2Module.MyReaderModule());
            this.readerContext.setExtraProcessor((object, key, value) -> {
                if (object instanceof JsonObject) {
                    ((JsonObject) object).put(key, value);
                }
            });
        }
        if (Sjf4jConfig.global().writeMode == Sjf4jConfig.WriteMode.USE_MODULE) {
            ObjectWriterProvider provider = JSONFactory.getDefaultObjectWriterProvider();
            provider.register(new Fastjson2Module.MyWriterModule());
        }
    }

    @Override
    public Fastjson2Reader createReader(Reader input) {
        Objects.requireNonNull(input, "input is null");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(InputStream input) {
        Objects.requireNonNull(input, "input is null");
        JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(String input) {
        Objects.requireNonNull(input, "input is null");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    @Override
    public Fastjson2Reader createReader(byte[] input) {
        Objects.requireNonNull(input, "input is null");
        JSONReader reader = JSONReader.of(input, readerContext);
        return new Fastjson2Reader(reader);
    }

    @Override
    public Object readNode(Reader input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + Types.name(type) + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + Types.name(type) + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + Types.name(type) + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    JSONReader reader = JSONReader.of(input, StandardCharsets.UTF_8, readerContext);
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node type '" + Types.name(type) + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + Types.name(type) + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON string into node type '" + Types.name(type) + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        Objects.requireNonNull(input, "input is null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try {
                    JSONReader reader = JSONReader.of(input, readerContext);
                    return Fastjson2StreamingIO.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON bytes into node type '" + Types.name(type) + "'", e);
                }
            }
            case USE_MODULE: {
                try (JSONReader reader = JSONReader.of(input, readerContext)) {
                    return reader.read(type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON bytes into node type '" + Types.name(type) + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }


    /// Write

    @Override
    public Fastjson2Writer createWriter(Writer output) {
        Objects.requireNonNull(output, "output is null");
        JSONWriter writer = JSONWriter.of(writerContext);
        return new Fastjson2Writer(writer);     // Fake writer
    }

    @Override
    public Fastjson2Writer createWriter(OutputStream output) {
        Objects.requireNonNull(output, "output is null");
        JSONWriter writer = JSONWriter.ofUTF8(writerContext);
        return new Fastjson2Writer(writer);     // Fake writer
    }


    @Override
    public void writeNode(Writer output, Object node) {
        Objects.requireNonNull(output, "output is null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    writer.writeAny(node);
                    writer.flushTo(output);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }


    @Override
    public void writeNode(OutputStream output, Object node) {
        Objects.requireNonNull(output, "output is null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    writer.flushTo(output);
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    writer.writeAny(node);
                    writer.flushTo(output);
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }

    @Override
    public String writeNodeAsString(Object node) {
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.writeNodeAsString(node);
            }
            case STREAMING_SPECIFIC: {
                try (JSONWriter writer = JSONWriter.of(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    return writer.toString();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON string", e);
                }
            }
            case USE_MODULE: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    writer.writeAny(node);
                    return writer.toString();
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) + "' to JSON string", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }

    @Override
    public byte[] writeNodeAsBytes(Object node) {
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.writeNodeAsBytes(node);
            }
            case STREAMING_SPECIFIC: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    Fastjson2StreamingIO.writeNode(writer, node);
                    return writer.getBytes();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node type " + Types.name(node) + " to JSON bytes", e);
                }
            }
            case USE_MODULE: {
                try (JSONWriter writer = JSONWriter.ofUTF8(writerContext)) {
                    writer.writeAny(node);
                    return writer.getBytes();
                } catch (Exception e) {
                    throw new JsonException("Failed to write node of type '" + Types.name(node) + "' to JSON bytes", e);
                }
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }



}

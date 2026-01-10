package org.sjf4j.facade.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.sjf4j.Sjf4jConfig;
import org.sjf4j.JsonException;
import org.sjf4j.facade.JsonFacade;
import org.sjf4j.util.TypeUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public class GsonJsonFacade implements JsonFacade<GsonReader, GsonWriter> {

    private final Gson gson;

    public GsonJsonFacade(GsonBuilder gsonBuilder) {
        if (gsonBuilder == null) throw new IllegalArgumentException("GsonBuilder must not be null");
        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        if (Sjf4jConfig.global().readMode == Sjf4jConfig.ReadMode.USE_MODULE ||
                Sjf4jConfig.global().writeMode == Sjf4jConfig.WriteMode.USE_MODULE) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
        }
        // TODO: Retrieve the original FieldNamingStrategy via reflection?
        gsonBuilder.setFieldNamingStrategy(new GsonModule.NodeFieldNamingStrategy());
        this.gson = gsonBuilder.create();
    }

    @Override
    public GsonReader createReader(Reader input) throws IOException {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonWriter createWriter(Writer output) throws IOException {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        return new GsonWriter(gson.newJsonWriter(output));
    }



    /// API

    @Override
    public Object readNode(Reader input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonReader reader = gson.newJsonReader(input)) {
                    return GsonStreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JsonReader reader = gson.newJsonReader(input)) {
                    return gson.fromJson(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(InputStream input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonReader reader = gson.newJsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    return GsonStreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JsonReader reader = gson.newJsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    return gson.fromJson(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(String input, Type type) {
        if (input == null) throw new IllegalArgumentException("Input must not be null");
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonReader reader = gson.newJsonReader(new StringReader(input))) {
                    return GsonStreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try {
                    return gson.fromJson(input, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(byte[] input, Type type) {
        switch (Sjf4jConfig.global().readMode) {
            case STREAMING_GENERAL: {
                return JsonFacade.super.readNode(input, type);
            }
            case STREAMING_SPECIFIC: {
                try (JsonReader reader = gson.newJsonReader(
                        new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
                    return GsonStreamingUtil.readNode(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            case USE_MODULE: {
                try (JsonReader reader = gson.newJsonReader(
                        new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8))) {
                    return gson.fromJson(reader, type);
                } catch (Exception e) {
                    throw new JsonException("Failed to read JSON streaming into node of type '" + type + "'", e);
                }
            }
            default:
                throw new JsonException("Unsupported read mode '" + Sjf4jConfig.global().readMode + "'");
        }
    }


    @Override
    public void writeNode(Writer output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonWriter writer = gson.newJsonWriter(output);
                    GsonStreamingUtil.writeNode(writer, node);
                    writer.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                gson.toJson(node, output);
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }


    @Override
    public void writeNode(OutputStream output, Object node) {
        if (output == null) throw new IllegalArgumentException("Output must not be null");
        switch (Sjf4jConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonWriter writer = gson.newJsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
                    GsonStreamingUtil.writeNode(writer, node);
                    writer.flush();
                } catch (IOException e) {
                    throw new JsonException("Failed to write node of type '" + TypeUtil.typeName(node) +
                            "' to JSON streaming", e);
                }
                break;
            }
            case USE_MODULE: {
                gson.toJson(node, new OutputStreamWriter(output, StandardCharsets.UTF_8));
                break;
            }
            default:
                throw new JsonException("Unsupported write mode '" + Sjf4jConfig.global().writeMode + "'");
        }
    }



}

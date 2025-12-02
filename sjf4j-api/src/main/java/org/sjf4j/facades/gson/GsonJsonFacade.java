package org.sjf4j.facades.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.NonNull;
import org.sjf4j.JsonConfig;
import org.sjf4j.JsonException;
import org.sjf4j.facades.JsonFacade;
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

    @Getter
    private final Gson gson;

    public GsonJsonFacade(@NonNull GsonBuilder gsonBuilder) {
        gsonBuilder.setNumberToNumberStrategy(new GsonModule.MyToNumberStrategy());
        gsonBuilder.setObjectToNumberStrategy(new GsonModule.MyToNumberStrategy());
        if (JsonConfig.global().readMode == JsonConfig.ReadMode.USE_MODULE ||
                JsonConfig.global().writeMode == JsonConfig.WriteMode.USE_MODULE) {
            gsonBuilder.registerTypeAdapterFactory(new GsonModule.MyTypeAdapterFactory());
        }
        this.gson = gsonBuilder.create();
    }

    @Override
    public GsonReader createReader(@NonNull Reader input) throws IOException {
        return new GsonReader(gson.newJsonReader(input));
    }

    @Override
    public GsonWriter createWriter(@NonNull Writer output) throws IOException {
        return new GsonWriter(gson.newJsonWriter(output));
    }



    /// API

    @Override
    public Object readNode(@NonNull Reader input, Type type) {
        switch (JsonConfig.global().readMode) {
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
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(@NonNull InputStream input, Type type) {
        switch (JsonConfig.global().readMode) {
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
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }

    @Override
    public Object readNode(@NonNull String input, Type type) {
        switch (JsonConfig.global().readMode) {
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
                return gson.fromJson(input, type);
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
                throw new JsonException("Unsupported read mode '" + JsonConfig.global().readMode + "'");
        }
    }


    @Override
    public void writeNode(@NonNull Writer output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonWriter writer = gson.newJsonWriter(output);
                    GsonStreamingUtil.startDocument(writer);
                    GsonStreamingUtil.writeNode(writer, node);
                    GsonStreamingUtil.endDocument(writer);
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
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }


    @Override
    public void writeNode(@NonNull OutputStream output, Object node) {
        switch (JsonConfig.global().writeMode) {
            case STREAMING_GENERAL: {
                JsonFacade.super.writeNode(output, node);
                break;
            }
            case STREAMING_SPECIFIC: {
                try {
                    JsonWriter writer = gson.newJsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
                    GsonStreamingUtil.startDocument(writer);
                    GsonStreamingUtil.writeNode(writer, node);
                    GsonStreamingUtil.endDocument(writer);
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
                throw new JsonException("Unsupported write mode '" + JsonConfig.global().writeMode + "'");
        }
    }



}

package org.sjf4j.spring.example;

import org.sjf4j.NodeConfig;
import org.sjf4j.binding.Binder;
import org.sjf4j.binding.BinderFactory;
import org.sjf4j.exception.BindingException;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Example application-owned Spring MVC converter for SJF4J runtime JSON/YAML binding.
 *
 * <p>Register as an HttpMessageConverter bean in Spring Boot, or use
 * WebMvcConfigurer.extendMessageConverters in plain Spring MVC.
 * YAML requires sjf4j-backend-snake and SnakeYAML on the classpath.
 *
 * <p>This is a test example, not an SJF4J public API.
 */
public final class Sjf4jHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final Binder<?, ?> json;
    private final Binder<?, ?> yaml;

    public Sjf4jHttpMessageConverter(NodeConfig config) {
        this(BinderFactory.jsonProvider().create(config),
                BinderFactory.yamlProvider().create(config));
    }

    public Sjf4jHttpMessageConverter(Binder<?, ?> json, Binder<?, ?> yaml) {
        super(MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("application/*+json"),
                MediaType.parseMediaType("application/yaml"));
        this.json = Objects.requireNonNull(json, "json");
        this.yaml = Objects.requireNonNull(yaml, "yaml");
    }

    @Override
    protected boolean supports(Class<?> type) {
        // Leave Spring's special body types to their existing converters.
        return type != String.class
                && type != byte[].class
                && !Resource.class.isAssignableFrom(type)
                && !ProblemDetail.class.isAssignableFrom(type);
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage input) throws IOException {
        Type target = contextClass == null ? type
                : GenericTypeResolver.resolveType(type, contextClass);
        try {
            if (isYaml(input.getHeaders().getContentType())) {
                Reader reader = new InputStreamReader(input.getBody(), StandardCharsets.UTF_8);
                return yaml.readNode(reader, target);
            }
            return json.readNode(input.getBody(), target);
        } catch (BindingException e) {
            throw new HttpMessageNotReadableException("Cannot read request body", e, input);
        }
    }

    @Override
    protected Object readInternal(Class<? extends Object> type, HttpInputMessage input) throws IOException {
        return read(type, null, input);
    }

    @Override
    protected void writeInternal(Object value, Type type, HttpOutputMessage output) throws IOException {
        try {
            if (isYaml(output.getHeaders().getContentType())) {
                Writer writer = new OutputStreamWriter(output.getBody(), StandardCharsets.UTF_8);
                yaml.writeNode(writer, value);
                writer.flush();
            } else {
                json.writeNode(output.getBody(), value);
            }
        } catch (BindingException e) {
            throw new HttpMessageNotWritableException("Cannot write response body", e);
        }
    }

    private static boolean isYaml(MediaType mediaType) {
        return mediaType != null
                && "application".equals(mediaType.getType())
                && "yaml".equals(mediaType.getSubtype());
    }
}

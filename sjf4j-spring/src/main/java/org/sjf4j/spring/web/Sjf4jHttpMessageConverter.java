package org.sjf4j.spring.web;

import org.sjf4j.RuntimeContext;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Spring MVC HTTP body converter using SJF4J runtime JSON and YAML binding.
 *
 * <p>Register this converter before Jackson's JSON converter through
 * {@code WebMvcConfigurer.extendMessageConverters}. It does not change
 * Spring Boot's application YAML configuration loading.
 *
 * <p>Requires Spring Web at runtime. YAML also requires a SJF4J YAML backend,
 * such as {@code sjf4j-backend-snake} with SnakeYAML.
 */
public final class Sjf4jHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {

    private final Binder<?, ?> json;
    private final Binder<?, ?> yaml;

    public Sjf4jHttpMessageConverter() {
        this(RuntimeContext.EMPTY);
    }

    public Sjf4jHttpMessageConverter(RuntimeContext context) {
        super(MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("application/*+json"),
                MediaType.parseMediaType("application/yaml"),
                MediaType.parseMediaType("application/x-yaml"),
                MediaType.parseMediaType("text/yaml"));
        Objects.requireNonNull(context, "context");
        this.json = BinderFactory.jsonProvider().create(context);
        this.yaml = BinderFactory.yamlProvider().create(context);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        // Keep special Spring response/request body types with their own converters.
        return clazz != String.class
                && clazz != byte[].class
                && !Resource.class.isAssignableFrom(clazz)
                && !ProblemDetail.class.isAssignableFrom(clazz);
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException {
        Type resolvedType = contextClass == null ? type : GenericTypeResolver.resolveType(type, contextClass);
        try {
            if (isYaml(inputMessage.getHeaders().getContentType())) {
                Reader reader = new InputStreamReader(inputMessage.getBody(), charset(inputMessage.getHeaders().getContentType()));
                return yaml.readNode(reader, resolvedType);
            }
            return json.readNode(inputMessage.getBody(), resolvedType);
        } catch (BindingException e) {
            throw new HttpMessageNotReadableException("SJF4J could not read HTTP body", e, inputMessage);
        }
    }

    @Override
    protected Object readInternal(Class<? extends Object> clazz, HttpInputMessage inputMessage) throws IOException {
        return read(clazz, null, inputMessage);
    }

    @Override
    protected void writeInternal(Object value, Type type, HttpOutputMessage outputMessage) throws IOException {
        try {
            MediaType contentType = outputMessage.getHeaders().getContentType();
            if (isYaml(contentType)) {
                Writer writer = new OutputStreamWriter(outputMessage.getBody(), charset(contentType));
                yaml.writeNode(writer, value);
                writer.flush();
            } else {
                json.writeNode(outputMessage.getBody(), value);
            }
        } catch (BindingException e) {
            throw new HttpMessageNotWritableException("SJF4J could not write HTTP body", e);
        }
    }

    private static boolean isYaml(MediaType contentType) {
        if (contentType == null) return false;
        String subtype = contentType.getSubtype();
        return ("application".equals(contentType.getType())
                && ("yaml".equals(subtype) || "x-yaml".equals(subtype)))
                || ("text".equals(contentType.getType()) && "yaml".equals(subtype));
    }

    private static Charset charset(MediaType contentType) {
        return contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
    }
}

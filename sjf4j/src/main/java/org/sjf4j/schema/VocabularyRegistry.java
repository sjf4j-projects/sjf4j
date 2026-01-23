package org.sjf4j.schema;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class VocabularyRegistry {

    private static final Map<String, String> KEYWORD_VOCAB_CACHE = new ConcurrentHashMap<>();

//    public static class Vocabulary {
//        private final String uri;
//        private final Set<String> keywords;
//        public Vocabulary(String uri, Set<String> keywords) {
//            Objects.requireNonNull(uri);
//            Objects.requireNonNull(keywords);
//            this.uri = uri;
//            this.keywords = keywords;
//        }
//        public String getUri() {return uri;}
//        public Set<String> getKeywords() {return keywords;}
//        public boolean contains(String keyword) {return keywords.contains(keyword);}
//    }

    public static void registerKeyword(String keyword, String vocabUri) {
        Objects.requireNonNull(keyword);
        Objects.requireNonNull(vocabUri);
        KEYWORD_VOCAB_CACHE.put(keyword, vocabUri);
    }

    public static String getVocabUri(String keyword) {
        return KEYWORD_VOCAB_CACHE.get(keyword);
    }


    /// Official Vocabulary List

    public static final String DRAFT_2020_12_VOCAB_CORE =
            "https://json-schema.org/draft/2020-12/vocab/core";
    public static final String DRAFT_2020_12_VOCAB_METADATA =
            "https://json-schema.org/draft/2020-12/vocab/meta-data";
    public static final String DRAFT_2020_12_VOCAB_VALIDATION =
            "https://json-schema.org/draft/2020-12/vocab/validation";
    public static final String DRAFT_2020_12_VOCAB_APPLICATOR =
            "https://json-schema.org/draft/2020-12/vocab/applicator";
    public static final String DRAFT_2020_12_VOCAB_UNEVALUATED =
            "https://json-schema.org/draft/2020-12/vocab/unevaluated";
    public static final String DRAFT_2020_12_VOCAB_FORMAT =
            "https://json-schema.org/draft/2020-12/vocab/format-annotation";
    public static final String DRAFT_2020_12_VOCAB_CONTENT =
            "https://json-schema.org/draft/2020-12/vocab/content";
    public static final String EXTENSION_VOCAB_SJF4J =
            "https://sjf4j.org/json-schema/vocab/sjf4j";

    static {
        // URI_DRAFT_2020_12_VOCAB_CORE
        registerKeyword("$schema", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$id", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$ref", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$dynamicRef", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$defs", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$anchor", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$dynamicAnchor", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$comment", DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$vocabulary", DRAFT_2020_12_VOCAB_CORE);

        // URI_DRAFT_2020_12_VOCAB_METADATA
        registerKeyword("title", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("description", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("default", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("deprecated", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("readOnly", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("writeOnly", DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("writeOnly", DRAFT_2020_12_VOCAB_METADATA);

        // URI_DRAFT_2020_12_VOCAB_VALIDATION
        registerKeyword("type", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("enum", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("const", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxLength", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minLength", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("pattern", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maximum", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("exclusiveMaximum", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minimum", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("exclusiveMinimum", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("multipleOf", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxItems", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minItems", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("uniqueItems", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxContains", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minContains", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxProperties", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minProperties", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("required", DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("dependentRequired", DRAFT_2020_12_VOCAB_VALIDATION);

        // URI_DRAFT_2020_12_VOCAB_APPLICATOR
        registerKeyword("allOf", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("anyOf", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("oneOf", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("not", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("if", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("then", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("else", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("items", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("prefixItems", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("contains", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("properties", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("patternProperties", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("additionalProperties", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("propertyNames", DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("dependentSchemas", DRAFT_2020_12_VOCAB_APPLICATOR);

        // URI_DRAFT_2020_12_VOCAB_UNEVALUATED
        registerKeyword("unevaluatedItems", DRAFT_2020_12_VOCAB_UNEVALUATED);
        registerKeyword("unevaluatedProperties", DRAFT_2020_12_VOCAB_UNEVALUATED);

        // URI_DRAFT_2020_12_VOCAB_FORMAT
        registerKeyword("format", DRAFT_2020_12_VOCAB_FORMAT);

        // URI_DRAFT_2020_12_VOCAB_CONTENT
        registerKeyword("contentEncoding", DRAFT_2020_12_VOCAB_CONTENT);
        registerKeyword("contentMediaType", DRAFT_2020_12_VOCAB_CONTENT);
        registerKeyword("contentSchema", DRAFT_2020_12_VOCAB_CONTENT);

        // URI_EXTENSION_VOCAB_SJF4J
        registerKeyword("id", EXTENSION_VOCAB_SJF4J);
        registerKeyword("definitions", EXTENSION_VOCAB_SJF4J);

    }

}

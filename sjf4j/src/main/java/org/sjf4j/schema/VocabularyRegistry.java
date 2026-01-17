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

    public static final String URI_DRAFT_2020_12_VOCAB_CORE = 
            "https://json-schema.org/draft/2020-12/vocab/core";
    public static final String URI_DRAFT_2020_12_VOCAB_METADATA = 
            "https://json-schema.org/draft/2020-12/vocab/meta-data";
    public static final String URI_DRAFT_2020_12_VOCAB_VALIDATION = 
            "https://json-schema.org/draft/2020-12/vocab/validation";
    public static final String URI_DRAFT_2020_12_VOCAB_APPLICATOR = 
            "https://json-schema.org/draft/2020-12/vocab/applicator";
    public static final String URI_DRAFT_2020_12_VOCAB_UNEVALUATED = 
            "https://json-schema.org/draft/2020-12/vocab/unevaluated";
    public static final String URI_DRAFT_2020_12_VOCAB_FORMAT = 
            "https://json-schema.org/draft/2020-12/vocab/format-annotation";
    public static final String URI_DRAFT_2020_12_VOCAB_CONTENT = 
            "https://json-schema.org/draft/2020-12/vocab/content";
    public static final String URI_EXTENSION_VOCAB_SJF4J =
            "https://sjf4j.org/json-schema/vocab/sjf4j";

    static {
        // URI_DRAFT_2020_12_VOCAB_CORE
        registerKeyword("$schema", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$id", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$ref", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$dynamicRef", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$defs", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$anchor", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$dynamicAnchor", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$comment", URI_DRAFT_2020_12_VOCAB_CORE);
        registerKeyword("$vocabulary", URI_DRAFT_2020_12_VOCAB_CORE);

        // URI_DRAFT_2020_12_VOCAB_METADATA
        registerKeyword("title", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("description", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("default", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("deprecated", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("readOnly", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("writeOnly", URI_DRAFT_2020_12_VOCAB_METADATA);
        registerKeyword("writeOnly", URI_DRAFT_2020_12_VOCAB_METADATA);

        // URI_DRAFT_2020_12_VOCAB_VALIDATION
        registerKeyword("type", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("enum", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("const", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxLength", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minLength", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("pattern", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maximum", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("exclusiveMaximum", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minimum", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("exclusiveMinimum", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("multipleOf", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxItems", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minItems", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("uniqueItems", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxContains", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minContains", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("maxProperties", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("minProperties", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("required", URI_DRAFT_2020_12_VOCAB_VALIDATION);
        registerKeyword("dependentRequired", URI_DRAFT_2020_12_VOCAB_VALIDATION);

        // URI_DRAFT_2020_12_VOCAB_APPLICATOR
        registerKeyword("allOf", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("anyOf", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("oneOf", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("not", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("if", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("then", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("else", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("items", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("prefixItems", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("contains", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("properties", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("patternProperties", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("additionalProperties", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("propertyNames", URI_DRAFT_2020_12_VOCAB_APPLICATOR);
        registerKeyword("dependentSchemas", URI_DRAFT_2020_12_VOCAB_APPLICATOR);

        // URI_DRAFT_2020_12_VOCAB_UNEVALUATED
        registerKeyword("unevaluatedItems", URI_DRAFT_2020_12_VOCAB_UNEVALUATED);
        registerKeyword("unevaluatedProperties", URI_DRAFT_2020_12_VOCAB_UNEVALUATED);

        // URI_DRAFT_2020_12_VOCAB_FORMAT
        registerKeyword("format", URI_DRAFT_2020_12_VOCAB_FORMAT);

        // URI_DRAFT_2020_12_VOCAB_CONTENT
        registerKeyword("contentEncoding", URI_DRAFT_2020_12_VOCAB_CONTENT);
        registerKeyword("contentMediaType", URI_DRAFT_2020_12_VOCAB_CONTENT);
        registerKeyword("contentSchema", URI_DRAFT_2020_12_VOCAB_CONTENT);

        // URI_EXTENSION_VOCAB_SJF4J
        registerKeyword("id", URI_EXTENSION_VOCAB_SJF4J);
        registerKeyword("definitions", URI_EXTENSION_VOCAB_SJF4J);

    }

}

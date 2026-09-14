package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonSelectorEdgeTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"bracket_notation_with_number_after_dot_notation_with_wildcard_on_nested_arrays_with_different_length", "$.*[1]", "[[1], [2,3]]", "[3]"},
                {"bracket_notation_with_wildcard_after_dot_notation_after_bracket_notation_with_wildcard", "$[*].bar[*]", "[{\"bar\": [42]}]", "[42]"},
                {"bracket_notation_with_wildcard_on_empty_array", "$[*]", "[]", "[]"},
                {"bracket_notation_with_wildcard_on_empty_object", "$[*]", "{}", "[]"},
                {"dot_notation_after_bracket_notation_with_wildcard_on_one_matching", "$[*].a", "[{\"a\": 1}]", "[1]"},
                {"dot_notation_after_bracket_notation_with_wildcard_on_some_matching", "$[*].a", "[{\"a\": 1},{\"b\": 1}]", "[1]"},
                {"dot_notation_with_wildcard_after_dot_notation_after_dot_notation_with_wildcard", "$.*.bar.*", "[{\"bar\": [42]}]", "[42]"},
                {"dot_notation_with_wildcard_after_recursive_descent_on_scalar", "$..*", "42", "[]"},
                {"dot_notation_with_wildcard_on_empty_array", "$.*", "[]", "[]"},
                {"dot_notation_with_wildcard_on_empty_object", "$.*", "{}", "[]"}
        });
    }

    @Test
    void consensusNotSupported() {
        JsonPathComparisonSupport.unsupported(new String[][]{
                {"bracket_notation_with_two_literals_separated_by_dot", "$['two'.'some']", "{\"one\":{\"key\":\"value\"},\"two\":{\"some\":\"more\",\"key\":\"other value\"},\"two.some\":\"42\",\"two'.'some\":\"43\"}"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"bracket_notation_with_quoted_special_characters_combined", "$[':@.\"$,*\\'\\\\']", "{\":@.\\\"$,*'\\\\\": 42}"},
                {"dot_bracket_notation_with_double_quotes", "$.[\"key\"]", "{\"key\":\"value\",\"other\":{\"key\":[{\"key\":42}]}}"},
                {"dot_notation_with_double_quotes", "$.\"key\"", "{\"key\":\"value\",\"\\\"key\\\"\":42}"},
                {"dot_notation_with_double_quotes_after_recursive_descent", "$..\"key\"", "{\"object\":{\"key\":\"value\",\"\\\"key\\\"\":100,\"array\":[{\"key\":\"something\",\"\\\"key\\\"\":0},{\"key\":{\"key\":\"russian dolls\"},\"\\\"key\\\"\":{\"\\\"key\\\"\":99}}]},\"key\":\"top\",\"\\\"key\\\"\":42}"},
                {"dot_notation_with_key_root_literal", "$.$", "{\"$\":\"value\"}"},
                {"dot_notation_with_number_-1", "$.-1", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"dot_notation_with_single_quotes", "$.'key'", "{\"key\":\"value\",\"'key'\":42}"},
                {"dot_notation_with_single_quotes_after_recursive_descent", "$..'key'", "{\"object\":{\"key\":\"value\",\"'key'\":100,\"array\":[{\"key\":\"something\",\"'key'\":0},{\"key\":{\"key\":\"russian dolls\"},\"'key'\":{\"'key'\":99}}]},\"key\":\"top\",\"'key'\":42}"},
                {"dot_notation_with_single_quotes_and_dot", "$.'some.key'", "{\"some.key\":42,\"some\":{\"key\":\"value\"},\"'some.key'\":43}"},
                {"dot_notation_with_space_padded_key", "$. a ", "{\" a\":1,\"a\":2,\" a \":3,\"\":4}"},
                {"dot_notation_without_root", ".key", "{\"key\":\"value\"}"},
                {"dot_notation_without_root_and_dot", "key", "{\"key\":\"value\"}"},
                {"empty", "", "{\"a\":42,\"\":21}"},
                {"filter_expression_after_dot_notation_with_wildcard_after_recursive_descent", "$..*[?(@.id>2)]", "[{\"complext\":{\"one\":[{\"name\":\"first\",\"id\":1},{\"name\":\"next\",\"id\":2},{\"name\":\"another\",\"id\":3},{\"name\":\"more\",\"id\":4}],\"more\":{\"name\":\"next to last\",\"id\":5}}},{\"name\":\"last\",\"id\":6}]"},
                {"filter_expression_after_recursive_descent", "$..[?(@.id==2)]", "{\"id\":2,\"more\":[{\"id\":2},{\"more\":{\"id\":2}},{\"id\":{\"id\":2}},[{\"id\":2}]]}"},
                {"filter_expression_on_object", "$[?(@.key)]", "{\"key\":42,\"another\":{\"key\":1}}"}
        });
    }
}

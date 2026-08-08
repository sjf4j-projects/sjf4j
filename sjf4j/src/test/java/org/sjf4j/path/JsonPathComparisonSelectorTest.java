package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonSelectorTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"bracket_notation_with_wildcard_on_null_value_array", "$[*]", "[40,null,42]", "[40,null,42]"},
                {"dot_notation_after_array_slice", "$[0:2].key", "[{\"key\":\"ey\"},{\"key\":\"bee\"},{\"key\":\"see\"}]", "[\"ey\",\"bee\"]"},
                {"dot_notation_after_bracket_notation_with_wildcard", "$[*].a", "[{\"a\":1},{\"a\":1}]", "[1,1]"},
                {"dot_notation_with_wildcard_on_array", "$.*", "[\"string\",42,{\"key\":\"value\"},[0,1]]", "[\"string\",42,{\"key\":\"value\"},[0,1]]"},
                {"dot_notation_with_wildcard_after_dot_notation_with_wildcard_on_nested_arrays", "$.*.*", "[[1,2,3],[4,5,6]]", "[1,2,3,4,5,6]"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"bracket_notation_with_empty_path", "$[]", "{\"\":42,\"''\":123,\"\\\"\\\"\":222}"},
                {"current_with_dot_notation", "@.a", "{\"a\":1}"},
                {"dot_bracket_notation", "$.['key']", "{\"key\":\"value\",\"other\":{\"key\":[{\"key\":42}]}}"},
                {"recursive_descent", "$..", "[{\"a\":{\"b\":\"c\"}},[0,1]]"},
                {"recursive_descent_after_dot_notation", "$.key..", "{\"some key\":\"value\",\"key\":{\"complex\":\"string\",\"primitives\":[0,1]}}"},
                {"recursive_descent_on_nested_arrays", "$..*", "[[0],[1]]"}
        });
    }
}

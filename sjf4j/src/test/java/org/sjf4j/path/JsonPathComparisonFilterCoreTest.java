package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonFilterCoreTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"filter_expression_with_bracket_notation", "$[?(@['key']==42)]", "[{\"key\":0},{\"key\":42},{\"key\":-1},{\"key\":41},{\"key\":43},{\"key\":42.0001},{\"key\":41.9999},{\"key\":100},{\"some\":\"value\"}]", "[{\"key\":42}]"},
                {"filter_expression_with_bracket_notation_and_current_object_literal", "$[?(@['@key']==42)]", "[{\"@key\":0},{\"@key\":42},{\"key\":42},{\"@key\":43},{\"some\":\"value\"}]", "[{\"@key\":42}]"},
                {"filter_expression_with_bracket_notation_with_number", "$[?(@[1]=='b')]", "[[\"a\",\"b\"],[\"x\",\"y\"]]", "[[\"a\",\"b\"]]"},
                {"filter_expression_with_equals", "$[?(@.key==42)]", "[{\"key\":0},{\"key\":42},{\"key\":-1},{\"key\":1},{\"key\":41},{\"key\":43},{\"key\":42.0001},{\"key\":41.9999},{\"key\":100},{\"key\":\"some\"},{\"key\":\"42\"},{\"key\":null},{\"key\":420},{\"key\":\"\"},{\"key\":{}},{\"key\":[]},{\"key\":[42]},{\"key\":{\"key\":42}},{\"key\":{\"some\":42}},{\"some\":\"value\"}]", "[{\"key\":42}]"},
                {"filter_expression_with_equals_false", "$[?(@.key==false)]", "[{\"some\":\"some value\"},{\"key\":true},{\"key\":false},{\"key\":null},{\"key\":\"value\"},{\"key\":\"\"},{\"key\":0},{\"key\":1},{\"key\":-1},{\"key\":42},{\"key\":{}},{\"key\":[]}]", "[{\"key\":false}]"},
                {"filter_expression_with_equals_on_array_of_numbers", "$[?(@==42)]", "[0,42,-1,41,43,42.0001,41.9999,null,100]", "[42]"},
                {"filter_expression_with_equals_on_array_without_match", "$[?(@.key==43)]", "[{\"key\":42}]", "[]"},
                {"filter_expression_with_equals_on_object_with_key_matching_query", "$[?(@.id==2)]", "{\"id\":2}", "[]"},
                {"filter_expression_with_equals_string", "$[?(@.key==\"value\")]", "[{\"key\":\"some\"},{\"key\":\"value\"},{\"key\":null},{\"key\":0},{\"key\":1},{\"key\":-1},{\"key\":\"\"},{\"key\":{}},{\"key\":[]},{\"key\":\"valuemore\"},{\"key\":\"morevalue\"},{\"key\":[\"value\"]},{\"key\":{\"some\":\"value\"}},{\"key\":{\"key\":\"value\"}},{\"some\":\"value\"}]", "[{\"key\":\"value\"}]"},
                {"filter_expression_with_equals_string_in_NFC", "$[?(@.key==\"Motörhead\")]", "[{\"key\":\"something\"},{\"key\":\"Mot\\u00f6rhead\"},{\"key\":\"mot\\u00f6rhead\"},{\"key\":\"Motorhead\"},{\"key\":\"Motoo\\u0308rhead\"},{\"key\":\"motoo\\u0308rhead\"}]", "[{\"key\":\"Motörhead\"}]"},
                {"filter_expression_with_equals_string_with_current_object_literal", "$[?(@.key==\"hi@example.com\")]", "[{\"key\":\"some\"},{\"key\":\"value\"},{\"key\":\"hi@example.com\"}]", "[{\"key\":\"hi@example.com\"}]"},
                {"filter_expression_with_equals_string_with_dot_literal", "$[?(@.key==\"some.value\")]", "[{\"key\":\"some\"},{\"key\":\"value\"},{\"key\":\"some.value\"}]", "[{\"key\":\"some.value\"}]"},
                {"filter_expression_with_equals_string_with_single_quotes", "$[?(@.key=='value')]", "[{\"key\":\"some\"},{\"key\":\"value\"}]", "[{\"key\":\"value\"}]"},
                {"filter_expression_with_equals_true", "$[?(@.key==true)]", "[{\"some\":\"some value\"},{\"key\":true},{\"key\":false},{\"key\":null},{\"key\":\"value\"},{\"key\":\"\"},{\"key\":0},{\"key\":1},{\"key\":-1},{\"key\":42},{\"key\":{}},{\"key\":[]}]", "[{\"key\":true}]"},
                {"filter_expression_with_equals_with_root_reference", "$.items[?(@.key==$.value)]", "{\"value\":42,\"items\":[{\"key\":10},{\"key\":42},{\"key\":50}]}", "[{\"key\":42}]"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"filter_expression_with_boolean_and_operator_and_value_false", "$[?(@.key>0 && false)]", "[{\"key\":1},{\"key\":3},{\"key\":\"nice\"},{\"key\":true},{\"key\":null},{\"key\":false},{\"key\":{}},{\"key\":[]},{\"key\":-1},{\"key\":0},{\"key\":\"\"}]"},
                {"filter_expression_with_boolean_and_operator_and_value_true", "$[?(@.key>0 && true)]", "[{\"key\":1},{\"key\":3},{\"key\":\"nice\"},{\"key\":true},{\"key\":null},{\"key\":false},{\"key\":{}},{\"key\":[]},{\"key\":-1},{\"key\":0},{\"key\":\"\"}]"},
                {"filter_expression_with_boolean_or_operator_and_value_false", "$[?(@.key>0 || false)]", "[{\"key\":1},{\"key\":3},{\"key\":\"nice\"},{\"key\":true},{\"key\":null},{\"key\":false},{\"key\":{}},{\"key\":[]},{\"key\":-1},{\"key\":0},{\"key\":\"\"}]"},
                {"filter_expression_with_boolean_or_operator_and_value_true", "$[?(@.key>0 || true)]", "[{\"key\":1},{\"key\":3},{\"key\":\"nice\"},{\"key\":true},{\"key\":null},{\"key\":false},{\"key\":{}},{\"key\":[]},{\"key\":-1},{\"key\":0},{\"key\":\"\"}]"},
                {"filter_expression_with_bracket_notation_with_number_on_object", "$[?(@[1]=='b')]", "{\"1\":[\"a\",\"b\"],\"2\":[\"x\",\"y\"]}"},
                {"filter_expression_with_current_object", "$[?(@)]", "[\"some value\",null,\"value\",0,1,-1,\"\",[],{},false,true]"},
                {"filter_expression_with_division", "$[?(@.key/10==5)]", "[{\"key\":60},{\"key\":50},{\"key\":10},{\"key\":-50},{\"key/10\":5}]"},
                {"filter_expression_with_dot_notation_with_dash", "$[?(@.key-dash == 'value')]", "[{\"key-dash\":\"value\"}]"},
                {"filter_expression_with_dot_notation_with_number", "$[?(@.2 == 'second')]", "[{\"a\":\"first\",\"2\":\"second\",\"b\":\"third\"}]"},
                {"filter_expression_with_dot_notation_with_number_on_array", "$[?(@.2 == 'third')]", "[[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]]"},
                {"filter_expression_with_equals_array", "$[?(@.d==[\"v1\",\"v2\"])]", "[{\"d\":[\"v1\",\"v2\"]},{\"d\":[\"a\",\"b\"]},{\"d\":\"v1\"},{\"d\":\"v2\"},{\"d\":{}},{\"d\":[]},{\"d\":null},{\"d\":-1},{\"d\":0},{\"d\":1},{\"d\":\"['v1','v2']\"},{\"d\":\"['v1', 'v2']\"},{\"d\":\"v1,v2\"},{\"d\":\"[\\\"v1\\\", \\\"v2\\\"]\"},{\"d\":\"[\\\"v1\\\",\\\"v2\\\"]\"}]"},
                {"filter_expression_with_equals_array_for_array_slice_with_range_1", "$[?(@[0:1]==[1])]", "[[1,2,3],[1],[2,3],1,2]"},
                {"filter_expression_with_equals_array_for_dot_notation_with_star", "$[?(@.*==[1,2])]", "[[1,2],[2,3],[1],[2],[1,2,3],1,2,3]"},
                {"filter_expression_with_equals_array_or_equals_true", "$[?(@.d==[\"v1\",\"v2\"] || (@.d == true))]", "[{\"d\":[\"v1\",\"v2\"]},{\"d\":[\"a\",\"b\"]},{\"d\":true}]"},
                {"filter_expression_with_equals_array_with_single_quotes", "$[?(@.d==['v1','v2'])]", "[{\"d\":[\"v1\",\"v2\"]},{\"d\":[\"a\",\"b\"]},{\"d\":\"v1\"},{\"d\":\"v2\"},{\"d\":{}},{\"d\":[]},{\"d\":null},{\"d\":-1},{\"d\":0},{\"d\":1},{\"d\":\"['v1','v2']\"},{\"d\":\"['v1', 'v2']\"},{\"d\":\"v1,v2\"},{\"d\":\"[\\\"v1\\\", \\\"v2\\\"]\"},{\"d\":\"[\\\"v1\\\",\\\"v2\\\"]\"}]"},
                {"filter_expression_with_equals_boolean_expression_value", "$[?((@.key<44)==false)]", "[{\"key\":42},{\"key\":43},{\"key\":44}]"},
                {"filter_expression_with_equals_null", "$[?(@.key==null)]", "[{\"some\":\"some value\"},{\"key\":true},{\"key\":false},{\"key\":null},{\"key\":\"value\"},{\"key\":\"\"},{\"key\":0},{\"key\":1},{\"key\":-1},{\"key\":42},{\"key\":{}},{\"key\":[]}]"},
                {"filter_expression_with_equals_number_for_array_slice_with_range_1", "$[?(@[0:1]==1)]", "[[1,2,3],[1],[2,3],1,2]"},
                {"filter_expression_with_equals_number_for_bracket_notation_with_star", "$[?(@[*]==2)]", "[[1,2],[2,3],[1],[2],[1,2,3],1,2,3]"},
                {"filter_expression_with_equals_number_for_dot_notation_with_star", "$[?(@.*==2)]", "[[1,2],[2,3],[1],[2],[1,2,3],1,2,3]"},
                {"filter_expression_with_equals_number_with_fraction", "$[?(@.key==-0.123e2)]", "[{\"key\":-12.3},{\"key\":-0.123},{\"key\":-12},{\"key\":12.3},{\"key\":2},{\"key\":\"-0.123e2\"}]"},
                {"filter_expression_with_equals_number_with_leading_zeros", "$[?(@.key==010)]", "[{\"key\":\"010\"},{\"key\":\"10\"},{\"key\":10},{\"key\":0},{\"key\":8}]"},
                {"filter_expression_with_equals_object", "$[?(@.d=={\"k\":\"v\"})]", "[{\"d\":{\"k\":\"v\"}},{\"d\":{\"a\":\"b\"}},{\"d\":\"k\"},{\"d\":\"v\"},{\"d\":{}},{\"d\":[]},{\"d\":null},{\"d\":-1},{\"d\":0},{\"d\":1},{\"d\":\"[object Object]\"},{\"d\":\"{\\\"k\\\": \\\"v\\\"}\"},{\"d\":\"{\\\"k\\\":\\\"v\\\"}\"},\"v\"]"},
                {"filter_expression_with_equals_on_object", "$[?(@.key==42)]", "{\"a\":{\"key\":0},\"b\":{\"key\":42},\"c\":{\"key\":-1},\"d\":{\"key\":41},\"e\":{\"key\":43},\"f\":{\"key\":42.0001},\"g\":{\"key\":41.9999},\"h\":{\"key\":100},\"i\":{\"some\":\"value\"}}"},
                {"filter_expression_with_equals_string_with_unicode_character_escape", "$[?(@.key==\"Mot\\u00f6rhead\")]", "[{\"key\":\"something\"},{\"key\":\"Mot\\u00f6rhead\"},{\"key\":\"mot\\u00f6rhead\"},{\"key\":\"Motorhead\"},{\"key\":\"Motoo\\u0308rhead\"},{\"key\":\"motoo\\u0308rhead\"}]"},
                {"filter_expression_with_equals_with_path_and_path", "$[?(@.key1==@.key2)]", "[{\"key1\":10,\"key2\":10},{\"key1\":42,\"key2\":50},{\"key1\":10},{\"key2\":10},{},{\"key1\":null,\"key2\":null},{\"key1\":null},{\"key2\":null},{\"key1\":0,\"key2\":0},{\"key1\":0},{\"key2\":0},{\"key1\":-1,\"key2\":-1},{\"key1\":\"\",\"key2\":\"\"},{\"key1\":false,\"key2\":false},{\"key1\":false},{\"key2\":false},{\"key1\":true,\"key2\":true},{\"key1\":[],\"key2\":[]},{\"key1\":{},\"key2\":{}},{\"key1\":{\"a\":1,\"b\":2},\"key2\":{\"b\":2,\"a\":1}}]"}
        });
    }
}

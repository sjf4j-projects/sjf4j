package org.sjf4j.path;

import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPathComparisonFilterEdgeTest {
    private static final String COMPARISONS = "[{\"key\":0},{\"key\":42},{\"key\":-1},{\"key\":41},{\"key\":43},{\"key\":42.0001},{\"key\":41.9999},{\"key\":100},{\"key\":\"43\"},{\"key\":\"42\"},{\"key\":\"41\"},{\"key\":\"value\"},{\"some\":\"value\"}]";
    private static final String VALUES = "[1,3,\"nice\",true,null,false,{},[],-1,0,\"\"]";
    private static final String LENGTHS = "[[1,2,3,4,5],[1,2,3,4],[1,2,3]]";
    private static final String PRESENCE = "[{\"some\":\"some value\"},{\"key\":true},{\"key\":false},{\"key\":null},{\"key\":\"value\"},{\"key\":\"\"},{\"key\":0},{\"key\":1},{\"key\":-1},{\"key\":42},{\"key\":{}},{\"key\":[]}]";
    private static final String EQUALITY = "[{\"key\":0},{\"key\":42},{\"key\":-1},{\"key\":1},{\"key\":41},{\"key\":43},{\"key\":42.0001},{\"key\":41.9999},{\"key\":100},{\"key\":\"some\"},{\"key\":\"42\"},{\"key\":null},{\"key\":420},{\"key\":\"\"},{\"key\":{}},{\"key\":[]},{\"key\":[42]},{\"key\":{\"key\":42}},{\"key\":{\"some\":42}},{\"some\":\"value\"}]";

    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"filter_expression_with_length_property", "$[?(@.length == 4)]", LENGTHS, "[]"},
                {"filter_expression_with_subpaths_deeply_nested", "$[?(@.a.b.c==3)]", "[{\"a\":{\"b\":{\"c\":3}}},{\"a\":3},{\"c\":3},{\"a\":{\"b\":{\"c\":2}}}]", "[{\"a\":{\"b\":{\"c\":3}}}]"},
                {"filter_expression_with_value_after_dot_notation_with_wildcard_on_array_of_objects", "$.*[?(@.key)]", "[{\"some\":\"some value\"},{\"key\":\"value\"}]", "[]"}
        });
        assertEquals(Sjf4j.global().fromJson("[[1,2,3,4]]"),
                JsonPath.parse("$[?(@.length() == 4)]").eval(Sjf4j.global().fromJson(LENGTHS)),
                "filter_expression_with_length_function");
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"filter_expression_with_greater_than", "$[?(@.key>42)]", COMPARISONS},
                {"filter_expression_with_greater_than_or_equal", "$[?(@.key>=42)]", COMPARISONS},
                {"filter_expression_with_in_array_of_values", "$[?(@.d in [2, 3])]", "[{\"d\":1},{\"d\":2},{\"d\":1},{\"d\":3},{\"d\":4}]"},
                {"filter_expression_with_in_current_object", "$[?(2 in @.d)]", "[{\"d\":[1,2,3]},{\"d\":[2]},{\"d\":[1]},{\"d\":[3,4]},{\"d\":[4,2]}]"},
                {"filter_expression_with_length_free_function", "$[?(length(@) == 4)]", LENGTHS},
                {"filter_expression_with_less_than_or_equal", "$[?(@.key<=42)]", COMPARISONS},
                {"filter_expression_with_multiplication", "$[?(@.key*2==100)]", "[{\"key\":60},{\"key\":50},{\"key\":10},{\"key\":-50},{\"key*2\":100}]"},
                {"filter_expression_with_negation_and_equals", "$[?(!(@.key==42))]", COMPARISONS},
                {"filter_expression_with_negation_and_equals_array_or_equals_true", "$[?(!(@.d==[\"v1\",\"v2\"]) || (@.d == true))]", "[{\"d\":[\"v1\",\"v2\"]},{\"d\":[\"a\",\"b\"]},{\"d\":true}]"},
                {"filter_expression_with_negation_and_less_than", "$[?(!(@.key<42))]", COMPARISONS},
                {"filter_expression_with_negation_and_without_value", "$[?(!@.key)]", PRESENCE},
                {"filter_expression_with_non_singular_existence_test", "$[?(@.a.*)]", "[{\"a\":0},{\"a\":\"x\"},{\"a\":false},{\"a\":true},{\"a\":null},{\"a\":[]},{\"a\":[1]},{\"a\":[1,2]},{\"a\":{}},{\"a\":{\"x\":\"y\"}},{\"a\":{\"x\":\"y\",\"w\":\"z\"}}]"},
                {"filter_expression_with_not_equals", "$[?(@.key!=42)]", EQUALITY},
                {"filter_expression_with_not_equals_array_or_equals_true", "$[?((@.d!=[\"v1\",\"v2\"]) || (@.d == true))]", "[{\"d\":[\"v1\",\"v2\"]},{\"d\":[\"a\",\"b\"]},{\"d\":true}]"},
                {"filter_expression_with_regular_expression_from_member", "$[?(@.name=~/@.pattern/)]", "[{\"name\":\"hullo world\"},{\"name\":\"hello world\"},{\"name\":\"yes hello world\"},{\"name\":\"HELLO WORLD\"},{\"name\":\"good bye\"},{\"pattern\":\"hello.*\"}]"},
                {"filter_expression_with_set_wise_comparison_to_scalar", "$[?(@[*]>=4)]", "[[1,2],[3,4],[5,6]]"},
                {"filter_expression_with_set_wise_comparison_to_set", "$.x[?(@[*]>=$.y[*])]", "{\"x\":[[1,2],[3,4],[5,6]],\"y\":[3,4,5]}"},
                {"filter_expression_with_subfilter", "$[?(@.a[?(@.price>10)])]", "[{\"a\":[{\"price\":1},{\"price\":3}]},{\"a\":[{\"price\":11}]},{\"a\":[{\"price\":8},{\"price\":12},{\"price\":3}]},{\"a\":[]}]"},
                {"filter_expression_with_subtraction", "$[?(@.key-50==-100)]", "[{\"key\":60},{\"key\":50},{\"key\":10},{\"key\":-50},{\"key-50\":-100}]"},
                {"filter_expression_with_triple_equal", "$[?(@.key===42)]", EQUALITY},
                {"filter_expression_with_value_after_recursive_descent", "$..[?(@.id)]", "{\"id\":2,\"more\":[{\"id\":2},{\"more\":{\"id\":2}},{\"id\":{\"id\":2}},[{\"id\":2}]]}"},
                {"filter_expression_with_value_false", "$[?(false)]", VALUES},
                {"filter_expression_with_value_from_recursive_descent", "$[?(@..child)]", "[{\"key\":[{\"child\":1},{\"child\":2}]},{\"key\":[{\"child\":2}]},{\"key\":[{}]},{\"key\":[{\"something\":42}]},{}]"},
                {"filter_expression_with_value_null", "$[?(null)]", VALUES},
                {"filter_expression_with_value_true", "$[?(true)]", VALUES},
                {"filter_expression_without_parens", "$[?@.key==42]", EQUALITY},
                {"filter_expression_without_value", "$[?(@.key)]", PRESENCE},
                {"union_with_slice_and_number", "$[1:3,4]", "[1,2,3,4,5]"},
                {"union_with_wildcard_and_number", "$[*,1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"}
        });
    }
}

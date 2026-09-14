package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonUnionTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"union", "$[0,1]", "[\"first\",\"second\",\"third\"]", "[\"first\",\"second\"]"},
                {"union_with_duplication_from_array", "$[0,0]", "[\"a\"]", "[\"a\",\"a\"]"},
                {"union_with_duplication_from_object", "$['a','a']", "{\"a\":1}", "[1,1]"},
                {"union_with_keys", "$['key','another']", "{\"key\":\"value\",\"another\":\"entry\"}", "[\"value\",\"entry\"]"},
                {"union_with_keys_after_array_slice", "$[:]['c','d']", "[{\"c\":\"cc1\",\"d\":\"dd1\",\"e\":\"ee1\"},{\"c\":\"cc2\",\"d\":\"dd2\",\"e\":\"ee2\"}]", "[\"cc1\",\"dd1\",\"cc2\",\"dd2\"]"},
                {"union_with_keys_after_bracket_notation", "$[0]['c','d']", "[{\"c\":\"cc1\",\"d\":\"dd1\",\"e\":\"ee1\"},{\"c\":\"cc2\",\"d\":\"dd2\",\"e\":\"ee2\"}]", "[\"cc1\",\"dd1\"]"},
                {"union_with_keys_on_object_without_key", "$['missing','key']", "{\"key\":\"value\",\"another\":\"entry\"}", "[\"value\"]"},
                {"union_with_numbers_in_decreasing_order", "$[4,1]", "[1,2,3,4,5]", "[5,2]"},
                {"union_with_negative_index_and_slice", "$[-1,1:4:2,-1]", "[0,1,2,3,4]", "[4,1,3,4]"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"union_with_filter", "$[?(@.key<3),?(@.key>6)]", "[{\"key\":1},{\"key\":8},{\"key\":3},{\"key\":10},{\"key\":7},{\"key\":2},{\"key\":6},{\"key\":4}]"},
                {"union_with_repeated_matches_after_dot_notation_with_wildcard", "$.*[0,:5]", "{\"a\":[\"string\",null,true],\"b\":[false,\"string\",5.4]}"}
        });
    }
}

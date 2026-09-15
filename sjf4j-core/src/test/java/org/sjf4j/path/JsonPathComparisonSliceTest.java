package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonSliceTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"array_slice", "$[1:3]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"second\",\"third\"]"},
                {"array_slice_on_exact_match", "$[0:5]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_on_non_overlapping_array", "$[7:10]", "[\"first\",\"second\",\"third\"]", "[]"},
                {"array_slice_on_object", "$[1:3]", "{\":\":42,\"more\":\"string\",\"a\":1,\"b\":2,\"c\":3,\"1:3\":\"nice\"}", "[]"},
                {"array_slice_on_partially_overlapping_array", "$[1:10]", "[\"first\",\"second\",\"third\"]", "[\"second\",\"third\"]"},
                {"array_slice_with_large_number_for_end", "$[2:113667776004]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_large_number_for_start", "$[-113667776004:2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"second\"]"},
                {"array_slice_with_i_json_maximum_bounds", "$[-9007199254740991:9007199254740991]", "[0,1,2]", "[0,1,2]"},
                {"array_slice_with_negative_start_and_end_and_range_of_-1", "$[-4:-5]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_negative_start_and_end_and_range_of_0", "$[-4:-4]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_negative_start_and_end_and_range_of_1", "$[-4:-3]", "[2,\"a\",4,5,100,\"nice\"]", "[4]"},
                {"array_slice_with_negative_start_and_positive_end_and_range_of_-1", "$[-4:1]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_negative_start_and_positive_end_and_range_of_0", "$[-4:2]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_negative_start_and_positive_end_and_range_of_1", "$[-4:3]", "[2,\"a\",4,5,100,\"nice\"]", "[4]"},
                {"array_slice_with_negative_step", "$[3:0:-2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"forth\",\"second\"]"},
                {"array_slice_with_negative_step_only", "$[::-2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"fifth\",\"third\",\"first\"]"},
                {"array_slice_with_open_end_and_negative_step", "$[3::-1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"forth\",\"third\",\"second\",\"first\"]"},
                {"array_slice_with_open_start_and_negative_step", "$[:2:-1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"fifth\",\"forth\"]"},
                {"array_slice_with_negative_step_and_start_greater_than_end", "$[0:3:-2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[]"},
                {"array_slice_with_open_end", "$[1:]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_open_start", "$[:2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"second\"]"},
                {"array_slice_with_open_start_and_end", "$[:]", "[\"first\",\"second\"]", "[\"first\",\"second\"]"},
                {"array_slice_with_open_start_and_end_and_step_empty", "$[::]", "[\"first\",\"second\"]", "[\"first\",\"second\"]"},
                {"array_slice_with_open_start_and_end_on_object", "$[:]", "{\":\":42,\"more\":\"string\"}", "[]"},
                {"array_slice_with_positive_start_and_negative_end_and_range_of_-1", "$[3:-4]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_positive_start_and_negative_end_and_range_of_0", "$[3:-3]", "[2,\"a\",4,5,100,\"nice\"]", "[]"},
                {"array_slice_with_positive_start_and_negative_end_and_range_of_1", "$[3:-2]", "[2,\"a\",4,5,100,\"nice\"]", "[5]"},
                {"array_slice_with_range_of_-1", "$[2:1]", "[\"first\",\"second\",\"third\",\"forth\"]", "[]"},
                {"array_slice_with_range_of_0", "$[0:0]", "[\"first\",\"second\"]", "[]"},
                {"array_slice_with_range_of_1", "$[0:1]", "[\"first\",\"second\"]", "[\"first\"]"},
                {"array_slice_with_start_-1_and_open_end", "$[-1:]", "[\"first\",\"second\",\"third\"]", "[\"third\"]"},
                {"array_slice_with_start_-2_and_open_end", "$[-2:]", "[\"first\",\"second\",\"third\"]", "[\"second\",\"third\"]"},
                {"array_slice_with_start_large_negative_number_and_open_end_on_short_array", "$[-4:]", "[\"first\",\"second\",\"third\"]", "[\"first\",\"second\",\"third\"]"},
                {"array_slice_with_step", "$[0:3:2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"third\"]"},
                {"array_slice_with_step_1", "$[0:3:1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"second\",\"third\"]"},
                {"array_slice_with_step_but_end_not_aligned", "$[0:4:2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"third\"]"},
                {"array_slice_with_step_empty", "$[1:3:]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"second\",\"third\"]"},
                {"array_slice_with_step_only", "$[::2]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]", "[\"first\",\"third\",\"fifth\"]"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"array_slice_with_large_number_for_end_and_negative_step", "$[2:-113667776004:-1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_large_number_for_start_end_negative_step", "$[113667776004:2:-1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_negative_step_on_partially_overlapping_array", "$[7:3:-1]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_step_0", "$[0:3:0]", "[\"first\",\"second\",\"third\",\"forth\",\"fifth\"]"},
                {"array_slice_with_step_and_leading_zeros", "$[010:024:010]", "[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25]"}
        });
    }
}

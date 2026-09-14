package org.sjf4j.path;

import org.junit.jupiter.api.Test;

class JsonPathComparisonFilterTest {
    @Test
    void consensusDiffs() {
        JsonPathComparisonSupport.consensus(new String[][]{
                {"filter_expression_with_boolean_and_operator", "$[?(@.key>42 && @.key<44)]", "[{\"key\":42},{\"key\":43},{\"key\":44}]", "[{\"key\":43}]"},
                {"filter_expression_with_boolean_or_operator", "$[?(@.key>43 || @.key<43)]", "[{\"key\":42},{\"key\":43},{\"key\":44}]", "[{\"key\":42},{\"key\":44}]"},
                {"filter_expression_with_subpaths", "$[?(@.a.b==3)]", "[{\"a\":{\"b\":3}},{\"a\":{\"b\":2}}]", "[{\"a\":{\"b\":3}}]"},
                {"filter_expression_with_tautological_comparison", "$[?(1==1)]", "[1,3,\"nice\",true,null,false,{},[],-1,0,\"\"]", "[1,3,\"nice\",true,null,false,{},[],-1,0,\"\"]"}
        });
    }

    @Test
    void noConsensus() {
        JsonPathComparisonSupport.noConsensus(new String[][]{
                {"filter_expression_with_addition", "$[?(@.key+50==100)]", "[{\"key\":60},{\"key\":50},{\"key\":10},{\"key\":-50},{\"key+50\":100}]"},
                {"filter_expression_with_regular_expression", "$[?(@.name=~/hello.*/)]", "[{\"name\":\"hullo world\"},{\"name\":\"hello world\"},{\"name\":\"yes hello world\"},{\"name\":\"HELLO WORLD\"},{\"name\":\"good bye\"}]"}
        });
    }
}

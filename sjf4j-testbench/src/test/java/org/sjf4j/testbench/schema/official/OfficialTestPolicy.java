package org.sjf4j.testbench.schema.official;

import org.sjf4j.JsonArray;

/** Official requirement versus functionality SJF4J commits to supporting. */
final class OfficialTestPolicy {
    static final String FORMAT_VOCABULARY = "optional/format-assertion.json";

    private OfficialTestPolicy() {}

    static boolean formatAssertion(JsonArray tags) {
        if (tags == null) return false;
        boolean assertion = false;
        for (int i = 0; i < tags.size(); i++) {
            Object tag = tags.get(i);
            if ("format-assertion".equals(tag)) assertion = true;
            else if (!"format-annotation".equals(tag)) {
                throw new IllegalArgumentException("Unknown official validation tag: " + tag);
            }
        }
        return assertion;
    }

    static boolean supportedOptional(String file, boolean assertion) {
        return FORMAT_VOCABULARY.equals(file) || (!file.contains("/") && assertion);
    }
}

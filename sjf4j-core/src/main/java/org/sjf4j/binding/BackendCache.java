package org.sjf4j.binding;

public final class BackendCache {

    /** Matches input names to writable POJO properties, or to a OneOf discriminator. */
    public volatile NameMatcher nameMatcher;

    /** Matches input names to creator arguments and parent-scope OneOf properties. */
    public volatile NameMatcher creatorNameMatcher;

    /** Prepared output names for readable POJO properties, in metadata order. */
    public volatile CompiledName[] compiledNames;

}

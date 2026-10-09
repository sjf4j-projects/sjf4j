package org.sjf4j.binding;

public final class BackendCache {

    public volatile NameMatcher nameMatcher;

    public volatile NameMatcher creatorNameMatcher;

    public volatile CompiledName[] compiledNames;

}

package org.sjf4j.processor.generate;

/**
 * Small callback used to emit one generated source member into a class body.
 */
public interface GeneratedMember {
    /**
     * Emits this member into the current generated class body.
     */
    void emit(SourceWriter out);
}

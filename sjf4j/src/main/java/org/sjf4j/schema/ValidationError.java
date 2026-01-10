package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;

public class ValidationError {
    private final String path;
    private final String keyword;
    private final String message;
    private final Object node;

    public ValidationError(String path, String keyword, String message, Object node) {
        this.path = path;
        this.keyword = keyword;
        this.message = message;
        this.node = node;
    }

    public String getPath() {
        return path;
    }
    public String getKeyword() {
        return keyword;
    }
    public String getMessage() {
        return message;
    }
    public Object getNode() {
        return node;
    }

    @Override
    public String toString() {
        return "ValidationError at " + path + " [" + keyword + "]: " + message +
                " (found: " + node + ")";
    }

}

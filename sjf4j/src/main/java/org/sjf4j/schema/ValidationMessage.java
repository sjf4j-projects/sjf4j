package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;

public class ValidationMessage {

    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final JsonPointer path;
    private final String keyword;
    private final String message;

    public ValidationMessage(Severity severity, JsonPointer path, String keyword, String message) {
        this.severity = severity;
        this.path = path;
        this.keyword = keyword;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }
    public JsonPointer getPath() {
        return path;
    }
    public String getKeyword() {
        return keyword;
    }
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "[" + severity + "] Keyword '" + keyword + "' failed at path '" + path + "': " + message;
    }

}

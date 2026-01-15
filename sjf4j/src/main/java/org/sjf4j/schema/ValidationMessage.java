package org.sjf4j.schema;

public class ValidationMessage {

    public enum Severity { ERROR, WARN, INFO, DEBUG }

    private final Severity severity;
    private final String path;
    private final String keyword;
    private final String message;

    public ValidationMessage(Severity severity, String path, String keyword, String message) {
        this.severity = severity;
        this.path = path;
        this.keyword = keyword;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
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

    @Override
    public String toString() {
        return "ValidationMessage [" + severity + "] at " + path + " : (" + keyword + ") " + message;
    }

}

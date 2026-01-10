package org.sjf4j.schema;

import org.sjf4j.path.JsonPointer;
import org.sjf4j.path.PathToken;

import java.util.ArrayList;
import java.util.List;

public class ValidationContext {

    private final boolean failFast;
    private final JsonPointer path;
    private final List<ValidationError> errors;

    public ValidationContext() {
        this(false, new JsonPointer(), new ArrayList<>());
    }
    public ValidationContext(boolean failFast) {
        this(failFast, new JsonPointer(), new ArrayList<>());
    }
    private ValidationContext(boolean failFast, JsonPointer path, List<ValidationError> errors) {
        this.failFast = failFast;
        this.path = path;
        this.errors = errors;
    }

    // Probe
    private boolean probeMode;
    private boolean hasError;
    private static ValidationContext PROBE;
    public static ValidationContext getProbe() {
        if (PROBE == null) {
            PROBE = new ValidationContext(true, null, null);
            PROBE.probeMode = true;
        }
        PROBE.hasError = false;
        return PROBE;
    }

    public void pushPathToken(PathToken pathToken) {
        if (!probeMode) {
            path.push(pathToken);
        }
    }
    public void popPathToken() {
        if (!probeMode) {
            path.pop();
        }
    }

    public List<ValidationError> getErrors() { return errors; }

    public void addError(String keyword, String message, Object node) {
        if (probeMode) {
            hasError = true;
        } else {
            ValidationError error = new ValidationError(path.toString(), keyword, message, node);
            errors.add(error);
        }
    }

    public boolean shouldAbort() {
        if (probeMode) {
            return hasError;
        } else {
            return failFast && !errors.isEmpty();
        }
    }

}

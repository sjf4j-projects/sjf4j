package org.sjf4j.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ValidationContext {

    private boolean isValid;
    private final boolean probe;
    private final boolean failFast;
    private final List<ValidationMessage> messages;

    public ValidationContext() {
        this(false);
    }
    public ValidationContext(boolean failFast) {
        this(false, failFast, new ArrayList<>());
    }
    private ValidationContext(boolean probe, boolean failFast, List<ValidationMessage> messages) {
        this.probe = probe;
        this.failFast = failFast;
        this.messages = messages;
    }

    public boolean isProbe() {
        return probe;
    }
    public boolean isFailFast() {
        return failFast;
    }
    public boolean isValid() {
        return isValid;
    }
    public boolean shouldAbort() {
        return failFast && !isValid;
    }

    public List<ValidationMessage> getMessages() { return messages; }

    public void addError(String path, String keyword, String message) {
        isValid = false;
        if (messages != null) {
            ValidationMessage error = new ValidationMessage(ValidationMessage.Severity.ERROR,
                    path, keyword, message);
            messages.add(error);
        }
    }

    /// Probe
    public ValidationContext createProbe() {
        return new ValidationContext(true, true, null);
    }

}

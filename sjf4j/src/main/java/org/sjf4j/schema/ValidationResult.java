package org.sjf4j.schema;

import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationResult {
    private final boolean valid;
    private final List<ValidationMessage> messages;

    public ValidationResult(boolean valid, List<ValidationMessage> messages) {
        this.valid = valid;
        this.messages = messages == null ? Collections.emptyList() : messages;
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationMessage> getMessages() {
        return messages;
    }

    public List<ValidationMessage> getErrors() {
        return messages.stream()
                .filter(m -> m.getSeverity() == ValidationMessage.Severity.ERROR)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return Nodes.inspect(messages);
    }
}

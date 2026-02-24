package org.sjf4j.schema;

import org.sjf4j.node.Nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ValidationResult {
    private final boolean valid;
    private final List<ValidationMessage> messages;
    private final ValidationMessage lastMessage;

    public ValidationResult(boolean valid, List<ValidationMessage> messages, ValidationMessage lastMessage) {
        this.valid = valid;
        this.messages = messages;
        this.lastMessage = lastMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationMessage> getMessages() {
        if (messages == null) {
            return lastMessage == null ? Collections.emptyList() : Collections.singletonList(lastMessage);
        }
        return messages;
    }

    public List<ValidationMessage> getErrors() {
        return getMessages().stream()
                .filter(m -> m.getSeverity() == ValidationMessage.Severity.ERROR)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return Nodes.inspect(getMessages());
    }

    public ValidationMessage getLastMessage() {
        if (lastMessage == null) {
            if (messages == null || messages.isEmpty()) {
                return null;
            } else {
                return messages.get(messages.size() - 1);
            }
        }
        return lastMessage;
    }

    public static final ValidationResult VALID = new ValidationResult(true, null, null);

}

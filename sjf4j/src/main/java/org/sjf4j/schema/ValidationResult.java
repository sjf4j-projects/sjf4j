package org.sjf4j.schema;

import org.sjf4j.node.Nodes;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of schema validation.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<ValidationMessage> messages;
    private final ValidationMessage lastMessage;

    public ValidationResult(boolean valid, List<ValidationMessage> messages, ValidationMessage lastMessage) {
        this.valid = valid;
        this.messages = messages;
        this.lastMessage = lastMessage;
    }

    /**
     * Returns whether validation passed.
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the number of messages in this result.
     */
    public int count() {
        if (messages != null) return messages.size();
        if (lastMessage != null) return 1;
        return 0;
    }

    /**
     * Returns all validation messages.
     */
    public List<ValidationMessage> getMessages() {
        if (messages == null) {
            return lastMessage == null ? Collections.emptyList() : Collections.singletonList(lastMessage);
        }
        return messages;
    }

    /**
     * Returns only error messages.
     */
    public List<ValidationMessage> getErrors() {
        return getMessages().stream()
                .filter(m -> m.getSeverity() == ValidationMessage.Severity.ERROR)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return Nodes.inspect(getMessages());
    }

    /**
     * Returns the latest message if present.
     */
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

    /**
     * Shared successful validation result.
     */
    public static final ValidationResult VALID = new ValidationResult(true, null, null);

}

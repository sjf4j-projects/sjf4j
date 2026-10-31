package org.sjf4j.schema;

import org.sjf4j.Nodes;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of schema validation.
 * <p>
 * Exposes validation errors and JSON Schema annotations independently.
 * Each output entry carries instance and keyword locations. Aggregated lists
 * are held by reference and may be modified by context reuse or result merging.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<ValidationMessage> messages;
    private final ValidationMessage lastMessage;
    private final List<ValidationAnnotation> annotations;

    /**
     * Shared successful validation result.
     */
    public static final ValidationResult SUCCESS = new ValidationResult(true, null, null);

    /**
     * Creates validation result with messages.
     */
    public ValidationResult(boolean valid, List<ValidationMessage> messages, ValidationMessage lastMessage) {
        this(valid, messages, lastMessage, null);
    }

    /**
     * Creates validation output with separate error messages and annotations.
     * Lists are retained by reference; a reusable context may continue changing them.
     */
    public ValidationResult(boolean valid, List<ValidationMessage> messages,
                            ValidationMessage lastMessage, List<ValidationAnnotation> annotations) {
        this.valid = valid;
        this.messages = messages;
        this.lastMessage = lastMessage;
        this.annotations = annotations;
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
     * <p>
     * Returned list can be synthesized from fail-fast last message. Consumers
     * should prefer message-level instance/keyword path accessors over parsing
     * the exception text. In aggregation mode the internal list is returned
     * directly; modifying it changes this result.
     */
    public List<ValidationMessage> getMessages() {
        if (messages == null) {
            return lastMessage == null ? Collections.emptyList() : Collections.singletonList(lastMessage);
        }
        return messages;
    }

    /**
     * Returns JSON Schema annotations with their keyword values and locations.
     * Annotations are distinct from error/warning messages. A result obtained
     * from a reusable context shares its annotation list until the caller
     * chooses to clear it.
     */
    public List<ValidationAnnotation> getAnnotations() {
        return annotations == null ? Collections.emptyList() : annotations;
    }

    /**
     * Returns a new list containing only ERROR-severity messages.
     */
    public List<ValidationMessage> getErrors() {
        return getMessages().stream()
                .filter(m -> m.getSeverity() == ValidationMessage.Severity.ERROR)
                .collect(Collectors.toList());
    }

    /**
     * Returns the latest message if present.
     * <p>
     * In non fail-fast mode this is the tail of collected messages.
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
     * Prepends messages from an earlier validation result.
     * <p>
     * This is used by {@link SchemaValidator} when superclass plans are applied
     * before subclass plans so message order matches validation order. In
     * fail-fast mode there is no aggregate list to prepend to.
     */
    public ValidationResult mergePrevious(ValidationResult previous) {
        if (messages != null && previous != null) {
            messages.addAll(0, previous.getMessages());
        }
        return this;
    }

    /**
     * Returns messages as debug string.
     */
    @Override
    public String toString() {
        return Nodes.inspect(getMessages());
    }

}

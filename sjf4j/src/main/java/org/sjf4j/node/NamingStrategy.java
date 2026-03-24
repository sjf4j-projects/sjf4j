package org.sjf4j.node;


/**
 * Built-in naming strategies for JSON-facing property names.
 */
public enum NamingStrategy {
    IDENTITY,
    SNAKE_CASE;

    public String translate(String propertyName) {
        if (this == IDENTITY || propertyName == null || propertyName.isEmpty()) return propertyName;

        int prefix = 0;
        while (prefix < propertyName.length() && propertyName.charAt(prefix) == '_') {
            prefix++;
        }
        if (prefix == propertyName.length()) return propertyName;

        boolean changed = false;
        for (int i = prefix; i < propertyName.length(); i++) {
            if (Character.isUpperCase(propertyName.charAt(i))) {
                changed = true;
                break;
            }
        }
        if (!changed) return propertyName;

        StringBuilder sb = new StringBuilder(propertyName.length() + 8);
        for (int i = 0; i < prefix; i++) {
            sb.append('_');
        }

        for (int i = prefix; i < propertyName.length(); i++) {
            char ch = propertyName.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > prefix) {
                    char prev = propertyName.charAt(i - 1);
                    boolean prevLowerOrDigit = Character.isLowerCase(prev) || Character.isDigit(prev);
                    boolean prevUpper = Character.isUpperCase(prev);
                    boolean nextLower = i + 1 < propertyName.length()
                            && Character.isLowerCase(propertyName.charAt(i + 1));
                    if (prevLowerOrDigit || (prevUpper && nextLower)) {
                        sb.append('_');
                    }
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}

/**
 * Service-discovered optional adapters for backend-native JSON trees.
 *
 * <p>Adapters classify an external tree without copying it into SJF4J
 * containers and can provide traversal or mutation according to their own
 * implementation. Providers must remain loadable when their optional backend
 * is absent and return {@code null} in that case.</p>
 */
package org.sjf4j.external;

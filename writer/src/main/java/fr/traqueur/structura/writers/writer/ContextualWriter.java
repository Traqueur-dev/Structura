package fr.traqueur.structura.writers.writer;

/**
 * A custom writer that knows the type declared for the value, its path, and can hand the values it
 * holds back to Structura — the richer counterpart of {@link Writer}, and the symmetric of
 * {@code ContextualReader}.
 *
 * <pre>
 * // Resolvable&lt;Team&gt;: the placeholder as is, else the value written with Structura's rules
 * registry.registerContextual(Resolvable.class, (resolvable, ctx) -&gt; resolvable.isPlaceholder()
 *         ? resolvable.placeholder()
 *         : ctx.write(resolvable.value(), ctx.typeArgument(0)));
 * </pre>
 *
 * <p>An exception thrown by the writer is reported with the path of the value.</p>
 *
 * @param <T> the type the writer serializes
 */
@FunctionalInterface
public interface ContextualWriter<T> {

    /**
     * Converts a value to its YAML form: a String, a Number, a Boolean, a Map or a List.
     *
     * @param value the value
     * @param context the declared type, the path, and Structura's serialization
     * @return its YAML form
     */
    Object write(T value, WriteContext context);
}

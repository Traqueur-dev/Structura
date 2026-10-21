package fr.traqueur.structura.readers;

/**
 * A custom reader that sees the value as YAML typed it, the full type requested and where the value
 * sits in the document — the richer counterpart of {@link Reader}, which only receives a string.
 *
 * <pre>
 * // amount: 100 and amount: "100" both reach the reader
 * registry.registerContextual(Amount.class, ctx -&gt; new Amount(Long.parseLong(ctx.text())));
 *
 * // one reader for every Resolvable&lt;T&gt;, reading T with Structura's own rules
 * registry.registerGeneric(Resolvable.class, ctx -&gt; ctx.raw() instanceof String s &amp;&amp; s.startsWith("%")
 *         ? Resolvable.placeholder(s)
 *         : Resolvable.of(ctx.convert(ctx.typeArgument(0))));
 * </pre>
 *
 * <p>An exception thrown by the reader becomes a {@code READER} error carrying the path and the
 * position of the value.</p>
 *
 * @param <T> the type the reader produces
 */
@FunctionalInterface
public interface ContextualReader<T> {

    /**
     * Reads a value.
     *
     * @param context the value and what is requested of it
     * @return the value read
     */
    T read(ReadContext context);
}

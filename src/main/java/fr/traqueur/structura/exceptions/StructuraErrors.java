package fr.traqueur.structura.exceptions;

import java.util.List;

/**
 * Every error of a document, reported together when the processor collects errors
 * ({@code Structura.builder().collectErrors(true)}), so that the whole file can be fixed in one go.
 *
 * <p>Its own kind, path and position are those of the first error, so that code handling a single
 * error keeps working; {@link #errors()} lists them all, in document order of discovery.</p>
 * <pre>
 * 3 errors in config.yml:
 *   config.yml:3:12: 'many' is not a number (limits.per-day)
 *   config.yml:6:3: 'symbol' is required but not provided (currencies.gems.symbol)
 *   config.yml:9:10: Value must be at least 1 (offers[0].price)
 * </pre>
 */
public class StructuraErrors extends StructuraException {

    private final List<StructuraException> errors;

    /**
     * @param errors the errors, at least one
     */
    public StructuraErrors(List<StructuraException> errors) {
        super(errors.getFirst().kind(), errors.getFirst().detail(), errors.getFirst().path().orElse(null),
                errors.getFirst());
        this.errors = List.copyOf(errors);
        StructuraException first = errors.getFirst();
        if (first.line().isPresent()) {
            locate(first.source().orElse(null), first.line().getAsInt(), first.column().getAsInt());
        }
        errors.stream().skip(1).forEach(this::addSuppressed);
    }

    /**
     * @return every error of the document, at least one
     */
    public List<StructuraException> errors() {
        return errors;
    }

    @Override
    public String getMessage() {
        if (errors.size() == 1) {
            return errors.getFirst().getMessage();
        }
        StringBuilder message = new StringBuilder().append(errors.size()).append(" errors");
        source().ifPresent(source -> message.append(" in ").append(source));
        message.append(':');
        for (StructuraException error : errors) {
            message.append(System.lineSeparator()).append("  ").append(error.getMessage());
        }
        return message.toString();
    }
}

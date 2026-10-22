package fr.traqueur.structura.exceptions;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Custom exception class for Structura API.
 * This exception is thrown when there is an error related to the Structura API.
 *
 * <p>An error raised while reading a document says where it happened: the {@link #path() path}
 * of the key in the document ({@code limits.per-day}, {@code offers[2].price}), the
 * {@link #line() line} and {@link #column() column} in the text, the {@link #source() source}
 * it was read from, and a stable {@link #kind() kind}. The message reads, for instance:</p>
 * <pre>
 * 3:5: 'many' is not a number (limits.per-day)
 * </pre>
 * <p>Each part is only present when known: an exception raised outside of a document (a registry
 * misuse, a direct call to the validator) keeps its plain message.</p>
 */
public class StructuraException extends RuntimeException {

    /**
     * The stable category of an error, meant for tools: the message may be reworded,
     * the kind will not change.
     */
    public enum Kind {
        /** A key that no component of the record reads (strict mode only). */
        UNKNOWN_KEY,
        /** A required key that is absent from the document. */
        MISSING,
        /** A value whose shape or type does not fit the component ({@code per-day: many} for an int). */
        WRONG_TYPE,
        /** A value that was read but refused by a validation rule or by the record itself. */
        INVALID,
        /** A custom reader that failed on the value. */
        READER,
        /** A document that is not valid YAML. */
        SYNTAX,
        /** Anything else: a misuse of the API, an unsupported declaration, an I/O failure. */
        OTHER
    }

    private final Kind kind;
    private final String detail;
    private String path;
    private String source;
    private int line = -1;
    private int column = -1;
    private boolean atKey;

    /**
     * Constructs a new StructuraException with the specified detail message.
     *
     * @param message the detail message
     */
    public StructuraException(String message) {
        this(Kind.OTHER, message, null, null);
    }

    /**
     * Constructs a new StructuraException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause of the exception
     */
    public StructuraException(String message, Throwable cause) {
        this(Kind.OTHER, message, null, cause);
    }

    /**
     * Constructs a new StructuraException raised at a given path of a document.
     *
     * @param kind   the category of the error
     * @param detail the reason, without the location ({@code 'many' is not a number})
     * @param path   the path of the key in the document, or null / empty when unknown
     */
    public StructuraException(Kind kind, String detail, String path) {
        this(kind, detail, path, null);
    }

    /**
     * Constructs a new StructuraException raised at a given path of a document.
     *
     * @param kind   the category of the error
     * @param detail the reason, without the location ({@code 'many' is not a number})
     * @param path   the path of the key in the document, or null / empty when unknown
     * @param cause  the cause of the exception
     */
    public StructuraException(Kind kind, String detail, String path, Throwable cause) {
        super(detail, cause);
        this.kind = kind == null ? Kind.OTHER : kind;
        this.detail = detail;
        this.path = path == null || path.isEmpty() ? null : path;
    }

    /**
     * Attaches the position of the error in the text it was read from. Called by the processor
     * once the error has bubbled up to the document; does nothing if a position is already set,
     * so that the innermost, most precise position wins.
     *
     * @param source the name of the source (a file name), or null when unnamed
     * @param line   the 1-based line
     * @param column the 1-based column
     * @return this exception
     */
    public StructuraException locate(String source, int line, int column) {
        if (this.line < 0) {
            this.source = source;
            this.line = line;
            this.column = column;
        }
        return this;
    }

    /**
     * Attaches the path of the error in the document, when the code that raised it did not know it.
     * Called while the error bubbles up through the conversion; does nothing if a path is already
     * set, so that the innermost, most precise path wins.
     *
     * @param path the path of the key in the document
     * @return this exception
     */
    public StructuraException atPath(String path) {
        if (this.path == null && path != null && !path.isEmpty()) {
            this.path = path;
        }
        return this;
    }

    /**
     * Makes the position of this error that of the key of its path rather than of its value, as for an
     * unknown key: {@code 'toggle' and 'select' cannot both be set} points at {@code select:}.
     *
     * @return this exception
     */
    public StructuraException pointAtKey() {
        this.atKey = true;
        return this;
    }

    /**
     * @return whether the position of this error is that of the key of its path rather than of its value
     */
    public boolean pointsAtKey() {
        return atKey || kind == Kind.UNKNOWN_KEY;
    }

    /**
     * @return the category of this error
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return the reason of this error, without its location
     */
    public String detail() {
        return detail;
    }

    /**
     * @return the path of the faulty key in the document ({@code limits.per-day}, {@code offers[2].price}), if known
     */
    public Optional<String> path() {
        return Optional.ofNullable(path);
    }

    /**
     * @return the name of the source the faulty value was read from, if known
     */
    public Optional<String> source() {
        return Optional.ofNullable(source);
    }

    /**
     * @return the 1-based line of the faulty value, if known
     */
    public OptionalInt line() {
        return line < 0 ? OptionalInt.empty() : OptionalInt.of(line);
    }

    /**
     * @return the 1-based column of the faulty value, if known
     */
    public OptionalInt column() {
        return column < 0 ? OptionalInt.empty() : OptionalInt.of(column);
    }

    /**
     * Renders the error as {@code [source:]line:column: detail (path)}, each part only when known.
     */
    @Override
    public String getMessage() {
        StringBuilder message = new StringBuilder();
        if (source != null) {
            message.append(source).append(':');
        }
        if (line >= 0) {
            message.append(line).append(':').append(column).append(": ");
        } else if (source != null) {
            message.append(' ');
        }
        message.append(detail);
        // An error pointing at a key (an unknown key, two keys set together) already names it and its section
        if (path != null && !pointsAtKey()) {
            message.append(" (").append(path).append(')');
        }
        return message.toString();
    }
}

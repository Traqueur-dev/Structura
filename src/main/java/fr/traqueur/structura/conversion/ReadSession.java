package fr.traqueur.structura.conversion;

import fr.traqueur.structura.exceptions.StructuraException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The state of one read of a document, shared by the factory, the converter and the validator
 * without threading it through every signature: whether errors are collected rather than thrown,
 * and the errors collected so far.
 *
 * <p>A session is bound to the reading thread for the duration of a parse; outside of one (a
 * direct call to the converter or the validator), errors are thrown as they always were.</p>
 */
public final class ReadSession {

    private static final ThreadLocal<ReadSession> CURRENT = new ThreadLocal<>();

    private final boolean collectErrors;
    private final List<StructuraException> errors = new ArrayList<>();

    /**
     * @param collectErrors whether errors are collected and reported together at the end of the read
     */
    public ReadSession(boolean collectErrors) {
        this.collectErrors = collectErrors;
    }

    /**
     * Runs a read within this session.
     *
     * @param body the read
     * @param <T> the type of the result
     * @return the result of the read
     */
    public <T> T run(Supplier<T> body) {
        ReadSession previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * @return the errors collected so far, in the order they were met
     */
    public List<StructuraException> errors() {
        return errors;
    }

    /**
     * Reports an error: collected when the current session collects errors, thrown otherwise.
     * The caller carries on with the next value only when this returns.
     *
     * @param error the error
     * @throws StructuraException the error itself, when errors are not collected
     */
    public static void report(StructuraException error) {
        ReadSession session = CURRENT.get();
        if (session == null || !session.collectErrors) {
            throw error;
        }
        session.errors.add(error);
    }

    /**
     * @return the number of errors collected so far in the current session, 0 outside of one;
     *         a read compares it before and after a value to know whether that value failed
     */
    public static int errorCount() {
        ReadSession session = CURRENT.get();
        return session == null ? 0 : session.errors.size();
    }
}

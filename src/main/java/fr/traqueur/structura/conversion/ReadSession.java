package fr.traqueur.structura.conversion;

import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.registries.CustomReaderRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistries;
import fr.traqueur.structura.registries.PolymorphicRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The state of one read of a document, shared by the factory, the converter and the validator
 * without threading it through every signature: whether errors are collected rather than thrown,
 * the errors collected so far, and whether unknown keys are refused.
 *
 * <p>A session is bound to the reading thread for the duration of a parse; outside of one (a
 * direct call to the converter or the validator), errors are thrown as they always were.</p>
 */
public final class ReadSession {

    private static final ThreadLocal<ReadSession> CURRENT = new ThreadLocal<>();

    private final boolean collectErrors;
    private final boolean strictKeys;
    private final CustomReaderRegistry readers;
    private final PolymorphicRegistries polymorphics;
    private final List<StructuraException> errors = new ArrayList<>();
    private final Set<Map<?, ?>> sharedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
    private int strictScopes;

    /**
     * @param collectErrors whether errors are collected and reported together at the end of the read
     */
    public ReadSession(boolean collectErrors) {
        this(collectErrors, false);
    }

    /**
     * @param collectErrors whether errors are collected and reported together at the end of the read
     * @param strictKeys whether a key that no component reads is an error everywhere in the document
     */
    public ReadSession(boolean collectErrors, boolean strictKeys) {
        this(collectErrors, strictKeys, null, null);
    }

    /**
     * @param collectErrors whether errors are collected and reported together at the end of the read
     * @param strictKeys whether a key that no component reads is an error everywhere in the document
     * @param readers the processor's own reader registry, looked up before the global one, or null
     * @param polymorphics the processor's own polymorphic registries, looked up before the global ones, or null
     */
    public ReadSession(boolean collectErrors, boolean strictKeys,
                       CustomReaderRegistry readers, PolymorphicRegistries polymorphics) {
        this.collectErrors = collectErrors;
        this.strictKeys = strictKeys;
        this.readers = readers;
        this.polymorphics = polymorphics;
    }

    /**
     * @return the reader registries of the current read, in lookup order: the processor's own, then the global one
     */
    public static List<CustomReaderRegistry> readerRegistries() {
        ReadSession session = CURRENT.get();
        CustomReaderRegistry global = CustomReaderRegistry.getInstance();
        if (session == null || session.readers == null || session.readers == global) {
            return List.of(global);
        }
        return List.of(session.readers, global);
    }

    /**
     * The registry of a polymorphic interface for the current read: the processor's own if it has
     * one for that interface, the global one otherwise.
     *
     * @param type the polymorphic interface
     * @param <T> the polymorphic interface type
     * @return its registry
     * @throws StructuraException if no registry knows the interface
     */
    public static <T extends Loadable> PolymorphicRegistry<T> polymorphicRegistry(Class<T> type) {
        ReadSession session = CURRENT.get();
        if (session != null && session.polymorphics != null) {
            Optional<PolymorphicRegistry<T>> own = session.polymorphics.find(type);
            if (own.isPresent()) {
                return own.get();
            }
        }
        return PolymorphicRegistries.global().get(type);
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
     * @return whether unknown keys are refused at this point of the read: everywhere in strict mode,
     *         and beneath a record annotated {@code @Strict}
     */
    public static boolean strictKeys() {
        ReadSession session = CURRENT.get();
        return session != null && (session.strictKeys || session.strictScopes > 0);
    }

    /**
     * Reads the values beneath a {@code @Strict} record: unknown keys are refused there too.
     *
     * @param body the read of the record's components
     * @param <T> the type of the result
     * @return the result of the read
     */
    public static <T> T strictScope(Supplier<T> body) {
        ReadSession session = CURRENT.get();
        if (session == null) {
            return body.get();
        }
        session.strictScopes++;
        try {
            return body.get();
        } finally {
            session.strictScopes--;
        }
    }

    /**
     * Marks a node read by a record that does not own it, such as the parent's node handed to a
     * fully inline polymorphic component: its keys are checked by the owner, not by that record.
     *
     * @param node the node
     * @param <M> the type of the node
     * @return the node
     */
    public static <M extends Map<?, ?>> M share(M node) {
        ReadSession session = CURRENT.get();
        if (session != null) {
            session.sharedNodes.add(node);
        }
        return node;
    }

    /**
     * @param node a node
     * @return whether the node was {@link #share shared} by its owner
     */
    public static boolean isShared(Map<?, ?> node) {
        ReadSession session = CURRENT.get();
        return session != null && session.sharedNodes.contains(node);
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

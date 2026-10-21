package fr.traqueur.structura.mapping;

import fr.traqueur.structura.exceptions.StructuraException;

import java.util.Collection;
import java.util.Optional;

/**
 * The errors of strict mode: a key that nothing reads, with the closest known key as a suggestion.
 */
public final class UnknownKeys {

    private UnknownKeys() {
    }

    /**
     * The error for an unknown key, suggesting the closest known key when it is close enough to be a typo.
     *
     * @param key the unknown key
     * @param nodePath the path of the section holding it, empty at the root
     * @param known the keys read in that section
     * @return the error, of kind UNKNOWN_KEY
     */
    public static StructuraException error(String key, String nodePath, Collection<String> known) {
        StringBuilder detail = new StringBuilder("unknown key '").append(key).append('\'');
        if (!nodePath.isEmpty()) {
            detail.append(" under '").append(nodePath).append('\'');
        }
        closestKey(key, known).ifPresent(suggestion ->
                detail.append(" (did you mean '").append(suggestion).append("'?)"));
        return new StructuraException(StructuraException.Kind.UNKNOWN_KEY, detail.toString(),
                nodePath.isEmpty() ? key : nodePath + "." + key);
    }

    /**
     * The known key closest to a key by edit distance, if it is close enough to be a typo.
     *
     * @param key the unknown key
     * @param known the keys read in its section
     * @return the suggestion, if any
     */
    public static Optional<String> closestKey(String key, Collection<String> known) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : known) {
            int distance = editDistance(key, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        int tolerance = Math.max(2, key.length() / 3);
        return best != null && bestDistance <= tolerance && bestDistance < key.length()
                ? Optional.of(best)
                : Optional.empty();
    }

    /**
     * The Levenshtein distance between two keys.
     */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}

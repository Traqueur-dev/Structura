package fr.traqueur.structura.writers.serializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What a configuration changes from its default, with the merge rules of layered reading: a section
 * is compared key by key, a list or a single value as a whole. Read as a layer over the default, the
 * result gives back the configuration.
 */
public final class Overrides {

    private Overrides() {
    }

    /**
     * @param current the configuration, as plain YAML values
     * @param defaults the default it is read over, as plain YAML values
     * @return the keys of {@code current} whose value differs from {@code defaults}, recursively for sections
     */
    public static Map<String, Object> of(Map<String, Object> current, Map<String, Object> defaults) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        current.forEach((key, value) -> {
            if (!defaults.containsKey(key)) {
                overrides.put(key, value);
                return;
            }
            Object fallback = defaults.get(key);
            if (value instanceof Map<?, ?> section && fallback instanceof Map<?, ?> defaultSection) {
                Map<String, Object> nested = of(asStringMap(section), asStringMap(defaultSection));
                if (!nested.isEmpty()) {
                    overrides.put(key, nested);
                }
            } else if (!Objects.equals(value, fallback)) {
                overrides.put(key, value);
            }
        });
        return overrides;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<Object, Object>) map).forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}

package fr.traqueur.config;

import java.util.Map;

public interface Configurable<T extends Enum<T>> {

    default String key(T element) {
        return element.name().replaceAll("_", "-").toLowerCase();
    }

}

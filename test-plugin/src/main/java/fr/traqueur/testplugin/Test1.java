package fr.traqueur.testplugin;

import fr.traqueur.config.Configurable;

import java.util.Map;

public enum Test1 implements Configurable {

    STR_1,
    STR_2,
    STR_3,
    ;

    private String value;

    @Override
    public String toString() {
        return "Test1{" +
                "value='" + value + '\'' +
                '}';
    }
}

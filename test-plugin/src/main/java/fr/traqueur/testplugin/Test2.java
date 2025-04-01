package fr.traqueur.testplugin;

import fr.traqueur.config.Configurable;

import java.util.List;
import java.util.Map;

public enum Test2 implements Configurable<Test2> {

    OBJ_1,
    ;

    private int int1;
    private String str1;
    private List<String> lst1;
    private Map<String, String> map1;


    @Override
    public String toString() {
        return "Test2{" +
                "int1=" + int1 +
                ", str1='" + str1 + '\'' +
                ", lst1=" + lst1 +
                ", map1=" + map1 +
                '}';
    }
}

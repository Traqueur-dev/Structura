package fr.traqueur.structura.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Polymorphic {

    String key() default "type";

}

package fr.traqueur.structura.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify options for a field or parameter.
 * This can be used to indicate whether the field is a key, its name, and if it is optional.
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Options {
    /**
     * Indicates whether the annotated field or parameter is a key.
     * Defaults to false.
     *
     * @return true if it is a key, false otherwise
     */
    boolean isKey() default false;

    /**
     * Specifies the name of the field or parameter.
     * If not provided, the default is an empty string.
     *
     * @return the name of the field or parameter
     */
    String name() default "";

    /**
     * Indicates whether the annotated field or parameter is optional.
     * Defaults to false.
     *
     * @return true if it is optional, false otherwise
     */
    boolean optional() default false;

    /**
     * Indicates whether the fields of this record should be inlined (flattened)
     * at the parent level instead of being nested under this field's key.
     *
     * When inline = false (default):
     * app-name: MyApp
     * server:
     *   host: localhost
     *   port: 8080
     *
     * When inline = true:
     * app-name: MyApp
     * host: localhost    # server fields are flattened to parent level
     * port: 8080
     *
     * Works for record types implementing Loadable, for interfaces annotated with
     * {@code @Polymorphic(inline = true)}, and for {@link java.util.Map} components.
     *
     * On a Map, inline turns the component into the catch-all of its node: it absorbs
     * every key that no sibling component claims. A sibling claims its effective name,
     * so {@link #name()} is honoured, and an inline sibling record claims — recursively —
     * the names of the fields it reads at that same level. Values go through the Map's
     * generic value type, so Map&lt;String, List&lt;String&gt;&gt; and
     * Map&lt;String, SomeRecord&gt; work as anywhere else. Given a record declaring a
     * String comment and a catch-all Map&lt;String, String&gt; byLocale:
     *
     * comment: "greeting shown on join"   # the declared component
     * fr_FR: "Bienvenue"                  # absorbed by byLocale
     * en_US: "Welcome"                    # absorbed by byLocale
     *
     * A catch-all map is never null: with nothing left to absorb it is simply empty, so
     * it is never reported as a missing required field. It may be declared anywhere in
     * the record. Two shapes are rejected at load time, because they would produce a
     * wrong but plausible result: two inline maps in the same record, since nothing says
     * how to split the remaining keys between them, and an inline map next to a fully
     * inline polymorphic component (inline here plus {@code @Polymorphic(inline = true)}),
     * whose keys are only known once its discriminator has been read and would therefore
     * be silently swallowed by the map.
     *
     * Defaults to false.
     *
     * @return true if the fields should be inlined, false otherwise
     */
    boolean inline() default false;
}
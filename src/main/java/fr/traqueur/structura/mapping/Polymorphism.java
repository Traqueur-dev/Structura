package fr.traqueur.structura.mapping;

import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.api.Loadable;

/**
 * The forms a {@link Polymorphic} interface takes in a document, decided by {@code inline} and {@code useKey}:
 *
 * <table>
 *   <caption>Polymorphic forms</caption>
 *   <tr><th>Annotation</th><th>Form</th><th>Example</th></tr>
 *   <tr><td>{@code @Polymorphic}</td><td>the discriminator inside the value</td>
 *       <td>{@code reward: {type: money, amount: 3}}</td></tr>
 *   <tr><td>{@code inline = true}</td><td>the discriminator next to the value, in the parent</td>
 *       <td>{@code type: mysql} then {@code database: {...}}</td></tr>
 *   <tr><td>{@code useKey = true}</td><td>the key of the value is the sub-type's name</td>
 *       <td>{@code trim: {material: DIAMOND}}</td></tr>
 *   <tr><td>{@code useKey = true, inline = true}</td><td>the sub-type is the registered key present in the
 *       section</td><td>{@code {label: Chat, select: {tab: chat}}}</td></tr>
 * </table>
 */
public final class Polymorphism {

    private Polymorphism() {
    }

    /**
     * @param type a type
     * @return whether it is a polymorphic interface: a {@link Loadable} interface annotated {@link Polymorphic}
     */
    public static boolean isPolymorphic(Class<?> type) {
        return type.isInterface() && Loadable.class.isAssignableFrom(type) && type.isAnnotationPresent(Polymorphic.class);
    }

    /**
     * {@code @Polymorphic(useKey = true, inline = true)}: the sub-type is the one registered key present in
     * the section; its value is the sub-type's node, or the sub-type reads the whole section when it has a
     * component of that name.
     *
     * @param type a type
     * @return whether the sub-type is selected by the key present in the section
     */
    public static boolean isKeySelected(Class<?> type) {
        Polymorphic polymorphic = annotation(type);
        return polymorphic != null && polymorphic.useKey() && polymorphic.inline();
    }

    /**
     * {@code @Polymorphic(useKey = true)} without {@code inline}: the key of the value is the sub-type's name.
     *
     * @param type a type
     * @return whether the key of the value names the sub-type
     */
    public static boolean isKeyDiscriminated(Class<?> type) {
        Polymorphic polymorphic = annotation(type);
        return polymorphic != null && polymorphic.useKey() && !polymorphic.inline();
    }

    /**
     * {@code @Polymorphic(inline = true)} without {@code useKey}: the discriminator sits next to the value,
     * in the parent section.
     *
     * @param type a type
     * @return whether the discriminator sits in the parent section
     */
    public static boolean isInlineDiscriminated(Class<?> type) {
        Polymorphic polymorphic = annotation(type);
        return polymorphic != null && polymorphic.inline() && !polymorphic.useKey();
    }

    private static Polymorphic annotation(Class<?> type) {
        if (type == null || !Loadable.class.isAssignableFrom(type)) {
            return null;
        }
        return type.getAnnotation(Polymorphic.class);
    }
}

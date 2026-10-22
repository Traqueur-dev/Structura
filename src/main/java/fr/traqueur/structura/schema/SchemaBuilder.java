package fr.traqueur.structura.schema;

import fr.traqueur.structura.annotations.Description;
import fr.traqueur.structura.annotations.Options;
import fr.traqueur.structura.annotations.Polymorphic;
import fr.traqueur.structura.annotations.defaults.DefaultReference;
import fr.traqueur.structura.api.Loadable;
import fr.traqueur.structura.exceptions.StructuraException;
import fr.traqueur.structura.mapping.FieldMapper;
import fr.traqueur.structura.mapping.Polymorphism;
import fr.traqueur.structura.references.Reference;
import fr.traqueur.structura.registries.DefaultValueRegistry;
import fr.traqueur.structura.registries.PolymorphicRegistry;
import fr.traqueur.structura.schema.Schema.Discriminator;
import fr.traqueur.structura.schema.Schema.Shape;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builds the {@link Schema} of a record with the rules the reader applies: the same keys, the same
 * inline flattening, the same polymorphic resolution. Used by {@code Structura.schema(Class)} and
 * {@code StructuraProcessor#schema(Class)}.
 */
public final class SchemaBuilder {

    private final Function<Class<? extends Loadable>, Optional<PolymorphicRegistry<?>>> polymorphics;
    private final Predicate<Type> hasReader;
    private final Set<Class<?>> visiting = new HashSet<>();

    /**
     * @param polymorphics finds the registry of a polymorphic interface, for its sub-types
     * @param hasReader whether a custom reader reads a type
     */
    public SchemaBuilder(Function<Class<? extends Loadable>, Optional<PolymorphicRegistry<?>>> polymorphics,
                         Predicate<Type> hasReader) {
        this.polymorphics = polymorphics;
        this.hasReader = hasReader;
    }

    /**
     * @param type a record implementing Loadable, or an enum implementing Loadable
     * @return its schema
     */
    public Schema describe(Class<?> type) {
        if (type == null) {
            throw new StructuraException("Cannot describe a null class");
        }
        if (type.isEnum()) {
            return describeConfigurableEnum(type);
        }
        if (!type.isRecord()) {
            throw new StructuraException("Class " + type.getName() + " is not a record type");
        }
        return section(type, null, true, null, describe(type.getAnnotation(Description.class), null));
    }

    private Schema of(Type generic, String key, boolean required, Object defaultValue, String description) {
        Class<?> raw = rawClass(generic);

        if (hasReader.test(generic) || (generic != raw && hasReader.test(raw))) {
            return new Schema(key, generic, Shape.CUSTOM, required, defaultValue, description);
        }
        if (raw == String.class || raw == char.class || raw == Character.class) {
            return new Schema(key, generic, Shape.TEXT, required, defaultValue, description);
        }
        if (raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                || raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class) {
            return new Schema(key, generic, Shape.INTEGER, required, defaultValue, description);
        }
        if (raw == double.class || raw == Double.class || raw == float.class || raw == Float.class) {
            return new Schema(key, generic, Shape.NUMBER, required, defaultValue, description);
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return new Schema(key, generic, Shape.BOOLEAN, required, defaultValue, description);
        }
        if (raw == LocalDate.class) {
            return new Schema(key, generic, Shape.DATE, required, defaultValue, description);
        }
        if (raw == LocalDateTime.class) {
            return new Schema(key, generic, Shape.DATE_TIME, required, defaultValue, description);
        }
        if (raw.isEnum()) {
            Schema schema = new Schema(key, generic, Shape.ENUM, required,
                    defaultValue instanceof Enum<?> constant ? constant.name() : defaultValue, description);
            Arrays.stream(raw.getEnumConstants()).map(constant -> ((Enum<?>) constant).name())
                    .forEach(schema.enumValues::add);
            return schema;
        }
        if (Reference.class.isAssignableFrom(raw)) {
            return new Schema(key, generic, Shape.REFERENCE, required, defaultValue, description);
        }
        if (Collection.class.isAssignableFrom(raw)) {
            Schema schema = new Schema(key, generic, Shape.LIST, required, defaultValue, description);
            schema.items = of(typeArgument(generic, 0), null, true, null, null);
            return schema;
        }
        if (Map.class.isAssignableFrom(raw)) {
            Schema schema = new Schema(key, generic, Shape.MAP, required, defaultValue, description);
            schema.values = of(typeArgument(generic, 1), null, true, null, null);
            return schema;
        }
        if (raw.isInterface() && Loadable.class.isAssignableFrom(raw) && raw.isAnnotationPresent(Polymorphic.class)) {
            return polymorphic(raw, generic, key, required, description, null);
        }
        if (raw.isRecord()) {
            return section(raw, key, required, defaultValue, description(raw, description));
        }
        return new Schema(key, generic, Shape.ANY, required, defaultValue, description);
    }

    private Schema section(Class<?> recordClass, String key, boolean required, Object defaultValue, String description) {
        Schema schema = new Schema(key, recordClass, Shape.SECTION, required, defaultValue, description);
        if (!visiting.add(recordClass)) {
            schema.recursive = true;
            return schema;
        }
        try {
            fill(schema, recordClass);
        } finally {
            visiting.remove(recordClass);
        }
        return schema;
    }

    /**
     * Adds the keys a record reads at the level of its node, as the reader does.
     */
    private void fill(Schema schema, Class<?> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Parameter[] parameters = recordClass.getDeclaredConstructors()[0].getParameters();

        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Parameter parameter = parameters[i];
            Class<?> type = component.getType();
            Options options = parameter.getAnnotation(Options.class);
            boolean inline = options != null && options.inline();

            if (inline && type.isRecord()) {
                Schema inlined = section(type, null, true, null, null);
                schema.properties.addAll(inlined.properties);
                if (inlined.remaining != null) schema.remaining = inlined.remaining;
                if (inlined.inlinePolymorphic != null) schema.inlinePolymorphic = inlined.inlinePolymorphic;
                continue;
            }
            if (inline && Map.class.isAssignableFrom(type)) {
                schema.remaining = of(typeArgument(component.getGenericType(), 1), null, true, null, null);
                continue;
            }
            if (inline && Polymorphism.isKeySelected(type)) {
                // The registered key present in this section selects the sub-type
                boolean optional = options.optional();
                schema.inlinePolymorphic = polymorphic(type, component.getGenericType(), FieldMapper.keyOf(component),
                        !optional, description(parameter), Discriminator.PRESENT_KEY);
                continue;
            }
            if (inline && isPolymorphic(type) && type.getAnnotation(Polymorphic.class).inline()) {
                schema.inlinePolymorphic = polymorphic(type, component.getGenericType(), FieldMapper.keyOf(component),
                        true, description(parameter), Discriminator.INLINE);
                continue;
            }
            if (options != null && options.isKey()) {
                if (type.isRecord()) {
                    // Complex key mapping: the key record's components sit at this level
                    schema.properties.addAll(section(type, null, true, null, null).properties);
                } else {
                    schema.keyProperty = of(component.getGenericType(), FieldMapper.keyOf(component), true, null,
                            description(parameter));
                }
                continue;
            }

            Object defaultValue = defaultValue(parameter);
            boolean optional = options != null && options.optional();
            String key = FieldMapper.keyOf(component);
            Schema property = of(component.getGenericType(), key, !optional && defaultValue == null, defaultValue,
                    description(parameter));

            if (property.shape() == Shape.POLYMORPHIC && Polymorphism.isInlineDiscriminated(type)) {
                // @Polymorphic(inline = true): the discriminator sits next to the value, in this section
                property.placement = Discriminator.PARENT;
                Schema discriminator = new Schema(property.discriminator, String.class, Shape.ENUM,
                        property.required, null, "The type of '" + key + "'");
                discriminator.enumValues.addAll(property.subtypes.keySet());
                schema.properties.add(discriminator);
            } else if (property.shape() == Shape.POLYMORPHIC && Polymorphism.isKeyDiscriminated(type)) {
                property.placement = Discriminator.KEY;
            }
            schema.properties.add(property);
        }
    }

    private Schema polymorphic(Class<?> type, Type generic, String key, boolean required, String description,
                               Discriminator placement) {
        Polymorphic annotation = type.getAnnotation(Polymorphic.class);
        Schema schema = new Schema(key, generic, Shape.POLYMORPHIC, required, null, description(type, description));
        schema.placement = placement != null ? placement
                : Polymorphism.isKeySelected(type) ? Discriminator.PRESENT_KEY
                : annotation.useKey() ? Discriminator.KEY
                : Discriminator.NESTED;
        // The sub-type of a key-selected value is named by the key present, not by a discriminator key
        schema.discriminator = schema.placement == Discriminator.PRESENT_KEY ? null : annotation.key();

        @SuppressWarnings("unchecked")
        Class<? extends Loadable> polymorphicType = (Class<? extends Loadable>) type;
        polymorphics.apply(polymorphicType).ifPresent(registry -> registry.availableNames().stream().sorted()
                .forEach(name -> registry.get(name).ifPresent(subType ->
                        schema.subtypes.put(name, section(subType, null, true, null,
                                description(subType, null))))));
        return schema;
    }

    private Schema describeConfigurableEnum(Class<?> enumClass) {
        Schema schema = new Schema(null, enumClass, Shape.SECTION, true, null,
                describe(enumClass.getAnnotation(Description.class), null));
        for (Object constant : enumClass.getEnumConstants()) {
            Schema entry = new Schema(FieldMapper.keyOf((Enum<?>) constant), enumClass, Shape.SECTION, true, null, null);
            Arrays.stream(enumClass.getDeclaredFields())
                    .filter(field -> !field.isSynthetic() && !field.isEnumConstant()
                            && !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                    .forEach(field -> {
                        Object defaultValue = DefaultValueRegistry.getInstance()
                                .getDefaultValue(field.getType(), List.of(field.getAnnotations()));
                        Options options = field.getAnnotation(Options.class);
                        boolean optional = options != null && options.optional();
                        entry.properties.add(of(field.getGenericType(), new FieldMapper().getFieldNameFromField(field),
                                !optional && defaultValue == null, defaultValue, description(field)));
                    });
            schema.properties.add(entry);
        }
        return schema;
    }

    private static Object defaultValue(Parameter parameter) {
        DefaultReference reference = parameter.getAnnotation(DefaultReference.class);
        if (reference != null) {
            return reference.key();
        }
        try {
            return DefaultValueRegistry.getInstance().getDefaultValue(parameter.getType(), List.of(parameter.getAnnotations()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String description(AnnotatedElement element) {
        Options options = element.getAnnotation(Options.class);
        return describe(element.getAnnotation(Description.class), options);
    }

    private static String description(Class<?> type, String fallback) {
        String own = describe(type.getAnnotation(Description.class), null);
        return fallback != null ? fallback : own;
    }

    private static String describe(Description description, Options options) {
        if (description != null && !description.value().isBlank()) {
            return description.value();
        }
        if (options != null && !options.description().isBlank()) {
            return options.description();
        }
        return null;
    }

    private static boolean isPolymorphic(Class<?> type) {
        return type.isInterface() && Loadable.class.isAssignableFrom(type) && type.isAnnotationPresent(Polymorphic.class);
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && index < parameterized.getActualTypeArguments().length) {
            return parameterized.getActualTypeArguments()[index];
        }
        return Object.class;
    }

    private static Class<?> rawClass(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType parameterized -> (Class<?>) parameterized.getRawType();
            default -> Object.class;
        };
    }
}

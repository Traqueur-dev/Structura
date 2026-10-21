package fr.traqueur.structura.schema;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The description of what a configuration reads, as a tree that follows the shape of the document:
 * keys as written in the YAML (inline components flattened, {@code @Options(name)} honoured), their type,
 * whether they are required, their default value and description, the sub-types of a polymorphic
 * value, the elements of a list and the values of a map.
 *
 * <p>Built by {@code Structura.schema(Class)} with Structura's own rules, so that a documentation or an
 * editor schema never reimplements the walk. {@link #toJsonSchema()} renders it as a JSON Schema, which
 * editors use for autocompletion and validation of YAML files.</p>
 *
 * <pre>
 * Schema schema = Structura.schema(ShopSettings.class);
 * for (Schema property : schema.properties()) {
 *     System.out.println(property.key() + " : " + property.shape() + (property.required() ? " (required)" : ""));
 * }
 * Files.writeString(Path.of("shop.schema.json"), schema.toJsonSchema());
 * </pre>
 */
public final class Schema {

    /**
     * The shape of a value in the document.
     */
    public enum Shape {
        /** A record: a section of keys, see {@link #properties()}. */
        SECTION,
        /** A list or a set, see {@link #items()}. */
        LIST,
        /** A map with free keys, see {@link #values()}. */
        MAP,
        /** A polymorphic interface, see {@link #discriminator()} and {@link #subtypes()}. */
        POLYMORPHIC,
        /** An enum, see {@link #enumValues()}. */
        ENUM,
        /** A text, or a single character. */
        TEXT,
        /** A whole number. */
        INTEGER,
        /** A decimal number. */
        NUMBER,
        /** {@code true} or {@code false}. */
        BOOLEAN,
        /** A date, {@code yyyy-MM-dd}. */
        DATE,
        /** A date and time, {@code yyyy-MM-ddTHH:mm:ss}. */
        DATE_TIME,
        /** The key of an object resolved through a {@code Reference}. */
        REFERENCE,
        /** A value read by a custom reader: any shape the reader accepts. */
        CUSTOM,
        /** A type Structura has no rule for. */
        ANY
    }

    /**
     * Where the discriminator of a polymorphic value is written.
     */
    public enum Discriminator {
        /** Inside the value: {@code reward: {type: money, amount: 3}}. */
        NESTED,
        /** Next to the value, in the parent section: {@code type: mysql} then {@code database: {...}}. */
        PARENT,
        /** In the parent section, with the sub-type's keys there too ({@code @Options(inline = true)}). */
        INLINE,
        /** The key of the value is the sub-type's name: {@code speed: {level: 2}}. */
        KEY
    }

    private final String key;
    private final Type type;
    private final Shape shape;
    final boolean required;
    final Object defaultValue;
    final String description;
    final List<Schema> properties = new ArrayList<>();
    Schema remaining;
    Schema keyProperty;
    Schema inlinePolymorphic;
    Schema items;
    Schema values;
    String discriminator;
    Discriminator placement;
    final Map<String, Schema> subtypes = new LinkedHashMap<>();
    final List<String> enumValues = new ArrayList<>();
    boolean recursive;

    Schema(String key, Type type, Shape shape, boolean required, Object defaultValue, String description) {
        this.key = key;
        this.type = type;
        this.shape = shape;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    Schema withKey(String key, boolean required, Object defaultValue, String description) {
        Schema copy = new Schema(key, type, shape, required, defaultValue,
                description != null ? description : this.description);
        copy.properties.addAll(properties);
        copy.remaining = remaining;
        copy.keyProperty = keyProperty;
        copy.inlinePolymorphic = inlinePolymorphic;
        copy.items = items;
        copy.values = values;
        copy.discriminator = discriminator;
        copy.placement = placement;
        copy.subtypes.putAll(subtypes);
        copy.enumValues.addAll(enumValues);
        copy.recursive = recursive;
        return copy;
    }

    /**
     * @return the key of this value in its section, as written in the document; null for the root,
     *         an element of a list or a value of a map
     */
    public String key() {
        return key;
    }

    /**
     * @return the Java type read, with its type arguments
     */
    public Type type() {
        return type;
    }

    /**
     * @return the shape of the value in the document
     */
    public Shape shape() {
        return shape;
    }

    /**
     * @return whether the key must be present: not optional and without default value
     */
    public boolean required() {
        return required;
    }

    /**
     * @return the default value applied when the key is absent, if any
     */
    public Optional<Object> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    /**
     * @return the description given by {@code @Description} or {@code @Options(description)}, if any
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * @return for a {@link Shape#SECTION section}, the keys read at its level, inline components flattened
     */
    public List<Schema> properties() {
        return List.copyOf(properties);
    }

    /**
     * @return for a section with an inline map, the value of every key no property claims
     */
    public Optional<Schema> remaining() {
        return Optional.ofNullable(remaining);
    }

    /**
     * @return for a section read with a simple key component ({@code {lobby: {port: 1}}}), the component
     *         given by the key; its other properties sit under that key
     */
    public Optional<Schema> keyProperty() {
        return Optional.ofNullable(keyProperty);
    }

    /**
     * @return for a section with a fully inline polymorphic component, that component: its discriminator
     *         and the keys of the selected sub-type sit in this section
     */
    public Optional<Schema> inlinePolymorphic() {
        return Optional.ofNullable(inlinePolymorphic);
    }

    /**
     * @return for a {@link Shape#LIST list}, its elements
     */
    public Optional<Schema> items() {
        return Optional.ofNullable(items);
    }

    /**
     * @return for a {@link Shape#MAP map}, its values
     */
    public Optional<Schema> values() {
        return Optional.ofNullable(values);
    }

    /**
     * @return for a {@link Shape#POLYMORPHIC polymorphic value}, the key holding the sub-type's name
     */
    public Optional<String> discriminator() {
        return Optional.ofNullable(discriminator);
    }

    /**
     * @return for a polymorphic value, where its discriminator is written
     */
    public Optional<Discriminator> discriminatorPlacement() {
        return Optional.ofNullable(placement);
    }

    /**
     * @return for a polymorphic value, each registered sub-type by name, sorted; empty when none is registered yet
     */
    public Map<String, Schema> subtypes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(subtypes));
    }

    /**
     * @return for an {@link Shape#ENUM enum}, the names of its constants
     */
    public List<String> enumValues() {
        return List.copyOf(enumValues);
    }

    /**
     * @return whether this section is a record already being described higher in the tree, whose
     *         properties are therefore not repeated
     */
    public boolean recursive() {
        return recursive;
    }

    /**
     * Renders this schema as a JSON Schema (draft 2020-12), for the autocompletion and validation of
     * YAML files in an editor. Keys that no component reads are flagged; a polymorphic value lists one
     * branch per registered sub-type.
     *
     * @return the JSON Schema, pretty-printed
     */
    public String toJsonSchema() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        if (type instanceof Class<?> clazz) {
            json.put("title", clazz.getSimpleName());
        }
        json.putAll(JsonSchemaWriter.toJson(this));
        return JsonSchemaWriter.write(json);
    }

    @Override
    public String toString() {
        return "Schema{" + (key != null ? key + ": " : "") + shape + " " + type.getTypeName() + "}";
    }
}

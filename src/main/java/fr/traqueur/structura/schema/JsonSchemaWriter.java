package fr.traqueur.structura.schema;

import fr.traqueur.structura.schema.Schema.Discriminator;
import fr.traqueur.structura.schema.Schema.Shape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a {@link Schema} as a JSON Schema, without any JSON library.
 */
final class JsonSchemaWriter {

    private JsonSchemaWriter() {
    }

    static Map<String, Object> toJson(Schema schema) {
        Map<String, Object> json = new LinkedHashMap<>();
        schema.description().ifPresent(description -> json.put("description", description));
        schema.defaultValue().filter(JsonSchemaWriter::isJsonValue).ifPresent(value ->
                json.put("default", value instanceof Enum<?> constant ? constant.name() : value));

        switch (schema.shape()) {
            case SECTION -> json.putAll(section(schema));
            case LIST -> json.putAll(list(schema));
            case MAP -> json.putAll(map(schema));
            case POLYMORPHIC -> json.putAll(polymorphic(schema, schema.discriminatorPlacement().orElse(Discriminator.NESTED)));
            case ENUM -> {
                json.put("type", "string");
                List<String> values = new ArrayList<>();
                for (String value : schema.enumValues) {
                    values.add(value);
                    String lower = value.toLowerCase(Locale.ROOT);
                    if (!lower.equals(value)) values.add(lower);
                }
                json.put("enum", values);
            }
            case TEXT, REFERENCE -> json.put("type", "string");
            case INTEGER -> json.put("type", "integer");
            case NUMBER -> json.put("type", "number");
            case BOOLEAN -> json.put("type", "boolean");
            case DATE -> {
                json.put("type", "string");
                json.put("format", "date");
            }
            case DATE_TIME -> {
                json.put("type", "string");
                json.put("format", "date-time");
            }
            case CUSTOM, ANY -> { /* any value */ }
        }
        return json;
    }

    /**
     * A section: its keys, the required ones, and what happens to the others. A section read with a
     * simple key component is a single-entry object whose key is that component.
     */
    private static Map<String, Object> section(Schema schema) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "object");
        if (schema.recursive) {
            return json;
        }
        Map<String, Object> body = sectionBody(schema);
        if (schema.keyProperty != null) {
            body.remove("type");
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("type", "object");
            inner.putAll(body);
            json.put("additionalProperties", inner);
            json.put("minProperties", 1);
            json.put("maxProperties", 1);
            return json;
        }
        json.putAll(body);
        return json;
    }

    private static Map<String, Object> sectionBody(Schema schema) {
        Map<String, Object> json = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Schema property : schema.properties) {
            properties.put(property.key(), toJson(property));
            if (property.required) {
                required.add(property.key());
            }
        }

        if (schema.inlinePolymorphic != null) {
            // The sub-type's keys sit in this section: one branch per sub-type, each listing them all
            Schema polymorphic = schema.inlinePolymorphic;
            List<Object> branches = new ArrayList<>();
            for (Map.Entry<String, Schema> subtype : polymorphic.subtypes.entrySet()) {
                Map<String, Object> branch = new LinkedHashMap<>();
                Map<String, Object> branchProperties = new LinkedHashMap<>();
                List<String> branchRequired = new ArrayList<>();
                branchProperties.put(polymorphic.discriminator, Map.of("const", subtype.getKey()));
                branchRequired.add(polymorphic.discriminator);
                for (Schema property : subtype.getValue().properties) {
                    branchProperties.put(property.key(), toJson(property));
                    if (property.required) branchRequired.add(property.key());
                }
                branch.put("properties", branchProperties);
                branch.put("required", branchRequired);
                branches.add(branch);
            }
            properties.put(polymorphic.discriminator, Map.of("enum", new ArrayList<>(polymorphic.subtypes.keySet())));
            required.add(polymorphic.discriminator);
            if (!branches.isEmpty()) {
                json.put("oneOf", branches);
            }
        }

        if (!properties.isEmpty()) {
            json.put("properties", properties);
        }
        if (!required.isEmpty()) {
            json.put("required", required);
        }
        if (schema.remaining != null) {
            json.put("additionalProperties", toJson(schema.remaining));
        } else if (schema.inlinePolymorphic == null) {
            json.put("additionalProperties", false);
        }
        return json;
    }

    private static Map<String, Object> list(Schema schema) {
        Map<String, Object> json = new LinkedHashMap<>();
        Schema items = schema.items;
        if (items != null && items.shape() == Shape.POLYMORPHIC && items.placement == Discriminator.KEY) {
            // A list of sub-types written as a section: each key is a sub-type's name
            json.putAll(byName(items));
            return json;
        }
        if (items != null && items.shape() == Shape.SECTION && items.keyProperty != null) {
            // A list of keyed records written as a section: each key is an element's key component
            Map<String, Object> inner = new LinkedHashMap<>(sectionBody(items));
            inner.put("type", "object");
            json.put("type", "object");
            json.put("additionalProperties", inner);
            return json;
        }
        json.put("type", "array");
        if (items != null) {
            json.put("items", toJson(items));
        }
        return json;
    }

    private static Map<String, Object> map(Schema schema) {
        Map<String, Object> json = new LinkedHashMap<>();
        Schema values = schema.values;
        if (values != null && values.shape() == Shape.POLYMORPHIC && values.placement == Discriminator.KEY) {
            json.putAll(byName(values));
            return json;
        }
        json.put("type", "object");
        if (values != null) {
            json.put("additionalProperties", toJson(values));
        }
        return json;
    }

    /**
     * Sub-types keyed by their name ({@code @Polymorphic(useKey = true)}).
     */
    private static Map<String, Object> byName(Schema polymorphic) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        polymorphic.subtypes.forEach((name, subtype) -> properties.put(name, toJson(subtype)));
        json.put("properties", properties);
        json.put("additionalProperties", false);
        return json;
    }

    private static Map<String, Object> polymorphic(Schema schema, Discriminator placement) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", "object");
        if (schema.subtypes.isEmpty()) {
            return json;
        }
        List<Object> branches = new ArrayList<>();
        for (Map.Entry<String, Schema> subtype : schema.subtypes.entrySet()) {
            Map<String, Object> branch = new LinkedHashMap<>(sectionBody(subtype.getValue()));
            if (placement == Discriminator.NESTED) {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (Map<String, Object>) branch.computeIfAbsent("properties",
                        ignored -> new LinkedHashMap<String, Object>());
                Map<String, Object> withDiscriminator = new LinkedHashMap<>();
                withDiscriminator.put(schema.discriminator, Map.of("const", subtype.getKey()));
                withDiscriminator.putAll(properties);
                branch.put("properties", withDiscriminator);
                List<Object> required = new ArrayList<>();
                required.add(schema.discriminator);
                Object existing = branch.get("required");
                if (existing instanceof Collection<?> collection) required.addAll(collection);
                branch.put("required", required);
            }
            branches.add(branch);
        }
        json.put("oneOf", branches);
        return json;
    }

    private static boolean isJsonValue(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, 0);
        return out.append('\n').toString();
    }

    private static void write(Object value, StringBuilder out, int indent) {
        switch (value) {
            case null -> out.append("null");
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    out.append("{}");
                    return;
                }
                out.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.append("  ".repeat(indent + 1));
                    writeString(String.valueOf(entry.getKey()), out);
                    out.append(": ");
                    write(entry.getValue(), out, indent + 1);
                    out.append(++i < map.size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(indent)).append('}');
            }
            case Collection<?> collection -> {
                if (collection.isEmpty()) {
                    out.append("[]");
                    return;
                }
                out.append("[\n");
                int i = 0;
                for (Object item : collection) {
                    out.append("  ".repeat(indent + 1));
                    write(item, out, indent + 1);
                    out.append(++i < collection.size() ? ",\n" : "\n");
                }
                out.append("  ".repeat(indent)).append(']');
            }
            case String string -> writeString(string, out);
            case Number number -> out.append(number);
            case Boolean bool -> out.append(bool);
            default -> writeString(value.toString(), out);
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}

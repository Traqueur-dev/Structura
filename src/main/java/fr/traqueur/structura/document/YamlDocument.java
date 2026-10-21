package fr.traqueur.structura.document;

import fr.traqueur.structura.exceptions.StructuraException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.StringReader;

/**
 * A YAML document read once, keeping both its plain Java form (the {@code Map}s, {@code List}s and
 * scalars the conversion works on) and its node tree, which knows where each key and value sits in
 * the text.
 *
 * <p>The conversion never sees positions: errors carry the path of the faulty key, and the document
 * turns that path back into a line and a column once the error reaches it ({@link #locate}). This
 * keeps positions out of every conversion signature, and works the same for a value the conversion
 * copied or enriched (a polymorphic discriminator added to a map, an inline record reading its
 * parent's node), since only the path matters.</p>
 */
public final class YamlDocument {

    private final String source;
    private final Node root;
    private final Object data;

    private YamlDocument(String source, Node root, Object data) {
        this.source = source;
        this.root = root;
        this.data = data;
    }

    /**
     * Reads a YAML text.
     *
     * @param text   the YAML content
     * @param source the name of the source for error messages (a file name), or null
     * @return the document; its {@link #data()} is null for an empty document
     * @throws StructuraException of kind {@link StructuraException.Kind#SYNTAX} if the text is not valid YAML
     */
    public static YamlDocument parse(String text, String source) {
        try {
            Node root = new Yaml().compose(new StringReader(text));
            Object data = root == null ? null : new NodeConstructor().construct(root);
            return new YamlDocument(source, root, data);
        } catch (MarkedYAMLException e) {
            Mark mark = e.getProblemMark() != null ? e.getProblemMark() : e.getContextMark();
            String problem = e.getProblem() != null ? e.getProblem() : e.getMessage();
            StructuraException error = new StructuraException(StructuraException.Kind.SYNTAX,
                    "invalid YAML: " + problem, null, e);
            if (mark != null) {
                error.locate(source, mark.getLine() + 1, mark.getColumn() + 1);
            }
            throw error;
        } catch (YAMLException e) {
            throw new StructuraException(StructuraException.Kind.SYNTAX, "invalid YAML: " + e.getMessage(), null, e);
        }
    }

    /**
     * @return the document as plain Java values, exactly as {@code new Yaml().load(text)} returns it
     */
    public Object data() {
        return data;
    }

    /**
     * @return the name of the source, or null
     */
    public String source() {
        return source;
    }

    /**
     * Attaches to an error the position its path designates in this document.
     *
     * <p>An unknown key points at the key itself, any other error at the value. When the path leads
     * to a key that is absent (a missing required key), the error points at the closest section that
     * does exist, which is where the key should be added.</p>
     *
     * @param error the error, located only if it has a path and no position yet
     * @return the same error
     */
    public StructuraException locate(StructuraException error) {
        if (root == null || error.line().isPresent()) {
            return error;
        }
        Node node = find(error.path().orElse(""), error.kind() == StructuraException.Kind.UNKNOWN_KEY);
        Mark mark = node.getStartMark();
        return error.locate(source, mark.getLine() + 1, mark.getColumn() + 1);
    }

    /**
     * Walks the node tree along a path such as {@code shop.offers[2].price}. A key may itself contain
     * dots ({@code @Options(name = "a.b")} or a free map key): at each mapping the longest key that
     * matches the rest of the path wins.
     *
     * @param path     the path to follow
     * @param keyNode  whether to return the key node of the last segment rather than its value
     * @return the deepest node reached
     */
    private Node find(String path, boolean keyNode) {
        Node current = root;
        Node lastKey = null;
        String rest = path;

        while (!rest.isEmpty()) {
            Node next = null;
            Node nextKey = null;
            String nextRest = null;

            if (current instanceof MappingNode mapping) {
                int best = -1;
                for (NodeTuple tuple : mapping.getValue()) {
                    if (!(tuple.getKeyNode() instanceof ScalarNode scalar)) {
                        continue;
                    }
                    String key = scalar.getValue();
                    if (key.length() > best && startsWithSegment(rest, key)) {
                        best = key.length();
                        next = tuple.getValueNode();
                        nextKey = scalar;
                        nextRest = rest.substring(key.length());
                    }
                }
            } else if (current instanceof SequenceNode sequence && rest.startsWith("[")) {
                int end = rest.indexOf(']');
                if (end > 0) {
                    try {
                        int index = Integer.parseInt(rest.substring(1, end));
                        if (index >= 0 && index < sequence.getValue().size()) {
                            next = sequence.getValue().get(index);
                            nextKey = next;
                            nextRest = rest.substring(end + 1);
                        }
                    } catch (NumberFormatException ignored) {
                        // not an index: stop here
                    }
                }
            }

            if (next == null) {
                return current;
            }
            current = next;
            lastKey = nextKey;
            rest = nextRest.startsWith(".") ? nextRest.substring(1) : nextRest;
        }

        return keyNode && lastKey != null ? lastKey : current;
    }

    private static boolean startsWithSegment(String rest, String key) {
        if (!rest.startsWith(key)) {
            return false;
        }
        if (rest.length() == key.length()) {
            return true;
        }
        char next = rest.charAt(key.length());
        return next == '.' || next == '[';
    }

    /**
     * The constructor {@code new Yaml()} uses, exposed to build the Java form of an already composed
     * node tree: the document is parsed once, and both forms agree.
     */
    private static final class NodeConstructor extends Constructor {
        NodeConstructor() {
            super(new LoaderOptions());
        }

        Object construct(Node node) {
            return constructDocument(node);
        }
    }
}

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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *
 * <p>Several documents can be {@link #merge merged} into layers (a default shipped in a jar, then the
 * server's file on top): each node remembers the layer it comes from, so an error names the file and
 * the line of the layer that holds the faulty value.</p>
 */
public final class YamlDocument {

    private final String source;
    private final Node root;
    private final Object data;
    /** The layer each node comes from, when the document merges several; empty for a single one. */
    private final Map<Node, String> sources;

    private YamlDocument(String source, Node root, Object data, Map<Node, String> sources) {
        this.source = source;
        this.root = root;
        this.data = data;
        this.sources = sources;
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
            return new YamlDocument(source, root, data, Map.of());
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
     * Merges documents read as layers, the last one on top: sections are merged key by key, at every
     * depth; a list or a single value of an upper layer replaces the one below. Every node keeps the
     * layer it was read from, for error positions.
     *
     * @param layers the documents, from the bottom (defaults) to the top (overrides)
     * @return the merged document
     */
    public static YamlDocument merge(List<YamlDocument> layers) {
        if (layers.size() == 1) {
            return layers.getFirst();
        }
        Map<Node, String> sources = new IdentityHashMap<>();
        Node merged = null;
        String topSource = null;
        for (YamlDocument layer : layers) {
            if (layer.root == null) {
                continue;
            }
            claim(layer.root, layer.source, sources);
            merged = merged == null ? layer.root : mergeNodes(merged, layer.root, sources);
            topSource = layer.source;
        }
        Object data = merged == null ? null : new NodeConstructor().construct(merged);
        return new YamlDocument(topSource, merged, data, sources);
    }

    /**
     * Records the layer of every node of a tree.
     */
    private static void claim(Node node, String source, Map<Node, String> sources) {
        if (sources.putIfAbsent(node, source) != null) {
            return; // an alias already visited
        }
        if (node instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                claim(tuple.getKeyNode(), source, sources);
                claim(tuple.getValueNode(), source, sources);
            }
        } else if (node instanceof SequenceNode sequence) {
            sequence.getValue().forEach(item -> claim(item, source, sources));
        }
    }

    /**
     * Merges an upper node over a lower one: two sections are merged key by key, anything else is
     * replaced by the upper node. A merged section is a new node standing where the upper one stands.
     */
    private static Node mergeNodes(Node lower, Node upper, Map<Node, String> sources) {
        if (!(lower instanceof MappingNode lowerMapping) || !(upper instanceof MappingNode upperMapping)) {
            return upper;
        }
        Map<String, NodeTuple> tuples = new LinkedHashMap<>();
        List<NodeTuple> others = new ArrayList<>();
        for (NodeTuple tuple : lowerMapping.getValue()) {
            put(tuples, others, tuple);
        }
        for (NodeTuple tuple : upperMapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key && tuples.containsKey(key.getValue())) {
                NodeTuple below = tuples.get(key.getValue());
                tuples.put(key.getValue(), new NodeTuple(tuple.getKeyNode(),
                        mergeNodes(below.getValueNode(), tuple.getValueNode(), sources)));
            } else {
                put(tuples, others, tuple);
            }
        }
        List<NodeTuple> value = new ArrayList<>(tuples.values());
        value.addAll(others);
        MappingNode merged = new MappingNode(upperMapping.getTag(), true, value,
                upperMapping.getStartMark(), upperMapping.getEndMark(), upperMapping.getFlowStyle());
        sources.put(merged, sources.get(upperMapping));
        return merged;
    }

    private static void put(Map<String, NodeTuple> tuples, List<NodeTuple> others, NodeTuple tuple) {
        if (tuple.getKeyNode() instanceof ScalarNode key) {
            tuples.put(key.getValue(), tuple);
        } else {
            others.add(tuple);
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
     * <p>An unknown key, or an error {@link StructuraException#pointAtKey() pointing at its key}, points at
     * the key itself, any other error at the value. When the path leads
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
        Node node = find(error.path().orElse(""), error.pointsAtKey());
        Mark mark = node.getStartMark();
        return error.locate(sources.getOrDefault(node, source), mark.getLine() + 1, mark.getColumn() + 1);
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

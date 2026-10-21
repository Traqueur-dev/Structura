package fr.traqueur.structura.writers.serializer;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites an existing YAML text with new values while keeping what a person wrote around them:
 * comments, the order of the keys, the quoting of an unchanged value, the indentation of lists.
 *
 * <p>The existing text is composed into a node tree with its comments; the new data is merged into
 * that tree key by key — a value that did not change keeps its node as written, a changed value takes
 * the comments of the one it replaces, a new key goes after the existing ones, a key absent from the
 * data is removed — and the tree is written back.</p>
 *
 * <p>Known limits, from SnakeYAML's comment support: the comments attached to a removed key go with it;
 * the spaces before a comment at the end of a line are reduced to one; blank lines are kept where they
 * precede a comment line or a key.</p>
 */
public final class DocumentUpdater {

    private static final Pattern INDENTED_SEQUENCE = Pattern.compile("(?m)^(\\s*)[^\\s#-][^\\n]*:\\s*\\n(\\s*)- ");

    /**
     * Rewrites a text with new data.
     *
     * @param existing the current text, or null / blank when there is none yet
     * @param data the new content, as plain YAML values (maps, lists, scalars)
     * @return the new text
     */
    public String update(String existing, Map<String, Object> data) {
        Yaml yaml = yaml(existing);
        if (existing == null || existing.isBlank()) {
            return data.isEmpty() ? "" : yaml.dump(data);
        }
        Node old = yaml.compose(new StringReader(existing));
        Node merged = old == null ? yaml.represent(data) : merge(old, data, yaml);
        if (merged instanceof MappingNode mapping && mapping.getValue().isEmpty() && !hasComments(mapping)) {
            return "";
        }
        StringWriter out = new StringWriter();
        yaml.serialize(merged, out);
        return out.toString();
    }

    private Node merge(Node old, Object value, Yaml yaml) {
        if (value instanceof Map<?, ?> map && old instanceof MappingNode mapping) {
            return mergeMapping(mapping, map, yaml);
        }
        if (value instanceof List<?> list && old instanceof SequenceNode sequence) {
            return mergeSequence(sequence, list, yaml);
        }
        Node fresh = yaml.represent(value);
        if (old instanceof ScalarNode oldScalar && fresh instanceof ScalarNode freshScalar) {
            if (sameScalar(oldScalar, freshScalar)) {
                return old; // unchanged: keep it exactly as written, quotes included
            }
            if (freshScalar.getTag().equals(Tag.STR) && oldScalar.getTag().equals(Tag.STR)) {
                fresh = new ScalarNode(Tag.STR, freshScalar.getValue(), null, null, oldScalar.getScalarStyle());
            }
        }
        copyComments(old, fresh);
        return fresh;
    }

    private Node mergeMapping(MappingNode old, Map<?, ?> data, Yaml yaml) {
        Map<String, Object> remaining = new LinkedHashMap<>();
        data.forEach((key, value) -> remaining.put(String.valueOf(key), value));

        List<NodeTuple> tuples = new ArrayList<>();
        for (NodeTuple tuple : old.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode key) || !remaining.containsKey(key.getValue())) {
                continue; // removed: the key is no longer part of the data
            }
            Object value = remaining.remove(key.getValue());
            tuples.add(new NodeTuple(tuple.getKeyNode(), merge(tuple.getValueNode(), value, yaml)));
        }
        if (!remaining.isEmpty()) {
            // New keys go after the existing ones, in the order of the data
            MappingNode added = (MappingNode) yaml.represent(remaining);
            tuples.addAll(added.getValue());
        }

        MappingNode merged = new MappingNode(old.getTag(), true, tuples, old.getStartMark(), old.getEndMark(),
                old.getFlowStyle());
        copyComments(old, merged);
        return merged;
    }

    private Node mergeSequence(SequenceNode old, List<?> data, Yaml yaml) {
        List<Node> items = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            items.add(i < old.getValue().size()
                    ? merge(old.getValue().get(i), data.get(i), yaml)
                    : yaml.represent(data.get(i)));
        }
        SequenceNode merged = new SequenceNode(old.getTag(), true, items, old.getStartMark(), old.getEndMark(),
                old.getFlowStyle());
        copyComments(old, merged);
        return merged;
    }

    private static boolean sameScalar(ScalarNode old, ScalarNode fresh) {
        if (!Objects.equals(old.getValue(), fresh.getValue())) {
            return false;
        }
        // "5" written as text is not the number 5
        return old.getTag().equals(fresh.getTag()) || old.getTag().equals(Tag.STR) == fresh.getTag().equals(Tag.STR);
    }

    private static void copyComments(Node from, Node to) {
        to.setBlockComments(from.getBlockComments());
        to.setInLineComments(from.getInLineComments());
        to.setEndComments(from.getEndComments());
    }

    private static boolean hasComments(Node node) {
        return notEmpty(node.getBlockComments()) || notEmpty(node.getInLineComments()) || notEmpty(node.getEndComments());
    }

    private static boolean notEmpty(List<CommentLine> comments) {
        return comments != null && !comments.isEmpty();
    }

    /**
     * A YAML instance that keeps comments, writing lists indented the way the existing text does.
     */
    private static Yaml yaml(String existing) {
        LoaderOptions loader = new LoaderOptions();
        loader.setProcessComments(true);
        DumperOptions dumper = new DumperOptions();
        dumper.setProcessComments(true);
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setIndent(2);
        int sequenceIndent = sequenceIndent(existing);
        if (sequenceIndent > 0) {
            dumper.setIndentWithIndicator(true);
            dumper.setIndicatorIndent(Math.min(sequenceIndent, 2));
        }
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    /**
     * How far the items of a list are indented under their key in the existing text: {@code 0} for
     * {@code key:\n- item}, {@code 2} for {@code key:\n  - item}.
     */
    private static int sequenceIndent(String existing) {
        if (existing == null) {
            return 0;
        }
        Matcher matcher = INDENTED_SEQUENCE.matcher(existing);
        return matcher.find() ? Math.max(0, matcher.group(2).length() - matcher.group(1).length()) : 0;
    }
}

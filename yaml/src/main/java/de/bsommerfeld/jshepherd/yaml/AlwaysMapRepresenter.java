package de.bsommerfeld.jshepherd.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;
import java.util.Set;

/**
 * Custom SnakeYAML Representer that ensures all JavaBeans are represented as YAML maps
 * (i.e., uses the generic Tag.MAP) to avoid emitting global tags with fully qualified
 * class names. This keeps the YAML clean and independent of Java type names.
 */
class AlwaysMapRepresenter extends Representer {

    AlwaysMapRepresenter(DumperOptions options) {
        super(options);
        // Be lenient with unknown properties when introspecting beans
        this.getPropertyUtils().setSkipMissingProperties(true);

        // Enums with instance fields are treated as JavaBeans by default,
        // resulting in mapping output. Registering an explicit representer
        // forces them to be written as plain scalar strings.
        this.multiRepresenters.put(Enum.class, data -> representScalar(
                Tag.STR, ((Enum<?>) data).name()));

        // SnakeYAML has no built-in java.time support — without these, a
        // LocalDate field would be dumped as an (empty) JavaBean mapping.
        this.representers.put(java.time.LocalDate.class, data -> representScalar(
                Tag.STR, data.toString()));
        this.representers.put(java.time.LocalDateTime.class, data -> representScalar(
                Tag.STR, data.toString()));
    }

    @Override
    protected Node representSequence(Tag tag, Iterable<?> sequence, DumperOptions.FlowStyle flowStyle) {
        Node node = super.representSequence(tag, sequence, flowStyle);
        if (node instanceof SequenceNode seq && seq.getValue().isEmpty()) {
            forgetIdentity(sequence);
        }
        return node;
    }

    @Override
    protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {
        Node node = super.representMapping(tag, mapping, flowStyle);
        if (mapping.isEmpty()) {
            forgetIdentity(mapping);
        }
        return node;
    }

    /**
     * Drops {@code value} from the identity map SnakeYAML uses to emit anchors
     * and aliases. {@code List.of()} and {@code Map.of()} hand out shared
     * singletons, so several empty collection fields are literally the same
     * object and the second one would be written as {@code *id001} pointing at
     * an {@code &id001 []} elsewhere in the file. An empty collection cannot be
     * cyclic, so nothing is lost by representing each occurrence on its own.
     */
    private void forgetIdentity(Object value) {
        this.representedObjects.remove(value);
    }

    @Override
    protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
        MappingNode node = super.representJavaBean(properties, javaBean);
        // Force the tag of any represented JavaBean to be a generic map
        node.setTag(Tag.MAP);
        return node;
    }
}

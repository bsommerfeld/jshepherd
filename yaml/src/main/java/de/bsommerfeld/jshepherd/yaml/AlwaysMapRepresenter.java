package de.bsommerfeld.jshepherd.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

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
    }

    @Override
    protected MappingNode representJavaBean(Set<Property> properties, Object javaBean) {
        MappingNode node = super.representJavaBean(properties, javaBean);
        // Force the tag of any represented JavaBean to be a generic map
        node.setTag(Tag.MAP);
        return node;
    }
}

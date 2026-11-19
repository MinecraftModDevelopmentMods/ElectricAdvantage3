package zone.moddev.mc.electricadvantage.processing;

import net.minecraft.resources.ResourceLocation;

/** IDs for recipes a generator copies from another recipe. */
final class GeneratedIds {
    private GeneratedIds() {}

    /** {@code <type namespace>:generated/<type path>/<source namespace>/<source path>}. */
    static ResourceLocation copy(ResourceLocation type, ResourceLocation source) {
        return ResourceLocation.fromNamespaceAndPath(type.getNamespace(),
            "generated/" + type.getPath() + "/" + source.getNamespace() + "/" + source.getPath());
    }
}

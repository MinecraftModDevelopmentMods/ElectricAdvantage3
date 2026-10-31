package zone.moddev.mc.electricadvantage.items;

import com.mojang.serialization.Codec;
import zone.moddev.mc.lib.api.ItemContext;
import zone.moddev.mc.lib.api.ItemImplementation;
import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.common.types.MMDMaterial;

import java.util.Optional;

/** One factory for standalone batteries and batteries selected by a material. */
public final class BatteryImplementation implements ItemImplementation<BatteryItem> {
    private static final Codec<Optional<Integer>> CAPACITY = Codec.intRange(1, Integer.MAX_VALUE)
            .optionalFieldOf("capacity").codec();

    @Override public Class<BatteryItem> itemClass() { return BatteryItem.class; }

    @Override public BatteryItem create(MMDMaterial material, MMDItem definition) {
        throw new UnsupportedOperationException("Battery creation requires an ItemContext");
    }

    @Override public BatteryItem create(ItemContext context) {
        int capacity = context.factoryData().decode(CAPACITY).getOrThrow()
                .orElseGet(() -> materialCapacity(context.material()));
        return new BatteryItem(context.properties().stacksTo(1), capacity);
    }

    private static int materialCapacity(MMDMaterial material) {
        var value = material.properties().special().get("battery_capacity");
        if (value == null || !Double.isFinite(value) || value < 1
                || value > Integer.MAX_VALUE || value != Math.rint(value)) {
            throw new IllegalArgumentException("Battery requires factory_data.capacity or a positive integer material battery_capacity");
        }
        return value.intValue();
    }
}

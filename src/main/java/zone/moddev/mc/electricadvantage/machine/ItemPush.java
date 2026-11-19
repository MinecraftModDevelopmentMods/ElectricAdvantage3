package zone.moddev.mc.electricadvantage.machine;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;

/** Pushes items from a machine's item group into the inventory on one side. */
public final class ItemPush {
    private ItemPush() {}

    /** Moves up to {@code limit} items from {@code group} into the neighbour on {@code side}; returns how many moved. */
    public static int push(ProcessorMachine host, String group, Direction side, int limit) {
        var level = host.level();
        var next = host.position().relative(side);
        if (limit <= 0 || !level.isLoaded(next)) return 0;
        var target = level.getCapability(Capabilities.ItemHandler.BLOCK, next, side.getOpposite());
        if (target == null) return 0;
        var items = host.resources().items(group);
        int moved = 0;
        for (int slot = 0; slot < items.size() && moved < limit; slot++) {
            var stack = items.peek(slot);
            if (stack.isEmpty()) continue;
            var offer = stack.copyWithCount(Math.min(stack.getCount(), limit - moved));
            int fits = offer.getCount() - ItemHandlerHelper.insertItem(target, offer, true).getCount();
            if (fits <= 0) continue;
            try (var tx = host.resources().begin()) {
                int taken = items.extract(slot, fits, tx);
                var left = ItemHandlerHelper.insertItem(target, offer.copyWithCount(taken), false);
                if (!left.isEmpty()) items.insert(slot, left, tx);
                tx.commit();
                moved += taken - left.getCount();
            }
        }
        return moved;
    }
}

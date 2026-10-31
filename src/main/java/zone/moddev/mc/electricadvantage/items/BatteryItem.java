package zone.moddev.mc.electricadvantage.items;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.List;

/** Shared presentation and capability access for data-defined batteries. */
public final class BatteryItem extends Item {
    private final int capacity;

    public BatteryItem(Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    public IEnergyStorage storage(ItemStack stack) {
        return new StackEnergyStorage(stack, capacity);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.electricadvantage.battery.charge",
                100L * storage(stack).getEnergyStored() / capacity, storage(stack).getEnergyStored(), capacity));
    }
}

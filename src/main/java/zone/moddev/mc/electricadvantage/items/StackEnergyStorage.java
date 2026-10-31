package zone.moddev.mc.electricadvantage.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;

/** Stack persistence adapter around MMDLib's shared energy transfer implementation. */
final class StackEnergyStorage implements IEnergyStorage {
    private static final String ENERGY_KEY = "electricadvantage:energy";
    private final ItemStack stack;
    private final int capacity;

    StackEnergyStorage(ItemStack stack, int capacity) {
        this.stack = stack;
        this.capacity = capacity;
    }

    @Override public int getEnergyStored() {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains(ENERGY_KEY) ? Math.clamp(tag.getInt(ENERGY_KEY), 0, capacity) : capacity;
    }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return true; }
    @Override public int receiveEnergy(int amount, boolean simulate) { return transfer(amount, simulate, true); }
    @Override public int extractEnergy(int amount, boolean simulate) { return transfer(amount, simulate, false); }

    private int transfer(int amount, boolean simulate, boolean receiving) {
        // Read the stack for every operation: multiple capability handles must never
        // retain independent cached charge values for the same stack.
        var buffer = new EnergyBuffer(capacity, capacity);
        buffer.setStored(getEnergyStored());
        int moved = receiving ? buffer.receiveEnergy(amount, simulate) : buffer.extractEnergy(amount, simulate);
        if (!simulate && moved > 0) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putInt(ENERGY_KEY, buffer.getEnergyStored()));
        }
        return moved;
    }
}

package zone.moddev.mc.electricadvantage.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricBlock;
import zone.moddev.mc.electricadvantage.infrastructure.ElectricNetworks;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.feature.ProcessorEnergy;
import zone.moddev.mc.lib.machine.feature.ProcessorMachine;
import zone.moddev.mc.lib.machine.feature.ProcessorState;
import zone.moddev.mc.lib.machine.feature.StorageAdmission;
import zone.moddev.mc.lib.machine.feature.StoredProcessor;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/**
 * The battery array: its energy store is the charge of the batteries in its eight slots, so a battery carries its
 * charge in and out. It is a backup store: the electric network charges it last and never from another array, and once
 * per tick, after everything else has run, it feeds loads through neighbouring conduits and machines.
 *
 * <p>A redstone signal stops it giving energy out; it still charges.
 */
public final class BatteryArrayProcessor extends StoredProcessor
        implements ProcessorEnergy, StorageAdmission, zone.moddev.mc.lib.api.redstone.IRedstoneOutput {
    public static final int RATE = 4096;
    private static final String BATTERIES = "batteries";
    private BatteryBuffer buffer;

    /** Whether the block at {@code pos} is a battery array, which the electric network treats as backup. */
    public static boolean isBackup(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
                && machine.features.findProcessor(BATTERIES, BatteryArrayProcessor.class).isPresent();
    }

    @Override
    public EnergyBuffer createEnergyStorage(long capacity, long inputRate, long outputRate) {
        if (buffer != null) throw new IllegalStateException("Battery array energy is already bound");
        return buffer = new BatteryBuffer(inputRate, outputRate);
    }

    /** Charging on every side; nothing out while redstone stops the array. */
    @Override
    public IoMode energyMode(@Nullable Direction side) {
        return executionStatus() == ProcessState.HELD || paused() ? IoMode.INPUT : IoMode.BOTH;
    }

    @Override
    public java.util.List<zone.moddev.mc.lib.api.redstone.RedstoneOutput> redstoneOutputs() {
        return java.util.List.of(new zone.moddev.mc.lib.api.redstone.RedstoneOutput("batteries/charge",
                net.minecraft.network.chat.Component.translatable("screen.electricadvantage.battery_array.charge"),
                net.minecraft.network.chat.Component.translatable("screen.electricadvantage.battery_array.charge.description"),
                0xD85B5B, java.util.Optional.empty(),
                () -> buffer == null ? 0 : zone.moddev.mc.lib.api.redstone.RedstoneControl.level(
                        buffer.stored() / (double) Math.max(1, buffer.capacity()))));
    }

    @Override
    public boolean acceptsItem(ProcessorMachine host, String member, ItemStack stack) {
        return stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }

    @Override
    public boolean canStart(ProcessorState state, ProcessorMachine host) { return true; }

    @Override
    public ProcessState process(ProcessorState state, ProcessorMachine host, int ticks) {
        if (host.level() instanceof ServerLevel level) GenerationSystems.queueBackup(level, host.position());
        return buffer.stored() > 0 ? ProcessState.RUNNING : ProcessState.IDLE;
    }

    /** Feeds neighbours from the batteries; run at the end of the level tick. */
    void discharge(ServerLevel level) {
        if (machine == null || executionStatus() == ProcessState.HELD || paused()) return;
        var pos = machine.position();
        int remaining = RATE;
        for (int offset = 0; offset < 6 && remaining > 0; offset++) {
            var direction = Direction.values()[(int) ((level.getGameTime() + offset) % 6)];
            var next = pos.relative(direction);
            if (!level.hasChunkAt(next)) continue;
            var state = level.getBlockState(next);
            if (state.getBlock() instanceof ElectricBlock block && block.conductor(state)) {
                remaining -= ElectricNetworks.get(level).sendBackup(next, pos, buffer, remaining);
            } else if (!isBackup(level, next)) {
                var target = level.getCapability(Capabilities.EnergyStorage.BLOCK, next, direction.getOpposite());
                if (target == null || !target.canReceive()) continue;
                int offered = buffer.extractEnergy(remaining, true);
                int accepted = Math.clamp(target.receiveEnergy(offered, false), 0, offered);
                buffer.extractEnergy(accepted, false);
                remaining -= accepted;
            }
        }
    }

    private @Nullable ItemStorage batteries() {
        return machine instanceof DefinedMachineEntity entity ? entity.items.inventory(BATTERIES) : null;
    }

    /** An energy store whose charge lives on the batteries; nothing of its own is saved. */
    private final class BatteryBuffer extends EnergyBuffer {
        BatteryBuffer(long inputRate, long outputRate) { super(0, inputRate, outputRate); }

        private long total(boolean capacity) {
            var slots = batteries();
            long total = 0;
            if (slots != null) for (int slot = 0; slot < slots.getSlots(); slot++) {
                var handler = slots.getStackInSlot(slot).getCapability(Capabilities.EnergyStorage.ITEM);
                if (handler != null) total += capacity ? handler.getMaxEnergyStored() : handler.getEnergyStored();
            }
            return total;
        }

        /** Moves up to {@code amount} into or out of the batteries in slot order, writing back changed stacks. */
        private long transfer(long amount, boolean simulate, boolean receiving) {
            var slots = batteries();
            long remaining = Math.max(0, amount);
            if (slots == null) return 0;
            for (int slot = 0; slot < slots.getSlots() && remaining > 0; slot++) {
                var copy = slots.getStackInSlot(slot).copy();
                IEnergyStorage handler = copy.getCapability(Capabilities.EnergyStorage.ITEM);
                if (handler == null) continue;
                int request = (int) Math.min(remaining, Integer.MAX_VALUE);
                int moved = Math.clamp(receiving ? handler.receiveEnergy(request, simulate) : handler.extractEnergy(request, simulate), 0, request);
                if (moved > 0 && !simulate) slots.setStackInSlot(slot, copy);
                remaining -= moved;
            }
            long moved = Math.max(0, amount) - remaining;
            if (moved > 0 && !simulate) changes().changed();
            return moved;
        }

        @Override public long stored() { return total(false); }
        @Override public long capacity() { return total(true); }
        @Override public long space() { return capacity() - stored(); }
        @Override public int getEnergyStored() { return (int) Math.min(stored(), Integer.MAX_VALUE); }
        @Override public int getMaxEnergyStored() { return (int) Math.min(capacity(), Integer.MAX_VALUE); }
        @Override public long insert(long amount, boolean simulate) { return transfer(amount, simulate, true); }
        @Override public long extract(long amount, boolean simulate) { return transfer(amount, simulate, false); }
        @Override public long refund(long amount) { return transfer(amount, false, true); }

        @Override public boolean consume(long amount) { return reserve(amount); }
        @Override public boolean reserve(long amount) {
            if (amount < 0 || amount > stored()) return false;
            return transfer(amount, false, false) == amount;
        }

        @Override public void setStored(long energy) {
            long current = stored(), target = Math.clamp(energy, 0, capacity());
            if (target > current) transfer(target - current, false, true);
            else transfer(current - target, false, false);
        }

        /** Capacity is the batteries'; capacitor upgrades do not apply. */
        @Override public void setCapacity(long capacity) {}

        /** Restores the battery stacks, for transaction rollback. */
        @Override public Runnable checkpoint() {
            var slots = batteries();
            if (slots == null) return () -> {};
            var saved = new ItemStack[slots.getSlots()];
            for (int slot = 0; slot < saved.length; slot++) saved[slot] = slots.getStackInSlot(slot).copy();
            return () -> {
                for (int slot = 0; slot < saved.length; slot++)
                    if (!ItemStack.matches(slots.getStackInSlot(slot), saved[slot])) slots.setStackInSlot(slot, saved[slot].copy());
                changes().changed();
            };
        }

        /** The charge is saved with the batteries. */
        @Override public Tag serializeNBT(HolderLookup.Provider provider) { return LongTag.valueOf(0); }
        @Override public void deserializeNBT(HolderLookup.Provider provider, Tag tag) {}
    }
}

package zone.moddev.mc.electricadvantage.farming;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.electricadvantage.machine.ElectricMachineEntity;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * The growth chamber Control Unit. {@link GreenhouseControllerProcessor} makes and shares greenhouse power; this holds
 * the fertilizer and greenhouse pools under 3.0's keys ({@code Fertilizer}, {@code Greenhouse}) and takes in
 * fertilizer every tick, as 3.0 did, even while redstone stops conversion.
 */
public final class GreenhouseControllerEntity extends ElectricMachineEntity {
    public static final int FERTILIZER_CAPACITY = 2000, BUFFER = 1000;
    long fertilizer, greenhouse;
    final List<BlockPos> chambers = new ArrayList<>();
    long nextScan = Long.MIN_VALUE;
    int rotation;

    public GreenhouseControllerEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
    }

    public long fertilizer() { return fertilizer; }
    public long greenhouse() { return greenhouse; }
    public void setGreenhouse(long amount) { greenhouse = Math.clamp(amount, 0, BUFFER); setChanged(); }
    public List<BlockPos> chambers() { return List.copyOf(chambers); }

    @Override
    protected void beforeMachineTick() {
        super.beforeMachineTick();
        refuel();
    }

    /** Takes one fertilizer item whenever its whole value fits in the pool. */
    private void refuel() {
        var slot = items.inventory("fertilizer");
        var stack = slot.getStackInSlot(0);
        int value = FarmingSystems.fertilizer(stack);
        if (value > 0 && fertilizer + value <= Math.max(FERTILIZER_CAPACITY, value)) {
            fertilizer += value;
            slot.setStackInSlot(0, stack.copyWithCount(stack.getCount() - 1));
            setChanged();
        }
    }

    @Override
    protected void saveMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveMachineData(tag, registries);
        tag.putLong("Fertilizer", fertilizer);
        tag.putLong("Greenhouse", greenhouse);
    }

    @Override
    protected void loadMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadMachineData(tag, registries);
        fertilizer = Math.max(0, tag.getLong("Fertilizer"));
        greenhouse = Math.clamp(tag.getLong("Greenhouse"), 0, BUFFER);
        nextScan = Long.MIN_VALUE;
    }
}

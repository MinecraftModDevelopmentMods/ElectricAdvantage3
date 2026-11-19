package zone.moddev.mc.electricadvantage.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Block entity of every Electric Advantage XML machine. The machine XML supplies storage, processors and screen; this
 * class adds two things MMDLib leaves to the mod.
 *
 * <ul>
 * <li><b>Redstone.</b> As in 3.0, a machine stops while any face receives a signal. New machines start with every face
 * set to read redstone (pause on 1-15); players can change that in the redstone drawer.</li>
 * <li><b>Saves from 3.0.</b> 3.0 saved the energy buffer as {@code Energy} and had no redstone settings. Those saves
 * load with the buffer and with every face reading redstone.</li>
 * </ul>
 */
public class ElectricMachineEntity extends DefinedMachineEntity {
    /** MMDLib's legacy redstone mode that reads every face and pauses on any signal. */
    private static final int PAUSE_ON_SIGNAL = 2;

    public ElectricMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
        var redstone = screenRedstone();
        if (redstone != null)
            for (int face = 0; face < 6; face++) redstone.mode(face, RedstoneControl.Mode.READ);
    }

    @Override
    protected CompoundTag migrateMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        boolean energy = !tag.contains("energy") && tag.contains("Energy", Tag.TAG_ANY_NUMERIC);
        boolean redstone = !tag.contains("redstone_control") && !tag.contains("redstone_mode");
        if (!energy && !redstone) return tag;
        var converted = tag.copy();
        if (energy) converted.putLong("energy", tag.getLong("Energy"));
        if (redstone) converted.putInt("redstone_mode", PAUSE_ON_SIGNAL);
        return converted;
    }
}

package zone.moddev.mc.electricadvantage.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.electricadvantage.machine.ElectricMachineEntity;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** The hydroelectric generator, with the turbine's client-side spin for {@code HydroturbineRenderer}. */
public final class HydroGeneratorEntity extends ElectricMachineEntity {
    public float rotorAngle, previousRotorAngle, rotorYaw;

    public HydroGeneratorEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
    }

    /** Whether the turbine is generating, from the processor state synced to clients. */
    public boolean spinning() {
        return features.findProcessor("hydro", HydroProcessor.class)
                .map(hydro -> hydro.executionStatus() == ProcessState.RUNNING).orElse(false);
    }

    @Override
    public void clientTick() {
        super.clientTick();
        var flow = HydroProcessor.waterFlow(level, worldPosition);
        if (flow.horizontalDistanceSqr() > 0.00001) rotorYaw = (float) Math.atan2(-flow.z, flow.x) - (float) Math.PI / 2;
        previousRotorAngle = rotorAngle;
        if (spinning()) rotorAngle = (rotorAngle + 30) % 360;
    }
}

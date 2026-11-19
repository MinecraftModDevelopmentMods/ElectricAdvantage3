package zone.moddev.mc.electricadvantage.drill;

import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.electricadvantage.machine.ElectricMachineEntity;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.Processor.ProcessState;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * The electric drill. Redstone holds its processor; this drops the target then, as in 3.0. It also makes the moves the
 * processor asks for once its tick is over: the drill, with its contents and state, takes the scaffold's place, a
 * cable is left where it was and a steel frame in front of that (EA2 overwrites whatever is there, bedrock included).
 */
public final class DrillEntity extends ElectricMachineEntity {
    private static final Supplier<Block> TRACK = block("electricadvantage:electric_track"),
            CABLE = block("electricadvantage:electric_conduit"), FRAME = block("poweradvantage:steel_frame");

    public DrillEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineDefinition definition,
            MachineRecipeType recipes) {
        super(type, pos, state, definition, recipes);
    }

    private static Supplier<Block> block(String id) {
        return Suppliers.memoize(() -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)));
    }

    static boolean isTrack(BlockState state) { return state.is(TRACK.get()); }

    public DrillProcessor drill() { return features.findProcessor("drill", DrillProcessor.class).orElseThrow(); }

    @Override
    protected void afterMachineTick() {
        super.afterMachineTick();
        var drill = drill();
        if (drill.executionStatus() == ProcessState.HELD && drill.drillStatus() != DrillProcessor.Status.DISABLED) drill.standDown();
        var way = drill.takeMove();
        if (way == null) return;
        var registries = level.registryAccess();
        var data = saveWithoutMetadata(registries);
        var facing = RelativeFace.facing(getBlockState());
        var next = worldPosition.relative(way);
        level.setBlock(next, getBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(next) instanceof DrillEntity moved)) return;
        moved.loadWithComponents(data, registries);
        moved.energy.consume(DrillProcessor.MOVE_COST);
        // The contents have moved; empty this drill so removing it drops nothing.
        for (int slot = 0; slot < items.getSlots(); slot++) items.setStackInSlot(slot, ItemStack.EMPTY);
        level.setBlock(worldPosition.relative(facing), FRAME.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(worldPosition, CABLE.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}

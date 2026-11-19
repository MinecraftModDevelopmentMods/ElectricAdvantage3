package zone.moddev.mc.electricadvantage.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Generated pump shaft. Loot is intentionally empty; removal clears its loaded lower column. A shaft hangs from the
 * Electric Pump: once the block above is neither the pump nor more shaft, the top segment goes, taking the rest.
 */
public final class PumpPipeBlock extends Block {
    private static final ResourceLocation PUMP = ResourceLocation.fromNamespaceAndPath("electricadvantage", "electric_pump");
    public PumpPipeBlock(Properties properties) { super(properties.noOcclusion()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(4, 0, 4, 12, 16, 12);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !neighbour.is(this) && !BuiltInRegistries.BLOCK.getKey(neighbour.getBlock()).equals(PUMP))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        super.onRemove(state, level, pos, replacement, moving);
        if (!level.isClientSide && replacement.getBlock() != this) {
            // Bottom-up removal prevents recursive onRemove calls along long columns.
            var bottom = pos.below();
            while (bottom.getY() >= level.getMinBuildHeight() && level.hasChunkAt(bottom)
                    && level.getBlockState(bottom).is(this)) bottom = bottom.below();
            for (var cursor = bottom.above(); cursor.getY() < pos.getY(); cursor = cursor.above()) {
                level.removeBlock(cursor, false);
            }
        }
    }
}

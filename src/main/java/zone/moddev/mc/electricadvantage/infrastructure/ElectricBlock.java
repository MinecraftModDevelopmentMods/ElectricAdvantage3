package zone.moddev.mc.electricadvantage.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

public final class ElectricBlock extends MaterialEntityBlock<ElectricBlockEntity> {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final java.util.Map<Direction, BooleanProperty> CONNECTIONS = java.util.Map.of(
            Direction.DOWN, BlockStateProperties.DOWN, Direction.UP, BlockStateProperties.UP,
            Direction.NORTH, BlockStateProperties.NORTH, Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.EAST, BlockStateProperties.EAST, Direction.WEST, BlockStateProperties.WEST);
    private final String kind;

    public ElectricBlock(Properties properties, BlockEntityBinding<ElectricBlockEntity> binding, String kind) {
        super(properties.noOcclusion().lightLevel(state -> state.getValue(LIT) ? 15 : 0), binding.prepared(), binding.type());
        this.kind = kind;
        var state = stateDefinition.any().setValue(ACTIVE, false).setValue(POWERED, false).setValue(LIT, false);
        for (var property : CONNECTIONS.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }
    public boolean led() { return kind.equals("led"); }
    public boolean enabled(BlockState state) { return !kind.equals("switch") || state.getValue(ACTIVE); }
    public boolean conductor(BlockState state) { return !led() && enabled(state); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, POWERED, LIT);
        CONNECTIONS.values().forEach(builder::add);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (kind.equals("scaffold") || kind.equals("switch")) return Shapes.block();
        VoxelShape shape = Block.box(6, 6, 6, 10, 10, 10);
        boolean connected = CONNECTIONS.values().stream().anyMatch(state::getValue);
        for (Direction direction : Direction.values()) {
            if (!state.getValue(CONNECTIONS.get(direction)) && (connected || direction.getAxis() == Direction.Axis.Y)) continue;
            shape = Shapes.or(shape, switch (direction) {
                case DOWN -> Block.box(6, 0, 6, 10, 6, 10);
                case UP -> Block.box(6, 10, 6, 10, 16, 10);
                case NORTH -> Block.box(6, 6, 0, 10, 10, 6);
                case SOUTH -> Block.box(6, 6, 10, 10, 10, 16);
                case WEST -> Block.box(0, 6, 6, 6, 10, 10);
                case EAST -> Block.box(10, 6, 6, 16, 10, 10);
            });
        }
        return shape;
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel server && old.getBlock() != this) {
            updateSignal(server, pos, state);
            ElectricNetworks.get(server).changed(pos);
            refresh(server, pos);
        }
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        super.onRemove(state, level, pos, replacement, moving);
        if (level instanceof ServerLevel server && replacement.getBlock() != this) ElectricNetworks.get(server).changed(pos);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level instanceof ServerLevel server) {
            updateSignal(server, pos, state);
            ElectricNetworks.get(server).neighbourChanged(pos, from);
            refresh(server, pos);
        }
    }
    private void updateSignal(ServerLevel level, BlockPos pos, BlockState state) {
        if (!kind.equals("switch")) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered).setValue(ACTIVE, powered), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            ElectricNetworks.get(level).changed(pos);
        }
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!kind.equals("switch")) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            level.setBlock(pos, state.cycle(ACTIVE), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            ElectricNetworks.get(server).changed(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    public static void refresh(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ElectricBlock)) return;
        var updated = state;
        for (Direction direction : Direction.values()) {
            var next = pos.relative(direction);
            boolean connection = false;
            if (level.hasChunkAt(next)) {
                connection = level.getBlockState(next).getBlock() instanceof ElectricBlock
                        || level.getCapability(Capabilities.EnergyStorage.BLOCK, next, direction.getOpposite()) != null;
            }
            updated = updated.setValue(CONNECTIONS.get(direction), connection);
        }
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }
}

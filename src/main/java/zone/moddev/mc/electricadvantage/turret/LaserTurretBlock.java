package zone.moddev.mc.electricadvantage.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

/** Base plate and shaft are the block model; the rotating head and beam are drawn by the renderer. */
public final class LaserTurretBlock extends MaterialEntityBlock<LaserTurretBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0,0,0,16,4,16), Block.box(4,4,4,12,16,12));
    public LaserTurretBlock(Properties properties, BlockEntityBinding<LaserTurretBlockEntity> binding) {
        super(properties.noOcclusion(),binding.prepared(),binding.type());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(POWERED,false).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ACTIVE,FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide&&placer instanceof Player player&&level.getBlockEntity(pos) instanceof LaserTurretBlockEntity be)be.setOwner(player);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){
        return level.getBlockEntity(pos) instanceof LaserTurretBlockEntity be?be.comparator():0;
    }
}

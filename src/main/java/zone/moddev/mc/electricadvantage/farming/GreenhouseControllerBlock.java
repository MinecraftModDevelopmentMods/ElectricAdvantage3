package zone.moddev.mc.electricadvantage.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

public final class GreenhouseControllerBlock extends MaterialEntityBlock<GreenhouseControllerBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public GreenhouseControllerBlock(Properties properties, BlockEntityBinding<GreenhouseControllerBlockEntity> binding) {
        super(properties,binding.prepared(),binding.type());
        registerDefaultState(stateDefinition.any().setValue(POWERED,false).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){
        return level.getBlockEntity(pos) instanceof GreenhouseControllerBlockEntity be?be.comparator():0;
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,BlockPos from,boolean moving){
        if(level.getBlockEntity(pos) instanceof GreenhouseControllerBlockEntity be)be.rescan();
        super.neighborChanged(state,level,pos,block,from,moving);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide && state.getBlock()!=next.getBlock() && level.getBlockEntity(pos) instanceof GreenhouseControllerBlockEntity be) be.items.dropAll(level,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}

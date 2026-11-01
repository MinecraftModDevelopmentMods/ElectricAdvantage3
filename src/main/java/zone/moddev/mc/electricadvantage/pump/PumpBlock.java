package zone.moddev.mc.electricadvantage.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.electricadvantage.infrastructure.PumpPipeBlock;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

public final class PumpBlock extends MaterialEntityBlock<PumpBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public PumpBlock(Properties properties, BlockEntityBinding<PumpBlockEntity> binding) {
        super(properties,binding.prepared(),binding.type());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(POWERED,false).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ACTIVE,FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){
        return level.getBlockEntity(pos) instanceof PumpBlockEntity be?be.comparator():0;
    }
    /** Breaking the pump retracts its pipe; removing the top segment clears the column below it. */
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        super.onRemove(state,level,pos,next,moving);
        if(!level.isClientSide&&state.getBlock()!=next.getBlock()&&level.getBlockState(pos.below()).getBlock() instanceof PumpPipeBlock)level.removeBlock(pos.below(),false);
    }
}

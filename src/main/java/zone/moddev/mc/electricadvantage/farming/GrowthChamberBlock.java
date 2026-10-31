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

public final class GrowthChamberBlock extends MaterialEntityBlock<GrowthChamberBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public GrowthChamberBlock(Properties properties, BlockEntityBinding<GrowthChamberBlockEntity> binding) {
        super(properties,binding.prepared(),binding.type());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(POWERED,false).setValue(FACING,Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ACTIVE,FACING,POWERED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide && state.getBlock()!=next.getBlock() && level.getBlockEntity(pos) instanceof GrowthChamberBlockEntity be) be.dropContents(level,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}

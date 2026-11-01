package zone.moddev.mc.electricadvantage.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import zone.moddev.mc.lib.api.BlockEntityBinding;
import zone.moddev.mc.lib.common.runtime.MaterialEntityBlock;

/** Faces into the block it was placed against, any of the six directions, as in EA2. */
public final class DrillBlock extends MaterialEntityBlock<DrillBlockEntity> {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ACTIVE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("active");
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.FACING;
    public DrillBlock(Properties properties, BlockEntityBinding<DrillBlockEntity> binding) {
        super(properties.noOcclusion(),binding.prepared(),binding.type());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(FACING,Direction.DOWN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ACTIVE,FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace().getOpposite());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide && state.getBlock()!=next.getBlock() && level.getBlockEntity(pos) instanceof DrillBlockEntity be) be.items.dropAll(level,pos);
        super.onRemove(state,level,pos,next,moving);
    }
}

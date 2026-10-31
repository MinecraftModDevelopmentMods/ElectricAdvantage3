package zone.moddev.mc.electricadvantage.processing;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.*;

public final class ProcessingSystems {
    private ProcessingSystems(){}
    public static void register(){
        var id=ResourceLocation.parse("electricadvantage:processing");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id,new BlockImplementation<ProcessingBlock>(){
            @Override public Class<ProcessingBlock> blockClass(){return ProcessingBlock.class;}
            @Override public ProcessingBlock create(MMDMaterial material,MMDBlock definition){throw new UnsupportedOperationException("BlockContext required");}
            @Override public ProcessingBlock create(BlockContext context){
                return new ProcessingBlock(context.properties(),context.blockEntity().orElseThrow().as(ProcessingBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id,new BlockEntityFactory<ProcessingBlockEntity,ProcessingSettings>(){
            @Override public Class<ProcessingBlockEntity> blockEntityClass(){return ProcessingBlockEntity.class;}
            @Override public Codec<ProcessingSettings> settingsCodec(){return ProcessingSettings.CODEC;}
            @Override public ProcessingBlockEntity create(BlockEntityType<ProcessingBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,ProcessingSettings settings){
                return new ProcessingBlockEntity(type,pos,state,settings);
            }
            @Override public BlockEntityTicker<ProcessingBlockEntity> ticker(Level level,MMDBlockEntity definition,ProcessingSettings settings){return level.isClientSide?null:ProcessingBlockEntity::tick;}
        });
    }
}

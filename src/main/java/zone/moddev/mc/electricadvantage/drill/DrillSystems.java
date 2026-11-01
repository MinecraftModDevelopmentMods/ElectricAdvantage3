package zone.moddev.mc.electricadvantage.drill;

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

public final class DrillSystems {
    private DrillSystems(){}
    public static void register(){
        var id=ResourceLocation.parse("electricadvantage:drill");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id,new BlockImplementation<DrillBlock>(){
            @Override public Class<DrillBlock> blockClass(){return DrillBlock.class;}
            @Override public DrillBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public DrillBlock create(BlockContext context){return new DrillBlock(context.properties(),context.blockEntity().orElseThrow().as(DrillBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id,new BlockEntityFactory<DrillBlockEntity,DrillSettings>(){
            @Override public Class<DrillBlockEntity> blockEntityClass(){return DrillBlockEntity.class;}
            @Override public Codec<DrillSettings> settingsCodec(){return DrillSettings.CODEC;}
            @Override public DrillBlockEntity create(BlockEntityType<DrillBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,DrillSettings settings){return new DrillBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<DrillBlockEntity> ticker(Level level,MMDBlockEntity definition,DrillSettings settings){return level.isClientSide?null:DrillBlockEntity::tick;}
        });
    }
}

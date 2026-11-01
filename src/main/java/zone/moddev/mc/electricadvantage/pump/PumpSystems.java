package zone.moddev.mc.electricadvantage.pump;

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

public final class PumpSystems {
    private PumpSystems(){}
    public static void register(){
        var id=ResourceLocation.parse("electricadvantage:pump");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id,new BlockImplementation<PumpBlock>(){
            @Override public Class<PumpBlock> blockClass(){return PumpBlock.class;}
            @Override public PumpBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public PumpBlock create(BlockContext context){return new PumpBlock(context.properties(),context.blockEntity().orElseThrow().as(PumpBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id,new BlockEntityFactory<PumpBlockEntity,PumpSettings>(){
            @Override public Class<PumpBlockEntity> blockEntityClass(){return PumpBlockEntity.class;}
            @Override public Codec<PumpSettings> settingsCodec(){return PumpSettings.CODEC;}
            @Override public PumpBlockEntity create(BlockEntityType<PumpBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,PumpSettings settings){return new PumpBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<PumpBlockEntity> ticker(Level level,MMDBlockEntity definition,PumpSettings settings){return level.isClientSide?null:PumpBlockEntity::tick;}
        });
    }
}

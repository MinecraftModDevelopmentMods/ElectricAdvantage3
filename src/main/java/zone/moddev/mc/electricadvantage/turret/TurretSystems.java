package zone.moddev.mc.electricadvantage.turret;

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

public final class TurretSystems {
    private TurretSystems(){}
    public static void register(){
        var id=ResourceLocation.parse("electricadvantage:laser_turret");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id,new BlockImplementation<LaserTurretBlock>(){
            @Override public Class<LaserTurretBlock> blockClass(){return LaserTurretBlock.class;}
            @Override public LaserTurretBlock create(MMDMaterial material,MMDBlock block){throw new UnsupportedOperationException("BlockContext required");}
            @Override public LaserTurretBlock create(BlockContext context){return new LaserTurretBlock(context.properties(),context.blockEntity().orElseThrow().as(LaserTurretBlockEntity.class));}
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id,new BlockEntityFactory<LaserTurretBlockEntity,TurretSettings>(){
            @Override public Class<LaserTurretBlockEntity> blockEntityClass(){return LaserTurretBlockEntity.class;}
            @Override public Codec<TurretSettings> settingsCodec(){return TurretSettings.CODEC;}
            @Override public LaserTurretBlockEntity create(BlockEntityType<LaserTurretBlockEntity> type,BlockPos pos,BlockState state,MMDBlockEntity definition,TurretSettings settings){return new LaserTurretBlockEntity(type,pos,state,settings);}
            @Override public BlockEntityTicker<LaserTurretBlockEntity> ticker(Level level,MMDBlockEntity definition,TurretSettings settings){return level.isClientSide?LaserTurretBlockEntity::clientTick:LaserTurretBlockEntity::tick;}
        });
    }
}

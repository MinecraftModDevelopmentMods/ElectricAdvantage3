package zone.moddev.mc.electricadvantage.generation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.mojang.serialization.Codec;
import zone.moddev.mc.lib.api.*;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.*;

public final class GenerationSystems {
    private static final Map<ServerLevel, Set<BlockPos>> BACKUP = new HashMap<>();
    private GenerationSystems() {}
    public static void queueBackup(ServerLevel level, BlockPos pos) {
        BACKUP.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
    }
    public static void register() {
        var id = ResourceLocation.parse("electricadvantage:generation");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id, new BlockImplementation<GenerationBlock>() {
            @Override public Class<GenerationBlock> blockClass() { return GenerationBlock.class; }
            @Override public GenerationBlock create(MMDMaterial material, MMDBlock block) { throw new UnsupportedOperationException("BlockContext required"); }
            @Override public GenerationBlock create(BlockContext context) {
                context.factoryData().decode(GenerationSettings.CODEC).getOrThrow();
                return new GenerationBlock(context.properties(), context.blockEntity().orElseThrow().as(GenerationBlockEntity.class));
            }
        });
        zone.moddev.mc.lib.util.Registries.BLOCK_ENTITY_FACTORIES.register(id, new BlockEntityFactory<GenerationBlockEntity, GenerationSettings>() {
            @Override public Class<GenerationBlockEntity> blockEntityClass() { return GenerationBlockEntity.class; }
            @Override public Codec<GenerationSettings> settingsCodec() { return GenerationSettings.CODEC; }
            @Override public GenerationBlockEntity create(BlockEntityType<GenerationBlockEntity> type, BlockPos pos, BlockState state, MMDBlockEntity definition, GenerationSettings settings) {
                return new GenerationBlockEntity(type, pos, state, settings);
            }
            @Override public BlockEntityTicker<GenerationBlockEntity> ticker(Level level, MMDBlockEntity definition, GenerationSettings settings) {
                return level.isClientSide ? GenerationBlockEntity::clientTick : GenerationBlockEntity::tick;
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                var pending = BACKUP.remove(level);
                if (pending != null) for (var pos : pending) {
                    if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof GenerationBlockEntity be && be.array()) be.dischargeBackup();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) BACKUP.remove(level);
        });
    }
}

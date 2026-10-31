package zone.moddev.mc.electricadvantage.infrastructure;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import zone.moddev.mc.electricadvantage.ElectricAdvantage;
import zone.moddev.mc.lib.api.BlockContext;
import zone.moddev.mc.lib.api.BlockImplementation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.factories.BlockEntityFactory;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MMDBlockEntity;
import zone.moddev.mc.lib.util.Registries;

public final class Infrastructure {
    private Infrastructure() {}
    public static void register() {
        var id = ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, "electric_infrastructure");
        MaterialExtensions.GLOBAL.registerBlockImplementation(id, new BlockImplementation<ElectricBlock>() {
            @Override public Class<ElectricBlock> blockClass() { return ElectricBlock.class; }
            @Override public ElectricBlock create(MMDMaterial material, MMDBlock definition) {
                throw new UnsupportedOperationException("Infrastructure requires BlockContext");
            }
            @Override public ElectricBlock create(BlockContext context) {
                var settings = context.factoryData().decode(InfrastructureSettings.CODEC).getOrThrow();
                return new ElectricBlock(context.properties(), context.blockEntity().orElseThrow().as(ElectricBlockEntity.class), settings.kind());
            }
        });
        Registries.BLOCK_ENTITY_FACTORIES.register(id, new BlockEntityFactory<ElectricBlockEntity, InfrastructureSettings>() {
            @Override public Class<ElectricBlockEntity> blockEntityClass() { return ElectricBlockEntity.class; }
            @Override public Codec<InfrastructureSettings> settingsCodec() { return InfrastructureSettings.CODEC; }
            @Override public ElectricBlockEntity create(BlockEntityType<ElectricBlockEntity> type, BlockPos pos, BlockState state,
                    MMDBlockEntity definition, InfrastructureSettings settings) {
                return new ElectricBlockEntity(type, pos, state, settings);
            }
            @Override public BlockEntityTicker<ElectricBlockEntity> ticker(Level level, MMDBlockEntity definition, InfrastructureSettings settings) {
                return level.isClientSide ? null : ElectricBlockEntity::tick;
            }
        });
        MaterialExtensions.GLOBAL.registerBlockImplementation(ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID, "pump_pipe"),
                new BlockImplementation<PumpPipeBlock>() {
                    @Override public Class<PumpPipeBlock> blockClass() { return PumpPipeBlock.class; }
                    @Override public PumpPipeBlock create(MMDMaterial material, MMDBlock definition) {
                        throw new UnsupportedOperationException("Pipe requires BlockContext");
                    }
                    @Override public PumpPipeBlock create(BlockContext context) { return new PumpPipeBlock(context.properties()); }
                });
        ElectricNetworks.registerEvents();
    }
}

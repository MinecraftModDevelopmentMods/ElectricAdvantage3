package zone.moddev.mc.electricadvantage.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class ElectricBlockEntity extends BlockEntity {
    public final EnergyBuffer energy;
    public final InfrastructureSettings settings;
    private final IEnergyStorage input;

    public ElectricBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, InfrastructureSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        energy = new EnergyBuffer(settings.capacity(), settings.rate()).onChange(this::setChanged);
        input = energy.view(() -> ((ElectricBlock)getBlockState().getBlock()).enabled(getBlockState()) ? IoMode.INPUT : IoMode.DISABLED);
    }
    public IEnergyStorage input() { return input; }
    public static void tick(Level world, BlockPos pos, BlockState state, ElectricBlockEntity be) {
        if (!(world instanceof ServerLevel level)) return;
        var block = (ElectricBlock)state.getBlock();
        if (block.led()) {
            if (level.getGameTime() % be.settings.cycleTicks() == 0) {
                boolean lit = be.energy.consume(be.settings.cycleCost());
                if (state.getValue(ElectricBlock.LIT) != lit) level.setBlock(pos, state.setValue(ElectricBlock.LIT, lit), 3);
            }
        } else if (block.enabled(state) && be.energy.stored() > 0) {
            ElectricNetworks.get(level).send(be);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
    }
}

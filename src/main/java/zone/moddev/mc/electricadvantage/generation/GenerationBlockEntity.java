package zone.moddev.mc.electricadvantage.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import zone.moddev.mc.lib.api.capabilities.*;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.poweradvantage.init.Fluids;

public final class GenerationBlockEntity extends BlockEntity implements ScreenHost {
    public final GenerationSettings settings;
    public final EnergyBuffer energy;
    public final FluidStorage steam = new FluidStorage();
    public final ItemStorage items;
    private final IEnergyStorage output;
    private final BackupEnergyStorage batteries;
    public float rotorAngle;
    public float previousRotorAngle;
    public float rotorYaw;
    private double fraction;
    private double lastOutput;
    private int oldComparator = -1;

    public GenerationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, GenerationSettings settings) {
        super(type, pos, state);
        this.settings = settings;
        settings.steamPerFe();
        energy = new EnergyBuffer(settings.capacity(), settings.rate()).onChange(this::setChanged);
        steam.addTank(settings.steamCapacity(), Role.INPUT, fluid -> fluid.is(Fluids.STEAM.get()));
        steam.onChange(this::setChanged);
        items = new ItemStorage(array() ? 8 : 0).onChange(this::setChanged);
        for (int slot = 0; slot < items.getSlots(); slot++) items.limit(1, slot).filter(stack -> stack.getCapability(Capabilities.EnergyStorage.ITEM) != null, slot);
        batteries = new BackupEnergyStorage() {
            @Override public int getEnergyStored() { return batteryTotal(false); }
            @Override public int getMaxEnergyStored() { return batteryTotal(true); }
            @Override public boolean canReceive() { return true; }
            @Override public boolean canExtract() { return !disabled(); }
            @Override public int receiveEnergy(int amount, boolean simulate) { return transferBatteries(amount, simulate, true); }
            @Override public int extractEnergy(int amount, boolean simulate) { return disabled() ? 0 : transferBatteries(amount, simulate, false); }
        };
        output = energy.view(() -> disabled() ? IoMode.DISABLED : IoMode.OUTPUT);
    }
    public boolean array() { return settings.kind().equals("array"); }
    public boolean disabled() { return level != null && level.hasNeighborSignal(worldPosition); }
    public IEnergyStorage capability() { return array() ? batteries : output; }
    public IFluidHandler fluidCapability() { return steam.view(IoMode.INPUT); }
    public int batteryTotal(boolean capacity) {
        long total = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            var handler = items.getStackInSlot(i).getCapability(Capabilities.EnergyStorage.ITEM);
            if (handler != null) total += capacity ? handler.getMaxEnergyStored() : handler.getEnergyStored();
        }
        return (int)Math.min(Integer.MAX_VALUE, total);
    }
    private int transferBatteries(int amount, boolean simulate, boolean receiving) {
        int remaining = Math.max(0, amount);
        for (int i = 0; i < items.getSlots() && remaining > 0; i++) {
            var handler = items.getStackInSlot(i).getCapability(Capabilities.EnergyStorage.ITEM);
            if (handler != null) remaining -= Math.clamp(receiving ? handler.receiveEnergy(remaining, simulate) : handler.extractEnergy(remaining, simulate), 0, remaining);
        }
        int moved = Math.max(0, amount) - remaining;
        if (!simulate && moved > 0) setChanged();
        return moved;
    }
    public static void clientTick(Level level, BlockPos pos, BlockState state, GenerationBlockEntity be) {
        var flow = be.waterFlow();
        if (flow.horizontalDistanceSqr() > 0.00001) be.rotorYaw = (float)Math.atan2(-flow.z, flow.x) - (float)Math.PI / 2;
        be.previousRotorAngle = be.rotorAngle;
        if (state.getValue(GenerationBlock.ACTIVE)) be.rotorAngle = (be.rotorAngle + 30) % 360;
    }
    public static void tick(Level world, BlockPos pos, BlockState state, GenerationBlockEntity be) {
        if (!(world instanceof ServerLevel level)) return;
        if (be.array()) {
            GenerationSystems.queueBackup(level, pos);
            be.setActive(!be.disabled() && be.batteryTotal(false) > 0);
        } else {
            be.lastOutput = 0;
            if (!be.disabled()) {
                if (be.settings.kind().equals("steam")) be.convertSteam();
                else {
                    double intensity = be.settings.kind().equals("solar") ? be.solarIntensity() : be.waterFlow().horizontalDistanceSqr() > 0.00001 ? 1 : 0;
                    double generated = Math.min(be.energy.space(), be.settings.generation() * intensity + be.fraction);
                    long whole = (long)generated;
                    be.energy.insert(whole, false);
                    double nextFraction = generated - whole;
                    if (nextFraction != be.fraction) { be.fraction = nextFraction; be.setChanged(); }
                    be.lastOutput = be.settings.generation() * intensity;
                    if (be.settings.kind().equals("hydro") && intensity > 0 && level.getGameTime() % 8 == 0) be.damageTurbine(level);
                }
                int remaining = be.settings.rate();
                for (int offset = 0; offset < 6 && remaining > 0; offset++) {
                    Direction side = Direction.values()[(int)((level.getGameTime() + offset) % 6)];
                    if (level.hasChunkAt(pos.relative(side))) remaining -= Transfer.pushEnergy(level, pos, side, be.output, remaining);
                }
            }
            be.setActive(be.lastOutput > 0);
        }
        int comparator = be.comparator();
        if (comparator != be.oldComparator) { be.oldComparator = comparator; level.updateNeighbourForOutputSignal(pos, state.getBlock()); }
    }
    public void dischargeBackup() {
        if (!(level instanceof ServerLevel server) || disabled()) return;
        int remaining = settings.rate();
        for (int offset = 0; offset < 6 && remaining > 0; offset++) {
            Direction direction = Direction.values()[(int)((server.getGameTime() + offset) % 6)];
            BlockPos next = worldPosition.relative(direction);
            if (!server.hasChunkAt(next)) continue;
            var state = server.getBlockState(next);
            if (state.getBlock() instanceof zone.moddev.mc.electricadvantage.infrastructure.ElectricBlock block && block.conductor(state)) {
                remaining -= zone.moddev.mc.electricadvantage.infrastructure.ElectricNetworks.get(server).sendBackup(next, worldPosition, batteries, remaining);
            } else {
                var target = server.getCapability(Capabilities.EnergyStorage.BLOCK, next, direction.getOpposite());
                if (target != null && !(target instanceof BackupEnergyStorage)) remaining -= Transfer.energy(batteries, target, remaining);
            }
        }
    }
    private void convertSteam() {
        int available = steam.tank(0).getFluidAmount() / settings.steamPerFe();
        int output = (int)Math.min(Math.min(available, settings.rate()), energy.space());
        if (output > 0) {
            steam.tank(0).drain(output * settings.steamPerFe(), IFluidHandler.FluidAction.EXECUTE);
            energy.insert(output, false);
            lastOutput = output;
        }
    }
    public double solarIntensity() {
        if (level == null) return 0;
        if (level.dimensionType().hasSkyLight() && level.canSeeSky(worldPosition.above())) {
            double sunlight = Math.max(0, Math.sin((level.getDayTime() % 24000) * Math.PI / 12000));
            return sunlight * (level.isThundering() ? settings.thunderFactor() : level.isRaining() ? settings.rainFactor() : 1);
        }
        return settings.blockLightFactor() * level.getBrightness(LightLayer.BLOCK, worldPosition.above()) / 15.0;
    }
    public Vec3 waterFlow() {
        if (level == null || !level.hasChunkAt(worldPosition.below())) return Vec3.ZERO;
        for (var direction : Direction.Plane.HORIZONTAL) {
            if (!level.hasChunkAt(worldPosition.below().relative(direction))) return Vec3.ZERO;
        }
        var fluid = level.getFluidState(worldPosition.below());
        return fluid.is(net.minecraft.tags.FluidTags.WATER) ? fluid.getFlow(level, worldPosition.below()) : Vec3.ZERO;
    }
    private void damageTurbine(ServerLevel level) {
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("electricadvantage:propeller")));
        for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition.below()).deflate(0.03125))) {
            victim.hurt(new DamageSource(type), settings.propellerDamage());
        }
    }
    private void setActive(boolean active) {
        if (level != null && getBlockState().getValue(GenerationBlock.ACTIVE) != active)
            level.setBlock(worldPosition, getBlockState().setValue(GenerationBlock.ACTIVE, active), 3);
    }
    public int comparator() { return (int)Math.clamp(15 * (settings.kind().equals("solar") ? lastOutput / Math.max(0.00001, settings.generation()) : screenEnergy() / (double)Math.max(1, screenCapacity())), 0, 15); }
    public long screenEnergy() { return array() ? batteryTotal(false) : energy.stored(); }
    public long screenCapacity() { return array() ? batteryTotal(true) : energy.capacity(); }
    public FluidStack screenSteam() { return steam.getFluidInTank(0); }
    public int screenSteamCapacity() { return settings.steamCapacity(); }
    public double screenOutput() { return lastOutput; }
    @Override public ItemStorage screenItems() { return items; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Steam", steam.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.putDouble("Fraction", fraction);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        if (tag.contains("Steam")) steam.deserializeNBT(registries, tag.getCompound("Steam"));
        if (tag.contains("Items")) items.deserializeNBT(registries, tag.getCompound("Items"));
        fraction = Math.clamp(tag.getDouble("Fraction"), 0, 0.999999999);
    }
}
